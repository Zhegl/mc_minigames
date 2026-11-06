package me.petr1furious.potatominigames.games.pillars;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Player-owned fixed Fusion mixes; memory changes only after an atomic save succeeds. */
final class PillarsPresets {
    static final int LIMIT = 5;
    static final List<String> MODIFIERS = List.of("chaos", "shuffle", "tactical", "big_house", "party", "rave", "adhd", "perks");

    record Preset(UUID owner, String ownerName, String name, List<String> modifiers) {
        Preset {
            modifiers = List.copyOf(modifiers);
        }
    }

    private final Path file;
    private final List<String> reserved;
    private final Map<String, Preset> presets = new LinkedHashMap<>();

    PillarsPresets(Path file, List<String> reserved) throws IOException {
        this.file = file.toAbsolutePath();
        this.reserved = List.copyOf(reserved);
        if (!Files.exists(this.file)) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(this.file.toFile());
            ConfigurationSection section = yaml.getConfigurationSection("presets");
            if (section == null) {
                if (!yaml.getKeys(false).isEmpty()) {
                    throw new IllegalArgumentException("Expected a presets section.");
                }
                return;
            }
            for (String key : section.getKeys(false)) {
                String name = checkedName(key);
                if (!name.equals(key)) {
                    throw new IllegalArgumentException("Preset names must be lowercase: " + key);
                }
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    throw new IllegalArgumentException("Invalid preset: " + key);
                }
                UUID owner = UUID.fromString(entry.getString("owner", ""));
                List<?> raw = entry.getList("modifiers");
                if (raw == null || raw.stream().anyMatch(value -> !(value instanceof String))) {
                    throw new IllegalArgumentException("Invalid modifiers for " + key);
                }
                List<String> modifiers = checkedModifiers(raw.stream().map(String.class::cast).toList());
                if (count(owner) >= LIMIT) {
                    throw new IllegalArgumentException("More than " + LIMIT + " presets for " + owner);
                }
                presets.put(name, new Preset(owner, entry.getString("ownerName", owner.toString()), name, modifiers));
            }
        } catch (InvalidConfigurationException | IllegalArgumentException exception) {
            throw new IOException("Invalid Pillars presets in " + file + ": " + exception.getMessage(), exception);
        }
    }

    List<Preset> all() {
        return presets.values().stream().sorted(java.util.Comparator.comparing(Preset::name)).toList();
    }

    List<String> names() {
        return presets.keySet().stream().sorted().toList();
    }

    Preset find(String name) {
        return presets.get(name.toLowerCase(Locale.ROOT));
    }

    Preset save(UUID owner, String ownerName, String rawName, List<String> modifiers) throws IOException {
        String name = checkedName(rawName);
        Preset previous = presets.get(name);
        if (previous != null && !previous.owner().equals(owner)) {
            throw new IllegalArgumentException("That preset belongs to " + previous.ownerName() + ".");
        }
        if (previous == null && count(owner) >= LIMIT) {
            throw new IllegalArgumentException("You may save at most " + LIMIT + " presets. Delete one first.");
        }
        Preset preset = new Preset(owner, ownerName, name, checkedModifiers(modifiers));
        persist(name, preset);
        presets.put(name, preset);
        return preset;
    }

    void delete(UUID owner, String rawName) throws IOException {
        String name = checkedName(rawName);
        Preset preset = presets.get(name);
        if (preset == null) {
            throw new IllegalArgumentException("Unknown Pillars preset: " + name);
        }
        if (!preset.owner().equals(owner)) {
            throw new IllegalArgumentException("Only " + preset.ownerName() + " may delete that preset.");
        }
        persist(name, null);
        presets.remove(name);
    }

    private long count(UUID owner) {
        return presets.values().stream().filter(preset -> preset.owner().equals(owner)).count();
    }

    private String checkedName(String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9][a-z0-9_-]{0,23}")) {
            throw new IllegalArgumentException("Use 1-24 letters a-z, digits, _ or -; start with a letter or digit.");
        }
        if (reserved.contains(name)) {
            throw new IllegalArgumentException("That name is a built-in Pillars mode: " + name);
        }
        return name;
    }

    private static List<String> checkedModifiers(List<String> raw) {
        var selected = new LinkedHashSet<String>();
        for (String value : raw) {
            String modifier = value.toLowerCase(Locale.ROOT);
            if (!MODIFIERS.contains(modifier)) {
                throw new IllegalArgumentException("Unknown modifier: " + value + ". Choose "
                        + String.join(", ", MODIFIERS) + ".");
            }
            if (!selected.add(modifier)) {
                throw new IllegalArgumentException("Duplicate modifier: " + modifier);
            }
        }
        if (selected.contains("party") && selected.contains("rave")) {
            throw new IllegalArgumentException("Choose party or rave, not both.");
        }
        return MODIFIERS.stream().filter(selected::contains).toList();
    }

    private void persist(String name, Preset replacement) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.createSection("presets");
        for (Preset preset : presets.values()) {
            if (!preset.name().equals(name)) {
                write(yaml, preset);
            }
        }
        if (replacement != null) {
            write(yaml, replacement);
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "pillars-presets-", ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void write(YamlConfiguration yaml, Preset preset) {
        String key = "presets." + preset.name();
        yaml.set(key + ".owner", preset.owner().toString());
        yaml.set(key + ".ownerName", preset.ownerName());
        yaml.set(key + ".modifiers", preset.modifiers());
    }
}
