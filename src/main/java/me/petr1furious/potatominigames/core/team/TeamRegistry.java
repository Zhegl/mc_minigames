package me.petr1furious.potatominigames.core.team;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Standing team colours, per game and per player, kept in {@code data/teams.yml}:
 *
 * <pre>
 * bingo:
 *   &lt;uuid&gt;: red
 * war:
 *   &lt;uuid&gt;: blue
 * </pre>
 *
 * Assignments live outside any session, so colours can be arranged before a game
 * is started and survive restarts. The same player can hold a different colour in
 * each game.
 */
public final class TeamRegistry {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public TeamRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/teams.yml");
        reload();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    public TeamColor get(String gameId, UUID playerId) {
        ConfigurationSection section = config.getConfigurationSection(gameId);
        if (section == null) {
            return null;
        }
        return TeamColor.fromId(section.getString(playerId.toString()));
    }

    public void set(String gameId, UUID playerId, TeamColor color) {
        config.set(gameId + "." + playerId, color.id());
        save();
    }

    public void clear(String gameId, UUID playerId) {
        config.set(gameId + "." + playerId, null);
        save();
    }

    /** Every standing assignment for a game, in insertion order. */
    public Map<UUID, TeamColor> all(String gameId) {
        Map<UUID, TeamColor> result = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection(gameId);
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            TeamColor color = TeamColor.fromId(section.getString(key));
            if (color == null) {
                continue;
            }
            try {
                result.put(UUID.fromString(key), color);
            } catch (IllegalArgumentException ignored) {
                // not a UUID key; skip
            }
        }
        return result;
    }

    private void save() {
        try {
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save team assignments: " + exception.getMessage());
        }
    }
}
