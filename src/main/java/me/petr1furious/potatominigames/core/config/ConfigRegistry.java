package me.petr1furious.potatominigames.core.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Binds {@link PropertyBag}s to YAML files; saves when a property is set by command. */
public final class ConfigRegistry {
    record Binding(PropertyBag bag, String fileName, String sectionPath) {
        String keyOf(Property<?> property) {
            return sectionPath.isEmpty() ? property.name() : sectionPath + "." + property.name();
        }
    }

    /** Command-editing rules for one file. */
    record FileSpec(List<String> hidden, Map<String, Property<?>> keys, List<Runnable> reloadHooks) {
    }

    private final JavaPlugin plugin;
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private final Map<String, YamlConfiguration> files = new LinkedHashMap<>();
    private final Map<String, FileSpec> specs = new LinkedHashMap<>();

    public ConfigRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Also loads the bag immediately. */
    public void register(PropertyBag bag, String fileName, String sectionPath) {
        bindings.put(bag.id(), new Binding(bag, fileName, sectionPath));
        loadBag(bindings.get(bag.id()));
    }

    /** Keys under these dotted prefixes are neither shown nor editable by {@code config}. */
    public void hide(String fileName, String... prefixes) {
        spec(fileName).hidden().addAll(List.of(prefixes));
    }

    /**
     * Types an undeclared key, so it can be set before it is in the file; '*' matches one segment.
     * Read by the game from the file, not live.
     */
    public <T> Property<T> key(String fileName, String pattern, PropertyCodec<T> codec) {
        Property<T> property = new Property<>(pattern, codec, null);
        spec(fileName).keys().put(pattern, property);
        return property;
    }

    /** Runs after the file is reloaded, e.g. to re-read sections that are not properties. */
    public void onReload(String fileName, Runnable hook) {
        spec(fileName).reloadHooks().add(hook);
    }

    public List<String> bagIds() {
        return List.copyOf(bindings.keySet());
    }

    public PropertyBag bag(String id) {
        Binding binding = bindings.get(id);
        return binding == null ? null : binding.bag();
    }

    /** The file a bag lives in, e.g. a game id's. */
    public String fileOf(String bagId) {
        Binding binding = bindings.get(bagId);
        return binding == null ? null : binding.fileName();
    }

    public PropertyBag.SetResult set(String bagId, String property, String raw) {
        Binding binding = bindings.get(bagId);
        if (binding == null) {
            return PropertyBag.SetResult.UNKNOWN_PROPERTY;
        }
        PropertyBag.SetResult result = binding.bag().set(property, raw);
        if (result == PropertyBag.SetResult.SUCCESS) {
            saveProperty(binding, binding.bag().property(property));
        }
        return result;
    }

    public void reloadAll() {
        files.clear();
        for (Binding binding : bindings.values()) {
            loadBag(binding);
        }
        for (String fileName : specs.keySet()) {
            runReloadHooks(fileName);
        }
    }

    public void reloadFile(String fileName) {
        files.remove(fileName);
        for (Binding binding : bindings.values()) {
            if (binding.fileName().equals(fileName)) {
                loadBag(binding);
            }
        }
        runReloadHooks(fileName);
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

    // For ConfigEditor.

    FileSpec specOf(String fileName) {
        FileSpec spec = specs.get(fileName);
        return spec != null ? spec : new FileSpec(List.of(), Map.of(), List.of());
    }

    /** Declared properties of a file by dotted key. */
    Map<String, Property<?>> declared(String fileName) {
        Map<String, Property<?>> result = new LinkedHashMap<>();
        for (Binding binding : bindings.values()) {
            if (binding.fileName().equals(fileName)) {
                for (Property<?> property : binding.bag().properties()) {
                    result.put(binding.keyOf(property), property);
                }
            }
        }
        return result;
    }

    /** Sets a declared property live and writes it; @throws IllegalArgumentException on a bad value */
    void setDeclared(String fileName, String key, String raw) {
        Binding binding = bindingOf(fileName, key);
        Property<?> property = propertyOf(binding, key);
        property.parseAndSet(raw);
        saveProperty(binding, property);
    }

    /** Back to the code default, with the key removed from the file. */
    void resetDeclared(String fileName, String key) {
        Binding binding = bindingOf(fileName, key);
        Property<?> property = propertyOf(binding, key);
        property.reset();
        section(binding).set(property.name(), null);
        saveFile(fileName);
    }

    /** Writes an undeclared key and reloads the file, as {@code reload} would. */
    void setRaw(String fileName, String key, Object value) {
        file(fileName).set(key, value);
        saveFile(fileName);
        reloadFile(fileName);
    }

    private Binding bindingOf(String fileName, String key) {
        for (Binding binding : bindings.values()) {
            if (binding.fileName().equals(fileName)) {
                for (Property<?> property : binding.bag().properties()) {
                    if (binding.keyOf(property).equals(key)) {
                        return binding;
                    }
                }
            }
        }
        throw new IllegalArgumentException("not a declared setting");
    }

    private static Property<?> propertyOf(Binding binding, String key) {
        String name = binding.sectionPath().isEmpty() ? key : key.substring(binding.sectionPath().length() + 1);
        return binding.bag().property(name);
    }

    private FileSpec spec(String fileName) {
        return specs.computeIfAbsent(fileName,
                name -> new FileSpec(new ArrayList<>(), new LinkedHashMap<>(), new ArrayList<>()));
    }

    private void runReloadHooks(String fileName) {
        for (Runnable hook : specOf(fileName).reloadHooks()) {
            try {
                hook.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Reloading " + fileName + ": " + exception.getMessage());
            }
        }
    }

    private YamlConfiguration loadFile(String fileName) {
        File target = new File(plugin.getDataFolder(), fileName);
        if (!target.exists() && plugin.getResource(fileName) != null) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(target);
    }

    private ConfigurationSection section(Binding binding) {
        YamlConfiguration config = file(binding.fileName());
        ConfigurationSection section = binding.sectionPath().isEmpty() ? config
                : config.getConfigurationSection(binding.sectionPath());
        return section != null ? section : config.createSection(binding.sectionPath());
    }

    private void loadBag(Binding binding) {
        binding.bag().load(section(binding));
    }

    private void saveProperty(Binding binding, Property<?> property) {
        property.save(section(binding));
        saveFile(binding.fileName());
    }
}
