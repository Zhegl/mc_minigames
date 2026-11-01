package me.petr1furious.potatominigames.core.queue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.Ballot;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.team.TeamColor;

/**
 * Chest menus: one icon per game, and for games with several modes a second menu with
 * one icon per mode. A running game is spectated, as with {@code /play}. Joining opens the
 * queue's votes (difficulty, map) one after another and then the team picker, for games with them.
 */
public final class QueueMenu implements Listener {
    private final SessionManager sessions;
    private final QueueService queues;

    private interface Action {
        void click(Player player) throws CommandFailure;
    }

    private record Entry(Function<Player, ItemStack> icon, Action action) {
    }

    private static final class Holder implements InventoryHolder {
        private final Map<Integer, Entry> entries = new LinkedHashMap<>();
        /** Set for a pick menu, which closes once they leave that queue. */
        private QueueService.Queue queue;
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
        show(player, Component.text("Play a game"), entries, null, null);
    }

    /** The mode menu of one game. */
    public void open(Player player, Minigame game) {
        List<Entry> entries = new ArrayList<>();
        for (QueueService.Queue queue : queues.queuesOf(game)) {
            entries.add(new Entry(viewer -> queueIcon(queue, viewer), clicker -> {
                if (hasPicks(clicker, queue)) {
                    openPicks(clicker, queue);
                    return;
                }
                clicker.closeInventory();
                queues.join(clicker, queue);
                openPicks(clicker, queue);
            }));
        }
        show(player, game.displayName(), entries, new Entry(viewer -> backIcon(), this::open), null);
    }

    private void pickGame(Player player, Minigame game) throws CommandFailure {
        List<QueueService.Queue> available = queues.queuesOf(game);
        if (available.isEmpty()) {
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
        return new Entry(viewer -> backIcon(), clicker -> {
            if (queue.game().showModes()) {
                open(clicker, queue.game());
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
        Holder holder = show(player, Component.text("Vote for the " + ballot.title() + ": ")
                .append(plain(queue.displayName())), entries, backFrom(queue, step), any);
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
        Holder holder = show(player, Component.text("Pick a team: ").append(plain(queue.displayName())),
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
            throw new CommandFailure("You are no longer in that queue.");
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
        holder.inventory = Bukkit.createInventory(holder, rows * 9, pages > 1
                ? title.append(Component.text(" [" + (page + 1) + "/" + pages + "]")) : title);
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
            holder.entries.put(bottom, new Entry(viewer -> navigationIcon("Previous page"), clicker -> {
                Holder previous = show(clicker, title, entries, back, extra, page - 1);
                previous.queue = holder.queue;
            }));
        }
        if (page + 1 < pages) {
            holder.entries.put(bottom + 8, new Entry(viewer -> navigationIcon("Next page"), clicker -> {
                Holder next = show(clicker, title, entries, back, extra, page + 1);
                next.queue = holder.queue;
            }));
        }
        render(holder, player);
        player.openInventory(holder.inventory);
        return holder;
    }

    private void render(Holder holder, Player viewer) {
        holder.entries.forEach((slot, entry) -> holder.inventory.setItem(slot, entry.icon().apply(viewer)));
    }

    private ItemStack gameIcon(Minigame game, Player viewer) {
        ItemStack item = new ItemStack(game.icon());
        Optional<ActiveSession> running = sessions.sessionOf(game);
        List<QueueService.Queue> gameQueues = queues.queuesOf(game);
        boolean mine = gameQueues.stream().anyMatch(queue -> queue.contains(viewer.getUniqueId()));
        item.editMeta(meta -> {
            meta.displayName(plain(game.displayName()).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            addDescription(lore, game.description(game.defaultMode()));
            lore.add(Component.empty());
            if (running.isPresent()) {
                lore.add(plain(Component.text("Being played", NamedTextColor.YELLOW)));
                lore.add(plain(Component.text("» Click to spectate", NamedTextColor.AQUA)));
            } else {
                if (game.showModes()) {
                    for (QueueService.Queue queue : gameQueues) {
                        lore.add(plain(Component.text()
                                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                                .append(Component.text(queue.mode(), NamedTextColor.GRAY))
                                .append(Component.text(" " + queue.countText(), NamedTextColor.WHITE))
                                .append(Component.text(leadingSummary(queue), NamedTextColor.DARK_GRAY))
                                .build()));
                    }
                } else if (!gameQueues.isEmpty()) {
                    queueStatus(lore, gameQueues.getFirst());
                }
                lore.add(Component.empty());
                Optional<QueueService.Queue> queued = queues.queueOf(viewer);
                if (mine && queued.isPresent() && hasPicks(viewer, queued.get())) {
                    pickStatus(lore, queued.get(), viewer);
                } else {
                    lore.add(plain(mine ? Component.text("You are queued", NamedTextColor.GREEN)
                            : Component.text(game.showModes() ? "» Click to pick a mode" : "» Click to join",
                                    NamedTextColor.AQUA)));
                }
            }
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
        return item;
    }

    private ItemStack queueIcon(QueueService.Queue queue, Player viewer) {
        ItemStack item = new ItemStack(queue.game().icon());
        Optional<ActiveSession> running = sessions.sessionOf(queue.game());
        boolean mine = queue.contains(viewer.getUniqueId());
        item.editMeta(meta -> {
            meta.displayName(plain(queue.displayName()).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            addDescription(lore, queue.game().description(queue.mode()));
            lore.add(Component.empty());
            if (running.isPresent()) {
                lore.add(plain(Component.text()
                        .append(running.get().displayName())
                        .append(Component.text(" is being played", NamedTextColor.YELLOW)).build())
                        .color(NamedTextColor.YELLOW));
                lore.add(plain(Component.text("» Click to spectate", NamedTextColor.AQUA)));
            } else {
                queueStatus(lore, queue);
                lore.add(Component.empty());
                if (hasPicks(viewer, queue)) {
                    pickStatus(lore, queue, viewer);
                } else {
                    lore.add(plain(mine ? Component.text("You are in this queue", NamedTextColor.GREEN)
                            : Component.text("» Click to join", NamedTextColor.AQUA)));
                }
            }
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
        return item;
    }

    /** " (hard, mibc)": what each ballot would pick now; "" without ballots. */
    private static String leadingSummary(QueueService.Queue queue) {
        List<String> leading = queue.ballots().stream().map(queue::leadingText).toList();
        return leading.isEmpty() ? "" : " (" + String.join(", ", leading) + ")";
    }

    /** Their votes and team, and where a click takes them. */
    private static void pickStatus(List<Component> lore, QueueService.Queue queue, Player viewer) {
        List<String> what = new ArrayList<>();
        for (Ballot ballot : queue.ballots()) {
            String vote = queue.vote(ballot.key(), viewer.getUniqueId());
            lore.add(plain(Component.text()
                    .append(Component.text("Your " + ballot.title() + ": ", NamedTextColor.GRAY))
                    .append(Component.text(vote != null ? vote : Ballot.ANY, NamedTextColor.WHITE))
                    .build()));
            what.add(ballot.title());
        }
        if (!queue.teamColors().isEmpty()) {
            TeamColor pick = queue.teamPick(viewer.getUniqueId());
            lore.add(plain(Component.text()
                    .append(Component.text("Your team: ", NamedTextColor.GRAY))
                    .append(pick != null ? pick.displayNameComponent()
                            : Component.text("auto", NamedTextColor.WHITE))
                    .build()));
            what.add("team");
        }
        String joined = what.size() <= 1 ? String.join("", what)
                : String.join(", ", what.subList(0, what.size() - 1)) + " and " + what.getLast();
        lore.add(plain(Component.text("» Click to pick " + joined, NamedTextColor.AQUA)));
    }

    private ItemStack choiceIcon(QueueService.Queue queue, Ballot ballot, String choice, Material material,
            Player viewer) {
        ItemStack item = new ItemStack(material);
        boolean mine = choice.equals(queue.vote(ballot.key(), viewer.getUniqueId()));
        List<Player> voters = queue.votedFor(ballot.key(), choice);
        item.editMeta(meta -> {
            meta.displayName(plain(Component.text(choice, NamedTextColor.WHITE)).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            if (choice.equals(queue.leading(ballot))) {
                lore.add(plain(Component.text("Leading", NamedTextColor.GOLD)));
            }
            String fallback = ballot.fallback() == null ? null : ballot.fallback().get();
            if (choice.equals(fallback)) {
                lore.add(plain(Component.text("Default, wins ties", NamedTextColor.DARK_GRAY)));
            }
            if (voters.isEmpty()) {
                lore.add(plain(Component.text("No votes yet", NamedTextColor.DARK_GRAY)));
            } else {
                lore.add(plain(Component.text("Votes (" + voters.size() + "):", NamedTextColor.GRAY)));
                for (Player voter : voters) {
                    lore.add(plain(Component.text()
                            .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(voter.getName(), NamedTextColor.WHITE))
                            .build()));
                }
            }
            lore.add(Component.empty());
            lore.add(plain(mine ? Component.text("Your vote", NamedTextColor.GREEN)
                    : Component.text("» Click to vote", NamedTextColor.AQUA)));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
        return item;
    }

    private ItemStack anyChoiceIcon(QueueService.Queue queue, Ballot ballot, Player viewer) {
        ItemStack item = new ItemStack(Material.GLASS);
        boolean mine = queue.vote(ballot.key(), viewer.getUniqueId()) == null;
        item.editMeta(meta -> {
            meta.displayName(plain(Component.text("Any", NamedTextColor.WHITE)).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(plain(Component.text("Leave it to the others' votes", NamedTextColor.GRAY)));
            lore.add(plain(Component.text()
                    .append(Component.text("Leading: ", NamedTextColor.GRAY))
                    .append(Component.text(queue.leadingText(ballot), NamedTextColor.GOLD))
                    .build()));
            lore.add(Component.empty());
            lore.add(plain(mine ? Component.text("Your choice", NamedTextColor.GREEN)
                    : Component.text("» Click to clear your vote", NamedTextColor.AQUA)));
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
                lore.add(plain(Component.text("Nobody yet", NamedTextColor.DARK_GRAY)));
            } else {
                lore.add(plain(Component.text("Picked by:", NamedTextColor.GRAY)));
                for (Player picker : pickers) {
                    lore.add(plain(Component.text()
                            .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(picker.getName(), NamedTextColor.WHITE))
                            .build()));
                }
            }
            lore.add(Component.empty());
            lore.add(plain(mine ? Component.text("Your team", NamedTextColor.GREEN)
                    : Component.text("» Click to pick", NamedTextColor.AQUA)));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
        return item;
    }

    private ItemStack autoTeamIcon(QueueService.Queue queue, Player viewer) {
        ItemStack item = new ItemStack(Material.GLASS);
        boolean mine = queue.teamPick(viewer.getUniqueId()) == null;
        item.editMeta(meta -> {
            meta.displayName(plain(Component.text("Auto", NamedTextColor.WHITE)).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(plain(Component.text("Put on a team when the game starts", NamedTextColor.GRAY)));
            lore.add(Component.empty());
            lore.add(plain(mine ? Component.text("Your choice", NamedTextColor.GREEN)
                    : Component.text("» Click to clear your pick", NamedTextColor.AQUA)));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
        return item;
    }

    private static void queueStatus(List<Component> lore, QueueService.Queue queue) {
        lore.add(plain(Component.text()
                .append(Component.text("Queued: ", NamedTextColor.GRAY))
                .append(Component.text(queue.countText(), NamedTextColor.WHITE))
                .append(Component.text(" (needs " + queue.minPlayers() + ")", NamedTextColor.DARK_GRAY))
                .build()));
        for (Ballot ballot : queue.ballots()) {
            String title = ballot.title().substring(0, 1).toUpperCase(java.util.Locale.ROOT) + ballot.title().substring(1);
            lore.add(plain(Component.text()
                    .append(Component.text(title + ": ", NamedTextColor.GRAY))
                    .append(Component.text(queue.leadingText(ballot), NamedTextColor.GOLD))
                    .build()));
        }
        if (queue.countdown() != null) {
            lore.add(plain(Component.text("Starts in " + queue.countdown() + "s", NamedTextColor.GREEN)));
        }
    }

    private static void addDescription(List<Component> lore, Component description) {
        if (!description.equals(Component.empty())) {
            lore.add(plain(description).color(NamedTextColor.GRAY));
        }
    }

    private static ItemStack backIcon() {
        return navigationIcon("Back");
    }

    private static ItemStack navigationIcon(String label) {
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> meta.displayName(plain(Component.text(label, NamedTextColor.GRAY))));
        return item;
    }

    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private void refreshOpen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) {
                if (holder.queue != null && !holder.queue.contains(player.getUniqueId())) {
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
        if (player.getOpenInventory().getTopInventory().getHolder() == holder) {
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
