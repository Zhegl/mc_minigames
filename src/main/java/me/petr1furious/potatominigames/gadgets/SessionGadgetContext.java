package me.petr1furious.potatominigames.gadgets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.petr1furious.potatominigames.core.session.SessionManager;

/**
 * Gadget context backed by the active session when one is running; outside
 * games it falls back to "any other non-spectator player" targeting.
 */
public final class SessionGadgetContext implements GadgetContext {
    private final Plugin plugin;
    private final SessionManager sessions;

    public SessionGadgetContext(Plugin plugin, SessionManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    @Override
    public Plugin plugin() {
        return plugin;
    }

    @Override
    public boolean gameRunning() {
        return sessions.isRunning();
    }

    @Override
    public boolean friendly(Player first, Player second) {
        if (sessions.isRunning()) {
            return sessions.activeSession().map(session -> session.arePlayersFriendly(first, second)).orElse(false);
        }
        return false;
    }

    @Override
    public Collection<Player> targetsFor(Player user) {
        if (sessions.isRunning()) {
            return sessions.activeSession().map(session -> session.getTrackedPlayers(user)).orElse(List.of());
        }
        List<Player> targets = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != user && player.getGameMode() != GameMode.SPECTATOR) {
                targets.add(player);
            }
        }
        return targets;
    }

    @Override
    public boolean hideTargetNames(Player viewer) {
        if (sessions.isRunning()) {
            return sessions.activeSession().map(session -> session.shouldHideTrackedPlayerNames(viewer))
                    .orElse(false);
        }
        return false;
    }
}
