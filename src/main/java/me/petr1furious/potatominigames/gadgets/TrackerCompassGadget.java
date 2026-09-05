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
 * A compass whose needle follows the nearest player the holder is allowed to
 * track, naming them in the action bar.
 *
 * <p>Deliberately says nothing about distance: knowing only the direction and who
 * you are chasing is the interesting part, and a live distance readout makes
 * hiding pointless. The action bar is written only while the compass is actually
 * in hand, so it never fights with other gadgets for that line.
 */
public class TrackerCompassGadget extends AbstractGadget {
    public static final String ID = "tracker-compass";
    private static final String TAG = "tracker_compass";
    /** Fast enough that the needle stays useful while running, cheap enough to ignore. */
    private static final long UPDATE_PERIOD_TICKS = 5L;

    public TrackerCompassGadget(GadgetContext context) {
        super(ID, context, true);
        context.plugin().getServer().getScheduler().runTaskTimer(context.plugin(), this::tick,
                UPDATE_PERIOD_TICKS, UPDATE_PERIOD_TICKS);
    }

    private void tick() {
        if (!usableNow()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || !isHolding(player)) {
                continue;
            }
            Player target = nearestTarget(player);
            if (target == null) {
                player.sendActionBar(Component.text("Nobody to track", NamedTextColor.GRAY));
                continue;
            }
            player.setCompassTarget(target.getLocation());
            player.sendActionBar(Component.text()
                    .append(Component.text("Tracking ", NamedTextColor.GRAY))
                    .append(Component.text(
                            context.hideTargetNames(player) ? "enemy" : target.getName(), NamedTextColor.AQUA))
                    .build());
        }
    }

    private boolean isHolding(Player player) {
        var inventory = player.getInventory();
        return GadgetItems.checkForItem(inventory.getItemInMainHand(), TAG)
                || GadgetItems.checkForItem(inventory.getItemInOffHand(), TAG);
    }

    /** Nearest trackable player in the same world; other worlds cannot be pointed at. */
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
