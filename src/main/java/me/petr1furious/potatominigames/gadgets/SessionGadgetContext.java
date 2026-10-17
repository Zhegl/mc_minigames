package me.petr1furious.potatominigames.gadgets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.lobby.LobbyService;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.GamePhase;
import me.petr1furious.potatominigames.core.session.SessionManager;

/**
 * Gadget context backed by the session the player is playing in; outside games
 * it falls back to "any other non-spectator player who is not in a game either".
 */
public final class SessionGadgetContext implements GadgetContext {
    private final Plugin plugin;
    private final SessionManager sessions;
    private LobbyService lobby;

    public SessionGadgetContext(Plugin plugin, SessionManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    public void setLobby(LobbyService lobby) {
        this.lobby = lobby;
    }

    @Override
    public Plugin plugin() {
        return plugin;
    }

    /** The running game the player plays in (spectators have none). */
    private Optional<GameSession> playing(Player player) {
        return sessions.sessionOf(player)
                .filter(session -> session.phase() == GamePhase.RUNNING && session.isParticipant(player))
                .map(ActiveSession::gameSession);
    }

    @Override
    public boolean inGame(Player player) {
        return playing(player).isPresent();
    }

    @Override
    public boolean inLobby(Player player) {
        return lobby != null && lobby.isLobby(player.getWorld());
    }

    @Override
    public boolean friendly(Player first, Player second) {
        return playing(first).map(session -> session.arePlayersFriendly(first, second)).orElse(false);
    }

    @Override
    public Collection<Player> targetsFor(Player user) {
        Optional<GameSession> session = playing(user);
        if (session.isPresent()) {
            return session.get().getTrackedPlayers(user);
        }
        List<Player> targets = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != user && player.getGameMode() != GameMode.SPECTATOR && !sessions.isBusy(player)) {
                targets.add(player);
            }
        }
        return targets;
    }

    @Override
    public boolean hideTargetNames(Player viewer) {
        return playing(viewer).map(session -> session.shouldHideTrackedPlayerNames(viewer)).orElse(false);
    }
}
