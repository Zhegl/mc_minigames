package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Material;

import java.util.*;

/** Authored repeating ribbons and eyelets, aligned with the surviving side lanes. */
final class HoleWallMapPatterns {
    static HoleWallDifficulty difficulty(int stage) {
        int kind = Math.floorMod(stage - 1, 48);
        if (kind <= 4 || kind >= 12 && kind <= 18 || kind >= 36 && kind <= 41)
            return HoleWallDifficulty.EASY;
        if (kind <= 9 || kind >= 19 && kind <= 25 || kind >= 42) return HoleWallDifficulty.NORMAL;
        return HoleWallDifficulty.HARD;
    }

    static HoleWallPattern generate(HoleWallLayout layout, int stage, Random random) {
        int kind = Math.floorMod(stage - 1, 48);
        var walkLeft = new HoleWallPattern.Gate(-4, 2, 0, 3);
        var walkRight = new HoleWallPattern.Gate(3, 2, 0, 3);
        var jumpLeft = new HoleWallPattern.Gate(-4, 2, 1, 3);
        var jumpRight = new HoleWallPattern.Gate(3, 2, 1, 3);
        var duckLeft = new HoleWallPattern.Gate(-4, 2, 0, 1.5);
        var duckRight = new HoleWallPattern.Gate(3, 2, 0, 1.5);
        List<HoleWallPattern.Gate> gates =
                new ArrayList<>(
                        switch (kind) {
                            case 0 -> List.of(walkLeft, walkRight);
                            case 1, 3 -> List.of(walkLeft, jumpRight);
                            case 2 -> List.of(jumpLeft, walkRight);
                            case 4 -> List.of(jumpLeft, jumpRight);
                            case 5 -> List.of(duckLeft, walkRight);
                            case 6, 9 -> List.of(jumpLeft, duckRight);
                            case 7 -> List.of(walkLeft, duckRight);
                            case 8 -> List.of(duckLeft, jumpRight);
                            case 10 ->
                                    List.of(new HoleWallPattern.Gate(-3.375, .75, 0, 3), jumpRight);
                            case 11 ->
                                    List.of(
                                            new HoleWallPattern.Gate(-3.375, .75, 0, 1.5),
                                            walkRight);
                            case 12 -> List.of(gate(-4, 2, 0, 2), jumpRight);
                            case 13 -> List.of(jumpLeft, gate(3, 2, 0, 2));
                            case 14 -> List.of(gate(-4, 2, 0, 4), walkRight);
                            case 15 -> List.of(walkLeft, gate(3, 2, 1, 2));
                            case 16 -> List.of(gate(-4, 2, 1, 2), jumpRight);
                            case 17 -> List.of(gate(-4, 2, 0, 2), gate(3, 2, 0, 4));
                            case 18 -> List.of(gate(-4, 2, 1, 4), gate(3, 2, 1, 2));
                            case 19 -> List.of(duckLeft, duckRight);
                            case 20 -> List.of(duckLeft, gate(3, 2, 1, 2));
                            case 21 -> List.of(gate(-4, 2, 1, 2), duckRight);
                            case 22 -> List.of(gate(-4, 2, 0, 2), duckRight);
                            case 23 -> List.of(duckLeft, gate(3, 2, 0, 4));
                            case 24 -> List.of(gate(-4, 2, 1, 4), duckRight);
                            case 25 -> List.of(duckLeft, gate(3, 2, 1, 4));
                            case 26 -> List.of(gate(-3.375, .75, 0, 3), gate(3, 1, 0, 3));
                            case 27 -> List.of(gate(-3, 1, 1, 3), gate(3.625, .75, 0, 3));
                            case 28 -> List.of(gate(-3.375, .75, 0, 1.5), gate(4, 1, 1, 2));
                            case 29 -> List.of(gate(-4, 1, 0, 2), gate(3.625, .75, 0, 1.5));
                            case 30 -> List.of(gate(-3.375, .75, 0, 3), gate(3, 1, 0, 1.5));
                            case 31 -> List.of(gate(-4, 2, 1, 1.5), gate(3.625, .75, 0, 3));
                            case 32 -> List.of(gate(-4, 2, 1, 1.5), gate(4, 1, 1, 3));
                            case 33 -> List.of(gate(-4, 1, 1, 2), gate(3, 2, 1, 1.5));
                            case 34 -> List.of(gate(-4, 2, 1, 1.5), gate(3, 2, 1, 1.5));
                            case 35 -> List.of(gate(-3, 1, 0, 1.5), gate(3, 1, 1, 1.5));
                            case 36 -> List.of(gate(-4, 2, 0, 2), gate(3, 2, 0, 2));
                            case 37 -> List.of(gate(-4, 2, 1, 2), walkRight);
                            case 38 -> List.of(walkLeft, gate(3, 2, 1, 2));
                            case 39 -> List.of(gate(-4, 2, 0, 4), gate(3, 2, 0, 2));
                            case 40 -> List.of(jumpLeft, jumpRight);
                            case 41 -> List.of(gate(-4, 2, 0, 2), walkRight);
                            case 42 -> List.of(duckLeft, gate(3, 2, 0, 2));
                            case 43 -> List.of(gate(-4, 2, 0, 2), duckRight);
                            case 44 -> List.of(duckLeft, gate(3, 2, 1, 2));
                            case 45 -> List.of(gate(-4, 2, 1, 2), duckRight);
                            case 46 -> List.of(duckLeft, duckRight);
                            case 47 -> List.of(duckLeft, gate(3, 2, 0, 4));
                            default -> throw new IllegalStateException("Unknown map wall: " + kind);
                        });
        if (layout == HoleWallLayout.SNAKE && (kind == 2 || kind == 5 || kind == 8 || kind == 11))
            gates.add(
                    new HoleWallPattern.Gate(
                            0, 1, kind == 2 ? 1 : 0, kind == 5 || kind == 11 ? 1.5 : 3));
        if (layout == HoleWallLayout.SNAKE)
            switch (kind) {
                case 12, 17 -> gates.add(gate(0, 1, 0, 3));
                case 14, 18 -> gates.add(gate(0, 1, 1, 3));
                case 19, 23 -> gates.add(gate(0, 1, 0, 1.5));
                case 21, 25 -> gates.add(gate(0, 1, 1, 3));
                case 32, 35 -> gates.add(gate(0, 1, 1, 1.5));
                default -> {}
            }
        boolean jumpRequired = HoleWallChallenge.requiresJump(difficulty(stage), kind, false);
        gates = HoleWallChallenge.gates(gates, kind, difficulty(stage), jumpRequired);
        var full =
                new HoleWallBlock(
                        layout == HoleWallLayout.SNAKE
                                ? Material.PRISMARINE_BRICKS
                                : Material.PURPUR_BLOCK,
                        HoleWallBlock.Shape.FULL);
        var cells = new ArrayList<HoleWallPattern.Cell>();
        for (int u = -HoleWallPattern.HALF_WIDTH; u <= HoleWallPattern.HALF_WIDTH; u++)
            for (int y = 0; y < 5; y++) {
                HoleWallBlock block = outline(layout, kind, u, y) ? full : null;
                for (var gate : gates)
                    if (u >= gate.start() && u + 1 <= gate.start() + gate.width()) {
                        double bottom = gate.bottom(), top = bottom + gate.height();
                        if (y >= bottom && y + 1 <= top) block = null;
                        else if (y < bottom) block = full;
                        else if (y < top && y + 1 > top) block = full.upperSlab();
                        else if (kind >= 12) block = full;
                    }
                for (var gate : gates)
                    if (gate.width() == .75
                            && (u == (int) Math.floor(gate.start())
                                    || u == (int) Math.floor(gate.start()) + 1)) {
                        if (y < gate.bottom()) block = full;
                        else if (y + 1 <= gate.bottom() + gate.height())
                            block =
                                    new HoleWallBlock(
                                            Material.OAK_FENCE, HoleWallBlock.Shape.FENCE_POST);
                        else if (y < gate.bottom() + gate.height()) block = full.upperSlab();
                        else block = full;
                    }
                if (block != null) cells.add(new HoleWallPattern.Cell(u, y, block));
            }
        cells = new ArrayList<>(HoleWallPattern.frameLowPassages(cells, gates, full));
        cells =
                new ArrayList<>(
                        HoleWallChallenge.frame(
                                cells, gates, full, difficulty(stage) != HoleWallDifficulty.EASY));
        return new HoleWallPattern(
                cells,
                gates,
                I18n.tr(
                        "party.rounds.hole_wall."
                                + layout.name().toLowerCase(Locale.ROOT)
                                + "."
                                + kind));
    }

    private static HoleWallPattern.Gate gate(
            double start, double width, double bottom, double height) {
        return new HoleWallPattern.Gate(start, width, bottom, height);
    }

    // Rows read top to bottom. Every seven-column tile puts its two-column opening at
    // columns 2/3: repeating it aligns those openings with u=-4/-3 and u=3/4. Consequently
    // the actual routes belong to the artwork, rather than cutting across unrelated shapes.
    // The physical route pass above supplies jump sills, crouching slabs and precision posts.
    private static final String[][] SNAKE_RIBBONS = {
        { // Deep U ribbons: tall prongs over a continuous low band between the openings.
            ".#..#..", ".#..#..", ".#..#..", "##..###", "##..###"
        },
        { // Repeated rising steps.
            "....##.", "....###", ".#..###", "##..###", "##..###"
        },
        { // Repeated falling steps.
            "##.....", "###....", "##..#..", "##..##.", "##..###"
        },
        { // Broad U-shaped stair ribbons.
            ".#..#..", "##..##.", "##..###", "##..###", "##..###"
        },
        { // Lower U ribbons leave an especially clear skyline above ducking passages.
            ".......", ".#..#..", "##..##.", "##..###", "##..###"
        },
        { // A stepped ribbon falls on the left and climbs again on the right.
            "##...##", "###..##", ".#..###", "##..##.", "##..###"
        }
    };

    private static final String[][] DONUT_EYELETS = {
        { // Rounded, four-cornered ring windows.
            ".####..", "##..##.", "#....##", "##..##.", ".####.."
        },
        { // Narrow upright windows with simple paired columns.
            ".####..", "##..##.", "##..##.", "##..##.", ".####.."
        },
        { // Large eyelets above a shared solid sill.
            ".####..", "##..##.", "#....#.", "##..##.", "#######"
        },
        { // Two clean rows of eyelets separated by one horizontal band.
            ".####..", "##..##.", "######.", "##..##.", ".####.."
        },
        { // A lower arcade for crouching routes.
            ".......", ".####..", "##..##.", "##..##.", ".####.."
        },
        { // Broad ring frames make the two precision posts easy to read.
            "#######", "##..###", "#....##", "##..###", "#######"
        }
    };

    // New stages have their own elevations. These are architectural rows, not rotations or
    // material variants of the original six tiles. Rows run from skyline to floor.
    private static final String[][] SNAKE_EXTENSIONS = {
        { // 12: split peaks, with a shelf on the right.
            "#...#..", "##..##.", "##..###", ".#..###", "##..###"
        },
        { // 13: a long descending shoulder.
            "###....", "####...", "###.#..", "##..##.", "##..###"
        },
        { // 14: square crests above tall, even legs.
            "##..###", "##..#..", "##..#..", "##..##.", "##..###"
        },
        { // 15: narrow spires growing into a broad valley.
            ".#...#.", ".#..##.", ".#..###", "##..###", "###.###"
        },
        { // 16: low double terraces.
            ".......", "#......", "##..#..", "###.##.", "#######"
        },
        { // 17: a high shelf and an open staircase below.
            "#####..", "##..#..", "##..#..", "#...##.", "##..###"
        },
        { // 18: paired narrow ridges around a sunken middle.
            ".#...#.", ".#...#.", "##..##.", "###.###", "#######"
        },
        { // 19: continuous cap above two rising supports.
            "#######", "#.....#", "##...##", "##..###", "##..###"
        },
        { // 20: wide fork with a low central tie.
            "##...##", "##...##", "##..###", "#######", "##..###"
        },
        { // 21: stepped overhang, held by a double-width left leg.
            "######.", "##...#.", "##..##.", "##..###", "#######"
        },
        { // 22: flat low bridge with tall outside uprights.
            "#.....#", "#.....#", "#######", "##..###", "##..###"
        },
        { // 23: rising buttress beneath a two-step crown.
            "....###", "...####", "#...###", "##..###", "#######"
        },
        { // 24: stepped fork closes only at the lower band.
            "#.....#", "##...##", "###.###", "#######", "##..###"
        },
        { // 25: high double beam and a deep open bay.
            "#######", "#######", "#.....#", "##...##", "###.###"
        },
        { // 26: tall U with a square left shoulder.
            "##....#", "###...#", "##...##", "##..###", "#######"
        },
        { // 27: a high ridge above a three-step saddle.
            "...#...", "..###..", ".#####.", "##..###", "##..###"
        },
        { // 28: linked square peaks and a low trough.
            "##..##.", "##..##.", "#######", "#.....#", "##...##"
        },
        { // 29: sloping crown with a double-thick lower ribbon.
            "#......", "##....#", "###..##", "#######", "#######"
        },
        { // 30: double lintel on a narrow trunk.
            ".#####.", ".#####.", "...#...", "##.#.##", "#######"
        },
        { // 31: battlement shelf with staggered supporting shoulders.
            "#.#.#.#", "#######", "##...##", "###..##", "#######"
        },
        { // 32: tall outside rails with two interior shelves.
            "#.....#", "##...##", "##...##", "#######", "###.###"
        },
        { // 33: two wide shoulders around a raised notch.
            "###.###", "##...##", "#.....#", "##...##", "#######"
        },
        { // 34: broad terraced arch above a narrow pedestal.
            ".#####.", "#######", "##...##", "##...##", "##..###"
        },
        { // 35: narrow central ridge grows into a low, full ribbon.
            "...#...", "...#...", "..###..", ".#####.", "#######"
        }
    };

    private static final String[][] DONUT_EXTENSIONS = {
        { // 12: square windows and a continuous foot.
            "#######", "#....##", "#....##", "#....##", "#######"
        },
        { // 13: pointed eyelets.
            "..##...", ".####..", "##..##.", "#....##", "#######"
        },
        { // 14: low open bowls.
            ".......", "#.....#", "#.....#", "##...##", ".#####."
        },
        { // 15: wide architrave on paired pillars.
            "#######", ".#####.", ".#...#.", ".#...#.", "##...##"
        },
        { // 16: thick square sockets.
            "#######", "##...##", "##...##", "#######", "#######"
        },
        { // 17: high circular crown on an open arcade.
            "..###..", ".#####.", "##...##", "#.....#", "##...##"
        },
        { // 18: tall hexagonal windows.
            ".#####.", "##...##", "#.....#", "#.....#", "#######"
        },
        { // 19: two-storey squared eyelets.
            "#######", "#.....#", "#######", "#.....#", "#######"
        },
        { // 20: a narrow crown opens into a deep bowl.
            "..###..", ".##.##.", "##...##", "#.....#", "#######"
        },
        { // 21: double cornice above an open colonnade.
            "#######", "#######", ".#...#.", ".#...#.", "##...##"
        },
        { // 22: stepped keyhole ring.
            ".#####.", "##...##", "##...##", "###.###", "#######"
        },
        { // 23: linked diamonds with a square sill.
            "...#...", "..###..", ".##.##.", "##...##", "#######"
        },
        { // 24: double-sided radial brackets.
            "##...##", "#.....#", "##...##", "###.###", "#######"
        },
        { // 25: deep eyelet under a solid cap.
            "#######", "##...##", "#.....#", "#.....#", "##...##"
        },
        { // 26: chamfered aperture in a broad frame.
            "#######", ".##.##.", "##...##", ".##.##.", "#######"
        },
        { // 27: two small upper windows over a single arch.
            "#######", "#.#.#.#", "#######", "#.....#", "##...##"
        },
        { // 28: thick crown on an angular low ring.
            ".#####.", ".#####.", "##...##", "#.....#", "#######"
        },
        { // 29: tall slit widening toward the foot.
            "#######", "###.###", "##...##", "#.....#", "#######"
        },
        { // 30: a low ring with paired high ears.
            "#.....#", "##...##", "#######", "#.....#", "#######"
        },
        { // 31: shallow rounded crown and thick square base.
            "..###..", ".#####.", "#.....#", "##...##", "#######"
        },
        { // 32: three ledges around a continuous tall window.
            "#######", "#....##", "##....#", "#....##", "#######"
        },
        { // 33: low double arches below a crown window.
            ".#####.", ".#...#.", "#######", "#..#..#", "#######"
        },
        { // 34: octagonal ring with a double lower band.
            ".#####.", "#.....#", "#.....#", "#######", "#######"
        },
        { // 35: central upper window above twin lower sockets.
            "#######", "##...##", "#######", "##.#.##", "#######"
        }
    };

    // Fourteen-column bays alternate two authored elevations. Columns 1/4 in each
    // half remain vertical supports, including beside every carved side route. Thus each
    // crown reaches the floor by shared faces before the general grounding filter runs.
    private static final String[][] SNAKE_APPROACHES = {
        { // 36: twin low valleys.
            ".#..#...#..#..", "##..##..#..###", "##..#####..###", "##..######.###", "##..#####..###"
        },
        { // 37: shelf beside a spire.
            "#####...#..#..", "#####...#..##.", "##..###.#..###", "##..#####..###", "###.#####..###"
        },
        { // 38: paired broad shoulders.
            "##..#...#..##.", "##..##.##..##.", "##..#####..###", "###.######.###", "##..######.###"
        },
        { // 39: high rails and a low terrace.
            ".#..#..##..###", ".#..#...#..###", ".#..#..##..##.", "##..##.##..###", "##..#####..###"
        },
        { // 40: raised stair saddle.
            ".#..##.##..#..", ".#..#####..##.", "##..#####..###", "###.######.###", "##############"
        },
        { // 41: uneven skyline.
            "##..#...#..###", "##..##.##..###", "##..#####..###", "##..##########", "#########..###"
        },
        { // 42: long ducking veranda.
            "############..", ".#..#..##..##.", "##..##.##..###", "##..######.###", "##..#####..###"
        },
        { // 43: offset towers.
            "##..###.#..#..", "##..##..#..#..", "##..#####..##.", "###.#####..###", "##..#####..###"
        },
        { // 44: sunken connecting rail.
            ".#..#...#..##.", "##..##..#..###", "#########..###", "##..#####..###", "##..##########"
        },
        { // 45: vaulted stair landings.
            "#####...#####.", "##..#####..##.", "###.##########", "##..#####..###", "#########..###"
        },
        { // 46: double overhead ribbon.
            "##############", "###.#####..###", "##..##.###.###", "##..#####..###", "##..#####..###"
        },
        { // 47: crowned trees.
            ".#####.#####..", "##..##.##..##.", "##..###.#..#..", "###.#####..##.", "##..#####..###"
        }
    };

    private static final String[][] DONUT_APPROACHES = {
        { // 36: paired square collars.
            ".#####..#####.", "##..##.##..##.", "##..##.##..##.", "##..#####..###", "##############"
        },
        { // 37: wide and rounded eyelets.
            "#######.#####.", "##..##.##..###", ".#..#..##..##.", "##..##.##..###", "##############"
        },
        { // 38: round and wide eyelets.
            ".#####.#######", "##..#####..##.", "##..##..#..#..", "##..#####..##.", "##############"
        },
        { // 39: offset ring crowns.
            "############..", "##..##.##..##.", "##..#####..##.", "##..##.##..###", "##############"
        },
        { // 40: raised double sockets.
            "#####...#####.", "##..#####..###", "##..##.##..##.", "##..#####..###", "##############"
        },
        { // 41: ring and crossbar.
            ".#####.#####..", "##..##.##..###", "#########..##.", "##..##.##..###", "##############"
        },
        { // 42: low arch gallery.
            "#######.#####.", "##..#####..##.", ".#..#..##..##.", "##..#####..###", "##############"
        },
        { // 43: alternating low arches.
            ".#####.#######", "##..##.##..###", "##..##..#..#..", "##..#####..###", "##############"
        },
        { // 44: upper tie between eyelets.
            ".#####..#####.", "#########..##.", "##..##.##..###", "##..#####..##.", "##############"
        },
        { // 45: right-hand bridge ring.
            "#######.#####.", "##..##.#######", "##..#####..##.", "##..##.##..###", "##############"
        },
        { // 46: double-belt arcade.
            "#####..#######", "##..#####..##.", "##############", "##..#####..###", "##############"
        },
        { // 47: bowl beneath a broad crown.
            "############..", "##..##.##..###", "##..##.##..##.", "#########..###", "##############"
        }
    };

    private static boolean outline(HoleWallLayout layout, int kind, int u, int y) {
        if (kind >= 36) {
            String[] rows =
                    (layout == HoleWallLayout.SNAKE ? SNAKE_APPROACHES : DONUT_APPROACHES)
                            [kind - 36];
            return rows[HoleWallPattern.HEIGHT - 1 - y].charAt(Math.floorMod(u + 6, 14)) == '#';
        }
        if (kind >= 12) {
            String[] rows =
                    (layout == HoleWallLayout.SNAKE ? SNAKE_EXTENSIONS : DONUT_EXTENSIONS)
                            [kind - 12];
            return rows[HoleWallPattern.HEIGHT - 1 - y].charAt(
                            Math.floorMod(u + 6, rows[0].length()))
                    == '#';
        }
        int motif =
                switch (kind) {
                    case 0 -> 0;
                    case 1, 8 -> 1;
                    case 2, 6 -> 2;
                    case 3, 10 -> 3;
                    case 4, 7 -> 4;
                    case 5 -> 0;
                    default -> 5;
                };
        String[] rows = (layout == HoleWallLayout.SNAKE ? SNAKE_RIBBONS : DONUT_EYELETS)[motif];
        return rows[HoleWallPattern.HEIGHT - 1 - y].charAt(Math.floorMod(u + 6, 7)) == '#';
    }
}
