package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.text.TimeFormat;

/** One continuous free-for-all. A death pauses only that player, never the match. */
final class ArmsRaceSession implements GameSession {
    private enum Phase { COUNTDOWN, FIGHT, RESULT, DONE }
    private record Hit(UUID attacker, int stage, long tick) { }
    private final SessionServices services;
    private final PartyConfig config;
    private final NamespacedKey shotStageKey;
    private final Random random = new Random();
    private final CountdownFreeze countdownFreeze = new CountdownFreeze();
    private final ArenaProjectiles projectiles = new ArenaProjectiles();
    private final DeathPause deathPause = new DeathPause();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Set<UUID> connected = new LinkedHashSet<>();
    private final Set<UUID> needsWeapon = new LinkedHashSet<>();
    private final Map<UUID, Long> respawnAt = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, Hit> lastHit = new HashMap<>();
    private ArmsRaceState state;
    private ArmsRaceArena arena;
    private World world;
    private BossBar bar;
    private BukkitTask ticker;
    private Phase phase = Phase.COUNTDOWN;
    private long tick;
    private long countdownEnd;
    private long matchEnd;
    private long resultEnd;
    private long aloneSince = -1;
    private int respawnTicks;
    private int protectionTicks;
    private int durationTicks;
    private boolean internalTeleport;
    private UUID champion;
    private List<Component> result = List.of();

    ArmsRaceSession(SessionServices services, PartyConfig config) {
        this.services = services;
        this.config = config;
        shotStageKey = new NamespacedKey(services.plugin(), "arms_race_shot_stage");
    }

    @Override public void onStart() throws CommandFailure {
        try {
            start();
        } catch (CommandFailure | RuntimeException failure) {
            countdownFreeze.releaseAll();
            throw failure;
        }
    }
    private void start() throws CommandFailure {
        for (var participant : services.participants().all()) {
            names.put(participant.id(), participant.name());
            connected.add(participant.id());
        }
        if (names.isEmpty()) throw new CommandFailure("Для гонки вооружений нужны участники.");
        state = new ArmsRaceState(names.keySet(), ArmsRaceWeapon.values().length);
        countdownEnd = Math.clamp(config.armsRaceCountdownSeconds.get(), 3, 15) * 20L;
        durationTicks = Math.clamp(config.armsRaceSeconds.get(), 60, 1800) * 20;
        respawnTicks = Math.max(DeathPause.TICKS, Math.clamp(config.armsRaceRespawnSeconds.get(), 1, 10) * 20);
        protectionTicks = Math.clamp(config.armsRaceProtectionSeconds.get(), 1, 5) * 20;
        world = services.worlds().createVoid("party_arms_race");
        services.arena().applyStandardGameRules(world);
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(6000);
        world.setStorm(false);
        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        arena = new ArmsRaceArena(world);
        arena.build();
        services.arena().applyBorder(world, arena.center(), PvpArena.BORDER_SIZE, PvpArena.BORDER_SIZE, 0);
        bar = services.bossBars().create(text("Гонка вооружений", NamedTextColor.GOLD), BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        for (Player player : services.participants().onlinePlayers()) spawn(player);
        services.messages().broadcast(text("Гонка вооружений: все против всех! Убийство текущим оружием открывает следующую ступень.", NamedTextColor.GOLD));
        services.messages().broadcast(text("12 ступеней, финал — золотая лопата. После смерти оружие сохраняется. Строить нельзя.", NamedTextColor.GRAY));
        if (services.isTest()) services.messages().broadcast(text("Тестовая игра: без сохранения очков. /party stop — закончить.", NamedTextColor.YELLOW));
        updateHud();
        ticker = Bukkit.getScheduler().runTaskTimer(services.plugin(), this::tick, 1L, 1L);
    }

    private static Component text(String value, NamedTextColor color) { return Component.text(value, color); }
    private String name(UUID id) { return names.getOrDefault(id, "Игрок"); }
    private Player player(UUID id) { return connected.contains(id) ? Bukkit.getPlayer(id) : null; }
    private boolean active(Player player) {
        return connected.contains(player.getUniqueId()) && state.alive(player.getUniqueId())
                && player.getGameMode() == GameMode.SURVIVAL && !player.isDead();
    }
    private boolean fighting(Player player) { return phase == Phase.FIGHT && active(player); }
    private boolean protectedSpawn(UUID id) { return tick < protectedUntil.getOrDefault(id, 0L); }
    private void teleport(Player player, Location to) {
        internalTeleport = true;
        try { player.teleport(to); } finally { internalTeleport = false; }
    }
    private void forceRespawn(Player player) {
        internalTeleport = true;
        try { if (player.isDead()) player.spigot().respawn(); } finally { internalTeleport = false; }
    }
    private void spawn(Player player) {
        UUID id = player.getUniqueId();
        countdownFreeze.release(id);
        forceRespawn(player);
        if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
        List<Location> enemies = new ArrayList<>();
        for (UUID enemyId : connected) {
            Player enemy = player(enemyId);
            if (!id.equals(enemyId) && enemy != null && active(enemy) && enemy.getWorld().equals(world)) enemies.add(enemy.getLocation());
        }
        services.playerState().resetForRound(player, GameMode.SURVIVAL);
        player.setAbsorptionAmount(0);
        player.setNoDamageTicks(0);
        player.setSaturation(20);
        state.setAlive(id, true);
        respawnAt.remove(id);
        deathPause.forget(id);
        needsWeapon.remove(id);
        lastHit.remove(id);
        protectedUntil.put(id, (phase == Phase.COUNTDOWN ? countdownEnd : tick) + protectionTicks);
        ArmsRaceWeapon.at(state.stage(id)).equip(player);
        teleport(player, arena.spawn(enemies, random));
        if (phase == Phase.COUNTDOWN) countdownFreeze.freeze(player);
        player.showBossBar(bar);
        player.sendActionBar(text("Ступень " + (state.stage(id) + 1) + "/12 • " + ArmsRaceWeapon.at(state.stage(id)).title, NamedTextColor.GREEN));
    }
    private void waitForRespawn(Player player) {
        if (player.isDead()) return; // The tick task respawns native deaths outside their event handler.
        if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
        services.playerState().resetForRound(player, GameMode.SPECTATOR);
        player.setAbsorptionAmount(0);
        services.playerState().makeSpectator(player, respawnTicks / 20 + 1);
        teleport(player, arena.spectator());
        player.showBossBar(bar);
    }

    private void tick() {
        if (phase == Phase.DONE || !services.session().isRunning()) return;
        tick++;
        if (connected.isEmpty()) { services.session().endGame(EndReason.ALL_PLAYERS_LEFT); return; }
        if (phase == Phase.COUNTDOWN && tick >= countdownEnd) {
            countdownFreeze.releaseAll();
            phase = Phase.FIGHT;
            matchEnd = tick + durationTicks;
            services.messages().broadcast(text("Бой! Пройдите всю цепочку оружия первым!", NamedTextColor.RED));
        }
        if (phase == Phase.RESULT) {
            if (tick >= resultEnd) finish(champion);
            return;
        }
        if (phase != Phase.FIGHT) return;
        for (UUID id : List.copyOf(needsWeapon)) {
            Player player = player(id);
            if (player != null && active(player)) ArmsRaceWeapon.at(state.stage(id)).equip(player);
            needsWeapon.remove(id);
        }
        for (UUID id : List.copyOf(respawnAt.keySet())) {
            Player player = player(id);
            if (player == null) continue;
            if (tick >= respawnAt.get(id) && !deathPause.pending(id, tick)) spawn(player);
            else if (!player.isDead() && player.getGameMode() != GameMode.SPECTATOR) waitForRespawn(player);
        }
        if (tick % 20 == 0) {
            projectiles.expire(tick);
            for (UUID id : connected) {
                Player player = player(id);
                if (player != null && active(player)) ArmsRaceWeapon.at(state.stage(id)).replenish(player);
            }
        }
        if (tick >= matchEnd) { requestFinish(state.timeWinner(connected)); return; }
        if (!services.isTest() && connected.size() < 2) {
            if (aloneSince < 0) aloneSince = tick;
            if (tick - aloneSince >= 15 * 20) requestFinish(connected.iterator().next());
        } else aloneSince = -1;
    }
    @Override public void onSecondTick(long elapsed) { updateHud(); }

    private Player attacker(Entity entity) {
        if (entity instanceof Player player) return player;
        return entity instanceof Projectile shot && shot.getShooter() instanceof Player player ? player : null;
    }
    private int weaponStage(EntityDamageByEntityEvent event, Player attacker) {
        int stage = state.stage(attacker.getUniqueId());
        ArmsRaceWeapon weapon = ArmsRaceWeapon.at(stage);
        if (event.getDamager() instanceof Projectile shot) {
            Integer firedAt = shot.getPersistentDataContainer().get(shotStageKey, PersistentDataType.INTEGER);
            return weapon.ranged() && firedAt != null && firedAt == stage ? stage : -1;
        }
        return !weapon.ranged() && attacker.getInventory().getItemInMainHand().getType() == weapon.material ? stage : -1;
    }
    private boolean validAttack(EntityDamageByEntityEvent event, Player victim) {
        Player attacker = attacker(event.getDamager());
        return attacker != null && attacker != victim && fighting(attacker) && fighting(victim)
                && attacker.getWorld().equals(world) && victim.getWorld().equals(world) && weaponStage(event, attacker) >= 0;
    }
    @Override public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!fighting(victim)) { event.setCancelled(true); return; }
        UUID victimId = victim.getUniqueId();
        if (event instanceof EntityDamageByEntityEvent hit) {
            if (!validAttack(hit, victim)) { event.setCancelled(true); return; }
            Player attacker = attacker(hit.getDamager());
            // Choosing to attack forfeits your own spawn shield, including attacks on a shielded player.
            protectedUntil.remove(attacker.getUniqueId());
            if (protectedSpawn(victimId)) { event.setCancelled(true); return; }
            if (event.getFinalDamage() > 0) lastHit.put(victimId, new Hit(attacker.getUniqueId(), weaponStage(hit, attacker), tick));
        } else if (protectedSpawn(victimId)) { event.setCancelled(true); return; }
        // Let vanilla produce the death screen; progression is recorded once in PlayerDeathEvent.
    }
    @Override public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !validAttack(event, victim) || protectedSpawn(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    private void die(Player victim) {
        UUID victimId = victim.getUniqueId();
        if (phase != Phase.FIGHT || !state.alive(victimId)) return;
        Hit hit = lastHit.remove(victimId);
        UUID creditedAttacker = hit != null && tick - hit.tick() <= 8 * 20 ? hit.attacker() : null;
        Player killer = creditedAttacker == null ? null : player(creditedAttacker);
        UUID killerId = killer != null && fighting(killer) ? killer.getUniqueId() : null;
        boolean advanced = state.death(victimId, killerId, hit == null ? -1 : hit.stage());
        needsWeapon.remove(victimId);
        clearShots(victimId);
        protectedUntil.remove(victimId);
        deathPause.begin(victimId, tick);
        respawnAt.put(victimId, tick + respawnTicks);
        services.messages().broadcast(text(creditedAttacker == null ? name(victimId) + " погиб"
                : name(creditedAttacker) + " убил " + name(victimId), NamedTextColor.RED));
        if (killerId != null) killer.showTitle(Title.title(text("Вы убили " + name(victimId), NamedTextColor.GOLD),
                text(advanced ? "Следующая ступень оружия" : "", NamedTextColor.WHITE),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(300))));
        if (!advanced) return;
        clearShots(killerId);
        if (state.winner() != null) { requestFinish(state.winner()); return; }
        ArmsRaceWeapon weapon = ArmsRaceWeapon.at(state.stage(killerId));
        // Keep the old item for the remainder of this tick: a sword sweep must not
        // count as kills with multiple newly unlocked weapons in one attack.
        needsWeapon.add(killerId);
        var maximum = killer.getAttribute(Attribute.MAX_HEALTH);
        double healthLimit = maximum == null ? 20 : Math.min(20, maximum.getValue());
        killer.setHealth(Math.min(healthLimit, killer.getHealth() + 4));
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .7f, 1.4f);
        services.messages().broadcast(text(name(killerId) + " → " + (state.stage(killerId) + 1) + "/12 • " + weapon.title, NamedTextColor.GOLD));
        updateHud();
    }
    @Override public void onPlayerDeath(PlayerDeathEvent event) {
        if (!connected.contains(event.getPlayer().getUniqueId())) return;
        Hit hit = lastHit.get(event.getPlayer().getUniqueId());
        UUID killer = hit != null && tick - hit.tick() <= 8 * 20 ? hit.attacker() : null;
        event.deathMessage(null);
        event.deathScreenMessageOverride(text(killer == null ? "Вы погибли" : "Вас убил " + name(killer), NamedTextColor.RED));
        event.getDrops().clear(); event.setDroppedExp(0);
        event.setKeepInventory(true); event.setKeepLevel(true);
        die(event.getPlayer());
    }
    @Override public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (phase != Phase.DONE && connected.contains(event.getPlayer().getUniqueId())) event.setRespawnLocation(arena.spectator());
    }
    @Override public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        if (phase == Phase.DONE || !connected.contains(player.getUniqueId())) return;
        if (phase == Phase.RESULT) {
            waitForRespawn(player);
            countdownFreeze.freeze(player);
        } else if (respawnAt.containsKey(player.getUniqueId()) && tick < respawnAt.get(player.getUniqueId())) {
            waitForRespawn(player);
        }
    }
    private void clearShots(UUID shooter) {
        projectiles.clear(shooter);
    }
    @Override public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !fighting(player)) { event.setCancelled(true); return; }
        int stage = state.stage(player.getUniqueId());
        ArmsRaceWeapon weapon = ArmsRaceWeapon.at(stage);
        if (!weapon.ranged() || event.getBow() == null || event.getBow().getType() != weapon.material) {
            event.setCancelled(true); return;
        }
        protectedUntil.remove(player.getUniqueId());
        event.getProjectile().getPersistentDataContainer().set(shotStageKey, PersistentDataType.INTEGER, stage);
        if (event.getProjectile() instanceof AbstractArrow arrow) arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        if (!event.isCancelled()) projectiles.track(player.getUniqueId(), event.getProjectile(), tick);
    }

    private void requestFinish(UUID winner) {
        if (phase != Phase.FIGHT) return;
        champion = winner;
        phase = Phase.RESULT;
        resultEnd = tick + DeathPause.TICKS;
        services.messages().broadcast(text(winner == null ? "Гонка завершена: ничья" : "Победитель гонки — " + name(winner), NamedTextColor.GOLD));
        for (UUID id : connected) {
            Player player = player(id);
            if (player != null && !player.isDead()) countdownFreeze.freeze(player);
        }
        updateHud();
    }
    private void finish(UUID winner) {
        if (phase == Phase.DONE) return;
        champion = winner;
        List<UUID> ranking = new ArrayList<>(state.leaderboard(names.keySet()));
        ranking.sort(java.util.Comparator.comparing(id -> !connected.contains(id)));
        if (champion != null) { ranking.remove(champion); ranking.addFirst(champion); }
        List<Component> lines = new ArrayList<>();
        if (champion == null) lines.add(text("Ничья: ни одного результативного убийства.", NamedTextColor.YELLOW));
        for (int i = 0; i < ranking.size(); i++) {
            UUID id = ranking.get(i);
            lines.add(text((i + 1) + ". " + name(id) + " • ступень " + (state.stage(id) + 1) + "/12 • "
                    + state.kills(id) + " убийств / " + state.deaths(id) + " смертей"
                    + (connected.contains(id) ? "" : " (вышел)"), i == 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY));
        }
        result = List.copyOf(lines);
        services.session().endGame(EndReason.FINISHED);
    }
    @Override public void onEnd(EndReason reason) {
        phase = Phase.DONE;
        countdownFreeze.releaseAll();
        projectiles.clearAll();
        if (ticker != null) ticker.cancel();
        if (reason == EndReason.FINISHED && state != null) {
            for (UUID id : names.keySet()) services.award("party", id, state.kills(id) + (id.equals(champion) ? 3 : 0));
        }
        deathPause.clear();
        respawnAt.clear(); protectedUntil.clear(); lastHit.clear(); needsWeapon.clear();
        if (arena != null) arena.cleanup();
    }
    @Override public List<Component> summary() { return result; }
    @Override public Component globalResult() {
        return champion == null ? null : text(name(champion) + " выиграл гонку вооружений!", NamedTextColor.GOLD);
    }
    private void updateHud() {
        if (phase == Phase.DONE || bar == null) return;
        long remaining = Math.max(0, ((phase == Phase.COUNTDOWN ? countdownEnd : matchEnd) - tick + 19) / 20);
        bar.name(text(phase == Phase.COUNTDOWN ? "Гонка вооружений • старт через " + remaining + " с"
                : phase == Phase.RESULT ? champion == null ? "Гонка завершена • ничья" : "Победитель: " + name(champion)
                : "Гонка вооружений • " + TimeFormat.clock(remaining), NamedTextColor.GOLD));
        bar.progress(Math.clamp((float) remaining * 20 / (phase == Phase.COUNTDOWN ? countdownEnd : durationTicks), 0, 1));
        List<Component> standings = new ArrayList<>();
        for (UUID id : state.leaderboard(connected).stream().limit(7).toList()) {
            standings.add(text(name(id) + " • " + (state.stage(id) + 1) + "/12", NamedTextColor.GRAY));
        }
        for (UUID id : connected) {
            Player player = player(id);
            if (player == null) continue;
            List<Component> lines = new ArrayList<>();
            lines.add(text("Все против всех • " + connected.size() + " игроков", NamedTextColor.AQUA));
            lines.add(text("Ступень " + (state.stage(id) + 1) + "/12: " + ArmsRaceWeapon.at(state.stage(id)).title, NamedTextColor.GREEN));
            lines.add(text("Убийств: " + state.kills(id) + " • Смертей: " + state.deaths(id), NamedTextColor.WHITE));
            if (respawnAt.containsKey(id)) lines.add(text("Возрождение: " + Math.max(0, (respawnAt.get(id) - tick + 19) / 20) + " с", NamedTextColor.YELLOW));
            else if (protectedSpawn(id)) lines.add(text("Защита появления • атака снимает её", NamedTextColor.YELLOW));
            lines.addAll(standings);
            services.sidebar().update(player, text("Гонка вооружений", NamedTextColor.LIGHT_PURPLE), lines);
            player.showBossBar(bar);
        }
    }

    @Override public boolean isInGame(Player player) { return phase != Phase.DONE && active(player); }
    @Override public boolean mayFollowPlayers(Player player) { return false; }
    @Override public Location spectatorLocation(Player viewer) { return arena == null ? null : arena.spectator(); }
    @Override public void onParticipantQuit(Player player) { disconnect(player); }
    @Override public void onParticipantLeave(Player player) { disconnect(player); }
    private void disconnect(Player player) {
        if (phase == Phase.DONE) return;
        UUID id = player.getUniqueId();
        countdownFreeze.release(id);
        connected.remove(id); state.setAlive(id, false);
        deathPause.forget(id);
        respawnAt.remove(id); protectedUntil.remove(id); lastHit.remove(id); needsWeapon.remove(id); clearShots(id);
    }
    @Override public void onParticipantRejoin(Player player) {
        if (phase == Phase.DONE || !names.containsKey(player.getUniqueId())) return;
        UUID id = player.getUniqueId();
        connected.add(id); state.setAlive(id, false);
        if (phase == Phase.COUNTDOWN) spawn(player);
        else {
            respawnAt.put(id, tick + respawnTicks); waitForRespawn(player);
            if (phase == Phase.RESULT) countdownFreeze.freeze(player);
        }
        player.sendMessage(text("Прогресс сохранён: ступень " + (state.stage(id) + 1) + "/12.", NamedTextColor.GREEN));
    }
    @Override public void onBlockBreak(BlockBreakEvent event) { event.setCancelled(true); }
    @Override public void onBlockPlace(BlockPlaceEvent event) { event.setCancelled(true); }
    @Override public void onEntityExplode(EntityExplodeEvent event) { event.blockList().clear(); }
    @Override public void onBlockExplode(BlockExplodeEvent event) { event.blockList().clear(); }
    @Override public void onFoodLevelChange(FoodLevelChangeEvent event) { event.setCancelled(true); }
    @Override public void onDropItem(PlayerDropItemEvent event) { event.setCancelled(true); }
    @Override public void onPickupItem(EntityPickupItemEvent event) { event.setCancelled(true); }
    @Override public void onCraftItem(CraftItemEvent event) { event.setCancelled(true); }
    @Override public void onInventoryClick(InventoryClickEvent event) { event.setCancelled(true); }
    @Override public void onInventoryDrag(InventoryDragEvent event) { event.setCancelled(true); }
    @Override public void onPlayerInteract(PlayerInteractEvent event) {
        if (!fighting(event.getPlayer())) { event.setCancelled(true); return; }
        if (event.getClickedBlock() != null) event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }
    @Override public void onPlayerMove(PlayerMoveEvent event) {
        if (internalTeleport || !connected.contains(event.getPlayer().getUniqueId()) || phase == Phase.DONE) return;
        if (countdownFreeze.hold(event)) return;
        if (respawnAt.containsKey(event.getPlayer().getUniqueId())) {
            if (event.hasChangedPosition()) event.setTo(event.getFrom());
        } else if (fighting(event.getPlayer()) && event.getTo().getY() < PvpArena.FLOOR - .5) event.getPlayer().setHealth(0);
    }
    @Override public void onPlayerJump(PlayerJumpEvent event) { countdownFreeze.onJump(event); }
    @Override public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!internalTeleport && connected.contains(event.getPlayer().getUniqueId()) && phase != Phase.DONE) event.setCancelled(true);
    }
}
