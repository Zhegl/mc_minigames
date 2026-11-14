package me.petr1furious.potatominigames.games.party;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Trident;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

/** Regression checks for fixed countdown anchors and bounded projectile cleanup. */
public final class PartyRuntimeCheck {
    public static void main(String[] args) {
        freezeChecks();
        projectileChecks();
        mountChecks();
        deathPauseChecks();
        bombChecks();
        impactChecks();
        System.out.println("Party runtime: fixed countdown anchors, jump/flight lock, speed restoration and targeted projectile expiry, mount ownership/cleanup and death/early-respawn timing passed");
    }

    private static void freezeChecks() {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (proxy, method, values) -> {
            return switch (method.getName()) {
                case "equals" -> proxy == values[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "Freeze check world";
                default -> throw new AssertionError("Unexpected world call: " + method.getName());
            };
        });
        FakePlayer first = new FakePlayer(new UUID(0, 1), new Location(world, 4.5, 81, .5));
        FakePlayer spectator = new FakePlayer(new UUID(0, 2), new Location(world, .5, 88, .5));
        spectator.walk = .3f; spectator.fly = .2f;
        CountdownFreeze freeze = new CountdownFreeze();
        first.velocity = new Vector(.2, .42, -.1);
        freeze.freeze(first.player); freeze.freeze(spectator.player);
        check(first.walk == 0 && first.fly == 0 && spectator.fly == 0, "Walking and spectator flight disabled");
        check(first.velocity.lengthSquared() == 0, "Preexisting knockback/jump velocity cleared");
        Location attempted = new Location(world, 7, 83, 2, 90, 30);
        PlayerMoveEvent move = new PlayerMoveEvent(first.player, new Location(world, 6, 82, 1), attempted);
        check(freeze.hold(move), "Frozen player movement handled");
        check(move.getTo().getX() == 4.5 && move.getTo().getY() == 81 && move.getTo().getZ() == .5,
                "Correction uses original spawn, not drifting event.from");
        check(move.getTo().getYaw() == 90 && move.getTo().getPitch() == 30, "Looking around does not move the player");
        PlayerMoveEvent fly = new PlayerMoveEvent(spectator.player, spectator.location.clone(), spectator.location.clone().add(0, 6, 0));
        freeze.hold(fly);
        check(fly.getTo().getY() == 88, "Waiting spectators cannot fly during countdown");
        PlayerJumpEvent jump = new PlayerJumpEvent(first.player, first.location.clone(), first.location.clone().add(0, .42, 0));
        freeze.onJump(jump);
        check(jump.isCancelled(), "Jump rejected before its movement is applied");
        freeze.freeze(first.player);
        freeze.release(first.id);
        check(first.walk == .2f && first.fly == .1f, "Repeated freeze preserves original speeds");
        PlayerMoveEvent free = new PlayerMoveEvent(first.player, first.location.clone(), attempted.clone());
        check(!freeze.hold(free) && free.getTo().getX() == 7, "Combat movement is unrestricted after release");
        PlayerJumpEvent freeJump = new PlayerJumpEvent(first.player, first.location.clone(), attempted.clone());
        freeze.onJump(freeJump);
        check(!freeJump.isCancelled(), "Combat jumping restored");
        freeze.releaseAll(); freeze.releaseAll();
        check(spectator.walk == .3f && spectator.fly == .2f, "Cleanup restores custom spectator speeds exactly and is idempotent");
    }

    private static void projectileChecks() {
        ArenaProjectiles shots = new ArenaProjectiles();
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        FakeArrow first = new FakeArrow(), second = new FakeArrow();
        shots.track(a, first.arrow, 0); shots.track(b, second.arrow, 0);
        shots.clear(a);
        check(first.removed == 1 && second.removed == 0, "Upgrade/death removes only that shooter's arrows");
        shots.expire(199);
        check(second.removed == 0, "Young in-flight arrows remain live");
        second.stuck = true; shots.expire(199);
        check(second.removed == 1, "Embedded arrows are promptly removed");
        FakeArrow expired = new FakeArrow();
        shots.track(a, expired.arrow, 10); shots.expire(209);
        check(expired.removed == 0, "Ten-second lifetime not shortened");
        shots.expire(210);
        check(expired.removed == 1, "Old arrows cannot accumulate indefinitely");
        FakeArrow alreadyGone = new FakeArrow(); alreadyGone.valid = false;
        shots.track(b, alreadyGone.arrow, 210); shots.expire(211); shots.clearAll();
        check(alreadyGone.removed == 0 && second.removed == 1, "Dead references are discarded without duplicate removal");
        FakeArrow trident = new FakeArrow(Trident.class);
        trident.stuck = true;
        shots.track(a, trident.arrow, 0); shots.expire(1000);
        check(trident.removed == 0, "Embedded/old Loyalty tridents survive arrow expiry to return to their owner");
        shots.clear(a);
        check(trident.removed == 1, "Round/quit cleanup still removes a tracked trident exactly once");
        FakeArrow[] volley = new FakeArrow[1000];
        for (int i = 0; i < volley.length; i++) {
            volley[i] = new FakeArrow(); shots.track(i % 2 == 0 ? a : b, volley[i].arrow, 300);
        }
        shots.expire(500); shots.clearAll();
        for (FakeArrow arrow : volley) check(arrow.removed == 1, "Sustained fire is fully expired exactly once");
    }

    private static void mountChecks() {
        PvpMounts mounts = new PvpMounts();
        FakePlayer first = new FakePlayer(new UUID(0, 1), new Location(null, 0, 81, 0));
        FakePlayer second = new FakePlayer(new UUID(0, 2), new Location(null, 5, 81, 0));
        FakeHorse a = new FakeHorse(), b = new FakeHorse();
        mounts.track(first.player, a.horse);
        mounts.track(second.player, b.horse);
        check(mounts.owner(a.horse) == first.player && mounts.owner(b.horse) == second.player, "Identical-class deciders keep separate horse ownership");
        check(mounts.mayRide(first.player, a.horse) && !mounts.mayRide(second.player, a.horse), "Only the owner may mount or remount their horse");
        check(!mounts.mayDamage(a.horse, first.player) && mounts.mayDamage(a.horse, second.player), "Enemy damage is allowed; self damage is blocked");
        check(!mounts.mayDamage(a.horse, null) && mounts.owner(first.player) == null, "Nonplayer attackers and unrelated entities are excluded");
        mounts.remove(first.id);
        check(a.ejected == 1 && a.removed == 1 && b.removed == 0, "Elimination/quit removes only the departing player's horse and dismounts them");
        check(mounts.owner(a.horse) == null && !mounts.mayRide(first.player, a.horse), "Removed mounts cannot be boarded during cleanup");
        FakeHorse replacement = new FakeHorse();
        mounts.track(second.player, replacement.horse);
        check(b.ejected == 1 && b.removed == 1 && mounts.owner(b.horse) == null, "Replacing a tracked mount cannot leak the previous entity");
        mounts.clear(); mounts.clear(); mounts.remove(first.id);
        check(replacement.ejected == 1 && replacement.removed == 1, "Round reset/abort cleanup removes all horses exactly once");
        check(mounts.owner(replacement.horse) == null, "No mount references survive round cleanup");
    }
    private static final class FakeHorse {
        int ejected, removed;
        final Horse horse = (Horse) Proxy.newProxyInstance(Horse.class.getClassLoader(), new Class<?>[] {Horse.class}, (proxy, method, values) -> {
            return switch (method.getName()) {
                case "equals" -> proxy == values[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "Test horse";
                case "eject" -> { ejected++; yield true; }
                case "remove" -> { removed++; yield null; }
                default -> throw new AssertionError("Unexpected horse call: " + method.getName());
            };
        });
    }

    private static void deathPauseChecks() {
        DeathPause pause = new DeathPause();
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        check(!pause.pending(a, 100) && pause.remaining(100) == 0, "An alive player has no death pause");
        pause.begin(a, 100);
        check(pause.pending(a, 100) && pause.remaining(a, 100) == 60, "A death reserves three seconds before automatic respawn/result");
        check(pause.pending(a, 120) && pause.pending(a, 159), "Clicking vanilla Respawn after one second cannot bypass the combat pause");
        pause.begin(a, 130);
        check(pause.remaining(a, 130) == 30, "Duplicate death/respawn callbacks cannot restart or extend the timer");
        check(!pause.pending(a, 160) && pause.remaining(a, 200) == 0, "Automatic respawn becomes available exactly at the deadline");
        pause.begin(b, 140);
        check(pause.remaining(160) == 40, "Two simultaneous/nearby deaths each retain their full reading time");
        pause.forget(a);
        check(pause.pending(b, 160), "One player's respawn/quit never clears another player's pause");
        pause.clear(); pause.clear();
        check(pause.remaining(160) == 0, "Round/end cleanup clears every pending death and is idempotent");
        pause.begin(a, 300);
        check(pause.remaining(a, 300) == 60, "The same player receives a fresh delay next life/round");
    }

    private static final class FakePlayer {
        final UUID id;
        final Location location;
        final Player player;
        float walk = .2f, fly = .1f;
        Vector velocity = new Vector();
        FakePlayer(UUID id, Location location) {
            this.id = id; this.location = location;
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, values) -> {
                return switch (method.getName()) {
                    case "equals" -> proxy == values[0];
                    case "hashCode" -> id.hashCode();
                    case "getUniqueId" -> id;
                    case "getLocation" -> location.clone();
                    case "getWalkSpeed" -> walk;
                    case "getFlySpeed" -> fly;
                    case "getVelocity" -> velocity.clone();
                    case "setWalkSpeed" -> { walk = (float) values[0]; yield null; }
                    case "setFlySpeed" -> { fly = (float) values[0]; yield null; }
                    case "setVelocity" -> { velocity = ((Vector) values[0]).clone(); yield null; }
                    case "setFallDistance" -> null;
                    default -> throw new AssertionError("Unexpected player call: " + method.getName());
                };
            });
        }
    }
    private static final class FakeArrow {
        boolean valid = true, stuck;
        int removed;
        final AbstractArrow arrow;
        FakeArrow() { this(AbstractArrow.class); }
        FakeArrow(Class<? extends AbstractArrow> type) {
            arrow = (AbstractArrow) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, values) -> {
                return switch (method.getName()) {
                    case "isValid" -> valid;
                    case "isInBlock" -> stuck;
                    case "remove" -> { removed++; valid = false; yield null; }
                    default -> throw new AssertionError("Unexpected projectile call: " + method.getName());
                };
            });
        }
    }

    private static void bombChecks() {
        PvpBombs bombs = new PvpBombs();
        FakePlayer first = new FakePlayer(new UUID(0, 1), new Location(null, 0, 81, 0));
        FakePlayer second = new FakePlayer(new UUID(0, 2), new Location(null, 4, 81, 0));
        FakeBomb a = new FakeBomb(10), b = new FakeBomb(11), exploding = new FakeBomb(12), foreign = new FakeBomb(13);
        PvpBombs.configure(a.entity, first.player, new Vector(0, .2, 1));
        check(
                a.source == first.player
                        && a.fuse == 30
                        && a.power == 2
                        && !a.incendiary
                        && !a.persistent,
                "Both placed and thrown TNT use the real owner, short fuse, reduced power, no fire and no persistence");
        check(a.velocity.equals(new Vector(0, .2, 1)), "Configured thrown TNT retains its aimed launch velocity");
        bombs.track(first.player, a.entity); bombs.track(second.player, b.entity); bombs.track(first.player, exploding.entity);
        check(bombs.owner(a.entity) == first.player && bombs.owner(b.entity) == second.player, "Native TNT blasts keep separate owners in mirror matchups");
        check(bombs.owner(foreign.entity) == null && bombs.owner(first.player) == null, "Untracked TNT and normal entities cannot impersonate a bomber's charge");
        check(bombs.active(first.id) == 2 && bombs.active(second.id) == 1, "Active TNT is counted per owner");
        exploding.valid = false; // Vanilla discards TNT before applying explosion damage.
        check(bombs.owner(exploding.entity) == first.player && bombs.active(first.id) == 1,
                "Discarding the exploding entity does not lose kill attribution or occupy a live-bomb slot");
        bombs.eliminate(first.id);
        check(a.removed == 1 && b.removed == 0 && bombs.owner(exploding.entity) == first.player,
                "Death clears live bombs but preserves the current blast for same-tick trades");
        bombs.prune();
        check(bombs.owner(exploding.entity) == null && exploding.removed == 0, "The next tick drops finished-blast references without removing an entity twice");
        bombs.remove(second.id); bombs.remove(second.id);
        check(b.removed == 1 && bombs.owner(b.entity) == null, "Withdrawal removes only that owner's remaining bombs once");
        FakeBomb last = new FakeBomb(14);
        bombs.track(first.player, last.entity); bombs.clear(); bombs.clear();
        check(last.removed == 1 && bombs.owner(last.entity) == null, "Round/stop cleanup cancels all remaining native TNT exactly once");
        FakeBomb normal = new FakeBomb(20), rain = new FakeBomb(21);
        PvpAirstrike.configure(rain.entity, first.player);
        check(
                rain.source == first.player
                        && rain.fuse == 18
                        && rain.power == 1.5f
                        && !rain.incendiary
                        && !rain.persistent
                        && rain.velocity.getY() < 0,
                "Airstrike TNT keeps ownership and falls with the weaker short-fuse configuration");
        bombs.track(first.player, normal.entity); bombs.trackAirstrike(first.player, rain.entity);
        check(bombs.active(first.id) == 1 && bombs.activeAirstrike(first.id) == 1, "Airstrike TNT does not consume the four manual-charge slots");
        check(
                bombs.blastDamage(normal.entity, 100) == 16
                        && bombs.blastDamage(rain.entity, 100) == 9,
                "Normal and airstrike blasts use their own damage caps");
        rain.valid = false;
        check(
                bombs.owner(rain.entity) == first.player
                        && bombs.blastDamage(rain.entity, 100) == 9,
                "Discarding a detonating airstrike retains both kill credit and its damage cap");
        bombs.eliminate(first.id);
        check(normal.removed == 1 && bombs.owner(rain.entity) == first.player, "Death removes live charges but retains a currently resolving airstrike blast");
        bombs.prune();
        check(bombs.owner(rain.entity) == null, "Finished airstrike references expire with the existing bounded cleanup");
        FakeBomb reset = new FakeBomb(22);
        bombs.trackAirstrike(first.player, reset.entity); bombs.clear();
        check(reset.removed == 1 && bombs.activeAirstrike(first.id) == 0, "Round/stop cleanup removes airstrike entities too");
    }

    private static void impactChecks() {
        BoundingBox box = new BoundingBox(.7, 81, -.3, 1.3, 82.8, .3);
        Entity target =
                (Entity)
                        Proxy.newProxyInstance(
                                Entity.class.getClassLoader(),
                                new Class<?>[] {Entity.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getBoundingBox" -> box.clone();
                                            default -> throw new AssertionError(method.getName());
                                        });
        Block wall =
                (Block)
                        Proxy.newProxyInstance(
                                Block.class.getClassLoader(),
                                new Class<?>[] {Block.class},
                                (proxy, method, args) -> {
                                    throw new AssertionError(method.getName());
                                });
        boolean[] blocked = {false};
        World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "equals" -> proxy == args[0];
                                            case "rayTrace" -> {
                                                Vector from = ((Location) args[0]).toVector(),
                                                        direction = (Vector) args[1];
                                                if (blocked[0])
                                                    yield new RayTraceResult(
                                                            from.add(
                                                                    direction.clone().multiply(.1)),
                                                            wall,
                                                            BlockFace.WEST);
                                                @SuppressWarnings("unchecked")
                                                var predicate =
                                                        (java.util.function.Predicate<Entity>)
                                                                args[6];
                                                if (!predicate.test(target)) yield null;
                                                RayTraceResult hit =
                                                        box.clone()
                                                                .expand((double) args[5])
                                                                .rayTrace(
                                                                        from,
                                                                        direction,
                                                                        (double) args[2]);
                                                yield hit == null
                                                        ? null
                                                        : new RayTraceResult(
                                                                hit.getHitPosition(), target);
                                            }
                                            case "getNearbyEntities" -> {
                                                @SuppressWarnings("unchecked")
                                                var predicate =
                                                        (java.util.function.Predicate<Entity>)
                                                                args[4];
                                                yield predicate.test(target)
                                                        ? List.of(target)
                                                        : List.of();
                                            }
                                            default -> throw new AssertionError(method.getName());
                                        });
        FakePlayer owner = new FakePlayer(new UUID(0, 5), new Location(world, -2, 81, 0));
        PvpBombs bombs = new PvpBombs((p, entity) -> entity == target);
        FakeBomb hit = new FakeBomb(100);
        hit.at = new Location(world, 0, 81, 0);
        PvpBombs.configure(hit.entity, owner.player, new Vector(1, 0, 0));
        bombs.track(owner.player, hit.entity, true);
        hit.at.setX(2); // The whole target was crossed between scheduler ticks.
        bombs.tick();
        check(
                hit.fuse == 0
                        && !hit.gravity
                        && hit.velocity.lengthSquared() == 0
                        && hit.at.getX() < .7
                        && Math.abs(hit.at.getY() - 81) < .00001
                        && bombs.owner(hit.entity) == owner.player,
                "swept direct contact detonates the same owned TNT at the hit without extra"
                    + " damage");
        bombs.tick();
        check(hit.teleports == 1, "an impact-armed TNT cannot be detonated twice by the monitor");
        FakeBomb covered = new FakeBomb(101);
        covered.at = new Location(world, 0, 81, 0);
        PvpBombs.configure(covered.entity, owner.player, new Vector(1, 0, 0));
        bombs.track(owner.player, covered.entity, true);
        covered.at.setX(2);
        blocked[0] = true;
        bombs.tick();
        check(
                covered.fuse == PvpBombs.FUSE,
                "cover stops the direct-contact ray before an opponent");
        blocked[0] = false;
        FakeBomb miss = new FakeBomb(102);
        miss.at = new Location(world, 0, 81, 3);
        PvpBombs.configure(miss.entity, owner.player, new Vector(1, 0, 0));
        bombs.track(owner.player, miss.entity, true);
        miss.at.setX(2);
        bombs.tick();
        check(miss.fuse == PvpBombs.FUSE, "a nearby miss retains its ordinary short fuse");
        FakeBomb placed = new FakeBomb(103), rain = new FakeBomb(104), overlap = new FakeBomb(105);
        for (FakeBomb bomb : List.of(placed, rain, overlap)) {
            bomb.at = new Location(world, 1, 81, 0);
            PvpBombs.configure(bomb.entity, owner.player, new Vector());
        }
        bombs.track(owner.player, placed.entity);
        bombs.trackAirstrike(owner.player, rain.entity);
        bombs.track(owner.player, overlap.entity, true);
        bombs.tick();
        check(
                placed.fuse == PvpBombs.FUSE && rain.fuse == PvpBombs.FUSE && overlap.fuse == 0,
                "only thrown TNT gains point-overlap contact detonation, never placed or airstrike"
                    + " charges");
        PvpBombs miners = PvpBombs.miners();
        FakeBomb mining = new FakeBomb(106);
        mining.at = new Location(world, 1, 81, 0);
        mining.fuse = PvpBombs.MINERS_FUSE;
        miners.track(owner.player, mining.entity, true);
        miners.tick();
        check(
                mining.fuse == 50
                        && miners.blastDamage(mining.entity, 5) == 5
                        && miners.blastDamage(mining.entity, 100) == 10,
                "Miners retains its old fuse, damage and lack of contact detonation");
        miners.clear();
        bombs.clear();
    }

    private static final class FakeBomb {
        boolean valid = true, incendiary = true, persistent = true;
        int removed, fuse;
        float power;
        Object source;
        boolean gravity = true;
        int teleports;
        Location at = new Location(null, 0, 81, 0);
        Vector velocity = new Vector();
        final TNTPrimed entity;
        FakeBomb(long id) {
            entity =
                    (TNTPrimed)
                            Proxy.newProxyInstance(
                                    TNTPrimed.class.getClassLoader(),
                                    new Class<?>[] {TNTPrimed.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> new UUID(1, id);
                                                case "getLocation" -> at.clone();
                                                case "getHeight" -> .98;
                                                case "getFuseTicks" -> fuse;
                                                case "setGravity" -> {
                                                    gravity = (boolean) args[0];
                                                    yield null;
                                                }
                                                case "teleport" -> {
                                                    at = ((Location) args[0]).clone();
                                                    teleports++;
                                                    yield true;
                                                }
                                                case "isValid" -> valid;
                                                case "remove" -> {
                                                    removed++;
                                                    valid = false;
                                                    yield null;
                                                }
                                                case "setSource" -> {
                                                    source = args[0];
                                                    yield null;
                                                }
                                                case "setFuseTicks" -> {
                                                    fuse = (int) args[0];
                                                    yield null;
                                                }
                                                case "setYield" -> {
                                                    power = (float) args[0];
                                                    yield null;
                                                }
                                                case "setIsIncendiary" -> {
                                                    incendiary = (boolean) args[0];
                                                    yield null;
                                                }
                                                case "setPersistent" -> {
                                                    persistent = (boolean) args[0];
                                                    yield null;
                                                }
                                                case "setVelocity" -> {
                                                    velocity = ((Vector) args[0]).clone();
                                                    yield null;
                                                }
                                                default ->
                                                        throw new AssertionError(
                                                                "Unexpected TNT call: "
                                                                        + method.getName());
                                            });
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
