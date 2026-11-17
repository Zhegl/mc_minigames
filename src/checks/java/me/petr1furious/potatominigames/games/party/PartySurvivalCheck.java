package me.petr1furious.potatominigames.games.party;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.TextDisplay;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Real arena mutations and hazard rules, without a running server. */
public final class PartySurvivalCheck {
    public static void main(String[] args) {
        standings();
        tnt();
        counting();
        rhinosAndMines();
        RhinoTargetingCheck.main(args);
        System.out.println("Party survival: TNT layers/collapse, count-platform geometry/unique answers, ravager swept hits, fixed mine fuses and elimination standings verified");
    }
    private static void standings() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        SurvivalStandings state = new SurvivalStandings(List.of(a, b, c));
        check(!state.decided(false, 0), "All participants start alive");
        check(state.eliminate(b, 20) && !state.eliminate(b, 21), "One life: duplicate deaths/withdrawals are idempotent");
        state.eliminate(c, 40);
        check(!state.decided(false, 41) && state.decided(false, 42), "Settle simultaneous falls before declaring a winner");
        check(!state.decided(true, 42), "Solo test continues while its player survives");
        check(state.ranking(List.of(a, b, c), 60).equals(List.of(a, c, b)), "Rank by survival, retaining deterministic ties");
        state.eliminate(a, 40);
        check(state.decided(false, 40) && state.survivors().isEmpty(), "Everyone falling means a draw");
        check(!state.alive(b), "Reconnect does not add an extra life");
        SurvivalStandings reveal = new SurvivalStandings(List.of(a, b));
        reveal.holdResults(true);
        reveal.eliminate(b, 30);
        check(!reveal.decided(false, 100), "A wrong player landing elsewhere during the reveal cannot win early");
        reveal.eliminate(a, 100);
        reveal.holdResults(false);
        check(reveal.decided(false, 100) && reveal.survivors().isEmpty(), "Finish all reveal penalties before resolving the draw");
    }

    private static void tnt() {
        FakeWorld world = new FakeWorld();
        TntRunArena arena = new TntRunArena(world.world);
        arena.build();
        int perLayer = 0;
        for (int x = -16; x <= 16; x++)
            for (int z = -16; z <= 16; z++) if (TntRunArena.inside(x, z, 16)) perLayer++;
        check(
                world.blocks.size() == perLayer * 3,
                "Exactly three circular layers, with no hidden supports");
        Set<String> spawns = new HashSet<>();
        for (int i = 0; i < 16; i++) {
            Location spawn = arena.spawn(i, 16);
            check(
                    world.at(spawn.getBlockX(), 86, spawn.getBlockZ()) == Material.TNT,
                    "Safe top-layer spawn");
            spawns.add(spawn.getBlockX() + ":" + spawn.getBlockZ());
        }
        check(spawns.size() == 16, "Sixteen distinct starts");
        Location feet = new Location(world.world, .5, 87, .5);
        arena.step(feet, 0, 10);
        check(
                world.at(0, 86, 0) == Material.RED_CONCRETE,
                "Footsteps visibly warn before disappearing");
        for (int tick = 1; tick < 10; tick++) {
            arena.step(feet, tick, 10);
            arena.tick(tick);
        }
        check(
                world.at(0, 86, 0) == Material.RED_CONCRETE,
                "Standing on a warning cannot extend its deadline");
        arena.tick(10);
        check(
                world.at(0, 86, 0) == Material.AIR && world.at(0, 80, 0) == Material.TNT,
                "Collapse affects only the touched layer");
        arena.step(new Location(world.world, 2.5, 89, 2.5), 10, 10);
        check(
                world.at(2, 86, 2) == Material.TNT,
                "Jumping does not erase a floor several blocks below");
        arena.step(new Location(world.world, 1, 81, 1), 10, 10);
        check(
                world.at(0, 80, 0) == Material.RED_CONCRETE
                        && world.at(1, 80, 1) == Material.RED_CONCRETE,
                "Footprint covers block boundaries on lower layers too");
        for (long elapsed : new long[] {1200, 2400, 12000}) {
            arena.tick(elapsed);
            for (int floor : TntRunArena.FLOORS)
                check(
                        world.at(16, floor, 0) == Material.TNT,
                        "Untouched platform edges remain throughout the game");
        }
        check(world.at(0, 86, 0) == Material.AIR, "Elapsed time never rebuilds collapsed tiles");
        check(
                TntRunArena.delay(10, 0) == 10 && TntRunArena.delay(10, 100000) == 3,
                "Collapse gets faster with a playable lower bound");
    }

    private static void counting() {
        Random random = new Random(4);
        int previous = 0;
        for (int round = 1; round <= 100; round++) {
            MobCountRound question = MobCountRound.generate(round, random);
            check(question.total() >= previous && question.total() <= 60, "More mobs over time, bounded entity count");
            previous = question.total();
            check(new HashSet<>(question.counts().values()).size() == question.counts().size(), "All species counts are distinct");
            check(question.counts().values().stream().filter(n -> n == question.target()).count() == 1, "Exactly one correct species per announced number");
            check(question.sectors().size() == question.counts().size()
                            && new HashSet<>(question.sectors()).equals(question.counts().keySet()),
                    "Every species has one sector");
            check(question.counts().size() == (round < 4 ? 4 : 5), "A fifth species joins later rounds");
        }
        check(MobCountRound.countSeconds(1, 10) == 10 && MobCountRound.countSeconds(100, 10) == 6, "Counting time tightens to six seconds");
        checkDisc(MobCountRound.generate(3, new Random(0)));
        checkDisc(MobCountRound.generate(4, new Random(0)));
    }

    private static void checkDisc(MobCountRound question) {
        FakeWorld world = new FakeWorld();
        MobCountArena arena = new MobCountArena(world.world);
        int sectors = question.sectors().size();
        int radius = MobCountArena.RADIUS;
        arena.prepare(question);
        check(world.displays == sectors, "Each species labels one wedge of the disc");
        check(world.at(radius, 80, radius) == Material.AIR && world.at(radius, 80, 0) != Material.AIR,
                "The curved rim ends within the surrounding square");
        for (int i = 0; i < sectors; i++) {
            Location marker = arena.marker(i, sectors);
            check(MobCountArena.sectorAt(marker.getBlockX(), marker.getBlockZ(), sectors) == i,
                    "Each label is over its matching wedge");
            check(world.at(marker.getBlockX(), 81, marker.getBlockZ()) == Material.valueOf(question.sectors().get(i).head),
                    "Every wedge has the matching mob head");
        }
        Set<Location> starts = new HashSet<>();
        for (int i = 0; i < 16; i++) {
            Location spawn = arena.spawn(i, 16);
            check(starts.add(spawn) && MobCountArena.sectorAt(spawn.getBlockX(), spawn.getBlockZ(), sectors) == MobCountArena.SEPARATOR,
                    "All players start in the neutral hub");
        }
        check(arena.countingSpots().size() >= 60 && new HashSet<>(arena.countingSpots()).size() == arena.countingSpots().size(),
                "Even the largest mob group fits in unique counting spots");
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int sector = MobCountArena.sectorAt(x, z, sectors);
                if (sector == MobCountArena.SEPARATOR)
                    check(world.at(x, 80, z) == Material.BLACK_CONCRETE, "Seams and the hub are solid neutral tiles");
                if (sector < 0) continue;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int neighbor = MobCountArena.sectorAt(x + dx, z + dz, sectors);
                        check(neighbor < 0 || neighbor == sector, "Distinct wedges never share an edge or corner");
                    }
                }
            }
        }
        arena.removeWrong(question);
        int correct = question.sectors().indexOf(question.answer());
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                check((world.at(x, 80, z) != Material.AIR) == (MobCountArena.sectorAt(x, z, sectors) == correct),
                        "Only floor belonging to the correct wedge remains");
            }
        }
        arena.cleanupLabels();
    }
    private static void rhinosAndMines() {
        check(RhinoHazards.segmentDistanceSquared(5, 0, 0, 0, 10, 0) == 0, "A fast dash cannot tunnel through a player");
        check(RhinoHazards.segmentDistanceSquared(-3, 0, 0, 0, 10, 0) == 9, "Collision clamps to the segment, not an infinite line");
        check(RhinoHazards.segmentDistanceSquared(0, 2, 0, 0, 0, 0) == 4, "Zero-length movement stays finite");
        double previousSpeed = 0;
        int previousRadius = 18, previousCooldown = 200, previousInterval = 100;
        for (int elapsed = 0; elapsed < 10000; elapsed += 20) {
            int stage = RhinoHazards.stage(elapsed);
            check(RhinoHazards.speed(stage) >= previousSpeed && RhinoHazards.speed(stage) <= 1.2, "Dash speed rises to a cap");
            check(RhinoHazards.radius(elapsed) <= previousRadius && RhinoHazards.radius(elapsed) >= 5, "Arena only shrinks");
            check(RhinoHazards.cooldown(stage) <= previousCooldown && RhinoHazards.cooldown(stage) >= 35, "Dashes become more frequent, with recovery time");
            check(RhinoHazards.mineInterval(4, stage) <= previousInterval && RhinoHazards.mineInterval(4, stage) >= 20, "Mine frequency increases to a safe cap");
            check(RhinoHazards.mineBatch(stage) >= 1 && RhinoHazards.mineBatch(stage) <= 3, "Mine batches are bounded");
            previousSpeed = RhinoHazards.speed(stage); previousRadius = RhinoHazards.radius(elapsed);
            previousCooldown = RhinoHazards.cooldown(stage); previousInterval = RhinoHazards.mineInterval(4, stage);
        }
        FakeWorld world = new FakeWorld();
        RhinoArena arena = new RhinoArena(world.world);
        arena.resize(18, 0);
        int holes = 0;
        for (int x = -18; x <= 18; x++) for (int z = -18; z <= 18; z++) {
            if (RhinoArena.hole(x, z, 0)) { check(world.at(x, 80, z) == Material.AIR, "Initial holes are real gaps"); holes++; }
        }
        check(holes > 0, "Arena starts with holes");
        for (int i = 0; i < 16; i++) {
            Location spawn = arena.spawn(i, 16);
            check(RhinoArena.solid(spawn.getBlockX(), spawn.getBlockZ(), 18, 0), "Spawn never starts over a hole");
        }
        int previousHoles = holes;
        for (int stage = 1; stage <= 10; stage++) {
            arena.resize(18, stage);
            holes = 0;
            for (int x = -18; x <= 18; x++) for (int z = -18; z <= 18; z++) {
                if (RhinoArena.hole(x, z, stage - 1)) check(world.at(x, 80, z) == Material.AIR, "New stages never fill existing holes");
                if (TntRunArena.inside(x, z, 18) && world.at(x, 80, z) == Material.AIR) holes++;
                if (x * x + z * z <= 12) check(world.at(x, 80, z) != Material.AIR, "The central refuge stays solid");
            }
            check(holes > previousHoles, "More real holes open even when the radius is unchanged");
            previousHoles = holes;
        }
        // Follow the live timeline too: shrinking and new holes must never restore floor.
        FakeWorld shrinkingWorld = new FakeWorld();
        RhinoArena shrinking = new RhinoArena(shrinkingWorld.world);
        shrinking.resize(18, 0);
        for (int seconds = 15; seconds <= 600; seconds += 15) {
            Map<Position, Material> before = new HashMap<>(shrinkingWorld.blocks);
            int radius = RhinoHazards.radius(seconds * 20L);
            int stage = RhinoHazards.stage(seconds * 20L);
            shrinking.resize(radius, stage);
            before.forEach((pos, material) -> {
                if (material == Material.AIR) check(shrinkingWorld.at(pos.x(), pos.y(), pos.z()) == Material.AIR, "Shrinking and difficulty never rebuild lost floor");
            });
            Location spawn = shrinking.safe(17, -17);
            check(shrinkingWorld.at(spawn.getBlockX(), 80, spawn.getBlockZ()) != Material.AIR, "Repositioning avoids all current holes");
        }
        arena.resize(5, 10);
        Location safe = arena.safe(17, -17);
        check(RhinoArena.solid(safe.getBlockX(), safe.getBlockZ(), 5, 10), "Hazards reposition inside the current arena");
        check(world.at(18, 80, 0) == Material.AIR, "Shrinking removes floor, not only an invisible boundary");
        for (int testRadius : new int[] {18, 10, 5}) {
            arena.resize(testRadius, 10);
            Location crowded = arena.safe(0, 0);
            Location separated = arena.spread(crowded, List.of(crowded));
            check(separated.distanceSquared(crowded) >= 3 * 3,
                    "Stacked resting rhinos separate even in the final arena");
            check(RhinoArena.solid(separated.getBlockX(), separated.getBlockZ(), testRadius, 10),
                    "Separation avoids holes and removed floor");
            check(separated.getX() * separated.getX() + separated.getZ() * separated.getZ()
                    <= (testRadius - 2) * (testRadius - 2), "Resting rhinos stay away from the rim");
            check(arena.spread(separated, List.of(crowded)).equals(separated),
                    "A separated rhino stays put instead of shuffling every tick");
            List<Location> herd = new ArrayList<>(java.util.Collections.nCopies(6, crowded));
            for (int tick = 0; tick < 20; tick++) for (int i = 0; i < herd.size(); i++) {
                List<Location> others = new ArrayList<>(herd);
                others.remove(i);
                herd.set(i, arena.spread(herd.get(i), others));
            }
            for (int i = 0; i < herd.size(); i++) {
                List<Location> others = new ArrayList<>(herd);
                others.remove(i);
                check(arena.spread(herd.get(i), others).equals(herd.get(i)), "A crowded herd settles without jitter");
                for (Location other : others) check(herd.get(i).distanceSquared(other) >= 4,
                        "All six rhinos can separate even in the final arena");
            }
        }

        UUID first = UUID.randomUUID(), thief = UUID.randomUUID();
        TimedMine mine = new TimedMine(7, 0, 160);
        check(mine.pickup(first, 10), "A ground mine is pickable");
        check(!mine.pickup(thief, 11) && !mine.throwBy(thief, 12), "Another player cannot duplicate or throw a carried mine");
        check(mine.throwBy(first, 20) && !mine.throwBy(first, 21), "Throw once, preserving the original fuse");
        check(mine.land(30) && mine.pickup(thief, 40), "A landed mine can be picked up again");
        mine.drop();
        check(mine.state() == TimedMine.State.GROUND && mine.carrier() == null && mine.expiresAt == 160, "Elimination drops the same mine without resetting it");
        check(!mine.expire(100), "The mine survives the former five-second deadline");
        check(mine.pickup(first, 150) && !mine.expire(159), "A held mine keeps counting down");
        check(!mine.throwBy(first, 160) && mine.expire(160), "It explodes exactly eight seconds from spawn, including in a hand");
        check(!mine.expire(161) && !mine.pickup(thief, 161) && !mine.land(161), "Explosions are one-shot and cannot rearm a spent mine");
    }
    private record Position(int x, int y, int z) { }
    private static final class FakeWorld {
        final Map<Position, Material> blocks = new HashMap<>();
        int displays;
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (p, m, a) -> switch (m.getName()) {
            case "getBlockAt" -> block((int) a[0], (int) a[1], (int) a[2]);
            case "setSpawnLocation" -> true;
            case "spawn" -> {
                check(a[1] == TextDisplay.class, "Only platform labels are spawned by this arena"); displays++;
                yield Proxy.newProxyInstance(TextDisplay.class.getClassLoader(), new Class<?>[] {TextDisplay.class}, (entity, method, args) -> null);
            }
            case "equals" -> p == a[0];
            case "hashCode" -> System.identityHashCode(p);
            case "toString" -> "Party survival check world";
            default -> throw new AssertionError("Unexpected world operation: " + m.getName());
        });
        Material at(int x, int y, int z) { return blocks.getOrDefault(new Position(x, y, z), Material.AIR); }
        Block block(int x, int y, int z) {
            return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class}, (p, m, a) -> switch (m.getName()) {
                case "getType" -> at(x, y, z);
                case "setType" -> { blocks.put(new Position(x, y, z), (Material) a[0]); yield null; }
                default -> throw new AssertionError("Unexpected block operation: " + m.getName());
            });
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
