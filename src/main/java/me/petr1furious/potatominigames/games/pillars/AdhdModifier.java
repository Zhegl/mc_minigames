package me.petr1furious.potatominigames.games.pillars;

import java.util.random.RandomGenerator;

/** Tick-based bursts; fractional shuffle intervals keep the requested threefold speed. */
final class AdhdModifier {
    enum Change { NONE, START, SHUFFLE, END }

    private final RandomGenerator random;
    private final double meanTicks;
    private final long durationTicks;
    /** One unit is a third of a server tick, so 10s / 3 cannot accumulate rounding drift. */
    private final long shufflePeriodUnits;
    private long tick;
    private long nextStart;
    private long endsAt;
    private long nextShuffleUnits;
    private boolean active;

    AdhdModifier(double meanSeconds, int durationSeconds, int shuffleSeconds, RandomGenerator random) {
        this.random = random;
        meanTicks = Math.max(1, meanSeconds) * 20;
        durationTicks = Math.max(1, durationSeconds) * 20L;
        shufflePeriodUnits = Math.max(1, shuffleSeconds) * 20L;
        nextStart = nextDelay();
    }

    Change tick() {
        tick++;
        if (active) {
            if (tick >= endsAt) {
                active = false;
                return Change.END;
            }
            if (tick * 3 >= nextShuffleUnits) {
                nextShuffleUnits += shufflePeriodUnits;
                return Change.SHUFFLE;
            }
        } else if (tick >= nextStart) {
            active = true;
            endsAt = tick + durationTicks;
            nextShuffleUnits = tick * 3 + shufflePeriodUnits;
            nextStart = tick + nextDelay();
            return Change.START;
        }
        return Change.NONE;
    }

    private long nextDelay() {
        // Starts are spread over ±25% of the mean, measured start-to-start, without overlap.
        return Math.max(durationTicks + 1, Math.round(meanTicks * (0.75 + random.nextDouble() * 0.5)));
    }

    boolean active() { return active; }
    long durationTicks() { return durationTicks; }
    long secondsLeft() { return Math.max(0, (endsAt - tick + 19) / 20); }
    long shuffleSecondsLeft() { return Math.max(0, (nextShuffleUnits - tick * 3 + 59) / 60); }
    float shuffleProgress() { return (float) Math.clamp((nextShuffleUnits - tick * 3) / (double) shufflePeriodUnits, 0, 1); }

    static double eventMean(double baseMean, double raveMean) {
        double accelerated = raveMean > 0 ? raveMean / 3 : 0;
        return baseMean > 0 && accelerated > 0 ? Math.min(baseMean, accelerated) : Math.max(baseMean, accelerated);
    }
}
