package me.petr1furious.potatominigames.games.finals;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.core.text.TimeFormat;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/** Paper adapter: the cashbox and payouts always belong to the pure match state. */
final class FinalsSession implements GameSession {
    private final SessionServices services;
    private final FinalsConfig config;
    private final FinalsMaps maps;
    private final FinalsLoadouts loadouts;
    private FinalsWeapons weapons;
    private FinalsGadgets gadgets;
    private boolean applyingCombatDamage;
    private boolean applyingBulletDamage;
    private final FinalsBulletKnockback bulletKnockback = new FinalsBulletKnockback();
    private final FinalsHitHealth hitHealth = new FinalsHitHealth(this::fighting, () -> this.tick);
    private final FinalsRegeneration regeneration = new FinalsRegeneration(this::fighting, () -> this.tick);
    private Location damageOrigin;

    private record OriginalVitals(double maxHealth, double scale, float walkSpeed, float flySpeed) {}

    private final Map<UUID, OriginalVitals> originalVitals = new HashMap<>();
    private final Map<UUID, FinalsClass> classes = new HashMap<>();
    private final Map<UUID, FinalsLoadouts.Choice> equippedLoadouts = new HashMap<>();
    private final FinalsKills kills = new FinalsKills();
    private final FinalsCashoutMusic cashoutMusic;
    private final FinalsTeamWipes teamWipes = new FinalsTeamWipes();
    private final Map<UUID, Location> teamRespawnPositions = new HashMap<>();
    private final Map<Integer, Location> startingSpawns = new HashMap<>();
    private final Map<UUID, Long> gadgetSicknessUntil = new HashMap<>();
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Map<UUID, Integer> roster = new LinkedHashMap<>();
    private final Set<UUID> connected = new LinkedHashSet<>();
    private final Set<UUID> withdrawn = new HashSet<>();
    private final Map<UUID, Long> respawnAt = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, Entity> ordnance = new HashMap<>();

    private record Frozen(Location anchor, float walkSpeed, float flySpeed) {}

    private final Map<UUID, Frozen> frozen = new HashMap<>();
    private FinalsState state;
    private FinalsState.Rules rules;
    private FinalsMapArena arena;
    private World world;
    private BukkitTask ticker;
    private BossBar bar;
    private FinalsHighlights highlights;
    private FinalsTabList tabList;
    private FinalsCashoutPhysics cashouts;
    private FinalsMapJumpPads mapJumpPads;
    private FinalsDropInput dropInput;
    private FinalsRevives revives;
    private FinalsRespawnInput respawnInput;
    private Item cashbox;
    private Location cashboxLocation;
    private Location cashboxRecovery;
    private final Map<UUID, Location> lastGrounded = new HashMap<>();
    private UUID recentDropper;
    private UUID cashboxThrower;
    private Vector cashboxPreviousCenter;
    private long pickupAfter;
    private long stealSirenAt;
    private boolean stealSirenHigh;
    private long tick;
    private long countdownEnd;
    private long missingTeamSince = -1;
    private int respawnTicks;
    private int protectionTicks;
    private boolean done;
    private boolean preparing;
    private boolean revivalMode;
    private UUID gameMaster;
    private boolean gameMasterOnline;
    private FinalsGameEvents gameEvents;
    private boolean internalTeleport;
    private List<Component> result = List.of();

    FinalsSession(SessionServices services, FinalsConfig config) {
        this(services, config, null, null);
    }

    FinalsSession(
            SessionServices services,
            FinalsConfig config,
            FinalsMaps maps,
            FinalsLoadouts loadouts) {
        this.services = services;
        this.config = config;
        this.maps = maps;
        this.loadouts = loadouts;
        this.cashoutMusic = new FinalsCashoutMusic(services.plugin());
    }

    void loadoutChanged(Player player) {
        if (!running() || !connected.contains(player.getUniqueId())) return;
        if (preparing) return;
        if (state == null) spawn(player);
        else player.sendMessage(text("loadout.next_spawn"));
    }

    boolean isGameMaster(Player player) {
        return running() && gameMasterOnline && player.getUniqueId().equals(gameMaster)
                && !withdrawn.contains(gameMaster);
    }

    FinalsGameEvents gameEvents() {
        return gameEvents;
    }

    boolean gameEventsReady() {
        return running() && !preparing && gameMasterOnline && state != null && !state.isFinished();
    }

    private FinalsGameEvents.Context gameEventsContext() {
        FinalsCombatContext combat = combatContext();
        return new FinalsGameEvents.Context() {
            public org.bukkit.plugin.java.JavaPlugin plugin() { return services.plugin(); }
            public World world() { return world; }
            public long tick() { return tick; }
            public Collection<Player> players() { return combat.players(); }
            public boolean active(Player player) { return fighting(player) && !protectedSpawn(player); }
            public boolean inside(Location point) { return combat.inside(point); }
            public boolean destructible(Block block) { return combat.destructible(block); }
            public void breakBlock(Block block) { combat.breakBlock(block); }
            public void damage(Player target, double amount, Location origin) {
                if (!gameEventsReady() || !active(target) || amount <= 0) return;
                boolean previous = applyingCombatDamage;
                boolean previousBullet = applyingBulletDamage;
                Location previousOrigin = damageOrigin;
                applyingCombatDamage = true;
                applyingBulletDamage = false;
                damageOrigin = origin == null ? target.getLocation() : origin.clone();
                try {
                    target.setNoDamageTicks(0);
                    bulletKnockback.damage(target, null, false, () -> target.damage(amount));
                } finally {
                    applyingCombatDamage = previous;
                    applyingBulletDamage = previousBullet;
                    damageOrigin = previousOrigin;
                }
            }
            public void announce(String key, Object... arguments) {
                services.messages().broadcast(text(key, arguments));
            }
        };
    }

    private void spawnGameMaster(Player player) {
        if (preparing || world == null) return;
        forceRespawn(player);
        release(player);
        if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
        services.playerState().resetForRound(player, GameMode.SPECTATOR);
        player.setFlying(true);
        player.setGlowing(false);
        teleport(player, arena.center().add(0, 12, 0));
        if (bar != null) player.showBossBar(bar);
        player.sendMessage(text("gm.instructions"));
        updateGameMasterHud();
    }

    private void updateGameMasterHud() {
        if (!gameMasterOnline || gameMaster == null || arena == null || preparing || done) return;
        Player player = Bukkit.getPlayer(gameMaster);
        if (player == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(text("gm.role"));
        for (int team = 0; team < 2; team++)
            lines.add(text("score", teamName(team), state == null ? 0 : state.score(team)));
        if (state != null) {
            lines.add(text("match_time", TimeFormat.clock(Math.max(0, (state.matchDeadline() - tick + 19) / 20))));
            lines.add(text("phase." + state.phase().name().toLowerCase(Locale.ROOT)));
        }
        if (gameEvents != null && gameEvents.activeKind() != null)
            lines.add(text("event.status", I18n.tr(gameEvents.activeKind().nameKey()), gameEvents.remainingSeconds()));
        lines.add(text("gm.sidebar"));
        services.sidebar().update(player, text("name"), lines);
        if (bar != null) player.showBossBar(bar);
    }

    private FinalsCombatContext combatContext() {
        return new FinalsCombatContext() {
            public org.bukkit.plugin.java.JavaPlugin plugin() {
                return services.plugin();
            }

            public World world() {
                return world;
            }

            public long tick() {
                return tick;
            }

            public Collection<Player> players() {
                return connected.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
            }

            public boolean active(Player player) {
                return fighting(player);
            }

            public boolean canAct(Player player) {
                return canObjective(player)
                        && !state.stealDeadlines().containsKey(player.getUniqueId())
                        && (loadouts == null || !FinalsLoadouts.viewing(player));
            }

            public boolean friendly(Player a, Player b) {
                return arePlayersFriendly(a, b);
            }

            public boolean inside(Location location) {
                return arena != null && arena.contains(location);
            }

            public boolean canPlace(Block block) {
                if (block == null) return false;
                Location center = block.getLocation().add(.5, .5, .5);
                if (!inside(center)) return false;
                List<Location> objectives = new ArrayList<>(arena.vaultLocations());
                objectives.add(arena.station(0));
                objectives.add(arena.station(1));
                for (Location objective : objectives) {
                    if (center.distanceSquared(objective.clone().add(0, .5, 0)) <= 9) return false;
                }
                for (UUID id : roster.keySet()) {
                    Location spawn = arena.spawn(roster.get(id), spawnIndex(id));
                    double dx = center.getX() - spawn.getX(), dz = center.getZ() - spawn.getZ();
                    // Keep a two-block approach radius and the complete standing/jumping
                    // headroom clear even while this roster member is dead or disconnected.
                    if (dx * dx + dz * dz <= 4
                            && center.getY() >= spawn.getY() - 1
                            && center.getY() <= spawn.getY() + 3) return false;
                }
                return true;
            }

            public boolean destructible(Block block) {
                return arena != null
                        && (arena.isCover(block)
                                || (gadgets != null && gadgets.isTemporaryCover(block)));
            }

            public void breakBlock(Block block) {
                if (!destructible(block)) return;
                if (gadgets == null || !gadgets.breakTemporary(block))
                    block.setType(Material.AIR, false);
            }

            public void track(Entity entity) {
                ordnance.put(entity.getUniqueId(), entity);
            }

            public void damage(Player target, Player source, double amount) {
                damage(target, source, amount, source.getLocation());
            }

            public void damage(Player target, Player source, double amount, Location origin) {
                applyDamage(target, source, amount, origin, false, false);
            }

            public void damageBullet(Player target, Player source, double amount, Location origin) {
                applyDamage(target, source, amount, origin, true, true);
            }

            public void damageReflectedBullet(Player target, Player source, double amount, Location origin) {
                applyDamage(target, source, amount, origin, false, true);
            }

            private void applyDamage(Player target, Player source, double amount, Location origin,
                    boolean bullet, boolean suppressKnockback) {
                if (amount <= 0
                        || !fighting(target)
                        || !fighting(source)
                        || protectedSpawn(target)
                        || protectedSpawn(source)
                        || (!target.equals(source) && arePlayersFriendly(source, target))) return;
                boolean previous = applyingCombatDamage;
                boolean previousBullet = applyingBulletDamage;
                Location previousOrigin = damageOrigin;
                applyingCombatDamage = true;
                applyingBulletDamage = bullet;
                damageOrigin = origin == null ? source.getLocation() : origin.clone();
                try {
                    target.setNoDamageTicks(0);
                    bulletKnockback.damage(target, source, suppressKnockback,
                            () -> target.damage(amount, source));
                } finally {
                    applyingCombatDamage = previous;
                    applyingBulletDamage = previousBullet;
                    damageOrigin = previousOrigin;
                }
            }

            public boolean weaponBlocked(Player player) {
                return reviveOccupied(player) || gadgets != null && gadgets.weaponBlocked(player);
            }

            public boolean gadgetsBlocked(Player player) {
                return tick < gadgetSicknessUntil.getOrDefault(player.getUniqueId(), 0L);
            }

            public boolean attackDevice(Player source, Location eye, Vector direction, double reach, double damage) {
                return gadgets != null && gadgets.attackDevice(source, eye, direction, reach, damage);
            }

            public double deviceDistance(Player source, Location eye, Vector direction, double reach) {
                return gadgets == null ? Double.POSITIVE_INFINITY
                        : gadgets.deviceDistance(source, eye, direction, reach);
            }

            public boolean tryPullCashout(Player source, Location eye, Vector direction, double reach) {
                if (!canObjective(source) || cashouts == null || !world.equals(eye.getWorld())) return false;
                int slot = cashouts.aimedStation(source, reach);
                if (slot < 0) return false;
                return cashouts.moveToward(slot, pullGoal(source, direction), .35);
            }

            public boolean tryPullCashbox(Player source, Location eye, Vector direction, double reach) {
                if (!canObjective(source) || state.phase() != FinalsState.Phase.CASHBOX_AVAILABLE
                        || cashbox == null || !cashbox.isValid() || !world.equals(eye.getWorld())) return false;
                var hit = cashbox.getBoundingBox().clone().expand(.2).rayTrace(eye.toVector(), direction, reach);
                if (hit == null) return false;
                double distance = hit.getHitPosition().distance(eye.toVector());
                var wall = world.rayTraceBlocks(eye, direction, reach, FluidCollisionMode.NEVER, true);
                if (wall != null && wall.getHitPosition().distance(eye.toVector()) + 1e-7 < distance) return false;
                Vector movement = pullGoal(source, direction).add(0, .4, 0).toVector()
                        .subtract(cashbox.getLocation().toVector());
                double length = movement.length();
                cashbox.setVelocity(length < 1e-8 ? new Vector()
                        : movement.multiply(Math.min(.45, length) / length));
                cashboxThrower = null;
                cashboxPreviousCenter = null;
                return true;
            }

            public void reveal(Player player) {
                if (gadgets != null) gadgets.reveal(player);
            }

            public boolean awaitingRespawn(Player player) {
                UUID id = player.getUniqueId();
                return running() && connected.contains(id) && teamWipes.dead(id)
                        && respawnAt.containsKey(id) && teamWipes.wave(id) == null && !withdrawn.contains(id);
            }

            public Location deathLocation(UUID id) {
                Location location = deathLocations.get(id);
                if (revives != null && revives.location(id) != null) location = revives.location(id);
                return location == null ? null : location.clone();
            }

            public boolean revive(Player teammate, Player reviver) {
                if (revivalMode)
                    return revives != null && revives.defibrillate(reviver, teammate.getUniqueId());
                UUID id = teammate.getUniqueId();
                Location point = deathLocation(id);
                if (!canAct(reviver) || !awaitingRespawn(teammate) || !friendly(teammate, reviver)
                        || point == null || !near(reviver, point, 4)) return false;
                teamRespawnPositions.put(id, safeRevivePoint(id, point));
                spawn(teammate, false);
                if (respawnAt.containsKey(id)) return false;
                teammate.sendMessage(text("revived", reviver.displayName()));
                return true;
            }

            public boolean defibrillateAimed(Player reviver) {
                return revivalMode && revives != null && revives.defibrillateAimed(reviver);
            }

            public boolean carrier(Player player) {
                return state != null && player.getUniqueId().equals(state.carrier());
            }

            public FinalsClass playerClass(Player player) {
                return classes.getOrDefault(player.getUniqueId(), FinalsClass.MEDIUM);
            }
        };
    }

    private boolean downed(UUID id) {
        return revivalMode && teamWipes.dead(id) && teamWipes.wave(id) == null
                && respawnAt.containsKey(id) && !withdrawn.contains(id);
    }

    private boolean reviveOccupied(Player player) {
        return revives != null && (revives.busy(player.getUniqueId()) || revives.carrying(player.getUniqueId()));
    }

    private boolean canRevive(Player player) {
        UUID id = player.getUniqueId();
        return fighting(player) && !protectedSpawn(player) && !id.equals(state.carrier())
                && !state.stealDeadlines().containsKey(id)
                && (loadouts == null || !FinalsLoadouts.viewing(player));
    }

    private FinalsRevives.Context reviveContext() {
        return new FinalsRevives.Context() {
            public org.bukkit.plugin.java.JavaPlugin plugin() { return services.plugin(); }
            public World world() { return world; }
            public long tick() { return tick; }
            public Collection<Player> players() { return feedbackPlayers(); }
            public boolean canAct(Player player) { return canRevive(player); }
            public boolean downed(UUID id) { return FinalsSession.this.downed(id); }
            public boolean friendly(Player player, UUID target) {
                return roster.containsKey(player.getUniqueId())
                        && Objects.equals(roster.get(player.getUniqueId()), roster.get(target));
            }
            public boolean inside(Location point) { return safeObjectiveLocation(point); }
            public Location fallback(UUID id) { return arena.spawn(roster.get(id), spawnIndex(id)); }
            public Location recovery(UUID id) { return recoveryPoint(id); }
            public boolean safeRecovery(Location point) { return supportedRecovery(point); }
            public void begin(Player player) {
                if (weapons != null) weapons.stopFiring(player.getUniqueId());
                if (gadgets != null) gadgets.reveal(player);
            }
            public boolean complete(UUID target, Player reviver, Location point, boolean defibrillator) {
                UUID source = reviver.getUniqueId();
                if (!running() || !downed(target) || !connected.contains(target)
                        || withdrawn.contains(source) || !friendly(reviver, target)
                        || (!defibrillator && !canRevive(reviver))) return false;
                Player player = Bukkit.getPlayer(target);
                if (player == null || !player.isOnline()) return false;
                teamRespawnPositions.put(target, safeRevivePoint(target, point));
                spawn(player, false);
                if (respawnAt.containsKey(target)) return false;
                if (defibrillator) {
                    player.setHealth(Math.max(.5, player.getAttribute(Attribute.MAX_HEALTH).getValue() / 2));
                    gadgetSicknessUntil.put(target, tick + 100);
                }
                player.sendMessage(text("revived", reviver.displayName()));
                return true;
            }
        };
    }

    private Location safeRevivePoint(UUID id, Location point) {
        if (point != null && world.equals(point.getWorld())) {
            for (int depth = 0; depth <= 4; depth++) {
                Location candidate = new Location(world, point.getBlockX() + .5,
                        point.getBlockY() - depth, point.getBlockZ() + .5, point.getYaw(), point.getPitch());
                if (!arena.contains(candidate) || !arena.contains(candidate.clone().add(0, 1, 0))) continue;
                try {
                    FinalsMaps.validateStanding(world, FinalsMapData.Point.of(candidate));
                    return candidate;
                } catch (IllegalArgumentException unsafe) {
                    // Destroyed floors or newly placed cover use the protected team fallback.
                }
            }
        }
        return arena.spawn(roster.get(id), spawnIndex(id));
    }

    private void requestDownedLoadout(UUID id) {
        if (!running() || preparing || loadouts == null || state == null || state.isFinished()
                || !connected.contains(id) || withdrawn.contains(id) || !respawnAt.containsKey(id)) return;
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.isOnline() && !player.isDead() && !FinalsLoadouts.viewing(player))
            loadouts.open(player);
    }

    private void requestRespawn(UUID id) {
        if (!running() || !revivalMode || state == null || state.isFinished()
                || !connected.contains(id) || withdrawn.contains(id) || !teamWipes.dead(id)) return;
        FinalsTeamWipes.Wave wave = teamWipes.wave(id);
        if (wave != null) {
            if (tick >= wave.deadline()) prepareTeamRespawns();
            return;
        }
        Long ready = respawnAt.get(id);
        if (ready == null || tick < ready) return;
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.isOnline()
                && (loadouts == null || !FinalsLoadouts.viewing(player))) spawn(player);
    }

    private void dropPressed(Player player) {
        if (!fighting(player)) return;
        if (revives != null && revives.throwCarried(player)) return;
        if (revives != null && revives.busy(player.getUniqueId())) {
            revives.interrupt(player.getUniqueId());
            return;
        }
        throwCashbox(player);
    }

    private void rememberVitals(Player player) {
        originalVitals.computeIfAbsent(
                player.getUniqueId(),
                id ->
                        new OriginalVitals(
                                player.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),
                                player.getAttribute(Attribute.SCALE).getBaseValue(),
                                player.getWalkSpeed(),
                                player.getFlySpeed()));
    }

    private void restoreVitals(Player player) {
        release(player);
        OriginalVitals previous = originalVitals.remove(player.getUniqueId());
        if (previous == null) return;
        var health = player.getAttribute(Attribute.MAX_HEALTH);
        health.setBaseValue(previous.maxHealth());
        player.getAttribute(Attribute.SCALE).setBaseValue(previous.scale());
        if (!player.isDead() && player.getHealth() > health.getValue())
            player.setHealth(health.getValue());
        player.setWalkSpeed(previous.walkSpeed());
        player.setFlySpeed(previous.flySpeed());
    }

    private void restoreAllVitals() {
        for (UUID id : List.copyOf(originalVitals.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) restoreVitals(player);
        }
        originalVitals.clear();
        classes.clear();
    }

    private static Component text(String key, Object... args) {
        return I18n.tr("finals." + key, args);
    }

    private static TeamColor color(int team) {
        return team == 0 ? TeamColor.RED : TeamColor.BLUE;
    }

    private static Component teamName(int team) {
        return color(team).displayNameComponent();
    }

    @Override
    public void onStart() throws CommandFailure {
        try {
            start();
        } catch (CommandFailure | RuntimeException error) {
            if (arena != null) arena.cancelBuild();
            if (cashouts != null) cashouts.clear();
            if (mapJumpPads != null) mapJumpPads.clear();
            if (highlights != null) highlights.clear();
            if (tabList != null) tabList.clear();
            if (dropInput != null) dropInput.clear();
            if (revives != null) revives.clear();
            if (respawnInput != null) respawnInput.clear();
            bulletKnockback.close();
            regeneration.close();
            hitHealth.close();
            if (gameEvents != null) gameEvents.clear();
            teamWipes.clear();
            teamRespawnPositions.clear();
            startingSpawns.clear();
            lastGrounded.clear();
            cashboxRecovery = null;
            releaseAll();
            if (weapons != null) weapons.clear();
            if (gadgets != null) gadgets.clear();
            restoreAllVitals();
            throw error;
        }
    }

    private void start() throws CommandFailure {
        var participants = new ArrayList<>(services.participants().all());
        if (participants.isEmpty() || participants.size() > 12)
            throw new CommandFailure(text("players_required"));
        if (participants.size() == 5) {
            var master = participants.removeLast();
            gameMaster = master.id();
            gameMasterOnline = true;
            master.setTeam(null);
        }
        // Preserve valid preferences where possible, but never start with an empty opposing team.
        int[] sizes = new int[2];
        int capacity = (participants.size() + 1) / 2;
        for (var participant : participants) {
            int preferred = participant.team() == TeamColor.BLUE ? 1 : 0;
            int team = sizes[preferred] < capacity ? preferred : 1 - preferred;
            sizes[team]++;
            roster.put(participant.id(), team);
            connected.add(participant.id());
            participant.setTeam(color(team));
        }
        revivalMode = (roster.size() == 4 || roster.size() == 6)
                && roster.values().stream().filter(team -> team == 0).count() == roster.size() / 2
                && roster.values().stream().filter(team -> team == 1).count() == roster.size() / 2;
        countdownEnd = Math.clamp(config.countdownSeconds.get(), 18, 60) * 20L;
        respawnTicks = Math.clamp(config.respawnSeconds.get(), 3, 15) * 20;
        protectionTicks = Math.clamp(config.protectionSeconds.get(), 1, 5) * 20;
        int value = Math.clamp(config.cashoutValue.get(), 100, 1000000);
        rules =
                new FinalsState.Rules(
                        Math.clamp(config.vaultOpenSeconds.get(), 1, 30) * 20L,
                        Math.clamp(config.cashoutSeconds.get(), 10, 120) * 20L,
                        Math.clamp(config.stealSeconds.get(), 2, 15) * 20L,
                        Math.clamp(config.durationSeconds.get(), 60, 1800) * 20L,
                        value,
                        Math.clamp(config.targetScore.get(), value, 10000000));
        arena =
                maps == null
                        ? new FinalsArena(services.worlds().createVoid("finals"))
                        : maps.open(services, services.option("map"));
        preparing = true;
        services.messages().broadcast(text("map.preparing"));
        arena.buildAsync(services.plugin(), this::prepared, this::preparationFailed);
    }

    private void prepared() {
        if (done || !running()) return;
        if (connected.isEmpty()) {
            services.session().endGame(EndReason.ALL_PLAYERS_LEFT);
            return;
        }
        world = arena.world();
        services.arena().applyStandardGameRules(world);
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(arena.timeOfDay());
        world.setStorm(false);
        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        preparing = false;
        bulletKnockback.start(services.plugin());
        regeneration.start(services.plugin());
        hitHealth.start(services.plugin());
        FinalsCombatContext combat = combatContext();
        weapons = new FinalsWeapons(combat);
        gadgets = new FinalsGadgets(combat);
        cashouts = new FinalsCashoutPhysics(arena);
        mapJumpPads = new FinalsMapJumpPads(arena);
        mapJumpPads.start();
        highlights = new FinalsHighlights(services.plugin());
        tabList = new FinalsTabList(services.plugin());
        if (gameMaster != null) gameEvents = new FinalsGameEvents(gameEventsContext());
        if (revivalMode) {
            revives = new FinalsRevives(reviveContext());
        }
        respawnInput = new FinalsRespawnInput(
                services.plugin(), this::requestRespawn, this::requestDownedLoadout);
        dropInput =
                new FinalsDropInput(
                        services.plugin(),
                        id -> {
                            Player player = Bukkit.getPlayer(id);
                            if (player != null) dropPressed(player);
                        });
        services.sidebar().setNameTagsVisible(false);
        services.arena()
                .applyBorder(world, arena.center(), arena.borderSize(), arena.borderSize(), 0);
        bar =
                services.bossBars()
                        .create(text("name"), BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        for (Player player : services.participants().onlinePlayers()) {
            if (connected.contains(player.getUniqueId())) {
                spawn(player);
                if (loadouts != null) loadouts.open(player);
            } else if (isGameMaster(player)) spawnGameMaster(player);
        }
        services.messages()
                .broadcast(
                        text(
                                "rules",
                                rules.cashoutValue(),
                                rules.targetScore(),
                                rules.cashoutTicks() / 20,
                                rules.stealTicks() / 20));
        services.messages().broadcast(text("controls"));
        if (services.isTest()) services.messages().broadcast(text("practice"));
        updateHighlights();
        updateTabList();
        updateHud();
        // Last startup operation: core's failed-start cleanup does not invoke onEnd.
        ticker = Bukkit.getScheduler().runTaskTimer(services.plugin(), this::tick, 1, 1);
    }

    private void preparationFailed(RuntimeException error) {
        if (done) return;
        services.plugin()
                .getLogger()
                .log(java.util.logging.Level.SEVERE, "Could not prepare Finals map", error);
        services.messages().broadcast(text("map.prepare_failed", I18n.message(error)));
        services.session().endGame(EndReason.ABORTED);
    }

    @Override
    public void onSecondTick(long secondsElapsed) {
        if (preparing && connected.isEmpty() && running())
            services.session().endGame(EndReason.ALL_PLAYERS_LEFT);
    }

    private boolean running() {
        return !done && services.session().isRunning();
    }

    private boolean active(Player player) {
        return running()
                && !preparing
                && connected.contains(player.getUniqueId())
                && !withdrawn.contains(player.getUniqueId())
                && !respawnAt.containsKey(player.getUniqueId())
                && !player.isDead()
                && player.getGameMode() == GameMode.SURVIVAL
                && player.getWorld().equals(world);
    }

    private boolean fighting(Player player) {
        return state != null && !state.isFinished() && active(player);
    }

    private void updateHighlights() {
        if (highlights == null) return;
        List<Player> players =
                connected.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
        highlights.update(
                arena,
                state == null ? null : state.phase(),
                state == null ? -1 : state.station(),
                state != null && state.phase() == FinalsState.Phase.VAULT_OPENING
                        ? Math.max(0, (state.phaseDeadline() - tick + 19) / 20)
                        : (rules.vaultOpenTicks() + 19) / 20,
                state != null && state.phase() == FinalsState.Phase.CASHOUT
                        ? Math.max(0, (state.phaseDeadline() - tick + 19) / 20)
                        : (rules.cashoutTicks() + 19) / 20,
                players,
                roster,
                player -> revives != null && revives.location(player.getUniqueId()) != null
                        ? revives.location(player.getUniqueId())
                        : deathLocations.getOrDefault(player.getUniqueId(), player.getLocation()),
                player -> downed(player.getUniqueId()), cashboxHighlightPoint(),
                state == null ? null : state.carrier(), hitHealth::visible);
    }

    private Location cashboxHighlightPoint() {
        if (state == null) return null;
        if (state.carrier() != null) {
            Player carrier = Bukkit.getPlayer(state.carrier());
            return carrier == null ? null : carrier.getLocation().add(0, .8, 0);
        }
        return cashbox != null && cashbox.isValid() ? cashbox.getLocation() : cashboxLocation;
    }

    private boolean protectedSpawn(Player player) {
        return tick < protectedUntil.getOrDefault(player.getUniqueId(), 0L);
    }

    private Location pullGoal(Player source, Vector direction) {
        Vector horizontal = direction.clone().setY(0);
        if (horizontal.lengthSquared() < 1e-8) horizontal = new Vector(0, 0, 1);
        return source.getLocation().add(horizontal.normalize().multiply(1.5));
    }

    private void updateTabList() {
        if (tabList == null) return;
        List<Player> players =
                connected.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
        if (gameMasterOnline) {
            Player master = Bukkit.getPlayer(gameMaster);
            if (master != null) {
                players = new ArrayList<>(players);
                players.add(master);
            }
        }
        tabList.update(world, players, roster, classes, kills.snapshot(), respawnAt.keySet(), gameMaster);
    }

    private boolean canObjective(Player player) {
        return fighting(player) && !protectedSpawn(player) && !reviveOccupied(player);
    }

    private boolean near(Player player, Location location, double distance) {
        return location != null
                && player.getWorld().equals(location.getWorld())
                && player.getLocation().distanceSquared(location) <= distance * distance;
    }

    private void teleport(Player player, Location target) {
        internalTeleport = true;
        try {
            player.teleport(target);
        } finally {
            internalTeleport = false;
        }
    }

    private void forceRespawn(Player player) {
        internalTeleport = true;
        try {
            if (player.isDead()) player.spigot().respawn();
        } finally {
            internalTeleport = false;
        }
    }

    private int spawnIndex(UUID id) {
        int index = 0;
        for (UUID other : roster.keySet()) {
            if (other.equals(id)) return index;
            if (roster.get(other).equals(roster.get(id))) index++;
        }
        return index;
    }

    private Location spawnLocation(UUID id) {
        int team = roster.get(id);
        if (state != null) return arena.spawn(team, spawnIndex(id));
        // Pick once per team so loadout changes and countdown rejoins keep the same anchor.
        return startingSpawns.computeIfAbsent(team, ignored -> {
            List<Location> candidates = arena.spawnLocations(team);
            return candidates.get(java.util.concurrent.ThreadLocalRandom.current()
                    .nextInt(candidates.size())).clone();
        }).clone();
    }

    private void spawn(Player player) {
        spawn(player, true);
    }

    private void spawn(Player player, boolean applySelectedLoadout) {
        UUID id = player.getUniqueId();
        if (preparing || !roster.containsKey(id) || teamWipes.locked(id, tick)) return;
        if (teamWipes.wave(id) != null) prepareTeamRespawns();
        boolean respawning = respawnAt.containsKey(id);
        if (respawning && loadouts != null && FinalsLoadouts.viewing(player)) player.closeInventory();
        forceRespawn(player);
        release(player);
        rememberVitals(player);
        FinalsLoadouts.Choice selected =
                loadouts == null ? FinalsLoadouts.Choice.defaults() : loadouts.choice(id);
        // A revive continues the same life/kit; queued edits wait for a real respawn.
        FinalsLoadouts.Choice choice = applySelectedLoadout
                ? selected : equippedLoadouts.getOrDefault(id, selected);
        classes.put(id, choice.playerClass());
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(choice.playerClass().health());
        player.getAttribute(Attribute.SCALE).setBaseValue(choice.playerClass().scale());
        player.setWalkSpeed(choice.playerClass().walkSpeed());
        if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
        services.playerState().resetForRound(player, GameMode.SURVIVAL);
        player.setHealth(
                Math.min(
                        choice.playerClass().health(),
                        player.getAttribute(Attribute.MAX_HEALTH).getValue()));
        player.setAbsorptionAmount(0);
        player.setNoDamageTicks(0);
        player.setGlowing(false);
        player.setSaturation(20);
        regeneration.reset(id, tick);
        hitHealth.forget(id);
        respawnAt.remove(id);
        kills.spawn(id);
        deathLocations.remove(id);
        protectedUntil.put(id, Math.max(tick, countdownEnd) + protectionTicks);
        var inventory = player.getInventory();
        weapons.equip(player, choice.weapon());
        gadgets.equip(player, choice);
        equippedLoadouts.put(id, choice);
        inventory.setItem(7, null);
        if (loadouts != null) inventory.setItem(8, loadouts.button(player));
        for (Material material :
                List.of(
                        Material.LEATHER_HELMET,
                        Material.LEATHER_CHESTPLATE,
                        Material.LEATHER_LEGGINGS,
                        Material.LEATHER_BOOTS)) {
            ItemStack armor = gear(material);
            LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
            meta.setColor(roster.get(id) == 0 ? Color.RED : Color.BLUE);
            armor.setItemMeta(meta);
            switch (material) {
                case LEATHER_HELMET -> inventory.setHelmet(armor);
                case LEATHER_CHESTPLATE -> inventory.setChestplate(armor);
                case LEATHER_LEGGINGS -> inventory.setLeggings(armor);
                case LEATHER_BOOTS -> inventory.setBoots(armor);
                default -> {}
            }
        }
        inventory.setHeldItemSlot(0);
        Location shared = teamRespawnPositions.get(id);
        teleport(player, shared == null ? spawnLocation(id) : shared.clone());
        lastGrounded.put(id, player.getLocation().clone());
        teamRespawnPositions.remove(id);
        teamWipes.spawn(id);
        gadgetSicknessUntil.remove(id);
        if (revives != null) revives.remove(id);
        if (respawnInput != null) respawnInput.track(player);
        if (respawning) objectiveSound(player.getLocation(), "minecraft:entity.enderman.teleport", 1.2f);
        if (dropInput != null) dropInput.track(player);
        if (state == null) freeze(player);
        services.sidebar().setNameColor(player, color(roster.get(id)));
        player.showBossBar(bar);
    }

    private static ItemStack gear(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.setUnbreakable(true));
        return item;
    }

    private ItemStack cashboxItem(Player viewer) {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        item.editMeta(meta -> {
            meta.displayName(text("item.cashbox"));
            meta.lore(List.of(text("item.cashbox_lore")));
        });
        return viewer == null ? item : I18n.item(item, viewer);
    }

    private void carryVisual(Player player, boolean carrying) {
        if (carrying && gadgets != null) gadgets.reveal(player);
        player.setGlowing(carrying || (gadgets != null && gadgets.isSonarRevealed(player)));
        player.getInventory()
                .setItemInOffHand(carrying ? cashboxItem(player) : new ItemStack(Material.AIR));
        if (carrying)
            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 0, false, false));
        else player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    private void removeCashbox() {
        if (cashbox != null) cashbox.remove();
        cashbox = null;
        cashboxThrower = null;
        cashboxPreviousCenter = null;
    }

    private void ensureCashbox() {
        if (state.phase() != FinalsState.Phase.CASHBOX_AVAILABLE) {
            removeCashbox();
            return;
        }
        if (cashbox != null && cashbox.isValid()) {
            cashboxLocation = cashbox.getLocation();
            if (!safeObjectiveLocation(cashboxLocation)) {
                removeCashbox();
                cashboxLocation = cashboxReturnPoint();
            } else if (depositThrownCashbox()) return;
        } else if (cashbox != null) {
            removeCashbox();
            cashboxLocation = cashboxReturnPoint();
        }
        if (!safeObjectiveLocation(cashboxLocation)) cashboxLocation = cashboxReturnPoint();
        if (cashbox == null || !cashbox.isValid()) {
            createCashbox(new Vector());
        }
        for (UUID id : connected) {
            Player player = Bukkit.getPlayer(id);
            if (player == null
                    || !canObjective(player)
                    || (id.equals(recentDropper) && tick < pickupAfter)) continue;
            if (near(player, cashboxLocation, 1.8) && state.pickUp(id, tick)) {
                removeCashbox();
                carryVisual(player, true);
                services.messages()
                        .broadcast(
                                text("picked_up", player.displayName(), teamName(roster.get(id))));
                break;
            }
        }
    }

    private void createCashbox(Vector velocity) {
        cashboxThrower = null;
        cashboxPreviousCenter = null;
        cashbox = world.dropItem(cashboxLocation, cashboxItem(null));
        cashbox.setVelocity(velocity);
        cashbox.setGravity(true);
        cashbox.setInvulnerable(true);
        cashbox.setGlowing(true);
        cashbox.setCanPlayerPickup(false);
        cashbox.setCanMobPickup(false);
        cashbox.setUnlimitedLifetime(true);
    }

    private boolean safeObjectiveLocation(Location point) {
        return arena != null && point != null && arena.contains(point)
                && (arena.deathHeight() == null || point.getY() >= arena.deathHeight());
    }

    private boolean supportedRecovery(Location point) {
        if (!safeObjectiveLocation(point)) return false;
        var support = point.clone().subtract(0, .15, 0).getBlock();
        return !support.isPassable() && !support.isLiquid();
    }

    private void rememberGround(Player player, Location point) {
        if (player.isOnGround() && supportedRecovery(point))
            lastGrounded.put(player.getUniqueId(), point.clone());
    }

    private Location recoveryPoint(UUID id) {
        Location point = lastGrounded.get(id);
        if (supportedRecovery(point)) return point.clone();
        Integer team = roster.get(id);
        if (team != null) for (Location spawn : arena.spawnLocations(team))
            if (supportedRecovery(spawn)) return spawn.clone();
        return arena.vault().clone().add(0, 1, 0);
    }

    private Location cashboxReturnPoint() {
        Location anchor = supportedRecovery(cashboxRecovery) ? cashboxRecovery
                : arena.vault().clone().add(0, 1, 0);
        return anchor.clone().add(0, .25, 0);
    }

    /** Sweep native item motion so a fast throw cannot skip an entire station between ticks. */
    private boolean depositThrownCashbox() {
        if (cashboxThrower == null || cashbox == null || !cashbox.isValid()) return false;
        var bounds = cashbox.getBoundingBox();
        Vector current = bounds.getCenter();
        Vector previous = cashboxPreviousCenter == null ? current : cashboxPreviousCenter;
        cashboxPreviousCenter = current.clone();
        Vector movement = current.clone().subtract(previous);
        double distance = movement.length();
        double limit = distance;
        if (distance > 1e-8) {
            var wall = world.rayTraceBlocks(previous.toLocation(world), movement.clone().normalize(),
                    distance, FluidCollisionMode.NEVER, true);
            if (wall != null) limit = Math.max(0, wall.getHitPosition().distance(previous) - 1e-7);
        }
        int station = FinalsCashboxFlight.hit(previous, current,
                new Vector(bounds.getWidthX() / 2, bounds.getHeight() / 2, bounds.getWidthZ() / 2),
                List.of(arena.station(0).toVector(), arena.station(1).toVector()), limit);
        UUID thrower = cashboxThrower;
        if (station < 0 || !state.depositThrown(thrower, station, tick)) return false;
        removeCashbox();
        cashboxLocation = null;
        announceDeposit(thrower, station);
        updateHighlights();
        updateHud();
        return true;
    }

    private void announceDeposit(UUID player, int station) {
        updateCashoutMusic();
        services.messages().broadcast(text("deposited", teamName(roster.get(player)), station == 0 ? "A" : "B"));
        var title = net.kyori.adventure.title.Title.title(
                text("cashout.started.title", teamName(roster.get(player))),
                text("cashout.started.subtitle", station == 0 ? "A" : "B"),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(150),
                        java.time.Duration.ofMillis(1800), java.time.Duration.ofMillis(350)));
        for (Player contestant : feedbackPlayers()) {
            contestant.showTitle(title);
            contestant.playSound(contestant.getLocation(), "minecraft:block.beacon.activate", .8f, 1.1f);
        }
    }

    private Collection<Player> feedbackPlayers() {
        return services.participants() == null ? List.of()
                : services.participants().onlinePlayers().stream()
                        .filter(player -> connected.contains(player.getUniqueId())).toList();
    }

    private void updateCashoutMusic() {
        if (done || cashouts == null || state == null || state.phase() != FinalsState.Phase.CASHOUT) {
            cashoutMusic.clear();
            return;
        }
        cashoutMusic.update(cashouts.soundSource(state.station()),
                services.participants().onlinePlayers().stream()
                        .filter(player -> connected.contains(player.getUniqueId()) || isGameMaster(player))
                        .toList());
    }

    private void objectiveSound(Location location, String sound, float pitch) {
        if (location != null && location.getWorld() != null)
            location.getWorld().playSound(location, sound, .8f, pitch);
    }

    static Vector cashboxThrowVelocity(Vector direction) {
        return direction.clone().normalize().multiply(1.3).add(new Vector(0, .12, 0));
    }

    private void throwCashbox(Player player) {
        UUID id = player.getUniqueId();
        if (state == null || !id.equals(state.carrier()) || !canObjective(player)) return;
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        var wall = world.rayTraceBlocks(eye, direction, .9, FluidCollisionMode.NEVER, true);
        double distance =
                wall == null
                        ? .9
                        : Math.max(0, wall.getHitPosition().distance(eye.toVector()) - .2);
        Location release = eye.clone().add(direction.clone().multiply(distance));
        if (!arena.contains(release)) release = player.getLocation().add(0, .4, 0);
        if (!state.drop(id, tick)) return;
        rememberGround(player, player.getLocation());
        cashboxRecovery = recoveryPoint(id);
        cashboxLocation = release;
        recentDropper = id;
        pickupAfter = tick + 20;
        carryVisual(player, false);
        removeCashbox();
        createCashbox(cashboxThrowVelocity(direction));
        cashboxThrower = id;
        cashboxPreviousCenter = cashbox.getBoundingBox().getCenter();
        services.messages().broadcast(text("thrown", player.displayName()));
        objectiveSound(release, "minecraft:entity.snowball.throw", .8f);
    }

    private void dropCashbox(Player player) {
        if (state == null || !player.getUniqueId().equals(state.carrier())) return;
        if (state.drop(player.getUniqueId(), tick)) {
            rememberGround(player, player.getLocation());
            cashboxRecovery = recoveryPoint(player.getUniqueId());
            cashboxThrower = null;
            cashboxPreviousCenter = null;
            cashboxLocation =
                    safeObjectiveLocation(player.getLocation())
                            ? player.getLocation().add(0, .4, 0)
                            : cashboxReturnPoint();
            recentDropper = player.getUniqueId();
            pickupAfter = tick + 20;
            carryVisual(player, false);
            services.messages().broadcast(text("dropped", player.displayName()));
        }
    }

    private void tick() {
        if (!running()) return;
        tick++;
        if (connected.isEmpty()) {
            services.session().endGame(EndReason.ALL_PLAYERS_LEFT);
            return;
        }
        if (cashouts != null) cashouts.tick();
        if (state == null && tick >= countdownEnd) {
            releaseAll();
            state = new FinalsState(roster, tick, rules);
            for (UUID id : roster.keySet()) {
                if (withdrawn.contains(id)) state.withdraw(id, tick);
                else if (!connected.contains(id)) state.disconnect(id, tick);
            }
            if (loadouts != null)
                for (Player player : services.participants().onlinePlayers()) {
                    if (connected.contains(player.getUniqueId()) && FinalsLoadouts.viewing(player))
                        player.closeInventory();
                }
            services.messages().broadcast(text("go"));
            if (revivalMode) services.messages().broadcast(text("revive.instructions"));
            announceVault();
            announceCashout(0);
            announceCashout(1);
            services.messages()
                    .playSound(
                            services.participants().onlinePlayers(),
                            Sound.BLOCK_NOTE_BLOCK_PLING,
                            1,
                            1.6f);
        }
        if (state == null) {
            if (tick % 2 == 0) updateHighlights();
            if (tick % 5 == 0) updateTabList();
            if (tick % 20 == 0) updateHud();
            return;
        }
        advanceObjective();
        updateCashoutMusic();
        if (state.isFinished()) {
            finish();
            return;
        }
        prepareTeamRespawns();
        for (UUID id : List.copyOf(respawnAt.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !connected.contains(id)) continue;
            if (revivalMode && teamWipes.dead(id)) {
                boolean wasDead = player.isDead();
                forceRespawn(player);
                if (player.getGameMode() != GameMode.SPECTATOR) waitForRespawn(player);
                if (wasDead && respawnInput != null) respawnInput.track(player);
                continue;
            }
            if (tick >= respawnAt.get(id)) spawn(player);
            else if (!player.isDead() && player.getGameMode() != GameMode.SPECTATOR)
                waitForRespawn(player);
        }
        for (UUID id : List.copyOf(state.stealDeadlines().keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null
                    || !canObjective(player)
                    || !player.isSneaking()
                    || state.station() < 0
                    || !near(player, arena.station(state.station()), 3)) {
                if (player == null) state.cancelSteal(id);
                else interruptSteal(player);
            } else if (state.completeSteal(id, tick)) {
                services.messages().broadcast(text("stolen", teamName(roster.get(id))));
                objectiveSound(arena.station(state.station()), "minecraft:block.enchantment_table.use", 1.1f);
            }
        }
        if (weapons != null) weapons.tick();
        if (mapJumpPads != null) mapJumpPads.tick(services.participants().onlinePlayers(), this::fighting, tick);
        if (revives != null) revives.tick();
        if (gadgets != null) gadgets.tick();
        if (gameEvents != null) gameEvents.tick();
        for (UUID id : connected) {
            Player player = Bukkit.getPlayer(id);
            if (player != null)
                regeneration.tick(player, classes.getOrDefault(id, FinalsClass.MEDIUM));
        }
        updateStealSiren();
        ensureCashbox();
        if (tick % 2 == 0) updateHighlights();
        if (tick % 5 == 0) {
            updateActionBars();
            updateTabList();
        }
        if (!services.isTest()) {
            boolean both =
                    connected.stream().anyMatch(id -> roster.get(id) == 0)
                            && connected.stream().anyMatch(id -> roster.get(id) == 1);
            if (both) missingTeamSince = -1;
            else if (missingTeamSince < 0) {
                missingTeamSince = tick;
                services.messages().broadcast(text("reconnect_grace"));
            } else if (tick - missingTeamSince >= 300) {
                services.session().endGame(EndReason.ALL_PLAYERS_LEFT);
                return;
            }
        }
        if (tick % 20 == 0) {
            ordnance.values()
                    .removeIf(entity -> {
                        if (!entity.isValid()) return true;
                        if (entity instanceof Projectile && entity.getTicksLived() > 200) {
                            entity.remove();
                            return true;
                        }
                        return false;
                    });
            updateHud();
        }
    }

    private void prepareTeamRespawns() {
        for (FinalsTeamWipes.Wave wave : teamWipes.due(tick)) {
            Location shared = arena.spawn(wave.team(), java.util.concurrent.ThreadLocalRandom.current().nextInt(3)).clone();
            for (UUID member : wave.members()) {
                if (withdrawn.contains(member)) continue;
                teamRespawnPositions.put(member, shared.clone());
                respawnAt.put(member, wave.deadline());
            }
            teamWipes.release(wave);
        }
    }

    private void advanceObjective() {
        int beforeRed = state.score(0), beforeBlue = state.score(1);
        int completedStation = state.station();
        FinalsState.Phase before = state.phase();
        state.tick(tick);
        if (before == FinalsState.Phase.VAULT_OPENING
                && state.phase() == FinalsState.Phase.CASHBOX_AVAILABLE) {
            cashboxLocation = arena.vault().add(0, 1, 0);
            cashboxRecovery = cashboxLocation.clone();
            services.messages().broadcast(text("vault_open"));
            objectiveSound(arena.vault(), "minecraft:block.chest.open", 1.1f);
        }
        boolean paid = false;
        for (int team = 0; team < 2; team++) {
            if (state.score(team) > (team == 0 ? beforeRed : beforeBlue)) {
                paid = true;
                cashboxLocation = null;
                services.messages()
                        .broadcast(
                                text(
                                        "paid",
                                        teamName(team),
                                        rules.cashoutValue(),
                                        state.score(team)));
                if (completedStation >= 0)
                    objectiveSound(arena.station(completedStation), "minecraft:entity.player.levelup", 1.2f);
            }
        }
        if (paid && !state.isFinished()) {
            arena.nextCashout(completedStation);
            if (cashouts != null) cashouts.replace(completedStation);
            announceCashout(completedStation);
            arena.nextVault();
            announceVault();
        }
    }

    private void announceCashout(int station) {
        Location location = arena.station(station);
        services.messages()
                .broadcast(
                        text(
                                "cashout_spawned",
                                station == 0 ? "A" : "B",
                                location.getBlockX(),
                                location.getBlockY(),
                                location.getBlockZ()));
    }

    private void announceVault() {
        Location location = arena.vault();
        services.messages()
                .broadcast(
                        text(
                                "vault_spawned",
                                location.getBlockX(),
                                location.getBlockY(),
                                location.getBlockZ()));
        objectiveSound(location, "minecraft:block.beacon.power_select", 1.3f);
    }

    private void waitForRespawn(Player player) {
        if (player.isDead()) return;
        services.playerState().resetForRound(player, GameMode.SPECTATOR);
        long remaining = Math.max(0, respawnAt.getOrDefault(player.getUniqueId(), tick) - tick);
        services.playerState().makeSpectator(player, (int) Math.min(60, (remaining + 19) / 20 + 1));
        teleport(player, arena.center().add(0, 10, 0));
        freeze(player);
    }

    private void freeze(Player player) {
        frozen.computeIfAbsent(
                player.getUniqueId(),
                id ->
                        new Frozen(
                                player.getLocation().clone(),
                                player.getWalkSpeed(),
                                player.getFlySpeed()));
        player.setWalkSpeed(0);
        player.setFlySpeed(0);
        player.setVelocity(new Vector());
    }

    private void release(Player player) {
        Frozen previous = frozen.remove(player.getUniqueId());
        if (previous != null) {
            player.setWalkSpeed(previous.walkSpeed());
            player.setFlySpeed(previous.flySpeed());
        }
    }

    private void releaseAll() {
        for (UUID id : List.copyOf(frozen.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) release(player);
        }
        frozen.clear();
    }

    private void finish() {
        result =
                List.of(
                        state.isDraw()
                                ? text("draw")
                                : text("winner", teamName(state.winningTeams().iterator().next())),
                        text("score", teamName(0), state.score(0)),
                        text("score", teamName(1), state.score(1)));
        services.session().endGame(EndReason.FINISHED);
    }

    @Override
    public void onEnd(EndReason reason) {
        if (done) return;
        done = true;
        cashoutMusic.clear();
        updateStealSiren();
        if (arena != null) arena.cancelBuild();
        if (ticker != null) ticker.cancel();
        if (cashouts != null) cashouts.clear();
        if (mapJumpPads != null) mapJumpPads.clear();
        if (highlights != null) highlights.clear();
        if (tabList != null) tabList.clear();
        if (dropInput != null) dropInput.clear();
        if (revives != null) revives.clear();
        if (respawnInput != null) respawnInput.clear();
        bulletKnockback.close();
        regeneration.close();
        hitHealth.close();
        if (gameEvents != null) gameEvents.clear();
        releaseAll();
        if (weapons != null) weapons.clear();
        if (gadgets != null) gadgets.clear();
        restoreAllVitals();
        if (loadouts != null)
            for (UUID id : connected) {
                Player viewer = Bukkit.getPlayer(id);
                if (viewer != null && FinalsLoadouts.viewing(viewer)) viewer.closeInventory();
            }
        if (gameMasterOnline) {
            Player master = Bukkit.getPlayer(gameMaster);
            if (master != null && FinalsEventCommands.isMenu(master.getOpenInventory().getTopInventory()))
                master.closeInventory();
        }
        removeCashbox();
        for (Entity entity : ordnance.values()) entity.remove();
        ordnance.clear();
        if (reason == EndReason.FINISHED && state != null && state.isFinished())
            for (UUID id : state.winners()) services.award("finals", id, 3);
        for (UUID id : connected) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) carryVisual(player, false);
        }
        respawnAt.clear();
        teamWipes.clear();
        teamRespawnPositions.clear();
        startingSpawns.clear();
        lastGrounded.clear();
        cashboxRecovery = null;
        gadgetSicknessUntil.clear();
        deathLocations.clear();
        protectedUntil.clear();
        equippedLoadouts.clear();
    }

    @Override
    public List<Component> summary() {
        return result;
    }

    @Override
    public Component globalResult() {
        return result.isEmpty() ? null : text("global_result", result.getFirst());
    }

    private void updateHud() {
        if (done || bar == null) return;
        long remaining =
                Math.max(
                        0,
                        ((state == null ? countdownEnd : state.matchDeadline()) - tick + 19) / 20);
        bar.name(
                state == null
                        ? text("countdown", remaining)
                        : text("timer", TimeFormat.clock(remaining)));
        bar.progress(
                Math.clamp(
                        (float) remaining
                                * 20
                                / (state == null ? countdownEnd : rules.matchTicks()),
                        0,
                        1));
        if (state != null && state.phase() == FinalsState.Phase.CASHOUT) {
            bar.name(
                    text(
                            "cashout_bar",
                            state.station() == 0 ? "A" : "B",
                            teamName(state.ownerTeam()),
                            Math.max(0, (state.phaseDeadline() - tick + 19) / 20)));
            bar.progress(
                    Math.clamp(
                            (float) (state.phaseDeadline() - tick) / rules.cashoutTicks(), 0, 1));
            bar.color(state.ownerTeam() == 0 ? BossBar.Color.RED : BossBar.Color.BLUE);
        } else bar.color(BossBar.Color.YELLOW);
        for (UUID id : connected) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            List<Component> lines = new ArrayList<>();
            lines.add(text("your_team", teamName(roster.get(id))));
            for (int team = 0; team < 2; team++)
                lines.add(text("score", teamName(team), state == null ? 0 : state.score(team)));
            lines.add(text("target", rules.targetScore()));
            lines.add(text("match_time", TimeFormat.clock(remaining)));
            lines.add(
                    text(
                            "loadout.current",
                            classes.getOrDefault(id, FinalsClass.MEDIUM).display()));
            if (state != null) {
                lines.add(text("phase." + state.phase().name().toLowerCase(Locale.ROOT)));
                if (state.phaseDeadline() >= 0)
                    lines.add(
                            text(
                                    "objective_timer",
                                    Math.max(0, (state.phaseDeadline() - tick + 19) / 20)));
                if (state.station() >= 0)
                    lines.add(
                            text(
                                    "station_owner",
                                    state.station() == 0 ? "A" : "B",
                                    teamName(state.ownerTeam())));
                Location target =
                        state.phase() == FinalsState.Phase.CARRIED
                                ? arena.station(0)
                                : state.phase() == FinalsState.Phase.CASHOUT
                                        ? arena.station(state.station())
                                        : state.phase() == FinalsState.Phase.CASHBOX_AVAILABLE
                                                ? cashboxLocation
                                                : arena.vault();
                if (target != null && player.getWorld().equals(world)) {
                    if (id.equals(state.carrier())
                            && player.getLocation().distanceSquared(arena.station(1))
                                    < player.getLocation().distanceSquared(target))
                        target = arena.station(1);
                    lines.add(text("distance", (int) player.getLocation().distance(target)));
                }
            }
            if (respawnAt.containsKey(id))
                lines.add(respawnStatus(id));
            else if (protectedSpawn(player)) lines.add(text("protected"));
            services.sidebar().update(player, text("name"), lines);
            player.showBossBar(bar);
        }
        updateGameMasterHud();
    }

    private void interruptSteal(Player player) {
        if (state != null && state.cancelSteal(player.getUniqueId()))
            player.sendMessage(text("steal_cancelled"));
        updateStealSiren();
    }

    private void updateStealSiren() {
        if (!running()
                || state == null
                || state.phase() != FinalsState.Phase.CASHOUT
                || state.stealDeadlines().isEmpty()) {
            stealSirenAt = 0;
            stealSirenHigh = false;
            return;
        }
        if (tick < stealSirenAt) return;
        world.playSound(
                arena.station(state.station()).clone().add(0, .75, 0),
                "minecraft:block.note_block.pling",
                1.6f,
                stealSirenHigh ? 1.25f : .7f);
        stealSirenHigh = !stealSirenHigh;
        stealSirenAt = tick + 6;
    }

    private void updateActionBars() {
        if (state == null) return;
        for (UUID id : connected) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            Component status;
            Long deadline = state.stealDeadlines().get(id);
            if (respawnAt.containsKey(id)) status = respawnStatus(id);
            else if (revives != null && revives.status(player) != null) status = revives.status(player);
            else if (deadline != null) {
                int progress =
                        (int)
                                Math.clamp(
                                        (rules.stealTicks() - Math.max(0, deadline - tick))
                                                * 10
                                                / rules.stealTicks(),
                                        0,
                                        10);
                status =
                        text(
                                "steal_progress",
                                "▰".repeat(progress) + "▱".repeat(10 - progress),
                                Math.max(0, (deadline - tick + 19) / 20));
            } else if (protectedSpawn(player)) status = text("protected");
            else if (gadgets != null && gadgets.usingHealingBeam(player)) status = gadgets.hud(player);
            else if (id.equals(state.carrier())) status = text("carrying");
            else if (weapons != null && player.getInventory().getHeldItemSlot() == 0)
                status = weapons.hud(player);
            else if (gadgets != null
                    && player.getInventory().getHeldItemSlot() >= 1
                    && player.getInventory().getHeldItemSlot() <= 4) status = gadgets.hud(player);
            else status = text("loadout.quick_controls");
            player.sendActionBar(status);
        }
    }

    private Component respawnStatus(UUID id) {
        long remaining = Math.max(0, (respawnAt.getOrDefault(id, tick) - tick + 19) / 20);
        if (downed(id)) return remaining == 0 ? text("respawn.ready") : text("respawn.unlock", remaining);
        return text("respawn", remaining);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (loadouts != null
                && running()
                && connected.contains(player.getUniqueId())
                && event.getHand() == EquipmentSlot.HAND
                && player.getInventory().getHeldItemSlot() == 8
                && (event.getAction() == Action.RIGHT_CLICK_AIR
                        || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (weapons != null) weapons.stopFiring(player.getUniqueId());
            if (revives != null) revives.interrupt(player.getUniqueId());
            loadouts.open(player);
            return;
        }
        if (!fighting(player)) {
            event.setCancelled(true);
            return;
        }
        // Pressure plates have no hand and must not interrupt a revive or held weapon.
        if (event.getAction() == Action.PHYSICAL) {
            event.setCancelled(true);
            if (event.getClickedBlock() != null) interactMapControl(event, event.getClickedBlock());
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }
        if (revives != null) {
            boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
            if (right && gadgets != null && gadgets.usingDefibrillator(player)) {
                event.setCancelled(true);
                gadgets.interact(event);
                return;
            }
            if (right && revives.interact(player)) {
                event.setCancelled(true);
                return;
            }
            if (reviveOccupied(player)) {
                revives.interrupt(player.getUniqueId());
                event.setCancelled(true);
                return;
            }
            if (!right) revives.interrupt(player.getUniqueId());
        }
        var block = event.getClickedBlock();
        if (block != null) event.setUseInteractedBlock(Event.Result.DENY);
        if (block != null && event.getAction() == Action.LEFT_CLICK_BLOCK
                && canBreakGlass(player, block)) {
            event.setUseInteractedBlock(Event.Result.ALLOW);
            // Paper's block-dig path also requires this result, even with an empty hand.
            event.setUseItemInHand(Event.Result.ALLOW);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.RIGHT_CLICK_AIR) {
            if (weapons != null) weapons.interact(event);
            return;
        }
        if (block != null && interactDoor(event, block)) return;
        if (block != null && interactMapControl(event, block)) return;
        UUID id = player.getUniqueId();
        int station =
                cashouts != null
                        ? cashouts.aimedStation(player, 4.5)
                        : block == null ? -1 : arena.stationAt(block);
        if ((block != null && arena.isVault(block)) || station >= 0) {
            event.setCancelled(true);
            if (station >= 0) {
                interactStation(player, station);
                return;
            }
            if (!canObjective(player) || !near(player, block.getLocation().add(.5, .5, .5), 4))
                return;
            if (state.activateVault(id, tick)) {
                services.messages().broadcast(text("vault_opening"));
                objectiveSound(arena.vault(), "minecraft:block.ender_chest.open", .9f);
            }
            updateHud();
            return;
        }
        if (gadgets != null && gadgets.interact(event)) return;
        if (weapons != null) weapons.interact(event);
    }

    @Override
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Player && event.getHand() == EquipmentSlot.HAND
                && gadgets != null && gadgets.interactHealingTarget(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (revives != null && revives.matches(event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                Player player = event.getPlayer();
                if (gadgets != null && gadgets.usingDefibrillator(player)) gadgets.interactStatue(player);
                else revives.interact(player);
            }
            return;
        }
        if (cashouts == null) return;
        int station = cashouts.station(event.getRightClicked());
        if (station < 0) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.HAND && cashouts.aimedStation(player, 4.5) == station)
            interactStation(player, station);
    }

    private void interactStation(Player player, int station) {
        if (!canObjective(player) || !near(player, arena.station(station), 4)) return;
        UUID id = player.getUniqueId();
        if (state.deposit(id, station, tick)) {
            carryVisual(player, false);
            announceDeposit(id, station);
        } else if (station == state.station()
                && station >= 0
                && player.isSneaking()
                && near(player, arena.station(station), 3)
                && state.beginSteal(id, tick)) {
            if (weapons != null) weapons.stopFiring(id);
            if (gadgets != null) gadgets.reveal(player);
            player.sendMessage(text("steal_started"));
            updateStealSiren();
        } else if (station >= 0
                && station == state.station()
                && state.ownerTeam() != roster.get(id)) {
            player.sendMessage(text("steal_hint"));
        }
        updateHud();
    }

    private Player attacker(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) return player;
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player)
            return player;
        return null;
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player source = attacker(event.getDamager());
        if (!applyingCombatDamage && event.getDamager() instanceof Player player && reviveOccupied(player)) {
            revives.interrupt(player.getUniqueId());
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            if (source != null) {
                event.setCancelled(true);
                if (!applyingCombatDamage && event.getDamager() instanceof Player
                        && fighting(source) && !protectedSpawn(source) && weapons != null)
                    weapons.melee(event);
            }
            return;
        }
        if (!fighting(victim)
                || protectedSpawn(victim)
                || source == null
                || !fighting(source)
                || protectedSpawn(source)
                || (arePlayersFriendly(source, victim)
                        && !(applyingCombatDamage && source.equals(victim)))) {
            event.setCancelled(true);
            return;
        }
        if (!applyingCombatDamage && event.getDamager() instanceof Player && weapons != null) {
            weapons.melee(event);
            return;
        }
        if (event.isCancelled()) return;
        if (gadgets != null && gadgets.blocksDamage(victim, source,
                damageOrigin == null ? source.getLocation() : damageOrigin)) {
            event.setCancelled(true);
            return;
        }
        if (applyingBulletDamage && event.getDamage() > 0 && weapons != null
                && weapons.canReflectBullet(victim, source)) {
            event.setCancelled(true);
            weapons.reflectBullet(victim, source, event.getDamage());
            return;
        }
        if (weapons != null)
            event.setDamage(event.getDamage() * weapons.incomingMultiplier(victim, source));
        if (event.getFinalDamage() > 0) {
            interruptSteal(victim);
            if (revives != null) revives.interrupt(victim.getUniqueId());
            if (gadgets != null) gadgets.reveal(victim);
        }
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }
        if (!fighting(player) || protectedSpawn(player)) {
            event.setCancelled(true);
            return;
        }
        // Entity damage is validated by the higher-priority hook before interrupting a steal.
        if (!(event instanceof EntityDamageByEntityEvent)
                && !event.isCancelled()
                && event.getFinalDamage() > 0) {
            interruptSteal(player);
            if (revives != null) revives.interrupt(player.getUniqueId());
        }
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!running() || preparing || !connected.contains(id) || respawnAt.containsKey(id)) return;
        event.deathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        if (state != null && !state.isFinished()) {
            Player killer = player.getKiller();
            UUID credited = killer == null ? null : killer.getUniqueId();
            if (credited != null && (!connected.contains(credited) || withdrawn.contains(credited)))
                credited = null;
            if (kills.death(id, credited, roster)) {
                services.messages().broadcast(text("kill", killer.displayName(), player.displayName()));
                killer.playSound(killer.getLocation(), "minecraft:entity.experience_orb.pickup", .8f, 1.4f);
            }
        }
        dropCashbox(player);
        if (state != null) state.death(id, tick);
        updateStealSiren();
        deathLocations.put(id, player.getLocation().clone());
        regeneration.forget(id);
        hitHealth.forget(id);
        if (weapons != null) weapons.forget(id);
        if (gadgets != null) gadgets.forget(id);
        clearOrdnance(id);
        gadgetSicknessUntil.remove(id);
        respawnAt.put(id, tick + (revivalMode ? 160 : respawnTicks));
        if (state != null && !state.isFinished()) {
            FinalsTeamWipes.Wave wave = teamWipes.death(id, tick, roster, withdrawn);
            if (wave != null) {
                for (UUID member : wave.members()) respawnAt.put(member, wave.deadline());
                if (revives != null) for (UUID member : wave.members()) revives.remove(member);
                for (Player teammate : feedbackPlayers()) {
                    if (wave.members().contains(teammate.getUniqueId())) teammate.sendMessage(text("team_wipe", 16));
                }
            } else if (revivalMode && revives != null) revives.down(player, deathLocations.get(id));
        }
        player.sendMessage(respawnStatus(id));
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (running() && !preparing && (connected.contains(event.getPlayer().getUniqueId()) || isGameMaster(event.getPlayer())))
            event.setRespawnLocation(arena.center().add(0, 10, 0));
    }

    @Override
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        // Respawning replaces the client's entity used for listener-relative music.
        cashoutMusic.remove(event.getPlayer());
        if (isGameMaster(event.getPlayer())) spawnGameMaster(event.getPlayer());
        else if (running() && respawnAt.containsKey(event.getPlayer().getUniqueId()))
            waitForRespawn(event.getPlayer());
    }

    private void clearOrdnance(UUID owner) {
        ordnance.values()
                .removeIf(entity -> {
                    Player source = attacker(entity);
                    if (source != null && source.getUniqueId().equals(owner)) {
                        entity.remove();
                        return true;
                    }
                    return !entity.isValid();
                });
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player
                && (!fighting(player) || protectedSpawn(player))) event.setCancelled(true);
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            if (!fighting(player) || protectedSpawn(player)) event.setCancelled(true);
            else ordnance.put(event.getEntity().getUniqueId(), event.getEntity());
        }
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        if (!canBreakGlass(event.getPlayer(), event.getBlock())) event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    private boolean canBreakGlass(Player player, Block block) {
        Material material = block.getType();
        boolean glass = material == Material.GLASS || material == Material.GLASS_PANE
                || material == Material.TINTED_GLASS
                || material.name().endsWith("_STAINED_GLASS")
                || material.name().endsWith("_STAINED_GLASS_PANE");
        return glass && canObjective(player) && arena.isCover(block)
            && player.getInventory().getItemInMainHand().getType() == Material.AIR;
    }

    private boolean interactMapControl(PlayerInteractEvent event, Block block) {
        boolean physical = event.getAction() == Action.PHYSICAL;
        String material = block.getType().name();
        boolean control = physical ? material.endsWith("_PRESSURE_PLATE")
                : event.getAction() == Action.RIGHT_CLICK_BLOCK
                        && (material.endsWith("_BUTTON") || material.endsWith("_FENCE_GATE"));
        if (!control) return false;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!canObjective(player) || !arena.contains(block.getLocation())
                || !physical && !near(player, block.getLocation().add(.5, .5, .5), 4.5)) return true;
        // Let vanilla handle gate direction, button release ticks and redstone updates,
        // while keeping the held weapon/gadget from activating on the same click.
        event.setUseInteractedBlock(Event.Result.ALLOW);
        event.setUseItemInHand(Event.Result.DENY);
        if (!physical && weapons != null) weapons.stopFiring(player.getUniqueId());
        return true;
    }

    private boolean interactDoor(PlayerInteractEvent event, Block block) {
        var data = block.getBlockData();
        if (!(data instanceof Door) && !(data instanceof TrapDoor)) return false;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!canObjective(player) || !arena.contains(block.getLocation())
                || !near(player, block.getLocation().add(.5, .5, .5), 4.5)) return true;
        Block otherHalf = null;
        Door otherData = null;
        if (data instanceof Door door) {
            otherHalf = block.getRelative(door.getHalf() == Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
            if (!arena.contains(otherHalf.getLocation()) || otherHalf.getType() != block.getType()
                    || !(otherHalf.getBlockData() instanceof Door matching)) return true;
            otherData = matching;
        }
        Openable openable = (Openable) data;
        boolean open = !openable.isOpen();
        openable.setOpen(open);
        block.setBlockData(data, false);
        if (otherData != null) {
            otherData.setOpen(open);
            otherHalf.setBlockData(otherData, false);
        }
        if (weapons != null) weapons.stopFiring(player.getUniqueId());
        String material = block.getType().name();
        String family = material.contains("IRON") ? "iron" : material.contains("COPPER") ? "copper" : "wooden";
        String kind = data instanceof Door ? "door" : "trapdoor";
        block.getWorld().playSound(block.getLocation(),
                "minecraft:block." + family + "_" + kind + (open ? ".open" : ".close"), .8f, 1f);
        return true;
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!running() || state == null || !ordnance.containsKey(event.getEntity().getUniqueId()))
            event.blockList().clear();
        else event.blockList().removeIf(block -> !arena.isCover(block));
        event.setYield(0);
    }

    @Override
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    @Override
    public void onDropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
        dropPressed(event.getPlayer());
    }

    @Override
    public void onPickupItem(EntityPickupItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
        if (revives != null) revives.interrupt(event.getPlayer().getUniqueId());
        if (revives != null && revives.carrying(event.getPlayer().getUniqueId())) return;
        if (weapons != null && fighting(event.getPlayer())) weapons.reload(event.getPlayer());
    }

    @Override
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (revives != null) revives.interrupt(event.getPlayer().getUniqueId());
        if (weapons != null) weapons.stopFiring(event.getPlayer().getUniqueId());
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        if (revives != null && event.getWhoClicked() instanceof Player player) revives.interrupt(player.getUniqueId());
        event.setCancelled(true);
    }

    @Override
    public void onInventoryDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (revives != null && event.getPlayer() instanceof Player player) revives.interrupt(player.getUniqueId());
        if (!FinalsLoadouts.isMenu(event.getInventory()) && !FinalsEventCommands.isMenu(event.getInventory())) event.setCancelled(true);
        else if (weapons != null) weapons.stopFiring(event.getPlayer().getUniqueId());
    }

    @Override
    public void onCraftItem(CraftItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (internalTeleport || !running() || !connected.contains(event.getPlayer().getUniqueId()))
            return;
        if (active(event.getPlayer())) rememberGround(event.getPlayer(), event.getFrom());
        Frozen hold = frozen.get(event.getPlayer().getUniqueId());
        if (hold != null) {
            Location to = hold.anchor().clone();
            to.setYaw(event.getTo().getYaw());
            to.setPitch(event.getTo().getPitch());
            event.setTo(to);
            event.getPlayer().setVelocity(new Vector());
        } else if (active(event.getPlayer()) && (!arena.contains(event.getTo())
                || (arena.deathHeight() != null && event.getTo().getY() < arena.deathHeight()))) {
            // Protected floor prevents accidental void deaths; escaping the bounds costs a life.
            dropCashbox(event.getPlayer());
            event.getPlayer().setHealth(0);
        }
    }

    @Override
    public void onPlayerJump(PlayerJumpEvent event) {
        if (running() && active(event.getPlayer()) && supportedRecovery(event.getFrom()))
            lastGrounded.put(event.getPlayer().getUniqueId(), event.getFrom().clone());
        if (running() && frozen.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().setVelocity(new Vector());
        }
    }

    @Override
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (internalTeleport || !running()) return;
        if (isGameMaster(event.getPlayer())) {
            if (preparing || event.getTo() == null || !arena.contains(event.getTo())) event.setCancelled(true);
        } else if (connected.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @Override
    public boolean arePlayersFriendly(Player first, Player second) {
        Integer a = roster.get(first.getUniqueId()), b = roster.get(second.getUniqueId());
        return a != null && a.equals(b);
    }

    @Override
    public boolean isInGame(Player player) {
        return !done && connected.contains(player.getUniqueId());
    }

    @Override
    public boolean mayFollowPlayers(Player participant) {
        return isGameMaster(participant);
    }

    @Override
    public Location spectatorLocation(Player viewer) {
        return arena == null || preparing ? null : arena.center().add(0, 12, 0);
    }

    @Override
    public void onTeamChange(Player player, TeamColor ignored) {
        if (isGameMaster(player)) {
            services.participants().find(player.getUniqueId()).ifPresent(p -> p.setTeam(null));
            player.sendMessage(text("gm.instructions"));
            return;
        }
        Integer team = roster.get(player.getUniqueId());
        if (team != null) {
            services.participants()
                    .find(player.getUniqueId())
                    .ifPresent(p -> p.setTeam(color(team)));
            player.sendMessage(text("teams_locked"));
        }
    }

    private void disconnect(Player player, boolean permanent) {
        UUID id = player.getUniqueId();
        if (done) return;
        cashoutMusic.remove(player);
        if (id.equals(gameMaster)) {
            gameMasterOnline = false;
            if (permanent) withdrawn.add(id);
            if (gameEvents != null) gameEvents.stop();
            if (tabList != null) tabList.remove(id);
            if (highlights != null) highlights.remove(id);
            player.closeInventory();
            return;
        }
        if (!roster.containsKey(id)) return;
        dropCashbox(player);
        connected.remove(id);
        regeneration.forget(id);
        hitHealth.forget(id);
        if (revives != null) revives.disconnected(id, permanent);
        if (respawnInput != null) respawnInput.remove(id);
        if (dropInput != null) dropInput.remove(id);
        if (highlights != null) highlights.remove(id);
        if (tabList != null) tabList.remove(id);
        if (permanent) {
            withdrawn.add(id);
            classes.remove(id);
            equippedLoadouts.remove(id);
            teamWipes.withdraw(id);
            teamRespawnPositions.remove(id);
            gadgetSicknessUntil.remove(id);
        }
        if (state != null) {
            if (permanent) state.withdraw(id, tick);
            else state.disconnect(id, tick);
        }
        updateStealSiren();
        if (weapons != null) weapons.forget(id);
        if (gadgets != null) gadgets.forget(id);
        clearOrdnance(id);
        restoreVitals(player);
        if (permanent || (!teamWipes.dead(id) && teamWipes.wave(id) == null
                && !teamRespawnPositions.containsKey(id))) {
            deathLocations.remove(id);
            respawnAt.remove(id);
        }
        protectedUntil.remove(id);
        carryVisual(player, false);
    }

    @Override
    public void onParticipantQuit(Player player) {
        disconnect(player, false);
    }

    @Override
    public void onParticipantLeave(Player player) {
        disconnect(player, true);
    }

    @Override
    public void onParticipantRejoin(Player player) {
        UUID id = player.getUniqueId();
        if (done || withdrawn.contains(id)) return;
        if (id.equals(gameMaster)) {
            gameMasterOnline = true;
            if (!preparing) spawnGameMaster(player);
            return;
        }
        if (!roster.containsKey(id)) return;
        connected.add(id);
        if (preparing) return;
        if (dropInput != null) dropInput.track(player);
        if (respawnInput != null) respawnInput.track(player);
        if (state == null) {
            spawn(player);
            if (loadouts != null) loadouts.open(player);
        } else {
            state.reconnect(id, tick);
            respawnAt.putIfAbsent(id, tick + respawnTicks);
            waitForRespawn(player);
        }
    }
}
