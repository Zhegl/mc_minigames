package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;
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
    private final JavaPlugin plugin;
    private final Map<UUID, Player> viewers = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Flight> flights = new HashMap<>();
    private final Map<Integer, BlockDisplay> outlines = new HashMap<>();
    private boolean showing;

    private static final class Flight {
        final Location start, target;
        final int duration;
        final long deadline;
        final long started;
        Location last;
        int stalled;
        Vector velocity;
        Flight(Location start, Location target, long now) {
            this.start = start.clone(); this.target = target.clone(); last = start.clone();
            duration = duration(start, target);
            started = now;
            deadline = now + duration * 4L + 100;
        }
    }

    FinalsMapJumpPads(FinalsMapArena arena) { this(arena, null); }

    FinalsMapJumpPads(FinalsMapArena arena, JavaPlugin plugin) {
        pads = List.copyOf(arena.jumpPads());
        this.plugin = plugin;
    }

    void start() { showing = true; refreshOutlines(); }

    private void refreshOutlines() {
        for (int i = 0; i < pads.size(); i++) {
            if (pads.get(i).target() == null) continue;
            BlockDisplay previous = outlines.get(i);
            if (previous != null && previous.isValid()) continue;
            if (previous != null) previous.remove();
            outlines.remove(i);
            Location at = pads.get(i).origin().add(-.5, .01, -.5);
            if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
            outlines.put(i, at.getWorld().spawn(at, BlockDisplay.class, display -> {
                display.setVisibleByDefault(false);
                display.setPersistent(false); display.setGravity(false); display.setInvulnerable(true);
                display.setBlock(Material.LIME_STAINED_GLASS.createBlockData());
                display.setTransformationMatrix(new org.joml.Matrix4f().scale(1, .06f, 1));
                display.setGlowing(true); display.setGlowColorOverride(Color.AQUA);
                display.setBrightness(new Display.Brightness(15, 15)); display.setViewRange(4);
            }));
        }
    }

    static boolean glowVisible(Pad pad, Location viewer) {
        return pad.target() != null && pad.origin().getWorld().equals(viewer.getWorld())
                && pad.origin().distanceSquared(viewer) <= 16 * 16;
    }

    void updateGlows(Collection<Player> players) {
        if (!showing || plugin == null) return;
        var present = new java.util.HashSet<UUID>();
        for (Player player : players) {
            if (!player.isOnline()) continue;
            present.add(player.getUniqueId());
            viewers.put(player.getUniqueId(), player);
            Location location = player.getLocation();
            outlines.forEach((index, outline) -> {
                if (!outline.isValid()) return;
                boolean visible = glowVisible(pads.get(index), location);
                if (visible && !player.canSee(outline)) player.showEntity(plugin, outline);
                else if (!visible && player.canSee(outline)) player.hideEntity(plugin, outline);
            });
        }
        viewers.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) return false;
            if (entry.getValue().isOnline())
                outlines.values().forEach(outline -> entry.getValue().hideEntity(plugin, outline));
            return true;
        });
    }

    static double rise(Location start, Location target) {
        double horizontal = Math.hypot(target.getX() - start.getX(), target.getZ() - start.getZ());
        return Math.max(6, horizontal * .2 + Math.abs(target.getY() - start.getY()) * .25);
    }

    static int duration(Location start, Location target) {
        int original = Math.max(8, (int) Math.ceil((start.distance(target) + 4 * rise(start, target)) / 1.5));
        return (int) Math.ceil(original / 1.5);
    }

    static Location arc(Location start, Location target, double progress) {
        double t = Math.clamp(progress, 0, 1);
        return start.clone().add(target.toVector().subtract(start.toVector()).multiply(t))
                .add(0, 4 * rise(start, target) * t * (1 - t), 0);
    }

    void tick(Collection<Player> players, Predicate<Player> active, long now) {
        if (showing && now % 20 == 0) refreshOutlines();
        updateGlows(players);
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
                if (pad.target() == null) player.setVelocity(player.getVelocity().setY(UP_SPEED));
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
        if (!sameWorld || now >= flight.deadline || (now > flight.started && at.distanceSquared(flight.last) > 64)) {
            flights.remove(player.getUniqueId());
            return;
        }
        if (at.distanceSquared(flight.last) < .0025) flight.stalled++;
        else flight.stalled = 0;
        flight.last = at.clone();
        long elapsed = now - flight.started;
        boolean landed = elapsed >= flight.duration && at.distanceSquared(flight.target) < .25;
        if (landed || flight.stalled >= 12) {
            flights.remove(player.getUniqueId());
            cooldowns.put(player.getUniqueId(), now + 20);
            player.setVelocity(new Vector());
            return;
        }
        Location expected = arc(flight.start, flight.target, (double) elapsed / flight.duration);
        Location next = arc(flight.start, flight.target, (double) (elapsed + 1) / flight.duration);
        Vector correction = expected.toVector().subtract(at.toVector()).multiply(.12);
        if (correction.lengthSquared() > .35 * .35) correction.normalize().multiply(.35);
        Vector velocity = next.toVector().subtract(expected.toVector()).add(correction);
        if (velocity.lengthSquared() > 2.7 * 2.7) velocity.normalize().multiply(2.7);
        if (flight.velocity != null) {
            Vector change = velocity.clone().subtract(flight.velocity);
            if (change.lengthSquared() > .25 * .25) change.normalize().multiply(.25);
            velocity = flight.velocity.clone().add(change);
        }
        flight.velocity = velocity.clone();
        player.setFallDistance(0);
        player.setVelocity(velocity);
    }

    void clear() {
        flights.clear(); cooldowns.clear();
        showing = false; outlines.values().forEach(BlockDisplay::remove); outlines.clear();
        viewers.clear();
    }
}
