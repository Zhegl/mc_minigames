package me.petr1furious.potatominigames.core.text;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Messages {

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
