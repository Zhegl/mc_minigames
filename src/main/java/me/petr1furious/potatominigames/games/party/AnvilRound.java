package me.petr1furious.potatominigames.games.party;

final class AnvilRound {
    private final int initial, rainTicks, moveTicks, waves, pvpWave;
    private int wave = 1;
    private long deadline;
    private boolean raining, done;

    AnvilRound(int players) {
        this(players, 120, 200, 10, 1);
    }

    AnvilRound(int players, int rainTicks, int moveTicks, int waves, int pvpWave) {
        initial = 4;
        this.rainTicks = rainTicks;
        this.moveTicks = moveTicks;
        this.waves = waves;
        this.pvpWave = pvpWave;
    }

    void start(long tick) {
        deadline = tick + relocationTicks();
    }

    boolean advance(long tick) {
        if (done || tick < deadline) return false;
        if (raining) {
            raining = false;
            if (wave >= waves) {
                done = true;
                return true;
            }
            wave++;
            deadline = tick + relocationTicks();
        } else {
            raining = true;
            deadline = tick + rainTicks;
        }
        return true;
    }

    int wave() {
        return wave;
    }

    int shelters() {
        return Math.max(1, initial - (wave - 1) / 2);
    }

    int shelterSize() {
        return wave >= 6 ? 1 : 2;
    }

    private int relocationTicks() {
        return Math.max(Math.min(moveTicks, 120), moveTicks - (wave - 1) * 20);
    }

    boolean raining() {
        return raining;
    }

    boolean done() {
        return done;
    }

    boolean pvpEnabled() {
        return wave >= pvpWave;
    }

    long remaining(long tick) {
        return Math.max(0, (deadline - tick + 19) / 20);
    }
}
