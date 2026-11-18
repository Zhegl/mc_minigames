package me.petr1furious.potatominigames.games.party;

import org.bukkit.block.BlockFace;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Walk the blocks emitted by the arena builder, including headroom and stair direction. */
final class HotPotatoArenaCheck {
    private record Cell(int x, int y, int z) {
        Cell offset(int dx, int dy, int dz) {
            return new Cell(x + dx, y + dy, z + dz);
        }
    }

    private static final List<BlockFace> DIRECTIONS =
            List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    static void checkRoutes() {
        // The cell is the supporting block. A null facing denotes a full block.
        Map<Cell, BlockFace> blocks = new HashMap<>();
        new HotPotatoArena((x, y, z, material, facing) -> blocks.put(new Cell(x, y, z), facing)).build();
        Cell hub = new Cell(0, 0, 0);
        Cell bridge = new Cell(0, 4, 0);
        check(walkable(blocks, hub) && walkable(blocks, bridge), "Bridge must cross a clear underpass");
        for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) {
            int radius = Math.max(Math.abs(x), Math.abs(z)), diagonal = Math.abs(x) + Math.abs(z);
            if (diagonal > 25 || (radius < 15 && diagonal < 25)) continue;
            for (int y = 0; y <= bridge.y + 2; y++)
                check(blocks.containsKey(new Cell(x, y, z)), "Gap in the sprint-jump boundary");
        }

        Set<Cell> reached = new HashSet<>();
        var queue = new ArrayDeque<Cell>();
        reached.add(hub);
        queue.add(hub);
        while (!queue.isEmpty()) {
            Cell cell = queue.remove();
            int exits = 0;
            for (BlockFace direction : DIRECTIONS) {
                for (int rise = -1; rise <= 1; rise++) {
                    Cell next = cell.offset(direction.getModX(), rise, direction.getModZ());
                    if (!walkable(blocks, next)) continue;
                    if (rise > 0 && blocks.get(next) != direction && blocks.get(cell) != direction) continue;
                    if (rise < 0 && blocks.get(cell) != direction.getOppositeFace()
                            && blocks.get(next) != direction.getOppositeFace()) continue;
                    exits++;
                    if (reached.add(next)) queue.add(next);
                }
            }
            check(exits >= 2, "Dead end at " + cell);
            check(Math.abs(cell.x) < 15 && Math.abs(cell.z) < 15 && Math.abs(cell.x) + Math.abs(cell.z) < 25,
                    "Escape through the arena wall at " + cell);
        }
        for (Cell cell : blocks.keySet()) {
            if (Math.abs(cell.x) >= 15 || Math.abs(cell.z) >= 15
                    || Math.abs(cell.x) + Math.abs(cell.z) >= 25 || cell.y < 0) continue;
            if (walkable(blocks, cell)) check(reached.contains(cell), "Isolated platform at " + cell);
        }
        for (BlockFace d : DIRECTIONS) {
            for (int lane = -1; lane <= 1; lane++) {
                for (int radius = 9; radius <= 12; radius++) {
                    Cell stair = new Cell(-radius * d.getModX() + lane * d.getModZ(), 13 - radius,
                            -radius * d.getModZ() + lane * d.getModX());
                    check(reached.contains(stair) && blocks.get(stair) == d, "Broken staircase at " + stair);
                }
            }
        }
        // Both sides of the upper circuit must still link the north and south landings
        // when a pursuer controls the bridge. Also walk each dock to its second exit.
        for (int sign : new int[] {-1, 1}) {
            Cell north = new Cell(0, 4, -7);
            Cell south = new Cell(0, 4, 7);
            Set<Cell> side = new HashSet<>(Set.of(north));
            queue.add(north);
            while (!queue.isEmpty()) {
                Cell cell = queue.remove();
                for (BlockFace d : DIRECTIONS) {
                    Cell next = cell.offset(d.getModX(), 0, d.getModZ());
                    if (sign * next.x < 0 || (Math.abs(next.z) <= 1 && sign * next.x < 6)) continue;
                    if (walkable(blocks, next) && side.add(next)) queue.add(next);
                }
            }
            check(side.contains(south), "Broken upper circuit on side " + sign);
            for (int z = 1; z <= 9; z++) {
                int y = z <= 7 ? 2 : 9 - z;
                check(reached.contains(new Cell(sign * 11, y, sign * z)), "Loading dock exit blocked");
            }
        }
        for (int count = 1; count <= 12; count++) {
            var starts = new HashSet<Cell>();
            for (int i = 0; i < count; i++) {
                var at = HotPotatoArena.spawn(null, i, count);
                Cell pad = new Cell(at.getBlockX(), at.getBlockY() - HotPotatoArena.FLOOR - 1, at.getBlockZ());
                check(starts.add(pad) && reached.contains(pad), "Unsafe or overlapping start for " + count);
            }
            if (count >= 3 && count <= 8) {
                for (Cell a : starts) for (Cell b : starts) {
                    if (!a.equals(b)) check(Math.hypot(a.x - b.x, a.z - b.z) > 3.5,
                            "Players start inside tag range for " + count);
                }
            }
        }
    }

    private static boolean walkable(Map<Cell, BlockFace> blocks, Cell cell) {
        return blocks.containsKey(cell) && !blocks.containsKey(cell.offset(0, 1, 0))
                && !blocks.containsKey(cell.offset(0, 2, 0));
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
