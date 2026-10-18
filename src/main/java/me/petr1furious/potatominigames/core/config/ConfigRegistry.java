package me.petr1furious.potatominigames.core.config;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Binds {@link PropertyBag}s to YAML files; saves when a property is set by command. */
public final class ConfigRegistry {
    private record Binding(PropertyBag bag, String fileName, String sectionPath) {
    }

    private final JavaPlugin plugin;
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private final Map<String, YamlConfiguration> files = new LinkedHashMap<>();

    public ConfigRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Also loads the bag immediately. */
    public void register(PropertyBag bag, String fileName, String sectionPath) {
        bindings.put(bag.id(), new Binding(bag, fileName, sectionPath));
        loadBag(bindings.get(bag.id()));
    }

    public List<String> bagIds() {
        return List.copyOf(bindings.keySet());
    }

    public PropertyBag bag(String id) {
        Binding binding = bindings.get(id);
        return binding == null ? null : binding.bag();
    }

    public PropertyBag.SetResult set(String bagId, String property, String raw) {
        Binding binding = bindings.get(bagId);
        if (binding == null) {
            return PropertyBag.SetResult.UNKNOWN_PROPERTY;
        }
        PropertyBag.SetResult result = binding.bag().set(property, raw);
        if (result == PropertyBag.SetResult.SUCCESS) {
            saveBag(binding);
        }
        return result;
    }

    public void reloadAll() {
        files.clear();
        for (Binding binding : bindings.values()) {
            loadBag(binding);
        }
    }

    public void reloadFile(String fileName) {
        files.remove(fileName);
        for (Binding binding : bindings.values()) {
            if (binding.fileName().equals(fileName)) {
                loadBag(binding);
            }
        }
    }

    /** For non-PropertyBag values in the same file. */
    public YamlConfiguration file(String fileName) {
        return files.computeIfAbsent(fileName, this::loadFile);
    }

    public void saveFile(String fileName) {
        YamlConfiguration config = files.get(fileName);
        if (config == null) {
            return;
        }
        try {
            File target = new File(plugin.getDataFolder(), fileName);
            target.getParentFile().mkdirs();
            config.save(target);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save " + fileName + ": " + exception.getMessage());
        }
    }

    private YamlConfiguration loadFile(String fileName) {
        File target = new File(plugin.getDataFolder(), fileName);
        if (!target.exists() && plugin.getResource(fileName) != null) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(target);
    }

    private void loadBag(Binding binding) {
        YamlConfiguration config = file(binding.fileName());
        ConfigurationSection section = binding.sectionPath().isEmpty() ? config
                : config.getConfigurationSection(binding.sectionPath());
        if (section == null) {
            section = config.createSection(binding.sectionPath());
        }
        binding.bag().load(section);
    }

    private void saveBag(Binding binding) {
        YamlConfiguration config = file(binding.fileName());
        ConfigurationSection section = binding.sectionPath().isEmpty() ? config
                : config.getConfigurationSection(binding.sectionPath());
        if (section == null) {
            section = config.createSection(binding.sectionPath());
        }
        binding.bag().save(section);
        saveFile(binding.fileName());
    }
}
