package me.petr1furious.potatominigames.games.party;

import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Switch;

import java.util.List;
import java.util.Random;

final class WaitForItArena {
    static final int FLOOR = 80;
    private static final int SIZE = 9;
    private static final int SPACING = 13;
    private static final int COLUMNS = 4;
    private static final Material FLOOR_BLOCK = Material.SMOOTH_STONE;
    private static final List<Cell> PLANTERS =
            List.of(new Cell(2, 2), new Cell(4, 2), new Cell(6, 2), new Cell(3, 4), new Cell(5, 4));
    private static final List<Cell> POSTS =
            List.of(new Cell(2, 3), new Cell(6, 3), new Cell(2, 5), new Cell(6, 5));
    private static final List<Material> OTHER_FLOWERS =
            List.of(Material.POPPY, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.ALLIUM);

    private final World world;
    private final int count;

    WaitForItArena(World world, int count) {
        this.world = world;
        this.count = count;
    }

    private record Cell(int x, int z) {}

    void build() {
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        for (int i = 0; i < count; i++)
            for (int x = 0; x < SIZE; x++)
                for (int z = 0; z < SIZE; z++) {
                    block(i, x, 0, z).setType(FLOOR_BLOCK, false);
                    if (x == 0 || x == SIZE - 1 || z == 0 || z == SIZE - 1) {
                        block(i, x, 1, z).setType(Material.SMOOTH_SANDSTONE, false);
                        block(i, x, 2, z).setType(Material.SMOOTH_SANDSTONE, false);
                        block(i, x, 3, z)
                                .setType(
                                        (x + z) % 4 == 0
                                                ? Material.SHROOMLIGHT
                                                : Material.CUT_SANDSTONE,
                                        false);
                    }
                }
    }

    void prepare(WaitForItRound.Task task, Random random) {
        // Choose a target once and build the same room for everyone.
        int target =
                random.nextInt(task == WaitForItRound.Task.BUTTON ? POSTS.size() : PLANTERS.size());
        for (int i = 0; i < count; i++) {
            clear(i);
            switch (task) {
                case FLOWER -> plantFlowers(i, target);
                case CRAFT -> block(i, 4, 1, 3).setType(Material.CRAFTING_TABLE, false);
                case CROP -> plantWheat(i, target);
                case BUTTON -> hideButton(i, target);
            }
        }
    }

    private void clear(int bay) {
        // Only these cells can differ between tasks.
        for (Cell cell : PLANTERS) {
            block(bay, cell.x, 0, cell.z).setType(FLOOR_BLOCK, false);
            block(bay, cell.x, 1, cell.z).setType(Material.AIR, false);
        }
        block(bay, 4, 1, 3).setType(Material.AIR, false);
        for (Cell post : POSTS) {
            block(bay, post.x, 1, post.z).setType(Material.AIR, false);
            block(bay, post.x, 2, post.z).setType(Material.AIR, false);
            block(bay, post.x, 2, post.z - 1).setType(Material.AIR, false);
        }
    }

    private void plantFlowers(int bay, int target) {
        int other = 0;
        for (int i = 0; i < PLANTERS.size(); i++) {
            Cell spot = PLANTERS.get(i);
            block(bay, spot.x, 0, spot.z).setType(Material.GRASS_BLOCK, false);
            block(bay, spot.x, 1, spot.z)
                    .setType(i == target ? Material.DANDELION : OTHER_FLOWERS.get(other++), false);
        }
    }

    private void plantWheat(int bay, int target) {
        Farmland soil = (Farmland) Material.FARMLAND.createBlockData();
        soil.setMoisture(soil.getMaximumMoisture());
        Ageable wheat = (Ageable) Material.WHEAT.createBlockData();
        for (int i = 0; i < PLANTERS.size(); i++) {
            Cell spot = PLANTERS.get(i);
            block(bay, spot.x, 0, spot.z).setBlockData(soil, false);
            wheat.setAge(i == target ? wheat.getMaximumAge() : 2);
            block(bay, spot.x, 1, spot.z).setBlockData(wheat, false);
        }
    }

    private void hideButton(int bay, int target) {
        for (Cell post : POSTS) {
            block(bay, post.x, 1, post.z).setType(Material.OAK_LOG, false);
            block(bay, post.x, 2, post.z).setType(Material.OAK_LOG, false);
        }
        Cell post = POSTS.get(target);
        Switch button = (Switch) Material.STONE_BUTTON.createBlockData();
        button.setAttachedFace(FaceAttachable.AttachedFace.WALL);
        button.setFacing(BlockFace.NORTH);
        block(bay, post.x, 2, post.z - 1).setBlockData(button, false);
    }

    Location spawn(int bay) {
        return new Location(world, originX(bay) + 4.5, FLOOR + 1, originZ(bay) + 7.5, 180, 0);
    }

    Location viewpoint() {
        int columns = Math.min(count, COLUMNS);
        int rows = (count + COLUMNS - 1) / COLUMNS;
        return new Location(
                world,
                originX(columns - 1) / 2.0 + SIZE / 2.0,
                FLOOR + 33,
                originZ((rows - 1) * COLUMNS) / 2.0 + SIZE / 2.0,
                0,
                88);
    }

    boolean contains(int bay, Location location) {
        return world.equals(location.getWorld())
                && location.getX() >= originX(bay) + 1
                && location.getX() < originX(bay) + SIZE - 1
                && location.getY() > FLOOR
                && location.getY() < FLOOR + 4
                && location.getZ() >= originZ(bay) + 1
                && location.getZ() < originZ(bay) + SIZE - 1;
    }

    boolean isTarget(int bay, WaitForItRound.Task task, Block block) {
        if (!contains(bay, block.getLocation())) return false;
        return switch (task) {
            case FLOWER -> block.getType() == Material.DANDELION;
            case CRAFT -> block.getType() == Material.CRAFTING_TABLE;
            case CROP ->
                    block.getType() == Material.WHEAT
                            && block.getBlockData() instanceof Ageable wheat
                            && wheat.getAge() == wheat.getMaximumAge();
            case BUTTON -> block.getType() == Material.STONE_BUTTON;
        };
    }

    private Block block(int bay, int x, int y, int z) {
        return world.getBlockAt(originX(bay) + x, FLOOR + y, originZ(bay) + z);
    }

    private static int originX(int bay) {
        return bay % COLUMNS * SPACING;
    }

    private static int originZ(int bay) {
        return bay / COLUMNS * SPACING;
    }
}
