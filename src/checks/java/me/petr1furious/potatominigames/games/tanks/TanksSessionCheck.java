package me.petr1furious.potatominigames.games.tanks;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
        var driver = model.actor(id);
        check(TanksSession.sidebarLines(driver, false, true, 1, 1).isEmpty(),
                "zoom clears the side of the target");
        var battleLines = TanksSession.sidebarLines(driver, false, false, 1, 1);
        check(!battleLines.contains(TanksSession.tr("hud.help"))
                        && !battleLines.contains(TanksSession.tr("hud.kills", 0)),
                "combat sidebar omits tutorial and scoreboard statistics");
        check(TanksSession.sidebarLines(driver, true, false, 1, 1)
                        .contains(TanksSession.tr("hud.help")),
                "preparation retains the controls reference");
        check(session.returnToLobbyAfterGame(), "returns to lobby");
        barrelPrediction();
        remountKitPreservation();
        arenaViewBudget();
        smokePresentationDeadline();
        disconnectDetachesBeforeNativeLogout();
        System.out.println(
                "TanksSessionCheck: event, barrel prediction and remount kit checks passed");
    }

    private static void disconnectDetachesBeforeNativeLogout() throws Exception {
        TanksSession session = new TanksSession(null, null, null);
        TanksMatch match = new TanksMatch(1000, 100, true);
        TanksMatch.Actor actor = match.add(new UUID(0, 91), 0, TanksRules.Chassis.MEDIUM);
        match.start();
        actor.damage(180);
        check(actor.fire() && actor.smoke(), "disconnect fixture consumes a shot and smoke");
        actor.selectShell(TanksRules.Shell.HE);
        set(session, "match", match);
        set(session, "scopes", new TanksScope(player -> null));
        Object vehicle = vehicle(new Location(null, 40, 65, 35), actor.chassis());
        set(vehicle, "speed", .2);
        set(vehicle, "trackSpeed", .2);
        set(vehicle, "smokeUntil", 120L);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> vehicles = (Map<UUID, Object>) get(session, "vehicles");
        vehicles.put(actor.id(), vehicle);
        @SuppressWarnings("unchecked")
        Map<UUID, Player> connections = (Map<UUID, Player>) get(session, "connections");
        @SuppressWarnings("unchecked")
        Map<UUID, Object> inputs = (Map<UUID, Object>) get(session, "inputs");
        int[] detaches = {0};
        Player player =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (object, method, args) ->
                                        switch (method.getName()) {
                                            case "getUniqueId" -> actor.id();
                                            case "getAttribute" -> null;
                                            case "leaveVehicle" -> {
                                                check(
                                                        (boolean) get(session, "internalTeleport"),
                                                        "logout detach bypasses the normal dismount"
                                                                + " lock");
                                                check(
                                                        actor.online(),
                                                        "driver detaches before native logout"
                                                                + " vehicle removal");
                                                detaches[0]++;
                                                yield true;
                                            }
                                            default ->
                                                    throw new AssertionError(
                                                            "quit Player: " + method.getName());
                                        });
        double hp = actor.hp();
        int reload = actor.reloadTicks(), smoke = actor.smokeTicks(), kits = actor.repairKits();
        for (int repeat = 0; repeat < 3; repeat++) {
            actor.reconnect();
            connections.put(actor.id(), player);
            inputs.put(actor.id(), new Object());
            session.onParticipantQuit(player);
            check(detaches[0] == repeat + 1, "every disconnect detaches its driver");
            check(
                    !actor.online()
                            && !connections.containsKey(actor.id())
                            && !inputs.containsKey(actor.id()),
                    "quit removes stale connection and held input");
            check(
                    (double) get(vehicle, "speed") == 0 && (double) get(vehicle, "trackSpeed") == 0,
                    "abandoned tank stops immediately");
            check(
                    actor.hp() == hp
                            && actor.reloadTicks() == reload
                            && actor.smokeTicks() == smoke
                            && actor.repairKits() == kits
                            && actor.smokeCharges() == 0
                            && actor.shell() == TanksRules.Shell.HE
                            && (long) get(vehicle, "smokeUntil") == 120L,
                    "disconnect retains damage, reload, chosen ammo, consumables and smoke"
                            + " deadline");
        }
    }

    private static void smokePresentationDeadline() {
        check(
                TanksSmokeVisual.particle() == org.bukkit.Particle.SMOKE,
                "concealment must use short-lived gray smoke rather than fourteen-second signal"
                        + " wisps");
        check(
                TanksSmokeVisual.CLIENT_TAIL_TICKS >= 41
                        && TanksSmokeVisual.CLIENT_TAIL_TICKS <= 42,
                "client fade budget covers the verified maximum particle lifetime without a long"
                        + " tail");
        long deployed = 100, until = deployed + 120;
        int bursts = 0;
        for (long now = deployed; now < until + 100; now++) {
            boolean active = now < until;
            check(
                    TanksSmokeVisual.emits(now, until) == active,
                    "visual emission spans the full six-second cover window without an early"
                            + " cutoff");
            if ((now - deployed) % 5 == 0 && TanksSmokeVisual.emits(now, until)) bursts++;
            TanksSmokeVisual.Phase phase = TanksSmokeVisual.phase(now, until);
            check(
                    (phase == TanksSmokeVisual.Phase.ACTIVE) == active,
                    "owner HUD reports protection only before the exact authoritative deadline");
            if (now >= until && now < until + 42)
                check(
                        phase == TanksSmokeVisual.Phase.FADING,
                        "owner HUD labels the bounded residual particles as expired protection");
        }
        check(
                bursts == 24 && bursts * TanksSmokeVisual.PARTICLES_PER_BURST <= 1728,
                "one smoke charge has a fixed bounded particle budget at the existing five-tick"
                        + " cadence");
        check(
                TanksSmokeVisual.phase(0, 0) == TanksSmokeVisual.Phase.NONE,
                "a player who never deployed smoke has no false smoke countdown");
    }

    private static void arenaViewBudget() throws Exception {
        // Disposable party worlds currently inherit four chunks; the tank-only policy must raise
        // it.
        int[] view = {4}, send = {4};
        World arena =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (object, method, args) -> {
                                    if (method.getName().equals("setViewDistance")) {
                                        view[0] = (int) args[0];
                                        return null;
                                    }
                                    if (method.getName().equals("setSendViewDistance")) {
                                        send[0] = (int) args[0];
                                        return null;
                                    }
                                    throw new AssertionError(
                                            "view policy must only configure this arena's view"
                                                    + " distance: "
                                                    + method.getName());
                                });
        Method policy =
                Arrays.stream(TanksSession.class.getDeclaredMethods())
                        .filter(method ->
                                method.getName().equals("configureArenaView")
                                        && Arrays.equals(
                                                method.getParameterTypes(),
                                                new Class<?>[] {World.class}))
                        .findFirst()
                        .orElse(null);
        if (policy != null) {
            policy.setAccessible(true);
            policy.invoke(null, arena);
        }
        check(
                view[0] * 16 >= 86 + 16,
                "tank arena view budget must include86-block scout spotting plus one chunk boundary"
                        + " pad");
        check(
                send[0] * 16 >= 86 + 16,
                "tank arena send budget must include scout spotting plus its chunk boundary pad");
        check(
                Math.pow(Math.max(view[0], send[0]) * 2 + 1, 2) <= 289,
                "tank arena view cost stays bounded to at most289 chunks per client");
        for (double start : new double[] {-64.01, -48.01, -16.01, -.01, .01, 15.99, 47.99}) {
            int chunkGap = (int) Math.floor((start + 86) / 16) - (int) Math.floor(start / 16);
            check(
                    view[0] >= chunkGap + 1,
                    "scout-range targets stay within the chunk budget across boundary offsets");
        }
    }

    private static void remountKitPreservation() throws Exception {
        TanksSession session = new TanksSession(null, null, null);
        TanksMatch match = new TanksMatch(1000, 100, true);
        TanksMatch.Actor actor = match.add(new UUID(0, 44), 0, TanksRules.Chassis.MEDIUM);
        match.start();
        actor.selectShell(TanksRules.Shell.HE);
        check(actor.fire(), "remount fixture starts with a consumed shot");
        actor.damage(75);
        double hp = actor.hp();
        int reload = actor.reloadTicks();
        ItemStack radar = new ExistingItem();
        ItemStack[] kit = new ItemStack[9];
        Arrays.setAll(kit, index -> new ExistingItem());
        int[] selected = {6}, writes = {0};
        PlayerInventory inventory =
                (PlayerInventory)
                        Proxy.newProxyInstance(
                                PlayerInventory.class.getClassLoader(),
                                new Class<?>[] {PlayerInventory.class},
                                (object, method, args) ->
                                        switch (method.getName()) {
                                            case "clear",
                                                    "setItem",
                                                    "setItemInOffHand",
                                                    "setContents",
                                                    "setStorageContents" -> {
                                                writes[0]++;
                                                throw new AssertionError(
                                                        "class remount must preserve existing kit"
                                                                + " and exact radar stack");
                                            }
                                            case "getItemInOffHand" -> radar;
                                            case "getItem" -> kit[(int) args[0]];
                                            case "setHeldItemSlot" -> {
                                                selected[0] = (int) args[0];
                                                yield null;
                                            }
                                            default ->
                                                    throw new AssertionError(
                                                            "Inventory: " + method.getName());
                                        });
        Player player =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (object, method, args) -> {
                                    if (method.getName().equals("getInventory")) return inventory;
                                    throw new AssertionError("Player: " + method.getName());
                                });
        Method prepare =
                TanksSession.class.getDeclaredMethod(
                        "prepareKit", Player.class, TanksMatch.Actor.class, boolean.class);
        prepare.setAccessible(true);
        for (int repeat = 0; repeat < 3; repeat++) prepare.invoke(session, player, actor, false);
        check(
                writes[0] == 0 && inventory.getItemInOffHand() == radar,
                "class remount retains the same offhand map object");
        for (int slot = 0; slot < kit.length; slot++)
            check(
                    inventory.getItem(slot) == kit[slot],
                    "class remount preserves every existing kit item");
        check(selected[0] == 1, "class remount returns the selected HE shell slot");
        actor.selectShell(TanksRules.Shell.AP);
        prepare.invoke(session, player, actor, false);
        check(selected[0] == 0, "class remount returns the selected AP shell slot");
        check(
                actor.hp() == hp && actor.reloadTicks() == reload,
                "remount keeps actual HP and reload state");
    }

    /** Identity-only item tokens avoid native item registries at the inventory-policy boundary. */
    private static final class ExistingItem extends ItemStack {
        ExistingItem() {
            super();
        }
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
        match.actor(targetId).spottedBy(shooter, 100);
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
