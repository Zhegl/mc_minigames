package me.petr1furious.potatominigames.gadgets;

import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import me.petr1furious.potatominigames.core.world.Locations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.Random;

public class OreshnikGadget extends AbstractGadget {
    private static final String TAG = "oreshnik";

    private final Property<Integer> wavesCount;
    private final Property<Integer> arrowsCount;
    private final Property<Double> explosionPower;
    private final Property<Integer> wavesDelay;
    private final Property<Double> range;

    private final Random random = new Random();

    public OreshnikGadget(GadgetContext context) {
        // Too destructive for out-of-game use.
        super("oreshnik", context, false);
        wavesCount = config().define("wavesCount", PropertyCodec.INT, 6);
        arrowsCount = config().define("arrowsCount", PropertyCodec.INT, 100);
        explosionPower = config().define("explosionPower", PropertyCodec.DOUBLE, 2.5);
        wavesDelay = config().define("wavesDelay", PropertyCodec.INT, 13);
        range = config().define("range", PropertyCodec.DOUBLE, 0.5);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!GadgetItems.isRightClick(event))
            return;
        if (!GadgetItems.checkForItem(event.getItem(), TAG))
            return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR)
            return;
        event.setCancelled(true);
        if (getRemainingCooldownTicks(player) > 0)
            return;
        setCooldown(player);
        var targetBlock = player.getTargetBlockExact(256);
        if (targetBlock == null) {
            player.sendActionBar(Component.text("Too far").color(NamedTextColor.RED));
            return;
        }
        spawnArrowWaves(targetBlock.getLocation(), wavesCount.get(), arrowsCount.get(), player);
        if (player.getGameMode() != GameMode.CREATIVE && event.getHand() != null) {
            ItemStack item = player.getInventory().getItem(event.getHand());
            if (item != null)
                item.setAmount(item.getAmount() - 1);
        }
    }

    @Override
    public boolean handleProjectileImpact(Projectile projectile, Location location, Entity shooter, Entity target) {
        if (!GadgetItems.checkForProjectile(projectile, TAG))
            return false;
        Locations.spawnExplosion(location, explosionPower.get(), shooter);
        projectile.remove();
        return true;
    }

    private int getRemainingCooldownTicks(Player player) {
        return player.getCooldown(Material.REPEATER);
    }

    private void setCooldown(Player player) {
        player.setCooldown(Material.REPEATER, 20);
    }

    /**
     * 500 blocks above the target, or just under a barrier above it: an arena roofed in barriers
     * (e.g. warfare maps walled off from flying out) would otherwise stop every arrow at the roof.
     * Only barriers count, so ordinary roofs are still hit from the sky.
     */
    private static double spawnHeight(Location center) {
        var world = center.getWorld();
        for (int y = center.getBlockY() + 1; y < world.getMaxHeight(); y++) {
            if (world.getBlockAt(center.getBlockX(), y, center.getBlockZ()).getType() == Material.BARRIER) {
                return Math.max(1, y - 1 - center.getY());
            }
        }
        return 500;
    }

    private void spawnArrowWaves(Location center, int waves, int arrows, ProjectileSource shooter) {
        double spawnHeight = spawnHeight(center);
        for (int wave = 0; wave < waves; wave++) {
            int delay = wave * wavesDelay.get();
            context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), () -> {
                for (int i = 0; i < arrows; i++) {
                    Location arrowLoc = center.clone().add(0, spawnHeight, 0);
                    arrowLoc.getWorld().spawn(arrowLoc, Arrow.class, spawnedArrow -> {
                        GadgetItems.setProjectileTag(spawnedArrow, TAG);
                        double scatterRange = range.get();
                        double randomX;
                        double randomZ;
                        do {
                            randomX = (random.nextDouble() * 2 - 1) * scatterRange;
                            randomZ = (random.nextDouble() * 2 - 1) * scatterRange;
                        } while (randomX * randomX + randomZ * randomZ > scatterRange * scatterRange);
                        spawnedArrow.setVelocity(spawnedArrow.getVelocity().add(new Vector(randomX, -10.0, randomZ)));
                        spawnedArrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                        spawnedArrow.setFireTicks(Integer.MAX_VALUE);
                        spawnedArrow.setShooter(shooter);
                    });
                }
            }, delay);
        }
    }

    @Override
    public ItemStack createItem(int count) {
        var item = new ItemStack(Material.REPEATER);
        GadgetItems.addTag(item, TAG);
        var meta = item.getItemMeta();
        meta.displayName(
                Component.text("ОРЕШНИК").color(NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        item.setAmount(count);
        return item;
    }
}
