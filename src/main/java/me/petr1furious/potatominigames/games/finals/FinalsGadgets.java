package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsGadgetState.Kind.*;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/** All gadget state belongs to one Finals session; its existing ticker drives every effect. */
final class FinalsGadgets {
    static final double JUMP_PAD_SPEED = 1.05;
    private static final NamespacedKey KEY = new NamespacedKey("potatominigames", "finals_gadget");
    private static final double DOME_RADIUS = 4;
    private static final long BEAM_INPUT_TICKS = 6;
    private final FinalsCombatContext context;
    private final FinalsGadgetState resources = new FinalsGadgetState();
    private final Map<UUID, FinalsLoadouts.Choice> equipped = new HashMap<>();
    private final FinalsGadgetEffects effects;
    private final List<Ordnance> ordnance = new ArrayList<>();
    private final Map<UUID, Hidden> hidden = new HashMap<>();
    private final Map<UUID, Revealed> revealed = new HashMap<>();
    private final Map<UUID, Beam> beams = new HashMap<>();
    private final Map<UUID, Charge> charges = new HashMap<>();
    private final Map<Block, Cover> cover = new HashMap<>();
    private final List<Pad> pads = new ArrayList<>();
    private final List<Dome> domes = new ArrayList<>();
    private final Map<UUID, Long> launchedUntil = new HashMap<>();

    private static final class Ordnance {
        final UUID owner;
        final FinalsGadgetState.Kind kind;
        final ItemDisplay display;
        final long expires;
        Location position;
        Vector velocity;

        Ordnance(
                UUID owner,
                FinalsGadgetState.Kind kind,
                ItemDisplay display,
                long expires,
                Location position,
                Vector velocity) {
            this.owner = owner;
            this.kind = kind;
            this.display = display;
            this.expires = expires;
            this.position = position;
            this.velocity = velocity;
        }
    }

    private record Hidden(Player player, long expires, Set<Player> viewers) {}

    private record Revealed(Player player, boolean originalGlow, long expires) {}

    private record Beam(Player player, UUID target, long expires) {}

    private record Charge(Player player, long expires, Set<UUID> hit) {}

    private record Cover(UUID owner, BlockData previous, BlockData placed, long expires) {}

    private record Pad(UUID owner, Block block, long expires) {}

    private record Dome(Player owner, Location center, long expires) {}

    FinalsGadgets(FinalsCombatContext context) {
        this.context = context;
        effects = new FinalsGadgetEffects(context, this);
    }

    private static String prefix(FinalsGadgetState.Kind kind) {
        return "finals.gadget." + kind.name().toLowerCase(Locale.ROOT);
    }

    private static Component text(String key, Object... args) {
        return I18n.tr("finals.gadget." + key, args);
    }

    private static FinalsGadgetState.Kind[] loadout(FinalsClass role) {
        return switch (role) {
            case LIGHT ->
                    new FinalsGadgetState.Kind[] {
                        FRAG_GRENADE, SONAR_GRENADE, VANISHING_BOMB, EVASIVE_DASH
                    };
            case MEDIUM ->
                    new FinalsGadgetState.Kind[] {
                        FRAG_GRENADE, JUMP_PAD, DEFIBRILLATOR, HEALING_BEAM
                    };
            case HEAVY ->
                    new FinalsGadgetState.Kind[] {RPG7, DOME_SHIELD, BARRICADE, CHARGE_N_SLAM};
        };
    }

    private static Material material(FinalsGadgetState.Kind kind) {
        return kind.material();
    }

    private ItemStack item(Player player, FinalsGadgetState.Kind kind) {
        ItemStack item = new ItemStack(material(kind));
        int available = resources.charges(player.getUniqueId(), kind, context.tick());
        item.editMeta(meta -> {
            meta.displayName(I18n.tr(prefix(kind) + ".name").color(NamedTextColor.AQUA));
            meta.lore(
                    List.of(
                            I18n.tr(prefix(kind) + ".lore").color(NamedTextColor.GRAY),
                            (kind == HEALING_BEAM
                                            ? text("beam_resource")
                                            : text(
                                                    "charges",
                                                    available,
                                                    kind.capacity,
                                                    kind.recharge / 20.0))
                                    .color(NamedTextColor.YELLOW)));
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, kind.name());
            meta.setEnchantmentGlintOverride(
                    kind == HEALING_BEAM
                            ? !resources.beamOverheated(player.getUniqueId())
                            : available > 0);
        });
        return item;
    }

    private static FinalsGadgetState.Kind from(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String name =
                item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (name == null) return null;
        try {
            var kind = FinalsGadgetState.Kind.valueOf(name);
            return item.getType() == material(kind) ? kind : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    void equip(Player player, FinalsClass role) {
        equip(player, new FinalsLoadouts.Choice(role, FinalsWeapon.byClass(role).getFirst()));
    }

    void equip(Player player, FinalsLoadouts.Choice choice) {
        forget(player.getUniqueId());
        equipped.put(player.getUniqueId(), choice);
        refreshItems(player);
    }

    private static FinalsGadgetState.Kind selected(FinalsLoadouts.Choice choice, int slot) {
        return slot == 4 ? choice.specialization() : choice.gadgets().get(slot - 1);
    }

    private void refreshItems(Player player) {
        var choice = equipped.get(player.getUniqueId());
        if (choice == null) return;
        for (int slot = 1; slot <= 4; slot++)
            player.getInventory()
                    .setItem(slot, I18n.item(item(player, selected(choice, slot)), player));
    }

    boolean usingDefibrillator(Player player) {
        var choice = equipped.get(player.getUniqueId());
        int slot = player.getInventory().getHeldItemSlot();
        return choice != null
                && slot >= 1
                && slot <= 4
                && selected(choice, slot) == DEFIBRILLATOR
                && from(player.getInventory().getItemInMainHand()) == DEFIBRILLATOR;
    }

    boolean usingHealingBeam(Player player) {
        var choice = equipped.get(player.getUniqueId());
        return choice != null
                && choice.specialization() == HEALING_BEAM
                && player.getInventory().getHeldItemSlot() == 4
                && from(player.getInventory().getItemInMainHand()) == HEALING_BEAM;
    }

    boolean interactHealingTarget(Player player) {
        if (!usingHealingBeam(player)) return false;
        return interact(
                new PlayerInteractEvent(
                        player,
                        Action.RIGHT_CLICK_AIR,
                        player.getInventory().getItemInMainHand(),
                        null,
                        BlockFace.UP,
                        EquipmentSlot.HAND));
    }

    boolean interactStatue(Player player) {
        if (!usingDefibrillator(player)) return false;
        return interact(
                new PlayerInteractEvent(
                        player,
                        Action.RIGHT_CLICK_AIR,
                        player.getInventory().getItemInMainHand(),
                        null,
                        null,
                        EquipmentSlot.HAND));
    }

    boolean interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return false;
        var kind = from(event.getItem());
        if (kind == null) return false;
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return true;
        Player player = event.getPlayer();
        var choice = equipped.get(player.getUniqueId());
        int slot = player.getInventory().getHeldItemSlot();
        if (choice == null
                || slot < 1
                || slot > 4
                || selected(choice, slot) != kind
                || !context.canAct(player)) return true;
        if (context.gadgetsBlocked(player)) {
            player.sendActionBar(text("revive_sickness"));
            return true;
        }
        if (kind == HEALING_BEAM) {
            beginBeam(player);
            player.sendActionBar(beamHud(player));
            return true;
        }
        if (kind == CLOAKING_DEVICE && hidden.containsKey(player.getUniqueId())) {
            reveal(player);
            return true;
        }
        if (effects.toggle(player, kind)) return true;
        if (!resources.ready(player.getUniqueId(), kind, context.tick())) {
            player.sendActionBar(
                    text(
                            "recharging",
                            I18n.tr(prefix(kind) + ".name"),
                            (resources.remaining(player.getUniqueId(), kind, context.tick()) + 19)
                                    / 20));
            return true;
        }
        boolean used =
                switch (kind) {
                    case EVASIVE_DASH -> dash(player);
                    case FRAG_GRENADE,
                            SONAR_GRENADE,
                            RPG7,
                            SMOKE_GRENADE,
                            FLASHBANG,
                            GAS_GRENADE,
                            PYRO_GRENADE,
                            GOO_GRENADE ->
                            launch(player, kind);
                    case VANISHING_BOMB -> vanish(player);
                    case CLOAKING_DEVICE -> vanish(player, 200);
                    case HEALING_BEAM -> beginBeam(player);
                    case JUMP_PAD -> placePad(player);
                    case DEFIBRILLATOR -> defibrillate(player);
                    case CHARGE_N_SLAM -> beginCharge(player);
                    case DOME_SHIELD -> dome(player);
                    case BARRICADE -> barricade(player);
                    default -> effects.activate(player, kind);
                };
        if (used) {
            resources.use(player.getUniqueId(), kind, context.tick());
            refreshItems(player);
        }
        return true;
    }

    Component hud(Player player) {
        var choice = equipped.get(player.getUniqueId());
        if (choice == null) return Component.empty();
        int slot = player.getInventory().getHeldItemSlot();
        var kind = selected(choice, slot >= 1 && slot <= 4 ? slot : 4);
        if (kind == HEALING_BEAM) return beamHud(player);
        long seconds = (resources.remaining(player.getUniqueId(), kind, context.tick()) + 19) / 20;
        if (seconds == 0)
            return text("hud_ready", I18n.tr(prefix(kind) + ".name"), kind.capacity, kind.capacity)
                    .color(NamedTextColor.AQUA);
        return text(
                        "hud",
                        I18n.tr(prefix(kind) + ".name"),
                        resources.charges(player.getUniqueId(), kind, context.tick()),
                        kind.capacity,
                        seconds)
                .color(NamedTextColor.AQUA);
    }

    private Component beamHud(Player player) {
        UUID id = player.getUniqueId();
        int heat = resources.beamHeat(id);
        int filled =
                (heat * 20 + FinalsGadgetState.BEAM_MAX_HEAT - 1) / FinalsGadgetState.BEAM_MAX_HEAT;
        Component bar =
                Component.text(
                                "▰".repeat(filled),
                                heat >= 120 ? NamedTextColor.RED : NamedTextColor.YELLOW)
                        .append(Component.text("▱".repeat(20 - filled), NamedTextColor.DARK_GRAY));
        if (resources.beamOverheated(id))
            return text("beam_overheated", bar, (heat + 39) / 40).color(NamedTextColor.RED);
        return text("beam_heat", bar, (heat * 100) / FinalsGadgetState.BEAM_MAX_HEAT)
                .color(NamedTextColor.AQUA);
    }

    private static Vector forward(Player player, double speed, double y) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < .001) direction = new Vector(0, 0, 1);
        return direction.normalize().multiply(speed).setY(y);
    }

    private boolean dash(Player player) {
        Vector velocity = forward(player, 1.35, .14);
        if (!context.inside(player.getLocation().add(velocity.clone().multiply(2)))) return false;
        reveal(player);
        player.setVelocity(velocity);
        context.world().spawnParticle(Particle.CLOUD, player.getLocation(), 12, .3, .2, .3, .03);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, .7f, 1.5f);
        return true;
    }

    private boolean launch(Player player, FinalsGadgetState.Kind kind) {
        reveal(player);
        Location origin = player.getEyeLocation();
        Vector velocity = origin.getDirection().normalize().multiply(kind == RPG7 ? 1.65 : .62);
        if (kind != RPG7) velocity.add(new Vector(0, .15, 0));
        if (!context.inside(origin)) return false;
        ItemDisplay display =
                context.world()
                        .spawn(
                                origin,
                                ItemDisplay.class,
                                entity -> {
                                    entity.setItemStack(new ItemStack(material(kind)));
                                    entity.setGravity(false);
                                    entity.setInvulnerable(true);
                                    entity.setPersistent(false);
                                    entity.setTeleportDuration(1);
                                });
        context.track(display);
        ordnance.add(
                new Ordnance(
                        player.getUniqueId(),
                        kind,
                        display,
                        context.tick() + (kind == RPG7 ? 45 : 32),
                        origin.clone(),
                        velocity));
        player.playSound(origin, Sound.ENTITY_SNOWBALL_THROW, .9f, kind == RPG7 ? .5f : 1f);
        return true;
    }

    private boolean vanish(Player player) {
        return vanish(player, 100);
    }

    private boolean vanish(Player player, long duration) {
        if (context.carrier(player)) {
            player.sendActionBar(text("carrier_visible"));
            return false;
        }
        restoreGlow(player.getUniqueId());
        reveal(player);
        Hidden effect = new Hidden(player, context.tick() + duration, new HashSet<>());
        hidden.put(player.getUniqueId(), effect);
        updateHidden(effect);
        context.world()
                .spawnParticle(
                        Particle.CLOUD, player.getLocation().add(0, 1, 0), 24, .4, .7, .4, .02);
        return true;
    }

    private void updateHidden(Hidden effect) {
        for (Player viewer : context.players()) {
            if (!viewer.equals(effect.player())
                    && !context.friendly(viewer, effect.player())
                    && effect.viewers().add(viewer))
                viewer.hidePlayer(context.plugin(), effect.player());
        }
    }

    void reveal(Player player) {
        Hidden effect = hidden.remove(player.getUniqueId());
        if (effect == null) return;
        for (Player viewer : effect.viewers())
            if (!effects.smokeHides(viewer, effect.player()))
                viewer.showPlayer(context.plugin(), effect.player());
    }

    boolean cloakedFor(Player viewer, Player target) {
        return hidden.containsKey(target.getUniqueId()) && !context.friendly(viewer, target);
    }

    private boolean beginBeam(Player player) {
        if (resources.beamOverheated(player.getUniqueId())) return false;
        Player target = aimedAlly(player, 10);
        if (target == null) target = player;
        reveal(player);
        beams.put(
                player.getUniqueId(),
                new Beam(player, target.getUniqueId(), context.tick() + BEAM_INPUT_TICKS));
        return true;
    }

    private Player aimedAlly(Player player, double range) {
        Location eye = player.getEyeLocation();
        Player best = null;
        double bestDistance = range;
        for (Player candidate : context.players()) {
            if (candidate.equals(player)
                    || !context.active(candidate)
                    || !context.friendly(player, candidate)) continue;
            RayTraceResult hit =
                    candidate
                            .getBoundingBox()
                            .expand(.35)
                            .rayTrace(eye.toVector(), eye.getDirection(), range);
            if (hit == null || !player.hasLineOfSight(candidate)) continue;
            double distance = hit.getHitPosition().distance(eye.toVector());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20 : attribute.getValue();
    }

    private boolean defibrillate(Player player) {
        if (context.defibrillateAimed(player)) {
            reveal(player);
            player.playSound(player.getLocation(), "minecraft:block.beacon.activate", .8f, 1.7f);
            return true;
        }
        record Candidate(Player player, Location point, double distance) {}
        List<Candidate> candidates = new ArrayList<>();
        for (Player teammate : context.players()) {
            if (teammate.equals(player)
                    || !context.friendly(player, teammate)
                    || !context.awaitingRespawn(teammate)) continue;
            Location death = context.deathLocation(teammate.getUniqueId());
            if (death == null || !context.inside(death)) continue;
            double distance = player.getLocation().distanceSquared(death);
            if (distance <= 4 * 4
                    && clearLine(player.getEyeLocation(), death.clone().add(0, .8, 0))) {
                candidates.add(new Candidate(teammate, death.clone(), distance));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance));
        for (Candidate candidate : candidates) {
            // In 3v3 the revive helper accepts only the aimed statue. A nearer,
            // off-axis teammate must not prevent reaching that valid target.
            if (!context.revive(candidate.player(), player)) continue;
            reveal(player);
            context.world()
                    .spawnParticle(
                            Particle.HEART, candidate.point().add(0, 1, 0), 10, .4, .5, .4, 0);
            player.playSound(player.getLocation(), "minecraft:block.beacon.activate", .8f, 1.7f);
            return true;
        }
        player.sendActionBar(text("defib_missing"));
        return false;
    }

    Block placement(Player player) {
        Block target = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
        if (target == null || !target.getType().isSolid()) return null;
        Block above = target.getRelative(BlockFace.UP);
        return placeable(above) ? above : null;
    }

    boolean placeable(Block block) {
        if (!context.canPlace(block)
                || !context.inside(block.getLocation().add(.5, .5, .5))
                || !block.getType().isAir()
                || cover.containsKey(block)) return false;
        BoundingBox box =
                new BoundingBox(
                        block.getX(),
                        block.getY(),
                        block.getZ(),
                        block.getX() + 1,
                        block.getY() + 1,
                        block.getZ() + 1);
        for (Player player : context.players())
            if (context.active(player) && player.getBoundingBox().overlaps(box)) return false;
        return true;
    }

    void putCover(Player owner, Block block, Material material, long duration) {
        BlockData previous = block.getBlockData().clone();
        block.setType(material, false);
        cover.put(
                block,
                new Cover(
                        owner.getUniqueId(),
                        previous,
                        block.getBlockData().clone(),
                        context.tick() + duration));
    }

    private boolean placePad(Player player) {
        Block block = placement(player);
        if (block == null) {
            player.sendActionBar(text("placement_failed"));
            return false;
        }
        putCover(player, block, Material.SLIME_BLOCK, 300);
        pads.add(new Pad(player.getUniqueId(), block, context.tick() + 300));
        return true;
    }

    private boolean barricade(Player player) {
        Block center = placement(player);
        if (center == null) {
            player.sendActionBar(text("placement_failed"));
            return false;
        }
        Vector forward = player.getLocation().getDirection();
        boolean alongX = Math.abs(forward.getZ()) >= Math.abs(forward.getX());
        List<Block> blocks = new ArrayList<>();
        for (int offset = -1; offset <= 1; offset++)
            for (int height = 0; height <= 1; height++) {
                Block block = center.getRelative(alongX ? offset : 0, height, alongX ? 0 : offset);
                if (!placeable(block)) {
                    player.sendActionBar(text("placement_failed"));
                    return false;
                }
                blocks.add(block);
            }
        for (Block block : blocks) putCover(player, block, Material.LIGHT_GRAY_CONCRETE, 400);
        return true;
    }

    private boolean dome(Player player) {
        Location center = player.getLocation().add(0, .6, 0);
        if (!context.inside(center)) return false;
        domes.add(new Dome(player, center, context.tick() + 160));
        player.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, .7f, .8f);
        return true;
    }

    boolean blocksDamage(Player target, Player source) {
        return blocksDamage(target, source, source == null ? null : source.getLocation());
    }

    boolean blocksDamage(Player target, Player source, Location origin) {
        if (source == null
                || source.equals(target)
                || !context.active(target)
                || !context.active(source)
                || context.carrier(target)
                || context.friendly(target, source)) return false;
        Location to = target.getLocation(), from = origin == null ? source.getLocation() : origin;
        if (effects.blocksDamage(target, source, from)) return true;
        for (Dome dome : domes) {
            Location c = dome.center();
            if (context.tick() < dome.expires()
                    && context.active(dome.owner())
                    && context.friendly(target, dome.owner())
                    && FinalsGadgetState.crossesDome(
                            to.getX(),
                            to.getY(),
                            to.getZ(),
                            from.getX(),
                            from.getY(),
                            from.getZ(),
                            c.getX(),
                            c.getY(),
                            c.getZ(),
                            DOME_RADIUS)) return true;
        }
        return false;
    }

    private boolean beginCharge(Player player) {
        if (!context.inside(player.getLocation().add(forward(player, 2.5, 0)))) return false;
        reveal(player);
        charges.put(player.getUniqueId(), new Charge(player, context.tick() + 20, new HashSet<>()));
        player.setVelocity(forward(player, 1.05, .18));
        player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, .7f, 1.3f);
        return true;
    }

    boolean clearLine(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return false;
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        return length < .01
                || context.world()
                                .rayTraceBlocks(
                                        from,
                                        delta.normalize(),
                                        length,
                                        FluidCollisionMode.NEVER,
                                        true)
                        == null;
    }

    void tick() {
        long now = context.tick();
        effects.tick();
        for (Hidden effect : List.copyOf(hidden.values())) {
            if (now >= effect.expires()
                    || !context.canAct(effect.player())
                    || context.carrier(effect.player())) reveal(effect.player());
            else updateHidden(effect);
        }
        for (var entry : List.copyOf(revealed.entrySet()))
            if (now >= entry.getValue().expires() || !context.active(entry.getValue().player()))
                restoreGlow(entry.getKey());
        for (Ordnance shot : List.copyOf(ordnance)) tickOrdnance(shot);
        Set<UUID> beamUsers = new HashSet<>(beams.keySet());
        for (Beam beam : List.copyOf(beams.values())) tickBeam(beam);
        for (var entry : equipped.entrySet())
            if (entry.getValue().specialization() == HEALING_BEAM
                    && !beamUsers.contains(entry.getKey()))
                resources.beamTick(entry.getKey(), false, now);
        for (Charge charge : List.copyOf(charges.values())) tickCharge(charge);
        for (var entry : List.copyOf(cover.entrySet()))
            if (now >= entry.getValue().expires()) breakTemporary(entry.getKey());
        pads.removeIf(pad -> now >= pad.expires() || !cover.containsKey(pad.block()));
        for (Pad pad : List.copyOf(pads)) tickPad(pad);
        domes.removeIf(dome -> now >= dome.expires() || !context.active(dome.owner()));
        if (now % 4 == 0) for (Dome dome : domes) drawDome(dome);
        launchedUntil.entrySet().removeIf(entry -> now >= entry.getValue());
        if (now % 20 == 0)
            for (Player player : context.players())
                if (context.active(player)) refreshItems(player);
    }

    private Player owner(UUID id) {
        for (Player player : context.players()) if (player.getUniqueId().equals(id)) return player;
        return null;
    }

    private void removeOrdnance(Ordnance shot) {
        ordnance.remove(shot);
        shot.display.remove();
    }

    private void tickOrdnance(Ordnance shot) {
        // Damage callbacks may synchronously remove another shot through player death.
        if (!ordnance.contains(shot)) return;
        Player source = owner(shot.owner);
        if (source == null
                || !context.canAct(source)
                || !shot.display.isValid()
                || !context.inside(shot.position)) {
            removeOrdnance(shot);
            return;
        }
        if (context.tick() >= shot.expires) {
            removeOrdnance(shot);
            detonate(source, shot.kind, shot.position);
            return;
        }
        Vector velocity = shot.velocity.clone();
        if (effects.intercept(source, shot.position, shot.position.clone().add(velocity))) {
            removeOrdnance(shot);
            return;
        }
        double length = velocity.length();
        RayTraceResult wall =
                length < .001
                        ? null
                        : context.world()
                                .rayTraceBlocks(
                                        shot.position,
                                        velocity.clone().normalize(),
                                        length + .08,
                                        FluidCollisionMode.NEVER,
                                        true);
        if (shot.kind == RPG7) {
            double hitDistance =
                    wall == null
                            ? length
                            : wall.getHitPosition().distance(shot.position.toVector());
            boolean impact = wall != null;
            for (Player player : context.players()) {
                if (player.equals(source)
                        || !context.active(player)
                        || context.friendly(source, player)) continue;
                RayTraceResult hit =
                        player.getBoundingBox()
                                .expand(.22)
                                .rayTrace(
                                        shot.position.toVector(),
                                        velocity.clone().normalize(),
                                        hitDistance);
                if (hit != null) {
                    hitDistance = hit.getHitPosition().distance(shot.position.toVector());
                    impact = true;
                }
            }
            if (impact) {
                Location point =
                        shot.position
                                .clone()
                                .add(velocity.normalize().multiply(Math.max(0, hitDistance - .05)));
                removeOrdnance(shot);
                detonate(source, shot.kind, point);
                return;
            }
        }
        if (wall != null) {
            if (shot.kind == PYRO_GRENADE || shot.kind == GOO_GRENADE) {
                Location contact = wall.getHitPosition().toLocation(context.world());
                if (wall.getHitBlockFace() != null)
                    contact.add(wall.getHitBlockFace().getDirection().multiply(.1));
                removeOrdnance(shot);
                detonate(source, shot.kind, contact);
                return;
            }
            BlockFace face = wall.getHitBlockFace();
            if (face != null) {
                Vector normal = face.getDirection();
                shot.velocity
                        .subtract(normal.multiply(2 * shot.velocity.dot(normal)))
                        .multiply(.42);
            } else shot.velocity.multiply(-.3);
        } else {
            Location next = shot.position.clone().add(velocity);
            if (!context.inside(next)) {
                removeOrdnance(shot);
                return;
            }
            shot.position = next;
            shot.display.teleport(next);
        }
        if (shot.kind != RPG7) shot.velocity.multiply(.97).add(new Vector(0, -.035, 0));
        else context.world().spawnParticle(Particle.SMOKE, shot.position, 2, .03, .03, .03, 0);
    }

    void detonate(Player source, FinalsGadgetState.Kind kind, Location center) {
        if (!context.active(source) || !context.inside(center)) return;
        if (effects.detonate(source, kind, center)) return;
        if (kind == SONAR_GRENADE) {
            context.world().spawnParticle(Particle.END_ROD, center, 48, 2.5, 1.5, 2.5, .03);
            context.world().playSound(center, Sound.BLOCK_BEACON_AMBIENT, .9f, 1.7f);
            for (Player enemy : context.players()) {
                if (!context.active(enemy)
                        || context.friendly(source, enemy)
                        || enemy.getLocation().distanceSquared(center) > 81) continue;
                revealBySonar(enemy);
            }
            return;
        }
        double radius = kind == RPG7 ? 4 : 4.5;
        double maximum = kind == RPG7 ? 12 : 10;
        effects.blastDevices(source, center, radius, maximum);
        context.world().spawnParticle(Particle.EXPLOSION, center, 2);
        context.world()
                .playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1, kind == RPG7 ? .8f : 1.2f);
        List<Player> targets = new ArrayList<>(context.players());
        // Apply self damage last: dying in the same blast must not suppress its enemy hits.
        targets.sort(Comparator.comparing(player -> player.equals(source)));
        for (Player target : targets) {
            if (!context.active(target)
                    || (!target.equals(source) && context.friendly(source, target))) continue;
            double distance = target.getLocation().add(0, .8, 0).distance(center);
            if (distance >= radius || !clearLine(center, target.getLocation().add(0, .8, 0)))
                continue;
            // Self damage is intentional, but is still routed through the session's protection
            // checks.
            context.damage(target, source, Math.max(2, maximum * (1 - distance / radius)), center);
        }
        if (context.active(source)) destroyAround(center, kind == RPG7 ? 2 : 1.5);
    }

    void destroyAround(Location center, double radius) {
        int r = (int) Math.ceil(radius);
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > radius * radius) continue;
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (context.inside(block.getLocation())
                            && (isTemporaryCover(block) || context.destructible(block)))
                        context.breakBlock(block);
                }
    }

    private void revealBySonar(Player enemy) {
        reveal(enemy);
        Revealed old = revealed.get(enemy.getUniqueId());
        revealed.put(
                enemy.getUniqueId(),
                new Revealed(
                        enemy,
                        old == null
                                ? enemy.isGlowing() && !context.carrier(enemy)
                                : old.originalGlow(),
                        context.tick() + 100));
        enemy.setGlowing(true);
    }

    boolean isSonarRevealed(Player player) {
        Revealed effect = revealed.get(player.getUniqueId());
        return effect != null && context.tick() < effect.expires() && context.active(player);
    }

    private void restoreGlow(UUID id) {
        Revealed effect = revealed.remove(id);
        if (effect != null
                && FinalsGadgetState.shouldRestoreGlow(
                        effect.originalGlow(),
                        effect.player().isGlowing(),
                        context.carrier(effect.player()))) effect.player().setGlowing(false);
    }

    private void tickBeam(Beam beam) {
        Player player = beam.player(), target = owner(beam.target());
        UUID id = player.getUniqueId();
        if (context.tick() >= beam.expires()
                || !context.canAct(player)
                || context.gadgetsBlocked(player)
                || !usingHealingBeam(player)
                || target == null
                || !context.active(target)
                || !context.friendly(player, target)
                || !player.getWorld().equals(target.getWorld())
                || player.getLocation().distanceSquared(target.getLocation()) > 100
                || !player.hasLineOfSight(target)) {
            beams.remove(id);
            resources.beamTick(id, false, context.tick());
            return;
        }
        boolean healing = target.getHealth() < maxHealth(target);
        if (!resources.beamTick(id, healing, context.tick())) return;
        if (resources.beamOverheated(id)) {
            beams.remove(id);
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, .6f, 1.4f);
            player.sendActionBar(beamHud(player));
        }
        if (context.tick() % 4 != 0) return;
        target.setHealth(
                Math.min(
                        maxHealth(target), target.getHealth() + (target.equals(player) ? .3 : .7)));
        Location start = player.getEyeLocation(), end = target.getLocation().add(0, 1, 0);
        Vector path = end.toVector().subtract(start.toVector());
        int segments = Math.max(1, (int) (path.length() * 3));
        for (int i = 0; i <= segments; i++)
            context.world()
                    .spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            start.clone().add(path.clone().multiply((double) i / segments)),
                            1,
                            0,
                            0,
                            0,
                            0);
    }

    private void tickCharge(Charge charge) {
        Player player = charge.player();
        if (!context.canAct(player)) {
            charges.remove(player.getUniqueId());
            return;
        }
        boolean last = context.tick() >= charge.expires();
        Location position = player.getLocation();
        if (!context.inside(position.clone().add(forward(player, 1.7, 0)))) {
            player.setVelocity(new Vector());
            charges.remove(player.getUniqueId());
            return;
        }
        if (!last)
            player.setVelocity(forward(player, .95, Math.max(-.3, player.getVelocity().getY())));
        destroyAround(position.clone().add(0, 1, 0).add(forward(player, 1.1, 0)), 1.4);
        double radius = last ? 3 : 1.9;
        for (Player enemy : List.copyOf(context.players())) {
            if (!context.active(enemy)
                    || context.friendly(player, enemy)
                    || charge.hit().contains(enemy.getUniqueId())) continue;
            if (enemy.getLocation().distanceSquared(position) > radius * radius
                    || !player.hasLineOfSight(enemy)) continue;
            charge.hit().add(enemy.getUniqueId());
            context.damage(enemy, player, last ? 9 : 7);
            if (context.active(enemy)) {
                Vector away = enemy.getLocation().toVector().subtract(position.toVector()).setY(0);
                if (away.lengthSquared() > .01)
                    enemy.setVelocity(away.normalize().multiply(.65).setY(.4));
            }
        }
        context.world()
                .spawnParticle(
                        Particle.CLOUD,
                        position,
                        last ? 25 : 5,
                        last ? 1.5 : .3,
                        .2,
                        last ? 1.5 : .3,
                        .03);
        if (last) {
            charges.remove(player.getUniqueId());
            player.setVelocity(player.getVelocity().multiply(.3));
        }
    }

    private void tickPad(Pad pad) {
        Location center = pad.block().getLocation().add(.5, 1, .5);
        for (Player player : context.players()) {
            if (!context.canAct(player) || launchedUntil.containsKey(player.getUniqueId()))
                continue;
            Location at = player.getLocation();
            if (Math.abs(at.getX() - center.getX()) > 1
                    || Math.abs(at.getZ() - center.getZ()) > 1
                    || at.getY() < center.getY() - .1
                    || at.getY() > center.getY() + .65) continue;
            Vector launch = forward(player, .42, JUMP_PAD_SPEED);
            if (!context.inside(at.clone().add(launch.clone().multiply(3)))) continue;
            player.setFallDistance(0);
            player.setVelocity(launch);
            launchedUntil.put(player.getUniqueId(), context.tick() + 20);
            context.world().spawnParticle(Particle.CLOUD, center, 12, .4, .1, .4, .02);
            player.playSound(center, Sound.BLOCK_SLIME_BLOCK_FALL, .8f, 1.3f);
        }
    }

    private void drawDome(Dome dome) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.AQUA, 1.2f);
        for (int ring = 0; ring <= 3; ring++) {
            double elevation = Math.PI * ring / 6;
            double radius = DOME_RADIUS * Math.cos(elevation),
                    y = DOME_RADIUS * Math.sin(elevation);
            for (int i = 0; i < 24; i++) {
                double angle = 2 * Math.PI * i / 24;
                context.world()
                        .spawnParticle(
                                Particle.DUST,
                                dome.center()
                                        .clone()
                                        .add(radius * Math.cos(angle), y, radius * Math.sin(angle)),
                                1,
                                0,
                                0,
                                0,
                                0,
                                dust);
            }
        }
    }

    boolean isTemporaryCover(Block block) {
        return cover.containsKey(block);
    }

    boolean breakTemporary(Block block) {
        Cover owned = cover.remove(block);
        if (owned == null) return false;
        // Never overwrite a later edit, even when a third party changed this block.
        if (block.getBlockData().equals(owned.placed()))
            block.setBlockData(owned.previous(), false);
        return true;
    }

    boolean weaponBlocked(Player player) {
        return effects.weaponBlocked(player);
    }

    double deviceDistance(Player source, Location eye, Vector direction, double reach) {
        return effects.deviceDistance(source, eye, direction, reach);
    }

    boolean attackDevice(
            Player source, Location eye, Vector direction, double reach, double damage) {
        return effects.attackDevice(source, eye, direction, reach, damage);
    }

    void forget(UUID id) {
        effects.forget(id);
        resources.forget(id);
        equipped.remove(id);
        Hidden effect = hidden.get(id);
        if (effect != null) reveal(effect.player());
        // A reconnect can create a new viewer object with the same UUID. Release the old
        // plugin visibility registration so that updateHidden can hide the new connection.
        for (Hidden other : hidden.values()) {
            for (Player viewer : List.copyOf(other.viewers())) {
                if (!viewer.getUniqueId().equals(id)) continue;
                viewer.showPlayer(context.plugin(), other.player());
                other.viewers().remove(viewer);
            }
        }
        restoreGlow(id);
        beams.remove(id);
        beams.entrySet().removeIf(entry -> entry.getValue().target().equals(id));
        charges.remove(id);
        launchedUntil.remove(id);
        for (Ordnance shot : List.copyOf(ordnance)) if (shot.owner.equals(id)) removeOrdnance(shot);
        for (var entry : List.copyOf(cover.entrySet()))
            if (entry.getValue().owner().equals(id)) breakTemporary(entry.getKey());
        pads.removeIf(pad -> pad.owner().equals(id));
        domes.removeIf(dome -> dome.owner().getUniqueId().equals(id));
    }

    void clear() {
        for (Hidden effect : List.copyOf(hidden.values())) reveal(effect.player());
        for (UUID id : List.copyOf(revealed.keySet())) restoreGlow(id);
        for (Ordnance shot : List.copyOf(ordnance)) removeOrdnance(shot);
        for (Block block : List.copyOf(cover.keySet())) breakTemporary(block);
        effects.clear();
        resources.clear();
        equipped.clear();
        beams.clear();
        charges.clear();
        pads.clear();
        domes.clear();
        launchedUntil.clear();
    }
}
