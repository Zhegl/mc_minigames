package me.petr1furious.potatominigames.core.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Snapshots players before a game and restores them after, plus the shared
 * "make this player a clean participant" reset used at round start.
 */
public final class PlayerStateService {
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();

    public void snapshot(Player player) {
        snapshots.put(player.getUniqueId(), PlayerSnapshot.of(player));
    }

    public boolean hasSnapshot(UUID id) {
        return snapshots.containsKey(id);
    }

    /** Restores and forgets the snapshot. Returns false if none was taken. */
    public boolean restore(Player player) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot == null) {
            return false;
        }
        snapshot.apply(player);
        return true;
    }

    public void forget(UUID id) {
        snapshots.remove(id);
    }

    public void forgetAll() {
        snapshots.clear();
    }

    /**
     * Full clean slate for a game round: gamemode, health, food, effects, inventory,
     * flight, velocity, air, fall distance. Does not teleport.
     */
    public void resetForRound(Player player, GameMode gameMode) {
        player.setGameMode(gameMode);
        player.setFlying(false);
        player.setAllowFlight(gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
        var maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute != null ? maxHealthAttribute.getValue() : 20.0;
        player.setHealth(Math.min(maxHealth, 20.0));
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 0, 0));
        player.setRemainingAir(player.getMaximumAir());
        player.setArrowsInBody(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(new ItemStack(Material.AIR));
        player.updateInventory();
    }

    public void makeSpectator(Player player, int blindnessSeconds) {
        player.setGameMode(GameMode.SPECTATOR);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        if (blindnessSeconds > 0) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.BLINDNESS, blindnessSeconds * 20, 0, false, false));
        }
    }

    public static void unlockAllRecipes(Player player) {
        java.util.Set<NamespacedKey> recipes = new java.util.HashSet<>();
        java.util.Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe instanceof Keyed keyed) {
                recipes.add(keyed.getKey());
            }
        }
        if (!recipes.isEmpty()) {
            player.discoverRecipes(recipes);
        }
    }

    public static void resetAllAdvancements(Player player) {
        java.util.Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criteria : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criteria);
            }
        }
    }
}
