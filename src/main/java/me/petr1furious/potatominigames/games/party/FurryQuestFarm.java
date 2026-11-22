package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.FurryQuestArena.DECK;
import static me.petr1furious.potatominigames.games.party.FurryQuestArena.EDGE;
import static me.petr1furious.potatominigames.games.party.FurryQuestArena.FLOOR;
import static org.bukkit.Material.*;

import java.util.Random;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Leaves;

/** The tallest scenery sits along the perimeter, behind the five task approaches. */
final class FurryQuestFarm {
    private final World world;
    private final Random details = new Random(150);

    FurryQuestFarm(World world) { this.world = world; }

    void build() {
        for (int x = -EDGE; x <= EDGE; x++) {
            for (int z = -EDGE; z <= EDGE; z++) {
                put(x, -1, z, DIRT);
                put(x, 0, z, GRASS_BLOCK);
                if (Math.abs(x) == EDGE || Math.abs(z) == EDGE) {
                    boolean pillar = (Math.abs(x) == EDGE ? z : x) % 6 == 0;
                    put(x, 1, z, pillar ? COBBLESTONE : SPRUCE_FENCE);
                    if (pillar) put(x, 2, z, MOSSY_COBBLESTONE_SLAB);
                    for (int y = pillar ? 3 : 2; y <= 12; y++) put(x, y, z, BARRIER);
                }
            }
        }

        // A wagon loop with shortcuts across the yard gives the herd several approaches.
        lane(-9,-8, -1,-13); lane(-1,-13, 10,-9); lane(10,-9, 15,-3);
        lane(15,-3, 11,6); lane(11,6, 8,12); lane(8,12, 1,15);
        lane(1,15, -6,13); lane(-6,13, -10,6); lane(-10,6, -14,0); lane(-14,0, -9,-8);
        lane(-14,0, -3,-3); lane(-3,-3, 4,-3); lane(4,-3, 10,-9);
        lane(-3,-3, -4,5); lane(-4,5, 1,15); lane(-4,5, 11,6);
        lane(-1,-13, 0,-21);

        for (var site : FurryQuest.Site.values()) {
            switch (site) {
                case POND -> pond(site.plot.x(), site.plot.z());
                case HAY -> barn(site.plot.x(), site.plot.z());
                case TREE -> orchard(site.plot.x(), site.plot.z());
                case TROUGH -> field(site.plot.x(), site.plot.z());
                case BELL -> bellGreen(site.plot.x(), site.plot.z());
                case PARCEL -> {
                    var loom = FurryQuest.Station.LOOM;
                    put(loom.x, loom.y, loom.z, LOOM);
                }
                case CHIMES -> {
                    chime(FurryQuest.Station.BLUE, BLUE_WOOL);
                    chime(FurryQuest.Station.GOLD, YELLOW_WOOL);
                    chime(FurryQuest.Station.RED, RED_WOOL);
                }
                case FISH, SWAP, CONTACT -> { } // These use existing scenery or livestock.
            }
        }
        farmGate();
        handcart(-18, 1);
        watchtower();
        connectRails();
    }

    private void chime(FurryQuest.Station station, Material color) {
        put(station.x, station.y - 1, station.z, NOTE_BLOCK);
        put(station.x, station.y, station.z, color);
    }

    private void lane(int x, int z, int endX, int endZ) {
        int length = Math.max(Math.abs(endX - x), Math.abs(endZ - z));
        for (int i = 0; i <= length; i++) {
            int cx = x + Math.round((endX - x) * i / (float) length);
            int cz = z + Math.round((endZ - z) * i / (float) length);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) < 2)
                        put(cx + dx, 0, cz + dz, details.nextInt(4) == 0 ? GRAVEL : COARSE_DIRT);
                }
            }
        }
    }

    private void watchtower() {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                put(x, 1, z, MOSSY_STONE_BRICKS);
                if (Math.abs(x) < 2 && Math.abs(z) < 2) {
                    for (int y = 2; y < DECK; y++)
                        put(x, y, z, y <= 3 ? STONE_BRICKS : STRIPPED_SPRUCE_WOOD);
                }
                boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
                if (rim) put(x, DECK - 1, z, DARK_OAK_WOOD);
                put(x, DECK, z, SPRUCE_PLANKS);
                put(x, DECK + 1, z, rim ? SPRUCE_SLAB : OAK_PLANKS);
            }
        }
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) {
            for (int y = 2; y < DECK; y++) put(x, y, z, SPRUCE_LOG);
            put(x, DECK - 2, z, COPPER_BLOCK);
        }
        for (int y = 2; y < DECK - 1; y++) face(0, y, -2, LADDER, BlockFace.NORTH);
        face(2, DECK - 2, 0, RED_WALL_BANNER, BlockFace.EAST);
        face(-2, DECK - 2, 0, RED_WALL_BANNER, BlockFace.WEST);
        face(0, DECK - 2, 2, RED_WALL_BANNER, BlockFace.SOUTH);
    }

    private void farmGate() {
        for (int x : new int[]{-3, 3}) {
            for (int y = 1; y <= 4; y++) put(x, y, -21, STRIPPED_SPRUCE_LOG);
            put(x, 5, -21, STONE_BRICK_SLAB);
            for (int side : new int[]{-1, 1}) put(x + side, 1, -20, POPPY);
        }
        for (int x = -2; x <= 2; x++) timber(x, 4, -21);
        face(0, 4, -20, YELLOW_WALL_BANNER, BlockFace.SOUTH);
    }

    private void handcart(int x, int z) {
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 1; dz++) put(x + dx, 1, z + dz, SPRUCE_PLANKS);
        }
        face(x + 1, 1, z - 1, POLISHED_BLACKSTONE_BUTTON, BlockFace.NORTH);
        face(x + 1, 1, z + 2, POLISHED_BLACKSTONE_BUTTON, BlockFace.SOUTH);
        put(x, 2, z, HAY_BLOCK); put(x + 1, 2, z + 1, BARREL);
        timber(x + 3, 1, z);
    }

    private void timber(int x, int y, int z) {
        Orientable beam = (Orientable) SPRUCE_LOG.createBlockData();
        beam.setAxis(Axis.X);
        at(x, y, z).setBlockData(beam, false);
    }

    private void pond(int x, int z) {
        for (int dx = -5; dx <= 4; dx++) for (int dz = -4; dz <= 3; dz++) {
            double radius = Math.pow((dx + .3 * dz) / 4.4, 2) + Math.pow(dz / 3.0, 2);
            if (radius > 1.55) continue;
            if (radius > 1) {
                put(x + dx, 0, z + dz, details.nextBoolean() ? MOSS_BLOCK : GRAVEL);
            } else {
                put(x + dx, -1, z + dz, details.nextBoolean() ? CLAY : GRAVEL);
                put(x + dx, 0, z + dz, WATER);
            }
        }
        for (int dx = 2; dx <= 5; dx++) for (int dz = 0; dz <= 1; dz++)
            put(x + dx, 0, z + dz, OAK_PLANKS);
        put(x + 2, 1, z + 1, OAK_FENCE);
        put(x - 2, 1, z - 1, LILY_PAD); put(x - 1, 1, z + 1, LILY_PAD);
        for (int dx = -2; dx <= 1; dx++) {
            put(x + dx, 0, z - 3, MUD);
            put(x + dx, 1, z - 3, SUGAR_CANE);
            if (dx % 2 == 0) put(x + dx, 2, z - 3, SUGAR_CANE);
        }
        tree(x - 4, z - 6, BIRCH_LOG, BIRCH_LEAVES, 4);
        put(x + 6, 1, z - 1, BARREL);
    }

    private void barn(int x, int z) {
        // Open-front red barn, with a stepped gable, contrasting beams and loft window.
        for (int dx = -5; dx <= 5; dx++) {
            int roof = 5 + (5 - Math.abs(dx)) / 2;
            for (int dz = -9; dz <= -4; dz++) {
                put(x + dx, 0, z + dz, SPRUCE_PLANKS);
                for (int y = 1; y < roof; y++) {
                    boolean wall = dz == -9 || Math.abs(dx) == 5 || (dz == -4 && (Math.abs(dx) >= 3 || y >= 4));
                    boolean window = (Math.abs(dx) == 5 && dz == -7 && y == 2) || (dz == -4 && dx == 0 && y == 5);
                    if (wall && !window) {
                        boolean beam = y == 4 || (Math.abs(dx) == 5 && (dz == -9 || dz == -4));
                        put(x + dx, y, z + dz, beam ? STRIPPED_OAK_WOOD : RED_TERRACOTTA);
                    }
                }
            }
            for (int dz = -10; dz <= -3; dz++) put(x + dx, roof, z + dz, DEEPSLATE_TILE_SLAB);
        }
        for (int dx = -2; dx <= 1; dx++) {
            put(x + dx, 1, z - 1, HAY_BLOCK);
            if (dx < 0) put(x + dx, 2, z - 1, HAY_BLOCK);
        }
        put(x, 1, z, HAY_BLOCK);
        put(x + 4, 1, z - 3, BARREL); put(x + 4, 2, z - 3, LANTERN);
        put(x - 4, 1, z - 5, COMPOSTER);
    }

    private void orchard(int x, int z) {
        tree(x, z, OAK_LOG, OAK_LEAVES, 5);
        tree(x - 6, z + 6, OAK_LOG, FLOWERING_AZALEA_LEAVES, 4);
        tree(x + 4, z + 8, OAK_LOG, AZALEA_LEAVES, 4);
        for (int dz = -2; dz <= 3; dz++) {
            put(x - 7, 1, z + dz, dz == 0 ? MOSSY_COBBLESTONE : COBBLESTONE_SLAB);
            if (dz % 2 == 0) put(x - 6, 1, z + dz, OXEYE_DAISY);
        }
        put(x - 3, 1, z + 7, BARREL); put(x - 2, 1, z + 7, COMPOSTER);
    }

    private void tree(int x, int z, Material log, Material leaves, int height) {
        for (int y = 1; y <= height; y++) put(x, y, z, log);
        Leaves foliage = (Leaves) leaves.createBlockData();
        foliage.setPersistent(true);
        for (int y = height - 1; y <= height + 1; y++) {
            int radius = y == height + 1 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if ((dx == 0 && dz == 0 && y <= height) || dx * dx + dz * dz > radius * radius + 1) continue;
                at(x + dx, y, z + dz).setBlockData(foliage, false);
            }
        }
    }

    private void field(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) put(x + dx, 1, z, WATER_CAULDRON);
        for (int dx = -3; dx <= 5; dx++) {
            for (int dz = 5; dz <= 11; dz++) {
                if (dx == 1) {
                    put(x + dx, 0, z + dz, WATER);
                } else {
                    Farmland soil = (Farmland) FARMLAND.createBlockData();
                    soil.setMoisture(soil.getMaximumMoisture());
                    at(x + dx, 0, z + dz).setBlockData(soil, false);
                    Ageable crop = (Ageable) (dx < 1 ? WHEAT : CARROTS).createBlockData();
                    crop.setAge(crop.getMaximumAge() - details.nextInt(2));
                    at(x + dx, 1, z + dz).setBlockData(crop, false);
                }
            }
        }
        for (int dx = -4; dx <= 6; dx++) {
            if (dx != 1 && dx != 2) put(x + dx, 1, z + 12, SPRUCE_FENCE);
        }
        put(x + 1, 0, z + 5, OAK_PLANKS);
        put(x + 5, 1, z + 2, COMPOSTER); put(x + 6, 1, z + 2, BARREL);
        put(x + 5, 2, z + 8, OAK_FENCE); put(x + 5, 1, z + 8, OAK_FENCE);
        face(x + 5, 3, z + 8, CARVED_PUMPKIN, BlockFace.NORTH);
        put(x + 5, 4, z + 8, SPRUCE_SLAB);
    }

    private void bellGreen(int x, int z) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) + Math.abs(dz) <= 4)
                put(x + dx, 0, z + dz, details.nextBoolean() ? ANDESITE : GRAVEL);
        }
        put(x, 1, z, CHISELED_STONE_BRICKS); put(x, 2, z, BELL);
        for (int dx : new int[]{-4, 4}) {
            put(x + dx, 1, z + 2, STONE_BRICKS);
            put(x + dx, 2, z + 2, SPRUCE_FENCE);
            put(x + dx, 3, z + 2, LANTERN);
        }
        for (int dx = -3; dx <= 3; dx++) {
            put(x + dx, 1, z + 4, dx % 2 == 0 ? CORNFLOWER : OXEYE_DAISY);
            if (Math.abs(dx) <= 1) face(x + dx, 1, z + 3, SPRUCE_STAIRS, BlockFace.SOUTH);
        }
    }

    private void connectRails() {
        for (int x = -EDGE; x <= EDGE; x++) for (int z = -EDGE; z <= EDGE; z++) {
            Block block = at(x, 1, z);
            if (block.getType() != SPRUCE_FENCE) continue;
            MultipleFacing rail = (MultipleFacing) block.getBlockData();
            for (BlockFace side : rail.getAllowedFaces()) {
                Material next = block.getRelative(side).getType();
                rail.setFace(side, next == SPRUCE_FENCE || next.isOccluding());
            }
            block.setBlockData(rail, false);
        }
    }

    private Block at(int x, int y, int z) { return world.getBlockAt(x, FLOOR + y, z); }
    private void put(int x, int y, int z, Material type) { at(x, y, z).setType(type, false); }
    private void face(int x, int y, int z, Material type, BlockFace facing) {
        Directional data = (Directional) type.createBlockData();
        data.setFacing(facing);
        at(x, y, z).setBlockData(data, false);
    }
}
