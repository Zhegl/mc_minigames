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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Deterministic checks of scheduling, standings, BO3, kit draws and actual generated geometry. */
public final class PvpCheck {
    public static void main(String[] args) {
        for (int n = 2; n <= 12; n++) {
            List<UUID> ids = ids(n);
            List<DuelTournament.Pair> schedule = DuelTournament.schedule(ids);
            check(schedule.size() == n * (n - 1) / 2, "Every pair must play once: " + n);
            Set<Set<UUID>> pairs = new HashSet<>();
            Map<UUID, Integer> appearances = new HashMap<>();
            for (var pair : schedule) {
                check(!pair.first().equals(pair.second()), "No self match");
                check(pairs.add(Set.of(pair.first(), pair.second())), "No duplicate pair");
                appearances.merge(pair.first(), 1, Integer::sum);
                appearances.merge(pair.second(), 1, Integer::sum);
            }
            for (UUID id : ids) check(appearances.get(id) == n - 1, "Equal matches including odd-roster byes");
            int perTour = n / 2;
            for (int i = 0; i < schedule.size(); i += perTour) {
                Set<UUID> seen = new HashSet<>();
                for (var pair : schedule.subList(i, Math.min(schedule.size(), i + perTour))) {
                    check(seen.add(pair.first()) && seen.add(pair.second()), "One duel per player per tour");
                }
            }
        }
        List<UUID> ids = ids(3);
        UUID a = ids.get(0), b = ids.get(1), c = ids.get(2);
        var match = new DuelTournament.Match(new DuelTournament.Pair(a, b));
        match.winRound(a); check(match.winner() == null, "One win is not BO3 victory");
        match.winRound(b); check(match.winner() == null, "1:1 requires decider");
        match.winRound(a); check(match.winner().equals(a) && match.wins(a) == 2, "BO3 ends at 2:1");
        expectFailure(() -> match.winRound(b));
        var sweep = new DuelTournament.Match(new DuelTournament.Pair(a, b));
        sweep.winRound(b); sweep.winRound(b);
        check(sweep.winner().equals(b) && sweep.roundsWon() == 2, "2:0 has no third round");
        var doubleForfeit = new DuelTournament.Match(new DuelTournament.Pair(a, b));
        doubleForfeit.forfeit(null);
        check(doubleForfeit.isComplete() && doubleForfeit.winner() == null, "Double forfeit advances instead of replaying");
        expectFailure(() -> doubleForfeit.winRound(a));
        var midMatchForfeit = new DuelTournament.Match(new DuelTournament.Pair(a, b));
        midMatchForfeit.winRound(a); midMatchForfeit.forfeit(b);
        check(midMatchForfeit.isComplete() && midMatchForfeit.winner().equals(b), "Withdrawal closes an unfinished BO3");
        var circular = new DuelTournament(ids);
        for (var pair : circular.schedule()) {
            UUID winner = pair.contains(a) && pair.contains(b) ? a : pair.contains(b) && pair.contains(c) ? b : c;
            circular.record(pair, winner);
        }
        ids.forEach(id -> check(circular.points(id) == 3, "Three points for match, not for individual rounds"));
        check(circular.titleContenders().size() == 3, "Circular tie needs playoffs");
        var forfeit = new DuelTournament(List.of(a, b));
        var pair = forfeit.schedule().getFirst();
        forfeit.record(pair, b);
        check(forfeit.points(b) == 3 && forfeit.points(a) == 0, "Forfeit awards one match victory");
        check(forfeit.titleContenders().equals(List.of(b)), "Clear winner requires no playoff");
        expectFailure(() -> forfeit.record(pair, a));
        var absent = new DuelTournament(List.of(a, b));
        absent.record(absent.schedule().getFirst(), null);
        check(absent.points(a) == 0 && absent.points(b) == 0, "Double forfeit awards nothing");
        expectFailure(() -> absent.record(absent.schedule().getFirst(), b));
        var head = new DuelTournament(ids(4));
        List<UUID> four = ids(4);
        for (var p : head.schedule()) {
            int x = four.indexOf(p.first()), y = four.indexOf(p.second());
            int lo = Math.min(x, y), hi = Math.max(x, y);
            int winner = lo == 0 && hi == 3 ? 3 : lo;
            head.record(p, four.get(winner));
        }
        check(head.points(four.get(0)) == head.points(four.get(1)), "Fixture must tie on points");
        check(head.titleContenders().equals(List.of(four.get(0))), "Personal meeting breaks two-way points tie");
        check(head.titleContenders(List.of(four.get(1), four.get(2), four.get(3))).equals(List.of(four.get(1))),
                "Withdrawn leader cannot hide the remaining contenders");
        Random random = new Random(42);
        Set<PvpClass> drawn = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            var kits = PvpClass.opening(random);
            check(kits.size() == 2 && kits.get(0) != kits.get(1), "Opening kits distinct; round two swaps them");
            drawn.addAll(kits);
        }
        check(drawn.size() == PvpClass.values().length, "All six classes reachable");
        arenaChecks();
        System.out.println("Party PvP: round robin 2–12, BO3, forfeits, standings/ties, class draws, arena symmetry and block reset passed");
    }
    private static List<UUID> ids(int n) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add(new UUID(0, i + 1));
        return ids;
    }
    private static void arenaChecks() {
        Map<PvpArena.Position, Material> blocks = new HashMap<>();
        World[] world = new World[1];
        world[0] = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] { World.class }, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getBlockAt" -> block(world[0], (int) args[0], (int) args[1], (int) args[2], blocks);
                case "getEntities" -> List.of();
                case "setSpawnLocation" -> true;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "PvP check world";
                default -> throw new AssertionError("Unexpected world operation: " + method.getName());
            };
        });
        PvpArena arena = new PvpArena(world[0]);
        arena.build();
        for (var entry : blocks.entrySet()) {
            var p = entry.getKey();
            check(entry.getValue() == blocks.get(new PvpArena.Position(-p.x(), p.y(), -p.z())), "Arena must be rotationally symmetric");
        }
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            Material floor = blocks.get(new PvpArena.Position(x, PvpArena.FLOOR, z));
            check(floor == Material.POLISHED_ANDESITE || floor == Material.STONE_BRICKS, "49x49 solid floor without holes");
        }
        Block original = block(world[0], 0, 80, 0, blocks);
        Block placed = block(world[0], 1, 81, 1, blocks);
        check(!arena.isPlaced(original), "Original arena never mutable");
        check(arena.canPlace(placed), "Can build in play area");
        check(!arena.canPlace(original), "Cannot replace floor");
        check(!arena.canPlace(block(world[0], 25, 81, 0, blocks)), "Cannot replace boundary");
        check(!arena.canPlace(block(world[0], 0, 86, 0, blocks)), "Build height capped at five blocks");
        placed.setType(Material.WHITE_WOOL); arena.track(placed);
        check(arena.isPlaced(placed), "Player block can be broken");
        arena.reset();
        check(placed.getType() == Material.AIR && !arena.isPlaced(placed), "Reset removes placed blocks and tracking");
        check(original.getType() == Material.POLISHED_ANDESITE, "Reset preserves original map");
    }
    private static Block block(World world, int x, int y, int z, Map<PvpArena.Position, Material> blocks) {
        var pos = new PvpArena.Position(x, y, z);
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] { Block.class }, (proxy, method, args) -> {
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
    private static void expectFailure(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid result accepted");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
