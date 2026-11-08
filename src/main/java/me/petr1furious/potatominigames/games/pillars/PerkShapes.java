package me.petr1furious.potatominigames.games.pillars;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/** Discrete plans are completed before the runtime mutates the world. */
final class PerkShapes {
    record Cell(int x, int y, int z) { }
    private PerkShapes() { }
    static List<Cell> anvilRain(int x, int y, int z, RandomGenerator random) {
        List<Cell> cells = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) cells.add(new Cell(x + dx, y, z + dz));
        for (int i = cells.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Cell old = cells.get(i); cells.set(i, cells.get(j)); cells.set(j, old);
        }
        return List.copyOf(cells);
    }
    // null means too large: callers must refuse the whole cast, not change a prefix.
    static List<Cell> connected(Cell start, Predicate<Cell> member, int limit) {
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        HashSet<Cell> seen = new HashSet<>();
        List<Cell> result = new ArrayList<>();
        queue.add(start); seen.add(start);
        while (!queue.isEmpty()) {
            Cell c = queue.removeFirst();
            if (!member.test(c)) continue;
            if (result.size() == limit) return null;
            result.add(c);
            for (Cell next : List.of(new Cell(c.x + 1, c.y, c.z), new Cell(c.x - 1, c.y, c.z),
                    new Cell(c.x, c.y + 1, c.z), new Cell(c.x, c.y - 1, c.z),
                    new Cell(c.x, c.y, c.z + 1), new Cell(c.x, c.y, c.z - 1)))
                if (seen.add(next)) queue.addLast(next);
        }
        return List.copyOf(result);
    }
}
