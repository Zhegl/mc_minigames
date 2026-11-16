package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/** Session-owned background music for players near the moving cashout body. */
final class FinalsCashoutMusic {
    private static final String[] TRACKS = {
        "minecraft:music_disc.pigstep", "minecraft:music_disc.lava_chicken"
    };
    private static final float VOLUME = .30f;
    private static final double RANGE_SQUARED = 32 * 32;
    // Vanilla sound attenuation is 16 blocks. A private source on the cashout bearing
    // preserves its direction while extending quiet playback to the configured 32 blocks.
    private static final double DISTANCE_SCALE = .45;
    // Vanilla discs attenuate to silence at 16 blocks. Keep the entity nearby and tracked,
    // but move its sound outside that distance so the client continues the same audio stream.
    private static final double MUTED_HEIGHT = 24;
    private final BiFunction<Player, Location, Entity> createEmitter;
    private final Map<UUID, Playback> listeners = new HashMap<>();
    private String track;
    private int nextTrack;

    private static final class Playback {
        final Player player;
        final Entity emitter;
        boolean started;

        Playback(Player player, Entity emitter) {
            this.player = player;
            this.emitter = emitter;
        }
    }

    FinalsCashoutMusic(JavaPlugin plugin) {
        this((player, point) -> createEmitter(plugin, player, point));
    }

    FinalsCashoutMusic(BiFunction<Player, Location, Entity> createEmitter) {
        this.createEmitter = createEmitter;
    }

    private static Entity createEmitter(JavaPlugin plugin, Player player, Location point) {
        TextDisplay emitter = point.getWorld().spawn(point, TextDisplay.class, display -> {
            display.setVisibleByDefault(false);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setViewRange(4f);
            // Client interpolation also moves the logical entity sound position. Smooth
            // movement between server packets; radius crossings fade over the same two ticks.
            display.setTeleportDuration(2);
            // Empty text is invisible. Do not mark the entity silent: the client rejects
            // entity-bound sounds whose source has that flag.
        });
        try {
            player.showEntity(plugin, emitter);
            return emitter;
        } catch (RuntimeException error) {
            emitter.remove();
            throw error;
        }
    }

    void update(Entity source, Collection<Player> audience) {
        if (source == null || !source.isValid()) {
            clear();
            return;
        }
        if (track == null) {
            // Both discs exceed even the maximum configured cashout duration (120 seconds).
            track = TRACKS[nextTrack];
            nextTrack = (nextTrack + 1) % TRACKS.length;
        }
        Location origin = source.getLocation();
        Set<UUID> present = new HashSet<>();
        for (Player player : audience) {
            if (!player.isOnline()
                    || !origin.getWorld().equals(player.getWorld())) continue;
            UUID id = player.getUniqueId();
            present.add(id);
            boolean nearby = player.getLocation().distanceSquared(origin) <= RANGE_SQUARED;
            Playback playback = listeners.get(id);
            if (playback != null && (playback.player != player || !playback.emitter.isValid())) {
                stop(playback);
                listeners.remove(id);
                playback = null;
            }
            Location point = player.getEyeLocation();
            point.setYaw(0);
            point.setPitch(0);
            if (nearby) {
                var direction = origin.clone().add(0, .5, 0).toVector().subtract(point.toVector());
                if (direction.lengthSquared() > 1e-8)
                    point.add(direction.normalize().multiply(player.getLocation().distance(origin) * DISTANCE_SCALE));
            } else point.add(0, MUTED_HEIGHT, 0);
            if (playback == null) {
                if (!nearby) continue;
                playback = new Playback(player, createEmitter.apply(player, point));
                listeners.put(id, playback);
            }
            // The private entity survives radius crossings. Moving it mutes/unmutes the
            // existing stream instead of issuing stop/play packets that restart the disc.
            if (!playback.emitter.getLocation().equals(point)) playback.emitter.teleport(point);
            if (!playback.started && nearby && playback.emitter.getTrackedBy().contains(player)) {
                player.playSound(playback.emitter, track, SoundCategory.RECORDS, VOLUME, 1f);
                playback.started = true;
            }
        }
        var iterator = listeners.entrySet().iterator();
        while (iterator.hasNext()) {
            var listener = iterator.next();
            if (!present.contains(listener.getKey())) {
                stop(listener.getValue());
                iterator.remove();
            }
        }
    }

    void remove(Player player) {
        Playback listener = listeners.remove(player.getUniqueId());
        if (listener != null) stop(listener);
    }

    void clear() {
        stopListeners();
        track = null;
    }

    private void stopListeners() {
        for (Playback listener : listeners.values()) stop(listener);
        listeners.clear();
    }

    private void stop(Playback playback) {
        // Stop only our current disc, leaving the steal siren and combat feedback intact.
        if (playback.started) playback.player.stopSound(track, SoundCategory.RECORDS);
        playback.emitter.remove();
    }
}
