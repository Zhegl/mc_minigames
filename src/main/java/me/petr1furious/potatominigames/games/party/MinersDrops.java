package me.petr1furious.potatominigames.games.party;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Item;

/** Bound abandoned loot without scanning the world or removing inventory items. */
final class MinersDrops {
    static final int LIMIT = 256;
    private final Map<UUID, Item> items = new LinkedHashMap<>();
    void track(Item item) {
        prune();
        items.put(item.getUniqueId(), item);
        while (items.size() > LIMIT) {
            var oldest = items.entrySet().iterator();
            Item removed = oldest.next().getValue(); oldest.remove();
            if (removed.isValid()) removed.remove();
        }
    }
    void prune() { items.values().removeIf(item -> !item.isValid()); }
    void clear() { for (Item item : items.values()) if (item.isValid()) item.remove(); items.clear(); }
}
