package me.petr1furious.potatominigames.games.party;

import java.util.List;
import org.bukkit.Color;
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

/** Tagged, stackable class potions. Beneficial splashes affect their caster; debuffs affect enemies. */
enum PvpPotion {
    HEALING("Лечение I • 2 сердца", false, 1, 0xF94B4B),
    HARMING("Мгновенный урон I", true, 1, 0x652B85),
    POISON("Яд I • 5 с", true, 100, 0x59B542),
    SLOWNESS("Замедление I • 4 с", true, 80, 0x718AA8),
    WEAKNESS("Слабость I • 6 с", true, 120, 0x626262),
    SPEED("Скорость I • 6 с", false, 120, 0x7CAFC6);

    private static final NamespacedKey KEY = new NamespacedKey("potatominigames", "pvp_potion");
    final String title;
    final boolean harmful;
    final int durationTicks;
    private final int color;

    PvpPotion(String title, boolean harmful, int durationTicks, int color) {
        this.title = title; this.harmful = harmful; this.durationTicks = durationTicks; this.color = color;
    }
    private PotionEffectType effectType() {
        return switch (this) {
            case HEALING -> PotionEffectType.INSTANT_HEALTH;
            case HARMING -> PotionEffectType.INSTANT_DAMAGE;
            case POISON -> PotionEffectType.POISON;
            case SLOWNESS -> PotionEffectType.SLOWNESS;
            case WEAKNESS -> PotionEffectType.WEAKNESS;
            case SPEED -> PotionEffectType.SPEED;
        };
    }
    boolean affects(boolean caster, boolean activeFighter) { return activeFighter && (harmful ? !caster : caster); }
    ItemStack item(int amount) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION, amount);
        potion.editMeta(PotionMeta.class, meta -> {
            meta.setMaxStackSize(8);
            meta.setColor(Color.fromRGB(color));
            meta.addCustomEffect(new PotionEffect(effectType(), durationTicks, 0), true);
            meta.displayName(Component.text(title, harmful ? NamedTextColor.RED : NamedTextColor.GREEN));
            meta.lore(List.of(Component.text(harmful ? "Брось в соперника • на себя не действует"
                    : "Брось под себя • действует только на тебя", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, name());
        });
        return potion;
    }
    static PvpPotion from(ItemStack item) {
        if (item == null || item.getType() != Material.SPLASH_POTION) return null;
        String name = item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (name == null) return null;
        try { return valueOf(name); } catch (IllegalArgumentException unknown) { return null; }
    }
    int replenish(Player player, int amount, int cap) {
        int owned = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (from(item) == this) owned += item.getAmount();
        }
        int added = refillAmount(owned, amount, cap);
        if (added == 0) return 0;
        int leftover = player.getInventory().addItem(item(added)).values().stream().mapToInt(ItemStack::getAmount).sum();
        return added - leftover;
    }
    static int refillAmount(int owned, int requested, int cap) { return Math.max(0, Math.min(requested, cap - owned)); }
}
