package me.petr1furious.potatominigames.games.party;

import java.util.List;

final class DropTowerCourse {
    static final List<Pad> PADS =
            List.of(
                    new Pad(0, 0, 100, 2),
                    new Pad(5, 0, 88, 2),
                    new Pad(5, 5, 76, 2),
                    new Pad(0, 5, 64, 2),
                    new Pad(-5, 5, 52, 2),
                    new Pad(-5, 0, 40, 2));

    private DropTowerCourse() {}

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
