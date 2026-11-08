package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Target reservations stay fair as players leave and rhinos join. */
public final class RhinoTargetingCheck {
    public static void main(String[] args) {
        Random random = new Random(12);
        for (int playerCount = 1; playerCount <= 16; playerCount++) {
            List<UUID> players = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) players.add(new UUID(0, i + 1));
            List<UUID> targets = new ArrayList<>();
            for (int rhino = 0; rhino < 6; rhino++) {
                targets.add(players.get(RhinoHazards.targetIndex(players, targets, random)));
                balanced(players, targets);
            }
            // Repeated asynchronous retargeting must preserve distinct/fair assignments.
            for (int turn = 0; turn < 100; turn++) {
                int rhino = random.nextInt(targets.size());
                List<UUID> others = new ArrayList<>(targets);
                others.remove(rhino);
                targets.set(rhino, players.get(RhinoHazards.targetIndex(players, others, random)));
                balanced(players, targets);
            }
            UUID survivor = players.getFirst();
            check(RhinoHazards.targetIndex(List.of(survivor), targets, random) == 0,
                    "One survivor remains targetable even with stale reservations");
        }
        List<UUID> players = List.of(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3));
        List<UUID> crowded = List.of(players.getFirst(), players.getFirst(), players.get(1));
        check(RhinoHazards.targetIndex(players, crowded, random) == 2,
                "An unclaimed player takes priority over an already targeted player");
        System.out.println("Rhino targeting: distinct targets, balanced sharing and solo/departure handling verified");
    }
    private static void balanced(List<UUID> players, List<UUID> targets) {
        int min = Integer.MAX_VALUE, max = 0;
        for (UUID player : players) {
            int count = Collections.frequency(targets, player);
            min = Math.min(min, count); max = Math.max(max, count);
        }
        check(max - min <= 1, "Targets are distinct when possible and otherwise shared evenly");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
