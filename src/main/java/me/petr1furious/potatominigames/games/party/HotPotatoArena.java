package me.petr1furious.potatominigames.games.party;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;

import java.util.EnumMap;
import java.util.List;

/** The Clockworks. Heights in the builder are relative to the lower courtyard floor. */
final class HotPotatoArena {
    static final int FLOOR = 80;
    static final int GALLERY = 4;
    private static final int EDGE = 15;
    private static final List<BlockFace> SIDES =
            List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private static final Material COPPER = Material.WAXED_CUT_COPPER;
    private static final Material PATINA = Material.WAXED_WEATHERED_CUT_COPPER;
    private static final Material TEAL = Material.WAXED_OXIDIZED_CUT_COPPER;
    private static final Material BRASS = Material.GOLD_BLOCK;
    private static final Material LIGHT = Material.OCHRE_FROGLIGHT;
    private static final Material BRICK = Material.DEEPSLATE_BRICKS;
    private static final Material IRON = Material.POLISHED_DEEPSLATE;
    private static final Material RIM = Material.POLISHED_BLACKSTONE;
    private static final Material GRATE = Material.WAXED_WEATHERED_COPPER_GRATE;

    interface Blocks {
        void place(int x, int y, int z, Material material, BlockFace stairFacing);
    }

    private final Blocks blocks;

    HotPotatoArena(Blocks blocks) {
        this.blocks = blocks;
    }

    static void build(World world) {
        var stairs = new EnumMap<BlockFace, Stairs>(BlockFace.class);
        for (BlockFace side : SIDES) {
            var step = (Stairs) Material.WAXED_CUT_COPPER_STAIRS.createBlockData();
            step.setFacing(side);
            stairs.put(side, step);
        }
        new HotPotatoArena((x, y, z, material, facing) -> {
            var block = world.getBlockAt(x, FLOOR + y, z);
            if (facing == null) block.setType(material, false);
            else block.setBlockData(stairs.get(facing), false);
        }).build();
    }

    void build() {
        foundation();
        for (int x = -EDGE; x <= EDGE; x++) {
            for (int z = -EDGE; z <= EDGE; z++) {
                int a = Math.abs(x), b = Math.abs(z);
                int radius = Math.max(a, b), diagonal = a + b;
                if (diagonal > 25) continue;
                put(x, 0, z, paving(a, b));
                if (radius == EDGE || diagonal == 25) {
                    parapet(x, z, Math.min(a, b));
                    continue;
                }

                // The ring and its east-west chord share a deck. The north-south crossing stays open below.
                if (radius <= 8 && diagonal <= 13 && (radius > 5 || diagonal > 8 || b <= 1)) {
                    Material deck = Material.SPRUCE_PLANKS;
                    if (radius == 8 || diagonal == 13) deck = TEAL;
                    else if (radius == 6 || diagonal == 9 || b == 1) deck = COPPER;
                    if ((radius == 7 && Math.min(a, b) == 0) || (a == 5 && b == 5)) deck = LIGHT;
                    if (x == 0 && z == 0) deck = Material.WAXED_CHISELED_COPPER;
                    put(x, GALLERY, z, deck);
                    if ((a == 5 && b == 6) || (a == 6 && b == 5)
                            || (a == 3 && b == 1)) put(x, 3, z, IRON);
                }
                if ((a == 4 || a == 5) && (b == 4 || b == 5) && diagonal >= 9) {
                    put(x, 1, z, IRON);
                    put(x, 2, z, a == b ? LIGHT : Material.CHISELED_TUFF_BRICKS);
                    put(x, 3, z, RIM);
                }
            }
        }
        for (BlockFace side : SIDES) staircase(side);
        loadingDock(1);
        loadingDock(-1);
        clockTower();
        flywheel();
        boilerHall();
        chimneys();
        cornerLanterns();
    }

    private void foundation() {
        for (int x = -23; x <= 23; x++) for (int z = -23; z <= 23; z++) {
            int radius = Math.max(Math.abs(x), Math.abs(z));
            int diagonal = Math.abs(x) + Math.abs(z);
            if (diagonal > 33 || (radius < EDGE && diagonal < 25)) continue;
            put(x, -1, z, Material.DEEPSLATE_TILES);
            if (radius == 23 || diagonal == 33) {
                put(x, -2, z, PATINA);
                put(x, -3, z, BRICK);
                put(x, -4, z, RIM);
            }
        }
    }

    static Location spawn(World world, int index, int count) {
        double angle = 2 * Math.PI * index / count;
        int x = count == 1 ? 0 : (int) Math.round(7 * Math.sin(angle));
        int z = count == 1 ? 5 : (int) Math.round(7 * Math.cos(angle));
        // Solo practice starts at floor level looking down the underpass at the dummy.
        int height = count == 1 ? 0 : GALLERY;
        float yaw = (float) Math.toDegrees(Math.atan2(x, -z));
        return new Location(world, x + .5, FLOOR + height + 1, z + .5, yaw, 0);
    }

    private static Material paving(int x, int z) {
        int radius = Math.max(x, z), lane = Math.min(x, z);
        int diagonal = x + z;
        if (radius == 0) return BRASS;
        // An eight-tooth gear is inlaid flush with the central floor.
        if (radius == 2 || (radius == 3 && lane == 0)) return COPPER;
        if (radius <= 3) return Material.DEEPSLATE_TILES;
        if (radius == 13 || diagonal == 22) return PATINA;
        if (lane == 2 && radius >= 7) return Material.POLISHED_ANDESITE;
        if (lane == 0 && (radius == 4 || radius == 8 || radius == 14)) return LIGHT;
        if (radius >= 10 && lane >= 4 && (radius + lane) % 5 == 0) return Material.TUFF_BRICKS;
        return radius >= 10 ? Material.POLISHED_TUFF : Material.STONE_BRICKS;
    }

    private void parapet(int x, int z, int bay) {
        boolean post = bay % 5 == 0;
        put(x, -2, z, RIM);
        put(x, -1, z, PATINA);
        put(x, 0, z, IRON);
        put(x, 1, z, BRICK);
        put(x, 2, z, post ? Material.CHISELED_TUFF_BRICKS : GRATE);
        put(x, 3, z, post ? COPPER : Material.CYAN_TERRACOTTA);
        put(x, 4, z, post ? LIGHT : PATINA);
        // A runner can sprint-jump from a landing; the glazed clerestory clears the highest deck by two blocks.
        put(x, 5, z, post ? IRON : Material.LIGHT_GRAY_STAINED_GLASS);
        put(x, 6, z, post ? COPPER : GRATE);
    }

    private void staircase(BlockFace outward) {
        BlockFace uphill = outward.getOppositeFace();
        for (int step = 1; step <= GALLERY; step++) {
            int radius = 13 - step;
            for (int across = -1; across <= 1; across++) {
                int x = outward.getModX() * radius + outward.getModZ() * across;
                int z = outward.getModZ() * radius - outward.getModX() * across;
                for (int y = 1; y < step; y++) put(x, y, z, y == 1 ? BRICK : IRON);
                blocks.place(x, step, z, Material.WAXED_CUT_COPPER_STAIRS, uphill);
            }
        }
    }

    // A runner halfway up the east or west flight can turn onto a loading dock, then
    // descend around the corner. The solid plinth keeps the low underside out of play.
    private void loadingDock(int sign) {
        for (int x = 10; x <= 12; x++) for (int z = 2; z <= 6; z++) {
            put(sign * x, 1, sign * z, z == 4 && x != 11 ? LIGHT : BRICK);
            put(sign * x, 2, sign * z, x == 11 ? Material.DARK_OAK_PLANKS : PATINA);
        }
        BlockFace uphill = sign == 1 ? BlockFace.NORTH : BlockFace.SOUTH;
        for (int x = 10; x <= 12; x++) {
            put(sign * x, 1, sign * 7, BRICK);
            blocks.place(sign * x, 2, sign * 7, Material.WAXED_CUT_COPPER_STAIRS, uphill);
            blocks.place(sign * x, 1, sign * 8, Material.WAXED_CUT_COPPER_STAIRS, uphill);
        }
    }

    private void clockTower() {
        BlockFace face = BlockFace.NORTH;
        for (int y = -2; y <= 15; y++) {
            for (int across = -5; across <= 5; across++) {
                for (int depth = 17; depth <= 23; depth++) {
                    boolean corner = Math.abs(across) == 5;
                    if (!corner && depth != 17 && depth != 23 && y != 15) continue;
                    Material wall = corner ? IRON : Material.TUFF_BRICKS;
                    if (y == 0 || y == 6 || y == 15) wall = COPPER;
                    at(face, across, y, depth, wall);
                }
            }
        }
        // The dial and pendulum sit one block proud of the tower masonry.
        relief(face, 16, 1,
                "   CCCCC   ",
                "  CIIVIIC  ",
                " CIDIIIIDC ",
                " CIIDIIDIC ",
                " CVIIGDIVC ",
                " CIIIIIIIC ",
                " CIIIIIIIC ",
                "  CIIVIIC  ",
                "   CCCCC   ",
                "    BDB    ",
                "    LDL    ",
                "    LGL    ",
                "    BGB    ",
                "    BCB    ");
        for (int tier = 0; tier < 5; tier++) {
            int half = 6 - tier;
            int depthHalf = Math.max(1, 4 - tier);
            for (int u = -half; u <= half; u++)
                for (int r = 20 - depthHalf; r <= 20 + depthHalf; r++)
                    at(face, u, 16 + tier, r, tier == 0 ? RIM
                            : Math.abs(u) == half ? TEAL : PATINA);
        }
        for (int dx : new int[] {-5, 5}) {
            for (int y = 8; y <= 13; y++) {
                put(dx, y, -20, y == 8 || y == 13 ? COPPER : LIGHT);
                put(dx, y, -21, y == 8 || y == 13 ? COPPER : GRATE);
            }
        }
        for (int u = -2; u <= 2; u++) at(face, u, 21, 20, TEAL);
        at(face, 0, 22, 20, BRASS);
        at(face, 0, 23, 20, Material.LIGHTNING_ROD);
    }

    private void flywheel() {
        BlockFace face = BlockFace.EAST;
        for (int u = -7; u <= 7; u++)
            for (int r = 17; r <= 21; r++) {
                at(face, u, -1, r, BRICK);
                at(face, u, 0, r, IRON);
                if (Math.abs(u) == 4 || u == 0)
                    for (int y = 1; y <= (u == 0 ? 8 : 4); y++) at(face, u, y, r, BRICK);
            }
        // Gaps between the spokes frame the sky. The second rim gives the casting depth.
        for (int r = 16; r <= 17; r++)
            relief(face, r, 2,
                    "     CCC     ",
                    "  CCCVVVCCC  ",
                    " CC   V   CC ",
                    " C V  V  V C ",
                    " C  V V V  C ",
                    "CV   VVV   VC",
                    "CVVVVVGVVVVVC",
                    "CV   VVV   VC",
                    " C  V V V  C ",
                    " C V  V  V C ",
                    " CC   V   CC ",
                    "  CCCVVVCCC  ",
                    "     CCC     ");
        for (int r = 16; r <= 21; r++) at(face, 0, 8, r, r == 16 ? BRASS : COPPER);
    }

    private void boilerHall() {
        BlockFace face = BlockFace.SOUTH;
        for (int r = 17; r <= 23; r++) {
            for (int u = -8; u <= 8; u++) {
                if (r == 17 || r == 23 || Math.abs(u) == 8)
                    for (int y = -1; y <= 7; y++)
                        at(face, u, y, r, Math.abs(u) % 5 == 3 ? IRON : Material.MUD_BRICKS);
                int roof = 8 + 3 - Math.abs(r - 20);
                at(face, u, roof, r, r == 20 || Math.abs(u) % 5 == 3 ? PATINA : TEAL);
                if (Math.abs(u) == 8)
                    for (int y = 8; y < roof; y++) at(face, u, y, r, PATINA);
            }
        }
        for (int depth : new int[] {16, 24}) relief(face, depth, 2,
                "CCCCCCCCCCCCCCCCC",
                " BCCCB CCC BCCCB ",
                " CLLLC CLC CLLLC ",
                " CLDLC LDL CLDLC ",
                " CLDLC LDL CLDLC ",
                " BBBBB BBB BBBBB ");
        for (int u : new int[] {-5, 5}) {
            for (int z = 19; z <= 21; z++) for (int x = u - 1; x <= u + 1; x++) {
                at(face, x, 11, z, RIM);
                at(face, x, 12, z, x == u ? LIGHT : PATINA);
                at(face, x, 13, z, TEAL);
            }
        }
    }

    private void chimneys() {
        BlockFace face = BlockFace.WEST;
        for (int u = -8; u <= 8; u++)
            for (int r = 16; r <= 22; r++)
                if (r == 16 || r == 22 || Math.abs(u) == 8) {
                    for (int y = -1; y <= 4; y++) at(face, u, y, r, y == 4 ? COPPER : BRICK);
                }
        for (int u = -8; u <= 8; u++)
            for (int r = 16; r <= 22; r++) at(face, u, 5, r, IRON);
        for (int r : new int[] {16, 22}) {
            relief(face, r, 1,
                    "CCCCCCCCCCCCCCCCC",
                    "BVVVBCVVVVVCBVVVB",
                    "BLDLBCLDLDLCBLDLB",
                    "BVVVBCVVVVVCBVVVB");
        }
        stack(face, -4, 19, 15);
        stack(face, 4, 19, 11);
    }

    private void stack(BlockFace face, int center, int depth, int top) {
        for (int y = 6; y <= top; y++) {
            boolean collar = y == 6 || y == top || y == top - 3;
            for (int u = -2; u <= 2; u++)
                for (int r = -2; r <= 2; r++) {
                    int radius = Math.max(Math.abs(u), Math.abs(r));
                    int diagonal = Math.abs(u) + Math.abs(r);
                    if (radius == 0 || diagonal > (collar ? 3 : 2)) continue;
                    Material brick = y == top ? RIM : collar ? COPPER : Material.BRICKS;
                    at(face, center + u, y, depth + r, brick);
                }
        }
    }

    private void cornerLanterns() {
        for (int sx : new int[] {-1, 1}) for (int sz : new int[] {-1, 1}) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                int distance = Math.abs(x) + Math.abs(z);
                if (distance > 3) continue;
                int px = sx * (14 + x), pz = sz * (14 + z);
                for (int y = 0; y <= 3; y++) put(px, y, pz, y == 2 ? IRON : BRICK);
                put(px, 4, pz, RIM);
                if (distance <= 2) {
                    put(px, 5, pz, COPPER);
                    if (distance < 2 || x == 0 || z == 0) {
                        put(px, 6, pz, distance == 2 ? RIM : LIGHT);
                        put(px, 7, pz, distance == 2 ? RIM : LIGHT);
                    }
                }
                put(px, 8, pz, TEAL);
                if (distance <= 1) put(px, 9, pz, PATINA);
            }
            put(sx * 14, 10, sz * 14, BRASS);
            put(sx * 14, 11, sz * 14, Material.LIGHTNING_ROD);
        }
    }

    // Each row is a horizontal course of masonry, listed from the roof down.
    private void relief(BlockFace face, int depth, int bottom, String... rows) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            for (int column = 0; column < line.length(); column++) {
                char glyph = line.charAt(column);
                if (glyph == ' ') continue;
                Material material = switch (glyph) {
                    case 'B' -> BRICK;
                    case 'C' -> COPPER;
                    case 'D' -> RIM;
                    case 'G' -> BRASS;
                    case 'I' -> Material.SMOOTH_QUARTZ;
                    case 'L' -> LIGHT;
                    case 'V' -> PATINA;
                    default -> throw new IllegalArgumentException("Unknown relief tile " + glyph);
                };
                at(face, column - line.length() / 2, bottom + rows.length - 1 - row, depth, material);
            }
        }
    }

    private void at(BlockFace face, int across, int y, int depth, Material material) {
        put(face.getModX() * depth - face.getModZ() * across, y,
                face.getModZ() * depth + face.getModX() * across, material);
    }

    private void put(int x, int y, int z, Material material) {
        blocks.place(x, y, z, material, null);
    }
}
