package me.petr1furious.potatominigames.core.text;

import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Message styling and broadcasting; session instances reach only that game's audience. */
public final class Messages {
    private final Supplier<Audience> audience;
    private final Component prefix;

    public Messages() {
        this(Bukkit::getServer, null);
    }

    /** @param prefix put before every broadcast, e.g. {@link #tag}; null for none */
    public Messages(Supplier<Audience> audience, Component prefix) {
        this.audience = audience;
        this.prefix = prefix;
    }

    /** "[Pillars] " with the name in its own colour. */
    public static Component tag(Component name) {
        return Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(name)
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .build();
    }

    public Component prefixed(Component message) {
        return prefix == null ? message : Component.text().append(prefix).append(message).build();
    }

    /** Sends one message with this instance's prefix. */
    public void send(Audience target, Component message) {
        target.sendMessage(prefixed(message));
    }

    /** "1 player", "3 players". */
    public static String count(int amount, String noun) {
        return amount + " " + noun + (amount == 1 ? "" : "s");
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
        audience.get().sendMessage(prefixed(message));
    }

    /** Always server-wide, even from a session's instance. */
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
