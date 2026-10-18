package me.petr1furious.potatominigames.core.queue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.SessionManager;

/**
 * Chest menus: one icon per game, and for games with several modes a second menu with
 * one icon per mode. A running game is spectated, as with {@code /play}.
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
        show(player, Component.text("Play a game"), entries, null);
    }

    /** The mode menu of one game. */
    public void open(Player player, Minigame game) {
        List<Entry> entries = new ArrayList<>();
        for (QueueService.Queue queue : queues.queuesOf(game)) {
            entries.add(new Entry(viewer -> queueIcon(queue, viewer), clicker -> {
                clicker.closeInventory();
                queues.join(clicker, queue);
            }));
        }
        show(player, game.displayName(), entries, new Entry(viewer -> backIcon(), this::open));
    }

    private void pickGame(Player player, Minigame game) throws CommandFailure {
        List<QueueService.Queue> available = queues.queuesOf(game);
        if (available.isEmpty()) {
            return;
        }
        if (available.size() > 1 && sessions.sessionOf(game).isEmpty()) {
            open(player, game);
            return;
        }
        player.closeInventory();
        queues.join(player, available.getFirst());
    }

    /** Lays the icons out centred on the middle row, with an optional back button below. */
    private void show(Player player, Component title, List<Entry> entries, Entry back) {
        int perRow = 7;
        int contentRows = Math.max(1, (entries.size() + perRow - 1) / perRow);
        int rows = Math.min(6, contentRows + 2);
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, rows * 9, title);
        for (int i = 0; i < entries.size() && i < (rows - 2) * perRow; i++) {
            int row = 1 + i / perRow;
            int inRow = Math.min(perRow, entries.size() - (row - 1) * perRow);
            // Centre each row; with an even count the icons sit one slot apart.
            int index = i % perRow;
            int column;
            if (inRow <= 4) {
                column = 4 - (inRow - 1) + index * 2;
            } else {
                column = 1 + (perRow - inRow) / 2 + index;
            }
            holder.entries.put(row * 9 + column, entries.get(i));
        }
        if (back != null) {
            holder.entries.put((rows - 1) * 9 + 4, back);
        }
        render(holder, player);
        player.openInventory(holder.inventory);
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
                if (gameQueues.size() > 1) {
                    for (QueueService.Queue queue : gameQueues) {
                        lore.add(plain(Component.text()
                                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                                .append(Component.text(queue.mode(), NamedTextColor.GRAY))
                                .append(Component.text(" " + queue.countText(), NamedTextColor.WHITE))
                                .build()));
                    }
                } else if (!gameQueues.isEmpty()) {
                    queueStatus(lore, gameQueues.getFirst());
                }
                lore.add(Component.empty());
                lore.add(plain(mine ? Component.text("You are queued", NamedTextColor.GREEN)
                        : Component.text(gameQueues.size() > 1 ? "» Click to pick a mode" : "» Click to join",
                                NamedTextColor.AQUA)));
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
                lore.add(plain(mine ? Component.text("You are in this queue", NamedTextColor.GREEN)
                        : Component.text("» Click to join", NamedTextColor.AQUA)));
            }
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
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> meta.displayName(plain(Component.text("Back", NamedTextColor.GRAY))));
        return item;
    }

    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private void refreshOpen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) {
                render(holder, player);
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
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
