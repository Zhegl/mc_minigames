package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.ArrayList;
import java.util.List;

final class MagicItems {
    private static final NamespacedKey FIRST = new NamespacedKey("potatominigames", "magic_wand_first");
    private static final NamespacedKey SECOND = new NamespacedKey("potatominigames", "magic_wand_second");
    private static final NamespacedKey STARTER = new NamespacedKey("potatominigames", "magic_starter_menu");

    static ItemStack wand(MagicWand wand, Player viewer) {
        ItemStack item = new ItemStack(wand.first().school.material);
        item.editMeta(meta -> {
            meta.displayName(I18n.tr(wand.enchanted() ? "party.magic.wand.double" : "party.magic.wand.single",
                    (wand.enchanted() ? wand.first().school.title() : wand.first().title())).color(wand.enchanted() ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            for (MagicSpell spell : new MagicSpell[] {wand.first(), wand.second()}) {
                if (spell == null) continue;
                lore.add(I18n.tr(spell == wand.first() ? "party.magic.wand.right" : "party.magic.wand.left",
                        spell.title(), spell.mana, spell.cooldown / 20.0).color(NamedTextColor.AQUA));
                lore.add(spell.description().color(NamedTextColor.GRAY));
            }
            meta.lore(lore);
            meta.setMaxStackSize(1);
            meta.setEnchantmentGlintOverride(wand.enchanted());
            meta.getPersistentDataContainer().set(FIRST, PersistentDataType.STRING, wand.first().name());
            if (wand.second() != null)
                meta.getPersistentDataContainer().set(SECOND, PersistentDataType.STRING, wand.second().name());
        });
        return I18n.item(item, viewer);
    }

    static MagicWand wand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var data = item.getItemMeta().getPersistentDataContainer();
        String first = data.get(FIRST, PersistentDataType.STRING);
        String second = data.get(SECOND, PersistentDataType.STRING);
        if (first == null) return null;
        try {
            MagicWand wand = new MagicWand(MagicSpell.valueOf(first), second == null ? null : MagicSpell.valueOf(second));
            return item.getType() == wand.first().school.material ? wand : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }

    static MagicSpell.School school(ItemStack item) {
        MagicWand wand = wand(item);
        return wand == null ? null : wand.first().school;
    }

    static ItemStack opener(Player viewer) {
        ItemStack item = new ItemStack(Material.BOOK);
        item.editMeta(meta -> {
            meta.displayName(I18n.tr("party.magic.starter.title").color(NamedTextColor.GOLD));
            meta.lore(List.of(I18n.tr("party.magic.starter.reopen").color(NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(STARTER, PersistentDataType.BYTE, (byte) 1);
        });
        return I18n.item(item, viewer);
    }
    static boolean opener(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(STARTER, PersistentDataType.BYTE);
    }
}
