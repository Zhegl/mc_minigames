package me.petr1furious.potatominigames.games.tanks;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Vanilla FOV zoom without a held-use item; vehicle motion is independent of walk speed. */
final class TanksScope {
    private static final NamespacedKey KEY = new NamespacedKey("potatominigames", "tanks_scope");

    private record Transition(Player player, long tick) {}

    private final Map<UUID, Player> scoped = new HashMap<>();
    private final Map<UUID, Transition> transitions = new HashMap<>();
    private final Function<Player, AttributeInstance> movementAttribute;

    TanksScope() {
        this(player -> player.getAttribute(Attribute.MOVEMENT_SPEED));
    }

    /** A narrow registry boundary lets server-free checks execute the real modifier lifecycle. */
    TanksScope(Function<Player, AttributeInstance> movementAttribute) {
        this.movementAttribute = movementAttribute;
    }

    /** Returns whether the toggle was accepted; active() reports the resulting state. */
    boolean toggle(Player player, long tick) {
        UUID id = player.getUniqueId();
        Player previous = scoped.get(id);
        if (previous != null && previous != player) stop(previous);
        Transition last = transitions.get(id);
        if (last != null && last.player == player && tick - last.tick < 4) return false;
        AttributeInstance attribute = movementAttribute.apply(player);
        if (attribute == null) return false;
        // Remove only this feature's key, including any leftover from a prior scope owner.
        if (attribute.getModifier(KEY) != null) attribute.removeModifier(KEY);
        if (scoped.get(id) == player) scoped.remove(id);
        else {
            attribute.addTransientModifier(
                    new AttributeModifier(KEY, -.8, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            scoped.put(id, player);
        }
        transitions.put(id, new Transition(player, tick));
        return true;
    }

    boolean active(Player player) {
        return scoped.get(player.getUniqueId()) == player;
    }

    void stop(Player player) {
        UUID id = player.getUniqueId();
        if (scoped.get(id) == player) scoped.remove(id);
        Transition last = transitions.get(id);
        if (last != null && last.player == player) transitions.remove(id);
        AttributeInstance attribute = movementAttribute.apply(player);
        if (attribute != null && attribute.getModifier(KEY) != null) attribute.removeModifier(KEY);
    }

    void clear() {
        for (Player player : List.copyOf(scoped.values())) stop(player);
        transitions.clear();
    }
}
