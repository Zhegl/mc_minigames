package me.petr1furious.potatominigames.games.party;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Production FFA progression and arena placement, independent of the duel tournament. */
public final class ArmsRaceCheck {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);
    public static void main(String[] args) {
        progressionChecks();
        rankingChecks();
        arenaChecks();
        System.out.println("Party Arms Race: 12-step FFA, final weapon victory, respawns, stale-shot rejection, reconnect progress, standings and 16 safe spawns passed");
    }
    private static void progressionChecks() {
        int count = ArmsRaceWeapon.values().length;
        check(count == 12 && ArmsRaceWeapon.at(count - 1) == ArmsRaceWeapon.GOLDEN_SHOVEL, "Twelve weapons with a shovel final");
        var state = new ArmsRaceState(List.of(A, B, C), count);
        check(state.death(B, A, 0), "First current-weapon kill advances");
        check(state.stage(A) == 1 && state.kills(A) == 1 && state.deaths(B) == 1, "Only killer progresses");
        check(state.alive(A) && state.alive(C) && !state.alive(B), "Death pauses only the victim; other players remain in FFA");
        check(!state.death(B, A, 1), "Duplicate death callbacks cannot award another kill");
        check(state.kills(A) == 1 && state.deaths(B) == 1, "Duplicate event does not change scores");
        check(!state.death(C, A, 0), "Previously fired arrows cannot skip the next weapon");
        check(state.stage(A) == 1 && state.deaths(C) == 1, "Stale shot earns no progress");
        state.setAlive(B, true);
        check(state.stage(B) == 0, "Respawning preserves weapon level");
        check(state.death(B, A, 1), "New weapon earns its own kill");
        int savedStage = state.stage(A), savedKills = state.kills(A);
        state.setAlive(A, false); state.setAlive(A, true);
        check(state.stage(A) == savedStage && state.kills(A) == savedKills, "Reconnect preserves progress and kills");
        check(!state.death(A, A, savedStage), "Suicide never advances");
        check(state.stage(A) == savedStage && state.deaths(A) == 1, "A death does not demote weapon level");
        state.setAlive(C, true);
        check(!state.death(C, A, savedStage), "Dead/disconnected killer cannot earn a ghost kill");
        var finish = new ArmsRaceState(List.of(A, B), count);
        for (int stage = 0; stage < count; stage++) {
            finish.setAlive(B, true);
            check(finish.death(B, A, stage), "Each weapon requires its own kill: " + stage);
            check(finish.stage(A) == Math.min(stage + 1, count - 1), "Exactly one level per kill");
            check((finish.winner() != null) == (stage == count - 1), "Reaching final weapon is not yet victory");
        }
        check(finish.winner().equals(A) && finish.kills(A) == 12, "Golden shovel kill wins immediately");
        finish.setAlive(B, true);
        check(!finish.death(B, A, count - 1) && finish.kills(A) == 12, "No score changes after victory");
        var fall = new ArmsRaceState(List.of(A, B), count);
        check(!fall.death(A, null, -1) && fall.deaths(A) == 1, "Unassisted environment death has no kill credit");
    }
    private static void rankingChecks() {
        var state = new ArmsRaceState(List.of(A, B, C), 12);
        check(state.timeWinner(List.of(A, B, C)) == null, "Zero kills at timeout is a draw");
        state.death(C, A, 0); state.setAlive(C, true);
        state.death(C, B, 0); state.setAlive(C, true);
        check(state.timeWinner(List.of(A, B, C)).equals(A), "Equal kills/deaths: first to reach level leads");
        state.death(A, null, -1); state.setAlive(A, true);
        check(state.timeWinner(List.of(A, B, C)).equals(B), "Fewer deaths break equal progress");
        state.death(C, A, 1);
        check(state.timeWinner(List.of(A, B, C)).equals(A), "Weapon progress takes precedence over deaths");
        check(state.timeWinner(List.of(B, C)).equals(B), "Disconnected leader cannot win on timeout");
        check(state.timeWinner(List.of()) == null, "No eligible players has no winner");
    }
    private static void arenaChecks() {
        Map<PvpArena.Position, Material> blocks = new HashMap<>();
        World[] world = new World[1];
        world[0] = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getBlockAt" -> block(world[0], (int) args[0], (int) args[1], (int) args[2], blocks);
                case "getEntities" -> List.of();
                case "setSpawnLocation" -> true;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "Arms Race check world";
                default -> throw new AssertionError("Unexpected world operation: " + method.getName());
            };
        });
        ArmsRaceArena arena = new ArmsRaceArena(world[0]);
        arena.build();
        List<Location> spawns = arena.spawns();
        check(spawns.size() == 16 && spawns.stream().distinct().count() == 16, "Sixteen distinct spawn points");
        for (Location spawn : spawns) {
            int x = spawn.getBlockX(), z = spawn.getBlockZ();
            check(arena.contains(spawn), "Spawn inside arena boundaries");
            check(blocks.get(new PvpArena.Position(x, PvpArena.FLOOR, z)) != null, "Spawn above solid floor");
            check(!blocks.containsKey(new PvpArena.Position(x, PvpArena.FLOOR + 1, z))
                    && !blocks.containsKey(new PvpArena.Position(x, PvpArena.FLOOR + 2, z)), "Two clear blocks of headroom at every spawn");
        }
        List<Location> enemies = List.of(spawns.get(0), spawns.get(4), spawns.get(7));
        Location chosen = arena.spawn(enemies, new Random(42));
        double best = spawns.stream().mapToDouble(location -> nearest(location, enemies)).max().orElseThrow();
        check(nearest(chosen, enemies) == best, "Respawn is farthest from its nearest opponent");
        check(arena.spawns().contains(arena.spawn(List.of(), new Random(1))), "An empty arena still has a valid spawn");
        chosen.setX(1000); spawns.getFirst().setY(-1000);
        check(arena.spawns().stream().allMatch(arena::contains), "Returned locations cannot mutate stored spawn points");
        Map<PvpArena.Position, Material> original = new HashMap<>(blocks);
        arena.cleanup();
        check(blocks.equals(original), "Session cleanup preserves map geometry");
    }
    private static double nearest(Location location, List<Location> enemies) {
        return enemies.stream().mapToDouble(enemy -> {
            double x = location.getX() - enemy.getX(), z = location.getZ() - enemy.getZ();
            return x * x + z * z;
        }).min().orElse(Double.POSITIVE_INFINITY);
    }
    private static Block block(World world, int x, int y, int z, Map<PvpArena.Position, Material> blocks) {
        var pos = new PvpArena.Position(x, y, z);
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getWorld" -> world;
                case "getX" -> x;
                case "getY" -> y;
                case "getZ" -> z;
                case "getType" -> blocks.getOrDefault(pos, Material.AIR);
                case "setType" -> { blocks.put(pos, (Material) args[0]); yield null; }
                default -> throw new AssertionError("Unexpected block operation: " + method.getName());
            };
        });
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
