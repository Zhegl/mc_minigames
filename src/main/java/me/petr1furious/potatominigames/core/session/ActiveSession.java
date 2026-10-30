package me.petr1furious.potatominigames.core.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.hud.BossBarService;
import me.petr1furious.potatominigames.core.hud.SidebarService;
import me.petr1furious.potatominigames.core.lobby.LobbyService;
import me.petr1furious.potatominigames.core.player.Participant;
import me.petr1furious.potatominigames.core.player.ParticipantRegistry;
import me.petr1furious.potatominigames.core.player.PlayerStateService;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.core.team.TeamService;
import me.petr1furious.potatominigames.core.text.Messages;
import me.petr1furious.potatominigames.core.text.Summary;
import me.petr1furious.potatominigames.core.world.ArenaService;
import me.petr1furious.potatominigames.core.world.SessionWorlds;

/**
 * One running game: RUNNING → ENDING → IDLE, then discarded. Sessions run side by side,
 * so HUDs, borders and {@link Messages} are scoped to this game's audience.
 */
public final class ActiveSession {
    /** Start option of {@code /<game> test}; see {@link #isTest}. */
    public static final String TEST_OPTION = "test";

    private final JavaPlugin plugin;
    private final SessionManager manager;
    private final Minigame game;
    private final String mode;
    private final Map<String, String> options;
    private final PlayerStateService playerState;
    private final LobbyService lobby;

    private final ParticipantRegistry participants = new ParticipantRegistry();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final SidebarService sidebar = new SidebarService(this::spectatorPlayers);
    private final BossBarService bossBars = new BossBarService(this::audiencePlayers);
    private final ArenaService arena = new ArenaService();
    private final Messages messages;
    private final TeamService teams;
    private final SessionWorlds worlds;

    private GameSession session;
    private GamePhase phase = GamePhase.IDLE;
    private BukkitTask ticker;
    private BukkitTask endingTask;
    private long elapsedSeconds;
    private boolean test;
    /** Set when the game ends; sent once everyone is back. */
    private List<Component> summaryLines;

    ActiveSession(JavaPlugin plugin, SessionManager manager, Minigame game, String mode,
            Map<String, String> options, PlayerStateService playerState, LobbyService lobby, TeamService teams,
            SessionWorlds worlds) {
        this.plugin = plugin;
        this.manager = manager;
        this.game = game;
        this.mode = mode;
        this.options = Map.copyOf(options);
        this.messages = new Messages(this::audience, Messages.tag(game.displayName()));
        this.playerState = playerState;
        this.lobby = lobby;
        this.teams = teams;
        this.worlds = worlds;
    }

    public Minigame game() {
        return game;
    }

    public String mode() {
        return mode;
    }

    /** See {@link SessionManager#start}. */
    public Map<String, String> options() {
        return options;
    }

    public Component displayName() {
        return SessionManager.displayName(game, mode);
    }

    public GamePhase phase() {
        return phase;
    }

    public boolean isRunning() {
        return phase == GamePhase.RUNNING;
    }

    /**
     * Started with {@code /<game> test} or by a single player: no points, and games keep going
     * where they would end for the last one standing.
     */
    public boolean isTest() {
        return test;
    }

    public long elapsedSeconds() {
        return elapsedSeconds;
    }

    public GameSession gameSession() {
        return session;
    }

    public ParticipantRegistry participants() {
        return participants;
    }

    public TeamService teams() {
        return teams;
    }

    public SidebarService sidebar() {
        return sidebar;
    }

    public SessionWorlds worlds() {
        return worlds;
    }

    public boolean isParticipant(Player player) {
        return participants.isParticipant(player);
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public boolean owns(World world) {
        return worlds.owns(world);
    }

    public List<Player> spectatorPlayers() {
        List<Player> result = new ArrayList<>();
        for (UUID id : spectators) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    /** Online participants and spectators. */
    public List<Player> audiencePlayers() {
        List<Player> result = new ArrayList<>(participants.onlinePlayers());
        result.addAll(spectatorPlayers());
        return result;
    }

    /** Who a watcher can follow: online participants still in the game, in its worlds, by name. */
    public List<Player> followTargets(Player viewer) {
        List<Player> result = new ArrayList<>();
        if (session == null) {
            return result;
        }
        for (Player player : participants.onlinePlayers()) {
            if (player != viewer && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                    && session.isInGame(player) && worlds.owns(player.getWorld())) {
                result.add(player);
            }
        }
        result.sort(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private Audience audience() {
        List<Audience> members = new ArrayList<>(audiencePlayers());
        members.add(Bukkit.getConsoleSender());
        return Audience.audience(members);
    }

    void start(List<Player> roster, Map<UUID, TeamColor> teamPicks, boolean queued) throws CommandFailure {
        // RUNNING from the start so a failure below gets cleaned up.
        phase = GamePhase.RUNNING;
        elapsedSeconds = 0;
        test = options.containsKey(TEST_OPTION) || roster.size() == 1;
        try {
            teams.configureStart(teamPicks, !queued, game.teamColors(mode));
            teams.attach(participants);
            session = game.createSession(new SessionServices(plugin, this, participants, playerState, teams,
                    sidebar, bossBars, arena, worlds, manager.spawnFinder(), messages, manager.points()), mode);

            for (Player player : roster) {
                Participant participant = participants.add(player);
                if (game.usesTeams()) {
                    teams.resolveForStart(game.id(), participant);
                }
                lobby.prepareForGame(player);
                manager.index(player, this);
            }
            session.onStart();
        } catch (CommandFailure | RuntimeException failure) {
            // Queued players stay queued; everyone else is restored.
            cleanup(false, queued);
            if (failure instanceof CommandFailure commandFailure) {
                throw commandFailure;
            }
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not start " + game.id(), failure);
            throw new CommandFailure("The game failed to start: " + failure.getMessage());
        }

        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            elapsedSeconds++;
            session.onSecondTick(elapsedSeconds);
        }, 20L, 20L);
        messages.broadcastGlobal(Component.text()
                .append(displayName())
                .append(Component.text(" started with " + Messages.count(participants.size(), "player")
                        + (test ? " (test)" : ""), NamedTextColor.GREEN))
                .build());
        messages.playSound(participants.onlinePlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    /** Late join, or puts a knocked-out participant back in. Admin-only. */
    public void join(Player player) throws CommandFailure {
        if (phase != GamePhase.RUNNING) {
            throw new CommandFailure("No game is running.");
        }
        boolean returning = participants.isParticipant(player);
        if (returning && session.isInGame(player)) {
            throw new CommandFailure(player.getName() + " is already in the game.");
        }
        if (!session.allowLateJoin()) {
            throw new CommandFailure(player.getName() + " cannot join this game once it has started.");
        }
        if (isSpectator(player)) {
            stopSpectating(player, false);
        }
        // A returning player already has a pre-game snapshot; a new one would capture spectator state.
        if (!returning) {
            Participant participant = participants.add(player);
            if (game.usesTeams()) {
                teams.resolveForStart(game.id(), participant);
            }
            lobby.prepareForGame(player);
            manager.index(player, this);
        }
        session.onParticipantJoin(player);
        messages.broadcast(Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" joined the game", NamedTextColor.GREEN))
                .build());
    }

    public void leave(Player player) throws CommandFailure {
        if (isSpectator(player)) {
            stopSpectating(player, true);
            return;
        }
        if (!participants.isParticipant(player)) {
            throw new CommandFailure(player.getName() + " is not in the game.");
        }
        if (phase == GamePhase.RUNNING && !session.allowLeave() && session.isInGame(player)) {
            throw new CommandFailure("You cannot leave " + game.id() + " once it has started.");
        }
        sidebar.clear(player);
        bossBars.hideFrom(player);
        participants.remove(player.getUniqueId());
        manager.unindex(player.getUniqueId(), this);
        session.onParticipantLeave(player);
        lobby.returnPlayer(player);
        messages.broadcast(Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" left the game", NamedTextColor.YELLOW))
                .build());
        if (phase == GamePhase.RUNNING && participants.isEmpty()) {
            endGame(EndReason.ALL_PLAYERS_LEFT);
        }
    }

    public void spectate(Player player) throws CommandFailure {
        if (phase == GamePhase.IDLE) {
            throw new CommandFailure("That game is not running.");
        }
        if (participants.isParticipant(player)) {
            throw new CommandFailure("You are playing in this game.");
        }
        if (!spectators.add(player.getUniqueId())) {
            return;
        }
        lobby.prepareForGame(player);
        manager.index(player, this);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(spectatorLocation(player));
        bossBars.sync(player);
        messages.send(player, Component.text()
                .append(Component.text("You are spectating ", NamedTextColor.GRAY))
                .append(displayName())
                .append(Component.text(". ", NamedTextColor.GRAY))
                .append(button("Leave", "/play leave", "Stop spectating"))
                .build());
    }

    /** Stops spectating without teleporting. */
    public void dropSpectator(Player player) {
        stopSpectating(player, false);
        // Admins skip the lobby state, which would reset spectator mode.
        if (player.getGameMode() == GameMode.SPECTATOR && LobbyService.isExempt(player)) {
            GameMode before = playerState.snapshotGameMode(player.getUniqueId());
            player.setGameMode(before != null && before != GameMode.SPECTATOR ? before : GameMode.SURVIVAL);
        }
    }

    void stopSpectating(Player player, boolean sendBack) {
        if (!spectators.remove(player.getUniqueId())) {
            return;
        }
        manager.unindex(player.getUniqueId(), this);
        sidebar.clear(player);
        bossBars.hideFrom(player);
        if (sendBack) {
            lobby.returnPlayer(player);
        }
    }

    private Location spectatorLocation(Player viewer) {
        Location preferred = session.spectatorLocation(viewer);
        if (preferred != null) {
            return preferred;
        }
        List<Player> playing = participants.onlinePlayers().stream()
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .toList();
        if (!playing.isEmpty()) {
            return playing.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(playing.size()))
                    .getLocation();
        }
        for (Player participant : participants.onlinePlayers()) {
            return participant.getLocation();
        }
        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    public void endGame(EndReason reason) {
        if (phase != GamePhase.RUNNING) {
            return;
        }
        phase = GamePhase.ENDING;
        cancelTicker();
        session.onEnd(reason);
        summaryLines = session.summary();
        announceResult();
        // Next tick: restoring inside a death handler conflicts with the respawn screen.
        endingTask = plugin.getServer().getScheduler().runTask(plugin, () -> cleanup(true, false));
    }

    /** Immediate teardown from any phase. */
    public void stopNow(EndReason reason) {
        if (phase == GamePhase.IDLE) {
            return;
        }
        cancelTicker();
        if (endingTask != null) {
            endingTask.cancel();
            endingTask = null;
        }
        if (phase == GamePhase.RUNNING) {
            // ENDING first so ending again from onEnd does not schedule a second cleanup.
            phase = GamePhase.ENDING;
            session.onEnd(reason);
            summaryLines = session.summary();
        }
        cleanup(false, false);
    }

    /** Everyone outside the game gets one line; the game's own audience gets the summary instead. */
    private void announceResult() {
        Component result = session.globalResult();
        Component line = result != null ? result
                : Component.text().append(displayName())
                        .append(Component.text(" has ended", NamedTextColor.GRAY)).build();
        List<Player> audience = audiencePlayers();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!audience.contains(player)) {
                player.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    /** @param seeds of the session's generated worlds; shown even when the game was stopped early */
    private void sendSummary(List<Player> players, boolean offerReplay, List<Long> seeds) {
        if (summaryLines == null && seeds.isEmpty()) {
            return;
        }
        List<Component> body = new ArrayList<>(summaryLines != null ? summaryLines : List.of());
        summaryLines = null;
        if (test && !body.isEmpty()) {
            body.add(Component.text("Test game: no points", NamedTextColor.DARK_GRAY));
        }
        if (!seeds.isEmpty()) {
            if (!body.isEmpty()) {
                body.add(Component.empty());
            }
            body.add(seedLine(seeds));
        }
        String command = manager.playCommand(game, mode);
        for (Player player : players) {
            if (!player.isOnline()) {
                continue;
            }
            List<Component> buttons = new ArrayList<>();
            if (offerReplay) {
                buttons.add(button("Play again", command, command));
            }
            if (lobby.world() != null && !lobby.isLobby(player.getWorld())) {
                buttons.add(Component.text("[Lobby]", NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.runCommand("/lobby"))
                        .hoverEvent(HoverEvent.showText(Component.text("/lobby", NamedTextColor.GRAY))));
            }
            Component footer = buttons.isEmpty() ? null
                    : Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text("  ")),
                            buttons);
            player.sendMessage(Summary.frame(displayName(), body, footer));
        }
    }

    /** "Seed: 123" — click to copy. */
    private static Component seedLine(List<Long> seeds) {
        List<Component> values = new ArrayList<>();
        for (long seed : seeds) {
            String text = Long.toString(seed);
            values.add(Component.text(text, NamedTextColor.WHITE)
                    .clickEvent(ClickEvent.copyToClipboard(text))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy", NamedTextColor.GRAY))));
        }
        return Component.text()
                .append(Component.text(seeds.size() == 1 ? "Seed: " : "Seeds: ", NamedTextColor.GRAY))
                .append(Component.join(net.kyori.adventure.text.JoinConfiguration.commas(true), values))
                .build();
    }

    private void cleanup(boolean offerReplay, boolean backToQueue) {
        if (phase == GamePhase.IDLE) {
            return;
        }
        if (endingTask != null) {
            endingTask.cancel();
            endingTask = null;
        }
        cancelTicker();
        List<Player> returning = audiencePlayers();
        Set<UUID> involved = new LinkedHashSet<>();
        for (Participant participant : participants.all()) {
            involved.add(participant.id());
        }
        involved.addAll(spectators);
        manager.runCleanupHooks(involved);

        for (Player player : returning) {
            sidebar.clear(player);
            bossBars.hideFrom(player);
            // A game can end on a death (a draw): the dead cannot be teleported, so they would stay
            // in the game world and keep it from unloading.
            if (player.isDead()) {
                player.spigot().respawn();
            }
            if (backToQueue) {
                lobby.backToQueue(player);
            } else {
                lobby.returnPlayer(player);
            }
        }
        sidebar.clearAll();
        bossBars.hideAll();
        arena.restoreAllBorders();
        // Read before the worlds, and with them their seeds, are gone.
        List<Long> seeds = worlds.seeds();
        // Only after players have left them.
        worlds.destroyOwn();
        teams.detach();
        phase = GamePhase.IDLE;
        manager.onSessionEnded(this, involved);
        participants.clear();
        spectators.clear();

        sendSummary(returning, offerReplay, seeds);
    }

    void handleQuit(Player player) {
        if (spectators.contains(player.getUniqueId())) {
            // Snapshot stays saved for the reconnect.
            spectators.remove(player.getUniqueId());
            manager.unindex(player.getUniqueId(), this);
            sidebar.clear(player);
            return;
        }
        if (!participants.isParticipant(player)) {
            return;
        }
        participants.find(player.getUniqueId()).ifPresent(participant -> participant.setOnline(false));
        if (phase != GamePhase.IDLE) {
            session.onParticipantQuit(player);
        }
    }

    void handleRejoin(Player player) {
        participants.find(player.getUniqueId()).ifPresent(participant -> participant.setOnline(true));
        session.onParticipantRejoin(player);
    }

    private void cancelTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    static Component button(String label, String command, String hover) {
        return Component.text("[" + label + "]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    public Collection<UUID> spectatorIds() {
        return List.copyOf(spectators);
    }
}
