package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Color;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Session-owned, private objective outlines and ally name markers. */
final class FinalsHighlights {
    private final JavaPlugin plugin;
    private final FinalsTeammateGlow teammateGlow;
    private final Map<UUID, View> views = new LinkedHashMap<>();

    record Marker(
            String key, Location location, Component label, NamedTextColor color, Material block) {}

    private record Placement(Location label, Location block, Component text, boolean projected) {}

    private record Overlay(TextDisplay label, BlockDisplay outline, boolean projected) {
        boolean valid() {
            return label.isValid() && (outline == null || outline.isValid());
        }

        void remove() {
            label.remove();
            if (outline != null) outline.remove();
        }
    }

    private static final class View {
        final Player player;
        final Map<String, Overlay> overlays = new LinkedHashMap<>();

        View(Player player) {
            this.player = player;
        }

        void clear() {
            overlays.values().forEach(Overlay::remove);
            overlays.clear();
        }
    }

    FinalsHighlights(JavaPlugin plugin) {
        this.plugin = plugin;
        teammateGlow = new FinalsTeammateGlow(plugin);
    }

    void update(
            FinalsMapArena arena,
            FinalsState.Phase phase,
            int activeStation,
            Collection<Player> players,
            Map<UUID, Integer> teams,
            Function<Player, Location> teammateLocation) {
        update(arena, phase, activeStation, -1, -1, players, teams, teammateLocation);
    }

    void update(FinalsMapArena arena, FinalsState.Phase phase, int activeStation,
            long vaultSeconds, long cashoutSeconds, Collection<Player> players,
            Map<UUID, Integer> teams, Function<Player, Location> teammateLocation) {
        update(arena, phase, activeStation, vaultSeconds, cashoutSeconds, players, teams,
                teammateLocation, ignored -> false);
    }

    void update(FinalsMapArena arena, FinalsState.Phase phase, int activeStation,
            long vaultSeconds, long cashoutSeconds, Collection<Player> players,
            Map<UUID, Integer> teams, Function<Player, Location> teammateLocation,
            Predicate<Player> downed) {
        update(arena, phase, activeStation, vaultSeconds, cashoutSeconds, players, teams,
                teammateLocation, downed, null, null);
    }

    void update(FinalsMapArena arena, FinalsState.Phase phase, int activeStation,
            long vaultSeconds, long cashoutSeconds, Collection<Player> players,
            Map<UUID, Integer> teams, Function<Player, Location> teammateLocation,
            Predicate<Player> downed, Location cashbox, UUID carrier) {
        teammateGlow.update(arena.world(),
                phase == FinalsState.Phase.FINISHED ? List.of() : players, teams);
        Set<UUID> present = new HashSet<>();
        for (Player player : players) {
            List<Marker> markers = new ArrayList<>(markers(arena, phase, activeStation, vaultSeconds, cashoutSeconds,
                    player, players, teams, teammateLocation, downed));
            if (markers.isEmpty()) continue;
            Marker box = cashboxMarker(phase, cashbox);
            if (box != null && arena.world().equals(cashbox.getWorld())
                    && !player.getUniqueId().equals(carrier)) markers.add(box);
            UUID id = player.getUniqueId();
            present.add(id);
            View view = views.get(id);
            if (view != null && view.player != player) {
                view.clear();
                views.remove(id);
            }
            view = views.computeIfAbsent(id, ignored -> new View(player));
            refresh(view, markers);
        }
        views.entrySet()
                .removeIf(entry -> {
                    if (present.contains(entry.getKey())) return false;
                    entry.getValue().clear();
                    return true;
                });
    }

    static Marker cashboxMarker(FinalsState.Phase phase, Location point) {
        if (point == null || phase != FinalsState.Phase.CASHBOX_AVAILABLE
                && phase != FinalsState.Phase.CARRIED) return null;
        return new Marker("cashbox", point.clone(), I18n.tr("finals.highlight.cashbox"),
                NamedTextColor.GOLD, Material.GOLD_BLOCK);
    }

    static List<Marker> markers(
            FinalsMapArena arena,
            FinalsState.Phase phase,
            int activeStation,
            Player viewer,
            Collection<Player> players,
            Map<UUID, Integer> teams,
            Function<Player, Location> teammateLocation) {
        return markers(arena, phase, activeStation, -1, -1, viewer, players, teams, teammateLocation);
    }

    static List<Marker> markers(FinalsMapArena arena, FinalsState.Phase phase, int activeStation,
            long vaultSeconds, long cashoutSeconds, Player viewer, Collection<Player> players,
            Map<UUID, Integer> teams, Function<Player, Location> teammateLocation) {
        return markers(arena, phase, activeStation, vaultSeconds, cashoutSeconds, viewer,
                players, teams, teammateLocation, ignored -> false);
    }

    static List<Marker> markers(FinalsMapArena arena, FinalsState.Phase phase, int activeStation,
            long vaultSeconds, long cashoutSeconds, Player viewer, Collection<Player> players,
            Map<UUID, Integer> teams, Function<Player, Location> teammateLocation,
            Predicate<Player> downed) {
        Integer team = teams.get(viewer.getUniqueId());
        if (phase == FinalsState.Phase.FINISHED
                || team == null
                || !viewer.isOnline()
                || !arena.world().equals(viewer.getWorld())) return List.of();
        List<Marker> result = new ArrayList<>();
        if (phase == null
                || phase == FinalsState.Phase.VAULT_CLOSED
                || phase == FinalsState.Phase.VAULT_OPENING) {
            result.add(
                    new Marker(
                            "vault",
                            arena.vault().clone(),
                            vaultSeconds < 0 ? I18n.tr("finals.highlight.vault")
                                    : I18n.tr("finals.highlight.vault_time", vaultSeconds),
                            NamedTextColor.GREEN,
                            Material.EMERALD_BLOCK));
        }
        for (int station = 0; station < 2; station++) {
            boolean box = phase == FinalsState.Phase.CASHBOX_AVAILABLE
                    || phase == FinalsState.Phase.CARRIED;
            if (!box && !(phase == FinalsState.Phase.CASHOUT && station == activeStation)) continue;
            result.add(
                    new Marker(
                            "cashout-" + station,
                            arena.station(station).clone(),
                            cashoutSeconds < 0
                                    ? I18n.tr("finals.highlight.cashout", station == 0 ? "A" : "B")
                                    : I18n.tr("finals.highlight.cashout_time", station == 0 ? "A" : "B", cashoutSeconds),
                            NamedTextColor.GOLD,
                            Material.GOLD_BLOCK));
        }
        for (Player target : players) {
            UUID id = target.getUniqueId();
            if (id.equals(viewer.getUniqueId())
                    || !team.equals(teams.get(id))
                    || !target.isOnline()
                    || !arena.world().equals(target.getWorld())) continue;
            Location point = teammateLocation.apply(target);
            if (point == null || !arena.world().equals(point.getWorld())) continue;
            result.add(
                    new Marker(
                            "teammate-" + id,
                            point.clone(),
                            I18n.tr(downed.test(target) ? "finals.highlight.downed"
                                    : "finals.highlight.teammate", target.getName()),
                            team == 0 ? NamedTextColor.RED : NamedTextColor.BLUE,
                            null));
        }
        return List.copyOf(result);
    }

    private void refresh(View view, List<Marker> markers) {
        Set<String> wanted = new HashSet<>();
        List<Location> projectedLabels = new ArrayList<>();
        for (Marker marker : markers) {
            wanted.add(marker.key());
            Overlay overlay = view.overlays.get(marker.key());
            if (overlay != null && !overlay.valid()) {
                overlay.remove();
                view.overlays.remove(marker.key());
                overlay = null;
            }
            Placement placement = separate(placement(view.player, marker), projectedLabels);
            Component text = I18n.render(placement.text().color(marker.color()), view.player);
            if (overlay == null) {
                overlay = create(view.player, marker, placement, text);
                view.overlays.put(marker.key(), overlay);
            } else {
                Location position = placement.label();
                if (!overlay.label().getLocation().equals(position))
                    overlay.label().teleport(position);
                if (!overlay.label().text().equals(text)) overlay.label().text(text);
                if (overlay.outline() != null) {
                    Location block = placement.block();
                    if (!overlay.outline().getLocation().equals(block))
                        overlay.outline().teleport(block);
                    if (overlay.projected() != placement.projected()) {
                        overlay.outline().setTransformationMatrix(outlineTransform(marker, placement.projected()));
                        overlay = new Overlay(overlay.label(), overlay.outline(), placement.projected());
                        view.overlays.put(marker.key(), overlay);
                    }
                }
            }
            // Paper clears private visibility when chunk tracking ends. The entity can be
            // valid again before this refresh, so validity alone does not mean it is visible.
            if (!view.player.canSee(overlay.label())) view.player.showEntity(plugin, overlay.label());
            if (overlay.outline() != null && !view.player.canSee(overlay.outline()))
                view.player.showEntity(plugin, overlay.outline());
        }
        view.overlays
                .entrySet()
                .removeIf(entry -> {
                    if (wanted.contains(entry.getKey())) return false;
                    entry.getValue().remove();
                    return true;
                });
    }

    private Overlay create(Player viewer, Marker marker, Placement placement, Component text) {
        TextDisplay label =
                marker.location()
                        .getWorld()
                        .spawn(
                                placement.label(),
                                TextDisplay.class,
                                entity -> {
                                    configure(entity);
                                    entity.setBillboard(Display.Billboard.CENTER);
                                    entity.setSeeThrough(true);
                                    entity.setShadowed(true);
                                    entity.text(text);
                                });
        BlockDisplay outline = null;
        try {
            if (marker.block() != null) {
                outline =
                        marker.location()
                                .getWorld()
                                .spawn(
                                        placement.block(),
                                        BlockDisplay.class,
                                        entity -> {
                                            configure(entity);
                                            entity.setBlock(marker.block().createBlockData());
                                            entity.setGlowing(true);
                                            entity.setGlowColorOverride(
                                                    Color.fromRGB(marker.color().value()));
                                            entity.setTransformationMatrix(outlineTransform(marker, placement.projected()));
                                        });
            }
            viewer.showEntity(plugin, label);
            if (outline != null) viewer.showEntity(plugin, outline);
            return new Overlay(label, outline, placement.projected());
        } catch (RuntimeException error) {
            label.remove();
            if (outline != null) outline.remove();
            throw error;
        }
    }

    private static void configure(Display display) {
        // Apply privacy inside the spawn callback, before the entity is tracked.
        display.setVisibleByDefault(false);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setViewRange(4f);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setTeleportDuration(2);
    }

    private static Location labelPosition(Marker marker) {
        return marker.location().clone().add(0, marker.block() == null ? 2.4 : 2, 0);
    }

    private static Placement placement(Player viewer, Marker marker) {
        Location eye = viewer.getEyeLocation();
        Location label = labelPosition(marker);
        Location block = marker.block() == null ? null : blockPosition(marker);
        int clientDistance = viewer.getClientViewDistance();
        int sendDistance = viewer.getSendViewDistance();
        int chunks = Math.min(clientDistance > 0 ? clientDistance : 2, sendDistance > 0 ? sendDistance : 2);
        double radius = Math.min(24, Math.max(8, (chunks - 1) * 16d));
        Vector direction = label.toVector().subtract(eye.toVector());
        double distance = direction.length();
        if (distance <= radius && viewer.isChunkSent(Chunk.getChunkKey(label))
                && (block == null || viewer.isChunkSent(Chunk.getChunkKey(block))))
            return new Placement(label, block, marker.label(), false);

        // A nearby private proxy remains tracked even when the objective's chunk is not sent.
        // Never load distant chunks just to display a direction marker.
        boolean cashout = marker.key().startsWith("cashout-");
        // Keep station letters readable instead of shrinking with the real objective distance.
        if (cashout || marker.key().equals("cashbox")) radius = Math.min(radius, 8);
        radius = Math.min(radius, distance);
        if (distance > 0) direction.multiply(1 / distance);
        Location projected = eye.clone().add(direction.clone().multiply(radius));
        while (radius > .5 && !viewer.isChunkSent(Chunk.getChunkKey(projected))) {
            radius *= .5;
            projected = eye.clone().add(direction.clone().multiply(radius));
        }
        if (!viewer.isChunkSent(Chunk.getChunkKey(projected))) projected = eye.clone();
        projected.setYaw(0);
        projected.setPitch(0);
        long actualDistance = Math.round(viewer.getLocation().distance(marker.location()));
        Component caption = cashout
                ? I18n.tr("finals.highlight.cashout_distant", marker.key().equals("cashout-0") ? "A" : "B",
                        marker.label(), actualDistance)
                : I18n.tr("finals.highlight.distant", marker.label(), actualDistance);
        return new Placement(projected, block == null ? null : projected.clone().add(0, -.5, 0),
                caption, true);
    }

    private static Placement separate(Placement placement, List<Location> occupied) {
        if (!placement.projected()) return placement;
        Location label = placement.label();
        double rise = 0;
        // Several far objectives can lie on the same bearing. Stack their small proxies/labels.
        for (int attempt = 0; attempt < occupied.size() * 2; attempt++) {
            boolean overlap = false;
            for (Location other : occupied) {
                if (label.distanceSquared(other) < .75 * .75) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) break;
            label.add(0, .8, 0);
            rise += .8;
        }
        occupied.add(label);
        Location block = placement.block();
        if (block != null && rise != 0) block.add(0, rise, 0);
        return placement;
    }

    private static Matrix4f outlineTransform(boolean projected) {
        return projected ? new Matrix4f().translation(-.125f, 0, -.125f).scale(.25f)
                : new Matrix4f().translation(-.01f, -.01f, -.01f).scale(1.02f);
    }

    private static Matrix4f outlineTransform(Marker marker, boolean projected) {
        if (!projected && marker.key().equals("cashbox"))
            return new Matrix4f().translation(-.15f, 0, -.15f).scale(.3f);
        return outlineTransform(projected);
    }

    private static Location blockPosition(Marker marker) {
        Location point = marker.location();
        if (marker.key().equals("cashbox")) return point.clone();
        if (marker.key().startsWith("cashout-"))
            return new Location(point.getWorld(), point.getX() - .5, point.getY(), point.getZ() - .5);
        return new Location(point.getWorld(), point.getBlockX(), point.getY(), point.getBlockZ());
    }

    void hideTeammate(UUID id) {
        for (View view : views.values()) {
            Overlay overlay = view.overlays.remove("teammate-" + id);
            if (overlay != null) overlay.remove();
        }
    }

    void remove(UUID id) {
        teammateGlow.remove(id);
        View view = views.remove(id);
        if (view != null) view.clear();
        hideTeammate(id);
    }

    void clear() {
        teammateGlow.clear();
        views.values().forEach(View::clear);
        views.clear();
    }
}
