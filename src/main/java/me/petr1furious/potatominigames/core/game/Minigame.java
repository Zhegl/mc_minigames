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

    /** One queue per mode. A single mode is never shown to players. */
    default List<String> modes() {
        return List.of("default");
    }

    /** Used by a plain {@code /<game> start}. */
    default String defaultMode() {
        return modes().getFirst();
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

    default boolean usesGadgets() {
        return false;
    }

    GameSession createSession(SessionServices services, String mode);

    default void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
