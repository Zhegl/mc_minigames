package me.petr1furious.potatominigames.games.tanks;

import org.bukkit.Particle;

/** Presentation only: the match's original smoke deadline remains authoritative. */
final class TanksSmokeVisual {
    // 26.3 SmokeParticle has at most40 lifetime ticks plus its removal tick; keep one tick of
    // margin.
    static final int CLIENT_TAIL_TICKS = 42, PARTICLES_PER_BURST = 72;

    enum Phase {
        ACTIVE,
        FADING,
        NONE
    }

    private TanksSmokeVisual() {}

    static Particle particle() {
        return Particle.SMOKE;
    }

    static boolean emits(long now, long until) {
        return now < until;
    }

    static Phase phase(long now, long until) {
        if (until <= 0 || now >= until + CLIENT_TAIL_TICKS) return Phase.NONE;
        return now < until ? Phase.ACTIVE : Phase.FADING;
    }
}
