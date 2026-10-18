/*
 * Based on PillarPeril by MarcPG (https://github.com/MarcPG1905/PillarPeril, Apache-2.0),
 * via the fork at https://github.com/Petr1Furious/PillarPeril. Rewritten in Java for
 * Potato Minigames by Claude, and modified; see NOTICE.
 */
package me.petr1furious.potatominigames.games.pillars;

import java.util.Locale;
import java.util.Map;

/** Slime template keeps the original world's coordinates; each game uses a throwaway copy. */
public record PillarsArena(String name, String template, double x, double z, Long time,
        Map<String, Object> gameRules) {

    /** Names accepted by {@code /time set}. */
    private static final Map<String, Long> NAMED_TIMES = Map.of(
            "day", 1000L,
            "noon", 6000L,
            "sunset", 12000L,
            "night", 13000L,
            "midnight", 18000L,
            "sunrise", 23000L);

    /** Default template: {@code slime_worlds/pillars_<name>.slime}. */
    public static String templateFor(String arenaName) {
        return "pillars_" + arenaName.toLowerCase(Locale.ROOT);
    }

    /** Tick or {@code /time set} name; null if neither. */
    public static Long parseTimeOfDay(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        Long named = NAMED_TIMES.get(text);
        if (named != null) {
            return named;
        }
        try {
            return Math.floorMod(Long.parseLong(text), 24000L);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
