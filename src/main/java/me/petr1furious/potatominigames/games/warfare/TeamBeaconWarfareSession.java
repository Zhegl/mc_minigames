package me.petr1furious.potatominigames.games.warfare;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.player.Participant;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.core.world.Locations;
import me.petr1furious.potatominigames.gadgets.FPVDroneGadget;

final class TeamBeaconWarfareSession extends WarfareSessionBase {
    // Blocks kept between the border and an intact beacon or, when it comes in, an alive player.
    private static final double BORDER_MARGIN = 8.0;
    // Glow display inset so its faces sit inside the wool instead of z-fighting with it.
    private static final float GLOW_INSET = 0.02f;

    private static final class PendingRespawn {
        final TeamColor team;
        final long respawnAtMillis;
        final BukkitTask task;

        PendingRespawn(TeamColor team, long respawnAtMillis, BukkitTask task) {
            this.team = team;
            this.respawnAtMillis = respawnAtMillis;
            this.task = task;
        }
    }

    private static final class BeaconState {
        final TeamColor team;
        final Location location;
        final BlockState replacedBlockState;
        final BossBar bossBar;
        int health;
        boolean destroyed;
        BlockDisplay glow;

        BeaconState(TeamColor team, Location location, BlockState replacedBlockState, BossBar bossBar, int health) {
            this.team = team;
            this.location = location;
            this.replacedBlockState = replacedBlockState;
            this.bossBar = bossBar;
            this.health = health;
        }
    }

    private final Map<TeamColor, BeaconState> beacons = new EnumMap<>(TeamColor.class);
    private final Map<UUID, PendingRespawn> pendingRespawns = new HashMap<>();
    private final Map<UUID, Long> spawnProtectionExpirations = new HashMap<>();
    private boolean ended = false;
    private boolean borderActive = false;
    private double borderShrinkPerSecond;

    TeamBeaconWarfareSession(SessionServices services, WarfareConfig config, FPVDroneGadget fpvDrone) {
        super(services, config, fpvDrone);
    }

    private TeamColor teamOf(Player player) {
        return services.participants().find(player.getUniqueId()).map(Participant::team).orElse(null);
    }

    @Override
    public void onStart() throws CommandFailure {
        List<TeamColor> participatingTeams = services.teams().activeTeams();
        // A test game may be one team alone.
        if (participatingTeams.size() < (services.isTest() ? 1 : 2)) {
            throw new CommandFailure("Team beacon mode needs players on at least two teams.");
        }
        // Beacon locations need the arena world to exist first.
        setupArena();

        try {
            for (TeamColor team : participatingTeams) {
                Location beaconLocation = map != null ? map.beacon(team, gameWorld()) : config.getBeacon(team);
                if (beaconLocation == null || beaconLocation.getWorld() == null) {
                    throw new CommandFailure(team.displayName() + " team has no configured beacon.");
                }
                placeBeacon(team, beaconLocation);
            }
        } catch (CommandFailure | RuntimeException failure) {
            // A failed start never reaches onEnd.
            clearBeacons();
            throw failure;
        }

        for (Player player : services.participants().onlinePlayers()) {
            TeamColor team = teamOf(player);
            services.sidebar().setNameColor(player, team);
            spawnPlayerAtTeamBeacon(player, team);
            showBossBars(player);
        }

        startHud();
    }

    /** Only once their beacon is gone: waiting to respawn, they would scout the enemy. */
    @Override
    public boolean mayFollowPlayers(Player participant) {
        TeamColor team = teamOf(participant);
        BeaconState beacon = team == null ? null : beacons.get(team);
        return !isPlayerAlive(participant) && (beacon == null || beacon.destroyed);
    }

    @Override
    public boolean allowLateJoin() {
        return true;
    }

    @Override
    public void onParticipantJoin(Player player) {
        // Already waiting out a respawn: spawn now, not twice.
        cancelPendingRespawn(player.getUniqueId());
        TeamColor team = teamOf(player);
        BeaconState beacon = team == null ? null : beacons.get(team);
        if (beacon == null || beacon.destroyed) {
            services.playerState().makeSpectator(player, 0);
            player.sendMessage(Component.text("Your team's beacon is gone. Spectating.",
                    NamedTextColor.YELLOW));
        } else {
            services.sidebar().setNameColor(player, team);
            spawnPlayerAtTeamBeacon(player, team);
        }
        showBossBars(player);
        updateHud();
    }

    @Override
    public void onTeamChange(Player player, TeamColor newTeam) {
        if (!services.session().isRunning()) {
            return;
        }
        cancelPendingRespawn(player.getUniqueId());
        spawnProtectionExpirations.remove(player.getUniqueId());
        services.sidebar().setNameColor(player, newTeam);
        BeaconState beacon = beacons.get(newTeam);
        if (beacon != null && !beacon.destroyed) {
            spawnPlayerAtTeamBeacon(player, newTeam);
        } else {
            services.playerState().makeSpectator(player, 0);
        }
        updateHud();
        checkGameEnd();
    }

    @Override
    public void onEnd(EndReason reason) {
        super.onEnd(reason);
        clearPendingRespawns();
        spawnProtectionExpirations.clear();
        clearBeacons();
    }

    @Override
    public boolean arePlayersFriendly(Player first, Player second) {
        TeamColor firstTeam = teamOf(first);
        return firstTeam != null && firstTeam == teamOf(second);
    }

    @Override
    public Collection<Player> getTrackedPlayers(Player viewer) {
        List<Player> result = new ArrayList<>();
        for (Player other : alivePlayers()) {
            if (other == viewer || arePlayersFriendly(viewer, other)) {
                continue;
            }
            result.add(other);
        }
        return result;
    }

    @Override
    public boolean shouldHideTrackedPlayerNames(Player viewer) {
        return shouldHidePerPlayerDistances();
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!services.participants().isParticipant(player)) {
            return;
        }

        event.setCancelled(true);
        Component death = event.deathMessage();
        if (death != null) {
            services.messages().broadcast(death.color(NamedTextColor.GRAY));
        }

        TeamColor team = teamOf(player);
        BeaconState beacon = team == null ? null : beacons.get(team);
        if (beacon != null && !beacon.destroyed) {
            queueRespawn(player, team);
        } else {
            services.playerState().makeSpectator(player, 0);
        }
        checkGameEnd();
        updateHud();
    }

    @Override
    public void onParticipantQuit(Player player) {
        cancelPendingRespawn(player.getUniqueId());
        spawnProtectionExpirations.remove(player.getUniqueId());
        checkGameEnd();
        updateHud();
    }

    @Override
    public void onParticipantLeave(Player player) {
        onParticipantQuit(player);
    }

    @Override
    public void onParticipantRejoin(Player player) {
        onParticipantJoin(player);
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        if (!services.participants().isParticipant(event.getPlayer())) {
            return;
        }

        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            event.setCancelled(true);
            return;
        }

        BeaconState beacon = getBeaconByBlock(event.getBlock());
        if (beacon != null) {
            handleBeaconDamage(event, beacon);
            return;
        }

        if (isProtectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        if (services.participants().isParticipant(event.getPlayer())
                && event.getPlayer().getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            event.setCancelled(true);
            return;
        }
        if (isProtectedBlock(event.getBlockPlaced())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtectedBlock);
    }

    @Override
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtectedBlock);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker != null && config.disableSpawnProtectionOnAttack.get()) {
            spawnProtectionExpirations.remove(attacker.getUniqueId());
        }

        if (event.getEntity() instanceof Player victim) {
            if (isSpawnProtected(victim)) {
                event.setCancelled(true);
                return;
            }

            if (attacker != null && attacker != victim && !config.friendlyFire.get()
                    && arePlayersFriendly(attacker, victim)) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && config.disableSpawnProtectionOnAttack.get()) {
            spawnProtectionExpirations.remove(player.getUniqueId());
        }
    }

    private boolean isProtectedBlock(Block block) {
        int radius = config.protectionRadius.get();
        for (BeaconState beacon : beacons.values()) {
            if (isBeaconBlock(block, beacon)) {
                return true;
            }
            if (block.getWorld().equals(beacon.location.getWorld())
                    && block.getLocation().distanceSquared(beacon.location) <= (double) radius * radius) {
                return true;
            }
        }
        return false;
    }

    private void handleBeaconDamage(BlockBreakEvent event, BeaconState beacon) {
        Player breaker = event.getPlayer();
        if (config.beaconBlockBreaksOnlyByEnemies.get() && teamOf(breaker) == beacon.team) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        beacon.health = Math.max(0, beacon.health - 1);
        updateBossBar(beacon);

        if (beacon.health > 0) {
            beacon.location.getWorld().playSound(beacon.location, Sound.BLOCK_ANVIL_LAND, 200.0f, 0.8f);
            breaker.sendActionBar(Component
                    .text(beacon.team.displayName() + " beacon: " + beacon.health + "/"
                            + config.beaconMaxHealth.get())
                    .color(beacon.team.textColor()));
            updateHud();
            return;
        }

        beacon.destroyed = true;
        removeGlow(beacon);
        beacon.location.getBlock().setType(Material.AIR, false);
        beacon.location.getWorld().playSound(beacon.location, Sound.BLOCK_BEACON_DEACTIVATE, 200.0f, 0.6f);
        services.messages().broadcast(Component.text(beacon.team.displayName(), beacon.team.textColor())
                .append(Component.text(" beacon destroyed", NamedTextColor.RED)));
        cancelPendingRespawnsForTeam(beacon.team);
        updateBossBar(beacon);
        updateArenaBorder();
        updateHud();
        checkGameEnd();
    }

    private void queueRespawn(Player player, TeamColor team) {
        cancelPendingRespawn(player.getUniqueId());
        services.playerState().makeSpectator(player, config.respawnBlindnessSeconds.get());
        int delaySeconds = config.respawnDelaySeconds.get();
        long respawnAtMillis = System.currentTimeMillis() + delaySeconds * 1000L;
        BukkitTask task = services.plugin().getServer().getScheduler().runTaskLater(services.plugin(), () -> {
            pendingRespawns.remove(player.getUniqueId());
            BeaconState beacon = beacons.get(team);
            if (beacon == null || beacon.destroyed || !services.participants().isParticipant(player)
                    || !player.isOnline()) {
                updateHud();
                checkGameEnd();
                return;
            }
            spawnPlayerAtTeamBeacon(player, team);
            updateHud();
            checkGameEnd();
        }, delaySeconds * 20L);
        pendingRespawns.put(player.getUniqueId(), new PendingRespawn(team, respawnAtMillis, task));
    }

    private void spawnPlayerAtTeamBeacon(Player player, TeamColor team) {
        BeaconState beacon = beacons.get(team);
        Location spawn = findSpawnNearBeacon(beacon.location, 5, 6);
        if (spawn == null) {
            spawn = services.spawnFinder().findSpawnAround(beacon.location, 5);
        }
        if (spawn == null) {
            spawn = beacon.location.clone().add(0.5, 1.0, 0.5);
        }
        prepareAlivePlayer(player, spawn, config.keepInvisibility.get());
        if (config.spawnProtectionSeconds.get() > 0) {
            spawnProtectionExpirations.put(player.getUniqueId(),
                    System.currentTimeMillis() + config.spawnProtectionSeconds.get() * 1000L);
        }
    }

    private void placeBeacon(TeamColor team, Location location) {
        Block block = location.getBlock();
        BlockState replacedState = block.getState();
        block.setType(team.woolMaterial(), false);
        int maxHealth = config.beaconMaxHealth.get();
        BossBar bossBar = services.bossBars().create(
                Component.text(team.displayName() + " Beacon " + maxHealth + "/" + maxHealth, team.textColor()),
                team.bossBarColor(), BossBar.Overlay.PROGRESS);
        BeaconState beacon = new BeaconState(team, block.getLocation(), replacedState, bossBar, maxHealth);
        beacons.put(team, beacon);
        ensureGlow(beacon);
    }

    /** Spawns the beacon's glowing outline if missing; it is non-persistent, so a chunk unload drops it. */
    private void ensureGlow(BeaconState beacon) {
        if (beacon.destroyed || (beacon.glow != null && beacon.glow.isValid())) {
            return;
        }
        var world = beacon.location.getWorld();
        if (world == null
                || !world.isChunkLoaded(beacon.location.getBlockX() >> 4, beacon.location.getBlockZ() >> 4)) {
            return;
        }
        float scale = 1.0f - 2 * GLOW_INSET;
        beacon.glow = world.spawn(beacon.location, BlockDisplay.class, display -> {
            display.setPersistent(false);
            display.setBlock(beacon.team.woolMaterial().createBlockData());
            display.setTransformation(new Transformation(new Vector3f(GLOW_INSET, GLOW_INSET, GLOW_INSET),
                    new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(4.0f);
            display.setGlowColorOverride(DyeColor.valueOf(beacon.team.name()).getColor());
            display.setGlowing(true);
        });
    }

    private void removeGlow(BeaconState beacon) {
        if (beacon.glow != null) {
            beacon.glow.remove();
            beacon.glow = null;
        }
    }

    private void clearBeacons() {
        for (BeaconState beacon : beacons.values()) {
            removeGlow(beacon);
            if (beacon.replacedBlockState != null) {
                beacon.replacedBlockState.update(true, false);
            }
            services.bossBars().hide(beacon.bossBar);
        }
        beacons.clear();
    }

    private void showBossBars(Player player) {
        for (BeaconState beacon : beacons.values()) {
            player.showBossBar(beacon.bossBar);
        }
    }

    private void updateBossBar(BeaconState beacon) {
        int maxHealth = config.beaconMaxHealth.get();
        float progress = Math.max(0.0f, Math.min(1.0f, beacon.health / (float) maxHealth));
        beacon.bossBar.progress(progress);
        beacon.bossBar.name(Component.text(
                beacon.team.displayName() + " Beacon " + beacon.health + "/" + maxHealth, beacon.team.textColor()));
    }

    private void cancelPendingRespawn(UUID playerId) {
        PendingRespawn pendingRespawn = pendingRespawns.remove(playerId);
        if (pendingRespawn != null) {
            pendingRespawn.task.cancel();
        }
    }

    private void clearPendingRespawns() {
        for (PendingRespawn pendingRespawn : pendingRespawns.values()) {
            pendingRespawn.task.cancel();
        }
        pendingRespawns.clear();
    }

    private void cancelPendingRespawnsForTeam(TeamColor team) {
        List<UUID> toCancel = new ArrayList<>();
        for (Map.Entry<UUID, PendingRespawn> entry : pendingRespawns.entrySet()) {
            if (entry.getValue().team == team) {
                toCancel.add(entry.getKey());
            }
        }
        for (UUID playerId : toCancel) {
            cancelPendingRespawn(playerId);
        }
    }

    private BeaconState getBeaconByBlock(Block block) {
        for (BeaconState beacon : beacons.values()) {
            if (isBeaconBlock(block, beacon)) {
                return beacon;
            }
        }
        return null;
    }

    private boolean isBeaconBlock(Block block, BeaconState beacon) {
        return block.getWorld().equals(beacon.location.getWorld()) && block.getX() == beacon.location.getBlockX()
                && block.getY() == beacon.location.getBlockY() && block.getZ() == beacon.location.getBlockZ();
    }

    private boolean isSpawnProtected(Player player) {
        Long expiresAt = spawnProtectionExpirations.get(player.getUniqueId());
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            spawnProtectionExpirations.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private int getAlivePlayers(TeamColor team) {
        int alive = 0;
        for (Player player : alivePlayers()) {
            if (teamOf(player) == team) {
                alive++;
            }
        }
        return alive;
    }

    private int getPendingRespawns(TeamColor team) {
        int pending = 0;
        for (PendingRespawn respawn : pendingRespawns.values()) {
            if (respawn.team == team) {
                pending++;
            }
        }
        return pending;
    }

    private int getRemainingBeaconCount() {
        int remaining = 0;
        for (BeaconState beacon : beacons.values()) {
            if (!beacon.destroyed) {
                remaining++;
            }
        }
        return remaining;
    }

    /** At most one beacon left: distances show and the border comes in. */
    private boolean isEndgame() {
        return getRemainingBeaconCount() <= 1;
    }

    private boolean shouldHidePerPlayerDistances() {
        return config.hidePerPlayerDistances.get() && !isEndgame();
    }

    @Override
    protected boolean borderAtStart() {
        return false;
    }

    /**
     * Brings the border in at the endgame, centred on the intact beacons and wide enough for every alive
     * player; it never shrinks past an intact beacon. Called again when a beacon breaks to shrink further.
     */
    private void updateArenaBorder() {
        if (!config.arenaBorderEnabled.get() || !isEndgame()) {
            return;
        }
        var world = gameWorld();
        List<Location> intact = new ArrayList<>();
        for (BeaconState beacon : beacons.values()) {
            if (!beacon.destroyed && world.equals(beacon.location.getWorld())) {
                intact.add(beacon.location.clone().add(0.5, 0, 0.5));
            }
        }
        List<Location> players = new ArrayList<>();
        for (Player player : alivePlayers()) {
            players.add(player.getLocation());
        }
        long seconds = Math.max(0, config.arenaBorderTimeToFinalSeconds.get());

        if (!borderActive) {
            List<Location> anchors = !intact.isEmpty() ? intact
                    : !players.isEmpty() ? players : List.of(configuredCenter().toLocation(world));
            Location center = boxCenter(anchors);
            List<Location> inside = new ArrayList<>(intact);
            inside.addAll(players);
            double initialSize = Math.max(sizeContaining(center, inside), config.arenaBorderInitialSize.get());
            double finalSize = Math.min(initialSize, borderFloor(center, intact));
            borderShrinkPerSecond = seconds == 0 ? 0.0
                    : Math.max(initialSize - finalSize,
                            config.arenaBorderInitialSize.get() - config.arenaBorderFinalSize.get()) / seconds;
            services.arena().applyBorder(world, center, initialSize, finalSize, shrinkSeconds(initialSize, finalSize));
            borderActive = true;
            services.messages().broadcast(Component.text("The arena border is closing in", NamedTextColor.RED));
            return;
        }

        var border = world.getWorldBorder();
        double size = border.getSize();
        double finalSize = Math.min(size, borderFloor(border.getCenter(), intact));
        long shrink = shrinkSeconds(size, finalSize);
        if (shrink > 0) {
            border.changeSize(finalSize, shrink * 20L);
        }
    }

    private long shrinkSeconds(double from, double to) {
        if (borderShrinkPerSecond <= 0.0 || from <= to) {
            return 0;
        }
        return Math.max(1, Math.round((from - to) / borderShrinkPerSecond));
    }

    /** The configured final size, but never small enough to leave an intact beacon outside. */
    private double borderFloor(Location center, List<Location> intact) {
        return Math.max(Math.max(1.0, config.arenaBorderFinalSize.get()), sizeContaining(center, intact));
    }

    private static double sizeContaining(Location center, List<Location> points) {
        double halfSize = 0.0;
        for (Location point : points) {
            halfSize = Math.max(halfSize, Math.max(Math.abs(point.getX() - center.getX()),
                    Math.abs(point.getZ() - center.getZ())));
        }
        return points.isEmpty() ? 1.0 : 2.0 * (halfSize + BORDER_MARGIN);
    }

    private static Location boxCenter(List<Location> points) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Location point : points) {
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minZ = Math.min(minZ, point.getZ());
            maxZ = Math.max(maxZ, point.getZ());
        }
        return new Location(points.getFirst().getWorld(), (minX + maxX) / 2.0, 0, (minZ + maxZ) / 2.0);
    }

    private boolean isTeamStillAlive(BeaconState beacon) {
        int alive = getAlivePlayers(beacon.team);
        if (alive > 0) {
            return true;
        }
        if (!beacon.destroyed) {
            return getPendingRespawns(beacon.team) > 0;
        }
        return false;
    }

    private Location findSpawnNearBeacon(Location beaconLocation, int radius, int maxYOffset) {
        var world = beaconLocation.getWorld();
        if (world == null) {
            return null;
        }

        int beaconY = beaconLocation.getBlockY();
        List<int[]> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                candidates.add(new int[] { beaconLocation.getBlockX() + dx, beaconLocation.getBlockZ() + dz });
            }
        }
        java.util.Collections.shuffle(candidates);

        for (int[] candidate : candidates) {
            Location spawn = findSpawnAtColumn(world, candidate[0], candidate[1], beaconY, maxYOffset);
            if (spawn != null) {
                return spawn;
            }
        }

        return null;
    }

    private Location findSpawnAtColumn(org.bukkit.World world, int x, int z, int startY, int maxDrop) {
        int minFeetY = Math.max(world.getMinHeight() + 1, startY - maxDrop);
        int maxFeetY = Math.min(world.getMaxHeight() - 1, startY + 1);

        for (int y = maxFeetY; y >= minFeetY; y--) {
            Location feet = new Location(world, x, y, z);
            if (!Locations.playerPassable(feet)) {
                continue;
            }
            if (!world.getBlockAt(x, y - 1, z).isSolid()) {
                continue;
            }
            return feet.add(0.5, 0, 0.5);
        }

        return null;
    }

    private void checkGameEnd() {
        if (ended || !services.session().isRunning()) {
            return;
        }
        List<TeamColor> activeTeams = new ArrayList<>();
        for (BeaconState beacon : beacons.values()) {
            if (isTeamStillAlive(beacon)) {
                activeTeams.add(beacon.team);
            }
        }

        if (activeTeams.size() == 1 && !services.isTest()) {
            ended = true;
            TeamColor winner = activeTeams.getFirst();
            summary.add(Component.text().append(Component.text("Winner: ", NamedTextColor.GREEN))
                    .append(Component.text(winner.displayName() + " team", winner.textColor())).build());
            for (Player player : services.participants().onlinePlayers()) {
                if (teamOf(player) == winner) {
                    services.award("war", player.getUniqueId(), 1);
                }
            }
            services.session().endGame(EndReason.FINISHED);
        } else if (activeTeams.isEmpty()) {
            ended = true;
            summary.add(Component.text("No teams left", NamedTextColor.YELLOW));
            services.session().endGame(EndReason.FINISHED);
        }
    }

    @Override
    protected List<Component> computeHudLines(Player viewer) {
        if (shouldHidePerPlayerDistances()) {
            return buildTeamSummaryLines(viewer);
        }
        return buildDistanceLines(viewer);
    }

    @Override
    protected Component hudTitle(Player viewer) {
        return Component.text(shouldHidePerPlayerDistances() ? "Teams" : "Distances", NamedTextColor.GOLD);
    }

    @Override
    protected boolean includeRefreshLine(Player viewer) {
        return !shouldHidePerPlayerDistances();
    }

    @Override
    public void onSecondTick(long secondsElapsed) {
        super.onSecondTick(secondsElapsed);
        for (BeaconState beacon : beacons.values()) {
            ensureGlow(beacon);
        }
        if (shouldHidePerPlayerDistances()) {
            updateHud();
        }
    }

    private List<Component> buildTeamSummaryLines(Player viewer) {
        List<BeaconState> states = new ArrayList<>(beacons.values());
        states.sort(Comparator.comparing((BeaconState state) -> state.destroyed)
                .thenComparing(state -> state.team.name()));
        List<Component> lines = new ArrayList<>();
        for (BeaconState state : states) {
            int alive = getAlivePlayers(state.team);
            Component status = !state.destroyed ? Component.text(" ✔", NamedTextColor.GRAY)
                    : alive > 0 ? Component.text(" " + alive, NamedTextColor.GRAY)
                            : Component.text(" ✘", NamedTextColor.GRAY);
            lines.add(Component.text(state.team.displayName(), state.team.textColor()).append(status));
        }

        PendingRespawn pendingRespawn = pendingRespawns.get(viewer.getUniqueId());
        if (pendingRespawn != null) {
            long millisLeft = Math.max(0L, pendingRespawn.respawnAtMillis - System.currentTimeMillis());
            int secondsLeft = (int) Math.ceil(millisLeft / 1000.0);
            lines.add(Component.text("Respawn: " + secondsLeft + "s", NamedTextColor.GREEN));
        }
        return lines;
    }
}
