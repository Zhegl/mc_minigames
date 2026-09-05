package me.petr1furious.potatominigames.gadgets;

import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class RadarGadget extends AbstractGadget {
    private static final String TAG = "radar";

    private final Property<Double> maxDistance;
    private final Property<Double> maxAngleDistance;
    private final Property<Integer> cooldown;
    private final Property<Integer> glowingEffectLength;

    public RadarGadget(GadgetContext context) {
        super("radar", context, true);
        maxDistance = config().define("maxDistance", PropertyCodec.DOUBLE, 40.0);
        maxAngleDistance = config().define("maxAngleDistance", PropertyCodec.DOUBLE, 40.0);
        cooldown = config().define("cooldown", PropertyCodec.INT, 40);
        glowingEffectLength = config().define("glowingEffectLength", PropertyCodec.INT, 4);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!GadgetItems.isRightClick(event))
            return;
        if (!GadgetItems.checkForItem(event.getItem(), TAG))
            return;

        Player player = event.getPlayer();
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.SPECTATOR)
            return;

        int remaining = getRemainingCooldownSeconds(player);
        if (remaining > 0) {
            player.sendActionBar(Component.text("Radar: " + remaining + "s").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 0.5f);
            return;
        }
        setCooldown(player);

        Player target = findTarget(player);
        if (target == null) {
            player.sendActionBar(Component.text("No players in cone").color(NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 0.7f);
            return;
        }

        if (glowingEffectLength.get() > 0) {
            target.addPotionEffect(
                    new PotionEffect(PotionEffectType.GLOWING, glowingEffectLength.get() * 20, 0, false, false));
        }

        player.sendActionBar(Component.text("Radar: ").color(NamedTextColor.YELLOW)
                .append(context.hideTargetNames(player) ? Component.text("enemy", NamedTextColor.AQUA)
                        : Component.text(target.getName(), NamedTextColor.AQUA)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 2f);
        target.playSound(target.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.5f);
    }

    private int getRemainingCooldownSeconds(Player player) {
        return (int) Math.ceil(player.getCooldown(Material.SPYGLASS) / 20.0);
    }

    private void setCooldown(Player player) {
        player.setCooldown(Material.SPYGLASS, cooldown.get() * 20);
    }

    private Player findTarget(Player source) {
        Player nearest = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        double maxDistanceSquared = maxDistance.get() * maxDistance.get();
        double maxAngle = Math.max(0.0, maxAngleDistance.get());
        Vector facing = source.getEyeLocation().getDirection().normalize();

        for (Player player : context.targetsFor(source)) {

            Vector toTarget = player.getEyeLocation().toVector().subtract(source.getEyeLocation().toVector());
            double distanceSquared = toTarget.lengthSquared();
            if (distanceSquared <= 1.0E-6 || distanceSquared > maxDistanceSquared)
                continue;

            double angle = getAngleDegrees(facing, toTarget);
            if (angle > maxAngle)
                continue;

            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                nearest = player;
            }
        }
        return nearest;
    }

    private double getAngleDegrees(Vector facing, Vector toTarget) {
        Vector targetDirection = toTarget.clone().normalize();
        double dot = Math.max(-1.0, Math.min(1.0, facing.dot(targetDirection)));
        return Math.toDegrees(Math.acos(dot));
    }

    @Override
    public ItemStack createItem(int count) {
        var item = new ItemStack(Material.SPYGLASS);
        GadgetItems.addTag(item, TAG);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Radar").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        item.setAmount(count);
        return item;
    }
}
