package me.petr1furious.potatominigames.games.cs;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiConsumer;

/** Short-lived, client-only solid bullet streaks; never added to the world's entity tracker. */
final class CsTracers {
    static final int LIFE_TICKS = 3;
    static final int MAX_ACTIVE = 128;
    static final double START = .5;
    static final float WIDTH = .02f;

    private record Streak(long until, List<Player> viewers, Object remove) {}

    private final Deque<Streak> active = new ArrayDeque<>();
    private final BiConsumer<Player, Object> sender;

    CsTracers() {
        this((viewer, packet) -> Native.BINDINGS.send(viewer, packet));
    }

    /** Native fixtures capture real packets while replacing only the connected-player transport. */
    CsTracers(BiConsumer<Player, Object> sender) {
        this.sender = sender;
    }

    void draw(
            Location from,
            Vector direction,
            double distance,
            Collection<Player> audience,
            long now) {
        if (from.getWorld() == null
                || !Double.isFinite(distance)
                || distance <= START
                || !Double.isFinite(direction.lengthSquared())
                || direction.lengthSquared() < .000001) return;
        List<Player> viewers =
                audience.stream()
                        .filter(p -> p.isOnline() && from.getWorld().equals(p.getWorld()))
                        .toList();
        if (viewers.isEmpty()) return;
        tick(now);
        while (active.size() >= MAX_ACTIVE) remove(active.removeFirst());
        Vector axis = direction.clone().normalize();
        double length = Math.min(256, distance) - START;
        Location center = from.clone().add(axis.clone().multiply(START + length / 2));
        center.setYaw(0);
        center.setPitch(0);
        // createEntity configures native display metadata without spawning/tracking an entity.
        BlockDisplay display = from.getWorld().createEntity(center, BlockDisplay.class);
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setBlock(Material.YELLOW_CONCRETE.createBlockData());
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        display.setTeleportDuration(0);
        display.setViewRange(8);
        // Disable midpoint-based frustum culling: any part of a long/up/down beam can be in view.
        display.setDisplayWidth(0);
        display.setDisplayHeight(0);
        display.setTransformationMatrix(transform(axis, length));
        Object remove = Native.BINDINGS.remove(display.getEntityId());
        // Register cleanup before sending so a partial transport failure cannot strand a beam.
        active.addLast(new Streak(now + LIFE_TICKS, viewers, remove));
        Object spawn = Native.BINDINGS.spawn(display);
        for (Player viewer : viewers) sender.accept(viewer, spawn);
    }

    static Matrix4f transform(Vector axis, double length) {
        Quaternionf rotation =
                new Quaternionf()
                        .rotationTo(
                                0,
                                0,
                                1,
                                (float) axis.getX(),
                                (float) axis.getY(),
                                (float) axis.getZ());
        return new Matrix4f()
                .rotate(rotation)
                .translate(-WIDTH / 2, -WIDTH / 2, (float) -length / 2)
                .scale(WIDTH, WIDTH, (float) length);
    }

    void tick(long now) {
        while (!active.isEmpty() && active.getFirst().until() <= now) remove(active.removeFirst());
    }

    void clear() {
        while (!active.isEmpty()) remove(active.removeFirst());
    }

    private void remove(Streak streak) {
        for (Player viewer : streak.viewers())
            if (viewer.isOnline()) sender.accept(viewer, streak.remove());
    }

    private static final class Native {
        static final Bindings BINDINGS = new Bindings();
    }

    /** Version-dependent packet access, isolated from geometry and lifecycle. Paper 26.2/26.3. */
    private static final class Bindings {
        private final Class<?> craftPlayer;
        private final Method entityHandle, playerHandle, type, entityData, values, send;
        private final Field connection;
        private final Constructor<?> add, metadata, remove, bundle;
        private final Object zero;

        Bindings() {
            try {
                craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
                Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
                Class<?> entityType = Class.forName("net.minecraft.world.entity.EntityType");
                Class<?> vector = Class.forName("net.minecraft.world.phys.Vec3");
                entityHandle =
                        Class.forName("org.bukkit.craftbukkit.entity.CraftEntity")
                                .getMethod("getHandle");
                playerHandle = craftPlayer.getMethod("getHandle");
                type = entity.getMethod("getType");
                entityData = entity.getMethod("getEntityData");
                values =
                        Class.forName("net.minecraft.network.syncher.SynchedEntityData")
                                .getMethod("getNonDefaultValues");
                zero = vector.getField("ZERO").get(null);
                add =
                        Class.forName(
                                        "net.minecraft.network.protocol.game.ClientboundAddEntityPacket")
                                .getConstructor(
                                        int.class,
                                        UUID.class,
                                        double.class,
                                        double.class,
                                        double.class,
                                        float.class,
                                        float.class,
                                        entityType,
                                        int.class,
                                        vector,
                                        double.class);
                metadata =
                        Class.forName(
                                        "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
                                .getConstructor(int.class, List.class);
                remove =
                        Class.forName(
                                        "net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket")
                                .getConstructor(int[].class);
                bundle =
                        Class.forName("net.minecraft.network.protocol.game.ClientboundBundlePacket")
                                .getConstructor(Iterable.class);
                connection =
                        Class.forName("net.minecraft.server.level.ServerPlayer")
                                .getField("connection");
                send =
                        Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl")
                                .getMethod(
                                        "send",
                                        Class.forName("net.minecraft.network.protocol.Packet"));
            } catch (ReflectiveOperationException error) {
                throw failure(error);
            }
        }

        Object spawn(BlockDisplay display) {
            try {
                Object handle = entityHandle.invoke(display);
                Location at = display.getLocation();
                Object spawn =
                        add.newInstance(
                                display.getEntityId(),
                                display.getUniqueId(),
                                at.getX(),
                                at.getY(),
                                at.getZ(),
                                0f,
                                0f,
                                type.invoke(handle),
                                0,
                                zero,
                                0d);
                Object data = values.invoke(entityData.invoke(handle));
                Object state =
                        metadata.newInstance(
                                display.getEntityId(), data == null ? List.of() : data);
                return bundle.newInstance(List.of(spawn, state));
            } catch (ReflectiveOperationException error) {
                throw failure(error);
            }
        }

        Object remove(int id) {
            try {
                return remove.newInstance((Object) new int[] {id});
            } catch (ReflectiveOperationException error) {
                throw failure(error);
            }
        }

        void send(Player viewer, Object packet) {
            if (!craftPlayer.isInstance(viewer)) return;
            try {
                Object listener = connection.get(playerHandle.invoke(viewer));
                if (listener != null) send.invoke(listener, packet);
            } catch (ReflectiveOperationException error) {
                throw failure(error);
            }
        }

        private static IllegalStateException failure(ReflectiveOperationException error) {
            return new IllegalStateException(
                    "CS tracers require Paper 26.2/26.3 display packets", error);
        }
    }
}
