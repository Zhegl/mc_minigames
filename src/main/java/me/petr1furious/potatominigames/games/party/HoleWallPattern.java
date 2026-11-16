package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import java.util.*;

/** Authored silhouettes with explicit alternative routes over the shrinking plus. */
record HoleWallPattern(List<Cell> cells, List<Gate> gates, Component title) {
    static final int HALF_WIDTH = 13, HEIGHT = 5;
    static final int[] KINDS = {
        0, 12, 1, 2, 3, 4, 5, 18, 6, 19, 13, 7, 15, 8, 16, 9, 17, 10, 14, 11, 20, 21, 22, 23, 24,
        25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47
    };
    private static final Set<Integer> INTRO =
            Set.of(0, 1, 2, 3, 4, 6, 7, 10, 11, 18, 20, 21, 36, 37, 38, 39, 40, 41);
    private static final Set<Integer> HARD = Set.of(13, 14, 15, 16, 17, 29, 30, 31, 32, 33, 34, 35);
    private static final String[] NAMES = {
        "arch",
        "double_gate",
        "barrier",
        "steps",
        "columns",
        "battlements",
        "zigzag",
        "aqueduct",
        "canopy",
        "keyhole",
        "terraces",
        "fence_windows",
        "duck_tunnel",
        "fence_slit",
        "duck_fence",
        "iron_slit",
        "chain_curtain",
        "end_lattice",
        "chess_columns",
        "reading_tunnel",
        "grand_portal",
        "three_aisles",
        "bell_tower",
        "split_viaduct",
        "crown_passage",
        "sawtooth_bridge",
        "twin_chimneys",
        "arrow_buttresses",
        "sunken_gallery",
        "raised_ducts",
        "offset_wicket",
        "raised_wicket",
        "low_crossroads",
        "drawbridge",
        "vaulted_hurdles",
        "staggered_lofts",
        "paired_obelisks",
        "stepped_arcade",
        "forked_spires",
        "bridge_piers",
        "lantern_arcade",
        "terrace_garden",
        "crossed_galleries",
        "roofed_stairs",
        "twin_belfries",
        "scalloped_gateway",
        "terraced_balconies",
        "crenellated_gallery"
    };

    record Cell(int u, int y, HoleWallBlock block) {
        Cell(int u, int y) {
            this(u, y, HoleWallBlock.STONE);
        }
    }

    record Gate(double start, double width, double bottom, double height) {
        double bodyHeight() {
            return Math.min(1.8, height);
        }
    }

    HoleWallPattern {
        cells = grounded(cells);
        gates = List.copyOf(gates);
    }

    static List<Cell> frameLowPassages(List<Cell> cells, List<Gate> gates, HoleWallBlock material) {
        var result = new LinkedHashMap<Long, Cell>();
        for (var cell : cells) result.put(cellKey(cell.u(), cell.y()), cell);
        for (var gate : gates)
            if (gate.height() == 1.5) {
                for (int u :
                        new int[] {
                            (int) Math.floor(gate.start()) - 1,
                            (int) Math.ceil(gate.start() + gate.width())
                        }) {
                    boolean passage = false;
                    for (var other : gates)
                        if (u < other.start() + other.width() && u + 1 > other.start())
                            passage = true;
                    if (passage) continue;
                    for (int y = 0; y < Math.ceil(gate.bottom() + gate.height()); y++)
                        result.putIfAbsent(cellKey(u, y), new Cell(u, y, material));
                }
            }
        return List.copyOf(result.values());
    }

    private static List<Cell> grounded(List<Cell> cells) {
        var positions = new HashSet<Long>();
        var reached = new HashSet<Long>();
        var pending = new ArrayDeque<Cell>();
        for (var cell : cells) {
            positions.add(cellKey(cell.u(), cell.y()));
            if (cell.y() == 0) pending.add(cell);
        }
        while (!pending.isEmpty()) {
            var cell = pending.removeFirst();
            long key = cellKey(cell.u(), cell.y());
            if (!positions.contains(key) || !reached.add(key)) continue;
            pending.add(new Cell(cell.u() - 1, cell.y()));
            pending.add(new Cell(cell.u() + 1, cell.y()));
            pending.add(new Cell(cell.u(), cell.y() - 1));
            pending.add(new Cell(cell.u(), cell.y() + 1));
        }
        return cells.stream().filter(c -> reached.contains(cellKey(c.u(), c.y()))).toList();
    }

    private static long cellKey(int u, int y) {
        return ((long) u << 32) ^ (y & 0xffffffffL);
    }

    static List<Integer> shuffledStages(Random random, boolean introductory) {
        var stages = new ArrayList<Integer>();
        for (int stage = 1; stage <= KINDS.length; stage++) stages.add(stage);
        Collections.shuffle(stages, random);
        if (introductory) {
            var first = stages.stream().filter(s -> INTRO.contains(KINDS[s - 1])).limit(5).toList();
            stages.removeAll(first);
            var next =
                    stages.stream()
                            .filter(s -> difficulty(s) != HoleWallDifficulty.HARD)
                            .limit(5)
                            .toList();
            stages.removeAll(next);
            stages.addAll(0, next);
            stages.addAll(0, first);
        }
        return stages;
    }

    static HoleWallDifficulty difficulty(int stage) {
        int kind = KINDS[Math.floorMod(stage - 1, KINDS.length)];
        if (INTRO.contains(kind)) return HoleWallDifficulty.EASY;
        return HARD.contains(kind) ? HoleWallDifficulty.HARD : HoleWallDifficulty.NORMAL;
    }

    static HoleWallPattern generate(int stage, int radius, Random random) {
        int kind = KINDS[Math.floorMod(stage - 1, KINDS.length)];
        boolean jumpRequired = HoleWallChallenge.requiresJump(difficulty(stage), kind, true);
        var gates =
                HoleWallChallenge.gates(
                        routes(kind, radius), kind, difficulty(stage), jumpRequired);
        var full =
                new HoleWallBlock(
                        kind % 2 == 0 ? Material.STONE_BRICKS : Material.DARK_PRISMARINE,
                        HoleWallBlock.Shape.FULL);
        var cells = new ArrayList<Cell>();
        var outline = silhouette(kind);
        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++)
            for (int y = 0; y < HEIGHT; y++) {
                HoleWallBlock block =
                        outline[HEIGHT - 1 - y].charAt(Math.floorMod(u + 4, 9)) == '#'
                                ? full
                                : null;
                // The central frame belongs to the exits, rather than cutting unrelated holes
                // through a decorative skyline. The hurdle deliberately remains an open jump.
                if (kind != 2 && Math.abs(u) <= radius) block = full;
                // Each silhouette has a readable sill/ceiling around its actual routes, including
                // half-height headroom. The wide alternatives are playable even on three-wide arms.
                for (var gate : gates)
                    if (u >= gate.start() && u + 1 <= gate.start() + gate.width()) {
                        double bottom = gate.bottom(), top = bottom + gate.height();
                        if (y >= bottom && y + 1 <= top) block = null;
                        else if (y < bottom) block = full;
                        else if (y < top && y + 1 > top) block = full.upperSlab();
                    }
                if (kind >= 13 && kind <= 15 && (u == -1 || u == 0)) {
                    // Two disconnected posts leave 0.75/0.875 blocks; a separate full-width route
                    // rewards moving farther instead of making precision the only solution.
                    var narrow =
                            gates.stream().filter(g -> g.width() < 1).findFirst().orElseThrow();
                    if (y < narrow.bottom()) block = full;
                    else if (y + 1 <= narrow.bottom() + narrow.height())
                        block =
                                new HoleWallBlock(
                                        kind == 15 ? Material.IRON_BARS : Material.OAK_FENCE,
                                        kind == 15
                                                ? HoleWallBlock.Shape.BARS
                                                : HoleWallBlock.Shape.FENCE_POST);
                    else if (y < narrow.bottom() + narrow.height()) block = full.upperSlab();
                    else block = full;
                } else if ((kind == 30 || kind == 31) && (u == 0 || u == 1)) {
                    // A right-offset pair of posts leaves a real 0.75-block opening. Its
                    // left-hand alternative remains full width at either surviving radius.
                    var narrow =
                            gates.stream().filter(g -> g.width() < 1).findFirst().orElseThrow();
                    if (y < narrow.bottom()) block = full;
                    else if (y + 1 <= narrow.bottom() + narrow.height())
                        block =
                                new HoleWallBlock(
                                        Material.OAK_FENCE, HoleWallBlock.Shape.FENCE_POST);
                    else if (y < narrow.bottom() + narrow.height()) block = full.upperSlab();
                    else block = full;
                } else if (block != null && kind == 16 && y < 4) {
                    // Keep any crouching lintel solid; hanging chains themselves form many thin
                    // gaps.
                    boolean lintel = false;
                    for (var g : gates)
                        if (g.height() == 1.5
                                && u >= g.start()
                                && u + 1 <= g.start() + g.width()
                                && y == (int) Math.floor(g.bottom() + g.height())) lintel = true;
                    if (!lintel)
                        block = new HoleWallBlock(Material.IRON_CHAIN, HoleWallBlock.Shape.CHAIN);
                } else if (block != null && kind == 17 && y != 0 && y != 3) {
                    block = new HoleWallBlock(Material.END_ROD, HoleWallBlock.Shape.ROD);
                } else if (block != null && kind == 11 && Math.abs(u) > radius && y < 3) {
                    block = new HoleWallBlock(Material.OAK_FENCE, HoleWallBlock.Shape.FENCE_POST);
                } else if (block != null && (kind == 3 || kind == 10) && Math.abs(u) > radius) {
                    block =
                            new HoleWallBlock(
                                    Material.STONE_BRICK_STAIRS, HoleWallBlock.Shape.STAIRS);
                }
                if (block != null) cells.add(new Cell(u, y, block));
            }
        cells = new ArrayList<>(frameLowPassages(cells, gates, full));
        cells =
                new ArrayList<>(
                        HoleWallChallenge.frame(
                                cells, gates, full, difficulty(stage) != HoleWallDifficulty.EASY));
        return new HoleWallPattern(
                cells, gates, I18n.tr("party.rounds.hole_wall.pattern." + NAMES[kind]));
    }

    boolean requiresIslands() {
        return !gates.isEmpty()
                && gates.stream().allMatch(g -> g.start() >= 5 || g.start() + g.width() <= -4);
    }

    static HoleWallPattern islands(Random random) {
        int kind = random.nextInt(3);
        var full =
                new HoleWallBlock(
                        kind % 2 == 0 ? Material.STONE_BRICKS : Material.DARK_PRISMARINE,
                        HoleWallBlock.Shape.FULL);
        double height = kind == 2 ? 1.5 : kind == 1 ? 2 : 3;
        double bottom = 1;
        var gates = List.of(new Gate(-6, 2, bottom, height), new Gate(5, 2, bottom, height));
        var cells = new ArrayList<Cell>();
        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++)
            for (int y = 0; y < HEIGHT; y++) {
                int a = Math.abs(u);
                boolean opening = a == 5 || a == 6;
                if (opening && y >= bottom && y + 1 <= bottom + height) continue;
                if (!opening && a > 7 && y > 3 + (a % 2)) continue;
                cells.add(
                        new Cell(
                                u,
                                y,
                                opening && y >= bottom && y < bottom + height
                                        ? full.upperSlab()
                                        : full));
            }
        return new HoleWallPattern(
                cells, gates, I18n.tr("party.rounds.hole_wall.pattern.islands_" + kind));
    }

    private static List<Gate> routes(int kind, int radius) {
        Gate left = new Gate(-radius, radius, 0, 3), right = new Gate(1, radius, 0, 3);
        Gate center = new Gate(0, 1, 0, 3), jumpCenter = new Gate(0, 1, 1, 3);
        Gate jumpRight = new Gate(1, radius, 1, 3), duckLeft = new Gate(-radius, radius, 0, 1.5);
        Gate duckCenter = new Gate(0, 1, 0, 1.5);
        Gate shortLeft = new Gate(-radius, radius, 0, 2);
        Gate shortRight = new Gate(1, radius, 0, 2);
        Gate lowJumpLeft = new Gate(-radius, radius, 1, 1.5);
        Gate lowJumpRight = new Gate(1, radius, 1, 1.5);
        return switch (kind) {
            case 0, 4, 11 -> List.of(left, right);
            case 1, 18 -> List.of(center, jumpRight);
            case 2, 17 -> List.of(jumpCenter, left);
            case 3, 6, 10 -> List.of(left, jumpRight);
            case 5, 9 -> List.of(jumpCenter, duckLeft);
            case 7 -> List.of(center, new Gate(-radius, radius, 1, 3), jumpRight);
            case 8, 12 -> List.of(duckCenter, right);
            case 13 -> List.of(new Gate(-.375, .75, 0, 3), right);
            case 14 -> List.of(new Gate(-.375, .75, 0, 1.5), jumpRight);
            case 15 -> List.of(new Gate(-.4375, .875, 0, 3), new Gate(1, radius, 0, 1.5));
            case 16 -> List.of(left, new Gate(1, radius, 0, 1.5));
            case 20 -> List.of(new Gate(-radius, radius * 2 + 1, 0, 3));
            case 21 -> List.of(left, jumpCenter, shortRight);
            case 22 -> List.of(new Gate(-radius, radius + 1, 0, 2), jumpRight);
            case 23 -> List.of(new Gate(-radius, radius, 1, 2), new Gate(0, radius + 1, 0, 2));
            case 24 -> List.of(duckLeft, new Gate(0, radius + 1, 0, 3));
            case 25 -> List.of(new Gate(-radius, radius, 1, 2), center, new Gate(1, radius, 1, 2));
            case 26 -> List.of(shortLeft, duckCenter, shortRight);
            case 27 -> List.of(new Gate(-radius, radius, 0, 1.5), jumpCenter, shortRight);
            case 28 -> List.of(new Gate(-radius, radius + 1, 0, 1.5), new Gate(1, radius, 1, 2));
            case 29 -> List.of(lowJumpLeft, lowJumpRight);
            case 30 -> List.of(new Gate(.625, .75, 0, 2), lowJumpLeft);
            case 31 -> List.of(new Gate(.625, .75, 1, 2), duckLeft);
            case 32 -> List.of(duckLeft, new Gate(0, 1, 1, 1.5), new Gate(1, radius, 0, 1.5));
            case 33 -> List.of(lowJumpLeft, new Gate(0, radius + 1, 1, 2));
            case 34 ->
                    List.of(new Gate(-radius, radius, 1, 2), new Gate(0, 1, 0, 1.5), lowJumpRight);
            case 35 -> List.of(new Gate(-radius, radius + 1, 1, 1.5), new Gate(1, radius, 0, 1.5));
            case 36 -> List.of(new Gate(-radius, radius * 2 + 1, 0, 2));
            case 37 -> List.of(shortLeft, right);
            case 38 -> List.of(new Gate(-radius, radius + 1, 0, 3), new Gate(1, radius, 0, 4));
            case 39 -> List.of(shortLeft, new Gate(0, 1, 0, 4), shortRight);
            case 40 -> List.of(new Gate(-radius, radius, 1, 3), center, shortRight);
            case 41 -> List.of(new Gate(-radius, radius, 0, 4), shortRight);
            case 42 -> List.of(duckLeft, new Gate(1, radius, 0, 4));
            case 43 -> List.of(shortLeft, new Gate(0, 1, 1, 2), new Gate(1, radius, 0, 1.5));
            case 44 -> List.of(new Gate(-radius, radius, 1, 3), new Gate(1, radius, 0, 1.5));
            case 45 -> List.of(shortLeft, duckCenter, jumpRight);
            case 46 -> List.of(new Gate(-radius, radius + 1, 1, 2), right);
            case 47 -> List.of(new Gate(-radius, radius + 1, 0, 1.5), new Gate(1, radius, 0, 4));
            default -> List.of(duckLeft, jumpRight);
        };
    }

    private static String[] silhouette(int kind) {
        // Front elevations, top to bottom. Repeating whole architectural bays keeps the
        // silhouette legible, like Classic's arches and Beach's stepped ribbons.
        // The middle bay is fitted to the surviving arm by the explicit route frame above.
        return switch (kind) {
            case 0, 11, 13 ->
                    new String[] {"#########", "##.....##", "#.......#", "#.......#", "#.......#"};
            case 1 ->
                    new String[] {"#########", "#..###..#", "#..###..#", "#..###..#", "#########"};
            case 2 ->
                    new String[] {".........", ".........", ".........", ".........", "#########"};
            case 3 ->
                    new String[] {"#.......#", "##.....##", "###...###", "####.####", "#########"};
            case 4 ->
                    new String[] {"##..#..##", "##..#..##", "##..#..##", "##..#..##", "#########"};
            case 5 ->
                    new String[] {"##..#..##", "##..#..##", "#########", "#########", "#########"};
            case 6 ->
                    new String[] {"##.....##", "###...###", ".###.###.", "..#####..", "...###..."};
            case 7 ->
                    new String[] {"#########", "#########", "#..###..#", "#...#...#", "#...#...#"};
            case 8 ->
                    new String[] {"#########", "##.....##", "#########", "##.....##", "##.....##"};
            case 12, 14 ->
                    new String[] {"#########", "#########", "#########", "##.....##", "##.....##"};
            case 9 ->
                    new String[] {"#########", "###...###", "##.....##", "###...###", "####.####"};
            case 10 ->
                    new String[] {"....#....", "...###...", "..#####..", ".#######.", "#########"};
            case 15 ->
                    new String[] {"#########", "##.....##", "##.....##", "##.....##", "#########"};
            case 16 ->
                    new String[] {"#########", "#.#.#.#.#", "#.#.#.#.#", "#.#.#.#.#", "#########"};
            case 17 ->
                    new String[] {"#########", "#..#.#..#", "#########", "#..#.#..#", "#########"};
            case 18 ->
                    new String[] {"#########", "##.###.##", "#...#...#", "##.###.##", "#########"};
            case 20 ->
                    new String[] {"..#####..", ".##...##.", "##.....##", "##.....##", "##.....##"};
            case 21 ->
                    new String[] {"#...#...#", "#########", "#.#...#.#", "#.#...#.#", "#.#...#.#"};
            case 22 ->
                    new String[] {"...###...", "...#.#...", "#########", "##.....##", "##.....##"};
            case 23 ->
                    new String[] {"##.....##", "##.....##", "#########", "#..#.#..#", "#########"};
            case 24 ->
                    new String[] {"#.#.#.#.#", "#########", "##.....##", "###...###", "###...###"};
            case 25 ->
                    new String[] {"#..#..#..", "##.##.##.", "#########", "#..#..#..", "#########"};
            case 26 ->
                    new String[] {".##...##.", ".##...##.", "#########", "#...#...#", "#...#...#"};
            case 27 ->
                    new String[] {"....#....", "#..###..#", "#########", "#..###..#", "#########"};
            case 28 ->
                    new String[] {"#########", "#.......#", "#########", "##..#..##", "##..#..##"};
            case 29 ->
                    new String[] {".#######.", "##.....##", "#########", "#.......#", "#########"};
            case 30 ->
                    new String[] {"###...###", "#.#...#.#", "#########", "#..#.#..#", "#########"};
            case 31 ->
                    new String[] {"##..#..##", "#########", "#.#.#.#.#", "#########", "#...#...#"};
            case 32 ->
                    new String[] {"#.......#", "#...#...#", "#########", "##.###.##", "##.....##"};
            case 33 ->
                    new String[] {"##.....##", "#########", "#.......#", "#.......#", "#########"};
            case 34 ->
                    new String[] {"..#...#..", ".###.###.", "#########", "##.....##", "#########"};
            case 35 ->
                    new String[] {"###..##..", "#########", "##..###..", "#########", "#..##..##"};
            case 36 ->
                    new String[] {".#.....#.", "###...###", ".#.....#.", ".##...##.", "###...###"};
            case 37 ->
                    new String[] {"..#####..", ".#######.", "##..#..##", "##..#..##", "##..#..##"};
            case 38 ->
                    new String[] {".#..#..#.", "#########", ".#..#..#.", ".#######.", ".#..#..#."};
            case 39 ->
                    new String[] {"#########", ".##...##.", ".##...##.", "####.####", "#..#.#..#"};
            case 40 ->
                    new String[] {".###.###.", ".#.#.#.#.", "#########", "##..#..##", "##..#..##"};
            case 41 ->
                    new String[] {"#.......#", "##..#..##", "##.###.##", "####.####", "#########"};
            case 42 ->
                    new String[] {"##..#..##", "#########", "##.....##", "#########", "##..#..##"};
            case 43 ->
                    new String[] {"#........", "###......", "#####....", "#######..", "#########"};
            case 44 ->
                    new String[] {".##...##.", ".##...##.", "####.####", "#..#.#..#", "#########"};
            case 45 ->
                    new String[] {"##..#..##", "#########", "#...#...#", "###...###", "#########"};
            case 46 ->
                    new String[] {"###......", "#####....", "#######..", "##..#.###", "#########"};
            case 47 ->
                    new String[] {"#.#...#.#", "#########", "###...###", "##.....##", "#########"};
            default ->
                    new String[] {"##.....##", "###...###", "####.####", "#########", "#########"};
        };
    }

    static boolean compatible(HoleWallPattern a, HoleWallPattern b) {
        for (Gate first : a.gates())
            for (Gate second : b.gates())
                if (Math.max(first.bottom(), second.bottom())
                                + Math.min(first.bodyHeight(), second.bodyHeight())
                        <= Math.min(
                                        first.bottom() + first.height(),
                                        second.bottom() + second.height())
                                + 1e-6) return true;
        return false;
    }
}
