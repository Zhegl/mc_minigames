package me.petr1furious.potatominigames.games.tanks;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.*;
import java.util.*;

/** Verifies production event boundaries without native item/particle registries. */
public final class TanksSessionCheck {
    public static void main(String[] args) throws Exception {
        UUID id = UUID.randomUUID();
        Player p =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (o, m, a) -> {
                                    if (m.getName().equals("getUniqueId")) return id;
                                    if (m.getName().equals("hashCode")) return id.hashCode();
                                    if (m.getName().equals("equals")) return o == a[0];
                                    if (m.getReturnType() == boolean.class) return false;
                                    if (m.getReturnType() == int.class) return 0;
                                    return null;
                                });
        TanksSession session = new TanksSession(null, null, null);
        TanksMatch model = new TanksMatch(1000, 100, false);
        model.add(id, 0, TanksRules.Chassis.MEDIUM);
        Field match = TanksSession.class.getDeclaredField("match");
        match.setAccessible(true);
        match.set(session, model);
        EntityDamageEvent hit =
                new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL, null, 10);
        session.onEntityDamage(hit);
        check(hit.isCancelled(), "vanilla damage cannot bypass tank armor");
        session.onItemHeld(new PlayerItemHeldEvent(p, 0, 1));
        check(model.actor(id).shell() == TanksRules.Shell.HE, "slot two selects HE");
        session.onItemHeld(new PlayerItemHeldEvent(p, 1, 0));
        check(model.actor(id).shell() == TanksRules.Shell.AP, "slot one selects AP");
        check(!session.allowLateJoin(), "roster locked");
        check(session.returnToLobbyAfterGame(), "returns to lobby");
        barrelPrediction();
        System.out.println("TanksSessionCheck: event and barrel prediction checks passed");
    }

    private static void barrelPrediction() throws Exception {
        double[] wall = {2};
        World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (object, method, args) -> {
                                    if (method.getName().equals("rayTraceBlocks")) {
                                        Location start = (Location) args[0];
                                        Vector direction = (Vector) args[1];
                                        double length = (double) args[2];
                                        double distance =
                                                (wall[0] - start.getZ()) / direction.getZ();
                                        return Double.isFinite(distance)
                                                        && distance >= 0
                                                        && distance <= length
                                                ? new RayTraceResult(
                                                        start.toVector()
                                                                .add(
                                                                        direction
                                                                                .clone()
                                                                                .multiply(
                                                                                        distance)))
                                                : null;
                                    }
                                    if (method.getName().equals("equals")) return object == args[0];
                                    if (method.getName().equals("getUID")) return new UUID(0, 77);
                                    throw new AssertionError("World: " + method.getName());
                                });
        TanksSession session = new TanksSession(null, null, null);
        TanksMatch match = new TanksMatch(1000, 100, false);
        UUID shooterId = new UUID(0, 1), targetId = new UUID(0, 2);
        TanksMatch.Actor shooter = match.add(shooterId, 0, TanksRules.Chassis.MEDIUM);
        match.add(targetId, 1, TanksRules.Chassis.HEAVY);
        set(session, "world", world);
        set(session, "match", match);
        set(session, "tick", 100L);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> vehicles = (Map<UUID, Object>) get(session, "vehicles");
        Location hull = new Location(world, 0, 65, 0);
        vehicles.put(shooterId, vehicle(hull, TanksRules.Chassis.MEDIUM));
        Location muzzle = TankVisual.muzzleAt(hull, 0, 0, TanksRules.Chassis.MEDIUM);
        Method predict =
                TanksSession.class.getDeclaredMethod(
                        "predict", TanksMatch.Actor.class, Location.class);
        predict.setAccessible(true);
        Object prediction = predict.invoke(session, shooter, muzzle);
        check(
                Math.abs(((Location) property(prediction, "at")).getZ() - 2) < 1e-8,
                "prediction stops at the wall between breech and muzzle");
        check((boolean) property(prediction, "blocked"), "pre-muzzle wall marks the gun blocked");

        wall[0] = Double.POSITIVE_INFINITY;
        vehicles.put(targetId, vehicle(new Location(world, 0, 65, 4.25), TanksRules.Chassis.HEAVY));
        prediction = predict.invoke(session, shooter, muzzle);
        check(
                property(prediction, "target") == null
                        && !(boolean) property(prediction, "blocked"),
                "prediction cannot reveal an unseen neighbor overlapping the barrel");
        Method preflight =
                TanksSession.class.getDeclaredMethod(
                        "preflight", TanksMatch.Actor.class, Location.class, boolean.class);
        preflight.setAccessible(true);
        Object physical = preflight.invoke(session, shooter, muzzle, false);
        check(
                targetId.equals(property(physical, "target"))
                        && (boolean) property(physical, "blocked"),
                "physical shot still hits the unseen neighbor before the muzzle");
        @SuppressWarnings("unchecked")
        Map<UUID, Long> spotted = (Map<UUID, Long>) get(session, "spottedRed");
        spotted.put(targetId, 101L);
        prediction = predict.invoke(session, shooter, muzzle);
        check(
                targetId.equals(property(prediction, "target"))
                        && (boolean) property(prediction, "blocked"),
                "spotted neighbor shares the firing obstruction guard");
        wall[0] = ((Location) property(prediction, "at")).getZ();
        physical = preflight.invoke(session, shooter, muzzle, false);
        check(
                property(physical, "target") == null,
                "world block wins an exact pre-muzzle hull tie");
        vehicles.remove(targetId);
        wall[0] = Double.POSITIVE_INFINITY;
        Object shooterVehicle = vehicles.get(shooterId);
        set(shooterVehicle, "turretPitch", 18f);
        Location depressed = TankVisual.muzzleAt(hull, 0, 18, TanksRules.Chassis.MEDIUM);
        prediction = predict.invoke(session, shooter, depressed);
        check(
                (boolean) property(prediction, "ownHull")
                        && (boolean) property(prediction, "blocked"),
                "gun depression into own hull produces a blocked marker");
        check(shooter.reloadTicks() == 0, "prediction does not consume a round");
    }

    private static Object vehicle(Location at, TanksRules.Chassis chassis) throws Exception {
        Class<?> type =
                Arrays.stream(TanksSession.class.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("Vehicle"))
                        .findFirst()
                        .orElseThrow();
        Constructor<?> constructor = type.getDeclaredConstructor(Location.class);
        constructor.setAccessible(true);
        Object vehicle = constructor.newInstance(at);
        set(vehicle, "chassis", chassis);
        return vehicle;
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static Object property(Object object, String name) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }

    private static void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
    }
}
