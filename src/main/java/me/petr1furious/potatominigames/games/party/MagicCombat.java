package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Bounded, round-owned spell simulation with destructible terrain inside the arena. */
final class MagicCombat {
    private static final NamespacedKey ICE_ABSORPTION =
            new NamespacedKey("potatominigames", "magic_ice_absorption");

    @FunctionalInterface
    interface Damage {
        boolean hit(Player caster, Player victim, double damage);
    }

    record Aim(Location point, Block block) {}

    private static final class Bolt {
        Player owner;
        final MagicSpell spell;
        Location at;
        Vector velocity;
        double remaining = 96;
        int reflections;

        Bolt(Player owner, MagicSpell spell) {
            this.owner = owner;
            this.spell = spell;
            at = owner.getEyeLocation();
            double speed = spell == MagicSpell.FIREBALL || spell == MagicSpell.FROZEN_ORB ? 2 : 3.2;
            velocity = at.getDirection().normalize().multiply(speed);
        }
    }

    private record Pending(Player owner, MagicSpell spell, Location point, long due) {}

    private record Zone(Player owner, MagicSpell spell, Location point, long born, long until) {}

    private record Temporary(Block block, BlockData original, BlockData placed, long until) {}

    private record Armor(Player owner, double previous, long until) {}

    private record Channel(Player owner, Location at, long due) {}

    private record Footprint(long tick, Location at) {}

    private record Trail(Player owner, long until) {}

    private static final class CleansableEffects {
        private static final List<PotionEffectType> TYPES =
                List.of(
                        PotionEffectType.SLOWNESS,
                        PotionEffectType.POISON,
                        PotionEffectType.WITHER,
                        PotionEffectType.BLINDNESS,
                        PotionEffectType.DARKNESS,
                        PotionEffectType.WEAKNESS,
                        PotionEffectType.NAUSEA,
                        PotionEffectType.MINING_FATIGUE,
                        PotionEffectType.LEVITATION,
                        PotionEffectType.GLOWING);
    }

    private final MagicBattleArena arena;
    private final Predicate<Player> fighting;
    private final Supplier<List<Player>> players;
    private final Map<UUID, MagicMana> mana;
    private final Damage damage;
    private final BiPredicate<Player, Location> teleport;
    private final List<Bolt> bolts = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final Map<MagicBattleArena.Pos, Temporary> temporary = new HashMap<>();
    private final Map<UUID, Long> shields = new HashMap<>(),
            silenced = new HashMap<>(),
            secondWind = new HashMap<>();
    private final Map<UUID, Armor> armor = new HashMap<>();
    private final Map<UUID, Channel> channels = new HashMap<>();
    private final Map<UUID, Trail> trails = new HashMap<>();
    private final Map<UUID, ArrayDeque<Footprint>> history = new HashMap<>();

    MagicCombat(
            MagicBattleArena arena,
            Predicate<Player> fighting,
            Supplier<List<Player>> players,
            Map<UUID, MagicMana> mana,
            Damage damage,
            BiPredicate<Player, Location> teleport) {
        this.arena = arena;
        this.fighting = fighting;
        this.players = players;
        this.mana = mana;
        this.damage = damage;
        this.teleport = teleport;
    }

    static Aim aim(Player caster, double distance) {
        var ray =
                caster.getWorld()
                        .rayTraceBlocks(
                                caster.getEyeLocation(),
                                caster.getEyeLocation().getDirection(),
                                distance,
                                FluidCollisionMode.NEVER,
                                true);
        Location at =
                ray == null
                        ? caster.getEyeLocation()
                                .add(caster.getEyeLocation().getDirection().multiply(distance))
                        : ray.getHitPosition()
                                .toLocation(caster.getWorld())
                                .add(ray.getHitBlockFace().getDirection().multiply(.05));
        return new Aim(at, ray == null ? null : ray.getHitBlock());
    }

    boolean shielded(Player p, long tick) {
        return shields.getOrDefault(p.getUniqueId(), 0L) > tick;
    }

    long silenceRemaining(Player p, long tick) {
        return Math.max(0, silenced.getOrDefault(p.getUniqueId(), 0L) - tick);
    }

    long channelRemaining(Player p, long tick) {
        Channel channel = channels.get(p.getUniqueId());
        return channel == null ? 0 : Math.max(0, channel.due() - tick);
    }

    void interrupt(Player p) {
        if (channels.remove(p.getUniqueId()) != null)
            p.sendActionBar(I18n.tr("party.magic.channel_interrupted"));
    }

    boolean preventDeath(Player p, double incoming, long tick) {
        if (secondWind.getOrDefault(p.getUniqueId(), 0L) <= tick || incoming < p.getHealth())
            return false;
        secondWind.remove(p.getUniqueId());
        p.setAbsorptionAmount(0);
        p.setHealth(6);
        p.setFireTicks(0);
        p.sendMessage(I18n.tr("party.magic.second_wind_saved"));
        ring(p.getLocation().add(0, .8, 0), 1.5, MagicSpell.School.LIFE.color, null);
        return true;
    }

    boolean cast(Player caster, MagicSpell spell, long tick) {
        if (!fighting.test(caster)
                || silenceRemaining(caster, tick) > 0
                || channels.containsKey(caster.getUniqueId())) return false;
        switch (spell) {
            case FIREBOLT,
                    FIREBALL,
                    ICE_LANCE,
                    GALE,
                    STONE_SHOT,
                    RIFT,
                    FROZEN_ORB,
                    LIFE_TETHER,
                    SILENCE -> {
                if (bolts.size() >= 128) return false;
                bolts.add(new Bolt(caster, spell));
            }
            case METEOR, THUNDER, QUAKE, GRAVITY_WELL, CINDER_RAIN, BLIZZARD, UPDRAFT, ERODE -> {
                Aim aim = aim(caster, spell.range());
                if (!arena.inside(aim.point()) || pending.size() >= 96) return false;
                pending.add(
                        new Pending(
                                caster, spell, aim.point().clone(), tick + spell.warningTicks()));
                ring(aim.point(), spell.radius(), spell.school.color, null);
            }
            case WIND_STEP, PHOENIX_STEP -> {
                caster.setVelocity(
                        horizontal(caster.getEyeLocation().getDirection(), caster)
                                .multiply(spell == MagicSpell.WIND_STEP ? 1.35 : 1.15)
                                .setY(.38));
                if (spell == MagicSpell.PHOENIX_STEP) {
                    trails.put(caster.getUniqueId(), new Trail(caster, tick + 12));
                    zone(caster, spell, caster.getLocation(), tick, 60);
                }
            }
            case BLINK -> {
                Location destination = blinkDestination(caster);
                if (destination == null || !teleport.test(caster, destination)) return false;
                caster.setFallDistance(0);
            }
            case SKYHOOK -> {
                Aim aim = aim(caster, 32);
                if (aim.block() == null
                        || !arena.inside(aim.point())
                        || caster.getEyeLocation().distanceSquared(aim.point()) < 9) return false;
                Vector velocity =
                        aim.point()
                                .clone()
                                .subtract(caster.getEyeLocation())
                                .toVector()
                                .normalize()
                                .multiply(1.65);
                velocity.setY(Math.clamp(velocity.getY() + .18, -.3, .9));
                caster.setVelocity(velocity);
                beam(caster.getEyeLocation(), aim.point(), spell.school.color);
            }
            case ICE_WALL -> {
                Aim aim = aim(caster, spell.range());
                if (aim.block() == null || !arena.inside(aim.point())) return false;
                boolean alongX =
                        Math.abs(caster.getLocation().getDirection().getZ())
                                >= Math.abs(caster.getLocation().getDirection().getX());
                int made = 0;
                for (int side = -2; side <= 2; side++)
                    for (int up = 0; up < 3; up++)
                        if (place(
                                aim.point()
                                        .clone()
                                        .add(alongX ? side : 0, up, alongX ? 0 : side)
                                        .getBlock(),
                                Material.BLUE_ICE,
                                tick + 160,
                                false)) made++;
                if (made == 0) return false;
            }
            case BRIDGE -> {
                Vector forward = horizontal(caster.getEyeLocation().getDirection(), caster);
                int made = 0;
                for (int i = 0; i < 24 && made < 12; i++) {
                    Block block =
                            caster.getLocation()
                                    .clone()
                                    .add(forward.clone().multiply(i))
                                    .subtract(0, 1, 0)
                                    .getBlock();
                    if (!arena.canBuild(block)) break;
                    if (place(block, Material.OAK_PLANKS, tick + 400, true)) made++;
                }
                if (made == 0) return false;
            }
            case STONE_DOME -> {
                Location base = caster.getLocation().getBlock().getLocation();
                int made = 0;
                for (int x = -2; x <= 2; x++)
                    for (int z = -2; z <= 2; z++)
                        for (int y = 0; y <= 3; y++) {
                            if ((Math.abs(x) == 2 || Math.abs(z) == 2) && y < 3
                                    || y == 3 && x * x + z * z <= 5)
                                if (place(
                                        base.clone().add(x, y, z).getBlock(),
                                        Material.COBBLESTONE,
                                        tick + 100,
                                        false)) made++;
                        }
                if (made == 0) return false;
            }
            case PILLAR -> {
                if (!pillar(caster, tick)) return false;
            }
            case FROST_NOVA -> {
                ring(caster.getLocation(), 6, spell.school.color, null);
                for (Player p : players.get())
                    if (enemy(caster, p)
                            && near(p, caster.getLocation(), 6)
                            && caster.hasLineOfSight(p))
                        if (damage.hit(caster, p, 6)) effect(p, PotionEffectType.SLOWNESS, 40, 2);
                caster.setFireTicks(0);
            }
            case FLAME_CONE, SHATTER -> {
                Location eye = caster.getEyeLocation();
                Vector forward = eye.getDirection();
                for (Player p : players.get())
                    if (enemy(caster, p)
                            && inCone(
                                    eye, forward, p, spell == MagicSpell.FLAME_CONE ? 8 : 9, .7)) {
                        boolean frozen = p.hasPotionEffect(PotionEffectType.SLOWNESS);
                        if (damage.hit(
                                caster, p, spell == MagicSpell.FLAME_CONE ? 7 : frozen ? 11 : 4)) {
                            if (spell == MagicSpell.FLAME_CONE)
                                p.setFireTicks(Math.max(p.getFireTicks(), 80));
                            else if (frozen) p.removePotionEffect(PotionEffectType.SLOWNESS);
                        }
                        beam(eye, p.getLocation().add(0, 1, 0), spell.school.color);
                    }
                cone(eye, forward, spell.school.color);
            }
            case COMBUSTION -> {
                List<Player> victims =
                        players.get().stream()
                                .filter(p ->
                                        enemy(caster, p)
                                                && near(p, caster.getLocation(), 10)
                                                && p.getFireTicks() > 0
                                                && caster.hasLineOfSight(p))
                                .toList();
                if (victims.isEmpty()) return false;
                for (Player p : victims)
                    if (damage.hit(caster, p, 10)) {
                        p.setFireTicks(0);
                        arena.world()
                                .spawnParticle(Particle.EXPLOSION, p.getLocation().add(0, 1, 0), 1);
                    }
            }
            case ICE_ARMOR -> {
                if (caster.getAbsorptionAmount() >= 6) return false;
                var capacity = caster.getAttribute(Attribute.MAX_ABSORPTION);
                if (capacity == null) return false;
                capacity.removeModifier(ICE_ABSORPTION);
                capacity.addTransientModifier(
                        new AttributeModifier(
                                ICE_ABSORPTION, 6, AttributeModifier.Operation.ADD_NUMBER));
                armor.put(
                        caster.getUniqueId(),
                        new Armor(caster, caster.getAbsorptionAmount(), tick + 100));
                caster.setAbsorptionAmount(6);
            }
            case CHAIN_LIGHTNING -> {
                if (!chain(caster)) return false;
            }
            case DISPEL -> dispel(caster);
            case FAULT_LINE -> fault(caster);
            case MEND -> {
                if (caster.getHealth() >= 20) return false;
                heal(caster, 6);
            }
            case AEGIS -> shields.put(caster.getUniqueId(), tick + 60);
            case SANCTUARY -> zone(caster, spell, caster.getLocation(), tick, 120);
            case PURIFY -> {
                var negative = CleansableEffects.TYPES;
                if (caster.getFireTicks() <= 0
                        && caster.getFreezeTicks() <= 0
                        && negative.stream().noneMatch(caster::hasPotionEffect)) return false;
                negative.forEach(caster::removePotionEffect);
                caster.setFireTicks(0);
                caster.setFreezeTicks(0);
                heal(caster, 2);
            }
            case SECOND_WIND -> secondWind.put(caster.getUniqueId(), tick + 160);
            case MANA_CHANNEL -> {
                if (mana.get(caster.getUniqueId()).mana() > 65) return false;
                channels.put(
                        caster.getUniqueId(), new Channel(caster, caster.getLocation(), tick + 40));
            }
            case SWAP -> {
                if (!swap(caster)) return false;
            }
            case REWIND -> {
                var footprints = history.get(caster.getUniqueId());
                if (footprints == null
                        || footprints.isEmpty()
                        || tick - footprints.getFirst().tick() < 60) return false;
                Location at = footprints.getFirst().at().clone();
                if (!validDestination(at, caster) || !teleport.test(caster, at)) return false;
                caster.setFallDistance(0);
                ring(at, .8, spell.school.color, null);
            }
            case DARK_VEIL -> {
                zone(caster, spell, caster.getLocation(), tick, 100);
                effect(caster, PotionEffectType.INVISIBILITY, 100, 0);
            }
        }
        caster.playSound(caster.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .7f, 1.1f);
        return true;
    }

    private void zone(Player p, MagicSpell spell, Location at, long tick, int duration) {
        zones.add(new Zone(p, spell, at.clone(), tick, tick + duration));
    }

    private Location blinkDestination(Player caster) {
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection();
        for (double distance = 10; distance >= 1; distance -= .5) {
            if (caster.getWorld()
                            .rayTraceBlocks(
                                    eye, direction, distance, FluidCollisionMode.NEVER, true)
                    != null) continue;
            Location feet =
                    eye.clone()
                            .add(direction.clone().multiply(distance))
                            .subtract(0, caster.getEyeHeight(), 0);
            if (validDestination(feet, caster)) return feet;
        }
        return null;
    }

    private boolean validDestination(Location at, Player caster) {
        if (!arena.inside(at) || !fits(at) || !arena.world().getWorldBorder().isInside(at))
            return false;
        return !occupied(at, players.get(), caster);
    }

    static boolean occupied(Location feet, Iterable<Player> players, Player... ignored) {
        BoundingBox destination = bodyAt(feet);
        nextPlayer:
        for (Player player : players) {
            for (Player exception : ignored) if (player == exception) continue nextPlayer;
            if (java.util.Objects.equals(player.getWorld(), feet.getWorld())
                    && player.getBoundingBox().overlaps(destination)) return true;
        }
        return false;
    }

    private static BoundingBox bodyAt(Location feet) {
        return new BoundingBox(
                feet.getX() - .3,
                feet.getY(),
                feet.getZ() - .3,
                feet.getX() + .3,
                feet.getY() + 1.8,
                feet.getZ() + .3);
    }

    private boolean swap(Player caster) {
        Player victim = target(caster, 24);
        if (victim == null) return false;
        Location from = caster.getLocation().clone(), to = victim.getLocation().clone();
        // Grounded endpoints prevent trading a safe island for a deliberate fall into the void.
        if (!arena.inside(from)
                || !arena.inside(to)
                || !fits(from)
                || !fits(to)
                || !arena.world().getWorldBorder().isInside(from)
                || !arena.world().getWorldBorder().isInside(to)
                || !from.clone().subtract(0, .2, 0).getBlock().getType().isSolid()
                || !to.clone().subtract(0, .2, 0).getBlock().getType().isSolid()) return false;
        if (occupied(from, players.get(), caster, victim)
                || occupied(to, players.get(), caster, victim)) return false;
        if (!teleport.test(caster, to)) return false;
        if (!teleport.test(victim, from)) {
            teleport.test(caster, from);
            return false;
        }
        caster.setFallDistance(0);
        victim.setFallDistance(0);
        beam(from.clone().add(0, 1, 0), to.clone().add(0, 1, 0), MagicSpell.School.VOID.color);
        return true;
    }

    private boolean pillar(Player caster, long tick) {
        Location start = caster.getLocation().clone(), top = start.clone().add(0, 3, 0);
        if (!validDestination(top, caster)) return false;
        List<Block> planned = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            Block block = start.clone().add(0, y, 0).getBlock();
            if (!arena.canBuild(block) || !block.getType().isAir()) return false;
            BoundingBox box =
                    new BoundingBox(
                            block.getX(),
                            block.getY(),
                            block.getZ(),
                            block.getX() + 1,
                            block.getY() + 1,
                            block.getZ() + 1);
            for (Player p : players.get())
                if (p != caster && p.getBoundingBox().overlaps(box)) return false;
            planned.add(block);
        }
        if (!teleport.test(caster, top)) return false;
        for (Block block : planned) place(block, Material.COBBLESTONE, tick + 160, true);
        caster.setFallDistance(0);
        return true;
    }

    private boolean place(Block block, Material material, long until, boolean floor) {
        if (!arena.canBuild(block) || !block.getType().isAir()) return false;
        for (Player p : players.get())
            if (p.getBoundingBox()
                            .overlaps(
                                    new BoundingBox(
                                            block.getX(),
                                            block.getY(),
                                            block.getZ(),
                                            block.getX() + 1,
                                            block.getY() + 1,
                                            block.getZ() + 1))
                    || !floor
                            && p.getLocation().distanceSquared(block.getLocation().add(.5, .5, .5))
                                    < 1.5) return false;
        BlockData original = block.getBlockData(), data = material.createBlockData();
        block.setBlockData(data, false);
        temporary.put(MagicBattleArena.Pos.of(block), new Temporary(block, original, data, until));
        return true;
    }

    private Player target(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection();
        var wall =
                arena.world().rayTraceBlocks(eye, direction, range, FluidCollisionMode.NEVER, true);
        double nearest = wall == null ? range : wall.getHitPosition().distance(eye.toVector());
        Player result = null;
        for (Player p : players.get())
            if (enemy(caster, p)) {
                var hit =
                        p.getBoundingBox().expand(.2).rayTrace(eye.toVector(), direction, nearest);
                if (hit != null) {
                    double distance = hit.getHitPosition().distance(eye.toVector());
                    if (distance < nearest) {
                        result = p;
                        nearest = distance;
                    }
                }
            }
        return result;
    }

    private boolean chain(Player caster) {
        Player first = target(caster, 36);
        if (first == null) return false;
        Set<UUID> visited = new HashSet<>();
        Player next = first;
        Location previous = caster.getEyeLocation();
        for (int jump = 0; jump < 3 && next != null; jump++) {
            Player current = next;
            visited.add(current.getUniqueId());
            beam(previous, current.getLocation().add(0, 1, 0), MagicSpell.School.STORM.color);
            damage.hit(caster, current, 8 - jump * 2);
            previous = current.getLocation().add(0, 1, 0);
            next =
                    players.get().stream()
                            .filter(p ->
                                    enemy(caster, p)
                                            && !visited.contains(p.getUniqueId())
                                            && near(p, current.getLocation(), 8)
                                            && clearLine(
                                                    current.getLocation().add(0, 1, 0),
                                                    p.getLocation().add(0, 1, 0)))
                            .min(
                                    Comparator.comparingDouble(p ->
                                            p.getLocation().distanceSquared(current.getLocation())))
                            .orElse(null);
        }
        return true;
    }

    private void dispel(Player caster) {
        Location at = caster.getLocation();
        bolts.removeIf(b ->
                b.owner != caster
                        && b.at.distanceSquared(at) <= 49
                        && clearLine(caster.getEyeLocation(), b.at));
        pending.removeIf(p ->
                p.owner() != caster
                        && p.point().distanceSquared(at) <= 49
                        && clearLine(caster.getEyeLocation(), p.point()));
        zones.removeIf(z ->
                z.owner() != caster
                        && z.point().distanceSquared(at) <= 49
                        && clearLine(caster.getEyeLocation(), z.point()));
        ring(at, 7, MagicSpell.School.STORM.color, null);
        for (Player p : players.get())
            if (enemy(caster, p) && near(p, at, 5) && caster.hasLineOfSight(p))
                if (damage.hit(caster, p, 2))
                    p.setVelocity(
                            horizontal(p.getLocation().subtract(at).toVector(), caster)
                                    .multiply(.8)
                                    .setY(.25));
    }

    private void fault(Player caster) {
        Location from = caster.getLocation().add(0, .5, 0);
        Vector direction = horizontal(caster.getEyeLocation().getDirection(), caster);
        var wall =
                arena.world().rayTraceBlocks(from, direction, 20, FluidCollisionMode.NEVER, true);
        double range = wall == null ? 20 : wall.getHitPosition().distance(from.toVector());
        beam(
                from,
                from.clone().add(direction.clone().multiply(range)),
                MagicSpell.School.EARTH.color);
        for (Player p : players.get())
            if (enemy(caster, p)
                    && p.getBoundingBox()
                                    .expand(.65, .8, .65)
                                    .rayTrace(from.toVector(), direction, range)
                            != null)
                if (damage.hit(caster, p, 8)) p.setVelocity(p.getVelocity().setY(.65));
    }

    void tick(long tick) {
        for (Player p : players.get())
            if (fighting.test(p)) {
                var footprints = history.computeIfAbsent(p.getUniqueId(), id -> new ArrayDeque<>());
                footprints.addLast(new Footprint(tick, p.getLocation().clone()));
                while (tick - footprints.getFirst().tick() > 60) footprints.removeFirst();
            }
        for (Channel channel : List.copyOf(channels.values())) {
            Player p = channel.owner();
            if (!fighting.test(p)
                    || !p.getWorld().equals(channel.at().getWorld())
                    || p.getLocation().distanceSquared(channel.at()) > .49) {
                interrupt(p);
                continue;
            }
            if (tick >= channel.due()) {
                channels.remove(p.getUniqueId());
                mana.get(p.getUniqueId()).restore(48);
                p.sendActionBar(I18n.tr("party.magic.channel_complete"));
            } else if (tick % 5 == 0) ring(p.getLocation(), .9, MagicSpell.School.LIFE.color, null);
        }
        for (Trail trail : List.copyOf(trails.values())) {
            if (!fighting.test(trail.owner()) || tick > trail.until()) {
                trails.remove(trail.owner().getUniqueId());
                continue;
            }
            if (tick % 3 == 0)
                zone(trail.owner(), MagicSpell.PHOENIX_STEP, trail.owner().getLocation(), tick, 60);
        }
        for (Bolt bolt : List.copyOf(bolts)) {
            if (!bolts.contains(bolt)) continue;
            if (!fighting.test(bolt.owner)) {
                bolts.remove(bolt);
                continue;
            }
            advance(bolt, tick);
        }
        for (Pending cast : List.copyOf(pending)) {
            if (!pending.contains(cast)) continue;
            if (!fighting.test(cast.owner())) {
                pending.remove(cast);
                continue;
            }
            if (tick % 4 == 0) {
                ring(cast.point(), cast.spell().radius(), cast.spell().school.color, null);
                if (cast.spell() == MagicSpell.METEOR)
                    arena.world()
                            .spawnParticle(
                                    Particle.FLAME,
                                    cast.point()
                                            .clone()
                                            .add(0, Math.max(0, (cast.due() - tick) * .35), 0),
                                    10,
                                    .25,
                                    .25,
                                    .25,
                                    .02);
            }
            if (tick >= cast.due()) {
                pending.remove(cast);
                switch (cast.spell()) {
                    case GRAVITY_WELL -> zone(cast.owner(), cast.spell(), cast.point(), tick, 60);
                    case CINDER_RAIN, BLIZZARD ->
                            zone(cast.owner(), cast.spell(), cast.point(), tick, 100);
                    case UPDRAFT -> zone(cast.owner(), cast.spell(), cast.point(), tick, 80);
                    case ERODE -> {
                        destroyTerrain(cast.point(), 4);
                        ring(cast.point(), 4, cast.spell().school.color, null);
                    }
                    default -> {
                        if (cast.spell() == MagicSpell.THUNDER)
                            arena.world().strikeLightningEffect(cast.point());
                        blast(cast.owner(), cast.spell(), cast.point());
                    }
                }
            }
        }
        for (Zone zone : List.copyOf(zones)) {
            if (!zones.contains(zone)) continue;
            if (!fighting.test(zone.owner()) || tick > zone.until()) {
                zones.remove(zone);
                continue;
            }
            tickZone(zone, tick);
        }
        for (var pos : List.copyOf(temporary.keySet())) {
            Temporary block = temporary.get(pos);
            if (tick >= block.until()) {
                restore(block);
                temporary.remove(pos);
            }
        }
        for (var id : List.copyOf(armor.keySet()))
            if (tick >= armor.get(id).until()) {
                restoreArmor(armor.remove(id));
            }
        shields.entrySet().removeIf(e -> tick >= e.getValue());
        silenced.entrySet().removeIf(e -> tick >= e.getValue());
        secondWind.entrySet().removeIf(e -> tick >= e.getValue());
        if (tick % 5 == 0)
            for (Player p : players.get()) {
                if (shielded(p, tick))
                    ring(p.getLocation().add(0, .8, 0), .85, MagicSpell.School.LIFE.color, null);
                if (secondWind.containsKey(p.getUniqueId()))
                    p.getWorld()
                            .spawnParticle(
                                    Particle.HAPPY_VILLAGER,
                                    p.getLocation().add(0, 1.2, 0),
                                    2,
                                    .4,
                                    .5,
                                    .4,
                                    0);
            }
    }

    private void tickZone(Zone zone, long tick) {
        MagicSpell spell = zone.spell();
        boolean pulse = tick > zone.born() && (tick - zone.born()) % 20 == 0;
        double radius =
                spell == MagicSpell.SANCTUARY
                        ? 4
                        : spell == MagicSpell.PHOENIX_STEP ? 1.8 : spell.radius();
        if (tick % 5 == 0) {
            if (spell == MagicSpell.PHOENIX_STEP)
                arena.world()
                        .spawnParticle(
                                Particle.FLAME,
                                zone.point().clone().add(0, .2, 0),
                                5,
                                .6,
                                .1,
                                .6,
                                .01);
            else ring(zone.point(), radius, spell.school.color, null);
            switch (spell) {
                case BLIZZARD ->
                        arena.world()
                                .spawnParticle(
                                        Particle.SNOWFLAKE,
                                        zone.point().clone().add(0, 2, 0),
                                        12,
                                        2,
                                        1,
                                        2,
                                        .02);
                case CINDER_RAIN ->
                        arena.world()
                                .spawnParticle(
                                        Particle.FLAME,
                                        zone.point().clone().add(0, 3, 0),
                                        10,
                                        2,
                                        1,
                                        2,
                                        .02);
                case UPDRAFT ->
                        arena.world()
                                .spawnParticle(
                                        Particle.CLOUD,
                                        zone.point().clone().add(0, 1, 0),
                                        6,
                                        1,
                                        .8,
                                        1,
                                        .03);
                case DARK_VEIL ->
                        arena.world()
                                .spawnParticle(
                                        Particle.SMOKE,
                                        zone.point().clone().add(0, 1, 0),
                                        16,
                                        2,
                                        .7,
                                        2,
                                        .01);
                default -> {}
            }
        }
        if (spell == MagicSpell.SANCTUARY) {
            if (pulse && near(zone.owner(), zone.point(), radius)) {
                heal(zone.owner(), 1);
                mana.get(zone.owner().getUniqueId()).restore(8);
            }
            return;
        }
        for (Player p : players.get())
            if (fighting.test(p) && near(p, zone.point(), radius) && visible(zone.point(), p)) {
                boolean enemy = enemy(zone.owner(), p);
                if (spell == MagicSpell.UPDRAFT) {
                    Vector velocity = p.getVelocity();
                    velocity.setY(Math.max(velocity.getY(), .32));
                    p.setVelocity(velocity);
                    if (enemy && pulse) damage.hit(zone.owner(), p, 1);
                    continue;
                }
                if (!enemy) continue;
                switch (spell) {
                    case GRAVITY_WELL -> {
                        Vector pull =
                                zone.point()
                                        .clone()
                                        .add(0, .7, 0)
                                        .subtract(p.getLocation())
                                        .toVector();
                        if (pull.lengthSquared() > .1) {
                            Vector velocity =
                                    p.getVelocity()
                                            .add(
                                                    pull.normalize()
                                                            .multiply(
                                                                    shielded(p, tick) ? .04 : .16));
                            double flat = Math.hypot(velocity.getX(), velocity.getZ());
                            if (flat > 1.2) {
                                velocity.setX(velocity.getX() / flat * 1.2);
                                velocity.setZ(velocity.getZ() / flat * 1.2);
                            }
                            velocity.setY(Math.clamp(velocity.getY(), -.25, .35));
                            p.setVelocity(velocity);
                        }
                        if (pulse) damage.hit(zone.owner(), p, 2);
                    }
                    case BLIZZARD -> {
                        effect(p, PotionEffectType.SLOWNESS, 15, 1);
                        if (pulse) damage.hit(zone.owner(), p, 2);
                    }
                    case CINDER_RAIN, PHOENIX_STEP -> {
                        if (pulse && damage.hit(zone.owner(), p, 3))
                            p.setFireTicks(Math.max(p.getFireTicks(), 40));
                    }
                    case DARK_VEIL -> effect(p, PotionEffectType.BLINDNESS, 30, 0);
                    default -> {}
                }
            }
    }

    private void advance(Bolt bolt, long tick) {
        Location from = bolt.at;
        Vector direction = bolt.velocity.clone().normalize();
        double length = Math.min(bolt.velocity.length(), bolt.remaining);
        var wall =
                arena.world()
                        .rayTraceBlocks(from, direction, length, FluidCollisionMode.NEVER, true);
        double distance = wall == null ? length : wall.getHitPosition().distance(from.toVector());
        Player victim = null;
        for (Player p : players.get())
            if (enemy(bolt.owner, p)) {
                var hit =
                        p.getBoundingBox()
                                .expand(.28)
                                .rayTrace(from.toVector(), direction, distance);
                if (hit != null) {
                    double at = hit.getHitPosition().distance(from.toVector());
                    if (at < distance) {
                        victim = p;
                        distance = at;
                    }
                }
            }
        bolt.at = from.clone().add(direction.clone().multiply(distance));
        bolt.remaining -= length;
        arena.world()
                .spawnParticle(
                        Particle.DUST,
                        bolt.at,
                        3,
                        .04,
                        .04,
                        .04,
                        0,
                        new Particle.DustOptions(Color.fromRGB(bolt.spell.school.color), 1.1f));
        if (victim != null && shielded(victim, tick)) {
            if (bolt.reflections++ == 0) {
                bolt.owner = victim;
                bolt.velocity.multiply(-1);
                bolt.at.add(bolt.velocity.clone().normalize().multiply(.8));
            } else bolts.remove(bolt);
            return;
        }
        if (victim != null || wall != null) {
            bolts.remove(bolt);
            if (bolt.spell == MagicSpell.FIREBALL || bolt.spell == MagicSpell.FROZEN_ORB)
                blast(bolt.owner, bolt.spell, bolt.at);
            else if (victim != null) {
                double power =
                        switch (bolt.spell) {
                            case FIREBOLT -> 6;
                            case ICE_LANCE, LIFE_TETHER -> 5;
                            case GALE -> 4;
                            case SILENCE -> 3;
                            default -> 7;
                        };
                if (damage.hit(bolt.owner, victim, power))
                    switch (bolt.spell) {
                        case FIREBOLT -> victim.setFireTicks(Math.max(victim.getFireTicks(), 60));
                        case ICE_LANCE -> effect(victim, PotionEffectType.SLOWNESS, 60, 1);
                        case GALE ->
                                victim.setVelocity(
                                        horizontal(direction, bolt.owner).multiply(1.1).setY(.35));
                        case RIFT -> {
                            MagicMana target = mana.get(victim.getUniqueId());
                            if (target != null) {
                                double stolen = Math.min(8, target.mana());
                                target.restore(-stolen);
                                mana.get(bolt.owner.getUniqueId()).restore(stolen / 2);
                            }
                        }
                        case LIFE_TETHER -> heal(bolt.owner, 2);
                        case SILENCE -> silenced.put(victim.getUniqueId(), tick + 40);
                        default -> {}
                    }
            }
        } else if (bolt.remaining <= 0 || !arena.inside(bolt.at)) bolts.remove(bolt);
    }

    private void blast(Player owner, MagicSpell spell, Location point) {
        arena.world().spawnParticle(Particle.EXPLOSION, point, 1);
        arena.world().playSound(point, Sound.ENTITY_GENERIC_EXPLODE, .7f, 1.2f);
        double radius = spell.radius();
        double peak =
                spell == MagicSpell.METEOR
                        ? 14
                        : spell == MagicSpell.QUAKE ? 8 : spell == MagicSpell.FROZEN_ORB ? 6 : 10;
        for (Player p : players.get())
            if (enemy(owner, p) && near(p, point, radius) && visible(point, p)) {
                double distance = p.getLocation().distance(point);
                double power =
                        spell == MagicSpell.QUAKE
                                ? peak
                                : peak
                                        - (peak - (spell == MagicSpell.FROZEN_ORB ? 3 : 4))
                                                * distance
                                                / radius;
                if (damage.hit(owner, p, power)) {
                    if (spell == MagicSpell.METEOR || spell == MagicSpell.FIREBALL)
                        p.setFireTicks(Math.max(p.getFireTicks(), 80));
                    if (spell == MagicSpell.THUNDER) effect(p, PotionEffectType.GLOWING, 80, 0);
                    if (spell == MagicSpell.QUAKE) p.setVelocity(p.getVelocity().setY(.6));
                    if (spell == MagicSpell.FROZEN_ORB) effect(p, PotionEffectType.SLOWNESS, 80, 1);
                }
            }
        if (spell == MagicSpell.FIREBALL || spell == MagicSpell.METEOR || spell == MagicSpell.QUAKE)
            destroyTerrain(point, spell == MagicSpell.METEOR ? 3 : 2);
    }

    private void destroyTerrain(Location point, int reach) {
        for (int x = -reach; x <= reach; x++)
            for (int y = -reach; y <= reach; y++)
                for (int z = -reach; z <= reach; z++)
                    if (x * x + y * y + z * z <= reach * reach)
                        arena.remove(point.clone().add(x, y, z).getBlock());
    }

    private static Vector horizontal(Vector direction, Player caster) {
        Vector flat = direction.clone().setY(0);
        if (flat.lengthSquared() < .001) {
            double yaw = Math.toRadians(caster.getLocation().getYaw());
            flat = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        return flat.normalize();
    }

    private boolean fits(Location feet) {
        BoundingBox body = bodyAt(feet);
        for (int x = (int) Math.floor(body.getMinX()); x < Math.ceil(body.getMaxX()); x++)
            for (int y = (int) Math.floor(body.getMinY()); y < Math.ceil(body.getMaxY()); y++)
                for (int z = (int) Math.floor(body.getMinZ()); z < Math.ceil(body.getMaxZ()); z++)
                    if (!arena.world().getBlockAt(x, y, z).isPassable()) return false;
        return true;
    }

    private boolean enemy(Player caster, Player target) {
        return !caster.getUniqueId().equals(target.getUniqueId())
                && fighting.test(target)
                && arena.world().equals(target.getWorld());
    }

    private static boolean near(Player player, Location at, double radius) {
        return player.getWorld().equals(at.getWorld())
                && player.getLocation().distanceSquared(at) <= radius * radius;
    }

    private boolean visible(Location source, Player player) {
        return clearLine(source.clone().add(0, .15, 0), player.getLocation().add(0, .9, 0));
    }

    private boolean clearLine(Location source, Location target) {
        Vector direction = target.clone().subtract(source).toVector();
        return direction.lengthSquared() < .1
                || arena.world()
                                .rayTraceBlocks(
                                        source,
                                        direction.clone().normalize(),
                                        direction.length(),
                                        FluidCollisionMode.NEVER,
                                        true)
                        == null;
    }

    private boolean inCone(Location from, Vector forward, Player p, double range, double cosine) {
        Vector towards = p.getLocation().add(0, 1, 0).subtract(from).toVector();
        return towards.lengthSquared() <= range * range
                && (towards.lengthSquared() < .1 || towards.normalize().dot(forward) >= cosine)
                && clearLine(from, p.getLocation().add(0, 1, 0));
    }

    private static void beam(Location from, Location to, int color) {
        Vector difference = to.clone().subtract(from).toVector();
        int count = Math.max(1, Math.min(80, (int) (difference.length() * 2)));
        var dust = new Particle.DustOptions(Color.fromRGB(color), 1.2f);
        for (int i = 0; i <= count; i++)
            from.getWorld()
                    .spawnParticle(
                            Particle.DUST,
                            from.clone().add(difference.clone().multiply((double) i / count)),
                            1,
                            0,
                            0,
                            0,
                            0,
                            dust);
    }

    private static void cone(Location from, Vector direction, int color) {
        Vector side = direction.clone().setY(0);
        if (side.lengthSquared() < .001) side = new Vector(0, 0, 1);
        side.normalize();
        // Render a fan without world entities; gameplay uses the full three-dimensional cone.
        Vector right = new Vector(-side.getZ(), 0, side.getX());
        for (int i = -2; i <= 2; i++)
            beam(
                    from,
                    from.clone().add(direction.clone().multiply(7)).add(right.clone().multiply(i)),
                    color);
    }

    static void ring(Location at, double radius, int rgb, Player viewer) {
        var dust = new Particle.DustOptions(Color.fromRGB(rgb), .8f);
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            Location point = at.clone().add(Math.cos(angle) * radius, .1, Math.sin(angle) * radius);
            if (viewer == null)
                at.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust, true);
            else viewer.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust, true);
        }
    }

    private static void effect(Player p, PotionEffectType effect, int ticks, int amplifier) {
        p.addPotionEffect(new PotionEffect(effect, ticks, amplifier, false, false));
    }

    private static void heal(Player p, double health) {
        if (p.isDead()) return;
        p.setHealth(Math.min(20, p.getHealth() + health));
        p.spawnParticle(Particle.HEART, p.getLocation().add(0, 1, 0), 2, .2, .2, .2, 0);
    }

    private void restore(Temporary block) {
        if (block.block().getBlockData().equals(block.placed())) {
            arena.remove(block.block());
            block.block().setBlockData(block.original(), false);
        }
    }

    private static void restoreArmor(Armor buff) {
        if (!buff.owner().isDead())
            buff.owner()
                    .setAbsorptionAmount(
                            Math.min(buff.owner().getAbsorptionAmount(), buff.previous()));
        var capacity = buff.owner().getAttribute(Attribute.MAX_ABSORPTION);
        if (capacity != null) capacity.removeModifier(ICE_ABSORPTION);
    }

    void remove(UUID owner) {
        bolts.removeIf(b -> b.owner.getUniqueId().equals(owner));
        pending.removeIf(p -> p.owner().getUniqueId().equals(owner));
        zones.removeIf(z -> z.owner().getUniqueId().equals(owner));
        shields.remove(owner);
        silenced.remove(owner);
        secondWind.remove(owner);
        channels.remove(owner);
        trails.remove(owner);
        history.remove(owner);
        Armor buff = armor.remove(owner);
        if (buff != null) restoreArmor(buff);
    }

    void clear() {
        bolts.clear();
        pending.clear();
        zones.clear();
        shields.clear();
        silenced.clear();
        secondWind.clear();
        channels.clear();
        trails.clear();
        history.clear();
        for (Armor buff : armor.values()) restoreArmor(buff);
        armor.clear();
        for (Temporary block : temporary.values()) restore(block);
        temporary.clear();
    }
}
