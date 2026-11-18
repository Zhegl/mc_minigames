package me.petr1furious.potatominigames.games.party;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Leaves;

/** The playable flagstone court is flat and unobstructed between the two temple gates. */
final class JumpRopeArena {
    static final int FLOOR = 80;
    private static final int WEST_RAIL = -(int) JumpRopeSweep.HALF_WIDTH - 1;
    private static final int EAST_RAIL = (int) JumpRopeSweep.HALF_WIDTH;
    private static final int NORTH_WALL = -10;
    private static final int SOUTH_WALL = 9;

    private final World world;

    JumpRopeArena(World world) {
        this.world = world;
    }

    void build() {
        for (int x = WEST_RAIL - 2; x <= EAST_RAIL + 2; x++) {
            for (int z = NORTH_WALL - 2; z <= SOUTH_WALL + 2; z++) {
                block(x, FLOOR - 1, z, Material.DEEPSLATE_BRICKS);
                boolean court = x > WEST_RAIL && x < EAST_RAIL && z > NORTH_WALL && z < SOUTH_WALL;
                Material tile =
                        court
                                ? Math.floorMod(x * 7 + z * 17, 11) < 3
                                        ? Material.MOSSY_STONE_BRICKS
                                        : Material.POLISHED_DEEPSLATE
                                : stone(x, z);
                block(x, FLOOR, z, tile);
            }
        }
        for (int z = NORTH_WALL; z <= SOUTH_WALL; z++) {
            sideWall(WEST_RAIL, z);
            sideWall(EAST_RAIL, z);
        }
        buildGate(NORTH_WALL, -1);
        buildGate(SOUTH_WALL, 1);
        jungleBorder(WEST_RAIL - 2);
        jungleBorder(EAST_RAIL + 2);
    }

    private void sideWall(int x, int z) {
        block(x, FLOOR + 1, z, stone(x, z));
        block(x, FLOOR + 2, z, Material.GREEN_STAINED_GLASS);
        block(
                x,
                FLOOR + 3,
                z,
                z % 5 == 0 ? Material.CHISELED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS);
        if (z % 5 == 0) block(x, FLOOR + 4, z, Material.MOSSY_COBBLESTONE_WALL);
    }

    private void buildGate(int wall, int outward) {
        int gate = wall + outward;
        // The ground-level opening is glazed so players can see the rope while it is parked.
        for (int x = WEST_RAIL + 1; x < EAST_RAIL; x++) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++)
                block(x, y, wall, Material.LIME_STAINED_GLASS);
        }
        for (int x : new int[] {WEST_RAIL - 1, WEST_RAIL, EAST_RAIL, EAST_RAIL + 1}) {
            for (int y = FLOOR + 1; y <= FLOOR + 5; y++)
                block(x, y, gate, y % 3 == 0 ? Material.CHISELED_STONE_BRICKS : stone(x, y));
        }
        for (int step = 0; step < 3; step++) {
            for (int x = WEST_RAIL - 1 + 2 * step; x <= EAST_RAIL + 1 - 2 * step; x++)
                block(x, FLOOR + 5 + step, gate, stone(x, gate + step));
        }
        int direction = -outward;
        lightStart(direction, false);
        block(-1, FLOOR + 8, gate, Material.MOSS_BLOCK);
        block(0, FLOOR + 8, gate, Material.MOSS_BLOCK);
    }

    void lightStart(int direction, boolean lit) {
        int gate = direction > 0 ? NORTH_WALL - 1 : SOUTH_WALL + 1;
        for (int x = -2; x <= 1; x++)
            block(
                    x,
                    FLOOR + 5,
                    gate,
                    lit ? Material.OCHRE_FROGLIGHT : Material.CHISELED_STONE_BRICKS);
    }

    private void jungleBorder(int x) {
        Leaves leaves = (Leaves) Material.JUNGLE_LEAVES.createBlockData();
        leaves.setPersistent(true);
        for (int z : new int[] {-8, -2, 5, 8}) {
            int top = FLOOR + 4 + Math.floorMod(z, 3);
            for (int y = FLOOR + 1; y < top; y++) block(x, y, z, Material.JUNGLE_LOG);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++)
                    world.getBlockAt(x + dx, top, z + dz).setBlockData(leaves, false);
            }
            world.getBlockAt(x, top + 1, z).setBlockData(leaves, false);
        }
    }

    private static Material stone(int x, int z) {
        return switch (Math.floorMod(x * 7 + z * 5, 9)) {
            case 0, 1, 2 -> Material.MOSSY_STONE_BRICKS;
            case 3 -> Material.CRACKED_STONE_BRICKS;
            default -> Material.STONE_BRICKS;
        };
    }

    private void block(int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }
}
