package me.petr1furious.potatominigames.games.party;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
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
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.SessionServices;

/** Party FFA: private mining cells, physical random loot, then one-life underground PvP. */
final class MinersSession implements GameSession {
    private record Hit(UUID attacker, long tick) { }
    private static final NamespacedKey COMPASS = new NamespacedKey("potatominigames", "miners_compass");
    private final SessionServices services;
    private final PartyConfig config;
    private final Random random = new Random();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, Integer> cells = new HashMap<>();
    private final Set<UUID> connected = new LinkedHashSet<>();
    private final Map<UUID, Hit> lastHit = new HashMap<>();
    private final Map<UUID, Long> deadAt = new HashMap<>();
    private final CountdownFreeze freeze = new CountdownFreeze();
    private final ArenaProjectiles shots = new ArenaProjectiles();
    private final PvpBombs bombs = new PvpBombs();
    private final MinersDrops drops = new MinersDrops();
    private final MinersGold gold = new MinersGold();
    private SurvivalStandings standings;
    private MinersRound round;
    private MinersArena arena;
    private World world;
    private BossBar bar;
    private BukkitTask ticker;
    private long tick;
    private int finaleSeconds;
    private boolean internalTeleport;
    private List<UUID> champions = List.of();
    private List<Component> result = List.of();

    MinersSession(SessionServices services, PartyConfig config) { this.services = services; this.config = config; }
    private static Component text(String value) { return Component.text(value, NamedTextColor.GOLD); }
    private void say(String value) { services.messages().broadcast(text(value)); }
    private String name(UUID id) { return names.getOrDefault(id, "Игрок"); }
    private Player player(UUID id) { return connected.contains(id) ? Bukkit.getPlayer(id) : null; }
    private boolean alive(Player player) {
        return standings != null && connected.contains(player.getUniqueId()) && standings.alive(player.getUniqueId()) && !player.isDead();
    }
    private boolean playing(Player player) { return round != null && round.mining() && alive(player) && player.getGameMode() == GameMode.SURVIVAL; }
    private boolean fighting(Player player) { return round != null && round.fighting() && playing(player); }
    private List<Player> survivors() {
        return standings.survivors().stream().map(this::player).filter(p -> p != null && alive(p)).toList();
    }
    @Override public void onStart() throws CommandFailure {
        try { start(); }
        catch (CommandFailure | RuntimeException error) { cleanup(); throw error; }
    }
    private void start() throws CommandFailure {
        List<UUID> roster = new ArrayList<>();
        for (var participant : services.participants().all()) {
            names.put(participant.id(), participant.name()); connected.add(participant.id()); roster.add(participant.id());
        }
        if (roster.isEmpty() || roster.size() > 12) throw new CommandFailure("Битва шахтёров рассчитана на 1–12 участников.");
        Collections.shuffle(roster, random);
        for (int i = 0; i < roster.size(); i++) cells.put(roster.get(i), i);
        standings = new SurvivalStandings(roster);
        finaleSeconds = Math.clamp(config.minersFinaleSeconds.get(), 15, 120);
        round = new MinersRound(Math.clamp(config.survivalCountdownSeconds.get(), 3, 15),
                Math.clamp(config.minersPreparationSeconds.get(), 10, 300), Math.clamp(config.minersCombatSeconds.get(), 30, 600), finaleSeconds);
        world = services.worlds().createVoid("party_miners");
        services.arena().applyStandardGameRules(world);
        world.setDifficulty(Difficulty.NORMAL); world.setTime(6000); world.setStorm(false);
        world.setGameRule(GameRules.PVP, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        arena = new MinersArena(world, roster.size(), random);
        services.arena().applyBorder(world, arena.center(), arena.borderSize(), arena.borderSize(), 0);
        bar = services.bossBars().create(text("Битва шахтёров • Подготовка арены"), BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        for (Player p : services.participants().onlinePlayers()) {
            respawn(p); p.closeInventory(); p.setItemOnCursor(null);
            services.playerState().resetForRound(p, GameMode.SPECTATOR);
            teleport(p, arena.spectator()); freeze.freeze(p); p.showBossBar(bar);
        }
        say("Битва шахтёров: копай руды, подбирай случайный лут и выживи после открытия шахт!");
        if (services.isTest()) say("Тренировка: без очков. /party stop — закончить.");
        ticker = Bukkit.getScheduler().runTaskTimer(services.plugin(), () -> {
            try { tickGame(); }
            catch (RuntimeException error) {
                services.plugin().getLogger().log(java.util.logging.Level.SEVERE, "Miners match failed", error);
                services.session().endGame(EndReason.ABORTED);
            }
        }, 1, 1);
    }
    private void teleport(Player p, Location to) {
        internalTeleport = true;
        try { p.teleport(to); } finally { internalTeleport = false; }
        p.setFallDistance(0); p.setVelocity(new org.bukkit.util.Vector());
    }
    private void respawn(Player p) {
        internalTeleport = true;
        try { if (p.isDead()) p.spigot().respawn(); } finally { internalTeleport = false; }
    }
    private void equip(Player p) {
        freeze.release(p.getUniqueId()); respawn(p);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        services.playerState().resetForRound(p, GameMode.SURVIVAL);
        p.setAbsorptionAmount(0); p.setSaturation(5); p.setNoDamageTicks(0);
        var inv = p.getInventory();
        ItemStack pick = PvpClass.item(Material.IRON_PICKAXE);
        pick.editMeta(meta -> meta.addEnchant(Enchantment.EFFICIENCY, 3, false));
        inv.setItem(0, pick); inv.setItem(1, PvpClass.item(Material.STONE_SWORD));
        inv.setItem(2, new ItemStack(Material.COBBLESTONE, 64)); inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 16));
        inv.setHelmet(PvpClass.item(Material.LEATHER_HELMET)); inv.setChestplate(PvpClass.item(Material.LEATHER_CHESTPLATE));
        inv.setLeggings(PvpClass.item(Material.LEATHER_LEGGINGS)); inv.setBoots(PvpClass.item(Material.LEATHER_BOOTS));
        ItemStack compass = new ItemStack(Material.COMPASS);
        compass.editMeta(meta -> { meta.displayName(text("Поиск шахтёра")); meta.getPersistentDataContainer().set(COMPASS, PersistentDataType.BYTE, (byte) 1); });
        inv.setItem(7, compass); inv.setHeldItemSlot(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 60000, 0, true, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60000, 0, true, false));
        teleport(p, arena.spawn(cells.get(p.getUniqueId()))); freeze.freeze(p);
        for (MinersOre ore : MinersOre.values()) p.sendMessage(Component.text(ore.description, NamedTextColor.GRAY));
        p.sendMessage(text("Лут выпадает на землю: подбирай и надевай сам. Камень даёт булыжник, запас до 128."));
    }
    private void tickGame() {
        if (round.phase() == MinersRound.Phase.DONE || !services.session().isRunning()) return;
        tick++;
        if (connected.isEmpty()) { services.session().endGame(EndReason.ALL_PLAYERS_LEFT); return; }
        if (round.phase() == MinersRound.Phase.BUILDING && arena.buildSlice(4096)) {
            for (Player p : survivors()) equip(p);
            round.ready(tick);
        }
        MinersRound.Phase previous = round.phase();
        if (previous == MinersRound.Phase.MINING && tick >= round.deadline()) arena.openSlice(4096);
        round.advance(tick, arena.opened());
        if (previous != round.phase()) {
            if (round.phase() == MinersRound.Phase.MINING) { freeze.releaseAll(); say("Добыча началась! В каждой коробке одинаковое число руд, но находки случайны."); }
            if (round.phase() == MinersRound.Phase.FIGHT) {
                world.setGameRule(GameRules.PVP, true);
                announce("Шахты открыты!", "PvP включено • компас ищет ближайшего врага");
                say("Шахты открыты! Одна жизнь. Можно продолжать добычу и прокапываться к соперникам.");
            }
            if (round.phase() == MinersRound.Phase.FINALE) {
                services.arena().applyBorder(world, arena.center(), arena.borderSize(), 3, finaleSeconds);
                announce("Финал!", "Граница сужается, игроки подсвечены");
            }
        }
        if (round.phase() == MinersRound.Phase.RESULT) {
            if (tick >= round.deadline()) services.session().endGame(EndReason.FINISHED);
            return;
        }
        for (UUID id : List.copyOf(deadAt.keySet())) if (tick - deadAt.get(id) >= DeathPause.TICKS) {
            Player p = player(id);
            if (p != null) { respawn(p); if (p.getGameMode() != GameMode.SPECTATOR) spectate(p); }
            deadAt.remove(id);
        }
        for (Player p : survivors()) if (!p.getWorld().equals(world) || p.getLocation().getY() < MinersArena.BASE - 1) out(p);
        if (standings.decided(services.isTest(), tick)) { result(standings.survivors()); return; }
        if (tick % 20 == 0) {
            shots.expire(tick); drops.prune();
            for (Player p : survivors()) {
                if (round.phase() == MinersRound.Phase.FINALE) p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                updateCompass(p);
                double pressure = round.pressure(tick);
                if (pressure > 0 && fighting(p)) p.damage(pressure);
            }
            hud();
        }
    }
    private void announce(String title, String subtitle) {
        Title message = Title.title(text(title), Component.text(subtitle, NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(3), Duration.ofMillis(200)));
        for (Player p : services.session().audiencePlayers()) p.showTitle(message);
    }
    private Player nearest(Player p) {
        return survivors().stream().filter(other -> other != p && other.getWorld().equals(p.getWorld()))
                .min(java.util.Comparator.comparingDouble(other -> other.getLocation().distanceSquared(p.getLocation()))).orElse(null);
    }
    private void updateCompass(Player p) {
        Player target = round.fighting() ? nearest(p) : null;
        Location at = target == null ? arena.spawn(cells.get(p.getUniqueId())) : target.getLocation();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (!isCompass(item)) continue;
            item.editMeta(CompassMeta.class, meta -> { meta.setLodestoneTracked(false); meta.setLodestone(at); });
            inv.setItem(i, item);
        }
    }
    private static boolean isCompass(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(COMPASS, PersistentDataType.BYTE);
    }
    private void hud() {
        if (bar == null || round.phase() == MinersRound.Phase.DONE) return;
        String line = switch (round.phase()) {
            case BUILDING -> "Подготовка арены • " + Math.round(arena.progress() * 100) + "%";
            case COUNTDOWN -> "Старт через " + round.secondsLeft(tick) + " с";
            case MINING -> "Добыча • PvP через " + round.secondsLeft(tick) + " с";
            case FIGHT -> "PvP • финал через " + round.secondsLeft(tick) + " с";
            case FINALE -> round.pressure(tick) > 0 ? "Последний бой!" : "Финал • " + round.secondsLeft(tick) + " с";
            case RESULT -> "Итоги • " + round.secondsLeft(tick) + " с";
            default -> "";
        };
        bar.name(text("Битва шахтёров • " + line));
        bar.progress(round.phase() == MinersRound.Phase.BUILDING ? arena.progress() : 1);
        for (Player p : services.session().audiencePlayers()) {
            p.showBossBar(bar);
            services.sidebar().update(p, text("Битва шахтёров"), List.of(Component.text(line, NamedTextColor.YELLOW),
                    Component.text("В игре: " + standings.survivors().size() + "/" + names.size(), NamedTextColor.WHITE),
                    Component.text("Золото: " + gold.count(p.getUniqueId()) + "/8 • до 14 сердец", NamedTextColor.GOLD)));
        }
    }
    @Override public void onSecondTick(long seconds) { hud(); }
    @Override public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!playing(p) || !arena.editable(event.getBlock())
                || !round.fighting() && !arena.owns(event.getBlock().getLocation(), cells.get(p.getUniqueId()))) { event.setCancelled(true); return; }
        Material material = event.getBlock().getType();
        boolean oreBlock = java.util.Arrays.stream(MinersOre.values()).anyMatch(ore -> ore.material == material);
        if (material != Material.STONE && material != Material.COBBLESTONE && !oreBlock) { event.setCancelled(true); return; }
        event.setCancelled(true); event.setDropItems(false); event.setExpToDrop(0);
        MinersOre ore = arena.takeOre(event.getBlock());
        Location at = event.getBlock().getLocation().add(.5, .25, .5);
        event.getBlock().setType(Material.AIR, false); // Consume provenance before creating rewards; Fortune cannot duplicate them.
        if (ore == MinersOre.GOLD) collectGold(p);
        else if (ore != null) for (MinersLoot.Drop loot : MinersLoot.roll(ore, random)) {
            Item item = world.dropItem(at, loot.item()); item.setPickupDelay(8); drops.track(item);
        }
        else giveCobble(p);
    }
    private void collectGold(Player p) {
        int before = gold.level(p.getUniqueId());
        if (!gold.collect(p.getUniqueId())) { p.sendActionBar(text("Максимум: 14 сердец")); return; }
        int level = gold.level(p.getUniqueId());
        if (level > before) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 60000, level - 1, true, false));
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) p.setHealth(Math.min(max.getValue(), p.getHealth() + 4));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .6f, 1.4f);
            p.sendMessage(text("+2 максимальных сердца! Теперь " + (10 + level * 2) + "."));
        } else p.sendActionBar(text("Золото: " + gold.count(p.getUniqueId()) % 4 + "/4 до +2 сердец"));
    }
    private void giveCobble(Player p) {
        int count = 0;
        for (ItemStack item : p.getInventory().getContents()) if (item != null && item.getType() == Material.COBBLESTONE) count += item.getAmount();
        if (count < 128) p.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 1));
    }
    @Override public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (!playing(p) || event.getBlock().getType() != Material.COBBLESTONE || !event.getBlockReplacedState().getType().isAir()
                || !arena.editable(event.getBlock()) || !round.fighting() && !arena.owns(event.getBlock().getLocation(), cells.get(p.getUniqueId()))) event.setCancelled(true);
    }
    private Player source(EntityDamageByEntityEvent event) {
        Player owner = bombs.owner(event.getDamageSource().getDirectEntity());
        if (owner == null) owner = bombs.owner(event.getDamager());
        if (owner != null) return owner;
        if (event.getDamager() instanceof Player p) return p;
        return event.getDamager() instanceof Projectile shot && shot.getShooter() instanceof Player p ? p : null;
    }
    private boolean bombBlast(EntityDamageByEntityEvent event) {
        return bombs.owner(event.getDamager()) != null || bombs.owner(event.getDamageSource().getDirectEntity()) != null;
    }
    private boolean validDamage(EntityDamageEvent event, Player victim) {
        if (!fighting(victim)) return false;
        if (!(event instanceof EntityDamageByEntityEvent hit)) return true;
        Player attacker = source(hit);
        if (attacker == null) return false;
        boolean trade = (hit.getDamager() instanceof Projectile || bombBlast(hit)) && connected.contains(attacker.getUniqueId())
                && !standings.alive(attacker.getUniqueId()) && standings.survived(attacker.getUniqueId(), tick) == tick;
        return fighting(attacker) || trade;
    }
    @Override public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !validDamage(event, p)) { event.setCancelled(true); return; }
        if (event instanceof EntityDamageByEntityEvent hit) {
            if (bombBlast(hit)) event.setDamage(PvpBombs.blastDamage(event.getDamage()));
            Player attacker = source(hit);
            if (attacker != p && event.getFinalDamage() > 0) lastHit.put(p.getUniqueId(), new Hit(attacker.getUniqueId(), tick));
        }
    }
    @Override public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player p) || !validDamage(event, p)) event.setCancelled(true);
    }
    @Override public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        if (!connected.contains(p.getUniqueId()) || !standings.alive(p.getUniqueId())) return;
        Hit hit = lastHit.remove(p.getUniqueId());
        UUID killer = hit != null && tick - hit.tick() <= 160 ? hit.attacker() : null;
        event.deathMessage(null); event.deathScreenMessageOverride(text(killer == null ? "Вы погибли" : "Вас убил " + name(killer)));
        event.getDrops().clear(); event.setKeepInventory(true); event.setKeepLevel(true); event.setDroppedExp(0);
        say(killer == null ? p.getName() + " погиб" : name(killer) + " убил " + p.getName());
        out(p);
    }
    private void out(Player p) {
        if (!standings.eliminate(p.getUniqueId(), tick)) return;
        freeze.release(p.getUniqueId()); bombs.eliminate(p.getUniqueId());
        deadAt.put(p.getUniqueId(), tick);
        if (!p.isDead()) spectate(p);
    }
    private void spectate(Player p) {
        if (p.isDead()) return;
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.closeInventory(); p.setItemOnCursor(null);
        services.playerState().resetForRound(p, GameMode.SPECTATOR); p.setAbsorptionAmount(0);
        teleport(p, arena.spectator()); p.showBossBar(bar);
    }
    @Override public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (round.phase() != MinersRound.Phase.DONE && !standings.alive(event.getPlayer().getUniqueId())) event.setRespawnLocation(arena.spectator());
    }
    @Override public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        if (round.phase() != MinersRound.Phase.DONE && !standings.alive(event.getPlayer().getUniqueId())) spectate(event.getPlayer());
    }
    private void result(List<UUID> winners) {
        champions = List.copyOf(winners); round.result(tick);
        shots.clearAll(); bombs.clear(); drops.clear(); freeze.releaseAll();
        say(champions.isEmpty() ? "Ничья — никто не выжил." : "Победитель: " + name(champions.getFirst()));
        List<Component> lines = new ArrayList<>();
        int place = 0;
        for (UUID id : standings.ranking(names.keySet(), tick)) lines.add(text(++place + ". " + name(id)));
        result = List.copyOf(lines);
        for (Player p : survivors()) freeze.freeze(p);
        hud();
    }
    @Override public void onParticipantQuit(Player p) { leave(p); }
    @Override public void onParticipantLeave(Player p) { leave(p); }
    private void leave(Player p) {
        if (standings == null) return;
        standings.eliminate(p.getUniqueId(), tick); connected.remove(p.getUniqueId());
        freeze.release(p.getUniqueId()); bombs.remove(p.getUniqueId()); shots.clear(p.getUniqueId());
        lastHit.remove(p.getUniqueId()); p.closeInventory(); p.setItemOnCursor(null);
    }
    @Override public void onParticipantRejoin(Player p) { connected.add(p.getUniqueId()); spectate(p); }
    @Override public void onParticipantJoin(Player p) { spectate(p); }
    @Override public boolean isInGame(Player p) { return alive(p); }
    @Override public Location spectatorLocation(Player viewer) { return arena == null ? null : arena.spectator(); }
    @Override public boolean mayFollowPlayers(Player viewer) { return !alive(viewer) && round != null && round.fighting(); }
    @Override public void onPlayerMove(PlayerMoveEvent event) {
        if (internalTeleport || round == null || round.phase() == MinersRound.Phase.DONE) return;
        if (freeze.hold(event) || !alive(event.getPlayer())) return;
        if (round.phase() == MinersRound.Phase.MINING && !arena.owns(event.getTo(), cells.get(event.getPlayer().getUniqueId()))) event.setTo(event.getFrom());
    }
    @Override public void onPlayerJump(PlayerJumpEvent event) { freeze.onJump(event); }
    @Override public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (internalTeleport || round == null || round.phase() == MinersRound.Phase.DONE || !names.containsKey(event.getPlayer().getUniqueId()) || !standings.alive(event.getPlayer().getUniqueId())) return;
        if (!fighting(event.getPlayer()) || event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL || !arena.contains(event.getTo())
                || !event.getTo().getBlock().isPassable() || !event.getTo().clone().add(0, 1, 0).getBlock().isPassable()) event.setCancelled(true);
    }
    @Override public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!playing(p)) { event.setCancelled(true); return; }
        if (PvpBombs.isBomb(event.getItem())) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND || !fighting(p)) return;
            if (event.getAction().isLeftClick()) throwBomb(p);
            else if (event.getAction().isRightClick() && event.getClickedBlock() != null) {
                var block = event.getClickedBlock().getRelative(event.getBlockFace());
                if (arena.editable(block) && block.getType().isAir()
                        && block.getLocation().distanceSquared(p.getEyeLocation()) <= 36)
                    bombs.use(p, block.getLocation().add(.5, 0, .5), false, services.plugin());
            }
            return;
        }
        if (isCompass(event.getItem()) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            Player target = round.fighting() ? nearest(p) : null;
            p.sendActionBar(text(target == null ? "После открытия шахт компас укажет ближайшего врага"
                    : name(target.getUniqueId()) + " • " + Math.round(target.getLocation().distance(p.getLocation()))
                    + " м • высота " + (target.getLocation().getBlockY() - p.getLocation().getBlockY())));
            return;
        }
        if (!round.fighting() && event.getItem() != null && (event.getItem().getType() == Material.ENDER_PEARL || event.getItem().getType() == Material.WIND_CHARGE)) event.setCancelled(true);
    }
    @Override public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        if (!PvpBombs.isBomb(event.getPlayer().getInventory().getItemInMainHand())) return;
        event.setCancelled(true); if (fighting(event.getPlayer())) throwBomb(event.getPlayer());
    }
    private void throwBomb(Player p) {
        Location origin = p.getEyeLocation().subtract(0, .65, 0);
        var direction = p.getEyeLocation().getDirection();
        var hit = world.rayTraceBlocks(origin, direction, .8, FluidCollisionMode.NEVER, true);
        Location at = hit == null ? origin.clone().add(direction.clone().multiply(.35)) : origin;
        if (!arena.chargeFits(at)) at = origin;
        if (arena.chargeFits(at)) bombs.use(p, at, true, services.plugin());
        else p.sendActionBar(text("Нет места для TNT: отойди немного от стены"));
    }
    @Override public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player p)) { event.setCancelled(true); return; }
        if (!playing(p) || !round.fighting() && !(event.getEntity() instanceof ThrownPotion)) { event.setCancelled(true); return; }
        shots.track(p.getUniqueId(), event.getEntity(), tick);
    }
    @Override public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player p) || !fighting(p)) { event.setCancelled(true); return; }
        if (event.getProjectile() instanceof AbstractArrow arrow) arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }
    @Override public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player caster) || !playing(caster)) { event.setCancelled(true); return; }
        PvpPotion potion = PvpPotion.from(event.getPotion().getItem());
        if (potion == null) { event.setCancelled(true); return; }
        for (var target : event.getAffectedEntities()) {
            boolean self = target.equals(caster);
            boolean active = target instanceof Player p && playing(p) && (self || round.fighting());
            if (!potion.affects(self, active)) event.setIntensity(target, 0);
        }
    }
    @Override public void onEntityExplode(EntityExplodeEvent event) { event.blockList().clear(); }
    @Override public void onBlockExplode(BlockExplodeEvent event) { event.blockList().clear(); }
    @Override public void onFoodLevelChange(FoodLevelChangeEvent event) { if (!round.fighting()) event.setCancelled(true); }
    @Override public void onDropItem(PlayerDropItemEvent event) {
        if (!playing(event.getPlayer())) event.setCancelled(true); else drops.track(event.getItemDrop());
    }
    @Override public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p) || !playing(p)
                || !round.fighting() && !arena.owns(event.getItem().getLocation(), cells.get(p.getUniqueId()))) event.setCancelled(true);
    }
    @Override public void onCraftItem(CraftItemEvent event) { event.setCancelled(true); }
    @Override public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || !playing(p) || event.getSlotType() == InventoryType.SlotType.CRAFTING
                || event.getSlotType() == InventoryType.SlotType.RESULT) event.setCancelled(true);
    }
    @Override public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || !playing(p)
                || event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) event.setCancelled(true);
    }
    private void cleanup() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        freeze.releaseAll(); bombs.clear(); shots.clearAll(); drops.clear(); gold.clear();
        for (Player p : services.participants().onlinePlayers()) { p.closeInventory(); p.setItemOnCursor(null); }
        if (round != null) round.done();
    }
    @Override public void onEnd(EndReason reason) {
        cleanup();
        if (reason == EndReason.FINISHED) for (UUID id : champions) services.award("party", id, 3);
    }
    @Override public List<Component> summary() { return result; }
    @Override public Component globalResult() { return champions.isEmpty() ? null : text(name(champions.getFirst()) + " победил в Битве шахтёров!"); }
}
