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

/**
 * Everything about what pillars hands out: the pool of items, their weights, the
 * custom items and the item groups that make matching items arrive together.
 */
public final class ItemPool {
    private ItemPool() {
    }

    /** The item forms that need a potion type to be worth anything. */
    private static final Set<String> POTION_FORMS = Set.of("potion", "splash_potion", "lingering_potion",
            "tipped_arrow");

    /**
     * Every potion type that actually does something. Water, mundane, thick and awkward
     * potions have no effects at all, which makes them the same kind of filler as the
     * items dropped by the blacklist.
     */
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
                    // A type the API cannot describe is no use to hand out anyway.
                }
            }
            effectivePotionTypes = List.copyOf(types);
        }
        return effectivePotionTypes;
    }

    // --- pool items ---

    /**
     * One thing a player can be given: a plain item out of the registry, one that rolls
     * extra data as it is handed out (a potion with no type is just an empty bottle), or
     * a custom item from the configuration under a name of its own.
     */
    public record PoolItem(String id, int weight, Supplier<ItemStack> creator) {
        public ItemStack createItemStack() {
            return creator.get();
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /**
     * Picks an item, the heavier ones coming up proportionally more often. Weights are
     * applied here rather than by repeating entries, so a weight of 500 costs nothing.
     *
     * @param weight what an item weighs for whoever is being given it, which is where
     *               the item groups raise the items they are waiting for
     */
    public static PoolItem randomByWeight(List<PoolItem> items, ToIntFunction<PoolItem> weight) {
        long total = 0;
        for (PoolItem item : items) {
            total += weight.applyAsInt(item);
        }
        // Everything weighted down to nothing is somebody's mistake rather than a wish to
        // hand out no items at all, so the weights are ignored instead.
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

    // --- item groups ---

    /** How long an item group keeps raising the weight of an item it covers. */
    public enum GroupMode {
        /** Until the player has been given that item once, which is all a matching pair needs. */
        ONCE,
        /** For the rest of the game, which is what ammunition for a weapon wants. */
        REPEATING
    }

    /**
     * A set of items that only make sense together, like a happy ghast and a harness.
     * Being given one of the triggers makes every item the group covers far more likely
     * for that player, so the pieces of a pair arrive within a few item cycles of each
     * other. With no triggers of its own, every item in the group sets off the others.
     */
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

    /**
     * One player's progress through the item groups. A group only raises an item once
     * the player holds one of its triggers, and a {@link GroupMode#ONCE} group stops
     * for an item they have already been given.
     */
    public static final class GroupState {
        private final List<ItemGroup> groups;
        private final Set<ItemGroup> triggered = new HashSet<>();
        private final Set<String> received = new HashSet<>();

        public GroupState(List<ItemGroup> groups) {
            this.groups = groups;
        }

        /** Remembers an item the player was just given and sets off every group it triggers. */
        public void record(PoolItem item) {
            received.add(item.id());
            for (ItemGroup group : groups) {
                if (group.triggeredBy(item.id())) {
                    triggered.add(group);
                }
            }
        }

        /** A group never makes an item rarer than its configured weight. */
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

    // --- building the pool ---

    /** The item's id without the {@code minecraft:} namespace, which is how the configuration names items. */
    public static String itemId(ItemType type) {
        return Registry.ITEM.getKeyOrThrow(type).asMinimalString();
    }

    /**
     * Compiles a configured pattern, in which {@code *} stands for any part of an id. A
     * pattern without any {@code *} names one item, so one list can hold both whole
     * families like {@code *_banner_pattern} and one-offs like {@code command_block}.
     */
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

    /**
     * Reads a custom item, written exactly the way {@code /give} takes it. The command
     * parser does the work on purpose: it knows every component the running version has,
     * and says precisely what is wrong with a definition.
     */
    public static ItemStack parseCustomItem(String definition) throws Exception {
        StringReader reader = new StringReader(definition.trim());
        ItemStack stack = ArgumentTypes.itemStack().parse(reader);
        if (reader.canRead()) {
            throw new IllegalArgumentException("unexpected text after the item: '" + reader.getRemaining() + "'");
        }
        return stack;
    }

    /**
     * Builds the pool of items a game hands out.
     *
     * @param world the game's world, which decides whether experimental items exist at all
     */
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

        // The blacklist is not applied to these: defining a variant is saying you want it,
        // whatever the patterns say about the item it is built from.
        for (Map.Entry<String, String> custom : config.customItems().entrySet()) {
            String name = custom.getKey();
            // A name that is already an item's would make two entries answer to it.
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

        // A weight of zero means the same as a blacklisting, so such an item leaves the
        // pool entirely; left in it, a group could still raise it back above zero.
        pool.removeIf(item -> item.weight() <= 0);
        return List.copyOf(pool);
    }

    /**
     * The configured weight of an item, or 1 when nothing mentions it. When several
     * patterns match, the longest wins, so a {@code splash_potion: 1} keeps its weight
     * next to a {@code *potion: 25} however the lines are ordered.
     */
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

    /** Whether any item (custom ones included) answers to a pattern, for config warnings. */
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
