package me.petr1furious.potatominigames.games.party;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.text.TimeFormat;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Common one-life party games: protected temporary arena, countdown and spectator elimination. */
abstract class PartySurvivalSession implements PartyRoundSession, Listener {
    final SessionServices services;
    final PartyRoundContext context;
    final PartyConfig config;
    final Random random = new Random();
    final String id;
    final Component title;
    final Map<UUID, String> names = new LinkedHashMap<>();
    final Set<UUID> connected = new LinkedHashSet<>();
    private final CountdownFreeze countdownFreeze = new CountdownFreeze();
    World world;
    SurvivalStandings standings;
    long tick;
    long startedAt;
    private long countdownEnd;
    private long durationTicks;
    private long timerStart;
    private boolean playing;
    private boolean done;
    private boolean internalTeleport;
    private BukkitTask ticker;
    private BossBar bar;
    private List<UUID> champions = List.of();
    private List<Component> result = List.of();

    PartySurvivalSession(
            SessionServices services, PartyConfig config, String id, PartyRoundContext context) {
        this.context = context;
        this.services = context.services();
        this.config = config;
        this.id = id;
        this.title = PartyRoundFactory.title(id);
    }

    abstract void buildArena();

    abstract Location spawn(int index, int count);

    abstract Location viewpoint();

    abstract double outHeight();

    abstract int durationSeconds();

    abstract Component instructions();

    abstract void playTick(long elapsed);

    abstract Component status();

    void beginPlay() {}

    void preparePlayer(Player player) {}

    boolean prepareArena() {
        return true;
    }

    Component preparationStatus() {
        return null;
    }

    protected boolean allowTeleport(PlayerTeleportEvent event) {
        return false;
    }

    protected void handleMove(PlayerMoveEvent event) {}

    void cleanup() {}

    void playerOut(Player player) {}

    long arenaTime() {
        return switch (id) {
            case "tool_race", "anvils", "color_floor", "build_memory", "mob_count" -> 6000L;
            default -> 18000L;
        };
    }

    @Override
    public final void onStart() throws CommandFailure {
        for (var participant : services.participants().all()) {
            names.put(participant.id(), participant.name());
            connected.add(participant.id());
        }
        if (names.isEmpty()) throw new CommandFailure(I18n.tr("party.rounds.error.participants"));
        if (PartyGamesState.QUICK_MODES.contains(id) && names.size() > 12)
            throw new CommandFailure(I18n.tr("party.rounds.error.quick_capacity", 12));
        standings = new SurvivalStandings(names.keySet());
        countdownEnd =
                Math.clamp(
                                context.embedded()
                                        ? context.options().countdownSeconds()
                                        : config.survivalCountdownSeconds.get(),
                                context.embedded() ? 0 : 3,
                                15)
                        * 20L;
        durationTicks = Math.max(20, durationSeconds()) * 20L;
        world = context.createVoid(id);
        services.arena().applyStandardGameRules(world);
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(arenaTime());
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRules.PVP, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        buildArena();
        bar =
                services.bossBars()
                        .create(text(title), BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        Bukkit.getPluginManager().registerEvents(this, services.plugin());
        int index = 0;
        for (Player player : services.participants().onlinePlayers()) {
            if (player.isDead()) player.spigot().respawn();
            player.closeInventory();
            player.setItemOnCursor(null);
            services.playerState().resetForRound(player, GameMode.SURVIVAL);
            player.setAbsorptionAmount(0);
            player.setSaturation(20);
            preparePlayer(player);
            teleport(player, spawn(index++, names.size()));
            countdownFreeze.freeze(player);
            player.showBossBar(bar);
        }
        say(instructions());
        if (services.isTest()) say(I18n.tr("party.rounds.test"));
        hud();
        ticker =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                services.plugin(),
                                () -> {
                                    try {
                                        tickGame();
                                    } catch (RuntimeException failure) {
                                        services.plugin()
                                                .getLogger()
                                                .log(
                                                        java.util.logging.Level.SEVERE,
                                                        "Party round failed: " + id,
                                                        failure);
                                        context.finish(EndReason.ABORTED, PartyRoundResult.empty());
                                    }
                                },
                                1L,
                                1L);
    }

    static Component text(Component value) {
        return value.color(NamedTextColor.GOLD);
    }

    final void say(Component value) {
        services.messages().broadcast(text(value));
    }

    final boolean playing() {
        return playing && !done;
    }

    final long elapsed() {
        return Math.max(0, tick - startedAt);
    }

    final List<Player> alivePlayers() {
        List<Player> players = new ArrayList<>();
        if (standings == null) return players;
        for (UUID id : standings.survivors()) {
            Player player = connected.contains(id) ? Bukkit.getPlayer(id) : null;
            if (player != null && player.isOnline() && !player.isDead()) players.add(player);
        }
        return players;
    }

    final boolean alive(Player player) {
        return !done
                && standings != null
                && connected.contains(player.getUniqueId())
                && standings.alive(player.getUniqueId())
                && !player.isDead();
    }

    final void teleport(Player player, Location location) {
        internalTeleport = true;
        try {
            player.teleport(location);
        } finally {
            internalTeleport = false;
        }
        player.setFallDistance(0);
        player.setVelocity(new org.bukkit.util.Vector());
    }

    final void freezeMovement(Player player) {
        countdownFreeze.freeze(player);
    }

    final void releaseMovement() {
        countdownFreeze.releaseAll();
    }

    final void announceTitle(Component main, Component subtitle, int seconds) {
        Title message =
                Title.title(
                        text(main),
                        subtitle.color(NamedTextColor.WHITE),
                        Title.Times.times(
                                Duration.ofMillis(100),
                                Duration.ofSeconds(seconds),
                                Duration.ofMillis(150)));
        for (Player player : services.session().audiencePlayers()) player.showTitle(message);
    }

    private void tickGame() {
        if (done || context.ended() || !services.session().isRunning()) return;
        tick++;
        if (connected.isEmpty()) {
            context.finish(EndReason.ALL_PLAYERS_LEFT, PartyRoundResult.empty());
            return;
        }
        if (!playing && !prepareArena()) {
            countdownEnd++;
            if (tick % 20 == 0) hud();
            return;
        }
        if (!playing && tick >= countdownEnd) {
            countdownFreeze.releaseAll();
            playing = true;
            startedAt = tick;
            announceTitle(I18n.tr("party.rounds.go"), title, 1);
            PartySounds.play(
                    services.session().audiencePlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, .7f, 1.6f);
            beginPlay();
        }
        if (playing) {
            playTick(elapsed());
            if (done || context.ended()) return;
            for (Player player : alivePlayers()) {
                if (!player.getWorld().equals(world) || player.getLocation().getY() < outHeight())
                    eliminate(player);
            }
            if (survivalAutoFinish() && standings.decided(services.isTest(), tick)) {
                finish(standings.survivors());
                return;
            }
            if (!standings.resultsHeld() && remainingRoundTicks() == 0) {
                finish(standings.survivors());
                return;
            }
        }
        // Native deaths may need one tick before they can become spectators.
        for (UUID id : connected) {
            Player player = Bukkit.getPlayer(id);
            if (player != null
                    && !standings.alive(id)
                    && (player.isDead() || player.getGameMode() != GameMode.SPECTATOR)) {
                if (player.isDead()) player.spigot().respawn();
                spectate(player);
            }
        }
        if (tick % 20 == 0) {
            if (!playing) {
                announceTitle(
                        Component.text(Math.max(1, (countdownEnd - tick + 19) / 20)), title, 1);
                for (Player player : alivePlayers())
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, .5f, 1);
            }
            hud();
        }
    }

    final void eliminate(Player player) {
        if (done || !standings.eliminate(player.getUniqueId(), tick)) return;
        countdownFreeze.release(player.getUniqueId());
        playerOut(player);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, .8f, .6f);
        say(I18n.tr("party.rounds.eliminated", player.getName(), standings.survivors().size()));
        player.sendMessage(I18n.tr("party.rounds.spectate").color(NamedTextColor.GRAY));
        spectate(player);
    }

    final void spectate(Player player) {
        if (player.isDead()) return;
        if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
        player.closeInventory();
        player.setItemOnCursor(null);
        services.playerState().resetForRound(player, GameMode.SPECTATOR);
        teleport(player, viewpoint());
        if (bar != null) player.showBossBar(bar);
    }

    final void finish(List<UUID> winners) {
        if (done) return;
        PartyRoundResult podium = roundResult();
        champions =
                podiumAwards()
                        ? (podium.places().isEmpty() ? List.of() : podium.places().getFirst())
                        : List.copyOf(winners);
        List<Component> lines = new ArrayList<>();
        lines.add(
                text(
                        champions.isEmpty()
                                ? I18n.tr("party.rounds.draw")
                                : I18n.tr(
                                        "common.summary.winners",
                                        String.join(
                                                ", ",
                                                champions.stream().map(names::get).toList()))));
        int place = 0;
        for (UUID id : standings.ranking(names.keySet(), tick)) {
            lines.add(
                    I18n.tr(
                                    "party.rounds.survival.result",
                                    ++place,
                                    names.get(id),
                                    TimeFormat.clock(
                                            Math.max(0, standings.survived(id, tick) - startedAt)
                                                    / 20))
                            .color(NamedTextColor.GRAY));
        }
        if (podiumAwards()) {
            lines.clear();
            for (var group : podium.places())
                for (UUID id : group)
                    lines.add(
                            text(
                                    I18n.tr(
                                            "party.rounds.points.result",
                                            podium.place(id),
                                            names.get(id),
                                            podium.awards().get(id))));
        }
        result = List.copyOf(lines);
        if (!context.embedded())
            PartySounds.play(
                    services.session().audiencePlayers(), Sound.ENTITY_PLAYER_LEVELUP, .6f, 1.2f);
        context.finish(EndReason.FINISHED, roundResult());
    }

    @Override
    public PartyRoundResult roundResult() {
        return standings == null
                ? PartyRoundResult.empty()
                : standings.result(context.eligible(), tick);
    }

    boolean survivalAutoFinish() {
        return true;
    }

    boolean podiumAwards() {
        return false;
    }

    final long gameTick() {
        return tick;
    }

    final void restartRoundTimer(int seconds) {
        timerStart = elapsed();
        durationTicks = seconds * 20L;
    }

    final long remainingRoundTicks() {
        return Math.max(0, timerStart + durationTicks - elapsed());
    }

    private void hud() {
        if (bar == null || done) return;
        Component preparing = !playing ? preparationStatus() : null;
        Component line;
        if (preparing != null) line = preparing;
        else if (!playing)
            line = I18n.tr("party.rounds.starts_in", Math.max(1, (countdownEnd - tick + 19) / 20));
        else line = status();
        bar.name(text(I18n.tr("party.rounds.status", title, line)));
        if (preparing != null) bar.progress(0);
        else
            bar.progress(
                    playing
                            ? Math.clamp((float) remainingRoundTicks() / durationTicks, 0, 1)
                            : Math.clamp(1f - (float) tick / Math.max(1, countdownEnd), 0, 1));
        for (Player player : services.session().audiencePlayers()) {
            player.showBossBar(bar);
            services.sidebar().update(player, text(title), sidebarLines(player, line));
        }
    }

    List<Component> sidebarLines(Player player, Component line) {
        return List.of(
                I18n.tr("party.rounds.remaining", standings.survivors().size(), names.size())
                        .color(NamedTextColor.WHITE),
                line.color(NamedTextColor.YELLOW),
                I18n.tr("party.rounds.time", TimeFormat.clock(elapsed() / 20))
                        .color(NamedTextColor.GRAY));
    }

    @Override
    public final void onSecondTick(long elapsed) {
        hud();
    }

    @Override
    public final void onEnd(EndReason reason) {
        if (done) return;
        done = true;
        if (ticker != null) ticker.cancel();
        countdownFreeze.releaseAll();
        HandlerList.unregisterAll(this);
        cleanup();
        context.hideBars();
        if (!context.embedded() && reason == EndReason.FINISHED) {
            if (podiumAwards())
                roundResult().awards().forEach((id, points) -> services.award("party", id, points));
            else for (UUID id : champions) services.award("party", id, 3);
        }
    }

    @Override
    public final List<Component> summary() {
        return result;
    }

    @Override
    public final Component globalResult() {
        return champions.isEmpty()
                ? null
                : text(
                        I18n.tr(
                                "party.rounds.victory",
                                String.join(", ", champions.stream().map(names::get).toList()),
                                title));
    }

    @Override
    public boolean isInGame(Player player) {
        return alive(player);
    }

    @Override
    public final Location spectatorLocation(Player viewer) {
        return world == null ? null : viewpoint();
    }

    @Override
    public final void onParticipantQuit(Player player) {
        disconnect(player);
    }

    @Override
    public final void onParticipantLeave(Player player) {
        disconnect(player);
    }

    private void disconnect(Player player) {
        context.withdraw(player.getUniqueId());
        if (done || standings == null) return;
        countdownFreeze.release(player.getUniqueId());
        if (standings.eliminate(player.getUniqueId(), tick)) playerOut(player);
        connected.remove(player.getUniqueId());
    }

    @Override
    public final void onParticipantRejoin(Player player) {
        if (done || !names.containsKey(player.getUniqueId())) return;
        connected.add(player.getUniqueId());
        spectate(player);
    }

    @Override
    public final void onParticipantJoin(Player player) {
        spectate(player);
    }

    @Override
    public final void onPlayerMove(PlayerMoveEvent event) {
        if (internalTeleport || done || !alive(event.getPlayer())) return;
        if (!countdownFreeze.hold(event)) handleMove(event);
    }

    @Override
    public final void onPlayerJump(PlayerJumpEvent event) {
        countdownFreeze.onJump(event);
    }

    @Override
    public final void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!internalTeleport
                && !done
                && standings != null
                && standings.alive(event.getPlayer().getUniqueId())
                && !allowTeleport(event)) event.setCancelled(true);
    }

    @Override
    public final void onPlayerDeath(PlayerDeathEvent event) {
        if (!names.containsKey(event.getPlayer().getUniqueId())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.deathMessage(null);
        eliminate(event.getPlayer());
    }

    @Override
    public final void onEntityDamage(EntityDamageEvent event) {
        handleDamage(event);
    }

    protected void handleDamage(EntityDamageEvent event) {
        if (event.getEntity().getWorld().equals(world)) event.setCancelled(true);
    }

    @Override
    public final void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        handleAttack(event);
    }

    protected void handleAttack(EntityDamageByEntityEvent event) {
        if (event.getEntity().getWorld().equals(world)) event.setCancelled(true);
    }

    @EventHandler
    public final void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity().getWorld().equals(world)) event.setCancelled(true);
    }

    @Override
    public final void onBlockBreak(BlockBreakEvent event) {
        handleBlockBreak(event);
    }

    protected void handleBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @Override
    public final void onBlockPlace(BlockPlaceEvent event) {
        handleBlockPlace(event);
    }

    protected void handleBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }

    @Override
    public final void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @Override
    public final void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    @Override
    public final void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @Override
    public final void onDropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onPickupItem(EntityPickupItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public final void onCraftItem(CraftItemEvent event) {
        handleCraftItem(event);
    }

    protected void handleCraftItem(CraftItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public final void onInventoryClick(InventoryClickEvent event) {
        handleInventoryClick(event);
    }

    protected void handleInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
    }

    @Override
    public final void onInventoryDrag(InventoryDragEvent event) {
        handleInventoryDrag(event);
    }

    protected void handleInventoryDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    @Override
    public final void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        handleSwapHandItems(event);
    }

    protected void handleSwapHandItems(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);
    }
}
