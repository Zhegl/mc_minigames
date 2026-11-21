package me.petr1furious.potatominigames.games.pillars;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;

/** World-independent geometry shared by live construction and regression checks. */
final class PerkLayouts {
    record Track(PerkShapes.Cell base, Rail.Shape shape, boolean powered) { }
    private static final String[] MAZE = {
            "###.###",
            "#...#.#",
            "#.#.#.#",
            "#.#...#",
            "#.###.#",
            "#.....#",
            "#####.#"
    };

    static Map<PerkTerrain.Pos, Material> maze() {
        Map<PerkTerrain.Pos, Material> plan = new LinkedHashMap<>();
        for (int z = 0; z < MAZE.length; z++) {
            for (int x = 0; x < MAZE[z].length(); x++) {
                boolean wall = MAZE[z].charAt(x) == '#';
                plan.put(new PerkTerrain.Pos(x - 3, -1, z - 3), Material.STONE_BRICKS);
                for (int y = 0; y < 2; y++) {
                    plan.put(new PerkTerrain.Pos(x - 3, y, z - 3), wall ? Material.MOSSY_STONE_BRICKS : Material.AIR);
                }
            }
        }
        return plan;
    }

    static Map<PerkTerrain.Pos, Material> garden(BlockFace forward) {
        Map<PerkTerrain.Pos, Material> plan = new LinkedHashMap<>();
        for (int z = 1; z <= 7; z++) {
            for (int x = -2; x <= 2; x++) {
                int dx = z * forward.getModX() - x * forward.getModZ();
                int dz = z * forward.getModZ() + x * forward.getModX();
                boolean edge = Math.abs(x) == 2 || z == 1 || z == 7;
                Material soil = edge ? Material.OAK_LOG : x == 0 ? Material.WATER : Material.FARMLAND;
                plan.put(new PerkTerrain.Pos(dx, -2, dz), Material.DIRT);
                plan.put(new PerkTerrain.Pos(dx, -1, dz), soil);
                Material crop = edge || x == 0 ? Material.AIR : x < 0 ? Material.WHEAT : Material.CARROTS;
                plan.put(new PerkTerrain.Pos(dx, 0, dz), crop);
            }
        }
        return plan;
    }

    static List<PerkTerrain.Pos> pillarOffsets() {
        List<PerkTerrain.Pos> offsets = new ArrayList<>();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                int distanceSquared = x * x + z * z;
                if (distanceSquared >= 25 && distanceSquared <= 64) offsets.add(new PerkTerrain.Pos(x, 0, z));
            }
        }
        return offsets;
    }
    static Map<PerkShapes.Cell, Material> tree() {
        Map<PerkShapes.Cell, Material> plan = new LinkedHashMap<>();
        for (int y = 5; y <= 9; y++) {
            int radius = y == 9 ? 2 : y == 5 ? 3 : 4;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
                if (x*x + z*z <= radius*radius + 1) plan.put(new PerkShapes.Cell(x, y, z), Material.OAK_LEAVES);
        }
        for (int y = 0; y <= 8; y++) plan.put(new PerkShapes.Cell(0, y, 0), Material.OAK_LOG);
        return Map.copyOf(plan);
    }
    static List<Track> metro(BlockFace forward) {
        BlockFace right = switch (forward) {
            case NORTH -> BlockFace.EAST; case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST; case WEST -> BlockFace.NORTH;
            default -> throw new IllegalArgumentException("Metro needs a horizontal direction");
        };
        List<Track> tracks = new ArrayList<>();
        for (int z = 0; z < 10; z++) for (int x = 0; x < 2; x++) {
            boolean corner = z == 0 || z == 9;
            Rail.Shape shape;
            if (corner) {
                BlockFace along = z == 0 ? forward : forward.getOppositeFace();
                BlockFace across = x == 0 ? right : right.getOppositeFace();
                boolean north = along == BlockFace.NORTH || across == BlockFace.NORTH;
                boolean east = along == BlockFace.EAST || across == BlockFace.EAST;
                shape = north ? (east ? Rail.Shape.NORTH_EAST : Rail.Shape.NORTH_WEST)
                        : (east ? Rail.Shape.SOUTH_EAST : Rail.Shape.SOUTH_WEST);
            } else shape = forward == BlockFace.NORTH || forward == BlockFace.SOUTH ? Rail.Shape.NORTH_SOUTH : Rail.Shape.EAST_WEST;
            tracks.add(new Track(new PerkShapes.Cell(forward.getModX()*z + right.getModX()*x, 0,
                    forward.getModZ()*z + right.getModZ()*x), shape, !corner));
        }
        return List.copyOf(tracks);
    }
    static List<PerkShapes.Cell> zombieRain() {
        List<PerkShapes.Cell> cells = new ArrayList<>();
        for (int x = -3; x < 3; x++) for (int z = -3; z < 3; z++) cells.add(new PerkShapes.Cell(x, 24, z));
        return List.copyOf(cells);
    }
}
