package me.petr1furious.potatominigames.games.bingo;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Kit items are PDC-marked so they never count for board claims. */
final class BingoKit {
    private final NamespacedKey starterItemKey;

    BingoKit(NamespacedKey starterItemKey) {
        this.starterItemKey = starterItemKey;
    }

    boolean isStarterItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(starterItemKey, PersistentDataType.BYTE);
        return marker != null && marker == 1;
    }

    void fillStarterItems(PlayerInventory inventory) {
        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        pickaxe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        pickaxe.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
        setLegacyNameUnbreakable(pickaxe, "Potato Pickaxe");

        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        axe.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        axe.addUnsafeEnchantment(Enchantment.LOOTING, 3);
        axe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        setLegacyNameUnbreakable(axe, "Potato Axe");

        ItemStack shovel = new ItemStack(Material.NETHERITE_SHOVEL);
        shovel.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        shovel.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        setLegacyNameUnbreakable(shovel, "Potato Shovel");

        ItemStack bakedPotato = new ItemStack(Material.COOKED_PORKCHOP, 64);
        ItemMeta bakedMeta = bakedPotato.getItemMeta();
        bakedMeta.displayName(Component.text("Baked Potato", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        markStarterItem(bakedMeta);
        bakedPotato.setItemMeta(bakedMeta);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        boots.addUnsafeEnchantment(Enchantment.DEPTH_STRIDER, 3);
        setLegacyNameUnbreakable(boots, "Potato Boots");

        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        helmet.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
        setLegacyNameUnbreakable(helmet, "Potato Helmet");

        inventory.setItem(0, axe);
        inventory.setItem(1, pickaxe);
        inventory.setItem(2, shovel);
        inventory.setItem(3, bakedPotato);
        inventory.setArmorContents(new ItemStack[] { boots, null, null, helmet });
    }

    private void setLegacyNameUnbreakable(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markStarterItem(meta);
        item.setItemMeta(meta);
    }

    private void markStarterItem(ItemMeta meta) {
        meta.getPersistentDataContainer().set(starterItemKey, PersistentDataType.BYTE, (byte) 1);
    }
}
