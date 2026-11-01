package me.petr1furious.potatominigames.games.party;

import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

enum PvpClass {
    GUARDIAN("Страж", "Каменный меч, щит; стойкость I на 4 с", "Стойкость", 20),
    RANGER("Стрелок", "Каменный меч, лук, 20 стрел; ускорение II на 4 с", "Рывок стрелка", 18),
    BERSERKER("Берсерк", "Железный топор, облегчённая броня; сила I на 3 с", "Ярость", 24),
    DUELIST("Дуэлянт", "Железный меч, 2 жемчуга; скорость I и спешка II на 5 с", "Натиск", 18),
    ALCHEMIST("Алхимик", "Каменный меч, яд, 2 зелья лечения; регенерация II на 4 с", "Второе дыхание", 24),
    ENGINEER("Инженер", "Каменный меч, арбалет, 12 стрел, 48 блоков; 2 сердца щита на 5 с", "Защитное поле", 24);

    final String title;
    final String description;
    final String ability;
    final int cooldown;
    PvpClass(String title, String description, String ability, int cooldown) {
        this.title = title; this.description = description; this.ability = ability; this.cooldown = cooldown;
    }
    /** Swap the random matchup, then use an identical random kit for the deciding round. */
    static List<PvpClass> opening(Random random) {
        int first = random.nextInt(values().length);
        int other = random.nextInt(values().length - 1);
        return List.of(values()[first], values()[other >= first ? other + 1 : other]);
    }
    void equip(Player player, NamespacedKey abilityKey) {
        var inv = player.getInventory();
        inv.setHelmet(item(Material.IRON_HELMET));
        inv.setChestplate(item(this == BERSERKER || this == DUELIST ? Material.CHAINMAIL_CHESTPLATE : Material.IRON_CHESTPLATE));
        inv.setLeggings(item(this == BERSERKER ? Material.CHAINMAIL_LEGGINGS : Material.IRON_LEGGINGS));
        inv.setBoots(item(Material.IRON_BOOTS));
        inv.setItem(0, item(this == BERSERKER ? Material.IRON_AXE : this == DUELIST ? Material.IRON_SWORD : Material.STONE_SWORD));
        inv.setItem(1, item(Material.IRON_PICKAXE));
        inv.setItem(2, new ItemStack(Material.WHITE_WOOL, this == ENGINEER ? 48 : 32));
        inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 16));
        switch (this) {
            case GUARDIAN -> inv.setItemInOffHand(item(Material.SHIELD));
            case RANGER -> {
                inv.setItem(3, item(Material.BOW));
                inv.setItem(9, new ItemStack(Material.ARROW, 20));
            }
            case BERSERKER -> inv.setItem(3, new ItemStack(Material.SNOWBALL, 4));
            case DUELIST -> inv.setItem(3, new ItemStack(Material.ENDER_PEARL, 2));
            case ALCHEMIST -> {
                inv.setItem(3, potion(PotionEffectType.POISON, 4 * 20, 0, 1, "Яд • 4 с"));
                inv.setItem(4, potion(PotionEffectType.INSTANT_HEALTH, 1, 0, 2, "Лечение • 2 сердца"));
            }
            case ENGINEER -> {
                inv.setItem(3, item(Material.CROSSBOW));
                inv.setItem(9, new ItemStack(Material.ARROW, 12));
            }
        }
        ItemStack active = new ItemStack(Material.BLAZE_POWDER);
        active.editMeta(meta -> {
            meta.displayName(Component.text(ability, NamedTextColor.GOLD));
            meta.lore(List.of(Component.text("ПКМ • перезарядка " + cooldown + " с", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(abilityKey, PersistentDataType.BYTE, (byte) 1);
        });
        inv.setItem(7, active);
        inv.setHeldItemSlot(0);
    }
    static ItemStack item(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.setUnbreakable(true));
        return item;
    }
    private static ItemStack potion(PotionEffectType effect, int ticks, int level, int amount, String name) {
        ItemStack item = new ItemStack(Material.SPLASH_POTION, amount);
        item.editMeta(PotionMeta.class, meta -> {
            meta.addCustomEffect(new PotionEffect(effect, ticks, level), true);
            meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE));
        });
        return item;
    }
    void activate(Player player) {
        switch (this) {
            case GUARDIAN -> effect(player, PotionEffectType.RESISTANCE, 4, 0);
            case RANGER -> effect(player, PotionEffectType.SPEED, 4, 1);
            case BERSERKER -> effect(player, PotionEffectType.STRENGTH, 3, 0);
            case DUELIST -> {
                effect(player, PotionEffectType.SPEED, 5, 0);
                effect(player, PotionEffectType.HASTE, 5, 1);
            }
            case ALCHEMIST -> effect(player, PotionEffectType.REGENERATION, 4, 1);
            case ENGINEER -> effect(player, PotionEffectType.ABSORPTION, 5, 0);
        }
    }
    private static void effect(Player player, PotionEffectType type, int seconds, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier));
    }
}
