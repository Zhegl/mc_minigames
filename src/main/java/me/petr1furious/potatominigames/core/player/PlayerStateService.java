package me.petr1furious.potatominigames.core.player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Snapshots players before a game (or before the lobby) and restores them after,
 * plus the shared "make this player a clean participant" reset used at round start.
 *
 * <p>Snapshots are written to {@code data/snapshots/<uuid>.yml} as soon as they are
 * taken: a player can wait in the lobby for hours with their real inventory held
 * here, so a restart must not lose it.
 */
public final class PlayerStateService {
    private final JavaPlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();

    public PlayerStateService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "data/snapshots");
    }

    public void snapshot(Player player) {
        put(player.getUniqueId(), PlayerSnapshot.of(player));
    }

    /** Takes a snapshot unless one already exists; returns whether one was taken. */
    public boolean snapshotIfAbsent(Player player) {
        if (hasSnapshot(player.getUniqueId())) {
            return false;
        }
        snapshot(player);
        return true;
    }

    public void put(UUID id, PlayerSnapshot snapshot) {
        snapshots.put(id, snapshot);
        write(id, snapshot);
    }

    public boolean hasSnapshot(UUID id) {
        return snapshots.containsKey(id);
    }

    /** Restores and forgets the snapshot. Returns false if none was taken. */
    public boolean restore(Player player) {
        PlayerSnapshot snapshot = take(player.getUniqueId());
        if (snapshot == null) {
            return false;
        }
        snapshot.apply(player);
        return true;
    }

    /** Restores everything but the location, and forgets the snapshot. */
    public boolean restoreInPlace(Player player) {
        PlayerSnapshot snapshot = take(player.getUniqueId());
        if (snapshot == null) {
            return false;
        }
        snapshot.applyState(player);
        return true;
    }

    public void forget(UUID id) {
        take(id);
    }

    private PlayerSnapshot take(UUID id) {
        PlayerSnapshot snapshot = snapshots.remove(id);
        if (snapshot != null) {
            fileFor(id).delete();
        }
        return snapshot;
    }

    private File fileFor(UUID id) {
        return new File(folder, id + ".yml");
    }

    private void write(UUID id, PlayerSnapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.save(yaml);
        try {
            folder.mkdirs();
            yaml.save(fileFor(id));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save the snapshot of " + id, exception);
        }
    }

    /**
     * Reads the snapshots saved by an earlier run. Called once the server has finished
     * starting, because a snapshot can point into a world another plugin loads.
     */
    public void loadSaved() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            try {
                UUID id = UUID.fromString(name.substring(0, name.length() - ".yml".length()));
                PlayerSnapshot snapshot = PlayerSnapshot.load(YamlConfiguration.loadConfiguration(file));
                if (snapshot != null) {
                    snapshots.put(id, snapshot);
                } else {
                    // Kept on disk rather than deleted: it may only be its world that is missing.
                    plugin.getLogger().warning("Snapshot " + name + " is unreadable (world gone?), skipping it");
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read snapshot " + name, exception);
            }
        }
        if (!snapshots.isEmpty()) {
            plugin.getLogger().info("Loaded " + snapshots.size() + " saved player snapshot(s)");
        }
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
