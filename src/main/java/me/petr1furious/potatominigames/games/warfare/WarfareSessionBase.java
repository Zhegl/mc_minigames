package me.petr1furious.potatominigames.games.warfare;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.gadgets.FPVDroneGadget;

abstract class WarfareSessionBase implements GameSession {
    protected final SessionServices services;
    protected final WarfareConfig config;
    protected final LiftHandler liftHandler = new LiftHandler();
    protected final List<Component> summary = new ArrayList<>();
    private final FPVDroneGadget fpvDrone;

    protected int secondsUntilHudRefresh;

    WarfareSessionBase(SessionServices services, WarfareConfig config, FPVDroneGadget fpvDrone) {
        this.services = services;
        this.config = config;
        this.fpvDrone = fpvDrone;
    }

    /** With slime-world arenas this is the cloned game world, so it always matches the one being reset. */
    protected org.bukkit.World gameWorld() {
        return services.worlds().resolveWorld(config.gameWorld.get());
    }

    /** Call first from onStart. */
    protected void setupArena() {
        services.worlds().setupWorld(false);
        var world = gameWorld();
        if (config.enforceGameRules.get()) {
            services.arena().applyStandardGameRules(world);
        }
        if (config.arenaBorderEnabled.get()) {
            Location center = chooseArenaBorderCenter();
            double initialSize = Math.max(1.0, config.arenaBorderInitialSize.get());
            double finalSize = Math.max(1.0, Math.min(initialSize, config.arenaBorderFinalSize.get()));
            services.arena().applyBorder(world, center, initialSize, finalSize,
                    Math.max(0, config.arenaBorderTimeToFinalSeconds.get()));
        }
    }

    private Location chooseArenaBorderCenter() {
        var world = gameWorld();
        var gameCenter = config.gameCenter.get();
        double radius = Math.max(0.0, config.arenaBorderCenterRadius.get());
        double offsetX = 0.0;
        double offsetZ = 0.0;
        if (radius > 0.0) {
            double angle = Math.random() * Math.PI * 2.0;
            double distance = Math.sqrt(Math.random()) * radius;
            offsetX = Math.cos(angle) * distance;
            offsetZ = Math.sin(angle) * distance;
        }
        return new Location(world, gameCenter.getX() + offsetX, gameCenter.getY(), gameCenter.getZ() + offsetZ);
    }

    /** Follows the shrinking border when one is active. */
    protected Location currentGameCenter() {
        var world = gameWorld();
        if (config.arenaBorderEnabled.get()) {
            return world.getWorldBorder().getCenter();
        }
        return config.gameCenter.get().toLocation(world);
    }

    protected int currentGameRadius() {
        if (config.arenaBorderEnabled.get()) {
            double liveBorderRadius = gameWorld().getWorldBorder().getSize() / 2.0 - 1.0;
            return (int) Math.max(1.0, Math.min(config.gameRadius.get(), liveBorderRadius));
        }
        return Math.max(1, config.gameRadius.get());
    }

    protected void prepareAlivePlayer(Player player, Location location, boolean keepInvisibility) {
        Location target = location.clone();
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        services.playerState().resetForRound(player, GameMode.SURVIVAL);
        player.teleport(target);
        if (keepInvisibility) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 1, false, false));
        }
        if (config.enableGameInventory.get()) {
            var kit = config.gameInventory();
            if (kit != null) {
                player.getInventory().setContents(kit.clone());
            }
        }
    }

    /** Survival or adventure in the game world; deliberately no separate alive/eliminated state. */
    protected boolean isPlayerAlive(Player player) {
        GameMode mode = player.getGameMode();
        return player.isOnline()
                && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && player.getWorld().equals(gameWorld());
    }

    @Override
    public boolean isInGame(Player player) {
        return isPlayerAlive(player);
    }

    protected List<Player> alivePlayers() {
        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerAlive(player)) {
                result.add(player);
            }
        }
        return result;
    }

    protected static String distanceRange(int distance, int granularity) {
        int lowerBound = (distance / granularity) * granularity;
        int upperBound = lowerBound + granularity;
        return lowerBound + "-" + upperBound;
    }

    protected List<Component> buildDistanceLines(Player viewer) {
        int granularity = Math.max(1, config.distanceGranularity.get());
        record Row(int distance, Component line) {
        }
        List<Row> rows = new ArrayList<>();
        for (Player target : getTrackedPlayers(viewer)) {
            if (!viewer.getWorld().equals(target.getWorld())) {
                continue;
            }
            double distance = viewer.getLocation().distance(target.getLocation());
            int roundedDistance = ((int) (distance + 0.5) / granularity) * granularity;
            rows.add(new Row(roundedDistance, Component.text()
                    .append(Component.text(distanceRange(roundedDistance, granularity), NamedTextColor.GOLD))
                    .append(Component.text(" " + targetLabel(viewer, target), NamedTextColor.AQUA))
                    .build()));
        }
        rows.sort((a, b) -> Integer.compare(b.distance(), a.distance()));
        List<Component> lines = new ArrayList<>();
        for (Row row : rows) {
            lines.add(row.line());
        }
        return lines;
    }

    private String targetLabel(Player viewer, Player target) {
        return shouldHideTrackedPlayerNames(viewer) ? "Enemy" : target.getName();
    }

    protected Component refreshLine() {
        return Component.text()
                .append(Component.text("Refresh: ", NamedTextColor.GRAY))
                .append(Component.text(Math.max(0, secondsUntilHudRefresh) + "s", NamedTextColor.GREEN))
                .build();
    }

    // Distances refresh only every interval (stale positions are intended); the
    // "Refresh" line ticks every second from the cached content.
    private final java.util.Map<java.util.UUID, List<Component>> cachedHudLines = new java.util.HashMap<>();

    @Override
    public void onSecondTick(long secondsElapsed) {
        secondsUntilHudRefresh--;
        if (secondsUntilHudRefresh <= 0) {
            secondsUntilHudRefresh = Math.max(1, config.distanceUpdateIntervalSeconds.get());
            updateHud();
        } else {
            renderAll(false);
        }
    }

    protected void startHud() {
        secondsUntilHudRefresh = Math.max(1, config.distanceUpdateIntervalSeconds.get());
        updateHud();
    }

    protected void updateHud() {
        renderAll(true);
    }

    private void renderAll(boolean recompute) {
        for (Player viewer : services.participants().onlinePlayers()) {
            if (recompute || !cachedHudLines.containsKey(viewer.getUniqueId())) {
                cachedHudLines.put(viewer.getUniqueId(), computeHudLines(viewer));
            }
            List<Component> lines = new ArrayList<>(cachedHudLines.get(viewer.getUniqueId()));
            if (includeRefreshLine(viewer)) {
                lines.add(refreshLine());
            }
            services.sidebar().update(viewer, hudTitle(viewer), lines);
        }
    }

    protected abstract List<Component> computeHudLines(Player viewer);

    protected abstract Component hudTitle(Player viewer);

    protected boolean includeRefreshLine(Player viewer) {
        return true;
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.enableLifts.get()) {
            return;
        }
        Player player = event.getPlayer();
        if (!services.participants().isParticipant(player) || fpvDrone.isPlayerInDroneMode(player)) {
            return;
        }
        liftHandler.handleLift(player, config.liftMaterial.get());
    }

    @Override
    public void onEnd(EndReason reason) {
        liftHandler.clear();
    }

    @Override
    public List<Component> summary() {
        return List.copyOf(summary);
    }

}
