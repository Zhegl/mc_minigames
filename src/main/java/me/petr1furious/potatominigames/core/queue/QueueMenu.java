package me.petr1furious.potatominigames.core.queue;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.Ballot;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Chest menus: one icon per game, and for games with several modes a second menu with
 * one icon per mode. A running game is spectated, as with {@code /play}. Joining opens the
 * queue's votes (difficulty, map) one after another and then the team picker, for games with them.
 */
public final class QueueMenu implements Listener {
    private final SessionManager sessions;
    private final QueueService queues;

    private record PartyGroup(String key, Material marker, NamedTextColor color, List<String> modes) {
    }

    private static final List<PartyGroup> PARTY_GROUPS = List.of(
            new PartyGroup("duels", Material.ORANGE_STAINED_GLASS_PANE, NamedTextColor.GOLD,
                    List.of("pvp", "sumo")),
            new PartyGroup("combat", Material.RED_STAINED_GLASS_PANE, NamedTextColor.RED,
                    List.of("pvp_ffa", "sumo_ffa", "arms_race", "one_shot", "miners", "magic_battle", "pvp_select")),
            new PartyGroup("challenges", Material.LIME_STAINED_GLASS_PANE, NamedTextColor.GREEN,
                    List.of("tnt_run", "rhino", "color_floor", "anvils", "tool_race", "parkour_race", "hole_in_the_wall")),
            new PartyGroup("creative", Material.PURPLE_STAINED_GLASS_PANE, NamedTextColor.LIGHT_PURPLE,
                    List.of("mob_count", "build_memory", "gartic_phone")));

    private static final PartyGroup QUICK_GAMES = new PartyGroup(
            "quick", Material.LIGHT_BLUE_STAINED_GLASS_PANE, NamedTextColor.AQUA,
            List.of("hot_potato", "jump_rope", "drop_tower", "chicken_shooter", "less_is_better", "wait_for_it",
                    "furry_quest"));

    private interface Action {
        void click(Player player) throws CommandFailure;
    }

    private record Entry(Function<Player, ItemStack> icon, Action action) {
    }

    private static final class Holder implements InventoryHolder {
        private final Map<Integer, Entry> entries = new LinkedHashMap<>();
        /** Set for a pick menu, which closes once they leave that queue. */
        private QueueService.Queue queue;
        /** Party browsing remains open across queue and session changes. */
        private Minigame partyGame;
        private boolean quickGames;
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public QueueMenu(JavaPlugin plugin, SessionManager sessions, QueueService queues) {
        this.sessions = sessions;
        this.queues = queues;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshOpen, 20L, 20L);
    }

    /** The game menu. */
    public void open(Player player) {
        List<Minigame> games = new ArrayList<>();
        for (QueueService.Queue queue : queues.visibleQueues()) {
            if (!games.contains(queue.game())) {
                games.add(queue.game());
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (Minigame game : games) {
            entries.add(new Entry(viewer -> gameIcon(game, viewer), clicker -> pickGame(clicker, game)));
        }
        show(player, I18n.tr("core.menu.play"), entries, null, null);
    }

    /** The mode menu of one game. */
    public void open(Player player, Minigame game) {
        if (game.id().equals("party")) {
            openParty(player, game, false);
            return;
        }
        List<Entry> entries = new ArrayList<>();
        Set<Minigame.ModeGroup> shown = new HashSet<>();
        for (QueueService.Queue queue : queues.queuesOf(game)) {
            Optional<Minigame.ModeGroup> group = modeGroup(game, queue.mode());
            if (group.isPresent()) {
                if (shown.add(group.get()))
                    entries.add(
                            new Entry(
                                    viewer -> groupIcon(game, group.get(), viewer),
                                    clicker -> openGroup(clicker, game, group.get())));
            } else entries.add(queueEntry(queue, false));
        }
        show(
                player,
                game.displayName(),
                entries,
                new Entry(viewer -> backIcon(), this::open),
                null);
    }

    private void openParty(Player player, Minigame game, boolean quickGames) {
        Holder holder = new Holder();
        holder.partyGame = game;
        holder.quickGames = quickGames;
        Component title = quickGames ? I18n.tr("core.menu.party.group.quick") : game.displayName();
        holder.inventory = Bukkit.createInventory(holder, quickGames ? 27 : 54, I18n.render(title, player));
        render(holder, player);
        player.openInventory(holder.inventory);
    }

    private Optional<Minigame.ModeGroup> modeGroup(Minigame game, String mode) {
        return game.modeGroups().stream().filter(group -> group.modes().contains(mode)).findFirst();
    }

    private Entry queueEntry(QueueService.Queue queue, boolean grouped) {
        return new Entry(
                viewer ->
                        grouped
                                ? queueIcon(
                                        queue, viewer, queue.game().groupedModeTitle(queue.mode()))
                                : queueIcon(queue, viewer),
                clicker -> {
                    if (hasPicks(clicker, queue)) {
                        openPicks(clicker, queue);
                        return;
                    }
                    clicker.closeInventory();
                    queues.join(clicker, queue);
                    openPicks(clicker, queue);
                });
    }

    private void openGroup(Player player, Minigame game, Minigame.ModeGroup group) {
        List<Entry> entries =
                queues.queuesOf(game).stream()
                        .filter(queue -> group.modes().contains(queue.mode()))
                        .map(queue -> queueEntry(queue, true))
                        .toList();
        show(
                player,
                group.title(),
                entries,
                new Entry(viewer -> backIcon(), clicker -> open(clicker, game)),
                null);
    }

    private ItemStack groupIcon(Minigame game, Minigame.ModeGroup group, Player viewer) {
        ItemStack item = new ItemStack(group.icon());
        var available =
                queues.queuesOf(game).stream()
                        .filter(queue -> group.modes().contains(queue.mode()))
                        .toList();
        item.editMeta(meta -> {
            meta.displayName(plain(group.title()).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            for (var queue : available) {
                lore.add(plain(game.groupedModeTitle(queue.mode())).color(NamedTextColor.GOLD));
                queueStatus(lore, queue);
            }
            lore.add(plain(I18n.tr("core.menu.click_mode")).color(NamedTextColor.AQUA));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(
                    available.stream().anyMatch(q -> q.contains(viewer.getUniqueId())));
        });
        return item;
    }

    private void pickGame(Player player, Minigame game) throws CommandFailure {
        List<QueueService.Queue> available = queues.queuesOf(game);
        if (available.isEmpty()) {
            return;
        }
        if (game.id().equals("party")) {
            open(player, game);
            return;
        }
        Optional<QueueService.Queue> mine = queues.queueOf(player);
        if (mine.isPresent() && mine.get().game() == game && hasPicks(player, mine.get())) {
            openPicks(player, mine.get());
            return;
        }
        if (game.showModes() && sessions.sessionOf(game).isEmpty()) {
            open(player, game);
            return;
        }
        player.closeInventory();
        queues.join(player, available.getFirst());
        openPicks(player, available.getFirst());
    }

    /**
     * After joining: each vote menu, then the team menu, for whichever the game has.
     * Does nothing unless they are in the queue (joining a running game spectates).
     */
    public void openPicks(Player player, QueueService.Queue queue) {
        if (!queue.contains(player.getUniqueId())) {
            return;
        }
        openPick(player, queue, 0);
    }

    /** The menu at this step: the queue's ballots in order, then teams; past the end the last one stays open. */
    private void openPick(Player player, QueueService.Queue queue, int step) {
        List<Ballot> ballots = queue.ballots();
        if (step < ballots.size()) {
            openBallot(player, queue, ballots.get(step));
        } else if (!queue.teamColors().isEmpty()) {
            openTeams(player, queue);
        }
    }

    /** Back from the first pick menu: the mode menu, or the game menu for one mode. */
    private Entry backFromPicks(QueueService.Queue queue) {
        return new Entry(
                viewer -> backIcon(),
                clicker -> {
                    if (queue.game().showModes()) {
                        var group = modeGroup(queue.game(), queue.mode());
                        if (group.isPresent()) openGroup(clicker, queue.game(), group.get());
                        else open(clicker, queue.game());
                    } else {
                        open(clicker);
                    }
                });
    }

    /** Back to the menu before this step. */
    private Entry backFrom(QueueService.Queue queue, int step) {
        if (step == 0) {
            return backFromPicks(queue);
        }
        return new Entry(viewer -> backIcon(), clicker -> openPick(clicker, queue, step - 1));
    }

    /** One vote of the queue the player is in; any pick moves on to the next menu. */
    public void openBallot(Player player, QueueService.Queue queue, Ballot ballot) {
        int step = queue.ballots().indexOf(ballot);
        List<String> choices = ballot.choices().get();
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < choices.size(); i++) {
            String choice = choices.get(i);
            Material material = ballot.icon(i, choices.size());
            entries.add(new Entry(viewer -> choiceIcon(queue, ballot, choice, material, viewer),
                    clicker -> vote(clicker, queue, ballot, choice, step)));
        }
        Entry any = new Entry(viewer -> anyChoiceIcon(queue, ballot, viewer),
                clicker -> vote(clicker, queue, ballot, null, step));
        Holder holder = show(player, I18n.tr("core.menu.vote_title", ballot.titleComponent(),
                plain(queue.displayName())), entries, backFrom(queue, step), any);
        holder.queue = queue;
    }

    private void vote(Player player, QueueService.Queue queue, Ballot ballot, String choice, int step)
            throws CommandFailure {
        requireQueued(player, queue);
        if (!Objects.equals(queue.vote(ballot.key(), player.getUniqueId()), choice)) {
            queues.pick(player, ballot.key(), choice);
        }
        openPick(player, queue, step + 1);
    }

    /** The colour menu of the queue the player is in; picks last only while they stay queued. */
    public void openTeams(Player player, QueueService.Queue queue) {
        List<Entry> entries = new ArrayList<>();
        for (TeamColor color : queue.teamColors()) {
            entries.add(new Entry(viewer -> teamIcon(queue, color, viewer), clicker -> {
                requireQueued(clicker, queue);
                queues.pickTeam(clicker, color);
            }));
        }
        Entry back = backFrom(queue, queue.ballots().size());
        Entry auto = new Entry(viewer -> autoTeamIcon(queue, viewer), clicker -> {
            requireQueued(clicker, queue);
            queues.clearTeamPick(clicker);
        });
        Holder holder = show(player, I18n.tr("core.menu.team_title", plain(queue.displayName())),
                entries, back, auto);
        holder.queue = queue;
    }

    /** Queued with a vote or team to pick. */
    private static boolean hasPicks(Player player, QueueService.Queue queue) {
        return queue.contains(player.getUniqueId())
                && (!queue.ballots().isEmpty() || !queue.teamColors().isEmpty());
    }

    private static void requireQueued(Player player, QueueService.Queue queue) throws CommandFailure {
        if (!queue.contains(player.getUniqueId())) {
            player.closeInventory();
            throw new CommandFailure(I18n.tr("core.queue.no_longer_queued"));
        }
    }

    /**
     * Lays the icons out centred on the middle rows, with optional back and extra buttons
     * below.
     */
    private Holder show(Player player, Component title, List<Entry> entries, Entry back, Entry extra) {
        return show(player, title, entries, back, extra, 0);
    }

    private Holder show(Player player, Component title, List<Entry> entries, Entry back, Entry extra, int page) {
        int offset = page * 28;
        int count = Math.min(28, entries.size() - offset);
        int perRow = 7;
        int contentRows = Math.max(1, (count + perRow - 1) / perRow);
        int rows = Math.min(6, contentRows + 2);
        Holder holder = new Holder();
        int pages = Math.max(1, (entries.size() + 27) / 28);
        holder.inventory = Bukkit.createInventory(holder, rows * 9, I18n.render(pages > 1
                ? I18n.tr("core.menu.page_title", title, page + 1, pages) : title, player));
        for (int i = 0; i < count; i++) {
            int row = 1 + i / perRow;
            int inRow = Math.min(perRow, count - (row - 1) * perRow);
            // Centre each row; with an even count the icons sit one slot apart.
            int index = i % perRow;
            int column;
            if (inRow <= 4) {
                column = 4 - (inRow - 1) + index * 2;
            } else {
                column = 1 + (perRow - inRow) / 2 + index;
            }
            holder.entries.put(row * 9 + column, entries.get(offset + i));
        }
        int bottom = (rows - 1) * 9;
        if (back != null) {
            holder.entries.put(bottom + (extra != null ? 3 : 4), back);
        }
        if (extra != null) {
            holder.entries.put(bottom + (back != null ? 5 : 4), extra);
        }
        if (page > 0) {
            holder.entries.put(bottom, new Entry(viewer -> navigationIcon(I18n.tr("core.menu.previous_page")), clicker -> {
                Holder previous = show(clicker, title, entries, back, extra, page - 1);
                previous.queue = holder.queue;
            }));
        }
        if (page + 1 < pages) {
            holder.entries.put(bottom + 8, new Entry(viewer -> navigationIcon(I18n.tr("core.menu.next_page")), clicker -> {
                Holder next = show(clicker, title, entries, back, extra, page + 1);
                next.queue = holder.queue;
            }));
        }
        render(holder, player);
        player.openInventory(holder.inventory);
        return holder;
    }

    private void render(Holder holder, Player viewer) {
        if (holder.partyGame != null) {
            populateParty(holder, viewer);
            // Removed queues and conditional actions must also disappear from the view.
            for (int slot = 0; slot < holder.inventory.getSize(); slot++) {
                if (!holder.entries.containsKey(slot)) holder.inventory.setItem(slot, null);
            }
        }
        holder.entries.forEach((slot, entry) -> holder.inventory.setItem(slot, I18n.item(entry.icon().apply(viewer), viewer)));
    }

    private void populateParty(Holder holder, Player player) {
        holder.entries.clear();
        Minigame game = holder.partyGame;
        Optional<ActiveSession> running = sessions.sessionOf(game);
        Map<String, QueueService.Queue> available = new LinkedHashMap<>();
        for (QueueService.Queue queue : queues.queuesOf(game)) available.put(queue.mode(), queue);

        if (running.isPresent()) {
            holder.entries.put(4, new Entry(viewer -> infoIcon(Material.ENDER_EYE,
                    I18n.tr("core.menu.party.spectate", running.get().displayName()).color(NamedTextColor.AQUA),
                    List.of(I18n.tr("core.menu.click_spectate").color(NamedTextColor.AQUA))), clicker -> {
                clicker.closeInventory();
                queues.spectate(clicker, game);
            }));
        } else if (available.containsKey("party_games")) {
            holder.entries.put(4, partyEntry(available.get("party_games"), "tournament"));
        }

        if (!holder.quickGames && QUICK_GAMES.modes().stream().anyMatch(available::containsKey)) {
            holder.entries.put(6, new Entry(viewer -> infoIcon(Material.CLOCK,
                    I18n.tr("core.menu.party.group.quick").color(NamedTextColor.AQUA),
                    List.of(I18n.tr("core.menu.party.quick_hint").color(NamedTextColor.GRAY))),
                    clicker -> openParty(clicker, game, true)));
        }

        List<PartyGroup> groups = holder.quickGames ? List.of(QUICK_GAMES) : PARTY_GROUPS;
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            PartyGroup group = groups.get(groupIndex);
            int row = (groupIndex + 1) * 9;
            holder.entries.put(row, new Entry(viewer -> infoIcon(group.marker(),
                    I18n.tr("core.menu.party.group." + group.key()).color(group.color()),
                    List.of(I18n.tr("core.menu.party.group_hint").color(NamedTextColor.GRAY))), clicker -> {}));
            for (int index = 0; index < group.modes().size(); index++) {
                QueueService.Queue queue = available.get(group.modes().get(index));
                if (queue != null) holder.entries.put(row + 2 + index, partyEntry(queue, group.key()));
            }
        }
        int footer = holder.inventory.getSize() - 9;
        holder.entries.put(footer, new Entry(viewer -> backIcon(),
                clicker -> { if (holder.quickGames) open(clicker, game); else open(clicker); }));
        holder.entries.put(footer + 4, new Entry(this::currentQueueIcon, clicker -> {
            Optional<QueueService.Queue> current = queues.queueOf(clicker);
            if (current.isPresent()) openPicks(clicker, current.get());
        }));
        if (queues.queueOf(player).isPresent()) {
            holder.entries.put(footer + 8, new Entry(viewer -> {
                Optional<QueueService.Queue> current = queues.queueOf(viewer);
                return infoIcon(Material.BARRIER, I18n.tr("core.menu.party.leave_queue").color(NamedTextColor.RED),
                        current.map(queue -> List.<Component>of(I18n.tr("core.menu.party.leave_hint", queue.displayName())
                                .color(NamedTextColor.GRAY))).orElseGet(List::of));
            }, clicker -> {
                // QueueService.leave also leaves active games: never call it for a stale queue view.
                if (queues.queueOf(clicker).isPresent()) queues.leave(clicker);
            }));
        }
    }

    private Entry partyEntry(QueueService.Queue queue, String group) {
        return new Entry(viewer -> partyQueueIcon(queue, group, viewer), clicker -> {
            if (!queue.enabled() || sessions.sessionOf(queue.game()).isPresent()) return;
            if (!queue.contains(clicker.getUniqueId())) queues.join(clicker, queue);
            openPicks(clicker, queue);
        });
    }

    private ItemStack partyQueueIcon(QueueService.Queue queue, String group, Player viewer) {
        List<Component> lore = new ArrayList<>();
        addDescription(lore, queue.game().description(queue.mode()));
        lore.add(plain(I18n.tr("core.menu.party.format." + group).color(NamedTextColor.DARK_GRAY)));
        lore.add(Component.empty());
        if (sessions.sessionOf(queue.game()).isPresent()) {
            lore.add(I18n.tr("core.menu.party.match_running").color(NamedTextColor.YELLOW));
            lore.add(I18n.tr("core.menu.party.spectate_above").color(NamedTextColor.GRAY));
        } else {
            partyQueueStatus(lore, queue);
            lore.add(Component.empty());
            if (hasPicks(viewer, queue)) {
                pickStatus(lore, queue, viewer);
            } else {
                lore.add(I18n.tr(queue.contains(viewer.getUniqueId())
                        ? "core.menu.in_this_queue" : "core.menu.click_join").color(queue.contains(viewer.getUniqueId())
                        ? NamedTextColor.GREEN : NamedTextColor.AQUA));
            }
        }
        Component title = queue.mode().equals("party_games") ? I18n.tr("core.menu.party.tournament")
                .color(NamedTextColor.LIGHT_PURPLE) : queue.game().modeTitle(queue.mode());
        ItemStack item = infoIcon(queue.game().icon(queue.mode()), title, lore);
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(queue.contains(viewer.getUniqueId())));
        return item;
    }

    private ItemStack currentQueueIcon(Player viewer) {
        Optional<QueueService.Queue> current = queues.queueOf(viewer);
        if (current.isEmpty()) {
            return infoIcon(Material.COMPASS, I18n.tr("core.menu.party.no_queue").color(NamedTextColor.GRAY),
                    List.of(I18n.tr("core.menu.party.choose_game").color(NamedTextColor.GRAY)));
        }
        QueueService.Queue queue = current.get();
        List<Component> lore = new ArrayList<>();
        partyQueueStatus(lore, queue);
        if (hasPicks(viewer, queue)) pickStatus(lore, queue, viewer);
        ItemStack item = infoIcon(Material.COMPASS,
                I18n.tr("core.menu.party.current_queue", queue.displayName()).color(NamedTextColor.GREEN), lore);
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return item;
    }

    private static void partyQueueStatus(List<Component> lore, QueueService.Queue queue) {
        lore.add(I18n.tr("core.menu.party.queue_status", queue.countText(),
                Math.max(0, queue.minPlayers() - queue.size())).color(NamedTextColor.GRAY));
        for (Ballot ballot : queue.ballots()) {
            lore.add(I18n.tr("core.menu.ballot_status", ballot.titleComponent(),
                    queue.leadingComponent(ballot).color(NamedTextColor.GOLD)).color(NamedTextColor.GRAY));
        }
        if (queue.countdown() != null) {
            lore.add(I18n.tr("core.menu.starts_in", queue.countdown()).color(NamedTextColor.GREEN));
        }
    }

    private static ItemStack infoIcon(Material material, Component title, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(plain(title).decorate(TextDecoration.BOLD));
            meta.lore(lore.stream().map(QueueMenu::plain).toList());
        });
        return item;
    }

    private ItemStack gameIcon(Minigame game, Player viewer) {
        ItemStack item = new ItemStack(game.icon());
        Optional<ActiveSession> running = sessions.sessionOf(game);
        List<QueueService.Queue> gameQueues = queues.queuesOf(game);
        boolean mine = gameQueues.stream().anyMatch(queue -> queue.contains(viewer.getUniqueId()));
        item.editMeta(meta -> {
            meta.displayName(plain(game.displayName()).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            addDescription(lore, game.id().equals("party") ? I18n.tr("core.menu.party.description")
                    : game.description(game.defaultMode()));
            lore.add(Component.empty());
            if (running.isPresent()) {
                lore.add(plain(I18n.tr("core.menu.being_played").color(NamedTextColor.YELLOW)));
                lore.add(plain(I18n.tr(game.id().equals("party") ? "core.menu.click_mode"
                        : "core.menu.click_spectate").color(NamedTextColor.AQUA)));
            } else {
                if (game.showModes() && !game.id().equals("party")) {
                    for (QueueService.Queue queue : gameQueues) {
                        lore.add(plain(I18n.tr("core.menu.mode_status", queue.game().modeTitle(queue.mode()).colorIfAbsent(NamedTextColor.GRAY),
                                Component.text(queue.countText(), NamedTextColor.WHITE),
                                leadingSummary(queue).color(NamedTextColor.DARK_GRAY))));
                    }
                } else if (!game.showModes() && !gameQueues.isEmpty()) {
                    queueStatus(lore, gameQueues.getFirst());
                }
                lore.add(Component.empty());
                Optional<QueueService.Queue> queued = queues.queueOf(viewer);
                if (mine && queued.isPresent() && hasPicks(viewer, queued.get())) {
                    pickStatus(lore, queued.get(), viewer);
                } else {
                    lore.add(plain(mine ? I18n.tr("core.menu.you_queued").color(NamedTextColor.GREEN)
                            : I18n.tr(game.showModes() ? "core.menu.click_mode" : "core.menu.click_join").color(NamedTextColor.AQUA)));
                }
            }
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
        return item;
    }

    private ItemStack queueIcon(QueueService.Queue queue, Player viewer) {
        return queueIcon(queue, viewer, queue.displayName());
    }

    private ItemStack queueIcon(QueueService.Queue queue, Player viewer, Component title) {
        ItemStack item = new ItemStack(queue.game().icon(queue.mode()));
        Optional<ActiveSession> running = sessions.sessionOf(queue.game());
        boolean mine = queue.contains(viewer.getUniqueId());
        item.editMeta(meta -> {
            meta.displayName(plain(title).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            addDescription(lore, queue.game().description(queue.mode()));
            lore.add(Component.empty());
            if (running.isPresent()) {
                lore.add(
                        plain(
                                I18n.tr("core.menu.session_played", running.get().displayName())
                                        .color(NamedTextColor.YELLOW)));
                lore.add(plain(I18n.tr("core.menu.click_spectate").color(NamedTextColor.AQUA)));
            } else {
                queueStatus(lore, queue);
                lore.add(Component.empty());
                if (hasPicks(viewer, queue)) {
                    pickStatus(lore, queue, viewer);
                } else {
                    lore.add(
                            plain(
                                    mine
                                            ? I18n.tr("core.menu.in_this_queue")
                                                    .color(NamedTextColor.GREEN)
                                            : I18n.tr("core.menu.click_join")
                                                    .color(NamedTextColor.AQUA)));
                }
            }
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
        return item;
    }

    /** " (hard, mibc)": what each ballot would pick now; "" without ballots. */
    private static Component leadingSummary(QueueService.Queue queue) {
        List<Component> leading = queue.ballots().stream().map(queue::leadingComponent).toList();
        return leading.isEmpty() ? Component.empty()
                : I18n.tr("core.menu.leading_summary", Component.join(JoinConfiguration.separator(Component.text(", ")), leading));
    }

    /** Their votes and team, and where a click takes them. */
    private static void pickStatus(List<Component> lore, QueueService.Queue queue, Player viewer) {
        List<Component> what = new ArrayList<>();
        for (Ballot ballot : queue.ballots()) {
            String vote = queue.vote(ballot.key(), viewer.getUniqueId());
            lore.add(plain(I18n.tr("core.menu.your_ballot", ballot.titleComponent(),
                    ballot.choiceComponent(vote).color(NamedTextColor.WHITE)).color(NamedTextColor.GRAY)));
            what.add(ballot.titleComponent());
        }
        if (!queue.teamColors().isEmpty()) {
            TeamColor pick = queue.teamPick(viewer.getUniqueId());
            lore.add(plain(I18n.tr("core.menu.your_team", pick != null ? pick.displayNameComponent()
                    : I18n.tr("core.menu.auto").color(NamedTextColor.WHITE)).color(NamedTextColor.GRAY)));
            what.add(I18n.tr("core.menu.team"));
        }
        lore.add(plain(I18n.tr("core.menu.click_picks", Component.join(
                JoinConfiguration.separator(Component.text(", ")), what)).color(NamedTextColor.AQUA)));
    }

    private ItemStack choiceIcon(QueueService.Queue queue, Ballot ballot, String choice, Material material,
            Player viewer) {
        ItemStack item = new ItemStack(material);
        boolean mine = choice.equals(queue.vote(ballot.key(), viewer.getUniqueId()));
        List<Player> voters = queue.votedFor(ballot.key(), choice);
        item.editMeta(meta -> {
            meta.displayName(plain(ballot.choiceComponent(choice).color(NamedTextColor.WHITE)).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            if (choice.equals(queue.leading(ballot))) {
                lore.add(plain(I18n.tr("core.menu.leading").color(NamedTextColor.GOLD)));
            }
            String fallback = ballot.fallback() == null ? null : ballot.fallback().get();
            if (choice.equals(fallback)) {
                lore.add(plain(I18n.tr("core.menu.default_ties").color(NamedTextColor.DARK_GRAY)));
            }
            if (voters.isEmpty()) {
                lore.add(plain(I18n.tr("core.menu.no_votes").color(NamedTextColor.DARK_GRAY)));
            } else {
                lore.add(plain(I18n.tr("core.menu.votes", voters.size()).color(NamedTextColor.GRAY)));
                for (Player voter : voters) {
                    lore.add(plain(Component.text()
                            .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(voter.getName(), NamedTextColor.WHITE))
                            .build()));
                }
            }
            lore.add(Component.empty());
            lore.add(plain(mine ? I18n.tr("core.menu.your_vote").color(NamedTextColor.GREEN)
                    : I18n.tr("core.menu.click_vote").color(NamedTextColor.AQUA)));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
        return item;
    }

    private ItemStack anyChoiceIcon(QueueService.Queue queue, Ballot ballot, Player viewer) {
        ItemStack item = new ItemStack(Material.GLASS);
        boolean mine = queue.vote(ballot.key(), viewer.getUniqueId()) == null;
        item.editMeta(meta -> {
            meta.displayName(plain(I18n.tr("core.ballot.any").color(NamedTextColor.WHITE)).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(plain(I18n.tr("core.menu.leave_votes").color(NamedTextColor.GRAY)));
            lore.add(plain(I18n.tr("core.menu.leading_value", queue.leadingComponent(ballot).color(NamedTextColor.GOLD))
                    .color(NamedTextColor.GRAY)));
            lore.add(Component.empty());
            lore.add(plain(mine ? I18n.tr("core.menu.your_choice").color(NamedTextColor.GREEN)
                    : I18n.tr("core.menu.clear_vote").color(NamedTextColor.AQUA)));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
        return item;
    }

    private ItemStack teamIcon(QueueService.Queue queue, TeamColor color, Player viewer) {
        ItemStack item = new ItemStack(color.woolMaterial());
        boolean mine = queue.teamPick(viewer.getUniqueId()) == color;
        List<Player> pickers = queue.pickedBy(color);
        item.editMeta(meta -> {
            meta.displayName(plain(color.displayNameComponent()).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            if (pickers.isEmpty()) {
                lore.add(plain(I18n.tr("core.menu.nobody").color(NamedTextColor.DARK_GRAY)));
            } else {
                lore.add(plain(I18n.tr("core.menu.picked_by").color(NamedTextColor.GRAY)));
                for (Player picker : pickers) {
                    lore.add(plain(Component.text()
                            .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(picker.getName(), NamedTextColor.WHITE))
                            .build()));
                }
            }
            lore.add(Component.empty());
            lore.add(plain(mine ? I18n.tr("core.menu.team_picked").color(NamedTextColor.GREEN)
                    : I18n.tr("core.menu.click_pick").color(NamedTextColor.AQUA)));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
        return item;
    }

    private ItemStack autoTeamIcon(QueueService.Queue queue, Player viewer) {
        ItemStack item = new ItemStack(Material.GLASS);
        boolean mine = queue.teamPick(viewer.getUniqueId()) == null;
        item.editMeta(meta -> {
            meta.displayName(plain(I18n.tr("core.menu.auto").color(NamedTextColor.WHITE)).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(plain(I18n.tr("core.menu.auto_description").color(NamedTextColor.GRAY)));
            lore.add(Component.empty());
            lore.add(plain(mine ? I18n.tr("core.menu.your_choice").color(NamedTextColor.GREEN)
                    : I18n.tr("core.menu.clear_pick").color(NamedTextColor.AQUA)));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
        return item;
    }

    private static void queueStatus(List<Component> lore, QueueService.Queue queue) {
        lore.add(plain(I18n.tr("core.menu.queue_status", Component.text(queue.countText(), NamedTextColor.WHITE),
                queue.minPlayers()).color(NamedTextColor.GRAY)));
        for (Ballot ballot : queue.ballots()) {
            lore.add(plain(I18n.tr("core.menu.ballot_status", ballot.titleComponent(),
                    queue.leadingComponent(ballot).color(NamedTextColor.GOLD)).color(NamedTextColor.GRAY)));
        }
        if (queue.countdown() != null) {
            lore.add(plain(I18n.tr("core.menu.starts_in", queue.countdown()).color(NamedTextColor.GREEN)));
        }
    }

    private static void addDescription(List<Component> lore, Component description) {
        if (!description.equals(Component.empty())) {
            lore.add(plain(description).color(NamedTextColor.GRAY));
        }
    }

    private static ItemStack backIcon() {
        return navigationIcon(I18n.tr("core.menu.back"));
    }

    private static ItemStack navigationIcon(Component label) {
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> meta.displayName(plain(label.color(NamedTextColor.GRAY))));
        return item;
    }

    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private void refreshOpen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) {
                boolean startedPlaying = holder.partyGame != null && sessions.sessionOf(player)
                        .map(session -> session.isParticipant(player)).orElse(false);
                if (startedPlaying || holder.queue != null && !holder.queue.contains(player.getUniqueId())) {
                    player.closeInventory();
                } else {
                    render(holder, player);
                }
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (holder.partyGame != null && event.getWhoClicked() instanceof Player viewer) {
            render(holder, viewer);
        }
        // Raw slots past the menu size are the player's inventory.
        Entry entry = holder.entries.get(event.getRawSlot());
        if (!(event.getWhoClicked() instanceof Player player) || entry == null) {
            return;
        }
        try {
            entry.action().click(player);
        } catch (CommandFailure failure) {
            player.sendMessage(failure.text());
        }
        // Show a pick straight away, not on the next refresh.
        if (player.getOpenInventory().getTopInventory() == holder.inventory) {
            render(holder, player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
