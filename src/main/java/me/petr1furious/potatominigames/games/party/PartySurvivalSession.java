package me.petr1furious.potatominigames.games.party;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
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

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.text.TimeFormat;

/** Common one-life party games: protected temporary arena, countdown and spectator elimination. */
abstract class PartySurvivalSession implements GameSession, Listener {
    final SessionServices services;
    final PartyConfig config;
    final Random random = new Random();
    final String id;
    final String title;
    final Map<UUID, String> names = new LinkedHashMap<>();
    final Set<UUID> connected = new LinkedHashSet<>();
    World world;
    SurvivalStandings standings;
    long tick;
    long startedAt;
    private long countdownEnd;
    private long durationTicks;
    private boolean playing;
    private boolean done;
    private boolean internalTeleport;
    private BukkitTask ticker;
    private BossBar bar;
    private List<UUID> champions = List.of();
    private List<Component> result = List.of();

    PartySurvivalSession(SessionServices services, PartyConfig config, String id, String title) {
        this.services = services; this.config = config; this.id = id; this.title = title;
    }
    abstract void buildArena();
    abstract Location spawn(int index, int count);
    abstract Location viewpoint();
    abstract double outHeight();
    abstract int durationSeconds();
    abstract String instructions();
    abstract void playTick(long elapsed);
    abstract String status();
    void beginPlay() { }
    void cleanup() { }
    void playerOut(Player player) { }

    @Override public final void onStart() throws CommandFailure {
        for (var participant : services.participants().all()) {
            names.put(participant.id(), participant.name());
            connected.add(participant.id());
        }
        if (names.isEmpty()) throw new CommandFailure("Нужны участники.");
        standings = new SurvivalStandings(names.keySet());
        countdownEnd = Math.clamp(config.survivalCountdownSeconds.get(), 3, 15) * 20L;
        durationTicks = Math.max(20, durationSeconds()) * 20L;
        world = services.worlds().createVoid("party_" + id);
        services.arena().applyStandardGameRules(world);
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(18000);
        world.setStorm(false);
        world.setGameRule(GameRules.PVP, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        buildArena();
        bar = services.bossBars().create(text(title), BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        Bukkit.getPluginManager().registerEvents(this, services.plugin());
        int index = 0;
        for (Player player : services.participants().onlinePlayers()) {
            if (player.isDead()) player.spigot().respawn();
            player.closeInventory();
            player.setItemOnCursor(null);
            services.playerState().resetForRound(player, GameMode.SURVIVAL);
            player.setAbsorptionAmount(0);
            player.setSaturation(20);
            teleport(player, spawn(index++, names.size()));
            player.showBossBar(bar);
        }
        say(instructions());
        if (services.isTest()) say("Тестовая игра: без очков. /party stop — закончить.");
        hud();
        ticker = Bukkit.getScheduler().runTaskTimer(services.plugin(), this::tickGame, 1L, 1L);
    }

    static Component text(String value) { return Component.text(value, NamedTextColor.GOLD); }
    final void say(String value) { services.messages().broadcast(text(value)); }
    final boolean playing() { return playing && !done; }
    final long elapsed() { return Math.max(0, tick - startedAt); }
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
        return !done && standings != null && connected.contains(player.getUniqueId())
                && standings.alive(player.getUniqueId()) && !player.isDead();
    }
    final void teleport(Player player, Location location) {
        internalTeleport = true;
        try { player.teleport(location); } finally { internalTeleport = false; }
        player.setFallDistance(0);
        player.setVelocity(new org.bukkit.util.Vector());
    }
    final void announceTitle(String main, String subtitle, int seconds) {
        Title message = Title.title(text(main), Component.text(subtitle, NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(seconds), Duration.ofMillis(150)));
        for (Player player : services.session().audiencePlayers()) player.showTitle(message);
    }
    private void tickGame() {
        if (done || !services.session().isRunning()) return;
        tick++;
        if (connected.isEmpty()) { services.session().endGame(EndReason.ALL_PLAYERS_LEFT); return; }
        if (!playing && tick >= countdownEnd) {
            playing = true;
            startedAt = tick;
            announceTitle("Поехали!", title, 1);
            beginPlay();
        }
        if (playing) {
            playTick(elapsed());
            if (done) return;
            for (Player player : alivePlayers()) {
                if (!player.getWorld().equals(world) || player.getLocation().getY() < outHeight()) eliminate(player);
            }
            if (standings.decided(services.isTest(), tick)) { finish(standings.survivors()); return; }
            if (!standings.resultsHeld() && elapsed() >= durationTicks) { finish(standings.survivors()); return; }
        }
        // Native deaths may need one tick before they can become spectators.
        for (UUID id : connected) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && !standings.alive(id) && (player.isDead() || player.getGameMode() != GameMode.SPECTATOR)) {
                if (player.isDead()) player.spigot().respawn();
                spectate(player);
            }
        }
        if (tick % 20 == 0) {
            if (!playing) {
                announceTitle(Long.toString(Math.max(1, (countdownEnd - tick + 19) / 20)), title, 1);
                for (Player player : alivePlayers()) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, .5f, 1);
            }
            hud();
        }
    }
    final void eliminate(Player player) {
        if (done || !standings.eliminate(player.getUniqueId(), tick)) return;
        playerOut(player);
        say(player.getName() + " выбывает! Осталось: " + standings.survivors().size());
        player.sendMessage(Component.text("Ты выбыл. Следующую игру можно начать через меню.", NamedTextColor.GRAY));
        spectate(player);
    }
    private void spectate(Player player) {
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
        champions = List.copyOf(winners);
        List<Component> lines = new ArrayList<>();
        lines.add(text(champions.isEmpty() ? "Ничья — никто не выжил." : "Победители: "
                + String.join(", ", champions.stream().map(names::get).toList())));
        int place = 0;
        for (UUID id : standings.ranking(names.keySet(), tick)) {
            lines.add(Component.text(++place + ". " + names.get(id) + " • "
                    + TimeFormat.clock(Math.max(0, standings.survived(id, tick) - startedAt) / 20), NamedTextColor.GRAY));
        }
        result = List.copyOf(lines);
        services.session().endGame(EndReason.FINISHED);
    }
    private void hud() {
        if (bar == null || done) return;
        String line = !playing ? "Старт через " + Math.max(1, (countdownEnd - tick + 19) / 20) + " с" : status();
        bar.name(text(title + " • " + line));
        bar.progress(playing ? Math.clamp(1f - (float) elapsed() / durationTicks, 0, 1)
                : Math.clamp(1f - (float) tick / countdownEnd, 0, 1));
        for (Player player : services.session().audiencePlayers()) {
            player.showBossBar(bar);
            services.sidebar().update(player, text(title), List.of(
                    Component.text("Осталось: " + standings.survivors().size() + "/" + names.size(), NamedTextColor.WHITE),
                    Component.text(line, NamedTextColor.YELLOW),
                    Component.text("Время: " + TimeFormat.clock(elapsed() / 20), NamedTextColor.GRAY)));
        }
    }
    @Override public final void onSecondTick(long elapsed) { hud(); }
    @Override public final void onEnd(EndReason reason) {
        if (done) return;
        done = true;
        if (ticker != null) ticker.cancel();
        HandlerList.unregisterAll(this);
        cleanup();
        if (reason == EndReason.FINISHED) for (UUID id : champions) services.award("party", id, 3);
    }
    @Override public final List<Component> summary() { return result; }
    @Override public final Component globalResult() {
        return champions.isEmpty() ? null : text(String.join(", ", champions.stream().map(names::get).toList()) + " — победа в " + title + "!");
    }
    @Override public final boolean isInGame(Player player) { return alive(player); }
    @Override public final Location spectatorLocation(Player viewer) { return world == null ? null : viewpoint(); }
    @Override public final void onParticipantQuit(Player player) { disconnect(player); }
    @Override public final void onParticipantLeave(Player player) { disconnect(player); }
    private void disconnect(Player player) {
        if (done || standings == null) return;
        if (standings.eliminate(player.getUniqueId(), tick)) playerOut(player);
        connected.remove(player.getUniqueId());
    }
    @Override public final void onParticipantRejoin(Player player) {
        if (done || !names.containsKey(player.getUniqueId())) return;
        connected.add(player.getUniqueId());
        spectate(player);
    }
    @Override public final void onParticipantJoin(Player player) { spectate(player); }
    @Override public final void onPlayerMove(PlayerMoveEvent event) {
        if (internalTeleport || done || !alive(event.getPlayer())) return;
        if (!playing && event.hasChangedPosition()) event.setTo(event.getFrom());
    }
    @Override public final void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!internalTeleport && !done && standings != null && standings.alive(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @Override public final void onPlayerDeath(PlayerDeathEvent event) {
        if (!names.containsKey(event.getPlayer().getUniqueId())) return;
        event.getDrops().clear(); event.setDroppedExp(0); event.setKeepInventory(true); event.deathMessage(null);
        eliminate(event.getPlayer());
    }
    @Override public final void onEntityDamage(EntityDamageEvent event) { if (event.getEntity().getWorld().equals(world)) event.setCancelled(true); }
    @Override public final void onEntityDamageByEntity(EntityDamageByEntityEvent event) { if (event.getEntity().getWorld().equals(world)) event.setCancelled(true); }
    @EventHandler public final void onTarget(EntityTargetLivingEntityEvent event) { if (event.getEntity().getWorld().equals(world)) event.setCancelled(true); }
    @Override public final void onBlockBreak(BlockBreakEvent event) { event.setCancelled(true); }
    @Override public final void onBlockPlace(BlockPlaceEvent event) { event.setCancelled(true); }
    @Override public final void onEntityExplode(EntityExplodeEvent event) { event.blockList().clear(); }
    @Override public final void onBlockExplode(BlockExplodeEvent event) { event.blockList().clear(); }
    @Override public final void onFoodLevelChange(FoodLevelChangeEvent event) { event.setCancelled(true); }
    @Override public final void onDropItem(PlayerDropItemEvent event) { event.setCancelled(true); }
    @Override public void onPickupItem(EntityPickupItemEvent event) { event.setCancelled(true); }
    @Override public final void onCraftItem(CraftItemEvent event) { event.setCancelled(true); }
    @Override public final void onInventoryClick(InventoryClickEvent event) { event.setCancelled(true); }
    @Override public final void onInventoryDrag(InventoryDragEvent event) { event.setCancelled(true); }
    @Override public final void onSwapHandItems(PlayerSwapHandItemsEvent event) { event.setCancelled(true); }
    @Override public void onPlayerInteract(PlayerInteractEvent event) { event.setCancelled(true); }
}
