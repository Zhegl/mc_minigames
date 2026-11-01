package me.petr1furious.potatominigames.core.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.lobby.LobbyService;
import me.petr1furious.potatominigames.core.player.PlayerStateService;
import me.petr1furious.potatominigames.core.score.PointsService;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.core.team.TeamRegistry;
import me.petr1furious.potatominigames.core.team.TeamService;
import me.petr1furious.potatominigames.core.world.SessionWorlds;
import me.petr1furious.potatominigames.core.world.SlimeWorldService;
import me.petr1furious.potatominigames.core.world.SpawnFinder;
import me.petr1furious.potatominigames.core.world.ThrowawayWorldService;

/** Running games, at most one per game type. Inert for players not in a session. */
public final class SessionManager {
    private final JavaPlugin plugin;
    private final PlayerStateService playerState;
    private final TeamRegistry teamRegistry;
    private final SlimeWorldService slimeWorlds;
    private final ThrowawayWorldService throwawayWorlds;
    private final SpawnFinder spawnFinder;
    private final PointsService points;
    private LobbyService lobby;

    private final Map<String, Minigame> games = new LinkedHashMap<>();
    private final List<Consumer<Collection<UUID>>> cleanupHooks = new ArrayList<>();
    private final List<Consumer<ActiveSession>> startListeners = new ArrayList<>();
    private final List<Consumer<ActiveSession>> endListeners = new ArrayList<>();
    private final List<Consumer<Player>> joinListeners = new ArrayList<>();

    private final Map<String, ActiveSession> sessions = new LinkedHashMap<>();
    /** Each player is in at most one session. */
    private final Map<UUID, ActiveSession> byPlayer = new HashMap<>();

    private final PropertyBag coreConfig = new PropertyBag("core");
    private final Property<Boolean> enableDummyGame = coreConfig.define("enableDummyGame", PropertyCodec.BOOL, false);

    public SessionManager(JavaPlugin plugin, PlayerStateService playerState, TeamRegistry teamRegistry,
            SlimeWorldService slimeWorlds, ThrowawayWorldService throwawayWorlds, SpawnFinder spawnFinder,
            PointsService points) {
        this.plugin = plugin;
        this.playerState = playerState;
        this.teamRegistry = teamRegistry;
        this.slimeWorlds = slimeWorlds;
        this.throwawayWorlds = throwawayWorlds;
        this.spawnFinder = spawnFinder;
        this.points = points;
    }

    /** Set after construction; the lobby depends on the manager. */
    public void setLobby(LobbyService lobby) {
        this.lobby = lobby;
    }

    public PropertyBag coreConfig() {
        return coreConfig;
    }

    public boolean isDummyGameEnabled() {
        return enableDummyGame.get();
    }

    public void registerGame(Minigame game) {
        games.put(game.id(), game);
    }

    public List<Minigame> games() {
        return List.copyOf(games.values());
    }

    public Minigame game(String id) {
        return games.get(id);
    }

    public void addCleanupHook(Consumer<Collection<UUID>> hook) {
        cleanupHooks.add(hook);
    }

    public void addStartListener(Consumer<ActiveSession> listener) {
        startListeners.add(listener);
    }

    public void addEndListener(Consumer<ActiveSession> listener) {
        endListeners.add(listener);
    }

    public void addJoinListener(Consumer<Player> listener) {
        joinListeners.add(listener);
    }

    SpawnFinder spawnFinder() {
        return spawnFinder;
    }

    PointsService points() {
        return points;
    }

    public Optional<ActiveSession> sessionOf(Minigame game) {
        return Optional.ofNullable(sessions.get(game.id()));
    }

    public Optional<ActiveSession> sessionOf(Player player) {
        return Optional.ofNullable(byPlayer.get(player.getUniqueId()));
    }

    public Optional<ActiveSession> sessionOf(UUID playerId) {
        return Optional.ofNullable(byPlayer.get(playerId));
    }

    public Optional<ActiveSession> sessionOwning(World world) {
        for (ActiveSession session : sessions.values()) {
            if (session.owns(world)) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
    }

    public List<ActiveSession> activeSessions() {
        return List.copyOf(sessions.values());
    }

    /** Playing, not spectating. */
    public boolean isPlaying(Player player) {
        ActiveSession session = byPlayer.get(player.getUniqueId());
        return session != null && session.isParticipant(player) && session.phase() != GamePhase.IDLE;
    }

    /** Playing or spectating. */
    public boolean isBusy(Player player) {
        return byPlayer.containsKey(player.getUniqueId());
    }

    void index(Player player, ActiveSession session) {
        byPlayer.put(player.getUniqueId(), session);
        for (Consumer<Player> listener : joinListeners) {
            listener.accept(player);
        }
    }

    void unindex(UUID playerId, ActiveSession session) {
        byPlayer.remove(playerId, session);
    }

    public ActiveSession start(Minigame game, String mode, List<Player> roster, Map<UUID, TeamColor> teamPicks,
            boolean queued) throws CommandFailure {
        return start(game, mode, roster, teamPicks, queued, Map.of());
    }

    /**
     * @param mode {@code ""} for single-mode games
     * @param teamPicks queue colour picks; empty for admin starts
     * @param queued saved colours apply only when false
     * @param options game-specific, e.g. {@code arena} for pillars, {@code difficulty} for bingo
     */
    public ActiveSession start(Minigame game, String mode, List<Player> roster, Map<UUID, TeamColor> teamPicks,
            boolean queued, Map<String, String> options) throws CommandFailure {
        ActiveSession running = sessions.get(game.id());
        if (running != null) {
            throw new CommandFailure(Component.text()
                    .append(running.displayName())
                    .append(Component.text(" is already " + running.phase().name().toLowerCase() + ".",
                            NamedTextColor.RED))
                    .build());
        }
        if (roster.isEmpty()) {
            throw new CommandFailure("No players to start the game with.");
        }
        for (Player player : roster) {
            ActiveSession other = byPlayer.get(player.getUniqueId());
            if (other != null && other.isParticipant(player)) {
                throw new CommandFailure(player.getName() + " is already playing "
                        + other.game().id() + ".");
            }
        }
        // Spectators of another game stop watching.
        for (Player player : roster) {
            ActiveSession other = byPlayer.get(player.getUniqueId());
            if (other != null) {
                other.stopSpectating(player, false);
            }
        }

        ActiveSession session = new ActiveSession(plugin, this, game, mode, options, playerState, lobby,
                new TeamService(teamRegistry), new SessionWorlds(throwawayWorlds, slimeWorlds));
        sessions.put(game.id(), session);
        try {
            session.start(roster, teamPicks, queued);
        } catch (CommandFailure failure) {
            sessions.remove(game.id(), session);
            throw failure;
        }
        for (Consumer<ActiveSession> listener : startListeners) {
            listener.accept(session);
        }
        return session;
    }

    void onSessionEnded(ActiveSession session, Collection<UUID> involved) {
        sessions.remove(session.game().id(), session);
        for (UUID id : involved) {
            byPlayer.remove(id, session);
        }
        for (Consumer<ActiveSession> listener : endListeners) {
            listener.accept(session);
        }
    }

    void runCleanupHooks(Collection<UUID> players) {
        for (Consumer<Collection<UUID>> hook : cleanupHooks) {
            hook.accept(players);
        }
    }

    public void stopAll(EndReason reason) {
        for (ActiveSession session : List.copyOf(sessions.values())) {
            session.stopNow(reason);
        }
    }

    public void handleQuit(Player player) {
        ActiveSession session = byPlayer.get(player.getUniqueId());
        if (session != null) {
            session.handleQuit(player);
        }
    }

    /** @return whether the player is back in a running game, so nothing else should move them */
    public boolean handleJoin(Player player) {
        ActiveSession session = byPlayer.get(player.getUniqueId());
        if (session != null && session.isParticipant(player) && session.phase() != GamePhase.IDLE) {
            session.handleRejoin(player);
            return true;
        }
        return false;
    }

    /** "Bingo", or "Bingo (butfast)" for a game with several modes. */
    public static Component displayName(Minigame game, String mode) {
        if (!game.showModes() || mode == null || mode.isEmpty()) {
            return game.displayName();
        }
        return Component.text().append(game.displayName())
                .append(Component.text(" (" + mode + ")", NamedTextColor.GRAY)).build();
    }

    public String playCommand(Minigame game, String mode) {
        return !game.showModes() || mode == null || mode.isEmpty()
                ? "/play " + game.id()
                : "/play " + game.id() + " " + mode;
    }
}
