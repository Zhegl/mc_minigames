package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.util.Vector;

/** No health damage: deterministic knockback, with an independent hurt window per victim. */
final class SumoCombat {
    static final int HIT_INTERVAL_TICKS = 8;
    private final Map<UUID, Integer> lastHit = new HashMap<>();

    boolean acceptHit(UUID victim, int tick) {
        Integer previous = lastHit.get(victim);
        if (previous != null && tick - previous < HIT_INTERVAL_TICKS) return false;
        lastHit.put(victim, tick);
        return true;
    }
    void reset() { lastHit.clear(); }

    static Vector knockback(Vector away, Vector current, boolean sprinting) {
        Vector direction = away.clone().setY(0);
        if (direction.lengthSquared() < 1.0e-6) direction = new Vector(1, 0, 0);
        Vector motion = direction.normalize().multiply(sprinting ? .65 : .5)
                .add(current.clone().setY(0).multiply(.25));
        if (motion.lengthSquared() > .8 * .8) motion.normalize().multiply(.8);
        motion.setY(Math.max(.34, Math.min(.42, current.getY() * .5 + .34)));
        return motion;
    }
}
