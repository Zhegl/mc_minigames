package me.petr1furious.potatominigames.games.party;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** One kill per weapon, ending with the deliberately difficult golden shovel. */
enum ArmsRaceWeapon {
    DIAMOND_SWORD(Material.DIAMOND_SWORD, "Алмазный меч"),
    IRON_SWORD(Material.IRON_SWORD, "Железный меч"),
    DIAMOND_AXE(Material.DIAMOND_AXE, "Алмазный топор"),
    STONE_SWORD(Material.STONE_SWORD, "Каменный меч"),
    IRON_AXE(Material.IRON_AXE, "Железный топор"),
    BOW(Material.BOW, "Лук"),
    CROSSBOW(Material.CROSSBOW, "Арбалет"),
    STONE_AXE(Material.STONE_AXE, "Каменный топор"),
    WOODEN_SWORD(Material.WOODEN_SWORD, "Деревянный меч"),
    WOODEN_AXE(Material.WOODEN_AXE, "Деревянный топор"),
    GOLDEN_SWORD(Material.GOLDEN_SWORD, "Золотой меч"),
    GOLDEN_SHOVEL(Material.GOLDEN_SHOVEL, "Золотая лопата");

    final Material material;
    final String title;

    ArmsRaceWeapon(Material material, String title) { this.material = material; this.title = title; }
    boolean ranged() { return this == BOW || this == CROSSBOW; }
    static ArmsRaceWeapon at(int stage) { return values()[stage]; }

    void equip(Player player) {
        var inventory = player.getInventory();
        inventory.clear();
        inventory.setHelmet(PvpClass.item(Material.CHAINMAIL_HELMET));
        inventory.setChestplate(PvpClass.item(Material.CHAINMAIL_CHESTPLATE));
        inventory.setLeggings(PvpClass.item(Material.CHAINMAIL_LEGGINGS));
        inventory.setBoots(PvpClass.item(Material.CHAINMAIL_BOOTS));
        inventory.setItemInOffHand(new ItemStack(Material.AIR));
        ItemStack weapon = PvpClass.item(material);
        weapon.editMeta(meta -> meta.displayName(Component.text(title, NamedTextColor.GOLD)));
        inventory.setItem(0, weapon);
        inventory.setHeldItemSlot(0);
        replenish(player);
    }
    void replenish(Player player) {
        if (!ranged()) return;
        ItemStack arrows = player.getInventory().getItem(9);
        if (arrows == null || arrows.getType() != Material.ARROW || arrows.getAmount() < 16) {
            player.getInventory().setItem(9, new ItemStack(Material.ARROW, 16));
        }
    }
}
