package me.petr1furious.potatominigames.games.tanks;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

/** A rotationally balanced, three-lane battlefield built in strictly bounded slices. */
final class TanksArena {
    static final int GROUND = 64, HALF_X = 64, HALF_Z = 50;
    static final double HULL_HALF_WIDTH = 1.65, HULL_HALF_LENGTH = 2.2, HULL_HEIGHT = 3;
    private static final int MIN_Y = 63, MAX_Y = 75;
    private static final int WIDTH = HALF_X * 2 + 1, DEPTH = HALF_Z * 2 + 1;
    private static final int TOTAL = WIDTH * DEPTH * (MAX_Y - MIN_Y + 1);
    private static final int[][] TREES = {
        {-51, -32}, {-45, -17}, {-53, 2}, {-44, 35}, {-15, -46}, {37, -40}
    };
    private final World world;
    private int cursor;
    private boolean labelled;

    TanksArena(World world) {
        this.world = world;
    }

    /** A budget counts visited cells too, so sparse upper layers cannot cause a long tick. */
    boolean buildSlice(int budget) {
        int end = cursor + Math.min(4096, Math.max(1, budget));
        end = Math.min(TOTAL, end);
        while (cursor < end) {
            int cell = cursor++;
            int y = MIN_Y + cell % (MAX_Y - MIN_Y + 1);
            int column = cell / (MAX_Y - MIN_Y + 1);
            int x = column % WIDTH - HALF_X, z = column / WIDTH - HALF_Z;
            Material material = materialAt(x, y, z);
            if (material != Material.AIR) world.getBlockAt(x, y, z).setType(material, false);
        }
        if (cursor == TOTAL && !labelled) {
            labelled = true;
            for (int side = 0; side < 2; side++) {
                final int team = side;
                world.spawn(
                        base(side).add(0, 4.5, 0),
                        TextDisplay.class,
                        label -> {
                            label.text(
                                    I18n.tr(
                                            team == 0
                                                    ? "tanks.arena.base-red"
                                                    : "tanks.arena.base-blue"));
                            label.setBillboard(Display.Billboard.CENTER);
                            label.setSeeThrough(false);
                            label.setShadowed(true);
                            label.setPersistent(false);
                            label.setInvulnerable(true);
                            label.setViewRange(3);
                        });
            }
        }
        return cursor == TOTAL;
    }

    float progress() {
        return (float) cursor / TOTAL;
    }

    Location spawn(int side, int index) {
        int slot = Math.floorMod(index, 6);
        return new Location(
                world,
                (-20 + slot * 8) * (side == 0 ? 1 : -1),
                GROUND + 1,
                side == 0 ? -41 : 41,
                side == 0 ? 0 : 180,
                0);
    }

    Location base(int side) {
        return new Location(world, 0, GROUND + 1, side == 0 ? -37 : 37);
    }

    Location spectator() {
        return new Location(world, 0, 87, 0, 180, 65);
    }

    /** Tests the rotated volume, including every corner, support cell, and roof clearance. */
    boolean canDrive(Location hull) {
        return canDrive(hull, TanksRules.Chassis.HEAVY);
    }

    boolean canDrive(Location hull, TanksRules.Chassis chassis) {
        if (hull == null
                || hull.getWorld() != world
                || !Double.isFinite(hull.getX())
                || !Double.isFinite(hull.getY())
                || !Double.isFinite(hull.getZ())
                || Math.abs(hull.getX()) >= HALF_X
                || Math.abs(hull.getZ()) >= HALF_Z
                || !Float.isFinite(hull.getYaw())
                || Math.abs(hull.getY() - (GROUND + 1)) > .05) return false;
        TanksRules.ModelGeometry geometry = TanksRules.geometry(chassis);
        double halfWidth = geometry.hullHalfWidth(), halfLength = geometry.hullHalfLength();
        double height = Math.max(geometry.hullHeight(), geometry.turretTop());
        double radius = Math.hypot(halfWidth, halfLength);
        int minX = (int) Math.floor(hull.getX() - radius),
                maxX = (int) Math.floor(hull.getX() + radius);
        int minZ = (int) Math.floor(hull.getZ() - radius),
                maxZ = (int) Math.floor(hull.getZ() + radius);
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                if (!footprintOverlaps(
                        hull.getX(), hull.getZ(), hull.getYaw(), x, z, halfWidth, halfLength))
                    continue;
                if (Math.abs(x) >= HALF_X - 1
                        || Math.abs(z) >= HALF_Z - 1
                        || !world.getBlockAt(x, GROUND, z).isSolid()) return false;
                for (int y = GROUND + 1; y < GROUND + 1 + height; y++)
                    if (!world.getBlockAt(x, y, z).isPassable()) return false;
            }
        return true;
    }

    /** Separating-axis test of an oriented tank rectangle and one whole world block. */
    static boolean footprintOverlaps(double x, double z, float yaw, int blockX, int blockZ) {
        return footprintOverlaps(x, z, yaw, blockX, blockZ, HULL_HALF_WIDTH, HULL_HALF_LENGTH);
    }

    private static boolean footprintOverlaps(
            double x,
            double z,
            float yaw,
            int blockX,
            int blockZ,
            double halfWidth,
            double halfLength) {
        double a = Math.toRadians(yaw), c = Math.cos(a), s = Math.sin(a);
        double dx = blockX + .5 - x, dz = blockZ + .5 - z;
        double ac = Math.abs(c), as = Math.abs(s), epsilon = 1e-7;
        return Math.abs(dx) < halfWidth * ac + halfLength * as + .5 - epsilon
                && Math.abs(dz) < halfWidth * as + halfLength * ac + .5 - epsilon
                && Math.abs(dx * c + dz * s) < halfWidth + .5 * (ac + as) - epsilon
                && Math.abs(-dx * s + dz * c) < halfLength + .5 * (ac + as) - epsilon;
    }

    static Material materialAt(int x, int y, int z) {
        int pattern = Math.floorMod(x * 7349 + z * 9151, 29);
        if (y == MIN_Y) return Material.STONE;
        if (y == GROUND) {
            for (int side = -1; side <= 1; side += 2) {
                double r = Math.hypot(x, z - side * 37);
                if (r >= 6 && r < 7)
                    return side < 0 ? Material.RED_CONCRETE : Material.BLUE_CONCRETE;
                if (r < 6)
                    return (x + z) % 3 == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE;
            }
            if (Math.abs(x) >= HALF_X - 1 || Math.abs(z) >= HALF_Z - 1)
                return Material.POLISHED_DEEPSLATE;
            boolean lane =
                    Math.abs(x) < 8
                            || Math.abs(Math.abs(x) - 25) < 6
                            || Math.abs(z) < 6
                            || Math.abs(z - x * .55) < 3;
            if (lane)
                return pattern < 8
                        ? Material.COARSE_DIRT
                        : pattern < 13 ? Material.GRAVEL : Material.DIRT_PATH;
            return pattern < 4
                    ? Material.MOSS_BLOCK
                    : pattern < 7 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
        }
        int height = y - GROUND;
        if (Math.abs(x) == HALF_X || Math.abs(z) == HALF_Z)
            return height < 5
                    ? (height == 4 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS)
                    : Material.AIR;
        for (int side = -1; side <= 1; side += 2) {
            int sx = x * side, sz = z * side;
            // Wide spawn berms force an initial flank and prevent spawn-to-spawn sniping.
            if (Math.abs(sx) <= 17 && sz >= 25 && sz <= 29 && height <= 4)
                return height == 4
                        ? Material.MOSS_BLOCK
                        : pattern < 11 ? Material.ANDESITE : Material.STONE;
            // A broken factory: drive-through doorways, high corners, fallen masonry.
            int rx = sx - 28, rz = sz - 8;
            if (Math.abs(rx) <= 8 && Math.abs(rz) <= 8) {
                boolean wall = Math.abs(rx) == 8 || Math.abs(rz) == 8;
                boolean door =
                        (Math.abs(rx) <= 3 && Math.abs(rz) == 8)
                                || (Math.abs(rz) <= 3 && Math.abs(rx) == 8);
                int top = Math.abs(rx) > 5 && Math.abs(rz) > 5 ? 7 : 4 + Math.floorMod(rx + rz, 3);
                if (wall && !door && height <= top)
                    return height == top
                            ? Material.CRACKED_STONE_BRICKS
                            : pattern < 7 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
                if (rx >= 4 && rz >= 4 && height <= 2) return Material.COBBLESTONE;
            }
            // Low rock clusters and a tall central sightline break make hull-down positions.
            double rock = sq((sx + 25) / 6.0) + sq((sz - 24) / 4.0);
            if (rock < 1 && height <= 1 + (1 - rock) * 4)
                return pattern < 8 ? Material.TUFF : Material.ANDESITE;
            if (sx >= 3 && sx <= 11 && sz >= 2 && sz <= 5 && height <= (sx < 6 ? 5 : 3))
                return pattern < 6 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS;
            // Flag stands sit off the capture disk, never in a spawning footprint.
            if (sx == 11 && sz == 37 && height <= 7) return Material.POLISHED_ANDESITE;
            if (sx >= 12 && sx <= 15 && sz == 37 && height >= 5 && height <= 7)
                return side < 0 ? Material.RED_WOOL : Material.BLUE_WOOL;
            for (int[] tree : TREES) {
                int tx = sx - tree[0], tz = sz - tree[1];
                if (tx == 0 && tz == 0 && height <= 7) return Material.SPRUCE_LOG;
                int crown = height >= 8 ? 1 : height >= 6 ? 2 : height >= 4 ? 3 : -1;
                if (Math.abs(tx) <= crown
                        && Math.abs(tz) <= crown
                        && Math.abs(tx) + Math.abs(tz) <= crown + 1) return Material.SPRUCE_LEAVES;
            }
        }
        return Material.AIR;
    }

    private static double sq(double n) {
        return n * n;
    }
}
