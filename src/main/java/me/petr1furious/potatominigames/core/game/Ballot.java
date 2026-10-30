package me.petr1furious.potatominigames.core.game;

import java.util.List;
import java.util.function.Supplier;

import org.bukkit.Material;

/**
 * Something queued players vote on, e.g. the bingo difficulty or the warfare map. The winner
 * reaches the session as {@link SessionServices#option option(key)}.
 *
 * @param key the session option, and the {@code /play <key>} command
 * @param title shown in menus and messages, e.g. "difficulty"
 * @param choices in menu order
 * @param fallback wins with no votes and breaks ties; null picks at random among the tied
 * @param icons spread over the choices, first to last
 */
public record Ballot(String key, String title, Supplier<List<String>> choices, Supplier<String> fallback,
        List<Material> icons) {

    /** {@code /play <key> any} drops the vote. */
    public static final String ANY = "any";

    /** Spread over the levels, easiest first. */
    private static final List<Material> DIFFICULTY_ICONS = List.of(Material.LIME_CONCRETE, Material.YELLOW_CONCRETE,
            Material.ORANGE_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE);

    /** The game's {@link Minigame#difficulties}, passed as option "difficulty". */
    public static Ballot difficulty(Minigame game) {
        return new Ballot("difficulty", "difficulty", game::difficulties, game::defaultDifficulty, DIFFICULTY_ICONS);
    }

    public Material icon(int index, int count) {
        if (icons.isEmpty()) {
            return Material.PAPER;
        }
        return icons.get(count <= 1 ? 0 : index * (icons.size() - 1) / (count - 1));
    }
}
