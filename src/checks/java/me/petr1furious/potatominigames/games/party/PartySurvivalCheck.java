package me.petr1furious.potatominigames.games.party;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.TextDisplay;

/** Real arena mutations and hazard rules, without a running server. */
public final class PartySurvivalCheck {
    public static void main(String[] args) {
        standings();
        tnt();
        counting();
        rhinosAndMines();
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
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) if (TntRunArena.inside(x, z, 16)) perLayer++;
        check(world.blocks.size() == perLayer * 3, "Exactly three circular layers, with no hidden supports");
        Set<String> spawns = new HashSet<>();
        for (int i = 0; i < 16; i++) {
            Location spawn = arena.spawn(i, 16);
            check(world.at(spawn.getBlockX(), 86, spawn.getBlockZ()) == Material.TNT, "Safe top-layer spawn");
            spawns.add(spawn.getBlockX() + ":" + spawn.getBlockZ());
        }
        check(spawns.size() == 16, "Sixteen distinct starts");
        Location feet = new Location(world.world, .5, 87, .5);
        arena.step(feet, 0, 10);
        check(world.at(0, 86, 0) == Material.RED_CONCRETE, "Footsteps visibly warn before disappearing");
        for (int tick = 1; tick < 10; tick++) { arena.step(feet, tick, 10); arena.tick(tick, 16); }
        check(world.at(0, 86, 0) == Material.RED_CONCRETE, "Standing on a warning cannot extend its deadline");
        arena.tick(10, 16);
        check(world.at(0, 86, 0) == Material.AIR && world.at(0, 80, 0) == Material.TNT, "Collapse affects only the touched layer");
        arena.step(new Location(world.world, 2.5, 89, 2.5), 10, 10);
        check(world.at(2, 86, 2) == Material.TNT, "Jumping does not erase a floor several blocks below");
        arena.step(new Location(world.world, 1, 81, 1), 10, 10);
        check(world.at(0, 80, 0) == Material.RED_CONCRETE && world.at(1, 80, 1) == Material.RED_CONCRETE,
                "Footprint covers block boundaries on lower layers too");
        arena.tick(20, 15);
        for (int floor : TntRunArena.FLOORS) check(world.at(16, floor, 0) == Material.AIR, "Overtime removes outer rings on every layer");
        check(world.at(0, 86, 0) == Material.AIR, "Shrinking never rebuilds collapsed tiles");
        check(TntRunArena.delay(10, 0) == 10 && TntRunArena.delay(10, 100000) == 3, "Collapse gets faster with a playable lower bound");
        check(TntRunArena.radius(0) == 16 && TntRunArena.radius(1200) == 15, "Shrinking begins at one minute");
    }
    private static void counting() {
        Random random = new Random(4);
        int previous = 0;
        for (int round = 1; round <= 100; round++) {
            MobCountRound question = MobCountRound.generate(round, 36, random);
            check(question.total() >= previous && question.total() <= 60, "More mobs over time, bounded entity count");
            previous = question.total();
            check(new HashSet<>(question.counts().values()).size() == question.counts().size(), "All species counts are distinct");
            check(question.counts().values().stream().filter(n -> n == question.target()).count() == 1, "Exactly one correct species per announced number");
            check(question.platforms().size() == 36 && new HashSet<>(question.platforms()).equals(question.counts().keySet()),
                    "Every species, including the answer, has reachable platforms");
            check(question.counts().size() == (round < 4 ? 4 : 5), "A fifth species joins later rounds");
        }
        check(MobCountRound.countSeconds(1, 10) == 10 && MobCountRound.countSeconds(100, 10) == 6, "Counting time tightens to six seconds");
        FakeWorld world = new FakeWorld();
        MobCountArena arena = new MobCountArena(world.world);
        MobCountRound question = MobCountRound.generate(3, 36, new Random(0));
        arena.prepare(question);
        check(world.displays == 36, "Each 2×2 platform is labelled with its species");
        Map<Integer, Integer> tiles = new HashMap<>();
        for (int x = -11; x <= 10; x++) for (int z = -11; z <= 10; z++) {
            int platform = MobCountArena.platformAt(x, z);
            if (platform < 0) check(world.at(x, 80, z) == Material.LIGHT_BLUE_STAINED_GLASS, "Counting fills all gaps with glass");
            else tiles.merge(platform, 1, Integer::sum);
        }
        check(tiles.size() == 36 && tiles.values().stream().allMatch(n -> n == 4), "Thirty-six exact 2×2 platforms");
        for (int i = 0; i < 5; i++) {
            int edge = MobCountArena.MIN + i * 4 + 2;
            check(MobCountArena.platformAt(edge, -11) == -1 && MobCountArena.platformAt(edge + 1, -11) == -1
                    && MobCountArena.platformAt(edge + 2, -11) >= 0, "Gaps are exactly two blocks wide");
        }
        arena.removeGlass();
        for (int x = -11; x <= 10; x++) for (int z = -11; z <= 10; z++) {
            if (MobCountArena.platformAt(x, z) < 0) check(world.at(x, 80, z) == Material.AIR, "Jump phase removes every glass bridge");
        }
        int correct = question.platforms().indexOf(question.answer());
        Location safe = arena.spawn(correct);
        check(arena.correct(safe, question), "A landed player on a correct platform passes");
        check(!arena.correct(safe.clone().add(0, 1, 0), question), "Being airborne over the answer is not landing in time");
        check(!arena.correct(safe.clone().add(0, .5, 0), question), "Head-height without an actual head beneath is airborne");
        check(arena.correct(new Location(world.world, MobCountArena.x(correct) + .5, 81.5, MobCountArena.z(correct) + .5), question),
                "Standing on the platform's head also counts");
        Location nearest = arena.nearest(new Location(world.world, -8.5, 81, -8.5));
        check(MobCountArena.platformAt(nearest.getBlockX(), nearest.getBlockZ()) >= 0, "Gap-to-platform transition always selects a safe spawn");
        arena.removeWrong(question);
        for (int i = 0; i < 36; i++) {
            check((world.at(MobCountArena.x(i), 80, MobCountArena.z(i)) != Material.AIR)
                    == (question.platforms().get(i) == question.answer()), "Only correct platforms survive the reveal");
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
        arena.resize(18);
        int holes = 0;
        for (int x = -18; x <= 18; x++) for (int z = -18; z <= 18; z++) {
            if (RhinoArena.hole(x, z)) { check(world.at(x, 80, z) == Material.AIR, "Initial holes are real gaps"); holes++; }
        }
        check(holes > 0, "Arena starts with holes");
        for (int i = 0; i < 16; i++) {
            Location spawn = arena.spawn(i, 16);
            check(RhinoArena.solid(spawn.getBlockX(), spawn.getBlockZ(), 18), "Spawn never starts over a hole");
        }
        arena.resize(5);
        Location safe = arena.safe(17, -17);
        check(RhinoArena.solid(safe.getBlockX(), safe.getBlockZ(), 5), "Hazards reposition inside the current arena");
        check(world.at(18, 80, 0) == Material.AIR, "Shrinking removes floor, not only an invisible boundary");

        UUID first = UUID.randomUUID(), thief = UUID.randomUUID();
        TimedMine mine = new TimedMine(7, 0, 100);
        check(mine.pickup(first, 10), "A ground mine is pickable");
        check(!mine.pickup(thief, 11) && !mine.throwBy(thief, 12), "Another player cannot duplicate or throw a carried mine");
        check(mine.throwBy(first, 20) && !mine.throwBy(first, 21), "Throw once, preserving the original fuse");
        check(mine.land(30) && mine.pickup(thief, 40), "A landed mine can be picked up again");
        mine.drop();
        check(mine.state() == TimedMine.State.GROUND && mine.carrier() == null && mine.expiresAt == 100, "Elimination drops the same mine without resetting it");
        check(mine.pickup(first, 90) && !mine.expire(99), "A held mine keeps counting down");
        check(!mine.throwBy(first, 100) && mine.expire(100), "It explodes exactly five seconds from spawn, including in a hand");
        check(!mine.expire(101) && !mine.pickup(thief, 101) && !mine.land(101), "Explosions are one-shot and cannot rearm a spent mine");
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
