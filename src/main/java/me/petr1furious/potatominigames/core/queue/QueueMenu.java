package me.petr1furious.potatominigames.core.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
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
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.SessionManager;

/**
 * The chest menu behind the lobby's compass: one icon per open queue, showing how
 * full it is and whether its game is running (then clicking spectates, exactly as
 * {@code /play} would).
 */
public final class QueueMenu implements Listener {
    private final SessionManager sessions;
    private final QueueService queues;

    /** Marks our inventories, and remembers which queue sits in which slot. */
    private static final class Holder implements InventoryHolder {
        private final List<QueueService.Queue> slots = new ArrayList<>();
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

    public void open(Player player) {
        List<QueueService.Queue> visible = queues.visibleQueues();
        Holder holder = new Holder();
        int rows = Math.max(1, Math.min(6, (visible.size() + 8) / 9));
        holder.inventory = Bukkit.createInventory(holder, rows * 9, Component.text("Play a game"));
        holder.slots.addAll(visible.subList(0, Math.min(visible.size(), rows * 9)));
        render(holder, player);
        player.openInventory(holder.inventory);
    }

    private void render(Holder holder, Player viewer) {
        for (int slot = 0; slot < holder.slots.size(); slot++) {
            holder.inventory.setItem(slot, icon(holder.slots.get(slot), viewer));
        }
    }

    private ItemStack icon(QueueService.Queue queue, Player viewer) {
        ItemStack item = new ItemStack(queue.game().icon());
        Optional<ActiveSession> running = sessions.sessionOf(queue.game());
        boolean mine = queue.contains(viewer.getUniqueId());
        item.editMeta(meta -> {
            meta.displayName(plain(queue.displayName()).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            Component description = queue.game().description(queue.mode());
            if (!description.equals(Component.empty())) {
                lore.add(plain(description).color(NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            if (running.isPresent()) {
                lore.add(plain(Component.text()
                        .append(running.get().displayName())
                        .append(Component.text(" is being played", NamedTextColor.YELLOW)).build())
                        .color(NamedTextColor.YELLOW));
                lore.add(plain(Component.text("Click to spectate", NamedTextColor.AQUA)));
            } else {
                lore.add(plain(Component.text("Queued: " + queue.countText()
                        + " (needs " + queue.minPlayers() + ")", NamedTextColor.WHITE)));
                if (queue.countdown() != null) {
                    lore.add(plain(Component.text("Starts in " + queue.countdown() + "s", NamedTextColor.GREEN)));
                }
                lore.add(plain(mine ? Component.text("You are in this queue", NamedTextColor.GREEN)
                        : Component.text("Click to join", NamedTextColor.AQUA)));
            }
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(mine);
        });
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
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != holder.inventory) {
            return;
        }
        int slot = event.getSlot();
        if (slot < 0 || slot >= holder.slots.size()) {
            return;
        }
        player.closeInventory();
        try {
            queues.join(player, holder.slots.get(slot));
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
