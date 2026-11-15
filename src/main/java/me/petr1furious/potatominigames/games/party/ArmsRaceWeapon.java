package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** One kill per weapon; standalone ends with a golden shovel, Party Games with a bow. */
enum ArmsRaceWeapon {
    DIAMOND_SWORD(Material.DIAMOND_SWORD),
    IRON_SWORD(Material.IRON_SWORD),
    DIAMOND_AXE(Material.DIAMOND_AXE),
    STONE_SWORD(Material.STONE_SWORD),
    IRON_AXE(Material.IRON_AXE),
    BOW(Material.BOW),
    CROSSBOW(Material.CROSSBOW),
    STONE_AXE(Material.STONE_AXE),
    WOODEN_SWORD(Material.WOODEN_SWORD),
    WOODEN_AXE(Material.WOODEN_AXE),
    GOLDEN_SWORD(Material.GOLDEN_SWORD),
    GOLDEN_SHOVEL(Material.GOLDEN_SHOVEL);

    final Material material;
    final Component title;

    ArmsRaceWeapon(Material material) {
        this.material = material;
        // These vanilla item weapons must also initialize without a running server registry.
        this.title = Component.translatable("item.minecraft." + material.name().toLowerCase(java.util.Locale.ROOT));
    }

    boolean ranged() {
        return this == BOW || this == CROSSBOW;
    }

    static ArmsRaceWeapon at(int stage) {
        return values()[stage];
    }

    static java.util.List<ArmsRaceWeapon> progression(boolean embedded) {
        return embedded
                ? java.util.List.of(
                        DIAMOND_SWORD, IRON_AXE, WOODEN_SWORD, GOLDEN_SHOVEL, BOW)
                : java.util.List.of(values());
    }

    void equip(Player player) {
        var inventory = player.getInventory();
        inventory.clear();
        inventory.setHelmet(PvpClass.item(Material.CHAINMAIL_HELMET));
        inventory.setChestplate(PvpClass.item(Material.CHAINMAIL_CHESTPLATE));
        inventory.setLeggings(PvpClass.item(Material.CHAINMAIL_LEGGINGS));
        inventory.setBoots(PvpClass.item(Material.CHAINMAIL_BOOTS));
        inventory.setItemInOffHand(new ItemStack(Material.AIR));
        ItemStack weapon = PvpClass.item(material);
        weapon.editMeta(meta -> meta.displayName(title.color(NamedTextColor.GOLD)));
        inventory.setItem(0, I18n.item(weapon, player));
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
