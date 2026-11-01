package me.petr1furious.potatominigames.games.party;

import java.util.UUID;

/** The fuse belongs to the mine, never to its item or projectile representation. */
final class TimedMine {
    enum State { GROUND, CARRIED, THROWN, SPENT }
    final long id;
    final long expiresAt;
    private State state = State.GROUND;
    private UUID carrier;

    TimedMine(long id, long spawnedAt, int fuseTicks) {
        this.id = id;
        expiresAt = spawnedAt + fuseTicks;
    }
    State state() { return state; }
    UUID carrier() { return carrier; }
    boolean pickup(UUID player, long tick) {
        if (state != State.GROUND || tick >= expiresAt) return false;
        carrier = player; state = State.CARRIED; return true;
    }
    boolean throwBy(UUID player, long tick) {
        if (state != State.CARRIED || !player.equals(carrier) || tick >= expiresAt) return false;
        carrier = null; state = State.THROWN; return true;
    }
    boolean land(long tick) {
        if (state != State.THROWN || tick >= expiresAt) return false;
        state = State.GROUND; return true;
    }
    void drop() { if (state == State.CARRIED) { state = State.GROUND; carrier = null; } }
    boolean expire(long tick) {
        if (state == State.SPENT || tick < expiresAt) return false;
        state = State.SPENT;
        return true;
    }
}
