/*
 * Based on PillarPeril by MarcPG (https://github.com/MarcPG1905/PillarPeril, Apache-2.0),
 * via the fork at https://github.com/Petr1Furious/PillarPeril. Rewritten in Java for
 * Potato Minigames by Claude, and modified; see NOTICE.
 */
package me.petr1furious.potatominigames.games.pillars;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;

/** Items and arenas are read from the file at each game start; other values are properties. */
public final class PillarsConfig {
    static final String FILE = "games/pillars.yml";
    static final List<String> MODES = List.of("original", "chaos", "item-shuffle");

    private final ConfigRegistry configs;
    private final PropertyBag bag = new PropertyBag("pillars");
    public final Property<String> mode = bag.define("mode", PropertyCodec.STRING, "original");
    public final Property<Integer> startCountdown = bag.define("startCountdown", PropertyCodec.INT, 3);
    public final Property<Integer> reconnectGraceSeconds = bag.define("reconnectGraceSeconds", PropertyCodec.INT, 60);
    public final Property<Double> platformHeight = bag.define("platformHeight", PropertyCodec.DOUBLE, 200.0);
    public final Property<Material> platformMaterial =
            bag.define("platformMaterial", PropertyCodec.MATERIAL, Material.BEDROCK);
    public final Property<Double> maxFall = bag.define("maxFall", PropertyCodec.DOUBLE, 40.0);
    public final Property<Double> pillarDistance = bag.define("pillarDistance", PropertyCodec.DOUBLE, 8.0);
    public final Property<Boolean> enableDraws = bag.define("enableDraws", PropertyCodec.BOOL, true);
    public final Property<Boolean> soundEffects = bag.define("soundEffects", PropertyCodec.BOOL, true);
    public final Property<Integer> soundCooldownSeconds = bag.define("soundCooldownSeconds", PropertyCodec.INT, 3);
    public final Property<Boolean> itemSound = bag.define("itemSound", PropertyCodec.BOOL, true);

    private final Map<String, Mode> modes = new LinkedHashMap<>();

    /** Defaults match PillarPeril's production config. */
    public static final class Mode {
        public final String id;
        public final Property<Integer> cooldownSeconds;
        public final Property<String> timeLimit;
        public final Property<String> color;
        public final Property<Boolean> scoreboard;
        public final Property<Boolean> actionBar;
        public final Property<Boolean> bossBar;
        /** "circular" or "random". */
        public final Property<String> layout;

        Mode(String id, PropertyBag bag, int cooldown, String timeLimit, String color, boolean actionBar,
                boolean bossBar, String layout) {
            this.id = id;
            this.cooldownSeconds = bag.define("cooldownSeconds", PropertyCodec.INT, cooldown);
            this.timeLimit = bag.define("timeLimit", PropertyCodec.STRING, timeLimit);
            this.color = bag.define("color", PropertyCodec.STRING, color);
            this.scoreboard = bag.define("scoreboard", PropertyCodec.BOOL, true);
            this.actionBar = bag.define("actionBar", PropertyCodec.BOOL, actionBar);
            this.bossBar = bag.define("bossBar", PropertyCodec.BOOL, bossBar);
            this.layout = bag.define("layout", PropertyCodec.STRING, layout);
        }

        public long timeLimitSeconds() {
            Long seconds = parseDuration(timeLimit.get());
            return seconds != null && seconds > 0 ? seconds : 300;
        }

        public TextColor accentColor() {
            TextColor parsed = TextColor.fromHexString(color.get().trim());
            return parsed != null ? parsed : NamedTextColor.WHITE;
        }
    }

    public record Items(List<String> blacklist, List<String> blacklistPatterns, List<String> blacklistExceptions,
            Map<String, Integer> weights, Map<String, String> customItems, List<ItemPool.ItemGroup> groups) {
    }

    public PillarsConfig(ConfigRegistry configs) {
        this.configs = configs;
        configs.register(bag, FILE, "");
        addMode("original", 5, "5min", "#FFFFFF", false, true, "circular");
        addMode("chaos", 3, "8min", "#FF5555", false, true, "random");
        addMode("item-shuffle", 10, "5min", "#A3FF54", true, false, "circular");
    }

    private void addMode(String id, int cooldown, String timeLimit, String color, boolean actionBar,
            boolean bossBar, String layout) {
        PropertyBag modeBag = new PropertyBag("pillars.modes." + id);
        modes.put(id, new Mode(id, modeBag, cooldown, timeLimit, color, actionBar, bossBar, layout));
        configs.register(modeBag, FILE, "modes." + id);
    }

    public void setDefaultMode(String id) {
        configs.set("pillars", "mode", id);
    }

    public Mode mode(String id) {
        Mode found = modes.get(id);
        return found != null ? found : modes.get("original");
    }

    public double deathHeight() {
        return platformHeight.get() - maxFall.get();
    }

    private YamlConfiguration file() {
        return configs.file(FILE);
    }

    public void reload() {
        configs.reloadFile(FILE);
    }

    public Items items(Logger log) {
        ConfigurationSection section = file().getConfigurationSection("items");
        if (section == null) {
            section = new YamlConfiguration();
        }
        List<String> blacklist = new ArrayList<>();
        for (String id : section.getStringList("blacklist")) {
            blacklist.add(id.trim().toLowerCase(Locale.ROOT).replace("minecraft:", ""));
        }

        Map<String, Integer> weights = new LinkedHashMap<>();
        ConfigurationSection weightSection = section.getConfigurationSection("weights");
        if (weightSection != null) {
            for (String pattern : weightSection.getKeys(false)) {
                Integer weight = weightOf(weightSection.get(pattern));
                if (weight == null) {
                    log.warning("pillars: invalid weight '" + weightSection.get(pattern) + "' for '" + pattern + "'");
                } else {
                    weights.put(pattern, weight);
                }
            }
        }

        Map<String, String> custom = new LinkedHashMap<>();
        ConfigurationSection customSection = section.getConfigurationSection("custom");
        if (customSection != null) {
            for (String name : customSection.getKeys(false)) {
                String definition = customSection.getString(name, "");
                if (!definition.isBlank()) {
                    custom.put(name.trim().toLowerCase(Locale.ROOT), definition);
                }
            }
        }

        List<ItemPool.ItemGroup> groups = new ArrayList<>();
        ConfigurationSection groupSection = section.getConfigurationSection("groups");
        if (groupSection != null) {
            for (String name : groupSection.getKeys(false)) {
                ConfigurationSection group = groupSection.getConfigurationSection(name);
                if (group == null) {
                    continue;
                }
                List<String> items = group.getStringList("items").stream().filter(s -> !s.isBlank()).toList();
                if (items.isEmpty()) {
                    log.warning("pillars: item group '" + name + "' has no items and does nothing");
                    continue;
                }
                List<String> triggers = group.getStringList("triggers").stream().filter(s -> !s.isBlank()).toList();
                Integer weight = weightOf(group.get("weight"));
                boolean repeating = group.getString("mode", "").trim().equalsIgnoreCase("repeating");
                groups.add(new ItemPool.ItemGroup(name, weight != null ? weight : 250,
                        repeating ? ItemPool.GroupMode.REPEATING : ItemPool.GroupMode.ONCE,
                        // No triggers: every item in the group triggers it.
                        (triggers.isEmpty() ? items : triggers).stream().map(ItemPool::wildcard).toList(),
                        items.stream().map(ItemPool::wildcard).toList()));
            }
        }

        return new Items(blacklist, section.getStringList("blacklist-patterns"),
                section.getStringList("blacklist-exceptions"), weights, custom, groups);
    }

    /** Non-negative integer, else null. */
    private static Integer weightOf(Object value) {
        Integer weight = null;
        if (value instanceof Number number) {
            weight = number.intValue();
        } else if (value != null) {
            try {
                weight = Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return weight != null && weight >= 0 ? weight : null;
    }

    public List<PillarsArena> arenas() {
        List<PillarsArena> arenas = new ArrayList<>();
        ConfigurationSection section = file().getConfigurationSection("arenas");
        if (section == null) {
            return arenas;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection arena = section.getConfigurationSection(name);
            if (arena == null) {
                continue;
            }
            Map<String, Object> gameRules = new LinkedHashMap<>();
            ConfigurationSection rules = arena.getConfigurationSection("gamerules");
            if (rules != null) {
                for (String rule : rules.getKeys(false)) {
                    gameRules.put(rule, rules.get(rule));
                }
            }
            arenas.add(new PillarsArena(name, arena.getString("template", PillarsArena.templateFor(name)),
                    arena.getDouble("x"), arena.getDouble("z"), PillarsArena.parseTimeOfDay(arena.getString("time")),
                    gameRules));
        }
        return arenas;
    }

    /** "30s", "5min", "1h30min", "90" (seconds). */
    public static Long parseDuration(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return null;
        }
        if (text.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(text);
        }
        var matcher = java.util.regex.Pattern.compile("(\\d+)\\s*(h|min|m|s)").matcher(text);
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                return null;
            }
            long amount = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "h" -> amount * 3600;
                case "min", "m" -> amount * 60;
                default -> amount;
            };
            end = matcher.end();
        }
        return end == text.length() ? total : null;
    }
}
