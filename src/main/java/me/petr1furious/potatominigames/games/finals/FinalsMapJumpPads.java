package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Permanent map pads; targeted flights use velocity so normal block collision still applies. */
public final class FinalsMapJumpPads {
    public record Pad(Location origin, Location target) {
        public Pad { origin = origin.clone(); target = target == null ? null : target.clone(); }
        @Override public Location origin() { return origin.clone(); }
        @Override public Location target() { return target == null ? null : target.clone(); }
    }

    static final double UP_SPEED = FinalsGadgets.JUMP_PAD_SPEED * 3;
    private final List<Pad> pads;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Flight> flights = new HashMap<>();

    private static final class Flight {
        final Location start, target;
        final int duration;
        final long deadline;
        Location last;
        int step, stalled;
        Flight(Location start, Location target, long now) {
            this.start = start.clone(); this.target = target.clone(); last = start.clone();
            duration = duration(start, target);
            deadline = now + duration * 4L + 100;
        }
    }

    FinalsMapJumpPads(FinalsMapArena arena) { pads = List.copyOf(arena.jumpPads()); }

    static double rise(Location start, Location target) {
        double horizontal = Math.hypot(target.getX() - start.getX(), target.getZ() - start.getZ());
        return Math.max(6, horizontal * .2 + Math.abs(target.getY() - start.getY()) * .25);
    }

    static int duration(Location start, Location target) {
        return Math.max(8, (int) Math.ceil((start.distance(target) + 4 * rise(start, target)) / 1.5));
    }

    static Location arc(Location start, Location target, double progress) {
        double t = Math.clamp(progress, 0, 1);
        return start.clone().add(target.toVector().subtract(start.toVector()).multiply(t))
                .add(0, 4 * rise(start, target) * t * (1 - t), 0);
    }

    void tick(Collection<Player> players, Predicate<Player> active, long now) {
        cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
        var present = new java.util.HashSet<UUID>();
        for (Player player : players) {
            UUID id = player.getUniqueId();
            if (!active.test(player)) { flights.remove(id); continue; }
            present.add(id);
            Flight flight = flights.get(id);
            if (flight != null) { advance(player, flight, now); continue; }
            if (cooldowns.containsKey(id)) continue;
            Location at = player.getLocation();
            for (Pad pad : pads) {
                Location origin = pad.origin();
                if (!at.getWorld().equals(origin.getWorld()) || Math.abs(at.getX() - origin.getX()) > .65
                        || Math.abs(at.getZ() - origin.getZ()) > .65
                        || at.getY() < origin.getY() - .1 || at.getY() > origin.getY() + .65) continue;
                player.setFallDistance(0);
                cooldowns.put(id, now + 20);
                if (pad.target() == null) player.setVelocity(new Vector(0, UP_SPEED, 0));
                else {
                    Flight launched = new Flight(at, pad.target(), now);
                    flights.put(id, launched);
                    advance(player, launched, now);
                }
                player.getWorld().spawnParticle(Particle.CLOUD, origin, 16, .4, .1, .4, .04);
                player.playSound(origin, "minecraft:block.slime_block.fall", .9f, 1.2f);
                break;
            }
        }
        flights.keySet().retainAll(present);
    }

    private void advance(Player player, Flight flight, long now) {
        Location at = player.getLocation();
        boolean sameWorld = at.getWorld().equals(flight.start.getWorld());
        if (!sameWorld || now >= flight.deadline || (flight.step > 0 && at.distanceSquared(flight.last) > 64)) {
            flights.remove(player.getUniqueId());
            return;
        }
        if (at.distanceSquared(flight.last) < .0025) flight.stalled++;
        else flight.stalled = 0;
        flight.last = at.clone();
        boolean landed = flight.step >= flight.duration && at.distanceSquared(flight.target) < .25;
        if (landed || flight.stalled >= 12) {
            flights.remove(player.getUniqueId());
            cooldowns.put(player.getUniqueId(), now + 20);
            player.setVelocity(new Vector());
            return;
        }
        Location waypoint = arc(flight.start, flight.target, (double) flight.step / flight.duration);
        if (at.distanceSquared(waypoint) < 2.25 && flight.step < flight.duration) flight.step++;
        waypoint = arc(flight.start, flight.target, (double) flight.step / flight.duration);
        Vector velocity = waypoint.subtract(at).toVector();
        if (velocity.lengthSquared() > 1.8 * 1.8) velocity.normalize().multiply(1.8);
        player.setFallDistance(0);
        player.setVelocity(velocity);
    }

    void clear() { flights.clear(); cooldowns.clear(); }
}
