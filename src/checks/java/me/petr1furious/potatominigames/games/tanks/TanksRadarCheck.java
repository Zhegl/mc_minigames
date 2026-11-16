package me.petr1furious.potatominigames.games.tanks;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Contextual map rendering does not expose blips omitted by the session visibility policy. */
public final class TanksRadarCheck {
    public static void main(String[] args) throws Exception {
        List<MapRenderer> renderers = new ArrayList<>();
        MapView view =
                (MapView)
                        Proxy.newProxyInstance(
                                MapView.class.getClassLoader(),
                                new Class<?>[] {MapView.class},
                                (object, method, values) ->
                                        switch (method.getName()) {
                                            case "getRenderers" -> List.copyOf(renderers);
                                            case "addRenderer" -> {
                                                renderers.add((MapRenderer) values[0]);
                                                yield null;
                                            }
                                            case "removeRenderer" -> renderers.remove(values[0]);
                                            default -> null;
                                        });
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(
                null,
                Proxy.newProxyInstance(
                        Server.class.getClassLoader(),
                        new Class<?>[] {Server.class},
                        (object, method, values) ->
                                method.getName().equals("createMap") ? view : null));
        World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (object, method, values) -> {
                                    throw new AssertionError(
                                            "radar raster never reads world chunks");
                                });
        Player red = player(1), blue = player(2);
        boolean[] spotted = {true};
        TanksRadar radar =
                new TanksRadar(
                        world,
                        player ->
                                player == red && spotted[0]
                                        ? List.of(new TanksRadar.Blip(20, 0, false, false, 0))
                                        : List.of());
        check(radar.isContextual(), "one canvas per viewer");
        byte[] visible = new byte[128 * 128], hidden = new byte[128 * 128];
        radar.render(view, canvas(visible), red);
        radar.render(view, canvas(hidden), blue);
        int enemy = TanksRadar.pixel(20) + 128 * TanksRadar.pixel(0);
        check(visible[enemy] == MapPalette.RED, "authorized enemy appears red");
        check(hidden[enemy] != MapPalette.RED, "other viewer has no enemy data");
        check(
                visible[TanksRadar.pixel(20) + 128 * TanksRadar.pixel(3)] == MapPalette.RED,
                "yaw zero points down the north-up map");
        spotted[0] = false;
        radar.render(view, canvas(visible), red);
        check(visible[enemy] == hidden[enemy], "lost contacts restore cached terrain");
        check(TanksRadar.pixel(-64) == 0 && TanksRadar.pixel(63) == 127, "world/map edges align");
        check(TanksRadar.pixel(0) == 64, "world center aligns");
        radar.clear();
        check(renderers.isEmpty(), "battle cleanup removes the renderer");
        System.out.println("Tanks radar privacy checks passed");
    }

    private static Player player(long id) {
        return (Player)
                Proxy.newProxyInstance(
                        Player.class.getClassLoader(),
                        new Class<?>[] {Player.class},
                        (object, method, args) ->
                                method.getName().equals("getUniqueId") ? new UUID(0, id) : null);
    }

    private static MapCanvas canvas(byte[] pixels) {
        return (MapCanvas)
                Proxy.newProxyInstance(
                        MapCanvas.class.getClassLoader(),
                        new Class<?>[] {MapCanvas.class},
                        (object, method, args) -> {
                            if (method.getName().equals("setPixel"))
                                pixels[(int) args[0] + 128 * (int) args[1]] = (byte) args[2];
                            else throw new AssertionError("Canvas: " + method.getName());
                            return null;
                        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
