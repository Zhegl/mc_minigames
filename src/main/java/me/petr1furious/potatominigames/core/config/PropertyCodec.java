package me.petr1furious.potatominigames.core.config;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

/** Converts a property between its typed value, YAML storage, and command-line strings. */
public interface PropertyCodec<T> {

    /** @throws IllegalArgumentException on bad input */
    T parse(String raw);

    String render(T value);

    default Object toYaml(T value) {
        return value;
    }

    @SuppressWarnings("unchecked")
    default T fromYaml(ConfigurationSection section, String key, T fallback) {
        Object stored = section.get(key);
        if (stored == null) {
            return fallback;
        }
        try {
            return (T) stored;
        } catch (ClassCastException exception) {
            return fallback;
        }
    }

    PropertyCodec<Integer> INT = new PropertyCodec<>() {
        public Integer parse(String raw) {
            return Integer.parseInt(raw);
        }

        public String render(Integer value) {
            return value.toString();
        }

        public Integer fromYaml(ConfigurationSection section, String key, Integer fallback) {
            return section.isInt(key) ? section.getInt(key) : fallback;
        }
    };

    PropertyCodec<Double> DOUBLE = new PropertyCodec<>() {
        public Double parse(String raw) {
            return Double.parseDouble(raw);
        }

        public String render(Double value) {
            return value.toString();
        }

        public Double fromYaml(ConfigurationSection section, String key, Double fallback) {
            return section.isDouble(key) || section.isInt(key) ? section.getDouble(key) : fallback;
        }
    };

    PropertyCodec<Boolean> BOOL = new PropertyCodec<>() {
        public Boolean parse(String raw) {
            String normalized = raw.toLowerCase(Locale.ROOT);
            if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on")) {
                return true;
            }
            if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off")) {
                return false;
            }
            throw new IllegalArgumentException("Expected true/false: " + raw);
        }

        public String render(Boolean value) {
            return value.toString();
        }

        public Boolean fromYaml(ConfigurationSection section, String key, Boolean fallback) {
            return section.isBoolean(key) ? section.getBoolean(key) : fallback;
        }
    };

    PropertyCodec<String> STRING = new PropertyCodec<>() {
        public String parse(String raw) {
            return raw;
        }

        public String render(String value) {
            return value;
        }

        public String fromYaml(ConfigurationSection section, String key, String fallback) {
            String stored = section.getString(key);
            return stored != null ? stored : fallback;
        }
    };

    PropertyCodec<Material> MATERIAL = new PropertyCodec<>() {
        public Material parse(String raw) {
            Material material = Material.matchMaterial(raw);
            if (material == null) {
                throw new IllegalArgumentException("Unknown material: " + raw);
            }
            return material;
        }

        public String render(Material value) {
            return value.getKey().getKey();
        }

        public Object toYaml(Material value) {
            return value.getKey().getKey();
        }

        public Material fromYaml(ConfigurationSection section, String key, Material fallback) {
            String stored = section.getString(key);
            if (stored == null) {
                return fallback;
            }
            Material material = Material.matchMaterial(stored);
            return material != null ? material : fallback;
        }
    };

    /** "x y z" (doubles), stored as a Bukkit Vector. */
    PropertyCodec<Vector> VECTOR = new PropertyCodec<>() {
        public Vector parse(String raw) {
            String[] parts = raw.trim().split("\\s+");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Expected 'x y z': " + raw);
            }
            return new Vector(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]));
        }

        public String render(Vector value) {
            return value.getX() + " " + value.getY() + " " + value.getZ();
        }

        public Vector fromYaml(ConfigurationSection section, String key, Vector fallback) {
            Vector stored = section.getVector(key);
            return stored != null ? stored : fallback;
        }
    };
}
