package me.petr1furious.potatominigames.core.text;

import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Message styling plus broadcasting. The server-wide instance broadcasts to
 * everyone; each game session gets its own instance whose broadcasts reach only
 * that game's players and spectators, so two games running side by side do not
 * talk over each other.
 */
public final class Messages {
    private final Supplier<Audience> audience;

    /** Broadcasts to the whole server. */
    public Messages() {
        this(Bukkit::getServer);
    }

    public Messages(Supplier<Audience> audience) {
        this.audience = audience;
    }

    public Component success(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    public Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    public Component info(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

    public Component highlight(String message) {
        return Component.text(message, NamedTextColor.GOLD);
    }

    public void broadcast(Component message) {
        audience.get().sendMessage(message);
    }

    /** Always server-wide, also from a session's instance: start and result one-liners. */
    public void broadcastGlobal(Component message) {
        Bukkit.getServer().sendMessage(message);
    }

    public void broadcastActionBar(Iterable<? extends Player> players, Component message) {
        for (Player player : players) {
            player.sendActionBar(message);
        }
    }

    public void playSound(Iterable<? extends Player> players, Sound sound, float volume, float pitch) {
        for (Player player : players) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}
