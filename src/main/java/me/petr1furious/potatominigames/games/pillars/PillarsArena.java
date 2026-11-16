/*
 * Based on PillarPeril by MarcPG (https://github.com/MarcPG1905/PillarPeril, Apache-2.0),
 * via the fork at https://github.com/Petr1Furious/PillarPeril. Rewritten in Java for
 * Potato Minigames by Claude, and modified; see NOTICE.
 */
package me.petr1furious.potatominigames.games.pillars;

import java.util.Locale;
import java.util.Map;

/** Slime template keeps the original world's coordinates; each game uses a throwaway copy. */
public record PillarsArena(String name, String template, double x, double z, Map<String, Object> gameRules) {


    /** Default template: {@code slime_worlds/pillars_<name>.slime}. */
    public static String templateFor(String arenaName) {
        return "pillars_" + arenaName.toLowerCase(Locale.ROOT);
    }

}
