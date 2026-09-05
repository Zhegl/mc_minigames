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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HimarsGadget extends AbstractGadget {
    private static final String TAG = "himars";

    private final Property<Integer> cooldown;
    private final Property<Double> explosionPowerPerBlock;
    private final Property<Double> maxExplosionPower;
    private final Property<Double> fireworkSpeedBlocksPerTick;
    private final Property<Double> aimDirectionBlend;

    private final Map<UUID, Location> projectileStart = new HashMap<>();

    public HimarsGadget(GadgetContext context) {
        super("himars", context, true);
        cooldown = config().define("cooldown", PropertyCodec.INT, 2);
        explosionPowerPerBlock = config().define("explosionPowerPerBlock", PropertyCodec.DOUBLE, 1 / 30.0);
        maxExplosionPower = config().define("maxExplosionPower", PropertyCodec.DOUBLE, 5.0);
        fireworkSpeedBlocksPerTick = config().define("fireworkSpeedBlocksPerTick", PropertyCodec.DOUBLE, 3.0);
        aimDirectionBlend = config().define("aimDirectionBlend", PropertyCodec.DOUBLE, 0.85);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!GadgetItems.checkForItem(event.getItem(), TAG))
            return;
        var item = event.getItem();
        if (!(item.getItemMeta() instanceof CrossbowMeta meta))
            return;
        if (meta.getChargedProjectiles().isEmpty()) {
            meta.addChargedProjectile(new ItemStack(Material.FIREWORK_ROCKET));
            item.setItemMeta(meta);
        }
    }

    @Override
    public void onBowShoot(EntityShootBowEvent event) {
        var bow = event.getBow();
        if (bow == null)
            return;
        if (event.getProjectile() instanceof Firework firework && GadgetItems.checkForItem(bow, TAG)) {
            Player shooter = (Player) event.getEntity();
            int remaining = shooter.getGameMode() == GameMode.SURVIVAL ? getRemainingCooldownSeconds(shooter) : 0;
            if (remaining > 0) {
                shooter.sendActionBar(Component.text("HIMARS: " + remaining + "s").color(NamedTextColor.RED));
                event.setCancelled(true);
                return;
            }
            setCooldown(shooter);
            applyLaunchDirection(firework, shooter);
            GadgetItems.setProjectileTag(firework, TAG);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.setPower(20);
            firework.setFireworkMeta(meta);
            projectileStart.put(firework.getUniqueId(), shooter.getLocation());
        }
    }

    @Override
    public boolean handleProjectileImpact(Projectile projectile, Location location, Entity shooter, Entity target) {
        if (!GadgetItems.checkForProjectile(projectile, TAG))
            return false;
        Location start = projectileStart.remove(projectile.getUniqueId());
        double power = 1.0;
        if (start != null) {
            power = Math.min(start.distance(location) * explosionPowerPerBlock.get(), maxExplosionPower.get());
        }
        Locations.spawnExplosion(location, power, shooter);
        projectile.remove();
        return true;
    }

    @Override
    public void onGameEnd() {
        projectileStart.clear();
    }

    private int getRemainingCooldownSeconds(Player player) {
        return (int) Math.ceil(player.getCooldown(Material.CROSSBOW) / 20.0);
    }

    private void setCooldown(Player player) {
        player.setCooldown(Material.CROSSBOW, cooldown.get() * 20);
    }

    private void applyLaunchDirection(Firework firework, Player shooter) {
        Vector vanillaVelocity = firework.getVelocity();
        Vector vanillaDirection = vanillaVelocity.clone().normalize();
        Vector aimDirection = shooter.getEyeLocation().getDirection().normalize();
        double blend = Math.max(0.0, Math.min(1.0, aimDirectionBlend.get()));
        Vector finalDirection = aimDirection.multiply(blend).add(vanillaDirection.multiply(1.0 - blend));

        if (finalDirection.lengthSquared() <= 1.0E-6) {
            finalDirection = aimDirection;
        } else {
            finalDirection.normalize();
        }

        firework.setVelocity(finalDirection.multiply(fireworkSpeedBlocksPerTick.get()));
    }

    @Override
    public ItemStack createItem(int count) {
        var crossbow = new ItemStack(Material.CROSSBOW);
        GadgetItems.addTag(crossbow, TAG);
        var meta = crossbow.getItemMeta();
        meta.displayName(Component.text("HIMARS").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        crossbow.setItemMeta(meta);
        crossbow.setAmount(count);
        return crossbow;
    }
}
