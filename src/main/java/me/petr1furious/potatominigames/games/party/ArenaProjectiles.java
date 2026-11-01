package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;

/** Bound arrow lifetime and remove a shooter's projectiles without scanning the entire world. */
final class ArenaProjectiles {
    private record Shot(Entity entity, long firedAt) { }
    private final Map<UUID, List<Shot>> shots = new HashMap<>();

    void track(UUID shooter, Entity projectile, long tick) {
        shots.computeIfAbsent(shooter, ignored -> new ArrayList<>()).add(new Shot(projectile, tick));
    }
    void expire(long tick) {
        shots.values().removeIf(list -> {
            list.removeIf(shot -> {
                Entity entity = shot.entity();
                if (!entity.isValid()) return true;
                if (tick - shot.firedAt() >= 200 || entity instanceof AbstractArrow arrow && arrow.isInBlock()) {
                    entity.remove(); return true;
                }
                return false;
            });
            return list.isEmpty();
        });
    }
    void clear(UUID shooter) {
        List<Shot> owned = shots.remove(shooter);
        if (owned != null) for (Shot shot : owned) shot.entity().remove();
    }
    void clearAll() { for (UUID id : List.copyOf(shots.keySet())) clear(id); }
}
