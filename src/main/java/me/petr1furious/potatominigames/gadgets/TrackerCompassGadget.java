package me.petr1furious.potatominigames.gadgets;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Points at the nearest trackable player. No distance shown, by design. The needle is
 * updated whenever carried (else it spins when drawn); the action bar only while held.
 */
public class TrackerCompassGadget extends AbstractGadget {
    public static final String ID = "tracker-compass";
    private static final String TAG = "tracker_compass";
    private static final long UPDATE_PERIOD_TICKS = 5L;

    public TrackerCompassGadget(GadgetContext context) {
        super(ID, context, true);
        context.plugin().getServer().getScheduler().runTaskTimer(context.plugin(), this::tick,
                UPDATE_PERIOD_TICKS, UPDATE_PERIOD_TICKS);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || !isCarrying(player) || !usableBy(player)) {
                continue;
            }
            boolean holding = isHolding(player);
            Player target = nearestTarget(player);
            if (target == null) {
                if (holding) {
                    player.sendActionBar(Component.text("Nobody to track", NamedTextColor.GRAY));
                }
                continue;
            }
            player.setCompassTarget(target.getLocation());
            if (holding) {
                player.sendActionBar(Component.text()
                        .append(Component.text("Tracking ", NamedTextColor.GRAY))
                        .append(Component.text(
                                context.hideTargetNames(player) ? "enemy" : target.getName(), NamedTextColor.AQUA))
                        .build());
            }
        }
    }

    private boolean isHolding(Player player) {
        var inventory = player.getInventory();
        return isTracker(inventory.getItemInMainHand()) || isTracker(inventory.getItemInOffHand());
    }

    /** Anywhere in the inventory, offhand included. */
    private boolean isCarrying(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTracker(item)) {
                return true;
            }
        }
        return false;
    }

    /** Type check first avoids a meta copy per slot. */
    private static boolean isTracker(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && GadgetItems.checkForItem(item, TAG);
    }

    /** Same world only. */
    private Player nearestTarget(Player user) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player candidate : context.targetsFor(user)) {
            if (candidate == user || !candidate.getWorld().equals(user.getWorld())) {
                continue;
            }
            double distance = candidate.getLocation().distanceSquared(user.getLocation());
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    @Override
    public ItemStack createItem(int count) {
        var item = new ItemStack(Material.COMPASS);
        GadgetItems.addTag(item, TAG);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Tracker").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        item.setAmount(count);
        return item;
    }
}
