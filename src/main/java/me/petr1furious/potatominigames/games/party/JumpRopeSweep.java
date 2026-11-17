package me.petr1furious.potatominigames.games.party;

import org.bukkit.util.BoundingBox;

import java.util.Random;

final class JumpRopeSweep {
    static final int SCORED_SWEEPS = 50;
    static final double HALF_WIDTH = 6;
    static final double HALF_DEPTH = .14;
    static final double BOTTOM = .13;
    static final double HEIGHT = .29;
    private static final double END_Z = 9.5;

    private JumpRopeSweep() {}

    static int ticks(int stage) {
        return stage == 0 ? 104 : Math.max(29, 69 - (stage - 1));
    }

    static int pauseTicks(int stage, Random random) {
        int jitter = pauseJitter(stage);
        return minimumPause(stage) + (jitter == 0 ? 0 : random.nextInt(jitter + 1));
    }

    private static int minimumPause(int stage) {
        // Even at the end wall, a player has time to land before the reversal.
        return Math.max(14, 30 - 2 * ((stage - 1) / 4));
    }

    private static int pauseJitter(int stage) {
        return Math.min(12, (stage - 1) / 3);
    }

    static int warningTicks(int stage) {
        return Math.max(8, 20 - stage / 4);
    }

    static int durationSeconds() {
        int total = 0;
        for (int stage = 0; stage <= SCORED_SWEEPS; stage++) {
            total += minimumPause(stage) + pauseJitter(stage) + ticks(stage);
        }
        return (total + 19) / 20 + 2;
    }

    static double position(int direction, long age, int stage) {
        double progress = Math.clamp((double) age / ticks(stage), 0, 1);
        double acceleration = Math.clamp((stage - 7) / 90.0, 0, .36);
        progress -= acceleration * progress * (1 - progress);
        return direction * END_Z * (2 * progress - 1);
    }

    static boolean hits(BoundingBox from, BoundingBox to, double fromZ, double toZ, double floorY) {
        double bottom = floorY + BOTTOM;
        double top = bottom + HEIGHT;
        // In the rope's frame the player moves by both their own and the rope's movement.
        // Clip the tick down to the times all three axes overlap simultaneously.
        Contact contact = new Contact();
        return contact.clip(from.getMaxX() + HALF_WIDTH, to.getMaxX() + HALF_WIDTH)
                && contact.clip(HALF_WIDTH - from.getMinX(), HALF_WIDTH - to.getMinX())
                && contact.clip(from.getMaxY() - bottom, to.getMaxY() - bottom)
                && contact.clip(top - from.getMinY(), top - to.getMinY())
                && contact.clip(
                        from.getMaxZ() - fromZ + HALF_DEPTH, to.getMaxZ() - toZ + HALF_DEPTH)
                && contact.clip(
                        fromZ + HALF_DEPTH - from.getMinZ(), toZ + HALF_DEPTH - to.getMinZ());
    }

    private static final class Contact {
        private double enter;
        private double leave = 1;

        boolean clip(double start, double end) {
            if (start <= 0 && end <= 0) return false;
            if (start <= 0) enter = Math.max(enter, start / (start - end));
            else if (end <= 0) leave = Math.min(leave, start / (start - end));
            return enter < leave;
        }
    }
}
