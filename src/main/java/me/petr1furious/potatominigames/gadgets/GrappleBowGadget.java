package me.petr1furious.potatominigames.gadgets;

import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

public class GrappleBowGadget extends AbstractGadget {
    private static final String TAG = "grapple_bow";

    private final Property<Integer> cooldown;
    private final Property<Double> pullStrength;
    private final Property<Double> verticalBoost;
    private final Property<Double> maxSpeed;
    private final Property<Boolean> disableWhileGliding;

    public GrappleBowGadget(GadgetContext context) {
        super("grapple-bow", context, true);
        cooldown = config().define("cooldown", PropertyCodec.INT, 30);
        pullStrength = config().define("pullStrength", PropertyCodec.DOUBLE, 1.8);
        verticalBoost = config().define("verticalBoost", PropertyCodec.DOUBLE, 0.2);
        maxSpeed = config().define("maxSpeed", PropertyCodec.DOUBLE, 2.8);
        disableWhileGliding = config().define("disableWhileGliding", PropertyCodec.BOOL, true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBowShoot(EntityShootBowEvent event) {
        var bow = event.getBow();
        if (bow == null || !GadgetItems.checkForItem(bow, TAG))
            return;
        if (!(event.getEntity() instanceof Player shooter))
            return;

        if (disableWhileGliding.get() && shooter.isGliding()) {
            shooter.sendActionBar(Component.text("Grapple Bow disabled while gliding").color(NamedTextColor.RED));
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 0.5f);
            event.setCancelled(true);
            return;
        }
        int remaining = getRemainingCooldownSeconds(shooter);
        if (remaining > 0) {
            shooter.sendActionBar(Component.text("Grapple Bow: " + remaining + "s").color(NamedTextColor.RED));
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 0.5f);
            event.setCancelled(true);
            return;
        }

        setCooldown(shooter);
        event.setConsumeItem(false);

        if (event.getProjectile() instanceof AbstractArrow arrow) {
            GadgetItems.setProjectileTag(arrow, TAG);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setDamage(0.0);
            arrow.setCritical(false);
            shooter.playSound(shooter.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.8f, 1.2f);
        }
    }

    @Override
    public boolean handleProjectileImpact(Projectile projectile, org.bukkit.Location location, Entity shooterEntity,
            Entity target) {
        if (!GadgetItems.checkForProjectile(projectile, TAG))
            return false;
        if (!(shooterEntity instanceof Player shooter))
            return false;

        Vector pull = location.toVector().subtract(shooter.getLocation().toVector());
        if (target != null) {
            pull = target.getLocation().toVector().subtract(shooter.getLocation().toVector());
        }

        if (pull.lengthSquared() > 1.0E-6) {
            Vector velocity = pull.normalize().multiply(pullStrength.get());
            velocity.setY(velocity.getY() + verticalBoost.get());
            double cappedMaxSpeed = Math.max(0.1, maxSpeed.get());
            if (velocity.length() > cappedMaxSpeed) {
                velocity.normalize().multiply(cappedMaxSpeed);
            }
            shooter.setVelocity(velocity);
            shooter.setFallDistance(0);
            shooter.playSound(shooter.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 1.1f);
        }

        projectile.remove();
        return true;
    }

    private int getRemainingCooldownSeconds(Player player) {
        return (int) Math.ceil(player.getCooldown(Material.BOW) / 20.0);
    }

    private void setCooldown(Player player) {
        player.setCooldown(Material.BOW, cooldown.get() * 20);
    }

    @Override
    public ItemStack createItem(int count) {
        var bow = new ItemStack(Material.BOW);
        GadgetItems.addTag(bow, TAG);
        ItemMeta meta = bow.getItemMeta();
        meta.displayName(
                Component.text("Grapple Bow").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.setEnchantmentGlintOverride(true);
        bow.setItemMeta(meta);
        bow.setAmount(count);
        return bow;
    }
}
