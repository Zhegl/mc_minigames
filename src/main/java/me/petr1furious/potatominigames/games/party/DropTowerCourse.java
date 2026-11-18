package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** One round's immutable checkpoint course. Coordinates are local to a racer's lane. */
record DropTowerCourse(List<Pad> pads, List<Jump> jumps) {
    static final int JUMPS = 20;
    static final int START_FLOOR = 288;
    static final int LANE_RADIUS = 12;
    private static final int LANE_SPACING = 28;
    // Leave three blocks on either flank for the gate wings, even when a route turns in a corner.
    private static final int CENTER_LIMIT = LANE_RADIUS - 4;
    private static final double PLAYER_HALF_WIDTH = .299999;

    DropTowerCourse {
        pads = List.copyOf(pads);
        jumps = List.copyOf(jumps);
    }

    enum Kind {
        DRIFT, DIAGONAL_DROP, LONG_DROP, GAP, DIAGONAL_GAP, GATE, OFFSET_GATE, BAR, SLALOM, PRECISION;

        boolean isGap() {
            return this == GAP || this == DIAGONAL_GAP;
        }

        boolean isDiagonal() {
            return this == DIAGONAL_DROP || this == DIAGONAL_GAP;
        }
    }

    enum Shape {
        TERRACE("#####", "#####", "#####", "#####", "#####"),
        OCTAGON(".###.", "#####", "#####", "#####", ".###."),
        DIAMOND("..#..", ".###.", "#####", ".###.", "..#.."),
        RUNWAY(".###.", ".###.", ".###.", ".###.", ".###."),
        TEE("#####", ".###.", "..#..", "..#..", "..#.."),
        FORK(".#.#.", ".#.#.", ".###.", "..#..", "..#.."),
        DOGLEG("...##", "..##.", ".###.", ".##..", "##..."),
        CROSS(".#.", "###", ".#."),
        ELBOW("##.", ".#.", ".##"),
        BALANCE_BEAM(".....", ".....", "#####", ".....", "....."),
        CORNER(".#.", ".##", "..."),
        STRIP("...", "###", "..."),
        DOMINO("...", ".##", "..."),
        PIN("#");

        private final String[] mask;
        final int radius;

        Shape(String... mask) {
            this.mask = mask;
            radius = mask.length / 2;
        }

        boolean tile(int x, int z, int rotation) {
            if (Math.abs(x) > radius || Math.abs(z) > radius) return false;
            return switch (rotation) {
                case 0 -> mask[z + radius].charAt(x + radius) == '#';
                case 1 -> mask[radius - x].charAt(z + radius) == '#';
                case 2 -> mask[radius - z].charAt(radius - x) == '#';
                case 3 -> mask[x + radius].charAt(radius - z) == '#';
                default -> throw new IllegalArgumentException("Invalid platform rotation");
            };
        }
    }

    record Pad(int x, int z, int floor, Shape shape, int rotation) {
        int radius() {
            return shape.radius;
        }

        boolean tile(int localX, int localZ) {
            return shape.tile(localX, localZ, rotation);
        }

        boolean landed(double px, double feet, double pz) {
            if (Math.abs(feet - floor - 1) > .05) return false;
            // Minecraft can support a player by the edge of their feet, including on an inside corner.
            for (int bx = (int) Math.floor(px - PLAYER_HALF_WIDTH); bx <= Math.floor(px + PLAYER_HALF_WIDTH); bx++)
                for (int bz = (int) Math.floor(pz - PLAYER_HALF_WIDTH); bz <= Math.floor(pz + PLAYER_HALF_WIDTH); bz++)
                    if (tile(bx - x, bz - z)) return true;
            return false;
        }

        boolean missed(double feet) {
            return feet < floor + .9;
        }
    }

    record Baffle(int minX, int maxX, int floor, int minZ, int maxZ) {
        boolean overlaps(double x, double z) {
            return x > minX - PLAYER_HALF_WIDTH && x < maxX + 1 + PLAYER_HALF_WIDTH
                    && z > minZ - PLAYER_HALF_WIDTH && z < maxZ + 1 + PLAYER_HALF_WIDTH;
        }

        boolean caught(double x, double feet, double z) {
            return Math.abs(feet - floor - 1) <= .05 && overlaps(x, z);
        }
    }

    record Jump(Kind kind, List<Baffle> baffles) {
        Jump {
            baffles = List.copyOf(baffles);
        }
    }

    record Lane(int x, int z) {
        static Lane at(int index) {
            return new Lane(index % 3 * LANE_SPACING, index / 3 * LANE_SPACING);
        }

        boolean contains(double worldX, double worldZ) {
            return Math.abs(worldX - x - .5) < LANE_RADIUS && Math.abs(worldZ - z - .5) < LANE_RADIUS;
        }
    }

    private record Heading(int x, int z) {
        Baffle bar(Pad from, int floor, int near, int far, int left, int right) {
            int x1 = from.x + near * x - left * z, z1 = from.z + near * z + left * x;
            int x2 = from.x + far * x - right * z, z2 = from.z + far * z + right * x;
            return new Baffle(Math.min(x1, x2), Math.max(x1, x2), floor, Math.min(z1, z2), Math.max(z1, z2));
        }
    }

    private static final List<Heading> CARDINAL =
            List.of(new Heading(1, 0), new Heading(0, 1), new Heading(-1, 0), new Heading(0, -1));
    private static final List<Heading> DIAGONAL =
            List.of(new Heading(1, 1), new Heading(-1, 1), new Heading(-1, -1), new Heading(1, -1));

    private static final List<List<Shape>> LANDINGS = List.of(
            List.of(Shape.TERRACE, Shape.OCTAGON, Shape.RUNWAY, Shape.DIAMOND),
            List.of(Shape.RUNWAY, Shape.TEE, Shape.DOGLEG, Shape.FORK),
            List.of(Shape.CROSS, Shape.ELBOW, Shape.BALANCE_BEAM, Shape.FORK),
            List.of(Shape.CORNER, Shape.STRIP, Shape.DOMINO, Shape.CROSS));

    static DropTowerCourse generate(Random random) {
        var kinds = new ArrayList<Kind>(JUMPS);
        kinds.add(Kind.DRIFT);
        band(kinds, random, List.of(Kind.DRIFT, Kind.DIAGONAL_DROP, Kind.GATE), List.of(Kind.GAP));
        band(kinds, random, List.of(Kind.DIAGONAL_DROP, Kind.LONG_DROP, Kind.BAR, Kind.GATE), List.of(Kind.GAP));
        band(kinds, random, List.of(Kind.OFFSET_GATE, Kind.BAR, Kind.LONG_DROP), List.of(Kind.GAP, Kind.DIAGONAL_GAP));
        band(kinds, random, List.of(Kind.SLALOM, Kind.OFFSET_GATE,
                random.nextBoolean() ? Kind.LONG_DROP : Kind.BAR), List.of(Kind.DIAGONAL_GAP));
        kinds.add(Kind.PRECISION);

        var pads = new ArrayList<Pad>(JUMPS + 1);
        var jumps = new ArrayList<Jump>(JUMPS);
        pads.add(new Pad(0, 0, START_FLOOR, Shape.TERRACE, 0));
        for (int tier = 0; tier < LANDINGS.size(); tier++) {
            var shapes = new ArrayList<>(LANDINGS.get(tier));
            Collections.shuffle(shapes, random);
            for (int i = 0; i < 5; i++) {
                Kind kind = kinds.get(jumps.size());
                Shape shape = kind == Kind.PRECISION ? Shape.PIN : shapes.get(i % shapes.size());
                place(pads, jumps, kind, shape, tier, random);
            }
        }
        return new DropTowerCourse(pads, jumps);
    }

    // Slot level gaps after different descents so no three checkpoints can share a height.
    private static void band(List<Kind> result, Random random, List<Kind> descents, List<Kind> gaps) {
        var falls = new ArrayList<>(descents);
        var takeoffs = new ArrayList<>(gaps);
        var slots = new ArrayList<Integer>();
        for (int i = 0; i < falls.size(); i++) slots.add(i);
        Collections.shuffle(falls, random);
        Collections.shuffle(slots, random);
        Collections.shuffle(takeoffs, random);
        for (int i = 0; i < falls.size(); i++) {
            result.add(falls.get(i));
            int gap = slots.indexOf(i);
            if (gap < takeoffs.size()) result.add(takeoffs.get(gap));
        }
    }

    private static void place(List<Pad> pads, List<Jump> jumps, Kind kind, Shape shape, int tier, Random random) {
        Pad from = pads.getLast();
        int rotation = random.nextInt(4);
        int drop = switch (kind) {
            case GAP, DIAGONAL_GAP -> 0;
            case DRIFT -> 9 + random.nextInt(3);
            case DIAGONAL_DROP -> 12 + tier + random.nextInt(3);
            case LONG_DROP -> 19 + tier + random.nextInt(3);
            case GATE -> 13 + tier;
            case BAR -> 15 + tier;
            case OFFSET_GATE -> 18 + tier;
            case SLALOM -> 36;
            case PRECISION -> 18;
        };
        int stride = switch (kind) {
            case DRIFT, PRECISION -> 4 + random.nextInt(2);
            case DIAGONAL_DROP -> 3 + random.nextInt(2);
            case LONG_DROP -> 6 + random.nextInt(2);
            case GATE, BAR, OFFSET_GATE, SLALOM -> 6;
            case GAP, DIAGONAL_GAP -> 1;
        };
        var headings = new ArrayList<>(kind.isDiagonal() ? DIAGONAL : CARDINAL);
        Collections.shuffle(headings, random);
        if (pads.size() > 1) {
            // Prefer a bend over immediately doubling back into the approach to the last pad.
            Pad previous = pads.get(pads.size() - 2);
            headings.sort(java.util.Comparator.comparingInt(h ->
                    (from.x - previous.x) * h.x + (from.z - previous.z) * h.z < 0 ? 1 : 0));
        }
        int preferredSide = random.nextBoolean() ? 1 : -1;
        for (Heading heading : headings) {
            int travel = stride;
            if (kind.isGap()) {
                int gapSquared = kind == Kind.DIAGONAL_GAP ? 2 : 4;
                while (tooClose(from, shape, rotation, travel * heading.x, travel * heading.z, gapSquared)) travel++;
            }
            int x = from.x + travel * heading.x, z = from.z + travel * heading.z;
            if (Math.abs(x) > CENTER_LIMIT || Math.abs(z) > CENTER_LIMIT) continue;
            int side = preferredSide;
            if (kind == Kind.SLALOM) {
                // Carry the turn out of the lower gate onto the landing. Mirror the weave near a lane wall.
                if (Math.abs(x + side * heading.z) > CENTER_LIMIT
                        || Math.abs(z - side * heading.x) > CENTER_LIMIT) side = -side;
                x += side * heading.z;
                z -= side * heading.x;
            }
            Pad target = new Pad(x, z, from.floor - drop, shape, rotation);
            List<Baffle> baffles = obstacles(kind, from, heading, side);
            pads.add(target);
            jumps.add(new Jump(kind, baffles));
            return;
        }
        throw new IllegalStateException("No legal heading from " + from + " for " + kind);
    }

    private static boolean tooClose(Pad start, Shape shape, int rotation, int dx, int dz, int gapSquared) {
        for (int x = -start.radius(); x <= start.radius(); x++) for (int z = -start.radius(); z <= start.radius(); z++) {
            if (!start.tile(x, z)) continue;
            for (int u = -shape.radius; u <= shape.radius; u++) for (int v = -shape.radius; v <= shape.radius; v++) {
                if (!shape.tile(u, v, rotation)) continue;
                int airX = Math.max(0, Math.abs(dx + u - x) - 1);
                int airZ = Math.max(0, Math.abs(dz + v - z) - 1);
                if (airX * airX + airZ * airZ < gapSquared) return true;
            }
        }
        return false;
    }

    private static List<Baffle> obstacles(Kind kind, Pad start, Heading h, int side) {
        int y = start.floor;
        return switch (kind) {
            case GATE -> List.of(h.bar(start, y - 5, 4, 4, -3, -2), h.bar(start, y - 5, 4, 4, 2, 3));
            case OFFSET_GATE -> List.of(h.bar(start, y - 7, 4, 4, -3 * side, 0),
                    h.bar(start, y - 7, 4, 4, 3 * side, 3 * side));
            case BAR -> List.of(h.bar(start, y - 6, 3, 4, 0, 0));
            case SLALOM -> List.of(h.bar(start, y - 5, 3, 3, -3 * side, -side),
                    h.bar(start, y - 5, 3, 3, 3 * side, 3 * side),
                    h.bar(start, y - 25, 5, 5, 0, 3 * side),
                    h.bar(start, y - 25, 5, 5, -3 * side, -3 * side));
            default -> List.of();
        };
    }
}
