package me.petr1furious.potatominigames.games.finals;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** A hit exposes current health for this life only, after damage cancellation has settled. */
final class FinalsHitHealth implements Listener {
    private final Predicate<Player> active;
    private final LongSupplier clock;
    private final Map<UUID, Long> until = new HashMap<>();
    private boolean started, closed;

    FinalsHitHealth(Predicate<Player> active, LongSupplier clock) {
        this.active = active;
        this.clock = clock;
    }

    void start(JavaPlugin plugin) {
        if (started || closed) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        started = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (closed || event.isCancelled() || event.getFinalDamage() <= 0
                || !(event.getEntity() instanceof Player player) || !active.test(player)) return;
        until.put(player.getUniqueId(), clock.getAsLong() + 100);
    }

    boolean visible(Player player) {
        Long deadline = until.get(player.getUniqueId());
        if (closed || deadline == null || !active.test(player)) return false;
        if (clock.getAsLong() < deadline) return true;
        until.remove(player.getUniqueId());
        return false;
    }

    void forget(UUID id) { until.remove(id); }

    void close() {
        closed = true;
        until.clear();
        if (started) HandlerList.unregisterAll(this);
    }
}
