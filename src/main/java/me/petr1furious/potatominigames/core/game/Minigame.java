package me.petr1furious.potatominigames.core.game;

import java.util.List;

import org.bukkit.Material;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;

import me.petr1furious.potatominigames.core.team.TeamColor;

public interface Minigame {

    /** Also the command root, e.g. "bingo". */
    String id();

    Component displayName();

    /** One queue per mode. Single modes are hidden unless showModes() is overridden. */
    default List<String> modes() {
        return List.of("default");
    }

    /** Used by a plain {@code /<game> start}. */
    default String defaultMode() {
        return modes().getFirst();
    }

    /** Categories may expose named modes in menus and commands even with just one game. */
    default boolean showModes() {
        return modes().size() > 1;
    }

    default Material icon() {
        return Material.PAPER;
    }

    /** One line for the lobby menu and {@code /play}. */
    default Component description(String mode) {
        return Component.empty();
    }

    /** Default; configurable per queue. */
    default int minPlayers(String mode) {
        return 2;
    }

    /** 0 for no limit; configurable per queue. */
    default int maxPlayers(String mode) {
        return 0;
    }

    default boolean usesTeams() {
        return false;
    }

    /** Colours players may pick or be balanced onto; empty without teams. */
    default List<TeamColor> teamColors(String mode) {
        return usesTeams() ? List.of(TeamColor.values()) : List.of();
    }

    /** Levels queued players vote on, easiest first; the winner is passed as option "difficulty". */
    default List<String> difficulties() {
        return List.of();
    }

    /** Used for "any", no votes, and to break ties. */
    default String defaultDifficulty() {
        List<String> levels = difficulties();
        return levels.isEmpty() ? null : levels.get(levels.size() / 2);
    }

    /**
     * What a mode's queue votes on, in menu order: by default the difficulty, if the game has
     * levels. A ballot with fewer than two choices is not shown.
     */
    default List<Ballot> ballots(String mode) {
        return difficulties().isEmpty() ? List.of() : List.of(Ballot.difficulty(this));
    }

    default boolean usesGadgets() {
        return false;
    }

    /** Start options, e.g. a forced arena, are in {@link SessionServices#option}. */
    GameSession createSession(SessionServices services, String mode);

    /** The game-specific last argument of {@code /<game> start}; null for none. */
    default StartOption startOption() {
        return null;
    }

    default void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
