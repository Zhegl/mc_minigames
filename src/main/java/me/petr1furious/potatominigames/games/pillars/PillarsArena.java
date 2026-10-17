/*
 * Based on PillarPeril by MarcPG (https://github.com/MarcPG1905/PillarPeril, Apache-2.0),
 * via the fork at https://github.com/Petr1Furious/PillarPeril. Rewritten in Java for
 * Potato Minigames by Claude, and modified; see NOTICE.
 */
package me.petr1furious.potatominigames.games.pillars;

import java.util.Locale;
import java.util.Map;

/**
 * A place for pillars games: a slime template (cut from the old arena world, so it
 * keeps that world's coordinates), the arena's center within it, and optionally the
 * time of day and game rules it plays with.
 *
 * <p>Every game plays in its own in-memory copy of the template, which is thrown
 * away afterwards, so nothing a game does to an arena ever sticks.
 */
public record PillarsArena(String name, String template, double x, double z, Long time,
        Map<String, Object> gameRules) {

    /** The times of day {@code /time set} accepts by name, so arenas are configured the same way. */
    private static final Map<String, Long> NAMED_TIMES = Map.of(
            "day", 1000L,
            "noon", 6000L,
            "sunset", 12000L,
            "night", 13000L,
            "midnight", 18000L,
            "sunrise", 23000L);

    /** Where an arena's template lives unless it says otherwise: {@code slime_worlds/pillars_<name>.slime}. */
    public static String templateFor(String arenaName) {
        return "pillars_" + arenaName.toLowerCase(Locale.ROOT);
    }

    /** A tick within the day or one of the names {@code /time set} knows; null if neither. */
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
