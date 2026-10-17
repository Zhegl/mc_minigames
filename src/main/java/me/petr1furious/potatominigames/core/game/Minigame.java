package me.petr1furious.potatominigames.core.game;

import java.util.List;

import org.bukkit.Material;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;

public interface Minigame {

    /** Stable identifier, also the command root name (e.g. "bingo", "war", "chase"). */
    String id();

    Component displayName();

    /**
     * The game's modes, each of which gets a queue of its own. A game with a single
     * way to play returns one entry, which is never shown to players.
     */
    default List<String> modes() {
        return List.of("default");
    }

    /** The mode an admin's plain {@code /<game> start} plays: usually the configured one. */
    default String defaultMode() {
        return modes().getFirst();
    }

    /** What the game looks like in the lobby menu. */
    default Material icon() {
        return Material.PAPER;
    }

    /** One line for the lobby menu and {@code /play}: what the game is about. */
    default Component description(String mode) {
        return Component.empty();
    }

    /** How many players a queue for this mode needs before it counts down (configurable per queue). */
    default int minPlayers(String mode) {
        return 2;
    }

    /** The most players one game takes from its queue; 0 for no limit (configurable per queue). */
    default int maxPlayers(String mode) {
        return 0;
    }

    default boolean usesTeams() {
        return false;
    }

    default boolean usesGadgets() {
        return false;
    }

    /** Creates the session for one play-through in the given mode (one of {@link #modes()}). */
    GameSession createSession(SessionServices services, String mode);

    /** Adds game-specific subcommands to the game's command root. */
    default void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
