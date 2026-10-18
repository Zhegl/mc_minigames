package me.petr1furious.potatominigames.core.config;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.configuration.ConfigurationSection;

public final class Property<T> {
    private final String name;
    private final PropertyCodec<T> codec;
    private final T defaultValue;
    private T value;
    private String type;
    private Supplier<List<String>> choices;
    private boolean strict;
    private Predicate<T> check;

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

    /** Label shown in command hints, e.g. "duration like 5min"; defaults to the codec's. */
    public Property<T> type(String label) {
        this.type = label;
        return this;
    }

    /** Values suggested on tab, without restricting what can be set. */
    public Property<T> suggest(Supplier<List<String>> values) {
        this.choices = values;
        this.strict = false;
        return this;
    }

    /** The only values that can be set by command. */
    public Property<T> oneOf(Supplier<List<String>> values) {
        this.choices = values;
        this.strict = true;
        return this;
    }

    /** Rejects parsed values failing the check. */
    public Property<T> validate(Predicate<T> check) {
        this.check = check;
        return this;
    }

    public String typeLabel() {
        if (type != null) {
            return type;
        }
        return strict ? "one of " + String.join("|", choices.get()) : codec.typeName();
    }

    /** Declared choices, else the codec's (booleans, materials). */
    public List<String> choices() {
        return choices != null ? choices.get() : codec.choices();
    }

    public String renderDefault() {
        return defaultValue == null ? "<unset>" : codec.render(defaultValue);
    }

    PropertyCodec<T> codec() {
        return codec;
    }

    T defaultValue() {
        return defaultValue;
    }

    /** @throws IllegalArgumentException with a user-facing reason */
    T parse(String raw) {
        if (strict) {
            List<String> allowed = choices.get();
            String match = allowed.stream().filter(raw::equalsIgnoreCase).findFirst().orElse(null);
            if (match == null) {
                throw new IllegalArgumentException("options: " + String.join(", ", allowed));
            }
            raw = match;
        }
        T parsed;
        try {
            parsed = codec.parse(raw);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("expected " + typeLabel());
        }
        if (check != null && !check.test(parsed)) {
            throw new IllegalArgumentException("expected " + typeLabel());
        }
        return parsed;
    }

    void parseAndSet(String raw) {
        value = parse(raw);
    }

    Object toYaml(String raw) {
        return codec.toYaml(parse(raw));
    }

    String render() {
        return value == null ? "<unset>" : codec.render(value);
    }

    void reset() {
        value = defaultValue;
    }

    void load(ConfigurationSection section) {
        value = codec.fromYaml(section, name, defaultValue);
    }

    void save(ConfigurationSection section) {
        section.set(name, value == null ? null : codec.toYaml(value));
    }
}
