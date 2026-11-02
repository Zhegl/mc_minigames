package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Random physical drops, never automatic equipment upgrades. Rolls are testable without Bukkit registries. */
final class MinersLoot {
    enum Quality { COMMON, GOOD, RARE }
    record Drop(Material material, int amount, Quality quality, String enchant, int level, PvpPotion potion) {
        ItemStack item() {
            ItemStack item = potion != null ? potion.item(amount) : material == Material.TNT ? PvpBombs.item(amount) : new ItemStack(material, amount);
            item.editMeta(meta -> {
                if (material.getMaxDurability() > 0) meta.setUnbreakable(true);
                if (enchant != null && level > 0) meta.addEnchant(resolve(enchant), level, true);
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.text("Шахтёрский лут • " + switch (quality) {
                    case COMMON -> "обычный"; case GOOD -> "хороший"; case RARE -> "редкий";
                }, quality == Quality.RARE ? NamedTextColor.LIGHT_PURPLE : quality == Quality.GOOD ? NamedTextColor.AQUA : NamedTextColor.GRAY));
                meta.lore(lore);
            });
            return item;
        }
    }
    static Quality quality(Random random) {
        int roll = random.nextInt(100);
        return roll < 70 ? Quality.COMMON : roll < 95 ? Quality.GOOD : Quality.RARE;
    }
    static List<Drop> roll(MinersOre ore, Random random) {
        if (ore == MinersOre.GOLD) return List.of();
        Quality quality = quality(random);
        if (ore == MinersOre.EMERALD) {
            MinersOre[] pools = {MinersOre.COAL, MinersOre.IRON, MinersOre.DIAMOND, MinersOre.LAPIS, MinersOre.REDSTONE};
            if (quality == Quality.COMMON) quality = Quality.GOOD;
            if (random.nextInt(5) == 0) return List.of(drop(Material.GOLDEN_APPLE, 1, quality, null, 0));
            return roll(pools[random.nextInt(pools.length)], quality, random);
        }
        return roll(ore, quality, random);
    }
    private static List<Drop> roll(MinersOre ore, Quality q, Random random) {
        int grade = q.ordinal();
        return switch (ore) {
            case COAL -> {
                if (q == Quality.COMMON && random.nextBoolean()) yield List.of(drop(Material.COOKED_BEEF, 12, q, null, 0));
                Material pick = q == Quality.RARE || q == Quality.GOOD && random.nextBoolean() ? Material.DIAMOND_PICKAXE : Material.IRON_PICKAXE;
                yield List.of(drop(pick, 1, q, "efficiency", grade + 3));
            }
            case IRON -> {
                Material[][] armour = {
                    {Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS},
                    {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS},
                    {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}
                };
                int level = q == Quality.COMMON ? (random.nextInt(4) == 0 ? 1 : 0) : q == Quality.GOOD ? 1 + random.nextInt(2) : 1;
                yield List.of(drop(armour[grade][random.nextInt(4)], 1, q, level > 0 ? "protection" : null, level));
            }
            case DIAMOND -> {
                int weapon = random.nextInt(4);
                if (weapon < 2) {
                    Material material = weapon == 0 ? (q == Quality.COMMON ? Material.IRON_SWORD : Material.DIAMOND_SWORD)
                            : q == Quality.RARE ? Material.DIAMOND_AXE : q == Quality.GOOD ? Material.IRON_AXE : Material.STONE_AXE;
                    yield List.of(drop(material, 1, q, grade > 0 ? "sharpness" : null, grade));
                }
                Material material = weapon == 2 ? Material.BOW : Material.CROSSBOW;
                yield List.of(drop(material, 1, q, material == Material.BOW ? "power" : "quick_charge", grade),
                        drop(Material.ARROW, 32 + grade * 16, q, null, 0));
            }
            case LAPIS -> {
                if (q == Quality.RARE) yield List.of(potion(PvpPotion.HEALING, 2, q), potion(PvpPotion.SPEED, 1, q));
                PvpPotion[] types = q == Quality.COMMON ? new PvpPotion[] {PvpPotion.SPEED, PvpPotion.SLOWNESS, PvpPotion.WEAKNESS}
                        : new PvpPotion[] {PvpPotion.HEALING, PvpPotion.POISON, PvpPotion.HARMING};
                yield List.of(potion(types[random.nextInt(types.length)], 1, q));
            }
            case REDSTONE -> {
                Material[] supplies = {Material.TNT, Material.ENDER_PEARL, Material.WIND_CHARGE};
                int choice = random.nextInt(supplies.length);
                yield List.of(drop(supplies[choice], choice == 0 ? 2 + grade : choice == 1 ? 1 + grade : 4 + grade * 2, q, null, 0));
            }
            default -> throw new IllegalArgumentException("Not a loot pool: " + ore);
        };
    }
    private static Drop drop(Material material, int amount, Quality quality, String enchant, int level) {
        return new Drop(material, amount, quality, enchant, level, null);
    }
    private static Drop potion(PvpPotion potion, int amount, Quality quality) {
        return new Drop(Material.SPLASH_POTION, amount, quality, null, 0, potion);
    }
    private static Enchantment resolve(String enchant) {
        return switch (enchant) {
            case "efficiency" -> Enchantment.EFFICIENCY;
            case "protection" -> Enchantment.PROTECTION;
            case "sharpness" -> Enchantment.SHARPNESS;
            case "power" -> Enchantment.POWER;
            case "quick_charge" -> Enchantment.QUICK_CHARGE;
            default -> throw new IllegalArgumentException(enchant);
        };
    }
}
