package me.petr1furious.potatominigames.games.finals;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Private music emitters preserve track progress while listeners cross the cashout radius. */
public final class FinalsCashoutMusicCheck {
    private static final String PIGSTEP = "minecraft:music_disc.pigstep", LAVA = "minecraft:music_disc.lava_chicken";
    private static int checks;

    public static void main(String[] args) throws Exception {
        Harness f = new Harness();
        Viewer near = new Viewer(f.world, 0), boundary = new Viewer(f.world, 32), far = new Viewer(f.world, 32.001);
        Viewer other = new Viewer(world("elsewhere"), 0), offline = new Viewer(f.world, 0);
        offline.online = false;
        List<Player> audience = List.of(near.player, boundary.player, far.player, other.player, offline.player);
        f.music.update(null, audience);
        require(f.created.isEmpty(), "Inactive cashouts create no music emitters");
        f.update(audience);
        require(f.created.size() == 2 && near.plays.isEmpty() && boundary.plays.isEmpty(),
                "Only eligible listeners within inclusive 32 blocks get emitters, and playback waits for tracking");
        require(f.emitter(near).at.equals(audible(near, f.source))
                        && f.emitter(boundary).at.equals(audible(boundary, f.source)),
                "Audible emitters point toward the cashout with distance-scaled attenuation");
        f.track(near); f.track(boundary); f.update(audience);
        playback(near, f.emitter(near), PIGSTEP); playback(boundary, f.emitter(boundary), PIGSTEP);
        for (int tick = 0; tick < 20; tick++) f.update(audience);
        require(near.plays.size() == 1 && boundary.plays.size() == 1, "Ordinary ticks never restart the music");
        Body original = f.emitter(near);
        near.at.add(1, .5, 1); f.update(audience);
        require(original.at.equals(audible(near, f.source))
                        && near.plays.size() == 1 && near.stops.isEmpty(),
                "Walking and vertical movement preserve cashout direction without restarting the stream");
        near.at.setY(70); near.at.setZ(0);
        for (int visit = 0; visit < 3; visit++) {
            near.at.setX(32.001); f.update(audience);
            require(original.at.equals(near.eye().add(0, 24, 0)), "Outside 32 blocks the emitter moves beyond sound attenuation range");
            near.at.setX(32); f.update(audience);
            require(original.at.equals(audible(near, f.source)), "Re-entering the inclusive boundary restores the same directional emitter");
        }
        require(near.plays.size() == 1 && near.stops.isEmpty() && original.removals == 0,
                "Exiting and re-entering never stops, replays or replaces the current track");
        f.source.at.setX(-40); f.update(audience);
        require(original.at.equals(near.eye().add(0, 24, 0)), "Eligibility follows a cashout that moves or falls away");
        f.source = new Body(near.at.clone()); f.update(audience);
        require(f.emitter(near) == original && near.plays.size() == 1 && near.stops.isEmpty(),
                "Replacing the cashout body keeps the existing emitter and track position");

        original.valid = false; f.update(audience);
        Body recovered = f.emitter(near);
        require(recovered != original && near.plays.size() == 1, "A failed emitter is replaced and waits for client tracking");
        f.track(near); f.update(audience);
        require(near.plays.size() == 2 && boundary.plays.size() == 1 && boundary.stops.isEmpty(),
                "Emitter recovery restarts only the affected listener's current track");
        playback(near, recovered, PIGSTEP);

        f.update(List.of(near.player));
        require(f.emitter(boundary).removals == 1 && boundary.stops.size() == 1,
                "Leaving the allowed audience stops its exact track and removes its emitter");
        near.online = false; f.update(List.of(near.player));
        require(recovered.removals == 1, "Offline listeners release their emitter");
        near.online = true; f.update(List.of(near.player)); f.track(near); f.update(List.of(near.player));
        Body beforeWorldChange = f.emitter(near);
        near.at.setWorld(other.at.getWorld()); f.update(List.of(near.player));
        require(beforeWorldChange.removals == 1, "Changing worlds releases music even while the player stays in the audience");
        near.at.setWorld(f.world); f.update(List.of(near.player)); f.track(near); f.update(List.of(near.player));
        Body previousConnection = f.emitter(near);
        Viewer replacement = new Viewer(near.id, f.world, 32);
        int stopsBefore = near.stops.size();
        f.update(List.of(replacement.player));
        require(previousConnection.removals == 1 && near.stops.size() == stopsBefore + 1 && replacement.plays.isEmpty(),
                "A replacement player connection cleans up the old transport and waits for its own emitter tracking");
        f.track(replacement); f.update(List.of(replacement.player));
        playback(replacement, f.emitter(replacement), PIGSTEP);
        Body removed = f.emitter(replacement);
        f.music.remove(replacement.player);
        int explicitStops = replacement.stops.size(); f.music.remove(replacement.player);
        require(removed.removals == 1 && replacement.stops.size() == explicitStops, "Explicit remove is complete and idempotent");
        f.update(List.of(replacement.player)); f.track(replacement); f.update(List.of(replacement.player));
        Body cleared = f.emitter(replacement);
        f.music.clear(); int clearStops = replacement.stops.size();
        f.music.clear(); f.music.update(null, List.of(replacement.player));
        require(cleared.removals == 1 && replacement.stops.size() == clearStops, "Repeated clear/inactive updates do not repeat cleanup or consume tracks");
        f.update(List.of(replacement.player)); f.track(replacement); f.update(List.of(replacement.player));
        playback(replacement, f.emitter(replacement), LAVA);
        f.music.clear();
        System.out.println("FinalsCashoutMusicCheck: " + checks + " checks passed");
    }

    private static Location audible(Viewer viewer, Body source) {
        Location eye = viewer.eye();
        var direction = source.at.clone().add(0, .5, 0).toVector().subtract(eye.toVector());
        double distance = viewer.at.distance(source.at) * .45;
        return direction.lengthSquared() < 1e-8 ? eye : eye.add(direction.normalize().multiply(distance));
    }

    private static final class Harness {
        final World world = world("city");
        Body source = new Body(new Location(world, 0, 70, 0));
        final Map<UUID, Body> latest = new HashMap<>();
        final List<Body> created = new ArrayList<>();
        final FinalsCashoutMusic music;
        Harness() throws Exception {
            var constructor = FinalsCashoutMusic.class.getDeclaredConstructor(BiFunction.class);
            constructor.setAccessible(true);
            BiFunction<Player, Location, Entity> factory = (player, at) -> {
                Body body = new Body(at.clone()); latest.put(player.getUniqueId(), body); created.add(body); return body.entity;
            };
            music = (FinalsCashoutMusic) constructor.newInstance(factory);
        }
        void update(Collection<Player> audience) { music.update(source.entity, audience); }
        Body emitter(Viewer player) { return latest.get(player.id); }
        void track(Viewer player) { emitter(player).tracking.add(player.player); }
    }

    private static final class Body {
        final UUID id = UUID.randomUUID();
        final Set<Player> tracking = new HashSet<>();
        final Entity entity;
        Location at;
        boolean valid = true;
        int removals;
        Body(Location at) {
            this.at = at;
            entity = proxy(Entity.class, (self, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id; case "isValid" -> valid; case "isDead" -> !valid;
                case "getWorld" -> this.at.getWorld(); case "getLocation" -> this.at.clone();
                case "getTrackedBy" -> tracking;
                case "teleport" -> { this.at = ((Location) args[0]).clone(); yield true; }
                case "remove" -> { valid = false; removals++; yield null; }
                case "equals" -> self == args[0]; case "hashCode" -> id.hashCode();
                default -> throw new AssertionError("Unexpected emitter operation: " + method.getName());
            });
        }
    }

    private record Played(Entity source, String sound, SoundCategory category, float volume, float pitch) {}
    private static final class Viewer {
        final UUID id;
        final Player player;
        final List<Played> plays = new ArrayList<>();
        final List<String> stops = new ArrayList<>();
        final Location at;
        boolean online = true;
        Viewer(World world, double x) { this(UUID.randomUUID(), world, x); }
        Viewer(UUID id, World world, double x) {
            this.id = id; at = new Location(world, x, 70, 0);
            player = proxy(Player.class, (self, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id; case "isOnline" -> online;
                case "getWorld" -> at.getWorld(); case "getLocation" -> at.clone(); case "getEyeLocation" -> eye();
                case "equals" -> self == args[0]; case "hashCode" -> id.hashCode();
                case "playSound" -> {
                    require(args.length == 5 && args[0] instanceof Entity && args[1] instanceof String,
                            "Playback uses the entity sound API");
                    plays.add(new Played((Entity) args[0], (String) args[1], (SoundCategory) args[2], (float) args[3], (float) args[4])); yield null;
                }
                case "stopSound" -> {
                    require(args.length == 2 && args[1] == SoundCategory.RECORDS && (PIGSTEP.equals(args[0]) || LAVA.equals(args[0])),
                            "Cleanup stops only the exact music disc in RECORDS");
                    stops.add((String) args[0]); yield null;
                }
                default -> throw new AssertionError("Unexpected listener operation: " + method.getName());
            });
        }
        Location eye() { return at.clone().add(0, 1.62, 0); }
    }

    private static void playback(Viewer player, Body emitter, String track) {
        Played last = player.plays.getLast();
        require(last.source == emitter.entity && last.sound.equals(track) && last.category == SoundCategory.RECORDS
                && Math.abs(last.volume - .30f) < 1e-6 && last.pitch == 1f,
                "Private emitter plays the current disc at 30% volume and normal pitch");
    }
    private static World world(String name) {
        return proxy(World.class, (self, method, args) -> switch (method.getName()) {
            case "getName", "toString" -> name; case "equals" -> self == args[0]; case "hashCode" -> System.identityHashCode(self);
            default -> throw new AssertionError("Unexpected world operation: " + method.getName());
        });
    }
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
