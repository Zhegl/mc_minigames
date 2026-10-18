package me.petr1furious.potatominigames.core.config;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

/** Typed config properties, settable with {@code config <path> <value>}. */
public final class PropertyBag {
    public enum SetResult {
        SUCCESS, UNKNOWN_PROPERTY, PARSE_ERROR
    }

    private final String id;
    private final Map<String, Property<?>> properties = new LinkedHashMap<>();

    public PropertyBag(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public <T> Property<T> define(String name, PropertyCodec<T> codec, T defaultValue) {
        Property<T> property = new Property<>(name, codec, defaultValue);
        properties.put(name, property);
        return property;
    }

    public List<String> names() {
        return List.copyOf(properties.keySet());
    }

    public boolean has(String name) {
        return properties.containsKey(name);
    }

    public Property<?> property(String name) {
        return properties.get(name);
    }

    Collection<Property<?>> properties() {
        return properties.values();
    }

    public SetResult set(String name, String raw) {
        Property<?> property = properties.get(name);
        if (property == null) {
            return SetResult.UNKNOWN_PROPERTY;
        }
        try {
            property.parseAndSet(raw);
            return SetResult.SUCCESS;
        } catch (RuntimeException exception) {
            return SetResult.PARSE_ERROR;
        }
    }

    public String render(String name) {
        Property<?> property = properties.get(name);
        return property == null ? null : property.render();
    }

    public void load(ConfigurationSection section) {
        for (Property<?> property : properties.values()) {
            property.load(section);
        }
    }
}
