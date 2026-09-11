package me.petr1furious.potatominigames.games.warfare;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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

/**
 * Team-beacon Warfare: each team defends a wool beacon with hit points; players
 * respawn at their beacon until it is destroyed; last team standing wins.
 */
final class TeamBeaconWarfareSession extends WarfareSessionBase {
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

    TeamBeaconWarfareSession(SessionServices services, WarfareConfig config, FPVDroneGadget fpvDrone) {
        super(services, config, fpvDrone);
    }

    private TeamColor teamOf(Player player) {
        return services.participants().find(player.getUniqueId()).map(Participant::team).orElse(null);
    }

    @Override
    public void onStart() throws CommandFailure {
        List<TeamColor> participatingTeams = services.teams().activeTeams();
        if (participatingTeams.size() < 2) {
            throw new CommandFailure("Team beacon mode needs players on at least two teams.");
        }
        // The arena (and, with slime worlds, the freshly cloned game world) has to
        // exist before beacon locations can resolve their world.
        setupArena();

        for (TeamColor team : participatingTeams) {
            Location beaconLocation = config.getBeacon(team);
            if (beaconLocation == null || beaconLocation.getWorld() == null) {
                throw new CommandFailure(team.displayName() + " team has no configured beacon.");
            }
            placeBeacon(team, beaconLocation);
        }

        for (Player player : services.participants().onlinePlayers()) {
            TeamColor team = teamOf(player);
            services.sidebar().setNameColor(player, team);
            spawnPlayerAtTeamBeacon(player, team);
            showBossBars(player);
        }

        startHud();
    }

    @Override
    public boolean allowLateJoin() {
        return true;
    }

    @Override
    public void onParticipantJoin(Player player) {
        // Brought back while waiting out a respawn: spawn now, not twice.
        cancelPendingRespawn(player.getUniqueId());
        TeamColor team = teamOf(player);
        BeaconState beacon = team == null ? null : beacons.get(team);
        if (beacon == null || beacon.destroyed) {
            services.playerState().makeSpectator(player, 0);
            player.sendMessage(Component.text("Your team's beacon is not available — spectating.",
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
        // Team commands work at any time; only a live round needs re-spawning.
        if (!services.sessions().isRunning()) {
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
        beacon.location.getBlock().setType(Material.AIR, false);
        beacon.location.getWorld().playSound(beacon.location, Sound.BLOCK_BEACON_DEACTIVATE, 200.0f, 0.6f);
        services.messages().broadcast(Component.text(beacon.team.displayName(), beacon.team.textColor())
                .append(Component.text(" beacon destroyed!", NamedTextColor.RED)));
        cancelPendingRespawnsForTeam(beacon.team);
        updateBossBar(beacon);
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
        beacons.put(team, new BeaconState(team, block.getLocation(), replacedState, bossBar, maxHealth));
    }

    private void clearBeacons() {
        for (BeaconState beacon : beacons.values()) {
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

    private boolean shouldHidePerPlayerDistances() {
        return config.hidePerPlayerDistances.get() && getRemainingBeaconCount() > 1;
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
        if (ended || !services.sessions().isRunning()) {
            return;
        }
        List<TeamColor> activeTeams = new ArrayList<>();
        for (BeaconState beacon : beacons.values()) {
            if (isTeamStillAlive(beacon)) {
                activeTeams.add(beacon.team);
            }
        }

        if (activeTeams.size() == 1) {
            ended = true;
            TeamColor winner = activeTeams.getFirst();
            services.messages().broadcast(Component.text("Game over! ", NamedTextColor.RED)
                    .append(Component.text(winner.displayName(), winner.textColor()))
                    .append(Component.text(" team wins!", NamedTextColor.RED)));
            for (Player player : services.participants().onlinePlayers()) {
                if (teamOf(player) == winner) {
                    services.points().add("war", player.getUniqueId(), 1);
                }
            }
            services.sessions().endGame(EndReason.FINISHED);
        } else if (activeTeams.isEmpty()) {
            ended = true;
            services.messages().broadcast(Component.text("Game over! No teams left!", NamedTextColor.RED));
            services.sessions().endGame(EndReason.FINISHED);
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
        // The team summary (with live respawn countdowns) refreshes every second.
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
