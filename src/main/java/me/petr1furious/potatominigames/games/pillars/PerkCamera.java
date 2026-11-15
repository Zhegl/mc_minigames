package me.petr1furious.potatominigames.games.pillars;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Changes only the client camera: the survival body stays vulnerable at its real position. */
final class PerkCamera {
    private final Constructor<?> packet;
    private final Method handle, send;
    private final Field connection;
    PerkCamera() {
        try {
            Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
            handle = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity").getMethod("getHandle");
            packet = Class.forName("net.minecraft.network.protocol.game.ClientboundSetCameraPacket").getConstructor(entity);
            connection = Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection");
            send = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl")
                    .getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Pillars remote camera unavailable", error); }
    }
    void point(Player viewer, Entity target) {
        if (!viewer.isOnline()) return;
        try { send.invoke(connection.get(handle.invoke(viewer)), packet.newInstance(handle.invoke(target))); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot switch Pillars camera", error); }
    }
}
