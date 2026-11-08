package me.petr1furious.potatominigames.games.pillars;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Charges travel with the actual item, including theft, storage and dropping. */
final class PerkItems {
    static final NamespacedKey TYPE = key("type"), SERIAL = key("serial"), CHARGES = key("charges"),
            GIFT = key("gift"), STARTED = key("started"), EXPIRES = key("expires");
    private static NamespacedKey key(String s) { return new NamespacedKey("potatominigames", "perk_" + s); }
    static PerkType type(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(TYPE, PersistentDataType.STRING);
        if (id == null) return null;
        try { PerkType type = PerkType.valueOf(id); return item.getType() == type.material ? type : null; }
        catch (IllegalArgumentException e) { return null; }
    }
    static String string(ItemStack item, NamespacedKey key) {
        return item == null || !item.hasItemMeta() ? null : item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
    static int charges(ItemStack item) {
        return type(item) == null ? 0 : item.getItemMeta().getPersistentDataContainer().getOrDefault(CHARGES, PersistentDataType.INTEGER, 0);
    }
    static long number(ItemStack item, NamespacedKey key, long fallback) {
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, fallback);
    }
    static void setNumber(ItemStack item, NamespacedKey key, long value) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, value));
    }
    static void tag(ItemStack item, NamespacedKey key, String value) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value));
    }
    static ItemStack create(PerkType type) {
        ItemStack item = new ItemStack(type.material);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(TYPE, PersistentDataType.STRING, type.name());
            meta.getPersistentDataContainer().set(SERIAL, PersistentDataType.STRING, UUID.randomUUID().toString());
            meta.getPersistentDataContainer().set(CHARGES, PersistentDataType.INTEGER, type.charges);
            meta.setEnchantmentGlintOverride(true); meta.setMaxStackSize(1); meta.setUnbreakable(true);
            if (type == PerkType.BOW) meta.addEnchant(Enchantment.PUNCH, 100, true);
        });
        if (type == PerkType.BREAK) item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(3600).animation(ItemUseAnimation.BLOCK).hasConsumeParticles(false).build());
        describe(item);
        return item;
    }
    static void charges(ItemStack item, int count) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(CHARGES, PersistentDataType.INTEGER, Math.max(0, count)));
        describe(item);
    }
    private static void describe(ItemStack item) {
        PerkType type = type(item);
        item.editMeta(meta -> {
            var color = type.legendary ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE;
            meta.displayName(Component.text((type.legendary ? "★ " : "") + type.title, color).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : type.instructions) lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(type == PerkType.GRAVITY ? "Многоразовый" : "Заряды: " + charges(item), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            if (type == PerkType.PLANKS && number(item, STARTED, 0) != 0)
                lore.add(Component.text("Уже активированы • повторно не запустить", NamedTextColor.RED));
            meta.lore(lore);
        });
    }
    static List<ItemStack> bundle(PerkType type) {
        return type == PerkType.BOW ? List.of(create(type), new ItemStack(org.bukkit.Material.ARROW)) : List.of(create(type));
    }
    static ItemStack cursedPumpkin() {
        ItemStack item = new ItemStack(org.bukkit.Material.CARVED_PUMPKIN);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Проклятая тыква", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        });
        return item;
    }
}
