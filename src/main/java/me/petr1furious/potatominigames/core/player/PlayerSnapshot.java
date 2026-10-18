package me.petr1furious.potatominigames.core.player;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public record PlayerSnapshot(
        Location location,
        GameMode gameMode,
        ItemStack[] inventoryContents,
        double health,
        int foodLevel,
        float saturation,
        int fireTicks,
        int expLevel,
        float expProgress,
        boolean allowFlight,
        boolean flying,
        Collection<PotionEffect> effects) {

    public static PlayerSnapshot of(Player player) {
        return new PlayerSnapshot(
                player.getLocation().clone(),
                player.getGameMode(),
                cloneContents(player.getInventory().getContents()),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getFireTicks(),
                player.getLevel(),
                player.getExp(),
                player.getAllowFlight(),
                player.isFlying(),
                List.copyOf(player.getActivePotionEffects()));
    }

    public PlayerSnapshot withLocation(Location newLocation) {
        return new PlayerSnapshot(newLocation.clone(), gameMode, inventoryContents, health, foodLevel, saturation,
                fireTicks, expLevel, expProgress, allowFlight, flying, effects);
    }

    public void apply(Player player) {
        player.teleport(location);
        applyState(player);
    }

    /** Everything except the location. */
    public void applyState(Player player) {
        player.setGameMode(gameMode);
        player.getInventory().setContents(cloneContents(inventoryContents));
        var maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute != null ? maxHealthAttribute.getValue() : 20.0;
        player.setHealth(Math.min(health, maxHealth));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setFireTicks(fireTicks);
        player.setLevel(expLevel);
        player.setExp(expProgress);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.addPotionEffects(effects);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
        player.updateInventory();
    }

    public void save(ConfigurationSection section) {
        section.set("location", location);
        section.set("gameMode", gameMode.name());
        // Item bytes carry their data version.
        section.set("inventory", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(
                replaceNulls(inventoryContents))));
        section.set("inventorySize", inventoryContents.length);
        section.set("health", health);
        section.set("foodLevel", foodLevel);
        section.set("saturation", (double) saturation);
        section.set("fireTicks", fireTicks);
        section.set("expLevel", expLevel);
        section.set("expProgress", (double) expProgress);
        section.set("allowFlight", allowFlight);
        section.set("flying", flying);
        section.set("effects", new ArrayList<>(effects));
    }

    /** Null if the section is unusable. */
    public static PlayerSnapshot load(ConfigurationSection section) {
        Location location = section.getLocation("location");
        if (location == null || location.getWorld() == null) {
            return null;
        }
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(
                Base64.getDecoder().decode(section.getString("inventory", "")));
        ItemStack[] contents = new ItemStack[section.getInt("inventorySize", items.length)];
        for (int i = 0; i < contents.length && i < items.length; i++) {
            contents[i] = items[i] == null || items[i].isEmpty() ? null : items[i];
        }
        List<PotionEffect> effects = new ArrayList<>();
        for (Object effect : section.getList("effects", List.of())) {
            if (effect instanceof PotionEffect potionEffect) {
                effects.add(potionEffect);
            }
        }
        return new PlayerSnapshot(
                location,
                GameMode.valueOf(section.getString("gameMode", GameMode.SURVIVAL.name())),
                contents,
                section.getDouble("health", 20.0),
                section.getInt("foodLevel", 20),
                (float) section.getDouble("saturation", 5.0),
                section.getInt("fireTicks", 0),
                section.getInt("expLevel", 0),
                (float) section.getDouble("expProgress", 0.0),
                section.getBoolean("allowFlight", false),
                section.getBoolean("flying", false),
                List.copyOf(effects));
    }

    private static ItemStack[] replaceNulls(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? ItemStack.empty() : contents[i];
        }
        return copy;
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }
}
