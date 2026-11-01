package me.petr1furious.potatominigames.games.party;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.text.TimeFormat;

final class DuelSession implements GameSession {
    private enum Phase { COUNTDOWN, FIGHT, BREAK, DONE }
    private final SessionServices services;
    private final PartyConfig config;
    private final boolean sumo;
    private final String title;
    private final Set<UUID> fighters = new HashSet<>();
    private final SumoCombat sumoCombat = new SumoCombat();
    private final Random random = new Random();
    private final NamespacedKey abilityKey;
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, PvpClass> kits = new HashMap<>();
    private final Map<UUID, Long> readyAt = new HashMap<>();
    private final Set<UUID> withdrawn = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final Deque<UUID> playoffs = new ArrayDeque<>();
    private DuelTournament tournament;
    private DuelTournament.Match match;
    private List<PvpClass> opening;
    private PvpClass decider;
    private DuelArena arena;
    private World world;
    private BossBar bar;
    private Phase phase = Phase.BREAK;
    private long now;
    private long deadline;
    private long fightStarted;
    private int scheduleIndex;
    private int roundLength;
    private int countdown;
    private int intermission;
    private boolean suddenDeath;
    private boolean playoff;
    private boolean training;
    private boolean internalTeleport;
    private UUID champion;
    private BukkitTask resolution;
    private List<Component> result = List.of();

    DuelSession(SessionServices services, PartyConfig config, boolean sumo) {
        this.services = services; this.config = config;
        this.sumo = sumo;
        title = sumo ? "Сумо" : "PvP";
        abilityKey = new NamespacedKey(services.plugin(), "party_pvp_ability");
    }
    @Override public void onStart() throws CommandFailure {
        List<UUID> roster = new ArrayList<>();
        for (var participant : services.participants().all()) {
            roster.add(participant.id()); names.put(participant.id(), participant.name());
        }
        if (roster.isEmpty()) throw new CommandFailure("Для турнира нужны участники.");
        training = roster.size() == 1;
        Collections.shuffle(roster, random);
        if (!training) tournament = new DuelTournament(roster);
        // Read once so config reload cannot change the rules halfway through a duel.
        roundLength = Math.clamp((sumo ? config.sumoRoundSeconds : config.roundSeconds).get(), 20, 300);
        countdown = Math.clamp((sumo ? config.sumoCountdownSeconds : config.countdownSeconds).get(), 3, 15);
        intermission = Math.clamp((sumo ? config.sumoIntermissionSeconds : config.intermissionSeconds).get(), 2, 30);
        world = services.worlds().createVoid(sumo ? "party_sumo" : "party_pvp");
        services.arena().applyStandardGameRules(world);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.PVP, true);
        world.setTime(6000);
        world.setStorm(false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        arena = sumo ? new SumoArena(world) : new PvpArena(world);
        arena.build();
        bar = services.bossBars().create(text(title + " • BO3", NamedTextColor.GOLD), BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        services.messages().broadcast(text(title + ": каждый с каждым, матч до двух побед. Победа в матче — 3 очка.", NamedTextColor.GOLD));
        services.messages().broadcast(text(sumo
                ? "Столкни соперника с платформы! Удары отбрасывают без потери здоровья. Строить нельзя."
                : "Раунд 1: случайные классы; раунд 2: обмен классами; раунд 3: одинаковый случайный класс.", NamedTextColor.GRAY));
        if (training) {
            services.messages().broadcast(text("Тренировка: один игрок, без турнирных очков. /party stop — закончить.", NamedTextColor.YELLOW));
            prepareRound();
        } else nextMatch();
    }
    private static Component text(String s, NamedTextColor color) { return Component.text(s, color); }
    private String name(UUID id) { return names.getOrDefault(id, "Игрок"); }
    private Player player(UUID id) { return withdrawn.contains(id) ? null : Bukkit.getPlayer(id); }
    private boolean fighter(Player p) { return fighters.contains(p.getUniqueId()) && !withdrawn.contains(p.getUniqueId()); }
    private boolean fighting(Player p) {
        return phase == Phase.FIGHT && fighter(p) && !eliminated.contains(p.getUniqueId()) && p.getGameMode() == GameMode.SURVIVAL;
    }
    private void teleport(Player p, Location to) {
        internalTeleport = true;
        try { p.teleport(to); } finally { internalTeleport = false; }
    }
    private void spectator(Player p) {
        if (p.isDead()) p.spigot().respawn();
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        services.playerState().resetForRound(p, GameMode.SPECTATOR);
        p.setAbsorptionAmount(0);
        teleport(p, arena.spectator());
        p.showBossBar(bar);
    }
    private void nextMatch() {
        kits.clear(); fighters.clear();
        if (!services.session().isRunning()) return;
        if (playoff) {
            playoffs.removeIf(id -> player(id) == null);
            // The winner remains to face the next tied contender. All playoff matches are BO3.
            if (playoffs.size() <= 1) {
                champion = playoffs.peekFirst();
                if (champion == null) {
                    List<UUID> eligible = names.keySet().stream().filter(id -> player(id) != null).toList();
                    champion = tournament.titleContenders(eligible).stream().findFirst().orElse(null);
                }
                finishTournament(); return;
            }
            match = new DuelTournament.Match(new DuelTournament.Pair(playoffs.removeFirst(), playoffs.removeFirst()));
        } else if (scheduleIndex < tournament.schedule().size()) {
            match = new DuelTournament.Match(tournament.schedule().get(scheduleIndex++));
        } else {
            List<UUID> eligible = tournament.standings().stream().filter(id -> !withdrawn.contains(id)).toList();
            if (eligible.isEmpty()) { services.session().endGame(EndReason.ALL_PLAYERS_LEFT); return; }
            List<UUID> tied = tournament.titleContenders(eligible);
            if (tied.size() > 1) {
                playoff = true;
                List<UUID> shuffled = new ArrayList<>(tied);
                Collections.shuffle(shuffled, random);
                playoffs.addAll(shuffled);
                services.messages().broadcast(text("Равенство очков и личных встреч: дополнительные BO3 за первое место!", NamedTextColor.GOLD));
                nextMatch(); return;
            }
            champion = tied.getFirst(); finishTournament(); return;
        }
        if (!sumo) {
            opening = PvpClass.opening(random);
            decider = PvpClass.values()[random.nextInt(PvpClass.values().length)];
        }
        // Forfeits are processed on the next tick, avoiding recursive traversal of the schedule.
        phase = Phase.BREAK; deadline = now + 1;
        if (player(match.pair().first()) != null && player(match.pair().second()) != null) {
            services.messages().broadcast(text((playoff ? "Тай-брейк: " : "Матч " + scheduleIndex + "/" + tournament.schedule().size() + ": ")
                    + name(match.pair().first()) + " vs " + name(match.pair().second()), NamedTextColor.AQUA));
            prepareRound();
        }
    }
    private void prepareRound() {
        phase = Phase.COUNTDOWN;
        deadline = now + countdown;
        suddenDeath = false; eliminated.clear(); readyAt.clear(); kits.clear(); fighters.clear(); sumoCombat.reset();
        arena.reset();
        services.arena().applyBorder(world, arena.center(), 53, 53, 0);
        for (Player p : services.participants().onlinePlayers()) spectator(p);
        if (training) {
            UUID id = names.keySet().iterator().next();
            fighters.add(id);
            if (!sumo) kits.put(id, PvpClass.values()[random.nextInt(PvpClass.values().length)]);
        } else {
            fighters.add(match.pair().first()); fighters.add(match.pair().second());
            if (!sumo) {
                int round = match.roundsWon();
                kits.put(match.pair().first(), round == 2 ? decider : opening.get(round == 0 ? 0 : 1));
                kits.put(match.pair().second(), round == 2 ? decider : opening.get(round == 0 ? 1 : 0));
            }
        }
        for (UUID id : fighters) {
            Player p = player(id);
            if (p == null) continue;
            p.setSpectatorTarget(null);
            services.playerState().resetForRound(p, GameMode.SURVIVAL);
            p.setAbsorptionAmount(0); p.setNoDamageTicks(0); p.setSaturation(20);
            PvpClass kit = kits.get(id);
            if (kit != null) kit.equip(p, abilityKey);
            boolean east = !training && match.pair().second().equals(id);
            if (!training && match.roundsWon() % 2 == 1) east = !east;
            teleport(p, arena.spawn(east));
            if (kit != null) {
                p.sendMessage(text("Класс: " + kit.title + " — " + kit.description, NamedTextColor.GREEN));
                p.sendMessage(text("Способность в слоте 8: ПКМ. Ломать можно только поставленные игроками блоки.", NamedTextColor.GRAY));
            } else p.sendMessage(text("Сумо: бей пустой рукой и столкни соперника вниз. Спринт усиливает отбрасывание.", NamedTextColor.GREEN));
        }
        updateHud();
    }
    @Override public void onSecondTick(long secondsElapsed) {
        now = secondsElapsed;
        if (phase == Phase.DONE) return;
        if (names.keySet().stream().allMatch(withdrawn::contains)) {
            services.session().endGame(EndReason.ALL_PLAYERS_LEFT); return;
        }
        if (!training && match != null && !match.isComplete()
                && (player(match.pair().first()) == null || player(match.pair().second()) == null)) {
            UUID a = match.pair().first(), b = match.pair().second();
            completeMatch(player(a) == null ? player(b) == null ? null : b : a);
            deadline = now + 1;
            updateHud(); return;
        }
        if (phase == Phase.COUNTDOWN && now >= deadline) {
            phase = Phase.FIGHT; fightStarted = now;
            services.messages().broadcast(text("Бой!", NamedTextColor.RED));
            for (UUID id : fighters) {
                Player p = player(id);
                if (p != null) { p.setNoDamageTicks(0); p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1.5f); }
            }
        } else if (phase == Phase.BREAK && now >= deadline) {
            if (training || !match.isComplete()) prepareRound(); else nextMatch();
        } else if (phase == Phase.FIGHT) {
            long elapsed = now - fightStarted;
            arena.tick(elapsed, roundLength);
            if (sumo) {
                for (UUID id : fighters) {
                    Player p = player(id);
                    if (p != null && arena.isOut(p.getLocation())) ringOut(p);
                }
                if (phase != Phase.FIGHT) { updateHud(); return; }
            }
            if (elapsed >= roundLength && !suddenDeath) {
                suddenDeath = true;
                if (!sumo) services.arena().applyBorder(world, arena.center(), 53, 7, 30);
                services.messages().broadcast(text(sumo ? "Овертайм! Края платформы исчезают каждые 3 секунды."
                        : "Овертайм! Граница сужается, через 30 секунд сравним оставшееся здоровье.", NamedTextColor.RED));
            }
            if (elapsed >= roundLength + 30) {
                if (training) { phase = Phase.BREAK; deadline = now + intermission; }
                else if (sumo) finishRound(null);
                else {
                    Player a = player(match.pair().first()), b = player(match.pair().second());
                    int comparison = Double.compare(a.getHealth() + a.getAbsorptionAmount(), b.getHealth() + b.getAbsorptionAmount());
                    finishRound(comparison == 0 ? null : comparison > 0 ? a.getUniqueId() : b.getUniqueId());
                }
            }
        }
        updateHud();
    }
    private void finishRound(UUID winner) {
        if (phase != Phase.FIGHT) return;
        phase = Phase.BREAK; deadline = now + intermission;
        arena.reset();
        for (UUID id : fighters) { Player p = player(id); if (p != null) spectator(p); }
        if (winner == null) services.messages().broadcast(text(sumo ? "Ничья в раунде — переигровка."
                : "Ничья в раунде — переигровка с теми же классами.", NamedTextColor.YELLOW));
        else {
            match.winRound(winner);
            services.messages().broadcast(text(name(winner) + " берёт раунд! " + match.wins(match.pair().first()) + ":"
                    + match.wins(match.pair().second()), NamedTextColor.GREEN));
            if (match.winner() != null) completeMatch(match.winner());
        }
    }
    private void completeMatch(UUID winner) {
        boolean forfeit = !match.isComplete();
        if (forfeit) match.forfeit(winner);
        if (!playoff) tournament.record(match.pair(), winner);
        else if (winner != null) playoffs.addFirst(winner);
        if (winner != null) {
            services.messages().broadcast(text(name(winner) + (forfeit ? " • техническая победа" : " выиграл BO3")
                    + (playoff ? "!" : " • +3 очка"), NamedTextColor.GOLD));
        }
        phase = Phase.BREAK; deadline = now + intermission;
        kits.clear(); fighters.clear(); arena.reset();
        for (Player p : services.participants().onlinePlayers()) spectator(p);
    }
    private void finishTournament() {
        List<UUID> ordered = new ArrayList<>(tournament.standings().stream()
                .sorted(java.util.Comparator.comparing(withdrawn::contains)).toList());
        if (champion != null) { ordered.remove(champion); ordered.addFirst(champion); }
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            UUID id = ordered.get(i);
            lines.add(text((i + 1) + ". " + name(id) + " — " + tournament.points(id) + " очков"
                    + (withdrawn.contains(id) ? " (выбыл)" : ""), i == 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY));
        }
        result = List.copyOf(lines);
        services.session().endGame(EndReason.FINISHED);
    }
    @Override public void onEnd(EndReason reason) {
        phase = Phase.DONE;
        if (resolution != null) resolution.cancel();
        if (reason == EndReason.FINISHED && tournament != null) {
            for (UUID id : names.keySet()) services.award("party", id, tournament.points(id));
        }
        if (arena != null) arena.reset();
    }
    @Override public List<Component> summary() { return result; }
    @Override public Component globalResult() {
        return champion == null ? null : text(name(champion) + " выиграл турнир «" + title + "»!", NamedTextColor.GOLD);
    }
    private void updateHud() {
        if (bar == null || phase == Phase.DONE) return;
        long remaining = phase == Phase.FIGHT ? Math.max(0, roundLength + (suddenDeath ? 30 : 0) - (now - fightStarted))
                : Math.max(0, deadline - now);
        String barTitle = phase == Phase.COUNTDOWN ? "Бой через " + remaining + " с"
                : phase == Phase.BREAK ? "Следующий раунд через " + remaining + " с"
                : (suddenDeath ? "Овертайм • " : title + " • ") + TimeFormat.clock(remaining);
        bar.name(text(barTitle, NamedTextColor.GOLD));
        bar.progress(Math.clamp((float) remaining / (phase == Phase.FIGHT ? suddenDeath ? 30 : roundLength : countdown), 0, 1));
        List<Component> lines = new ArrayList<>();
        if (!training && match != null) {
            lines.add(text((playoff ? "Тай-брейк" : "Матч " + scheduleIndex + "/" + tournament.schedule().size()) + " • BO3", NamedTextColor.AQUA));
            lines.add(text(name(match.pair().first()) + " " + match.wins(match.pair().first()) + ":"
                    + match.wins(match.pair().second()) + " " + name(match.pair().second()), NamedTextColor.WHITE));
            for (UUID id : tournament.standings().stream().limit(8).toList()) {
                lines.add(text(name(id) + ": " + tournament.points(id), NamedTextColor.GRAY));
            }
        } else lines.add(text("Тренировка", NamedTextColor.YELLOW));
        for (Player p : services.participants().onlinePlayers()) {
            List<Component> own = new ArrayList<>(lines);
            PvpClass kit = kits.get(p.getUniqueId());
            if (kit != null) {
                own.add(text("Класс: " + kit.title, NamedTextColor.GREEN));
                long cooldown = Math.max(0, readyAt.getOrDefault(p.getUniqueId(), 0L) - now);
                own.add(text("ПКМ: " + (cooldown == 0 ? "готово" : cooldown + " с"), NamedTextColor.GOLD));
            }
            if (sumo) own.add(text("Столкни соперника вниз!", NamedTextColor.GREEN));
            services.sidebar().update(p, text("Вечеринка • " + title, NamedTextColor.LIGHT_PURPLE), own);
            p.showBossBar(bar);
        }
    }
    @Override public boolean isInGame(Player player) {
        // Waiting participants stay enrolled, but the camera only follows active fighters.
        return fighter(player) && !eliminated.contains(player.getUniqueId());
    }
    @Override public boolean mayFollowPlayers(Player player) { return !isInGame(player); }
    @Override public Location spectatorLocation(Player viewer) { return arena == null ? null : arena.spectator(); }
    @Override public void onParticipantQuit(Player p) { withdraw(p); }
    @Override public void onParticipantLeave(Player p) { withdraw(p); }
    private void withdraw(Player p) {
        if (phase == Phase.DONE || !names.containsKey(p.getUniqueId())) return;
        withdrawn.add(p.getUniqueId());
        services.messages().broadcast(text(p.getName() + " выбыл из турнира; оставшиеся матчи — технические поражения.", NamedTextColor.YELLOW));
    }
    @Override public void onParticipantRejoin(Player p) {
        // Reconnecting does not reroll a kit or erase a forfeit.
        withdrawn.add(p.getUniqueId());
        spectator(p);
        p.sendMessage(text("Вы выбыли из этого турнира и можете наблюдать.", NamedTextColor.YELLOW));
    }
    @Override public void onParticipantJoin(Player p) { onParticipantRejoin(p); }
    private Player source(Entity entity) {
        if (entity instanceof Player p) return p;
        return entity instanceof Projectile projectile && projectile.getShooter() instanceof Player p ? p : null;
    }
    private boolean validDamage(EntityDamageEvent event, Player victim) {
        if (!fighting(victim)) return false;
        if (event instanceof EntityDamageByEntityEvent hit) {
            Player attacker = source(hit.getDamager());
            // An already airborne projectile may trade kills in the same server tick.
            boolean trade = attacker != null && hit.getDamager() instanceof Projectile
                    && resolution != null && fighter(attacker) && eliminated.contains(attacker.getUniqueId());
            return attacker != null && (fighting(attacker) || trade);
        }
        return true;
    }
    @Override public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player p) || !validDamage(event, p)) event.setCancelled(true);
    }
    @Override public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (sumo) {
            event.setCancelled(true);
            if (!fighting(p)) return;
            if (arena.isOut(p.getLocation()) || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                ringOut(p); return;
            }
            if (event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Player attacker
                    && attacker != p && fighting(attacker)
                    && attacker.getInventory().getItemInMainHand().getType() == Material.AIR
                    && sumoCombat.acceptHit(p.getUniqueId(), Bukkit.getCurrentTick())) {
                var away = p.getLocation().toVector().subtract(attacker.getLocation().toVector());
                if (away.clone().setY(0).lengthSquared() < 1.0e-6) away = attacker.getLocation().getDirection();
                p.setVelocity(SumoCombat.knockback(away, p.getVelocity(), attacker.isSprinting()));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, .5f, 1);
            }
            return;
        }
        if (!validDamage(event, p)) { event.setCancelled(true); return; }
        if (event.getFinalDamage() >= p.getHealth()) {
            event.setCancelled(true);
            if (training) { phase = Phase.BREAK; deadline = now + intermission; spectator(p); return; }
            eliminate(p);
        }
    }
    private void ringOut(Player player) {
        if (!fighting(player)) return;
        if (training) {
            phase = Phase.BREAK; deadline = now + intermission;
            spectator(player);
        } else eliminate(player);
    }
    private void eliminate(Player p) {
        eliminated.add(p.getUniqueId());
        p.setGameMode(GameMode.SPECTATOR);
        if (resolution == null) resolution = Bukkit.getScheduler().runTask(services.plugin(), () -> {
            resolution = null;
            if (phase != Phase.FIGHT || !services.session().isRunning()) return;
            UUID a = match.pair().first(), b = match.pair().second();
            finishRound(eliminated.contains(a) && eliminated.contains(b) ? null : eliminated.contains(a) ? b : a);
        });
    }
    @Override public void onPlayerDeath(PlayerDeathEvent event) {
        if (!fighter(event.getPlayer())) return;
        event.deathMessage(null); event.getDrops().clear(); event.setDroppedExp(0);
        event.setKeepInventory(true); event.setKeepLevel(true);
        Bukkit.getScheduler().runTask(services.plugin(), () -> {
            if (!services.session().isRunning()) return;
            Player p = event.getPlayer();
            if (p.isOnline()) { p.spigot().respawn(); if (!training && phase == Phase.FIGHT) eliminate(p); else spectator(p); }
        });
    }
    @Override public void onBlockBreak(BlockBreakEvent event) {
        if (!fighting(event.getPlayer()) || !arena.isPlaced(event.getBlock())) { event.setCancelled(true); return; }
        arena.broken(event.getBlock());
        event.setDropItems(false); event.setExpToDrop(0);
    }
    @Override public void onBlockPlace(BlockPlaceEvent event) {
        if (!fighting(event.getPlayer()) || !arena.canPlace(event.getBlock())
                || event.getBlock().getType() != Material.WHITE_WOOL || !event.getBlockReplacedState().getType().isAir()) {
            event.setCancelled(true); return;
        }
        arena.track(event.getBlock());
    }
    @Override public void onEntityExplode(EntityExplodeEvent event) { event.blockList().clear(); }
    @Override public void onBlockExplode(BlockExplodeEvent event) { event.blockList().clear(); }
    @Override public void onPlayerInteract(PlayerInteractEvent event) {
        if (sumo || !fighting(event.getPlayer())) { event.setCancelled(true); return; }
        if (event.getClickedBlock() != null) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick() || event.getItem() == null
                || !event.getItem().getItemMeta().getPersistentDataContainer().has(abilityKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        long remaining = readyAt.getOrDefault(p.getUniqueId(), 0L) - now;
        if (remaining > 0) { p.sendActionBar(text("Перезарядка: " + remaining + " с", NamedTextColor.RED)); return; }
        PvpClass kit = kits.get(p.getUniqueId());
        kit.activate(p); readyAt.put(p.getUniqueId(), now + kit.cooldown);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1.5f);
        services.messages().broadcast(text(p.getName() + " • " + kit.ability, NamedTextColor.YELLOW));
    }
    @Override public void onPlayerMove(PlayerMoveEvent event) {
        if (!fighter(event.getPlayer()) || internalTeleport) return;
        if (sumo) {
            if (phase == Phase.COUNTDOWN && event.hasChangedPosition()) event.setTo(event.getFrom());
            else if (phase == Phase.FIGHT && arena.isOut(event.getTo())) ringOut(event.getPlayer());
            return;
        }
        if (phase == Phase.COUNTDOWN && event.hasChangedPosition() || !arena.contains(event.getTo())) {
            event.setTo(event.getFrom());
        }
    }
    @Override public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (internalTeleport || !fighter(event.getPlayer())) return;
        if (sumo || !fighting(event.getPlayer()) || event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || !arena.contains(event.getTo())) event.setCancelled(true);
    }
    @Override public void onEntityShootBow(EntityShootBowEvent event) {
        if (sumo || !(event.getEntity() instanceof Player p) || !fighting(p)) event.setCancelled(true);
    }
    @Override public void onFoodLevelChange(FoodLevelChangeEvent event) { event.setCancelled(true); }
    @Override public void onDropItem(PlayerDropItemEvent event) { event.setCancelled(true); }
    @Override public void onPickupItem(EntityPickupItemEvent event) { event.setCancelled(true); }
    @Override public void onCraftItem(CraftItemEvent event) { event.setCancelled(true); }
    @Override public void onInventoryClick(InventoryClickEvent event) {
        if (event.getSlotType() == InventoryType.SlotType.CRAFTING || event.getSlotType() == InventoryType.SlotType.RESULT
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ALL_SLOT
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ONE_SLOT
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ALL_CURSOR
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ONE_CURSOR) event.setCancelled(true);
    }
}
