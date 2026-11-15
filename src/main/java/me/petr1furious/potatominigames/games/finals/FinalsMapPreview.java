package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsMapData.*;

import me.petr1furious.potatominigames.core.command.GameCommands;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Private editor overlays: never replace blocks or persist entities in an imported map. */
public final class FinalsMapPreview implements Listener {
    private static final double RANGE_SQUARED = 64 * 64;
    private final JavaPlugin plugin;
    private final FinalsMaps maps;
    private final Map<UUID, View> views = new LinkedHashMap<>();
    private BukkitTask ticker;

    record Marker(String key, int number, Point point, NamedTextColor color, boolean spawn) {}

    private static final class View {
        final Player player;
        final String name, world;
        final Map<Marker, TextDisplay> displays = new LinkedHashMap<>();
        Locale locale;

        View(Player player, String name, String world) {
            this.player = player;
            this.name = name;
            this.world = world;
        }

        void clear() {
            displays.values().forEach(TextDisplay::remove);
            displays.clear();
        }
    }

    public FinalsMapPreview(JavaPlugin plugin, FinalsMaps maps) {
        this.plugin = plugin;
        this.maps = maps;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void show(Player player, String name) {
        Draft draft = maps.previewDraft(name);
        if (!player.hasPermission(GameCommands.ADMIN_PERMISSION)
                || !player.getWorld().getName().equals(draft.world))
            throw FinalsMaps.failure("preview_world");
        hide(player);
        View view = new View(player, name, draft.world);
        views.put(player.getUniqueId(), view);
        try {
            refresh(view, draft);
            if (ticker == null)
                ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
        } catch (RuntimeException error) {
            hide(player);
            throw error;
        }
    }

    public void hide(Player player) {
        View view = views.remove(player.getUniqueId());
        if (view != null) view.clear();
        if (views.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void tick() {
        for (View view : List.copyOf(views.values())) {
            if (!view.player.isOnline()
                    || !view.player.hasPermission(GameCommands.ADMIN_PERMISSION)
                    || !view.player.getWorld().getName().equals(view.world)) {
                hide(view.player);
                continue;
            }
            try {
                refresh(view, maps.previewDraft(view.name));
            } catch (IllegalArgumentException invalidMap) {
                hide(view.player);
            }
        }
    }

    private void refresh(View view, Draft draft) {
        Player player = view.player;
        if (!Objects.equals(view.locale, player.locale())) {
            view.clear();
            view.locale = player.locale();
        }
        Location eye = player.getLocation();
        List<Marker> visible =
                markers(draft).stream()
                        .filter(marker ->
                                marker.point().at(player.getWorld()).distanceSquared(eye)
                                        <= RANGE_SQUARED)
                        .toList();
        view.displays
                .entrySet()
                .removeIf(entry -> {
                    if (visible.contains(entry.getKey()) && entry.getValue().isValid())
                        return false;
                    entry.getValue().remove();
                    return true;
                });
        for (Marker marker : visible) {
            view.displays.computeIfAbsent(
                    marker,
                    key -> {
                        TextDisplay display =
                                player.getWorld()
                                        .spawn(
                                                key.point().at(player.getWorld()).add(0, 1.7, 0),
                                                TextDisplay.class,
                                                entity -> {
                                                    // Apply privacy before the entity enters the
                                                    // world's tracking system.
                                                    entity.setVisibleByDefault(false);
                                                    entity.setPersistent(false);
                                                    entity.setGravity(false);
                                                    entity.setInvulnerable(true);
                                                    entity.setBillboard(Display.Billboard.CENTER);
                                                    entity.setShadowed(true);
                                                    entity.setSeeThrough(true);
                                                    entity.setViewRange(1f);
                                                    entity.text(
                                                            I18n.render(
                                                                    I18n.tr(
                                                                                    "finals.map.preview."
                                                                                            + key
                                                                                                    .key(),
                                                                                    key.number())
                                                                            .color(key.color()),
                                                                    player));
                                                });
                        player.showEntity(plugin, display);
                        return display;
                    });
            outline(player, marker);
        }
    }

    static List<Marker> markers(Draft draft) {
        List<Marker> result = new ArrayList<>();
        append(result, draft.vaults, "vault", NamedTextColor.GREEN, false);
        append(result, draft.cashouts, "cashout", NamedTextColor.GOLD, false);
        append(result, draft.red, "red_spawn", NamedTextColor.RED, true);
        append(result, draft.blue, "blue_spawn", NamedTextColor.BLUE, true);
        for (int i = 0; i < draft.jumpPads.size(); i++) {
            var pad = draft.jumpPads.get(i);
            result.add(new Marker(pad.targeted() ? "pad_targeted" : "pad_up", i + 1,
                    pad.origin(), NamedTextColor.AQUA, true));
            if (pad.target() != null)
                result.add(new Marker("pad_target", i + 1, pad.target(), NamedTextColor.LIGHT_PURPLE, true));
        }
        return List.copyOf(result);
    }

    private static void append(
            List<Marker> markers,
            List<Point> points,
            String key,
            NamedTextColor color,
            boolean spawn) {
        for (int i = 0; i < Math.min(16, points.size()); i++) {
            Point point = points.get(i);
            if (point != null) markers.add(new Marker(key, i + 1, point, color, spawn));
        }
    }

    private static void outline(Player player, Marker marker) {
        Location origin = marker.point().at(player.getWorld()).add(0, .08, 0);
        Particle.DustOptions dust =
                new Particle.DustOptions(Color.fromRGB(marker.color().value()), 1.2f);
        for (int i = 0; i < 4; i++) {
            double offset = -.45 + i * .3;
            particle(player, origin, offset, -.45, dust);
            particle(player, origin, .45, offset, dust);
            particle(player, origin, -offset, .45, dust);
            particle(player, origin, -.45, -offset, dust);
        }
        if (!marker.spawn()) return;
        double yaw = Math.toRadians(marker.point().yaw());
        double dx = -Math.sin(yaw), dz = Math.cos(yaw);
        for (int i = 1; i <= 4; i++) particle(player, origin, dx * i * .3, dz * i * .3, dust);
        particle(player, origin, dx * .9 + dz * .25, dz * .9 - dx * .25, dust);
        particle(player, origin, dx * .9 - dz * .25, dz * .9 + dx * .25, dust);
    }

    private static void particle(
            Player player, Location origin, double x, double z, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, origin.clone().add(x, 0, z), 1, 0, 0, 0, 0, dust);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        hide(event.getPlayer());
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) close();
    }

    public void close() {
        views.values().forEach(View::clear);
        views.clear();
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        HandlerList.unregisterAll(this);
    }
}
