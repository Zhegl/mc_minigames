package me.petr1furious.potatominigames.games.tanks;

import io.papermc.paper.entity.TeleportFlag;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Executes actual display transforms and the seat protocol through a narrow Bukkit boundary. */
public final class TankVisualCheck {
    public static void main(String[] args) throws Exception {
        installBlockDataFactory();
        RenderWorld render = new RenderWorld();
        for (TanksRules.Chassis chassis : TanksRules.Chassis.values()) {
            TankVisual visual = new TankVisual(new Location(render.world, 0, 65, 0), 0, chassis);
            check(
                    visual.entities().size() >= 40 && visual.entities().size() <= 72,
                    "detailed model has a bounded entity budget");
            check(
                    !(visual.seat() instanceof LivingEntity),
                    "seat does not expose a competing vanilla vehicle-health bar");
            double seatY = render.states.get(visual.seat()).position.getY() - 65;
            // Player riding attachment is .6 above the feet; zero-height Interaction seats
            // subtract it, unlike the native Zombie used by the original mount smoke test.
            double pilotFeetY = seatY - .6;
            check(
                    pilotFeetY + 1.62 >= TanksRules.geometry(chassis).turretTop() + .27,
                    "pilot eye clears turret roof in every class");
            check(
                    pilotFeetY + 1.15 < TanksRules.geometry(chassis).turretTop(),
                    "held equipment sits within the turret in third person");
            List<Entity> blocks =
                    visual.entities().stream()
                            .filter(entity -> entity instanceof BlockDisplay)
                            .toList();
            TanksRules.ModelGeometry bounds = TanksRules.geometry(chassis);
            for (Entity entity : visual.entities()) {
                RenderEntity state = render.states.get(entity);
                if (!(entity instanceof BlockDisplay)) continue;
                check(state.matrix != null, "every part gets an initial transform");
                for (int x = 0; x <= 1; x++)
                    for (int y = 0; y <= 1; y++)
                        for (int z = 0; z <= 1; z++) {
                            Vector3f point = state.matrix.transformPosition(new Vector3f(x, y, z));
                            check(
                                    Float.isFinite(point.x + point.y + point.z),
                                    "display matrices stay finite");
                            check(point.y >= -.01, "tracks rest above the ground plane");
                            check(
                                    Math.abs(point.x) <= TanksArena.HULL_HALF_WIDTH + .01,
                                    "model width stays inside the collision hull");
                            // The final four blocks form the decorative cannon, which is not an
                            // armor target.
                            if (blocks.indexOf(entity) < blocks.size() - 4) {
                                boolean inHull =
                                        Math.abs(point.x) <= bounds.hullHalfWidth() + .01
                                                && Math.abs(point.z)
                                                        <= bounds.hullHalfLength() + .01
                                                && point.y >= -.01
                                                && point.y <= bounds.hullHeight() + .01;
                                boolean inTurret =
                                        Math.abs(point.x) <= bounds.turretHalfWidth() + .01
                                                && Math.abs(point.z)
                                                        <= bounds.turretHalfLength() + .01
                                                && point.y >= bounds.turretBottom() - .01
                                                && point.y <= bounds.turretTop() + .01;
                                check(
                                        inHull || inTurret,
                                        "rendered armor fits the shared class-specific projectile"
                                                + " volumes: "
                                                + chassis
                                                + " part "
                                                + blocks.indexOf(entity)
                                                + " "
                                                + point);
                            }
                        }
            }
            visual.update(new Location(render.world, 5, 65, 8, 90, 0), 135, -15, 30, 100);
            RenderEntity mount = render.states.get(visual.seat());
            check(
                    mount.position.getX() > 5 && Math.abs(mount.position.getZ() - 8) < .01,
                    "rear seat offset turns with the hull");
            check(mount.position.getYaw() == 0, "seat following does not overwrite mouse yaw");
            check(mount.retainedPassengers, "moving seat explicitly retains its passenger");
            Location muzzle = visual.muzzle();
            check(
                    muzzle.getX() < 5 && muzzle.getZ() < 8 && muzzle.getY() > 66,
                    "independent turret aims northwest/up");
            Map<Entity, Matrix4f> parked = new IdentityHashMap<>();
            for (Entity entity : visual.entities())
                if (entity instanceof BlockDisplay)
                    parked.put(entity, new Matrix4f(render.states.get(entity).matrix));
            visual.update(new Location(render.world, 4.9, 65, 8, 90, 0), 135, -15, 20, 100);
            long movingParts =
                    parked.entrySet().stream()
                            .filter(entry ->
                                    !entry.getValue()
                                            .equals(render.states.get(entry.getKey()).matrix))
                            .count();
            check(
                    movingParts >= 20 && movingParts <= 30,
                    "displacement animates road wheels and tread cleats without touching fixed"
                            + " armor transforms");
            int transforms = render.transforms;
            int before = render.particles;
            for (int i = 0; i < 100; i++)
                visual.update(new Location(render.world, 4.9, 65, 8, 90, 0), 135, -15, 20, 100);
            check(
                    render.transforms == transforms,
                    "stationary tanks do not resend model transforms");
            check(render.particles - before <= 12, "damage smoke has a bounded update rate");
            int ownedCount = visual.entities().size();
            for (int reconnect = 0; reconnect < 3; reconnect++) {
                Entity discardedSeat = visual.seat();
                discardedSeat.remove();
                Entity replacement = visual.seat();
                check(replacement != discardedSeat && replacement.isValid(),
                        "a removed logout seat is replaced before remount");
                check(visual.entities().size() == ownedCount
                                && !visual.entities().contains(discardedSeat)
                                && visual.entities().contains(replacement),
                        "seat recovery owns one current anchor without duplicating the model");
                RenderEntity state = render.states.get(replacement);
                check(state.invulnerable && !state.gravity && !state.persistent && !state.visibleByDefault,
                        "recovered seats retain privacy and nonpersistent vehicle configuration");
                check(Math.abs(state.position.getY() - mount.position.getY()) < .001,
                        "recovered seats retain the class-specific camera anchor");
            }
            mount = render.states.get(visual.seat());
            visual.wreck();
            check(mount.ejected, "wreck ejects its pilot");
            List<Entity> owned = List.copyOf(visual.entities());
            visual.remove();
            check(
                    owned.stream().allMatch(entity -> render.states.get(entity).removed),
                    "cleanup removes every owned entity");
            check(visual.entities().isEmpty(), "cleanup drops references");
            visual.remove();
        }
        System.out.println("Tank visual transform checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void installBlockDataFactory() throws Exception {
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(
                null,
                Proxy.newProxyInstance(
                        Server.class.getClassLoader(),
                        new Class<?>[] {Server.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("createBlockData")) {
                                Material material = (Material) args[0];
                                return Proxy.newProxyInstance(
                                        BlockData.class.getClassLoader(),
                                        new Class<?>[] {BlockData.class},
                                        (data, operation, values) ->
                                                operation.getName().equals("getMaterial")
                                                        ? material
                                                        : null);
                            }
                            throw new AssertionError("Server: " + method.getName());
                        }));
    }

    private static final class RenderEntity {
        Matrix4f matrix;
        Location position;
        boolean retainedPassengers, ejected, removed;
        boolean invulnerable, gravity = true, persistent = true, visibleByDefault = true;
    }

    private static final class RenderWorld {
        final Map<Entity, RenderEntity> states = new IdentityHashMap<>();
        int particles, transforms;
        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "spawn" -> {
                                                RenderEntity state = new RenderEntity();
                                                state.position = ((Location) args[0]).clone();
                                                Entity entity =
                                                        (Entity)
                                                                Proxy.newProxyInstance(
                                                                        Entity.class
                                                                                .getClassLoader(),
                                                                        new Class<?>[] {
                                                                            (Class<?>) args[1]
                                                                        },
                                                                        (object,
                                                                                action,
                                                                                values) -> {
                                                                            String name =
                                                                                    action
                                                                                            .getName();
                                                                            if (name.equals(
                                                                                    "setTransformationMatrix")) {
                                                                                transforms++;
                                                                                state.matrix =
                                                                                        new Matrix4f(
                                                                                                (Matrix4f)
                                                                                                        values[
                                                                                                                0]);
                                                                            } else if (name.equals(
                                                                                    "teleport")) {
                                                                                state.position =
                                                                                        ((Location)
                                                                                                        values[
                                                                                                                0])
                                                                                                .clone();
                                                                                if (values.length
                                                                                                > 1
                                                                                        && values[1]
                                                                                                instanceof
                                                                                                TeleportFlag
                                                                                                                []
                                                                                                        flags)
                                                                                    state.retainedPassengers =
                                                                                            List.of(
                                                                                                            flags)
                                                                                                    .contains(
                                                                                                            TeleportFlag
                                                                                                                    .EntityState
                                                                                                                    .RETAIN_PASSENGERS);
                                                                                return true;
                                                                            } else if (name.equals(
                                                                                    "eject")) {
                                                                                state.ejected =
                                                                                        true;
                                                                                return true;
                                                                            } else if (name.equals(
                                                                                    "remove"))
                                                                                state.removed =
                                                                                        true;
                                                                            else if (name.equals("isValid")) return !state.removed;
                                                                            else if (name.equals("setInvulnerable")) state.invulnerable = (boolean) values[0];
                                                                            else if (name.equals("setGravity")) state.gravity = (boolean) values[0];
                                                                            else if (name.equals("setPersistent")) state.persistent = (boolean) values[0];
                                                                            else if (name.equals("setVisibleByDefault")) state.visibleByDefault = (boolean) values[0];
                                                                            else if (name.equals(
                                                                                    "equals"))
                                                                                return object
                                                                                        == values[
                                                                                                0];
                                                                            else if (name.equals(
                                                                                    "hashCode"))
                                                                                return System
                                                                                        .identityHashCode(
                                                                                                object);
                                                                            else if (!name
                                                                                            .startsWith(
                                                                                                    "set")
                                                                                    && !name.equals(
                                                                                            "text"))
                                                                                throw new AssertionError(
                                                                                        "Entity: "
                                                                                                + name);
                                                                            return null;
                                                                        });
                                                states.put(entity, state);
                                                @SuppressWarnings("unchecked")
                                                Consumer<Entity> configure =
                                                        (Consumer<Entity>) args[2];
                                                configure.accept(entity);
                                                yield entity;
                                            }
                                            case "spawnParticle" -> {
                                                particles++;
                                                yield null;
                                            }
                                            case "equals" -> proxy == args[0];
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "getUID" -> new java.util.UUID(0, 17);
                                            default ->
                                                    throw new AssertionError(
                                                            "World: " + method.getName());
                                        });
    }
}
