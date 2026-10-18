package me.petr1furious.potatominigames.core.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Dotted-path view of one or more config files for the {@code config} commands: every scalar,
 * flat string list and declared property, minus each file's hidden prefixes.
 */
public final class ConfigEditor {
    public enum Kind {
        SECTION, VALUE, LIST
    }

    private record Mount(String prefix, String fileName) {
    }

    /** A resolved path; {@code key} is the path inside {@code fileName}. */
    public record Node(Kind kind, String path, String fileName, String key, Property<?> declared,
            Property<?> template, Object raw) {

        public boolean isDeclared() {
            return declared != null;
        }

        public String value() {
            if (declared != null) {
                return declared.render();
            }
            return raw == null ? "<unset>" : String.valueOf(raw);
        }

        public String type() {
            if (kind == Kind.LIST) {
                return "list";
            }
            if (declared != null) {
                return declared.typeLabel();
            }
            if (template != null) {
                return template.typeLabel();
            }
            return switch (raw) {
                case Integer ignored -> "int";
                case Long ignored -> "int";
                case Number ignored -> "number";
                case Boolean ignored -> "boolean";
                case null, default -> "text";
            };
        }

        /** Rendered code default; null for undeclared keys. */
        public String defaultValue() {
            return declared != null ? declared.renderDefault() : null;
        }

        public List<String> choices() {
            if (declared != null) {
                return declared.choices();
            }
            if (template != null) {
                return template.choices();
            }
            return raw instanceof Boolean ? PropertyCodec.BOOL.choices() : List.of();
        }

        public List<String> entries() {
            List<String> entries = new ArrayList<>();
            if (raw instanceof List<?> list) {
                list.forEach(entry -> entries.add(String.valueOf(entry)));
            }
            return entries;
        }
    }

    private final ConfigRegistry registry;
    private final List<Mount> mounts = new ArrayList<>();

    public ConfigEditor(ConfigRegistry registry) {
        this.registry = registry;
    }

    /** Shows {@code fileName} under {@code prefix} (one segment, or "" for the root). */
    public ConfigEditor mount(String prefix, String fileName) {
        mounts.add(new Mount(prefix, fileName));
        return this;
    }

    /** Null if unknown or hidden. */
    public Node resolve(String path) {
        if (path.isEmpty()) {
            return new Node(Kind.SECTION, "", null, "", null, null, null);
        }
        for (Mount mount : mounts) {
            if (!mount.prefix().isEmpty() && (path.equals(mount.prefix()) || path.startsWith(mount.prefix() + "."))) {
                String key = path.length() == mount.prefix().length() ? ""
                        : path.substring(mount.prefix().length() + 1);
                return resolve(mount.fileName(), key, path);
            }
        }
        Mount root = rootMount();
        return root == null ? null : resolve(root.fileName(), path, path);
    }

    public List<Node> children(Node section) {
        List<Node> result = new ArrayList<>();
        if (section.kind() != Kind.SECTION) {
            return result;
        }
        if (section.path().isEmpty()) {
            Mount root = rootMount();
            if (root != null) {
                result.addAll(children(root.fileName(), "", ""));
            }
            for (Mount mount : mounts) {
                if (!mount.prefix().isEmpty()) {
                    Node node = resolve(mount.fileName(), "", mount.prefix());
                    if (node != null) {
                        result.add(node);
                    }
                }
            }
            return result;
        }
        return children(section.fileName(), section.key(), section.path());
    }

    /** Declared: live via the codec. Undeclared: typed like the stored value, written, file reloaded. */
    public void set(Node node, String raw) {
        if (node.isDeclared()) {
            registry.setDeclared(node.fileName(), node.key(), raw);
        } else {
            registry.setRaw(node.fileName(), node.key(),
                    node.template() != null ? node.template().toYaml(raw) : parseLike(node.raw(), raw));
        }
    }

    public void reset(Node node) {
        if (!node.isDeclared()) {
            throw new IllegalArgumentException("not a declared setting, so it has no default");
        }
        registry.resetDeclared(node.fileName(), node.key());
    }

    /** @return the entry as stored */
    public String listAdd(Node node, String entry) {
        List<String> entries = node.entries();
        if (find(entries, entry) != null) {
            throw new IllegalArgumentException(entry + " is already listed");
        }
        entries.add(entry);
        registry.setRaw(node.fileName(), node.key(), entries);
        return entry;
    }

    /** @return the entry as it was stored */
    public String listRemove(Node node, String entry) {
        List<String> entries = node.entries();
        String found = find(entries, entry);
        if (found == null) {
            throw new IllegalArgumentException(entry + " is not listed"
                    + (entries.isEmpty() ? "" : " (entries: " + String.join(", ", entries) + ")"));
        }
        entries.remove(found);
        registry.setRaw(node.fileName(), node.key(), entries);
        return found;
    }

    private static String find(List<String> entries, String entry) {
        return entries.stream().filter(entry::equalsIgnoreCase).findFirst().orElse(null);
    }

    private Mount rootMount() {
        return mounts.stream().filter(mount -> mount.prefix().isEmpty()).findFirst().orElse(null);
    }

    private Node resolve(String fileName, String key, String path) {
        ConfigRegistry.FileSpec spec = registry.specOf(fileName);
        if (!key.isEmpty() && isHidden(spec, key)) {
            return null;
        }
        Map<String, Property<?>> declared = registry.declared(fileName);
        Property<?> property = declared.get(key);
        if (property != null) {
            return new Node(Kind.VALUE, path, fileName, key, property, null, null);
        }
        YamlConfiguration yaml = registry.file(fileName);
        Object raw = key.isEmpty() ? yaml : yaml.get(key);
        Property<?> template = templateOf(spec, key);
        if (raw instanceof ConfigurationSection) {
            Node node = new Node(Kind.SECTION, path, fileName, key, null, null, null);
            return key.isEmpty() || !children(fileName, key, path).isEmpty() ? node : null;
        }
        if (raw instanceof List<?> list) {
            return list.stream().allMatch(entry -> entry instanceof String)
                    ? new Node(Kind.LIST, path, fileName, key, null, null, list) : null;
        }
        if (raw == null) {
            boolean declaredBelow = declared.keySet().stream()
                    .anyMatch(name -> name.startsWith(key + ".") && !isHidden(spec, name));
            if (declaredBelow) {
                return new Node(Kind.SECTION, path, fileName, key, null, null, null);
            }
            // Only inside an existing section, so a typo cannot create one.
            int dot = key.lastIndexOf('.');
            boolean parentExists = dot < 0 || yaml.isConfigurationSection(key.substring(0, dot));
            return template != null && parentExists
                    ? new Node(Kind.VALUE, path, fileName, key, null, template, null) : null;
        }
        if (raw instanceof Number || raw instanceof Boolean || raw instanceof String) {
            return new Node(Kind.VALUE, path, fileName, key, null, template, raw);
        }
        return null;
    }

    private List<Node> children(String fileName, String key, String path) {
        Set<String> names = new LinkedHashSet<>();
        YamlConfiguration yaml = registry.file(fileName);
        ConfigurationSection section = key.isEmpty() ? yaml : yaml.getConfigurationSection(key);
        if (section != null) {
            names.addAll(section.getKeys(false));
        }
        String prefix = key.isEmpty() ? "" : key + ".";
        for (String name : registry.declared(fileName).keySet()) {
            if (name.startsWith(prefix)) {
                names.add(name.substring(prefix.length()).split("\\.")[0]);
            }
        }
        String[] segments = key.isEmpty() ? new String[0] : key.split("\\.");
        for (String pattern : registry.specOf(fileName).keys().keySet()) {
            String[] parts = pattern.split("\\.");
            if (parts.length > segments.length && !parts[segments.length].equals("*")
                    && matches(parts, segments, segments.length)) {
                names.add(parts[segments.length]);
            }
        }
        List<Node> result = new ArrayList<>();
        for (String name : names) {
            Node child = resolve(fileName, prefix + name, path.isEmpty() ? name : path + "." + name);
            if (child != null) {
                result.add(child);
            }
        }
        return result;
    }

    private static boolean isHidden(ConfigRegistry.FileSpec spec, String key) {
        return spec.hidden().stream().anyMatch(prefix -> key.equals(prefix) || key.startsWith(prefix + "."));
    }

    private static Property<?> templateOf(ConfigRegistry.FileSpec spec, String key) {
        String[] segments = key.split("\\.");
        for (var entry : spec.keys().entrySet()) {
            String[] parts = entry.getKey().split("\\.");
            if (parts.length == segments.length && matches(parts, segments, parts.length)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean matches(String[] pattern, String[] segments, int count) {
        for (int i = 0; i < count; i++) {
            if (!pattern[i].equals("*") && !pattern[i].equals(segments[i])) {
                return false;
            }
        }
        return true;
    }

    private static Object parseLike(Object current, String raw) {
        try {
            return switch (current) {
                case Integer ignored -> Integer.parseInt(raw);
                case Long ignored -> Long.parseLong(raw);
                case Number ignored -> Double.parseDouble(raw);
                case Boolean ignored -> PropertyCodec.BOOL.parse(raw);
                case null, default -> raw;
            };
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("expected " + (current instanceof Integer || current instanceof Long
                    ? "int" : current instanceof Number ? "number" : "true or false"));
        }
    }
}
