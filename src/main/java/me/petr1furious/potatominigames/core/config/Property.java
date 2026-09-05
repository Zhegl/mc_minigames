package me.petr1furious.potatominigames.core.config;

public final class Property<T> {
    private final String name;
    private final PropertyCodec<T> codec;
    private final T defaultValue;
    private T value;

    Property(String name, PropertyCodec<T> codec, T defaultValue) {
        this.name = name;
        this.codec = codec;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String name() {
        return name;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    PropertyCodec<T> codec() {
        return codec;
    }

    T defaultValue() {
        return defaultValue;
    }

    void parseAndSet(String raw) {
        value = codec.parse(raw);
    }

    String render() {
        return value == null ? "<unset>" : codec.render(value);
    }

    void load(org.bukkit.configuration.ConfigurationSection section) {
        value = codec.fromYaml(section, name, defaultValue);
    }

    void save(org.bukkit.configuration.ConfigurationSection section) {
        section.set(name, value == null ? null : codec.toYaml(value));
    }
}
