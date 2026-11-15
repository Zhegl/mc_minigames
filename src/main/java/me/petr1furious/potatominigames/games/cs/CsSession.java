package me.petr1furious.potatominigames.games.cs;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.*;
import me.petr1furious.potatominigames.core.hud.PrivatePlayerGlow;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.core.text.TimeFormat;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

/** Independent competitive bomb-defusal match on the imported Dust II world. */
final class CsSession implements GameSession, Listener {
    private record Notice(Component text, long until) {}

    private record Drop(
            Item item,
            CsGun gun,
            CsMatch.Utility utility,
            boolean kit,
            boolean bomb,
            long expires) {}

    private record PendingDrop(int slot, int life, CsGun gun) {}

    private static final class Channel {
        final boolean plant;
        final Location anchor;
        final int site;
        final long end;
        long pressed, nextPlantSound;

        Channel(boolean plant, Location anchor, int site, long end, long pressed) {
            this.plant = plant;
            this.anchor = anchor;
            this.site = site;
            this.end = end;
            this.pressed = pressed;
            this.nextPlantSound = pressed + 8;
        }
    }

    private static final class Shop implements InventoryHolder {
        final UUID owner;
        final Map<Integer, String> choices = new HashMap<>();
        Inventory inventory;

        Shop(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class Scores implements InventoryHolder {
        final UUID owner;
        Inventory inventory;

        Scores(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final SessionServices services;
    private final CsConfig config;
    private final CsGame owner;
    private final Random random = new Random();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, Float> walkSpeeds = new HashMap<>(), flySpeeds = new HashMap<>();
    private final Map<UUID, Location> frozen = new HashMap<>();
    private final Map<UUID, Long> deathAt = new HashMap<>();
    private final Map<UUID, Integer> handled = new HashMap<>(), cameraIndex = new HashMap<>();
    private final Map<UUID, PendingDrop> pendingDrops = new LinkedHashMap<>();
    private final Map<UUID, Channel> channels = new HashMap<>();
    private final Map<UUID, Drop> drops = new LinkedHashMap<>();
    private final Map<UUID, Notice> notices = new HashMap<>();
    private final Set<UUID> weaponHudShown = new HashSet<>();
    private final boolean deathmatch;
    private final boolean wingman;
    private final CsDeathmatch dm;
    private CsMatch match;
    private World world;
    private CsArena arena;
    private CsRadar radar;
    private CsCombat combat;
    private CsGrenades grenades;
    private PrivatePlayerGlow teammateGlow;
    // Chat arrives asynchronously; publish immutable membership, never read the live model there.
    private volatile Map<UUID, Integer> chatTeams = Map.of();
    private Item plantedBomb;
    private Location bombAt;
    private BossBar bar;
    private BukkitTask task;
    private long tick, missingSince = -1;
    private int roundSeen, versionSeen;
    private boolean built, done, internalTeleport, resetting, cameraSwitch, finished, ctBombKnown;
    private List<Component> result = List.of();

    CsSession(SessionServices services, CsConfig config, CsGame owner) {
        this(services, config, owner, false);
    }

    CsSession(SessionServices services, CsConfig config, CsGame owner, boolean deathmatch) {
        this(services, config, owner, deathmatch, false);
    }

    CsSession(
            SessionServices services,
            CsConfig config,
            CsGame owner,
            boolean deathmatch,
            boolean wingman) {
        if (deathmatch && wingman) throw new IllegalArgumentException("Incompatible CS modes");
        this.deathmatch = deathmatch;
        this.wingman = wingman;
        this.dm = deathmatch ? new CsDeathmatch() : null;
        this.services = services;
        this.config = config;
        this.owner = owner;
    }

    boolean deathmatch() {
        return deathmatch;
    }

    boolean wingman() {
        return wingman;
    }

    void attacking(Player p) {
        if (dm != null) dm.attack(p.getUniqueId());
    }

    JavaPlugin plugin() {
        return services.plugin();
    }

    World world() {
        return world;
    }

    CsArena arena() {
        return arena;
    }

    CsMatch match() {
        return match;
    }

    long now() {
        return tick;
    }

    Random random() {
        return random;
    }

    CsMatch.Actor actor(Player p) {
        return match == null ? null : match.actor(p.getUniqueId());
    }

    Player player(UUID id) {
        var a = match == null ? null : match.actor(id);
        return a != null && a.online ? Bukkit.getPlayer(id) : null;
    }

    List<Player> players() {
        return match == null
                ? List.of()
                : match.actors().stream()
                        .filter(a -> a.online)
                        .map(a -> Bukkit.getPlayer(a.id))
                        .filter(Objects::nonNull)
                        .toList();
    }

    List<Player> viewers() {
        return services.session().audiencePlayers();
    }

    boolean active(Player p) {
        return built
                && !done
                && actor(p) != null
                && match.live(p.getUniqueId())
                && !p.isDead()
                && p.getGameMode() == GameMode.SURVIVAL
                && p.getWorld().equals(world);
    }

    boolean canMove(Player p) {
        return active(p)
                && (match.phase == CsMatch.Phase.LIVE || match.phase == CsMatch.Phase.ROUND_END);
    }

    boolean canAct(Player p) {
        return active(p)
                && match.phase == CsMatch.Phase.LIVE
                && !channels.containsKey(p.getUniqueId());
    }

    CsGun gun(Player p) {
        var a = actor(p);
        return a == null ? null : a.gun(p.getInventory().getHeldItemSlot());
    }

    boolean shopOpen(Player p) {
        InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder();
        return holder instanceof Shop || holder instanceof Scores;
    }

    void actionBar(Player p, Component text) {
        notices.put(p.getUniqueId(), new Notice(text, tick + 20));
        weaponHud(p);
    }

    void weaponHud(Player p) {
        UUID id = p.getUniqueId();
        // Objective progress owns the action bar while planting/defusing.
        if (channels.containsKey(id)) return;
        Notice notice = notices.get(id);
        Component text = notice != null && tick < notice.until ? notice.text : null;
        CsGun gun =
                active(p)
                                && !shopOpen(p)
                                && (match.phase == CsMatch.Phase.LIVE
                                        || match.phase == CsMatch.Phase.FREEZE)
                        ? gun(p)
                        : null;
        if (gun != null) {
            Component ammo = CsItems.ammoHud(gun, tick, deathmatch);
            text = text == null ? ammo : tr("hud.weapon_notice", ammo, text);
        }
        if (text != null) {
            p.sendActionBar(text);
            weaponHudShown.add(id);
        } else if (weaponHudShown.remove(id)) p.sendActionBar(Component.empty());
    }

    private void clearWeaponHud(Player p) {
        notices.remove(p.getUniqueId());
        if (weaponHudShown.remove(p.getUniqueId())) p.sendActionBar(Component.empty());
    }

    boolean visible(Player viewer, Player target) {
        if (!active(viewer) || !active(target) || grenades.obscured(viewer, target)) return false;
        Vector delta =
                target.getEyeLocation().toVector().subtract(viewer.getEyeLocation().toVector());
        return delta.lengthSquared() < 4900
                && (delta.lengthSquared() < .01
                        || viewer.getEyeLocation().getDirection().dot(delta.normalize()) > .25)
                && viewer.hasLineOfSight(target);
    }

    Location objective(Player viewer) {
        var a = actor(viewer);
        if (a == null) return null;
        if (match.planted()) {
            if (match.side(a) == 0 || ctBombKnown) return bombAt;
            if (bombAt != null
                    && players().stream()
                            .filter(this::active)
                            .filter(p -> match.side(actor(p)) == 1)
                            .anyMatch(p ->
                                    p.getEyeLocation().distanceSquared(bombAt) < 576
                                            && p.hasLineOfSight(plantedBomb))) ctBombKnown = true;
            return ctBombKnown ? bombAt : null;
        }
        if (match.side(a) != 0) return null;
        Player carrier = match.carrier == null ? null : player(match.carrier);
        if (carrier != null) return carrier.getLocation();
        return drops.values().stream()
                .filter(d -> d.bomb)
                .map(d -> d.item.getLocation())
                .findFirst()
                .orElse(null);
    }

    private Component tr(String key, Object... args) {
        return CsItems.tr(key, args);
    }

    private Component name(UUID id) {
        return Component.text(names.getOrDefault(id, "?"));
    }

    private Component side(int side) {
        return tr(side == 0 ? "side.t" : "side.ct");
    }

    private Component team(int team) {
        return tr(team == 0 ? "team.red" : "team.blue");
    }

    private void say(Component text) {
        services.messages().broadcast(text);
    }

    private void showRoundResult() {
        int winner = match.lastRound.winner();
        Component message =
                tr(match.side(winner) == 0 ? "round_result.t" : "round_result.ct")
                        .decorate(TextDecoration.BOLD);
        var times =
                Title.Times.times(
                        Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(250));
        for (Player viewer : viewers()) {
            var participant = actor(viewer);
            NamedTextColor color =
                    participant == null
                            ? NamedTextColor.GOLD
                            : participant.team == winner
                                    ? NamedTextColor.GREEN
                                    : NamedTextColor.RED;
            // The subtitle line is conspicuous without the oversized primary title.
            viewer.showTitle(
                    Title.title(
                            Component.empty(), I18n.render(message.color(color), viewer), times));
        }
    }

    @Override
    public void onStart() throws CommandFailure {
        // Keep the generic color teams hidden too, including during roster refreshes.
        services.sidebar().setNameTagsVisible(false);
        List<me.petr1furious.potatominigames.core.player.Participant> roster =
                new ArrayList<>(services.participants().all());
        if (roster.isEmpty()
                || roster.size() > (deathmatch ? 16 : 10)
                || !services.isTest() && roster.size() < 2)
            throw new CommandFailure(tr(deathmatch ? "dm.error.roster" : "error.roster"));
        Collections.shuffle(roster, random);
        Map<UUID, Integer> teams = new LinkedHashMap<>();
        int[] counts = {0, 0};
        for (var p : roster) {
            int team =
                    p.team() == TeamColor.BLUE
                            ? 1
                            : p.team() == TeamColor.RED ? 0 : counts[0] <= counts[1] ? 0 : 1;
            if (!deathmatch && counts[team] >= 5) team = 1 - team;
            teams.put(p.id(), team);
            counts[team]++;
            names.put(p.id(), p.name());
            p.setTeam(deathmatch ? null : team == 0 ? TeamColor.RED : TeamColor.BLUE);
        }
        while (Math.abs(counts[0] - counts[1]) > 1) {
            int larger = counts[0] > counts[1] ? 0 : 1;
            for (var p : roster)
                if (teams.get(p.id()) == larger) {
                    teams.put(p.id(), 1 - larger);
                    p.setTeam(deathmatch ? null : larger == 0 ? TeamColor.BLUE : TeamColor.RED);
                    counts[larger]--;
                    counts[1 - larger]++;
                    break;
                }
        }
        match =
                new CsMatch(
                        teams,
                        config.half.get(),
                        (deathmatch ? config.dmCountdown.get() : config.freeze.get()) * 20,
                        (deathmatch ? config.dmDuration.get() : config.round.get()) * 20,
                        config.bomb.get() * 20,
                        config.buy.get() * 20,
                        services.isTest(),
                        deathmatch);
        owner.started(this, names.keySet());
        world = services.worlds().createVoid("cs_" + UUID.randomUUID().toString().replace("-", ""));
        services.arena().applyStandardGameRules(world);
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        arena = new CsArena(world, wingman);
        radar = new CsRadar(this);
        combat = new CsCombat(this);
        grenades = new CsGrenades(this);
        teammateGlow = new PrivatePlayerGlow(plugin());
        refreshChatTeams();
        bar =
                services.bossBars()
                        .create(tr("loading", 0), BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        Bukkit.getPluginManager().registerEvents(this, plugin());
        for (Player p : players()) {
            walkSpeeds.put(p.getUniqueId(), p.getWalkSpeed());
            flySpeeds.put(p.getUniqueId(), p.getFlySpeed());
            if (!p.isDead()) {
                p.setItemOnCursor(null);
                p.closeInventory();
                services.playerState().resetForRound(p, GameMode.SPECTATOR);
                freeze(p);
                p.showBossBar(bar);
            }
        }
        say(tr(deathmatch ? "dm.rules" : wingman ? "wingman.rules" : "rules"));
        say(tr("map_credit"));
        task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin(),
                                () -> {
                                    try {
                                        tick();
                                    } catch (RuntimeException failure) {
                                        plugin().getLogger()
                                                .log(
                                                        java.util.logging.Level.SEVERE,
                                                        "Counter-Strike match failed",
                                                        failure);
                                        services.session().endGame(EndReason.ABORTED);
                                    }
                                },
                                1,
                                1);
    }

    private void freeze(Player p) {
        frozen.put(p.getUniqueId(), p.getLocation().clone());
        p.setWalkSpeed(0);
        p.setFlySpeed(0);
        p.setVelocity(new Vector());
        p.setFallDistance(0);
    }

    private void release(Player p) {
        frozen.remove(p.getUniqueId());
        p.setWalkSpeed(walkSpeeds.getOrDefault(p.getUniqueId(), .2f));
        p.setFlySpeed(flySpeeds.getOrDefault(p.getUniqueId(), .1f));
        p.setVelocity(new Vector());
    }

    private void teleport(Player p, Location at) {
        if (combat != null) combat.resetMovement(p);
        boolean previous = internalTeleport;
        internalTeleport = true;
        try {
            p.teleport(at);
        } finally {
            internalTeleport = previous;
        }
    }

    private void respawn(Player p) {
        boolean previous = internalTeleport;
        internalTeleport = true;
        try {
            if (p.isDead()) p.spigot().respawn();
        } finally {
            internalTeleport = previous;
        }
    }

    private void tick() {
        if (done || !services.session().isRunning()) return;
        tick++;
        if (players().isEmpty()) {
            services.session().endGame(EndReason.ALL_PLAYERS_LEFT);
            return;
        }
        if (!built) {
            built = arena.buildSlice(4096);
            if (tick % 10 == 0) {
                bar.name(tr("loading", Math.round(arena.progress() * 100)));
                bar.progress(arena.progress());
            }
            if (built) {
                match.start(tick, random);
                newRound();
            }
            return;
        }
        if (!deathmatch && !match.practice) {
            boolean red = match.actors().stream().anyMatch(a -> a.online && a.team == 0),
                    blue = match.actors().stream().anyMatch(a -> a.online && a.team == 1);
            if (!red || !blue) {
                if (missingSince < 0) missingSince = tick;
                if (tick - missingSince >= 1200) {
                    match.forfeit(red ? 0 : 1);
                    finish();
                    return;
                }
            } else missingSince = -1;
        }
        for (var entry : List.copyOf(pendingDrops.entrySet())) {
            pendingDrops.remove(entry.getKey());
            Player p = player(entry.getKey());
            var a = match.actor(entry.getKey());
            var request = entry.getValue();
            if (p != null
                    && a.life == request.life
                    && a.alive
                    && (request.gun == null || a.gun(request.slot) == request.gun))
                dropSlot(p, request.slot);
        }
        for (Player p : players()) {
            var a = actor(p);
            if (a.alive && match.phase == CsMatch.Phase.LIVE && !arena.inside(p.getLocation())) {
                match.suicide(a.id);
                death(p);
                if (!p.isDead()) p.setHealth(0);
            }
            if (deathmatch
                    && !a.alive
                    && a.online
                    && match.phase == CsMatch.Phase.LIVE
                    && tick >= deathAt.getOrDefault(a.id, tick + CsDeathmatch.RESPAWN_TICKS)) {
                spawnDeathmatch(p);
                continue;
            }
            if (!a.alive && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) spectate(p);
            if (!deathmatch
                    && !a.alive
                    && p.isDead()
                    && tick >= deathAt.getOrDefault(a.id, tick + 60)) {
                respawn(p);
                spectate(p);
            }
            if (!deathmatch && !a.alive && p.getGameMode() == GameMode.SPECTATOR) camera(p);
        }
        if (match.phase == CsMatch.Phase.LIVE || match.phase == CsMatch.Phase.ROUND_END)
            combat.tick();
        if (match.phase == CsMatch.Phase.LIVE) {
            grenades.tick();
            if (!deathmatch) {
                channels();
                bombTick();
            }
        }
        match.tick(tick, random);
        if (match.number != roundSeen) {
            newRound();
            return;
        }
        if (match.version != versionSeen) {
            versionSeen = match.version;
            if (match.phase == CsMatch.Phase.LIVE) {
                for (Player p : players())
                    if (actor(p).alive) {
                        release(p);
                        if (dm != null) dm.protect(p.getUniqueId(), tick);
                    }
                say(tr(deathmatch ? "dm.fight" : "fight"));
            }
            if (match.phase == CsMatch.Phase.ROUND_END) {
                channels.clear();
                combat.roundEnded();
                grenades.clear();
                say(
                        tr(
                                "round_winner",
                                side(match.side(match.lastRound.winner())),
                                tr(
                                        "reason."
                                                + match.lastRound
                                                        .reason()
                                                        .name()
                                                        .toLowerCase(Locale.ROOT)),
                                match.score[0],
                                match.score[1]));
                showRoundResult();
                if (match.lastRound.reason() == CsMatch.Reason.EXPLOSION && bombAt != null) {
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, bombAt, 1);
                    world.playSound(bombAt, Sound.ENTITY_GENERIC_EXPLODE, 3, .7f);
                }
                clearBomb();
                // Keep the existing five-second round-end window playable for survivors.
                // newRound() will freeze them at their next spawn after saving their gear.
                for (Player p : players()) if (active(p)) release(p);
            }
            if (match.phase == CsMatch.Phase.HALFTIME) {
                say(tr("halftime"));
                for (Player p : players()) if (!p.isDead()) freeze(p);
            }
            if (match.phase == CsMatch.Phase.FINISHED) {
                finish();
                return;
            }
        }
        drops.values()
                .removeIf(d -> {
                    if (!d.item.isValid() || tick >= d.expires && !d.bomb) {
                        d.item.remove();
                        return true;
                    }
                    return false;
                });
        if (match.dropped && !match.planted())
            for (Drop drop : drops.values())
                if (drop.bomb && drop.item.getLocation().getY() < 73)
                    drop.item.teleport(arena.spawn(0, 2));
        if (!deathmatch && tick % 10 == 0) {
            Map<UUID, Integer> teams = new HashMap<>();
            for (var actor : match.actors()) if (actor.online) teams.put(actor.id, actor.team);
            teammateGlow.update(
                    world,
                    players(),
                    teams,
                    (viewer, target) ->
                            active(target)
                                    && viewer.canSee(target)
                                    && !grenades.obscured(viewer, target));
        }
        if (tick % 4 == 0) {
            notices.values().removeIf(notice -> tick >= notice.until);
            for (Player p : players()) weaponHud(p);
        }
        if (tick % 20 == 0) hud();
    }

    private void newRound() {
        if (deathmatch) {
            startDeathmatch();
            return;
        }
        resetting = true;
        try {
            ctBombKnown = false;
            roundSeen = match.number;
            versionSeen = match.version;
            channels.clear();
            pendingDrops.clear();
            deathAt.clear();
            handled.clear();
            combat.clear();
            grenades.clear();
            teammateGlow.clear();
            clearDrops();
            clearBomb();
            arena.reset();
            arena.shuffleSpawns(random);
            int[] index = {0, 0};
            for (Player p : players()) {
                release(p);
                respawn(p);
                if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
                p.setItemOnCursor(null);
                p.closeInventory();
                services.playerState().resetForRound(p, GameMode.SURVIVAL);
                var a = actor(p);
                p.setAbsorptionAmount(0);
                p.setNoDamageTicks(0);
                p.setHealth(20);
                p.setSaturation(20);
                CsItems.sync(p, match, radar.view);
                p.getInventory().setHeldItemSlot(a.primary != null ? 0 : 1);
                teleport(p, arena.spawn(match.side(a), index[match.side(a)]++));
                freeze(p);
                p.showBossBar(bar);
            }
            say(tr("round_start", match.number, match.score[0], match.score[1]));
            hud();
        } finally {
            resetting = false;
        }
    }

    private void finish() {
        if (deathmatch) {
            finishDeathmatch();
            return;
        }
        finished = true;
        List<Component> lines = new ArrayList<>();
        int winner =
                match.score[0] == match.score[1] ? -1 : match.score[0] > match.score[1] ? 0 : 1;
        lines.add(
                winner < 0
                        ? tr("draw", match.score[0], match.score[1])
                        : tr("winner", team(winner), match.score[0], match.score[1]));
        match.actors().stream()
                .sorted(
                        Comparator.<CsMatch.Actor>comparingInt(a -> a.kills)
                                .reversed()
                                .thenComparingInt(a -> a.deaths))
                .forEach(a -> {
                    lines.add(tr("standing", name(a.id), a.kills, a.deaths, a.assists, a.mvps));
                    if (a.online)
                        services.award("cs", a.id, winner < 0 ? 1 : a.team == winner ? 3 : 0);
                });
        result = List.copyOf(lines);
        services.session().endGame(EndReason.FINISHED);
    }

    void hurt(
            Player victim,
            UUID attacker,
            double damage,
            boolean head,
            boolean leg,
            CsWeapon weapon,
            CsMatch.KillCause cause) {
        if (!active(victim) || dm != null && dm.protectedSpawn(victim.getUniqueId(), tick)) return;
        boolean hadVest = actor(victim).armour > 0;
        var outcome = match.hurt(victim.getUniqueId(), attacker, damage, head, leg, weapon, cause);
        if (outcome.health() <= 0) return;
        var a = actor(victim);
        combat.tag(victim, a.armour > 0);
        victim.setNoDamageTicks(0);
        if (outcome.killed()) {
            death(victim);
            victim.setHealth(0);
        } else {
            if (hadVest && a.armour <= 0) CsItems.syncArmour(victim, match);
            victim.setHealth(Math.max(.01, a.health / 5));
            victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, .65f, 1);
        }
        if (attacker != null) {
            Player p = player(attacker);
            if (p != null)
                actionBar(p, tr(head ? "hit_head" : "hit", Math.round(outcome.health())));
        }
    }

    private void death(Player p) {
        clearWeaponHud(p);
        var a = actor(p);
        if (Objects.equals(handled.get(a.id), a.life)) return;
        handled.put(a.id, a.life);
        combat.stopScope(p);
        cancelChannel(a.id);
        pendingDrops.remove(a.id);
        deathAt.put(a.id, tick + 60);
        if (!deathmatch && match.dropBomb(a.id)) dropBomb(p.getLocation());
        if (!deathmatch && a.primary != null)
            addDrop(p.getLocation(), CsItems.gun(a.primary, null), a.primary, null, false, false);
        if (!deathmatch && a.secondary != null)
            addDrop(
                    p.getLocation(),
                    CsItems.gun(a.secondary, null),
                    a.secondary,
                    null,
                    false,
                    false);
        if (!deathmatch)
            a.utility.forEach((u, n) -> {
                for (int i = 0; i < n; i++)
                    addDrop(p.getLocation(), CsItems.utility(u, null), null, u, false, false);
            });
        if (!deathmatch && a.kit)
            addDrop(p.getLocation(), CsItems.kit(null), null, null, true, false);
        a.loseEquipment();
        say(
                CsItems.killMessage(
                        a, a.killedBy == null ? Component.empty() : name(a.killedBy), name(a.id)));
    }

    private void startDeathmatch() {
        roundSeen = match.number;
        versionSeen = match.version;
        arena.reset();
        for (Player p : players()) spawnDeathmatch(p);
        say(tr("dm.start", config.dmDuration.get() / 60));
        hud();
    }

    private void spawnDeathmatch(Player p) {
        if (!match.respawnDeathmatch(p.getUniqueId())) return;
        combat.stopScope(p);
        respawn(p);
        release(p);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.closeInventory();
        p.setItemOnCursor(null);
        services.playerState().resetForRound(p, GameMode.SURVIVAL);
        dm.equip(actor(p), tick);
        deathAt.remove(p.getUniqueId());
        pendingDrops.remove(p.getUniqueId());
        p.setAbsorptionAmount(0);
        p.setNoDamageTicks(0);
        p.setHealth(20);
        p.setSaturation(20);
        CsItems.sync(p, match, radar.view);
        p.getInventory().setHeldItemSlot(0);
        List<Location> opponents =
                players().stream()
                        .filter(other -> other != p && active(other))
                        .map(Player::getLocation)
                        .toList();
        teleport(p, arena.deathmatchSpawn(opponents, random));
        if (match.phase != CsMatch.Phase.LIVE) freeze(p);
        p.showBossBar(bar);
        actionBar(p, tr("dm.spawned"));
    }

    private Component deathmatchResult() {
        var ranking = CsDeathmatch.ranking(match);
        if (ranking.isEmpty() || ranking.getFirst().kills == 0) return tr("dm.draw");
        var first = ranking.getFirst();
        Component winners =
                Component.join(
                        net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ")),
                        ranking.stream()
                                .filter(a -> a.kills == first.kills && a.deaths == first.deaths)
                                .map(a -> name(a.id))
                                .toList());
        return tr("dm.winner", winners, first.kills, first.deaths);
    }

    private void finishDeathmatch() {
        finished = true;
        List<Component> lines = new ArrayList<>();
        lines.add(deathmatchResult());
        for (var actor : CsDeathmatch.ranking(match)) {
            int place = CsDeathmatch.place(match, actor);
            lines.add(tr("dm.standing", place, name(actor.id), actor.kills, actor.deaths));
            if (!services.isTest() && names.size() >= 2 && actor.kills > 0)
                services.award("cs", actor.id, Math.max(0, 4 - place));
        }
        result = List.copyOf(lines);
        services.session().endGame(EndReason.FINISHED);
    }

    private void deathmatchHud() {
        long left = Math.max(0, (match.until - tick + 19) / 20);
        Component status =
                match.phase == CsMatch.Phase.FREEZE
                        ? tr("dm.countdown", left)
                        : tr("dm.time", TimeFormat.clock(left));
        bar.name(status);
        bar.progress(
                (float)
                        Math.clamp(
                                left
                                        / (double)
                                                (match.phase == CsMatch.Phase.FREEZE
                                                        ? config.dmCountdown.get()
                                                        : config.dmDuration.get()),
                                0,
                                1));
        for (Player p : viewers()) {
            List<Component> lines = new ArrayList<>();
            lines.add(status);
            lines.add(tr("dm.players", match.actors().stream().filter(a -> a.online).count()));
            var a = actor(p);
            if (a != null) {
                lines.add(tr("dm.position", CsDeathmatch.place(match, a)));
                lines.add(tr("hud.kda", a.kills, a.deaths, a.assists));
                if (a.alive) {
                    lines.add(
                            tr(
                                    "hud.health",
                                    Math.round(a.health),
                                    Math.round(a.armour),
                                    tr(a.helmet ? "yes" : "no")));
                    if (dm.protectedSpawn(a.id, tick)) lines.add(tr("dm.protected"));
                } else
                    lines.add(
                            tr(
                                    "dm.respawn",
                                    Math.max(
                                            0,
                                            (deathAt.getOrDefault(a.id, tick) - tick + 19) / 20)));
            }
            lines.add(tr("dm.select_hint"));
            services.sidebar().update(p, tr("mode.deathmatch"), lines);
            p.showBossBar(bar);
            CsNameTags.update(p.getScoreboard(), match, names, null);
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Scores scores)
                fillDeathmatchScores(p, scores);
        }
    }

    private void openDeathmatchShop(Player p) {
        if (!built || done || !isInGame(p)) return;
        combat.stopScope(p);
        var a = actor(p);
        for (CsGun gun : new CsGun[] {a.primary, a.secondary}) if (gun != null) gun.heldUntil = -1;
        p.clearActiveItem();
        Shop shop = new Shop(p.getUniqueId());
        shop.inventory = Bukkit.createInventory(shop, 54, I18n.render(tr("dm.weapons"), p));
        for (var category : CsWeapon.Category.values())
            shop.inventory.setItem(
                    category.ordinal() * 9,
                    CsItems.item(
                            Material.PAPER,
                            "shop",
                            tr("buy.category." + category.name().toLowerCase(Locale.ROOT)),
                            List.of(tr("dm.free")),
                            p));
        for (var entry : CsShop.deathmatchSlots().entrySet()) {
            var weapon = entry.getValue();
            shop.inventory.setItem(
                    entry.getKey(),
                    CsItems.item(
                            weapon.material,
                            "shop",
                            tr("weapon." + weapon.id),
                            List.of(tr("dm.free"), tr("dm.selection_rules")),
                            p));
            shop.choices.put(entry.getKey(), weapon.id);
        }
        p.openInventory(shop.inventory);
    }

    private void selectDeathmatchWeapon(Player p, String id) {
        if (!built || done || !isInGame(p)) return;
        var weapon = CsWeapon.find(id);
        if (weapon == null) return;
        boolean applied = dm.select(match, p.getUniqueId(), weapon, tick);
        if (applied) {
            combat.stopScope(p);
            CsItems.sync(p, match, radar.view);
        }
        actionBar(p, tr(applied ? "dm.selected" : "dm.selected_next", tr("weapon." + weapon.id)));
    }

    private void fillDeathmatchScores(Player viewer, Scores scores) {
        scores.inventory.clear();
        int slot = 0;
        for (var a : CsDeathmatch.ranking(match)) {
            scores.inventory.setItem(
                    slot++,
                    CsItems.item(
                            a.alive ? Material.LIME_WOOL : Material.RED_WOOL,
                            "score",
                            name(a.id),
                            List.of(
                                    tr("dm.position", CsDeathmatch.place(match, a)),
                                    tr("hud.kda", a.kills, a.deaths, a.assists),
                                    tr(
                                            "score.status",
                                            tr(a.alive ? "status.alive" : "status.dead"))),
                            viewer));
        }
    }

    private void spectate(Player p) {
        if (p.isDead()) return;
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        services.playerState().resetForRound(p, GameMode.SPECTATOR);
        teleport(p, arena.spectator());
        freeze(p);
    }

    private void camera(Player p) {
        var a = actor(p);
        List<Player> allies =
                players().stream()
                        .filter(this::active)
                        .filter(other -> actor(other).team == a.team)
                        .toList();
        if (allies.isEmpty()) {
            if (p.getSpectatorTarget() != null) {
                cameraSwitch = true;
                try {
                    p.setSpectatorTarget(null);
                } finally {
                    cameraSwitch = false;
                }
            }
            if (!frozen.containsKey(a.id)) freeze(p);
            return;
        }
        Player target = allies.get(Math.floorMod(cameraIndex.getOrDefault(a.id, 0), allies.size()));
        if (p.getSpectatorTarget() != target) {
            release(p);
            cameraSwitch = true;
            try {
                p.setSpectatorTarget(target);
            } finally {
                cameraSwitch = false;
            }
        }
    }

    void cancelChannel(UUID id) {
        channels.remove(id);
    }

    @SuppressWarnings("deprecation")
    private void beginChannel(Player p) {
        var a = actor(p);
        if (!active(p) || match.phase != CsMatch.Phase.LIVE) return;
        boolean plant = match.side(a) == 0;
        int site = arena.site(p.getLocation());
        if (plant
                && (!Objects.equals(match.carrier, a.id)
                        || match.planted()
                        || site < 0
                        || !p.isOnGround())) return;
        if (!plant
                && (!match.planted()
                        || bombAt == null
                        || p.getEyeLocation().distanceSquared(bombAt) > 12.25)) return;
        if (!plant && !lookingAtBomb(p)) return;
        Channel channel = channels.get(a.id);
        if (channel != null) {
            channel.pressed = tick;
            return;
        }
        if (!plant && channels.entrySet().stream().anyMatch(e -> !e.getValue().plant)) return;
        if (!plant) ctBombKnown = true;
        long duration = plant ? 64 : a.kit ? 100 : 200;
        channels.put(
                a.id, new Channel(plant, p.getLocation().clone(), site, tick + duration, tick));
        CsBombAudio.play(
                viewers(),
                plant ? p.getLocation() : bombAt,
                plant ? CsBombAudio.Cue.PLANT_START : CsBombAudio.Cue.DEFUSE_START,
                plant ? 1.3f : .85f);
    }

    private boolean lookingAtBomb(Player p) {
        if (bombAt == null) return false;
        Vector delta =
                bombAt.clone().add(0, .2, 0).toVector().subtract(p.getEyeLocation().toVector());
        if (delta.lengthSquared() < .01) return true;
        if (p.getEyeLocation().getDirection().dot(delta.clone().normalize()) < .8) return false;
        return world.rayTraceBlocks(
                        p.getEyeLocation(),
                        delta.clone().normalize(),
                        Math.max(.01, delta.length() - .3),
                        FluidCollisionMode.NEVER,
                        true)
                == null;
    }

    private void channels() {
        for (var entry : List.copyOf(channels.entrySet())) {
            Player p = player(entry.getKey());
            Channel c = entry.getValue();
            if (p == null
                    || !active(p)
                    || tick - c.pressed > 6
                    || p.getInventory().getHeldItemSlot() != 7
                    || p.getLocation().distanceSquared(c.anchor) > .09
                    || c.plant && arena.site(p.getLocation()) != c.site
                    || !c.plant && !lookingAtBomb(p)) {
                channels.remove(entry.getKey());
                continue;
            }
            if (tick >= c.end) {
                if (c.plant) {
                    if (match.plant(p.getUniqueId(), c.site, tick)) {
                        bombAt = p.getLocation().clone().add(0, .1, 0);
                        CsBombAudio.play(viewers(), bombAt, CsBombAudio.Cue.PLANTED, 1.5f);
                        plantedBomb = world.dropItem(bombAt, CsItems.bomb(null));
                        plantedBomb.setGravity(false);
                        plantedBomb.setVelocity(new Vector());
                        plantedBomb.setInvulnerable(true);
                        plantedBomb.setPickupDelay(Integer.MAX_VALUE);
                        CsItems.sync(p, match, radar.view);
                        for (Player recipient : players())
                            recipient.sendMessage(
                                    match.side(actor(recipient)) == 0
                                            ? tr("planted_known", c.site == 0 ? "A" : "B")
                                            : tr("planted"));
                    }
                } else if (match.defuse(p.getUniqueId(), tick)) {
                    CsBombAudio.play(viewers(), bombAt, CsBombAudio.Cue.DEFUSED, .9f);
                    say(tr("defused", name(p.getUniqueId())));
                }
                channels.remove(entry.getKey());
            } else {
                if (c.plant && tick >= c.nextPlantSound) {
                    CsBombAudio.play(
                            viewers(),
                            c.anchor,
                            CsBombAudio.Cue.PLANT_KEYS,
                            (tick / 8) % 2 == 0 ? 1.2f : 1.5f);
                    c.nextPlantSound = tick + 8;
                }
                p.sendActionBar(
                        tr(
                                c.plant ? "planting" : "defusing",
                                String.format(Locale.ROOT, "%.1f", (c.end - tick) / 20.0)));
            }
        }
    }

    private void bombTick() {
        if (match.phase != CsMatch.Phase.LIVE || !match.planted() || bombAt == null) return;
        if (!ctBombKnown && tick % 5 == 0)
            players().stream()
                    .filter(this::active)
                    .filter(p -> match.side(actor(p)) == 1)
                    .findFirst()
                    .ifPresent(this::objective);
        long left = match.detonateAt - tick;
        if (left <= 0) {
            for (Player p : players())
                if (active(p)) {
                    double distance = p.getLocation().distance(bombAt);
                    if (distance < 45)
                        hurt(
                                p,
                                null,
                                500 * Math.exp(-Math.pow(distance / 23, 2)),
                                false,
                                false,
                                null,
                                CsMatch.KillCause.C4);
                }
            return;
        }
        int interval = left > 400 ? 20 : left > 200 ? 10 : left > 100 ? 5 : 2;
        if (tick % interval == 0)
            CsBombAudio.play(
                    viewers(),
                    bombAt,
                    CsBombAudio.Cue.BEEP,
                    (left < 100 ? 1.8f : 1.3f) * (match.bombSite == 1 ? .9f : 1));
    }

    private void clearBomb() {
        if (plantedBomb != null) plantedBomb.remove();
        plantedBomb = null;
        bombAt = null;
    }

    private void dropBomb(Location at) {
        addDrop(at, CsItems.bomb(null), null, null, false, true);
    }

    private void addDrop(
            Location at,
            ItemStack item,
            CsGun gun,
            CsMatch.Utility utility,
            boolean kit,
            boolean bomb) {
        if (gun != null) gun.switchAway();
        if (drops.size() >= 96) {
            var it = drops.entrySet().iterator();
            while (it.hasNext()) {
                var old = it.next();
                if (!old.getValue().bomb) {
                    old.getValue().item.remove();
                    it.remove();
                    break;
                }
            }
        }
        Item entity = world.dropItem(at.clone().add(0, .25, 0), CsItems.physical(item));
        entity.setPickupDelay(12);
        entity.setInvulnerable(true);
        drops.put(
                entity.getUniqueId(),
                new Drop(entity, gun, utility, kit, bomb, bomb ? Long.MAX_VALUE : tick + 1800));
    }

    private void clearDrops() {
        drops.values().forEach(d -> d.item.remove());
        drops.clear();
    }

    private void dropSlot(Player p, int slot) {
        if (deathmatch) return;
        var a = actor(p);
        if (a == null
                || !active(p)
                || match.phase != CsMatch.Phase.LIVE
                        && match.phase != CsMatch.Phase.FREEZE
                        && match.phase != CsMatch.Phase.ROUND_END) return;
        if (slot <= 1) {
            var gun = a.gun(slot);
            if (gun == null) return;
            combat.stopScope(p);
            gun.switchAway();
            addDrop(p.getLocation(), CsItems.gun(gun, null), gun, null, false, false);
            if (slot == 0) a.primary = null;
            else a.secondary = null;
        } else if (slot >= 3 && slot <= 6) {
            var u = CsMatch.Utility.values()[slot - 3];
            int n = a.utility.getOrDefault(u, 0);
            if (n == 0) return;
            if (n == 1) a.utility.remove(u);
            else a.utility.put(u, n - 1);
            addDrop(p.getLocation(), CsItems.utility(u, null), null, u, false, false);
        } else if (slot == 7) {
            if (match.side(a) == 0) {
                if (!match.dropBomb(a.id)) return;
                dropBomb(p.getLocation());
            } else {
                if (!a.kit) return;
                a.kit = false;
                addDrop(p.getLocation(), CsItems.kit(null), null, null, true, false);
            }
        }
        CsItems.sync(p, match, radar.view);
        p.clearActiveItem();
    }

    @Override
    public void onPickupItem(EntityPickupItemEvent event) {
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player p) || !active(p)) return;
        Drop drop = drops.get(event.getItem().getUniqueId());
        if (drop == null) return;
        var a = actor(p);
        if (drop.bomb) {
            if (!match.takeBomb(a.id)) return;
        } else if (drop.kit) {
            if (match.side(a) != 1 || a.kit) return;
            a.kit = true;
        } else if (drop.utility != null) {
            int n = a.utility.getOrDefault(drop.utility, 0);
            if (a.grenades() >= 4 || n >= (drop.utility == CsMatch.Utility.FLASH ? 2 : 1)) return;
            a.utility.put(drop.utility, n + 1);
        } else if (drop.gun != null) {
            var old = drop.gun.weapon.primary ? a.primary : a.secondary;
            // Drop the current slot with Q first; do not swap endlessly while standing on loot.
            if (old != null) return;
            if (drop.gun.weapon.primary) a.primary = drop.gun;
            else a.secondary = drop.gun;
            drop.gun.deploy(tick);
        }
        drops.remove(event.getItem().getUniqueId());
        event.getItem().remove();
        CsItems.sync(p, match, radar.view);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, .6f, 1);
    }

    private void openShop(Player p) {
        if (deathmatch) {
            openDeathmatchShop(p);
            return;
        }
        var a = actor(p);
        if (!built || !match.canBuy(a, tick) || !arena.buyZone(p.getLocation(), match.side(a))) {
            actionBar(p, tr("buy.closed", config.buy.get()));
            return;
        }
        cancelChannel(a.id);
        combat.stopScope(p);
        for (CsGun gun : new CsGun[] {a.primary, a.secondary})
            if (gun != null) {
                gun.heldUntil = -1;
                gun.scoped = false;
            }
        p.clearActiveItem();
        Shop shop = new Shop(a.id);
        shop.inventory =
                Bukkit.createInventory(shop, CsShop.SIZE, I18n.render(tr("buy.title", a.money), p));
        for (var category : CsWeapon.Category.values()) {
            Material color =
                    switch (category) {
                        case PISTOL -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                        case SMG -> Material.LIME_STAINED_GLASS_PANE;
                        case RIFLE -> Material.ORANGE_STAINED_GLASS_PANE;
                        case SNIPER -> Material.PURPLE_STAINED_GLASS_PANE;
                        case SHOTGUN -> Material.YELLOW_STAINED_GLASS_PANE;
                        case HEAVY -> Material.RED_STAINED_GLASS_PANE;
                    };
            ItemStack label =
                    CsItems.item(
                            color,
                            "shop",
                            tr("buy.category." + category.name().toLowerCase(Locale.ROOT)),
                            List.of(),
                            p);
            var group = CsShop.group(category);
            for (int column = group.column(); column < group.column() + group.width(); column++)
                for (int row = 0; row <= CsShop.WEAPON_ROWS; row++)
                    shop.inventory.setItem(row * 9 + column, label.clone());
        }
        for (var entry : CsShop.slots(match.side(a)).entrySet()) {
            CsWeapon weapon = entry.getValue();
            ItemStack item =
                    CsItems.item(
                            weapon.material,
                            "shop",
                            tr("weapon." + weapon.id),
                            CsItems.shopLore(weapon),
                            p);
            shop.inventory.setItem(entry.getKey(), item);
            shop.choices.put(entry.getKey(), weapon.id);
        }
        String[] gear = {"vest", "helmet", "kit", "he", "flash", "smoke", "fire"};
        Material[] icons = {
            Material.CHAINMAIL_CHESTPLATE,
            Material.IRON_HELMET,
            Material.SHEARS,
            Material.SLIME_BALL,
            Material.FIREWORK_STAR,
            Material.GRAY_DYE,
            Material.FIRE_CHARGE
        };
        for (int i = 0; i < gear.length; i++) {
            if (i == 2 && match.side(a) != 1) continue;
            int price = match.gearPrice(a, gear[i]);
            shop.inventory.setItem(
                    CsShop.GEAR_START + i,
                    CsItems.item(
                            icons[i],
                            "shop",
                            tr("buy." + gear[i]),
                            List.of(tr("buy.price", price)),
                            p));
            shop.choices.put(CsShop.GEAR_START + i, gear[i]);
        }
        p.openInventory(shop.inventory);
    }

    private void purchase(Player p, String item) {
        if (deathmatch) {
            selectDeathmatchWeapon(p, item);
            return;
        }
        var a = actor(p);
        if (a == null || !match.canBuy(a, tick) || !arena.buyZone(p.getLocation(), match.side(a))) {
            p.closeInventory();
            actionBar(p, tr("buy.closed", config.buy.get()));
            return;
        }
        CsWeapon weapon = CsWeapon.find(item);
        CsGun old = weapon == null ? null : weapon.primary ? a.primary : a.secondary;
        CsMatch.Buy status =
                weapon == null ? match.gear(a, item, tick) : match.weapon(a, weapon, tick);
        if (status == CsMatch.Buy.OK) {
            if (old != null)
                addDrop(p.getLocation(), CsItems.gun(old, null), old, null, false, false);
            CsItems.sync(p, match, radar.view);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .7f, 1.4f);
        } else actionBar(p, tr("buy.error." + status.name().toLowerCase(Locale.ROOT)));
        openShop(p);
    }

    private void openScores(Player p) {
        if (!built || done || !isInGame(p)) return;
        combat.stopScope(p);
        Scores scores = new Scores(p.getUniqueId());
        scores.inventory =
                Bukkit.createInventory(
                        scores, 27, I18n.render(tr(deathmatch ? "dm.score" : "score.title"), p));
        fillScores(p, scores);
        p.openInventory(scores.inventory);
    }

    private void fillScores(Player viewer, Scores scores) {
        if (deathmatch) {
            fillDeathmatchScores(viewer, scores);
            return;
        }
        var self = actor(viewer);
        for (int team = 0; team < 2; team++) {
            int slot = team * 18;
            scores.inventory.setItem(
                    slot++,
                    CsItems.item(
                            Material.PAPER,
                            "score",
                            tr("score.team", team(team), side(match.side(team)), match.score[team]),
                            List.of(tr("score.alive", aliveCount(team))),
                            viewer));
            for (var actor : match.actors()) {
                if (actor.team != team) continue;
                String status = !actor.online ? "offline" : actor.alive ? "alive" : "dead";
                Material icon =
                        !actor.online
                                ? Material.GRAY_WOOL
                                : actor.alive ? Material.LIME_WOOL : Material.RED_WOOL;
                List<Component> lore = new ArrayList<>();
                lore.add(tr("score.status", tr("status." + status)));
                lore.add(tr("hud.kda", actor.kills, actor.deaths, actor.assists));
                lore.add(tr("score.mvp", actor.mvps));
                if (self != null && actor.team == self.team) lore.add(tr("hud.cash", actor.money));
                scores.inventory.setItem(
                        slot++, CsItems.item(icon, "score", name(actor.id), lore, viewer));
            }
        }
    }

    private long aliveCount(int team) {
        return match.actors().stream().filter(a -> a.team == team && a.online && a.alive).count();
    }

    private void refreshChatTeams() {
        if (deathmatch) {
            chatTeams = Map.of();
            return;
        }
        Map<UUID, Integer> teams = new HashMap<>();
        for (var actor : match.actors()) if (actor.online) teams.put(actor.id, actor.team);
        chatTeams = Map.copyOf(teams);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeamChat(AsyncChatEvent event) {
        UUID sender = event.getPlayer().getUniqueId();
        Map<UUID, Integer> members = chatTeams;
        if (!members.containsKey(sender)) return;
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!text.startsWith("!")) return;
        event.setCancelled(true); // never let private text reach the global chat audience
        String message = text.substring(1).strip();
        if (message.isEmpty()) return;
        Bukkit.getScheduler()
                .runTask(
                        plugin(),
                        () -> {
                            var actor = match.actor(sender);
                            if (done || actor == null || !actor.online) return;
                            Component line =
                                    tr("chat.team", name(sender), Component.text(message))
                                            .color(NamedTextColor.AQUA);
                            for (Player recipient : players())
                                if (actor(recipient).team == actor.team)
                                    recipient.sendMessage(line);
                        });
    }

    void command(Player p, String action) {
        switch (action) {
            case "buy" -> openShop(p);
            case "score" -> openScores(p);
            case "reload" -> {
                if (built) combat.reload(p);
            }
            case "drop" -> {
                if (built) dropSlot(p, p.getInventory().getHeldItemSlot());
            }
            case "pause" -> {
                var a = actor(p);
                if (built && a != null && match.pause(a.team)) say(tr("pause", team(a.team)));
                else actionBar(p, tr("pause_denied"));
            }
            case "help" -> p.sendMessage(tr(deathmatch ? "dm.controls" : "controls"));
            default -> throw new IllegalArgumentException();
        }
    }

    private void hud() {
        if (!built) return;
        if (deathmatch) {
            deathmatchHud();
            return;
        }
        long left =
                Math.max(
                        0,
                        ((match.planted() && match.phase == CsMatch.Phase.LIVE
                                                ? match.detonateAt
                                                : match.until)
                                        - tick
                                        + 19)
                                / 20);
        bar.name(
                tr(
                        "bar",
                        side(0),
                        match.score[match.teamForSide(0)],
                        side(1),
                        match.score[match.teamForSide(1)],
                        tr("phase." + match.phase.name().toLowerCase(Locale.ROOT)),
                        TimeFormat.clock(left)));
        bar.progress(
                Math.clamp(
                        (float) left
                                / Math.max(
                                        1,
                                        match.planted()
                                                ? config.bomb.get()
                                                : match.phase == CsMatch.Phase.LIVE
                                                        ? config.round.get()
                                                        : config.freeze.get()),
                        0,
                        1));
        for (Player p : services.session().audiencePlayers()) {
            List<Component> lines = new ArrayList<>();
            var a = actor(p);
            lines.add(tr("hud.score", team(0), match.score[0], team(1), match.score[1]));
            lines.add(tr("hud.round", match.number, config.half.get() * 2));
            lines.add(
                    tr(
                            "hud.alive",
                            aliveCount(match.teamForSide(0)),
                            aliveCount(match.teamForSide(1))));
            lines.add(tr("hud.roster"));
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Scores scores
                    && scores.owner.equals(p.getUniqueId())) fillScores(p, scores);
            if (a != null) {
                lines.add(tr("hud.side", side(match.side(a))));
                lines.add(tr("hud.cash", a.money));
                lines.add(
                        tr(
                                "hud.health",
                                Math.round(a.health),
                                Math.round(a.armour),
                                a.helmet ? tr("yes") : tr("no")));
                lines.add(tr("hud.kda", a.kills, a.deaths, a.assists));
                CsGun gun = gun(p);
                if (gun != null)
                    lines.add(
                            tr(
                                    gun.reloadAt >= 0 ? "hud.reloading" : "hud.ammo",
                                    gun.magazine,
                                    gun.reserve));
                if (!a.alive) lines.add(tr("hud.dead"));
                if (Objects.equals(match.carrier, a.id)) lines.add(tr("hud.carrier"));
                if (a.kit) lines.add(tr("hud.kit"));
            }
            if (match.planted() && match.phase == CsMatch.Phase.LIVE)
                lines.add(
                        a != null && match.side(a) == 1 && !ctBombKnown
                                ? tr("hud.bomb_unknown", TimeFormat.clock(left))
                                : tr(
                                        "hud.bomb",
                                        match.bombSite == 0 ? "A" : "B",
                                        TimeFormat.clock(left)));
            services.sidebar().update(p, tr("name"), lines);
            p.showBossBar(bar);
            CsNameTags.update(p.getScoreboard(), match, names, a == null ? null : a.team);
        }
    }

    @Override
    public void onSecondTick(long elapsed) {
        if (!done) hud();
    }

    @Override
    public boolean isInGame(Player p) {
        return !done && actor(p) != null && actor(p).online;
    }

    @Override
    public boolean mayFollowPlayers(Player p) {
        return false;
    }

    @Override
    public boolean arePlayersFriendly(Player a, Player b) {
        return !deathmatch
                && actor(a) != null
                && actor(b) != null
                && actor(a).team == actor(b).team;
    }

    @Override
    public Location spectatorLocation(Player p) {
        return arena == null ? null : arena.spectator();
    }

    @Override
    public List<Component> summary() {
        return result;
    }

    @Override
    public Component globalResult() {
        if (!finished) return null;
        if (deathmatch) return deathmatchResult();
        int winner =
                match.score[0] == match.score[1] ? -1 : match.score[0] > match.score[1] ? 0 : 1;
        return winner < 0
                ? tr("draw", match.score[0], match.score[1])
                : tr("winner", team(winner), match.score[0], match.score[1]);
    }

    @Override
    public void onEnd(EndReason reason) {
        done = true;
        chatTeams = Map.of();
        if (teammateGlow != null) teammateGlow.clear();
        if (task != null) task.cancel();
        HandlerList.unregisterAll(this);
        owner.ended(this);
        if (grenades != null) grenades.clear();
        if (combat != null) combat.clear();
        if (radar != null) radar.clear();
        channels.clear();
        pendingDrops.clear();
        clearDrops();
        clearBomb();
        if (arena != null) arena.reset();
        for (Player p : players()) {
            release(p);
            p.clearActiveItem();
            clearWeaponHud(p);
        }
        services.bossBars().hideAll();
    }

    private void disconnect(Player p) {
        clearWeaponHud(p);
        var a = actor(p);
        if (a == null) return;
        if (combat != null) combat.stopScope(p);
        if (a.alive) {
            match.disconnect(a.id);
            death(p);
        } else match.disconnect(a.id);
        cancelChannel(a.id);
        pendingDrops.remove(a.id);
        release(p);
        if (grenades != null) grenades.release(a.id);
        if (teammateGlow != null) teammateGlow.remove(a.id);
        refreshChatTeams();
    }

    @Override
    public void onParticipantLeave(Player p) {
        disconnect(p);
    }

    @Override
    public void onParticipantQuit(Player p) {
        disconnect(p);
    }

    @Override
    public void onParticipantRejoin(Player p) {
        if (done || actor(p) == null) return;
        match.reconnect(p.getUniqueId());
        refreshChatTeams();
        deathAt.put(p.getUniqueId(), tick + 60);
        if (!p.isDead()) spectate(p);
        p.sendMessage(tr(deathmatch ? "dm.rejoin" : "rejoin"));
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        var a = actor(event.getPlayer());
        if (a == null) return;
        if (a.alive) match.suicide(a.id);
        death(event.getPlayer());
        event.deathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.deathScreenMessageOverride(
                I18n.render(
                        deathmatch
                                ? tr("dm.death_screen", CsDeathmatch.RESPAWN_TICKS / 20)
                                : a.killedBy == null || a.killedBy.equals(a.id)
                                        ? tr("you_died")
                                        : tr("killed_by", name(a.killedBy)),
                        event.getPlayer()));
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!done && actor(event.getPlayer()) != null && arena != null)
            event.setRespawnLocation(arena.spectator());
    }

    @Override
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        if (!done
                && !resetting
                && actor(event.getPlayer()) != null
                && !actor(event.getPlayer()).alive) spectate(event.getPlayer());
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !built || done) return;
        Player p = event.getPlayer();
        var a = actor(p);
        if (a == null || !a.alive) return;
        if (event.getAction().isRightClick()) {
            rightClick(p);
            var gun = gun(p);
            if (canAct(p) && !shopOpen(p) && gun != null && gun.weapon.requiresTriggerRelease()) {
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.ALLOW);
            }
        } else if (event.getAction().isLeftClick()) leftClick(p, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTriggerRelease(PlayerStopUsingItemEvent event) {
        if (!done && actor(event.getPlayer()) != null) combat.releaseTrigger(event.getPlayer());
    }

    @Override
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        // Pistols use an invisible long native-use state only to track button release.
        event.setCancelled(true);
    }

    @Override
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND
                && built
                && !done
                && actor(event.getPlayer()) != null
                && actor(event.getPlayer()).alive) rightClick(event.getPlayer());
    }

    private void rightClick(Player p) {
        int slot = p.getInventory().getHeldItemSlot();
        if (slot == 8) {
            if (p.isSneaking()) openScores(p);
            else openShop(p);
            return;
        }
        if (slot == 7) {
            if (deathmatch) return;
            beginChannel(p);
            return;
        }
        if (!canAct(p)) return;
        if (slot >= 3 && slot <= 6) {
            throwGrenade(p, CsGrenadePhysics.ThrowMode.SHORT);
        } else if (slot == 2) combat.knife(p, true, null);
        else combat.right(p);
    }

    private void throwGrenade(Player p, CsGrenadePhysics.ThrowMode mode) {
        int slot = p.getInventory().getHeldItemSlot();
        if (slot >= 3 && slot <= 6 && grenades.toss(p, CsMatch.Utility.values()[slot - 3], mode))
            CsItems.sync(p, match, radar.view);
    }

    private void leftClick(Player p, Player target) {
        int slot = p.getInventory().getHeldItemSlot();
        if (slot >= 3 && slot <= 6) throwGrenade(p, CsGrenadePhysics.ThrowMode.LONG);
        else if (slot == 2) combat.knife(p, false, target);
        else combat.left(p);
    }

    /** Swing/interact/attack overlap is debounced by the gun or shared grenade throw cooldown. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombatSwing(PlayerArmSwingEvent event) {
        if (built && event.getHand() == EquipmentSlot.HAND && isInGame(event.getPlayer())) {
            Player p = event.getPlayer();
            int slot = p.getInventory().getHeldItemSlot();
            if (slot >= 3 && slot <= 6) throwGrenade(p, CsGrenadePhysics.ThrowMode.LONG);
            else combat.left(p);
        }
    }

    @Override
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        if (actor(event.getPlayer()) == null) return;
        event.setCancelled(true);
        if (built) leftClick(event.getPlayer(), event.getAttacked() instanceof Player p ? p : null);
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event) {
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player p) || !built || !active(p)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            p.setFallDistance(0);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent hit
                && hit.getDamager() instanceof Player source
                && source.getInventory().getHeldItemSlot() == 2) combat.knife(source, false, p);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (built) {
            cancelChannel(event.getPlayer().getUniqueId());
            combat.switchSlot(event.getPlayer(), event.getPreviousSlot(), event.getNewSlot());
        }
    }

    @Override
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
        if (built) combat.reload(event.getPlayer());
    }

    @Override
    public void onDropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
        if (!built || !active(event.getPlayer())) return;
        var a = actor(event.getPlayer());
        int slot = event.getPlayer().getInventory().getHeldItemSlot();
        pendingDrops.put(a.id, new PendingDrop(slot, a.life, a.gun(slot)));
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player p
                && event.getView().getTopInventory().getHolder() instanceof Shop shop
                && shop.owner.equals(p.getUniqueId())) {
            String item = shop.choices.get(event.getRawSlot());
            if (item != null)
                Bukkit.getScheduler()
                        .runTask(
                                plugin(),
                                () -> {
                                    if (!done
                                            && isInGame(p)
                                            && p.getOpenInventory().getTopInventory()
                                                    == shop.inventory) purchase(p, item);
                                });
        }
    }

    @Override
    public void onInventoryDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onCraftItem(CraftItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @Override
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    @Override
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onPlayerJump(PlayerJumpEvent event) {
        if (frozen.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (done || internalTeleport || actor(event.getPlayer()) == null) return;
        Location anchor = frozen.get(event.getPlayer().getUniqueId());
        if (anchor != null && event.hasChangedPosition()) {
            Location to = anchor.clone();
            to.setYaw(event.getTo().getYaw());
            to.setPitch(event.getTo().getPitch());
            event.setTo(to);
            event.getPlayer().setVelocity(new Vector());
        } else if (built
                && wingman
                && !event.isCancelled()
                && event.hasChangedPosition()
                && canMove(event.getPlayer())
                && arena.outsideWingman(event.getTo())) {
            Location to = event.getFrom().clone();
            to.setYaw(event.getTo().getYaw());
            to.setPitch(event.getTo().getPitch());
            event.setTo(to);
            event.getPlayer().setVelocity(new Vector());
            combat.resetMovement(event.getPlayer());
        } else if (built
                && !event.isCancelled()
                && event.hasChangedPosition()
                && match.phase == CsMatch.Phase.LIVE
                && active(event.getPlayer())) {
            combat.recordMovement(event.getPlayer(), event.getFrom(), event.getTo());
        }
    }

    @Override
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!done
                && !internalTeleport
                && actor(event.getPlayer()) != null
                && event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE)
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void physics(BlockPhysicsEvent event) {
        if (world != null && event.getBlock().getWorld().equals(world)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void cameraStart(PlayerStartSpectatingEntityEvent event) {
        var a = actor(event.getPlayer());
        if (done || a == null || !a.online || cameraSwitch) return;
        if (a.alive
                || !(event.getNewSpectatorTarget() instanceof Player target)
                || actor(target) == null
                || actor(target).team != a.team
                || !active(target)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void cameraStop(PlayerStopSpectatingEntityEvent event) {
        var a = actor(event.getPlayer());
        if (!done && !cameraSwitch && a != null && a.online && !a.alive) {
            event.setCancelled(true);
            cameraIndex.merge(a.id, 1, Integer::sum);
        }
    }
}
