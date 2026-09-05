package me.petr1furious.potatominigames.core.player;

import java.util.Collection;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/** Everything needed to put a player back the way they were before a game. */
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

    public void apply(Player player) {
        player.teleport(location);
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

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }
}
