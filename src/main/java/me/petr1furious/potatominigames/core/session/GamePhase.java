package me.petr1furious.potatominigames.core.session;

public enum GamePhase {
    IDLE,
    RUNNING,
    /** The game is over and results are showing; players return after the grace period. */
    ENDING;

    public boolean active() {
        return this != IDLE;
    }
}
