package me.petr1furious.potatominigames.games.tanks;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** One battle map with per-viewer contacts supplied by the session's visibility policy. */
final class TanksRadar extends MapRenderer {
    record Blip(double x, double z, boolean ally, boolean self, float yaw) {}

    private final MapView view;
    private final Function<Player, List<Blip>> contacts;
    private final byte[] terrain = new byte[128 * 128];
    private final Set<UUID> initialized = new HashSet<>();
    private final Map<UUID, List<Integer>> previous = new HashMap<>();
    private boolean closed;

    TanksRadar(World world, Function<Player, List<Blip>> blips) {
        super(true);
        contacts = blips;
        // The arena is deterministic: cache its exact top-down geometry without loading chunks.
        for (int pz = 0; pz < 128; pz++)
            for (int px = 0; px < 128; px++) {
                int x = px - 64, z = pz - 64;
                byte color;
                if (Math.abs(z) > TanksArena.HALF_Z) color = MapPalette.DARK_GRAY;
                else {
                    Material above = TanksArena.materialAt(x, 65, z);
                    Material floor = TanksArena.materialAt(x, 64, z);
                    color = color(above != Material.AIR ? above : floor);
                    if (above == Material.AIR
                            && (Math.floorMod(x, 16) == 0 || Math.floorMod(z, 16) == 0)
                            && floor != Material.RED_CONCRETE
                            && floor != Material.BLUE_CONCRETE) color = MapPalette.GRAY_1;
                }
                terrain[px + pz * 128] = color;
            }
        view = Bukkit.createMap(world);
        for (MapRenderer renderer : List.copyOf(view.getRenderers())) view.removeRenderer(renderer);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        view.setCenterX(0);
        view.setCenterZ(0);
        view.setScale(MapView.Scale.CLOSEST);
        view.addRenderer(this);
    }

    ItemStack item(Player viewer) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.displayName(I18n.render(I18n.tr("tanks.radar"), viewer));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (closed) return;
        UUID id = player.getUniqueId();
        if (initialized.add(id)) {
            for (int y = 0; y < 128; y++)
                for (int x = 0; x < 128; x++) canvas.setPixel(x, y, terrain[x + y * 128]);
        } else {
            for (int old : previous.getOrDefault(id, List.of()))
                canvas.setPixel(old % 128, old / 128, terrain[old]);
        }
        List<Integer> painted = new ArrayList<>();
        List<Blip> blips = contacts.apply(player);
        if (blips != null) {
            for (Blip blip : blips) if (!blip.self) arrow(canvas, painted, blip);
            for (Blip blip : blips) if (blip.self) arrow(canvas, painted, blip);
        }
        previous.put(id, painted);
    }

    private static void arrow(MapCanvas canvas, List<Integer> painted, Blip blip) {
        if (!Double.isFinite(blip.x)
                || !Double.isFinite(blip.z)
                || !Float.isFinite(blip.yaw)
                || Math.abs(blip.x) > TanksArena.HALF_X
                || Math.abs(blip.z) > TanksArena.HALF_Z) return;
        byte color =
                blip.self ? MapPalette.WHITE : blip.ally ? MapPalette.PALE_BLUE : MapPalette.RED;
        int x = pixel(blip.x), y = pixel(blip.z);
        double a = Math.toRadians(blip.yaw), dx = -Math.sin(a), dy = Math.cos(a);
        for (int i = 0; i <= 3; i++)
            dot(canvas, painted, x + (int) Math.round(dx * i), y + (int) Math.round(dy * i), color);
        for (int side : new int[] {-1, 1})
            dot(
                    canvas,
                    painted,
                    x + (int) Math.round(dx + side * dy),
                    y + (int) Math.round(dy - side * dx),
                    color);
    }

    private static void dot(MapCanvas canvas, List<Integer> painted, int x, int y, byte color) {
        if (x < 0 || x >= 128 || y < 0 || y >= 128) return;
        canvas.setPixel(x, y, color);
        painted.add(x + y * 128);
    }

    static int pixel(double coordinate) {
        return (int) Math.clamp(Math.floor(coordinate + 64), 0, 127);
    }

    private static byte color(Material material) {
        return switch (material) {
            case RED_CONCRETE, RED_WOOL -> MapPalette.RED;
            case BLUE_CONCRETE, BLUE_WOOL -> MapPalette.BLUE;
            case SPRUCE_LOG, SPRUCE_LEAVES, MOSS_BLOCK -> MapPalette.DARK_GREEN;
            case GRASS_BLOCK -> MapPalette.LIGHT_GREEN;
            case DIRT_PATH, COARSE_DIRT -> MapPalette.LIGHT_BROWN;
            case GRAVEL -> MapPalette.LIGHT_GRAY;
            case POLISHED_DEEPSLATE -> MapPalette.DARK_GRAY;
            case SMOOTH_STONE, POLISHED_ANDESITE -> MapPalette.GRAY_1;
            default -> MapPalette.GRAY_2;
        };
    }

    void clear() {
        if (closed) return;
        closed = true;
        view.removeRenderer(this);
        initialized.clear();
        previous.clear();
    }
}
