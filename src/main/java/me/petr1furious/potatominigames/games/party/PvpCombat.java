package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Class passives have separate cooldowns; no lasting player attributes are modified. */
final class PvpCombat {
    private final Map<UUID, Long> backstabReady = new HashMap<>();
    private final Map<UUID, Long> leechReady = new HashMap<>();
    private final Map<UUID, Long> frostReady = new HashMap<>();

    boolean backstab(UUID player, long second, Vector facing, Vector towardsAttacker) {
        if (second < backstabReady.getOrDefault(player, 0L) || !isBehind(facing, towardsAttacker)) return false;
        backstabReady.put(player, second + 5);
        return true;
    }
    boolean leech(UUID player, long second) {
        if (second < leechReady.getOrDefault(player, 0L)) return false;
        leechReady.put(player, second + 3);
        return true;
    }
    boolean frost(UUID player, long second) {
        if (second < frostReady.getOrDefault(player, 0L)) return false;
        frostReady.put(player, second + 2);
        return true;
    }
    void forget(UUID player) { backstabReady.remove(player); leechReady.remove(player); frostReady.remove(player); }
    void reset() { backstabReady.clear(); leechReady.clear(); frostReady.clear(); }
    static boolean isBehind(Vector facing, Vector towardsAttacker) {
        Vector a = facing.clone().setY(0), b = towardsAttacker.clone().setY(0);
        return a.lengthSquared() > 1.0e-6 && b.lengthSquared() > 1.0e-6 && a.normalize().dot(b.normalize()) <= -.5;
    }
    static Vector dash(Vector facing, boolean backwards) {
        Vector direction = facing.clone().setY(0);
        if (direction.lengthSquared() < 1.0e-6) direction = new Vector(0, 0, 1);
        return direction.normalize().multiply(backwards ? -.9 : 1.05).setY(.2);
    }
    static boolean canPayFury(double health) { return health > 2; }
    static Vector launch(Vector current) {
        Vector motion = current.clone().setY(0).multiply(.5);
        if (motion.lengthSquared() > .6 * .6) motion.normalize().multiply(.6);
        return motion.setY(.9);
    }
    static void heal(Player player, double health) {
        var maximum = player.getAttribute(Attribute.MAX_HEALTH);
        double cap = maximum == null ? 20 : Math.min(20, maximum.getValue());
        player.setHealth(Math.min(cap, player.getHealth() + health));
    }
}
