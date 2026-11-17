package me.petr1furious.potatominigames.games.party;

import java.util.List;
import java.util.Map;

final class DropTowerCourse {
    static final int LANE_RADIUS = 12;
    private static final int LANE_SPACING = 28;
    static final List<Pad> PADS =
            List.of(
                    new Pad(0, 0, 198, 2),
                    new Pad(5, 0, 186, 2),
                    new Pad(5, 5, 174, 2),
                    new Pad(0, 7, 159, 1),
                    new Pad(-5, 7, 145, 1),
                    new Pad(-7, 2, 131, 1),
                    new Pad(-1, 0, 116, 1),
                    new Pad(5, 0, 101, 1),
                    new Pad(8, 4, 85, 0),
                    new Pad(4, 8, 68, 0),
                    new Pad(-2, 8, 51, 0),
                    new Pad(-7, 3, 34, 1));
    static final Map<Integer, Baffle> BAFFLES =
            Map.of(3, new Baffle(-4, -2, 154, 7), 6, new Baffle(1, 4, 110, 0));

    private DropTowerCourse() {}

    record Lane(int x, int z) {
        static Lane at(int index) {
            return new Lane(index % 3 * LANE_SPACING, index / 3 * LANE_SPACING);
        }

        boolean contains(double worldX, double worldZ) {
            return Math.abs(worldX - x - .5) < LANE_RADIUS
                    && Math.abs(worldZ - z - .5) < LANE_RADIUS;
        }
    }

    record Baffle(int minX, int maxX, int floor, int z) {
        boolean overlaps(double x, double z) {
            return x > minX - .3 && x < maxX + 1.3 && Math.abs(z - this.z - .5) < .8;
        }

        boolean caught(double x, double y, double z) {
            return Math.abs(y - floor - 1) <= .05 && overlaps(x, z);
        }
    }

    record Pad(int x, int z, int floor, int radius) {
        boolean landed(double x, double y, double z) {
            return Math.abs(y - floor - 1) <= .05
                    && Math.abs(x - this.x - .5) < radius + .5
                    && Math.abs(z - this.z - .5) < radius + .5;
        }

        boolean missed(double y) {
            return y < floor + .9;
        }
    }
}
