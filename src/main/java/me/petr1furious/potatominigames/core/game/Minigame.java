package me.petr1furious.potatominigames.core.game;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;

public interface Minigame {

    /** Stable identifier, also the command root name (e.g. "bingo", "war", "chase"). */
    String id();

    Component displayName();

    default boolean usesTeams() {
        return false;
    }

    default boolean usesGadgets() {
        return false;
    }

    GameSession createSession(SessionServices services);

    /** Adds game-specific subcommands to the game's command root. */
    default void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
