package me.petr1furious.potatominigames.games.party;

import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class JumpRopeSweep {
    static final int SCORED_SWEEPS = 50;
    static final double HALF_WIDTH = 6;
    static final double HALF_DEPTH = .14;
    static final double BOTTOM = .13;
    static final double HEIGHT = .29;
    private static final double END_Z = 9.5;
    private static final int MINIMUM_REST = 12;

    private JumpRopeSweep() {}

    static List<Pass> course(Random random) {
        List<Pass> passes = new ArrayList<>(SCORED_SWEEPS + 1);
        passes.add(new Pass(104, 30, 20, Pace.STEADY));
        Pace[] paces = Pace.values();
        for (int stage = 1; stage <= SCORED_SWEEPS; stage++) {
            Pace pace = stage == 1 ? Pace.STEADY : paces[random.nextInt(paces.length)];
            int travelBase = Math.max(18, 58 - (stage - 1) * 5);
            // Players at the wall still need enough time to land before jumping back.
            int restBase = Math.max(MINIMUM_REST, 28 - (stage - 1) * 2);
            int travel = travelBase + pace.travelOffset + random.nextInt(pace.travelChoices);
            int rest = restBase + pace.restOffset + random.nextInt(pace.restChoices);
            int warning = Math.max(6, 16 - (stage - 1) * 2);
            passes.add(new Pass(travel, rest, warning, pace));
        }
        return List.copyOf(passes);
    }

    static int durationSeconds(List<Pass> passes) {
        int total = passes.stream().mapToInt(pass -> pass.restTicks() + pass.travelTicks()).sum();
        return (total + 19) / 20 + 2;
    }

    enum Pace {
        STEADY(1, 4, 6, 12),
        RUSH(-3, 3, 0, 6),
        SURGE(2, 4, 4, 10),
        HESITATE(6, 4, 17, 15);

        private final int travelOffset;
        private final int travelChoices;
        private final int restOffset;
        private final int restChoices;

        Pace(int travelOffset, int travelChoices, int restOffset, int restChoices) {
            this.travelOffset = travelOffset;
            this.travelChoices = travelChoices;
            this.restOffset = restOffset;
            this.restChoices = restChoices;
        }

        private double progress(double time) {
            return switch (this) {
                case STEADY -> time;
                case RUSH -> time + .36 * time * (1 - time);
                case SURGE -> time - .48 * time * (1 - time);
                case HESITATE -> {
                    if (time < .3) yield time;
                    if (time < .6) yield .3 + .5 * (time - .3);
                    yield .45 + 1.375 * (time - .6);
                }
            };
        }
    }

    record Pass(int travelTicks, int restTicks, int warningTicks, Pace pace) {
        double position(int direction, long age) {
            if (age <= 0) return -direction * END_Z;
            if (age >= travelTicks) return direction * END_Z;
            double progress = pace.progress((double) age / travelTicks);
            return direction * END_Z * (2 * progress - 1);
        }
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
