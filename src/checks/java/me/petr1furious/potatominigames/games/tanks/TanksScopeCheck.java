package me.petr1furious.potatominigames.games.tanks;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exercises scoped FOV ownership without native attribute registry initialization. */
public final class TanksScopeCheck {
    private static final NamespacedKey SCOPE = new NamespacedKey("potatominigames", "tanks_scope");
    private static final NamespacedKey OTHER = new NamespacedKey("other", "speed");

    public static void main(String[] args) {
        TanksScope scope = new TanksScope(player -> player.getAttribute(null));
        Pilot first = new Pilot(1);
        first.modifiers.put(
                OTHER, new AttributeModifier(OTHER, .15, AttributeModifier.Operation.ADD_SCALAR));
        check(
                scope.toggle(first.player, 0) && scope.active(first.player),
                "first toggle enables FOV scope");
        AttributeModifier zoom = first.modifiers.get(SCOPE);
        check(
                zoom != null
                        && zoom.getAmount() == -.8
                        && zoom.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                "scope uses native multiplicative FOV modifier");
        check(
                !scope.toggle(first.player, 0) && !scope.toggle(first.player, 3),
                "duplicate attack and interact events are debounced");
        check(
                scope.active(first.player) && first.additions == 1,
                "debounce neither toggles nor stacks the modifier");
        check(
                scope.toggle(first.player, 4) && !scope.active(first.player),
                "four ticks later toggles scope off");
        check(
                !first.modifiers.containsKey(SCOPE) && first.modifiers.containsKey(OTHER),
                "toggle off removes only the tank scope modifier");
        check(!scope.toggle(first.player, 5), "off transition is debounced too");
        check(scope.toggle(first.player, 8), "scope can be reentered");

        Pilot replacement = new Pilot(1);
        check(
                !scope.active(replacement.player),
                "a new Player transport does not inherit old scope state");
        check(
                scope.toggle(replacement.player, 9) && scope.active(replacement.player),
                "reconnect gets independent debounce and scope state");
        check(!first.modifiers.containsKey(SCOPE), "reconnect cleans the old Player transport");
        scope.stop(first.player);
        check(
                scope.active(replacement.player) && replacement.modifiers.containsKey(SCOPE),
                "stale connection cleanup cannot stop its replacement");
        scope.stop(replacement.player);
        scope.stop(replacement.player);
        check(
                !scope.active(replacement.player) && replacement.modifiers.isEmpty(),
                "stop is idempotent");
        check(
                scope.toggle(replacement.player, 9),
                "explicit stop clears debounce for the next lifecycle");
        Pilot second = new Pilot(2);
        check(scope.toggle(second.player, 10), "another player scopes independently");
        scope.clear();
        scope.clear();
        check(
                !scope.active(second.player)
                        && !scope.active(replacement.player)
                        && !second.modifiers.containsKey(SCOPE)
                        && !replacement.modifiers.containsKey(SCOPE),
                "clear removes every owned scope safely");
        check(first.modifiers.containsKey(OTHER), "cleanup preserves external modifiers");
        check(
                first.walkWrites + replacement.walkWrites + second.walkWrites == 0,
                "vehicle scope never changes player walk speed");
        TanksScope unsupported = new TanksScope(player -> null);
        check(
                !unsupported.toggle(first.player, 1) && !unsupported.active(first.player),
                "missing attribute fails cleanly");
        System.out.println("Tanks scope FOV ownership checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Pilot {
        final Map<NamespacedKey, AttributeModifier> modifiers = new HashMap<>();
        int additions, walkWrites;
        final AttributeInstance attribute =
                (AttributeInstance)
                        Proxy.newProxyInstance(
                                AttributeInstance.class.getClassLoader(),
                                new Class<?>[] {AttributeInstance.class},
                                (object, method, args) ->
                                        switch (method.getName()) {
                                            case "getModifier" -> modifiers.get(args[0]);
                                            case "getModifiers" -> List.copyOf(modifiers.values());
                                            case "removeModifier" -> {
                                                modifiers.remove(args[0]);
                                                yield null;
                                            }
                                            case "addTransientModifier" -> {
                                                AttributeModifier modifier =
                                                        (AttributeModifier) args[0];
                                                if (modifiers.putIfAbsent(
                                                                modifier.getKey(), modifier)
                                                        != null)
                                                    throw new AssertionError("stacked scope key");
                                                additions++;
                                                yield null;
                                            }
                                            default ->
                                                    throw new AssertionError(
                                                            "Attribute: " + method.getName());
                                        });
        final Player player;

        Pilot(long id) {
            player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (object, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> new UUID(0, id);
                                                case "getAttribute" -> attribute;
                                                case "setWalkSpeed" -> {
                                                    walkWrites++;
                                                    yield null;
                                                }
                                                case "equals" -> object == args[0];
                                                case "hashCode" -> System.identityHashCode(object);
                                                default ->
                                                        throw new AssertionError(
                                                                "Player: " + method.getName());
                                            });
        }
    }
}
