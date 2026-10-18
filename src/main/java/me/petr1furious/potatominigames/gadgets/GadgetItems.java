package me.petr1furious.potatominigames.gadgets;

import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Item/projectile tagging via custom model data strings and PDC. */
public final class GadgetItems {
    private static final NamespacedKey PROJECTILE_TYPE_KEY = new NamespacedKey("potato_minigames",
            "projectile_type");

    private GadgetItems() {
    }

    public static boolean checkForItem(ItemStack item, String tag) {
        if (item == null) {
            return false;
        }
        // Empty slots are AIR stacks with null meta.
        var meta = item.getItemMeta();
        if (meta != null && meta.hasCustomModelDataComponent()) {
            List<String> cmd = meta.getCustomModelDataComponent().getStrings();
            return cmd.size() == 1 && cmd.get(0).equals(tag);
        }
        return false;
    }

    public static boolean checkForProjectile(Projectile projectile, String tag) {
        if (projectile == null) {
            return false;
        }
        if (projectile.getPersistentDataContainer().has(PROJECTILE_TYPE_KEY, PersistentDataType.STRING)) {
            String value = projectile.getPersistentDataContainer().get(PROJECTILE_TYPE_KEY,
                    PersistentDataType.STRING);
            return value.equals(tag);
        }
        return false;
    }

    public static boolean setProjectileTag(Projectile projectile, String tag) {
        if (projectile == null) {
            return false;
        }
        projectile.getPersistentDataContainer().set(PROJECTILE_TYPE_KEY, PersistentDataType.STRING, tag);
        return true;
    }

    public static boolean isLeftClick(PlayerInteractEvent event) {
        return event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR
                || event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK;
    }

    public static boolean isRightClick(PlayerInteractEvent event) {
        return event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
    }

    public static void addTag(ItemStack item, String tag) {
        if (item == null || tag == null || tag.isEmpty())
            return;
        var meta = item.getItemMeta();
        if (meta == null)
            return;
        var cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(tag));
        meta.setCustomModelDataComponent(cmd);
        item.setItemMeta(meta);
    }
}
