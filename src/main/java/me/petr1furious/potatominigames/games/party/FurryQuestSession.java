package me.petr1furious.potatominigames.games.party;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.hud.PrivatePlayerGlow;
import me.petr1furious.potatominigames.games.party.FurryQuest.Action;
import me.petr1furious.potatominigames.games.party.FurryQuest.Task;
import me.petr1furious.potatominigames.games.party.FurryQuestArena.Site;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class FurryQuestSession extends PartySurvivalSession {
    static final String ID = "furry_quest";

    private static final List<Site> NOTES = List.of(Site.LOW, Site.MID, Site.HIGH);
    private static final List<Site> FILES = List.of(Site.FILE_RED, Site.FILE_BLUE, Site.FILE_YELLOW);
    private static final List<NamedTextColor> COLORS =
            List.of(NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.YELLOW);

    private static final int OUTLINE_TICKS = 40;
    private static final int COMPASS_TICKS = 400;
    private static final int SMOKE_TICKS = 60;
    private static final double SMOKE_RADIUS = 5;

    private enum Perk {
        MORPH(1, Material.FEATHER),
        REVEAL(2, Material.GLOW_INK_SAC),
        COMPASS(3, Material.COMPASS),
        SMOKE(4, Material.INK_SAC),
        SPEED(-1, Material.AIR);

        final int slot;
        final Material item;

        Perk(int slot, Material item) {
            this.slot = slot;
            this.item = item;
        }

        String key() {
            return "perk." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final class Disguise {
        final Player player;
        Animals animal;
        final boolean invisible, silent, collidable;
        final Set<Player> hiddenFrom = new HashSet<>();
        long biteAt, nextGuess;
        long workingSince = -1;
        long revealUntil, compassUntil, perkReadyAt;

        Disguise(Player player, Animals animal) {
            this.player = player;
            this.animal = animal;
            this.invisible = player.isInvisible();
            this.silent = player.isSilent();
            this.collidable = player.isCollidable();
        }
    }

    private final Map<UUID, Disguise> disguises = new HashMap<>();
    private final Map<UUID, Disguise> owners = new HashMap<>();
    private PrivatePlayerGlow outlines;
    private FurryQuestArena arena;
    private FurryQuest quest;
    private boolean disguised;

    FurryQuestSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, ID, context);
    }

    @Override
    void buildArena() {
        quest = new FurryQuest(names.keySet(), random);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        outlines = new PrivatePlayerGlow(services.plugin());
        arena = new FurryQuestArena(world, random);
        arena.build();
    }

    @Override
    boolean prepareArena() {
        if (!disguised) {
            disguised = true;
            for (Player player : alivePlayers()) {
                Animals animal = arena.animal(player.getLocation(), arena.roll(null), false);
                player.hideEntity(services.plugin(), animal);
                Disguise disguise = new Disguise(player, animal);
                disguises.put(player.getUniqueId(), disguise);
                owners.put(animal.getUniqueId(), disguise);
                player.setInvisible(true);
                player.setSilent(true);
                player.setCollidable(false);
            }
        }
        updateVisibility();
        return true;
    }

    private void updateVisibility() {
        List<Player> viewers = world.getPlayers();
        for (Disguise disguise : disguises.values()) {
            for (Player viewer : List.copyOf(disguise.hiddenFrom)) {
                if (!viewers.contains(viewer)) {
                    viewer.showPlayer(services.plugin(), disguise.player);
                    disguise.hiddenFrom.remove(viewer);
                }
            }
            for (Player viewer : viewers) {
                if (!viewer.equals(disguise.player) && disguise.hiddenFrom.add(viewer))
                    viewer.hidePlayer(services.plugin(), disguise.player);
            }
        }
    }

    @Override
    Location spawn(int index, int count) {
        return arena.meadow();
    }
    @Override
    Location viewpoint() {
        return new Location(world, .5, FurryQuestArena.FLOOR + 22, .5, 0, 90);
    }
    @Override
    double outHeight() {
        return FurryQuestArena.FLOOR - 3;
    }
    @Override
    long arenaTime() {
        return 6000;
    }
    @Override
    int durationSeconds() {
        return FurryQuest.SECONDS;
    }
    @Override
    boolean survivalAutoFinish() {
        return false;
    }
    @Override
    boolean podiumAwards() {
        return true;
    }

    @Override
    Component instructions() {
        return message("instructions", FurryQuest.TASK_POINTS);
    }

    @Override
    void playTick(long elapsed) {
        if (elapsed >= FurryQuest.SECONDS * 20L) {
            for (var place : roundResult().places()) {
                for (UUID id : place) say(message("result", names.get(id), quest.score(id)));
            }
            finish(List.of());
            return;
        }
        arena.tick(elapsed);
        if (elapsed % 5 == 0)
            updateVisibility();
        for (Disguise d : disguises.values()) {
            Player player = d.player;
            if (!canPlay(player))
                continue;
            Location at = player.getLocation();
            at.setPitch(0);
            Location previous = d.animal.getLocation();
            if (previous.distanceSquared(at) > 0.000001 || previous.getYaw() != at.getYaw())
                d.animal.teleport(at);
            tickPerks(d, elapsed);
            UUID id = player.getUniqueId();
            if (quest.task(id) == Task.STASH && quest.step(id) == 1 && player.isSprinting() && elapsed % 5 == 0)
                world.spawnParticle(Particle.CLOUD, at.clone().add(0, .3, 0), 5, .18, .1, .18, .025);
            if (elapsed % 16 == 0)
                arena.guide(player, arena.block(target(id)));
            Action action = quest.objective(id).action();
            if (action == Action.SNEAK || d.workingSince >= 0) {
                boolean nearby = at.distanceSquared(arena.block(target(id)).getLocation().add(.5, .5, .5)) <= 6.25;
                if (!nearby || (action == Action.SNEAK && !player.isSneaking()))
                    d.workingSince = -1;
                else if (d.workingSince < 0)
                    d.workingSince = elapsed;
                else if (elapsed - d.workingSince >= 30)
                    advance(player);
            }
            if (d.biteAt > 0 && elapsed == d.biteAt) {
                player.playSound(at, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1, 1);
                player.sendActionBar(message("bite"));
            }
        }
    }

    @Override
    void beginPlay() {
        for (Player player : alivePlayers()) {
            assign(player);
            equip(player);
        }
    }

    private Site target(UUID id) {
        if (quest.task(id) == Task.CHIMES)
            return Site.MID;
        return quest.objective(id).site();
    }

    private Component roleLabel(UUID id) {
        return message("role." + quest.role(id).name().toLowerCase(java.util.Locale.ROOT));
    }

    private void assign(Player player) {
        UUID id = player.getUniqueId();
        player.sendMessage(message("assignment", roleLabel(id),
                message("goal." + quest.role(id).name().toLowerCase(java.util.Locale.ROOT))));
    }

    private void equip(Player player) {
        Material item = quest.objective(player.getUniqueId()).item();
        player.getInventory().setItem(0, item == Material.AIR ? null : new ItemStack(item));
        player.getInventory().setHeldItemSlot(0);
    }

    @Override
    Component status() {
        return message("time", Math.max(0, FurryQuest.SECONDS - elapsed() / 20));
    }

    @Override
    List<Component> sidebarLines(Player player, Component line) {
        UUID id = player.getUniqueId();
        var lines = new ArrayList<>(List.of(line.color(NamedTextColor.YELLOW)));
        if (quest.task(id) == null)
            return lines;
        lines.add(message("score", quest.score(id)));
        Disguise d = disguises.get(id);
        if (d != null)
            lines.add(message("form", Component.translatable(d.animal.getType().translationKey())));
        lines.add(message("chapter", roleLabel(id), quest.chapter(id)));
        lines.add(hint(id));
        long wait = quest.waitTicks(id, elapsed());
        lines.add(wait == 0 ? message("ready") : message("wait", (wait + 19) / 20));
        return lines;
    }

    private Component hint(UUID id) {
        return message("task." + quest.task(id).name().toLowerCase(java.util.Locale.ROOT) + "." + quest.step(id));
    }

    private void advance(Player player) {
        UUID id = player.getUniqueId();
        Task previousJob = quest.task(id);
        var previous = quest.role(id);
        disguises.get(id).workingSince = -1;
        boolean complete = quest.advance(id);
        equip(player);
        player.sendActionBar(
                complete ? message("complete", FurryQuest.TASK_POINTS).color(NamedTextColor.GREEN) : hint(id));
        if (previousJob == Task.EVIDENCE && quest.task(id) == Task.REPORT)
            player.sendMessage(message("evidence", color(quest.evidence(id))));
        if (previousJob != Task.CHIMES && quest.task(id) == Task.CHIMES)
            showMelody(player);
        if (quest.role(id) != previous) {
            assign(player);
            player.sendActionBar(message("fresh", roleLabel(id)).color(NamedTextColor.GOLD));
            grantPerk(disguises.get(id));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .6f, complete ? 1.5f : 1f);
    }

    private Component color(int index) {
        return message("color." + index).color(COLORS.get(index));
    }

    private void showMelody(Player player) {
        var tune = quest.melody(player.getUniqueId());
        player.sendActionBar(message("song", color(NOTES.indexOf(tune.get(0))), color(NOTES.indexOf(tune.get(1))),
                color(NOTES.indexOf(tune.get(2)))));
    }

    private boolean canPlay(Player player) {
        return playing() && alive(player) && world.equals(player.getWorld());
    }

    private boolean mining(UUID id) {
        Action action = quest.objective(id).action();
        return action == Action.CHOP || action == Action.DIG;
    }

    @Override
    protected void handleBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (canPlay(player) && mining(id) && event.getBlock().equals(arena.block(target(id)))
                && event.getBlock().getType() == target(id).material) {
            arena.temporary(event.getBlock(), Material.AIR, elapsed());
            advance(player);
        }
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Block b = event.getClickedBlock();
        // Allow the native mining animation and break event for the marked log.
        if (canPlay(player) && event.getAction().isLeftClick() && mining(id) && arena.block(target(id)).equals(b))
            return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick() || !canPlay(player))
            return;
        if (b != null && arena.claim(b)) {
            grantPerk(disguises.get(id));
            return;
        }
        Disguise d = disguises.get(id);
        if (usePerk(d, null))
            return;
        Site site = target(id);
        Block marker = arena.block(site);
        Task job = quest.task(id);
        if (job == Task.REPORT && quest.step(id) == 1) {
            if (b == null || elapsed() < d.nextGuess)
                return;
            for (int i = 0; i < FILES.size(); i++) {
                if (!b.equals(arena.block(FILES.get(i))))
                    continue;
                if (i == quest.evidence(id))
                    advance(player);
                else {
                    d.nextGuess = elapsed() + 40;
                    player.sendActionBar(message("evidence", color(quest.evidence(id))));
                }
                return;
            }
            if (b.equals(marker))
                player.sendActionBar(message("evidence", color(quest.evidence(id))));
            return;
        }
        if (job == Task.CHIMES) {
            if (b == null || elapsed() < d.nextGuess)
                return;
            for (Site note : NOTES) {
                if (!b.equals(arena.block(note)))
                    continue;
                playNote(b, note);
                if (quest.matchesNote(id, note))
                    advance(player);
                else {
                    d.nextGuess = elapsed() + 30;
                    showMelody(player);
                }
                return;
            }
            return;
        }
        Action action = quest.objective(id).action();
        if (action == Action.SIGHT) {
            var ray = player.rayTraceBlocks(20);
            if (ray != null && marker.equals(ray.getHitBlock())
                    && player.getEyeLocation().distanceSquared(marker.getLocation().add(.5, .5, .5)) >= 36)
                advance(player);
            else
                player.sendActionBar(message("aim"));
            return;
        }
        if (action == Action.REEL || action == Action.CAST) {
            if (player.getLocation().distanceSquared(marker.getLocation()) > 49)
                return;
            if (action == Action.REEL) {
                if (elapsed() >= d.biteAt && elapsed() <= d.biteAt + 35) {
                    d.biteAt = 0;
                    advance(player);
                } else {
                    cast(d);
                }
            } else {
                var sight = player.rayTraceBlocks(7, org.bukkit.FluidCollisionMode.ALWAYS);
                if (sight == null || sight.getHitBlock() == null || sight.getHitBlock().getType() != Material.WATER
                        || sight.getHitBlock().getLocation().distanceSquared(marker.getLocation()) > 23)
                    return;
                advance(player);
                cast(d);
            }
            return;
        }
        if (b == null || !(b.equals(marker) || b.equals(marker.getRelative(0, 1, 0))))
            return;
        switch (action) {
            case CHOP, DIG, SNEAK, CAST, REEL, SIGHT -> {
                return;
            }
            case TEND -> {
                if (player.getLocation().distanceSquared(marker.getLocation().add(.5, .5, .5)) <= 6.25
                        && d.workingSince < 0) {
                    d.workingSince = elapsed();
                    player.sendActionBar(message("working"));
                }
                return;
            }
            case BUILD -> arena.temporary(marker.getRelative(0, 1, 0), Material.OAK_FENCE, elapsed());
            case SIGN -> arena.temporary(marker.getRelative(0, 1, 0), Material.OAK_SIGN, elapsed());
            case LIGHT -> arena.temporary(marker.getRelative(0, 1, 0), Material.TORCH, elapsed());
            case HARVEST -> {
                if (marker.getType() != Material.WHEAT)
                    return;
                arena.temporary(marker, Material.AIR, elapsed());
            }
            case SOW -> arena.temporary(marker.getRelative(0, 1, 0), Material.WHEAT, elapsed());
            case NOTE -> playNote(b, site);
            case CLIMB -> {
                if (player.getY() < FurryQuestArena.FLOOR + 4.9)
                    return;
            }
            case USE -> {
                if (site == Site.SWITCH) {
                    var lever = (org.bukkit.block.data.Powerable) marker.getBlockData();
                    lever.setPowered(job == Task.POWER);
                    marker.setBlockData(lever, false);
                    world.playSound(marker.getLocation(), Sound.BLOCK_LEVER_CLICK, .8f, 1f);
                }
            }
        }
        advance(player);
    }

    private void playNote(Block block, Site site) {
        world.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1,
                site == Site.LOW           ? .7f
                        : site == Site.MID ? 1f
                                           : 1.4f);
    }

    private void cast(Disguise d) {
        d.biteAt = elapsed() + 20 + random.nextInt(30);
        world.spawnParticle(
                Particle.SPLASH, arena.block(Site.POND).getLocation().add(.5, 1, .5), 12, .3, .1, .3, .1);
        d.player.sendActionBar(message("cast"));
    }

    private void morph(Disguise d, Location at, EntityType species) {
        UUID before = d.animal.getUniqueId();
        outlines.remove(before);
        owners.remove(before);
        d.animal.remove();
        d.animal = arena.animal(at, species, false);
        owners.put(d.animal.getUniqueId(), d);
        d.player.hideEntity(services.plugin(), d.animal);
    }

    private void grantPerk(Disguise d) {
        Perk perk = Perk.values()[random.nextInt(Perk.values().length)];
        Player player = d.player;
        if (perk == Perk.SPEED) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false));
        } else if (perk == Perk.COMPASS) {
            d.compassUntil = elapsed() + COMPASS_TICKS;
            player.getInventory().setItem(perk.slot, perkItem(player, perk));
            updateCompass(d, elapsed());
        } else {
            var inventory = player.getInventory();
            ItemStack current = inventory.getItem(perk.slot);
            if (current == null || current.isEmpty()) {
                current = perkItem(player, perk);
            } else {
                current.setAmount(current.getAmount() + 1);
            }
            inventory.setItem(perk.slot, current);
        }
        player.sendMessage(message(perk.key()));
    }

    private static ItemStack perkItem(Player player, Perk perk) {
        ItemStack item = new ItemStack(perk.item);
        item.editMeta(meta -> {
            meta.displayName(I18n.render(message(perk.key() + ".item"), player));
            meta.setEnchantmentGlintOverride(true);
        });
        return item;
    }

    private boolean usePerk(Disguise d, EntityType animal) {
        var inventory = d.player.getInventory();
        int slot = inventory.getHeldItemSlot();
        ItemStack held = inventory.getItemInMainHand();
        for (Perk perk : Perk.values()) {
            if (slot != perk.slot || held.getType() != perk.item)
                continue;
            if (perk == Perk.COMPASS || elapsed() < d.perkReadyAt)
                return true;
            d.perkReadyAt = elapsed() + 2;
            if (held.getAmount() == 1) {
                inventory.setItem(slot, null);
            } else {
                held.setAmount(held.getAmount() - 1);
                inventory.setItem(slot, held);
            }
            switch (perk) {
                case MORPH -> {
                    EntityType species = animal == null ? arena.roll(d.animal.getType()) : animal;
                    if (species != d.animal.getType())
                        morph(d, d.player.getLocation(), species);
                    d.player.sendActionBar(
                            message("perk.morphed", Component.translatable(species.translationKey())));
                }
                case REVEAL -> {
                    d.revealUntil = elapsed() + OUTLINE_TICKS;
                    reveal(d);
                }
                case SMOKE -> smoke(d);
                case COMPASS, SPEED -> {
                }
            }
            return true;
        }
        // Paper may emit two right-click events for a single entity interaction.
        return elapsed() < d.perkReadyAt;
    }

    private void reveal(Disguise d) {
        var targets = disguises.values()
                              .stream()
                              .filter(other
                                      -> other != d && canPlay(other.player)
                                              && other.animal.getTrackedBy().contains(d.player))
                              .map(other -> other.animal)
                              .toList();
        outlines.updateTargets(d.player, targets);
    }

    private void smoke(Disguise d) {
        Location at = d.player.getLocation();
        world.spawnParticle(Particle.CLOUD, at.clone().add(0, 1, 0), 32, 1.3, .55, 1.3, .03);
        for (Disguise other : disguises.values()) {
            if (other == d || !canPlay(other.player)
                    || other.player.getLocation().distanceSquared(at) > SMOKE_RADIUS * SMOKE_RADIUS)
                continue;
            other.player.addPotionEffect(
                    new PotionEffect(PotionEffectType.BLINDNESS, SMOKE_TICKS, 0, false, false));
            other.player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOWNESS, SMOKE_TICKS, 1, false, false));
        }
    }

    private void tickPerks(Disguise d, long tick) {
        if (d.revealUntil > 0) {
            if (tick >= d.revealUntil) {
                d.revealUntil = 0;
                outlines.remove(d.player.getUniqueId());
            } else if (tick % 4 == 0) {
                reveal(d);
            }
        }
        if (d.compassUntil > 0) {
            if (tick >= d.compassUntil) {
                d.compassUntil = 0;
                d.player.getInventory().setItem(Perk.COMPASS.slot, null);
            } else if (tick % 5 == 0) {
                updateCompass(d, tick);
            }
        }
    }

    private void updateCompass(Disguise d, long tick) {
        Location origin = d.player.getLocation();
        Disguise nearest = null;
        double closest = Double.POSITIVE_INFINITY;
        for (Disguise other : disguises.values()) {
            if (other == d || !canPlay(other.player))
                continue;
            double distance = other.player.getLocation().distanceSquared(origin);
            if (distance >= closest)
                continue;
            closest = distance;
            nearest = other;
        }

        var inventory = d.player.getInventory();
        ItemStack item = inventory.getItem(Perk.COMPASS.slot);
        if (item == null || !(item.getItemMeta() instanceof CompassMeta meta))
            return;
        Location target = nearest == null ? null : nearest.player.getLocation().toBlockLocation();
        if (!java.util.Objects.equals(target, meta.getLodestone())) {
            meta.setLodestoneTracked(false);
            meta.setLodestone(target);
            item.setItemMeta(meta);
            inventory.setItem(Perk.COMPASS.slot, item);
        }
        if (inventory.getHeldItemSlot() == Perk.COMPASS.slot) {
            long seconds = (d.compassUntil - tick + 19) / 20;
            d.player.sendActionBar(nearest == null
                            ? message("perk.compass.empty", seconds)
                            : message("perk.compass.target",
                                      Component.translatable(nearest.animal.getType().translationKey()), seconds));
        }
    }

    @Override
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !canPlay(player))
            return;
        var clicked = event.getRightClicked();
        EntityType species = FurryQuestArena.SPECIES.contains(clicked.getType()) ? clicked.getType() : null;
        usePerk(disguises.get(player.getUniqueId()), species);
    }

    @Override
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!canPlay(player) || !(event.getAttacked() instanceof Animals animal) || !animal.isValid()
                || animal.getLocation().distanceSquared(player.getLocation()) > 16
                || !player.hasLineOfSight(animal))
            return;
        Disguise victim = owners.get(animal.getUniqueId());
        if ((victim == null && !arena.wild(animal)) || (victim != null && !canPlay(victim.player)))
            return;
        var hit = quest.hit(player.getUniqueId(),
                victim == null ? animal.getUniqueId() : victim.player.getUniqueId(), elapsed());
        if (hit == FurryQuest.Hit.WAIT)
            return;
        world.spawnParticle(Particle.POOF, animal.getLocation().add(0, .6, 0), 14, .25, .3, .25, .03);
        if (hit == FurryQuest.Hit.MISS) {
            arena.replaceWild(animal);
            player.sendActionBar(message("miss").color(NamedTextColor.RED));
        } else {
            Location dest = arena.meadow();
            teleport(victim.player, dest);
            morph(victim, dest, arena.roll(victim.animal.getType()));
            victim.player.sendActionBar(message("caught").color(NamedTextColor.RED));
            player.sendActionBar(message("kill").color(NamedTextColor.GREEN));
        }
    }

    @Override
    public void onInventoryOpen(InventoryOpenEvent event) {
        event.setCancelled(true);
    }

    @Override
    void playerOut(Player player) {
        quest.withdraw(player.getUniqueId());
        removeDisguise(player.getUniqueId());
    }

    private void removeDisguise(UUID id) {
        Disguise disguise = disguises.remove(id);
        if (disguise == null)
            return;
        outlines.remove(id);
        outlines.remove(disguise.animal.getUniqueId());
        owners.remove(disguise.animal.getUniqueId());
        disguise.animal.remove();
        for (Player viewer : disguise.hiddenFrom) viewer.showPlayer(services.plugin(), disguise.player);
        disguise.player.removePotionEffect(PotionEffectType.SPEED);
        disguise.player.removePotionEffect(PotionEffectType.BLINDNESS);
        disguise.player.removePotionEffect(PotionEffectType.SLOWNESS);
        for (Perk perk : Perk.values()) {
            if (perk.slot >= 0)
                disguise.player.getInventory().setItem(perk.slot, null);
        }
        disguise.player.setInvisible(disguise.invisible);
        disguise.player.setSilent(disguise.silent);
        disguise.player.setCollidable(disguise.collidable);
    }

    @Override
    void cleanup() {
        for (UUID id : List.copyOf(disguises.keySet())) removeDisguise(id);
        if (outlines != null)
            outlines.clear();
        if (arena != null)
            arena.cleanup();
    }

    @Override
    public PartyRoundResult roundResult() {
        return quest == null ? PartyRoundResult.empty() : quest.result(context.eligible());
    }

    private static Component message(String key, Object... args) {
        return I18n.tr("party.rounds.furry_quest." + key, args);
    }
}
