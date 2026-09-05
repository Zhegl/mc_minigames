package me.petr1furious.potatominigames.core.score;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persistent cross-round standings per game ({@code data/points.yml}).
 * Not shown in any HUD — viewed with the per-game {@code points} command.
 */
public final class PointsService {
    public record Entry(UUID playerId, String name, int points) {
    }

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public PointsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/points.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void add(String gameId, UUID playerId, int delta) {
        if (delta == 0) {
            return;
        }
        String path = gameId + "." + playerId;
        config.set(path, config.getInt(path, 0) + delta);
        save();
    }

    public List<Entry> standings(String gameId) {
        ConfigurationSection section = config.getConfigurationSection(gameId);
        if (section == null) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String name = Bukkit.getOfflinePlayer(id).getName();
                entries.add(new Entry(id, name != null ? name : key, section.getInt(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        entries.sort((a, b) -> Integer.compare(b.points(), a.points()));
        return entries;
    }

    public void reset(String gameId) {
        config.set(gameId, null);
        save();
    }

    private void save() {
        try {
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save points: " + exception.getMessage());
        }
    }
}
