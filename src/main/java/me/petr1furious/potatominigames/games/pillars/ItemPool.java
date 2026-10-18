/*
 * Based on PillarPeril by MarcPG (https://github.com/MarcPG1905/PillarPeril, Apache-2.0),
 * via the fork at https://github.com/Petr1Furious/PillarPeril. Rewritten in Java for
 * Potato Minigames by Claude, and modified; see NOTICE.
 */
package me.petr1furious.potatominigames.games.pillars;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import com.mojang.brigadier.StringReader;

import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

public final class ItemPool {
    private ItemPool() {
    }

    private static final Set<String> POTION_FORMS = Set.of("potion", "splash_potion", "lingering_potion",
            "tipped_arrow");

    /** Potion types with effects; water/mundane/thick/awkward are filler. */
    private static List<PotionType> effectivePotionTypes;

    private static List<PotionType> effectivePotionTypes() {
        if (effectivePotionTypes == null) {
            List<PotionType> types = new ArrayList<>();
            for (PotionType type : Registry.POTION) {
                try {
                    if (!type.getPotionEffects().isEmpty()) {
                        types.add(type);
                    }
                } catch (RuntimeException ignored) {
                }
            }
            effectivePotionTypes = List.copyOf(types);
        }
        return effectivePotionTypes;
    }

    public record PoolItem(String id, int weight, Supplier<ItemStack> creator) {
        public ItemStack createItemStack() {
            return creator.get();
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** @param weight per-player weight, including item group boosts */
    public static PoolItem randomByWeight(List<PoolItem> items, ToIntFunction<PoolItem> weight) {
        long total = 0;
        for (PoolItem item : items) {
            total += weight.applyAsInt(item);
        }
        // All-zero weights are a config mistake; fall back to uniform.
        if (total <= 0) {
            return items.get(ThreadLocalRandom.current().nextInt(items.size()));
        }
        long roll = ThreadLocalRandom.current().nextLong(total);
        for (PoolItem item : items) {
            roll -= weight.applyAsInt(item);
            if (roll < 0) {
                return item;
            }
        }
        return items.getLast();
    }

    /** How long a group keeps boosting an item. */
    public enum GroupMode {
        /** Until the player has received it once. */
        ONCE,
        /** For the rest of the game (e.g. ammunition). */
        REPEATING
    }

    /** Items that belong together (e.g. happy ghast + harness); receiving a trigger boosts the rest. */
    public record ItemGroup(String name, int weight, GroupMode mode, List<Pattern> triggers,
            List<Pattern> items) {

        public boolean triggeredBy(String id) {
            return triggers.stream().anyMatch(pattern -> pattern.matcher(id).matches());
        }

        public boolean boosts(String id) {
            return items.stream().anyMatch(pattern -> pattern.matcher(id).matches());
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Per-player group progress. */
    public static final class GroupState {
        private final List<ItemGroup> groups;
        private final Set<ItemGroup> triggered = new HashSet<>();
        private final Set<String> received = new HashSet<>();

        public GroupState(List<ItemGroup> groups) {
            this.groups = groups;
        }

        public void record(PoolItem item) {
            received.add(item.id());
            for (ItemGroup group : groups) {
                if (group.triggeredBy(item.id())) {
                    triggered.add(group);
                }
            }
        }

        /** Never lowers an item below its configured weight. */
        public int weightOf(PoolItem item) {
            int weight = item.weight();
            for (ItemGroup group : triggered) {
                boolean active = group.boosts(item.id())
                        && (group.mode() == GroupMode.REPEATING || !received.contains(item.id()));
                if (active) {
                    weight = Math.max(weight, group.weight());
                }
            }
            return weight;
        }
    }

    /** Without the {@code minecraft:} namespace. */
    public static String itemId(ItemType type) {
        return Registry.ITEM.getKeyOrThrow(type).asMinimalString();
    }

    /** {@code *} matches any substring; no {@code *} means an exact id. */
    public static Pattern wildcard(String pattern) {
        String[] parts = pattern.trim().toLowerCase(java.util.Locale.ROOT).split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex.toString());
    }

    /** Parses {@code /give} syntax using the command parser, for full component support and clear errors. */
    public static ItemStack parseCustomItem(String definition) throws Exception {
        StringReader reader = new StringReader(definition.trim());
        ItemStack stack = ArgumentTypes.itemStack().parse(reader);
        if (reader.canRead()) {
            throw new IllegalArgumentException("unexpected text after the item: '" + reader.getRemaining() + "'");
        }
        return stack;
    }

    /** @param world decides whether experimental items are enabled */
    public static List<PoolItem> build(World world, PillarsConfig.Items config) {
        List<Pattern> patterns = config.blacklistPatterns().stream().map(ItemPool::wildcard).toList();
        Set<String> exceptions = new HashSet<>();
        for (String exception : config.blacklistExceptions()) {
            exceptions.add(exception.trim().toLowerCase(java.util.Locale.ROOT));
        }
        Map<Pattern, Integer> weights = new LinkedHashMap<>();
        config.weights().forEach((pattern, weight) -> weights.put(wildcard(pattern), weight));
        Set<String> blacklist = new HashSet<>(config.blacklist());

        List<PoolItem> pool = new ArrayList<>();
        Set<String> registryIds = new HashSet<>();
        for (ItemType type : Registry.ITEM) {
            String id = itemId(type);
            registryIds.add(id);
            if (blacklist.contains(id) || !world.isEnabled(type)) {
                continue;
            }
            if (!exceptions.contains(id) && patterns.stream().anyMatch(pattern -> pattern.matcher(id).matches())) {
                continue;
            }
            pool.add(poolItem(type, id, weightOf(id, weights)));
        }

        // Custom items bypass the blacklist.
        for (Map.Entry<String, String> custom : config.customItems().entrySet()) {
            String name = custom.getKey();
            // Skip names that clash with a registry item.
            if (registryIds.contains(name)) {
                continue;
            }
            ItemStack template;
            try {
                template = parseCustomItem(custom.getValue());
            } catch (Exception exception) {
                continue;
            }
            pool.add(new PoolItem(name, weightOf(name, weights), template::clone));
        }

        // Weight 0 means blacklisted; removed so a group cannot boost it back.
        pool.removeIf(item -> item.weight() <= 0);
        return List.copyOf(pool);
    }

    /** Defaults to 1; the longest matching pattern wins, regardless of order. */
    private static int weightOf(String id, Map<Pattern, Integer> weights) {
        int weight = 1;
        int longest = -1;
        for (Map.Entry<Pattern, Integer> entry : weights.entrySet()) {
            String pattern = entry.getKey().pattern();
            if (entry.getKey().matcher(id).matches() && pattern.length() > longest) {
                longest = pattern.length();
                weight = entry.getValue();
            }
        }
        return weight;
    }

    private static PoolItem poolItem(ItemType type, String id, int weight) {
        if (!POTION_FORMS.contains(id) || effectivePotionTypes().isEmpty()) {
            return new PoolItem(id, weight, type::createItemStack);
        }
        return new PoolItem(id, weight, () -> {
            ItemStack stack = type.createItemStack();
            List<PotionType> types = effectivePotionTypes();
            stack.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(
                    types.get(ThreadLocalRandom.current().nextInt(types.size()))));
            return stack;
        });
    }

    /** For config warnings. */
    public static boolean matchesAnything(String pattern, Collection<String> customNames) {
        Pattern regex = wildcard(pattern);
        for (ItemType type : Registry.ITEM) {
            if (regex.matcher(itemId(type)).matches()) {
                return true;
            }
        }
        return customNames.stream().anyMatch(name -> regex.matcher(name).matches());
    }
}
