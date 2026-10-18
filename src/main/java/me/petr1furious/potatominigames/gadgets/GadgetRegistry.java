package me.petr1furious.potatominigames.gadgets;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import me.petr1furious.potatominigames.core.world.Locations;

/** Dispatches gadget events; skips gadgets whose {@code usableBy(player)} is false. */
public final class GadgetRegistry implements Listener {
    private final Map<String, Gadget> gadgets = new LinkedHashMap<>();

    public void register(Gadget gadget) {
        gadgets.put(gadget.id(), gadget);
    }

    public Gadget get(String id) {
        return gadgets.get(id);
    }

    public List<String> ids() {
        return List.copyOf(gadgets.keySet());
    }

    public List<Gadget> all() {
        return List.copyOf(gadgets.values());
    }

    public void registerEvents(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void endForPlayers(Collection<UUID> players) {
        for (Gadget gadget : gadgets.values()) {
            gadget.onGameEnd(players);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        for (Gadget gadget : gadgets.values()) {
            if (gadget.usableBy(event.getPlayer())) {
                gadget.onPlayerInteract(event);
            }
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        var bow = event.getBow();
        if (bow == null || !bow.getItemMeta().hasCustomModelDataComponent()) {
            return;
        }
        for (Gadget gadget : gadgets.values()) {
            if (event.getEntity() instanceof Player shooter && gadget.usableBy(shooter)) {
                gadget.onBowShoot(event);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        Entity shooter = Locations.getEntityShooter(projectile);
        Location location = computeImpactLocation(event, projectile);

        for (Gadget gadget : gadgets.values()) {
            if (gadget.handleProjectileImpact(projectile, location, shooter, event.getHitEntity())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Exact impact point via ray/plane intersection with the hit block face. */
    private static Location computeImpactLocation(ProjectileHitEvent event, Projectile projectile) {
        if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            Vector projectileStart = projectile.getLocation().toVector();
            Vector projectileDirection = projectile.getVelocity().normalize();

            Block hitBlock = event.getHitBlock();
            BlockFace hitFace = event.getHitBlockFace();
            Vector planeNormal = hitFace.getDirection();
            Vector planePoint = hitBlock.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5))
                    .add(planeNormal.multiply(0.501));

            double denominator = planeNormal.dot(projectileDirection);
            if (Math.abs(denominator) > 1e-6) {
                double t = planeNormal.dot(planePoint.subtract(projectileStart)) / denominator;
                Vector intersection = projectileStart.add(projectileDirection.multiply(t));
                return intersection.toLocation(projectile.getWorld());
            }
            return projectile.getLocation();
        }
        if (event.getHitEntity() != null) {
            return event.getHitEntity().getLocation();
        }
        return projectile.getLocation();
    }
}
