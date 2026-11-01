package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;

/** Pin participants to their spawn, including spectator flight, without changing their view. */
final class CountdownFreeze {
    private record Frozen(Player player, Location anchor, float walkSpeed, float flySpeed) { }
    private final Map<UUID, Frozen> players = new HashMap<>();

    void freeze(Player player) {
        UUID id = player.getUniqueId();
        Frozen previous = players.get(id);
        players.put(id, new Frozen(player, player.getLocation().clone(),
                previous == null ? player.getWalkSpeed() : previous.walkSpeed(),
                previous == null ? player.getFlySpeed() : previous.flySpeed()));
        player.setWalkSpeed(0);
        player.setFlySpeed(0);
        stop(player);
    }
    boolean hold(PlayerMoveEvent event) {
        Frozen frozen = players.get(event.getPlayer().getUniqueId());
        if (frozen == null) return false;
        Location to = event.getTo(), anchor = frozen.anchor();
        if (!anchor.getWorld().equals(to.getWorld()) || anchor.getX() != to.getX()
                || anchor.getY() != to.getY() || anchor.getZ() != to.getZ()) {
            Location locked = anchor.clone();
            locked.setYaw(to.getYaw()); locked.setPitch(to.getPitch());
            event.setTo(locked);
            stop(event.getPlayer());
        }
        return true;
    }
    void onJump(PlayerJumpEvent event) {
        if (players.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    void release(UUID id) {
        Frozen frozen = players.remove(id);
        if (frozen == null) return;
        Player player = frozen.player();
        player.setWalkSpeed(frozen.walkSpeed());
        player.setFlySpeed(frozen.flySpeed());
        stop(player);
    }
    void releaseAll() { for (UUID id : List.copyOf(players.keySet())) release(id); }
    private static void stop(Player player) {
        if (player.getVelocity().lengthSquared() > 0) player.setVelocity(new Vector());
        player.setFallDistance(0);
    }
}
