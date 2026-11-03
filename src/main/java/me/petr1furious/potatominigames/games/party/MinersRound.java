package me.petr1furious.potatominigames.games.party;

/** Match clock pauses at the mining boundary until every partition has been opened. */
final class MinersRound {
    enum Phase { BUILDING, COUNTDOWN, MINING, FIGHT, FINALE, RESULT, DONE }
    private final int countdown, preparation, combat, finale;
    private Phase phase = Phase.BUILDING;
    private long deadline;
    MinersRound(int countdownSeconds, int preparationSeconds, int combatSeconds, int finaleSeconds) {
        countdown = countdownSeconds * 20; preparation = preparationSeconds * 20;
        combat = combatSeconds * 20; finale = finaleSeconds * 20;
    }
    Phase phase() { return phase; }
    long deadline() { return deadline; }
    void ready(long tick) { if (phase == Phase.BUILDING) { phase = Phase.COUNTDOWN; deadline = tick + countdown; } }
    void advance(long tick, boolean opened) {
        if (tick < deadline) return;
        switch (phase) {
            case COUNTDOWN -> { phase = Phase.MINING; deadline = tick + preparation; }
            case MINING -> { if (opened) { phase = Phase.FIGHT; deadline = tick + combat; } }
            case FIGHT -> { phase = Phase.FINALE; deadline = tick + finale; }
            default -> { }
        }
    }
    boolean mining() { return phase == Phase.MINING || fighting(); }
    boolean fighting() { return phase == Phase.FIGHT || phase == Phase.FINALE; }
    long secondsLeft(long tick) { return Math.max(0, (deadline - tick + 19) / 20); }
    double pressure(long tick) { return phase == Phase.FINALE && tick >= deadline ? 2 + (tick - deadline) / 100 * 2 : 0; }
    void result(long tick) { if (phase != Phase.DONE && phase != Phase.RESULT) { phase = Phase.RESULT; deadline = tick + DeathPause.TICKS; } }
    void done() { phase = Phase.DONE; }
}
