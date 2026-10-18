package me.petr1furious.potatominigames.core.queue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.lobby.LobbyService;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.core.text.Messages;

/**
 * One auto-starting queue per game and mode; a player is in at most one.
 *
 * <p>At its minimum a queue counts down from {@code countdownSeconds}, shortened (never
 * lengthened) to {@code everyoneQueuedSeconds} when every free player is in it and to
 * {@code voteSeconds} on a start vote (all but one of 3+, both of 2, the only one of 1).
 * Dropping below the minimum cancels it.
 *
 * <p>A game type runs one session at a time: joining a running game spectates, and on
 * start everyone left in any of its queues becomes a spectator.
 */
public final class QueueService implements Listener {
    private static final Messages QUEUE =
            new Messages(Bukkit::getServer, Messages.tag(Component.text("Queue", NamedTextColor.YELLOW)));

    /** {@code /play difficulty any} drops the vote. */
    public static final String ANY_DIFFICULTY = "any";

    private static final Title.Times COUNTDOWN_TITLE_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200));

    public final class Queue {
        private final Minigame game;
        private final String mode;
        private final Property<Boolean> enabled;
        private final Property<Integer> minPlayers;
        private final Property<Integer> maxPlayers;
        private final Set<UUID> members = new LinkedHashSet<>();
        private final Set<UUID> votes = new LinkedHashSet<>();
        private final Map<UUID, TeamColor> teamPicks = new LinkedHashMap<>();
        /** Difficulty votes; no entry means any. */
        private final Map<UUID, String> difficultyVotes = new LinkedHashMap<>();
        /** Seconds left, or null while not counting down. */
        private Integer countdown;

        private Queue(Minigame game, String mode, PropertyBag bag) {
            this.game = game;
            this.mode = mode;
            this.enabled = bag.define("enabled", PropertyCodec.BOOL, true);
            this.minPlayers = bag.define("minPlayers", PropertyCodec.INT, game.minPlayers(mode));
            this.maxPlayers = bag.define("maxPlayers", PropertyCodec.INT, game.maxPlayers(mode));
        }

        public Minigame game() {
            return game;
        }

        public String mode() {
            return mode;
        }

        public boolean enabled() {
            return enabled.get();
        }

        public int size() {
            return members.size();
        }

        public int minPlayers() {
            return Math.max(1, minPlayers.get());
        }

        /** 0 means no limit. */
        public int maxPlayers() {
            return Math.max(0, maxPlayers.get());
        }

        public Integer countdown() {
            return countdown;
        }

        public boolean contains(UUID playerId) {
            return members.contains(playerId);
        }

        public Component displayName() {
            return SessionManager.displayName(game, mode);
        }

        public String command() {
            return sessions.playCommand(game, mode);
        }

        /** "3/16" or "3" for a queue without a maximum. */
        public String countText() {
            return maxPlayers() > 0 ? members.size() + "/" + maxPlayers() : String.valueOf(members.size());
        }

        public int votesNeeded() {
            return members.size() >= 3 ? members.size() - 1 : Math.max(1, members.size());
        }

        public int votesCast() {
            int count = 0;
            for (UUID id : votes) {
                if (members.contains(id)) {
                    count++;
                }
            }
            return count;
        }

        /** Colours open to picks; empty for a game without teams. */
        public List<TeamColor> teamColors() {
            return game.teamColors(mode);
        }

        public TeamColor teamPick(UUID playerId) {
            return teamPicks.get(playerId);
        }

        /** Online members who picked this colour, in pick order. */
        public List<Player> pickedBy(TeamColor color) {
            List<Player> result = new ArrayList<>();
            teamPicks.forEach((id, pick) -> {
                Player player = Bukkit.getPlayer(id);
                if (pick == color && player != null) {
                    result.add(player);
                }
            });
            return result;
        }

        /** Levels open to votes; empty for a game without difficulties. */
        public List<String> difficulties() {
            return game.difficulties();
        }

        /** Null for any. */
        public String difficultyVote(UUID playerId) {
            return difficultyVotes.get(playerId);
        }

        /** Online members who voted for this level, in vote order. */
        public List<Player> votedFor(String level) {
            List<Player> result = new ArrayList<>();
            difficultyVotes.forEach((id, vote) -> {
                Player player = Bukkit.getPlayer(id);
                if (vote.equals(level) && player != null) {
                    result.add(player);
                }
            });
            return result;
        }

        /**
         * The most-voted level; ties go to the default, else the middle of the tied ones.
         * Null for a game without difficulties.
         */
        public String leadingDifficulty() {
            List<String> levels = difficulties();
            if (levels.isEmpty()) {
                return null;
            }
            String fallback = game.defaultDifficulty();
            if (!levels.contains(fallback)) {
                fallback = levels.get(levels.size() / 2);
            }
            Map<String, Integer> counts = new HashMap<>();
            difficultyVotes.forEach((id, vote) -> {
                if (members.contains(id) && levels.contains(vote)) {
                    counts.merge(vote, 1, Integer::sum);
                }
            });
            if (counts.isEmpty()) {
                return fallback;
            }
            int best = counts.values().stream().max(Integer::compare).orElseThrow();
            List<String> tied = levels.stream().filter(level -> counts.getOrDefault(level, 0) == best).toList();
            return tied.contains(fallback) ? fallback : tied.get((tied.size() - 1) / 2);
        }
    }

    private final JavaPlugin plugin;
    private final SessionManager sessions;
    private final LobbyService lobby;

    private final PropertyBag config = new PropertyBag("queue");
    private final Property<Integer> countdownSeconds = config.define("countdownSeconds", PropertyCodec.INT, 60);
    private final Property<Integer> everyoneQueuedSeconds =
            config.define("everyoneQueuedSeconds", PropertyCodec.INT, 15);
    private final Property<Integer> voteSeconds = config.define("voteSeconds", PropertyCodec.INT, 5);
    private final Property<Integer> afkSeconds = config.define("afkSeconds", PropertyCodec.INT, 300);

    private final Map<String, Queue> queues = new LinkedHashMap<>();
    private final Map<UUID, Queue> byPlayer = new LinkedHashMap<>();
    /** Last time each player did something; written from the async chat thread too. */
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    public QueueService(JavaPlugin plugin, SessionManager sessions, LobbyService lobby) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.lobby = lobby;
    }

    public PropertyBag config() {
        return config;
    }

    public void createQueues(ConfigRegistry configs) {
        configs.register(config, "config.yml", "queue");
        for (Minigame game : sessions.games()) {
            for (String mode : game.modes()) {
                PropertyBag bag = new PropertyBag("queues." + game.id() + "." + mode);
                Queue queue = new Queue(game, mode, bag);
                configs.register(bag, "config.yml", "queues." + game.id() + "." + mode);
                queues.put(game.id() + "." + mode, queue);
            }
        }
        sessions.addStartListener(this::onGameStarted);
        // Any way into a game (admin join, spectating) removes them from queues.
        sessions.addJoinListener(player -> {
            Queue queue = byPlayer.get(player.getUniqueId());
            if (queue != null) {
                remove(player, queue);
            }
        });
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public List<Queue> visibleQueues() {
        return queues.values().stream().filter(Queue::enabled).toList();
    }

    /** Visible queues, in mode order. */
    public List<Queue> queuesOf(Minigame game) {
        return visibleQueues().stream().filter(queue -> queue.game == game).toList();
    }

    public Optional<Queue> find(Minigame game, String mode) {
        return Optional.ofNullable(queues.get(game.id() + "." + mode));
    }

    public Optional<Queue> queueOf(Player player) {
        return Optional.ofNullable(byPlayer.get(player.getUniqueId()));
    }

    /** Spectates instead if the game is already running. */
    public void join(Player player, Queue queue) throws CommandFailure {
        if (!queue.enabled()) {
            throw new CommandFailure("That queue is closed.");
        }
        Optional<ActiveSession> current = sessions.sessionOf(player);
        if (current.isPresent() && current.get().isParticipant(player)) {
            throw new CommandFailure(Component.text()
                    .append(Component.text("You are playing ", NamedTextColor.RED))
                    .append(current.get().displayName())
                    .append(Component.text(". Use /play leave first.", NamedTextColor.RED))
                    .build());
        }
        touch(player);

        Optional<ActiveSession> running = sessions.sessionOf(queue.game);
        if (running.isPresent()) {
            if (current.isPresent() && current.get() == running.get()) {
                throw new CommandFailure("You are already spectating this game.");
            }
            Queue previous = byPlayer.get(player.getUniqueId());
            if (previous != null) {
                remove(player, previous);
            }
            current.ifPresent(session -> session.dropSpectator(player));
            running.get().spectate(player);
            return;
        }

        Queue previous = byPlayer.get(player.getUniqueId());
        if (previous == queue) {
            throw new CommandFailure("You are already in this queue.");
        }
        if (previous != null) {
            remove(player, previous);
        }
        current.ifPresent(session -> session.dropSpectator(player));

        lobby.enterQueue(player);
        queue.members.add(player.getUniqueId());
        byPlayer.put(player.getUniqueId(), queue);

        QUEUE.send(player, Component.text()
                .append(Component.text("You joined ", NamedTextColor.GREEN))
                .append(queue.displayName())
                .append(Component.text(" (" + queue.countText() + "). ", NamedTextColor.GREEN))
                .append(memberButtons(queue))
                .build());
        announce(queue, player, true);
    }

    /** Leaves the queue or stops spectating. */
    public void leave(Player player) throws CommandFailure {
        Queue queue = byPlayer.get(player.getUniqueId());
        if (queue != null) {
            remove(player, queue);
            lobby.leaveQueue(player);
            QUEUE.send(player, Component.text("You left the queue.", NamedTextColor.YELLOW));
            return;
        }
        Optional<ActiveSession> session = sessions.sessionOf(player);
        if (session.isPresent()) {
            session.get().leave(player);
            return;
        }
        throw new CommandFailure("You are not in a queue or a game.");
    }

    public void vote(Player player) throws CommandFailure {
        Queue queue = byPlayer.get(player.getUniqueId());
        if (queue == null) {
            throw new CommandFailure("You are not in a queue.");
        }
        if (!queue.votes.add(player.getUniqueId())) {
            throw new CommandFailure("You already voted to start.");
        }
        touch(player);
        Component line = Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" voted to start (" + queue.votesCast() + "/" + queue.votesNeeded() + ")",
                        NamedTextColor.GRAY))
                .build();
        for (Player member : online(queue)) {
            QUEUE.send(member, line);
        }
    }

    /** The queue of a team game the player is in. */
    public Queue requireTeamQueue(Player player) throws CommandFailure {
        Queue queue = byPlayer.get(player.getUniqueId());
        if (queue == null) {
            throw new CommandFailure("Join a queue first.");
        }
        if (!queue.game.usesTeams()) {
            throw new CommandFailure("This game has no teams.");
        }
        return queue;
    }

    /** Lasts only while they stay in this queue; never saved. */
    public void pickTeam(Player player, TeamColor color) throws CommandFailure {
        Queue queue = requireTeamQueue(player);
        if (!queue.teamColors().contains(color)) {
            throw new CommandFailure(Component.text()
                    .append(color.displayNameComponent())
                    .append(Component.text(" is not available in ", NamedTextColor.RED))
                    .append(queue.displayName())
                    .append(Component.text(".", NamedTextColor.RED))
                    .build());
        }
        if (queue.teamPicks.get(player.getUniqueId()) == color) {
            throw new CommandFailure("You already picked that team.");
        }
        touch(player);
        queue.teamPicks.put(player.getUniqueId(), color);
        announcePick(queue, Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" picked ", NamedTextColor.GRAY))
                .append(color.displayNameComponent())
                .build());
    }

    /** Back to auto-balancing. */
    public void clearTeamPick(Player player) throws CommandFailure {
        Queue queue = requireTeamQueue(player);
        if (queue.teamPicks.remove(player.getUniqueId()) == null) {
            throw new CommandFailure("You have no team picked.");
        }
        touch(player);
        announcePick(queue, Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" will be auto-balanced", NamedTextColor.GRAY))
                .build());
    }

    /** The queue of a game with difficulties the player is in. */
    public Queue requireDifficultyQueue(Player player) throws CommandFailure {
        Queue queue = byPlayer.get(player.getUniqueId());
        if (queue == null) {
            throw new CommandFailure("Join a queue first.");
        }
        if (queue.difficulties().isEmpty()) {
            throw new CommandFailure("This game has no difficulty levels.");
        }
        return queue;
    }

    /** Null votes for any. Lasts only while they stay in this queue; never saved. */
    public void pickDifficulty(Player player, String level) throws CommandFailure {
        Queue queue = requireDifficultyQueue(player);
        String key = level == null ? null : level.toLowerCase(Locale.ROOT);
        if (key != null && !queue.difficulties().contains(key)) {
            throw new CommandFailure("Unknown difficulty: " + level + " (options: "
                    + String.join(", ", queue.difficulties()) + ", " + ANY_DIFFICULTY + ")");
        }
        if (Objects.equals(queue.difficultyVotes.get(player.getUniqueId()), key)) {
            throw new CommandFailure(key == null ? "You already voted for any." : "You already voted for that.");
        }
        touch(player);
        if (key == null) {
            queue.difficultyVotes.remove(player.getUniqueId());
        } else {
            queue.difficultyVotes.put(player.getUniqueId(), key);
        }
        announcePick(queue, Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" voted for ", NamedTextColor.GRAY))
                .append(Component.text(key == null ? ANY_DIFFICULTY : key, NamedTextColor.WHITE))
                .append(Component.text(" (leading: " + queue.leadingDifficulty() + ")", NamedTextColor.GRAY))
                .build());
    }

    private void announcePick(Queue queue, Component line) {
        for (Player member : online(queue)) {
            QUEUE.send(member, line);
        }
    }

    /** Ignores the minimum and timers. */
    public void forceStart(Queue queue) throws CommandFailure {
        if (sessions.sessionOf(queue.game).isPresent()) {
            throw new CommandFailure(Component.text()
                    .append(queue.game.displayName())
                    .append(Component.text(" is already running.", NamedTextColor.RED))
                    .build());
        }
        if (online(queue).isEmpty()) {
            throw new CommandFailure("Nobody is in that queue.");
        }
        startGame(queue);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long afkMillis = afkSeconds.get() * 1000L;
        for (Queue queue : queues.values()) {
            if (queue.members.isEmpty()) {
                queue.countdown = null;
                continue;
            }
            if (sessions.sessionOf(queue.game).isPresent()) {
                // Normally handled on game start; catches stragglers.
                moveToSpectators(queue, sessions.sessionOf(queue.game).get());
                continue;
            }
            if (afkMillis > 0) {
                for (Player member : online(queue)) {
                    if (now - lastActivity.getOrDefault(member.getUniqueId(), now) > afkMillis) {
                        remove(member, queue);
                        lobby.leaveQueue(member);
                        QUEUE.send(member, Component.text("Removed from the queue: AFK.",
                                NamedTextColor.YELLOW));
                    }
                }
            }
            if (queue.members.size() < queue.minPlayers()) {
                queue.countdown = null;
                showWaiting(queue);
                continue;
            }

            int allowed = countdownSeconds.get();
            if (everyoneFreeIsIn(queue)) {
                allowed = Math.min(allowed, everyoneQueuedSeconds.get());
            }
            if (queue.votesCast() >= queue.votesNeeded()) {
                allowed = Math.min(allowed, voteSeconds.get());
            }
            if (queue.countdown == null) {
                queue.countdown = allowed;
                announceCountdown(queue);
            } else {
                queue.countdown = Math.min(queue.countdown - 1, allowed);
            }
            if (queue.countdown <= 0) {
                startGame(queue);
            } else {
                showCountdown(queue);
            }
        }
    }

    private boolean everyoneFreeIsIn(Queue queue) {
        long now = System.currentTimeMillis();
        long afkMillis = afkSeconds.get() * 1000L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (queue.members.contains(player.getUniqueId()) || sessions.isBusy(player)) {
                continue;
            }
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (afkMillis > 0 && now - lastActivity.getOrDefault(player.getUniqueId(), now) > afkMillis) {
                continue;
            }
            return false;
        }
        return true;
    }

    private void startGame(Queue queue) {
        List<Player> members = online(queue);
        int max = queue.maxPlayers();
        List<Player> roster = max > 0 && members.size() > max ? members.subList(0, max) : members;
        roster = List.copyOf(roster);
        Map<UUID, TeamColor> picks = new LinkedHashMap<>();
        for (Player player : roster) {
            TeamColor pick = queue.teamPicks.get(player.getUniqueId());
            if (pick != null) {
                picks.put(player.getUniqueId(), pick);
            }
        }
        Map<String, String> options = new LinkedHashMap<>();
        String difficulty = queue.leadingDifficulty();
        if (difficulty != null) {
            options.put("difficulty", difficulty);
        }
        Map<UUID, String> difficultyVotes = new LinkedHashMap<>(queue.difficultyVotes);
        for (Player player : roster) {
            removeQuietly(player);
        }
        try {
            sessions.start(queue.game, queue.mode, roster, picks, true, options);
        } catch (CommandFailure failure) {
            plugin.getLogger().warning("Queued " + queue.game.id() + " could not start: "
                    + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(failure.text()));
            // Requeue at the front with a full countdown.
            List<UUID> rest = new ArrayList<>(queue.members);
            queue.members.clear();
            for (Player player : roster) {
                queue.members.add(player.getUniqueId());
                byPlayer.put(player.getUniqueId(), queue);
                player.sendMessage(failure.text());
            }
            queue.teamPicks.putAll(picks);
            difficultyVotes.forEach(queue.difficultyVotes::putIfAbsent);
            queue.members.addAll(rest);
            queue.countdown = countdownSeconds.get();
        }
    }

    /** Leftover queuers spectate; the game's players leave any queue (admins can start with anyone). */
    private void onGameStarted(ActiveSession session) {
        for (Player player : session.participants().onlinePlayers()) {
            Queue queue = byPlayer.get(player.getUniqueId());
            if (queue != null) {
                remove(player, queue);
            }
        }
        for (Queue queue : queues.values()) {
            if (queue.game == session.game()) {
                moveToSpectators(queue, session);
            }
        }
    }

    private void moveToSpectators(Queue queue, ActiveSession session) {
        for (Player player : online(queue)) {
            removeQuietly(player);
            try {
                session.spectate(player);
            } catch (CommandFailure failure) {
                player.sendMessage(failure.text());
            }
        }
        queue.countdown = null;
    }

    private void remove(Player player, Queue queue) {
        removeQuietly(player);
        announce(queue, player, false);
    }

    private void removeQuietly(Player player) {
        Queue queue = byPlayer.remove(player.getUniqueId());
        if (queue == null) {
            return;
        }
        queue.members.remove(player.getUniqueId());
        queue.votes.remove(player.getUniqueId());
        queue.teamPicks.remove(player.getUniqueId());
        queue.difficultyVotes.remove(player.getUniqueId());
        player.sendActionBar(Component.empty());
        if (queue.members.size() < queue.minPlayers()) {
            queue.countdown = null;
        }
    }

    private List<Player> online(Queue queue) {
        List<Player> players = new ArrayList<>();
        for (UUID id : queue.members) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    /** Not in a game or queue. Only they get join/leave messages. */
    private List<Player> freePlayers() {
        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!sessions.isBusy(player) && !byPlayer.containsKey(player.getUniqueId())) {
                result.add(player);
            }
        }
        return result;
    }

    private void announce(Queue queue, Player who, boolean joined) {
        Component line = Component.text()
                .append(Component.text(who.getName(), NamedTextColor.AQUA))
                .append(Component.text(joined ? " joined " : " left ", NamedTextColor.GRAY))
                .append(queue.displayName())
                .append(Component.text(" (" + queue.countText() + ") ", NamedTextColor.GRAY))
                .append(button("Join", queue.command(), "Join this queue"))
                .build();
        for (Player player : freePlayers()) {
            if (player != who) {
                QUEUE.send(player, line);
            }
        }
        Component memberLine = Component.text()
                .append(Component.text(who.getName(), NamedTextColor.AQUA))
                .append(Component.text(joined ? " joined your queue " : " left your queue ", NamedTextColor.GRAY))
                .append(Component.text("(" + queue.countText() + ")", NamedTextColor.GRAY))
                .build();
        for (Player member : online(queue)) {
            if (member != who) {
                QUEUE.send(member, memberLine);
            }
        }
    }

    private void announceCountdown(Queue queue) {
        Component members = Component.text()
                .append(queue.displayName())
                .append(Component.text(" starts in " + queue.countdown + "s"
                        + difficultySuffix(queue, ", difficulty ") + ". ", NamedTextColor.GREEN))
                .append(memberButtons(queue))
                .build();
        for (Player member : online(queue)) {
            QUEUE.send(member, members);
        }
        Component others = Component.text()
                .append(queue.displayName())
                .append(Component.text(" starts in " + queue.countdown + "s with " + queue.countText()
                        + (queue.size() == 1 ? " player" : " players")
                        + difficultySuffix(queue, ", difficulty ") + ". ", NamedTextColor.GRAY))
                .append(button("Join", queue.command(), "Join this queue"))
                .build();
        for (Player player : freePlayers()) {
            QUEUE.send(player, others);
        }
    }

    private void showWaiting(Queue queue) {
        Component bar = Component.text()
                .append(queue.displayName())
                .append(Component.text(" • waiting for players " + queue.members.size() + "/" + queue.minPlayers()
                        + difficultySuffix(queue, " • "),
                        NamedTextColor.GRAY))
                .build();
        for (Player member : online(queue)) {
            member.sendActionBar(bar);
        }
    }

    private void showCountdown(Queue queue) {
        int seconds = queue.countdown;
        Component bar = Component.text()
                .append(queue.displayName())
                .append(Component.text(" • " + queue.countText() + " • starts in " + seconds + "s • votes "
                        + queue.votesCast() + "/" + queue.votesNeeded() + difficultySuffix(queue, " • "),
                        NamedTextColor.GRAY))
                .build();
        for (Player member : online(queue)) {
            member.sendActionBar(bar);
            if (seconds <= 5) {
                member.showTitle(Title.title(
                        Component.text(String.valueOf(seconds), seconds <= 3 ? NamedTextColor.RED
                                : NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                        queue.displayName(), COUNTDOWN_TITLE_TIMES));
                member.playSound(member.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
            }
        }
    }

    /** The leading difficulty after a separator, or "" without difficulties. */
    private static String difficultySuffix(Queue queue, String separator) {
        String level = queue.leadingDifficulty();
        return level == null ? "" : separator + level;
    }

    /** [Vote start] [Difficulty] [Pick team] [Leave]; the pick buttons only where there is something to pick. */
    private static Component memberButtons(Queue queue) {
        var buttons = Component.text()
                .append(button("Vote start", "/play vote", "Vote to start early"))
                .append(Component.space());
        if (!queue.difficulties().isEmpty()) {
            buttons.append(button("Difficulty", "/play difficulty", "Vote for the difficulty"))
                    .append(Component.space());
        }
        if (!queue.teamColors().isEmpty()) {
            buttons.append(button("Pick team", "/play team", "Choose your team colour"))
                    .append(Component.space());
        }
        return buttons.append(button("Leave", "/play leave", "Leave the queue")).build();
    }

    private static Component button(String label, String command, String hover) {
        return Component.text("[" + label + "]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private void touch(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Looking around counts as activity; falling does not.
        if (event.hasChangedOrientation() || event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            touch(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Queue queue = byPlayer.get(player.getUniqueId());
        if (queue != null) {
            // Return is resolved on next login.
            remove(player, queue);
        }
        lastActivity.remove(player.getUniqueId());
    }
}
