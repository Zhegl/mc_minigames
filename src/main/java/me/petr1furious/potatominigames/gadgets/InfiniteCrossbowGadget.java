package me.petr1furious.potatominigames.gadgets;

import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import me.petr1furious.potatominigames.core.world.Locations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;

import java.util.ArrayList;
import java.util.List;

public class InfiniteCrossbowGadget extends AbstractGadget {
    private static final String TAG = "infinite_crossbow";

    private final Property<Boolean> enableExplosions;
    private final Property<Double> explosionPower;
    private final Property<Integer> maxLoadedProjectiles;

    public InfiniteCrossbowGadget(GadgetContext context) {
        super("infinite-crossbow", context, true);
        enableExplosions = config().define("enableExplosions", PropertyCodec.BOOL, true);
        explosionPower = config().define("explosionPower", PropertyCodec.DOUBLE, 1.8);
        maxLoadedProjectiles = config().define("maxLoadedProjectiles", PropertyCodec.INT, 10);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        var item = event.getItem();
        if (!GadgetItems.checkForItem(item, TAG))
            return;
        if (!(item.getItemMeta() instanceof CrossbowMeta meta))
            return;
        int count = meta.getChargedProjectiles().size();
        int max = maxLoadedProjectiles.get();
        if (count < max) {
            meta.addChargedProjectile(new ItemStack(Material.ARROW));
            setLore(meta, count + 1, max);
            item.setItemMeta(meta);
            if (GadgetItems.isLeftClick(event)) {
                event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.1f, 2f);
            }
        } else if (GadgetItems.isLeftClick(event)) {
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.1f, 0.5f);
        }
    }

    @Override
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof Arrow arrow))
            return;
        var bow = event.getBow();
        if (bow == null)
            return;
        if (GadgetItems.checkForItem(bow, TAG)) {
            GadgetItems.setProjectileTag(arrow, TAG);
        }
    }

    @Override
    public boolean handleProjectileImpact(Projectile projectile, org.bukkit.Location location, Entity shooter,
            Entity target) {
        if (!GadgetItems.checkForProjectile(projectile, TAG))
            return false;
        if (!enableExplosions.get())
            return false;
        Locations.spawnExplosion(location, explosionPower.get(), shooter);
        projectile.remove();
        return true;
    }

    @Override
    public ItemStack createItem(int count) {
        var crossbow = new ItemStack(Material.CROSSBOW);
        GadgetItems.addTag(crossbow, TAG);
        var meta = crossbow.getItemMeta();
        meta.displayName(
                Component.text("Infinite Crossbow").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,
                        false));
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        crossbow.setItemMeta(meta);
        var cmeta = (CrossbowMeta) crossbow.getItemMeta();
        cmeta.addChargedProjectile(new ItemStack(Material.ARROW));
        setLore(cmeta, 1, maxLoadedProjectiles.get());
        crossbow.setItemMeta(cmeta);
        crossbow.setAmount(count);
        return crossbow;
    }

    private void setLore(CrossbowMeta meta, int projectiles, int max) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Crossbow with infinite ammo").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (max > 1) {
            lore.add(Component.text("Loaded: " + projectiles + "/" + max).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
    }
}
