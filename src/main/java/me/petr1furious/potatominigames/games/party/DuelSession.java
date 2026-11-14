package me.petr1furious.potatominigames.games.party;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

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
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Trident;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
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

final class DuelSession implements PartyRoundSession {
    private enum Phase {
        SELECTING,
        COUNTDOWN,
        FIGHT,
        RESULT,
        BREAK,
        DONE
    }

    private record Hit(UUID attacker, int tick) {}

    private final SessionServices services;
    private final PartyRoundContext context;
    private final boolean ffa;
    private SurvivalStandings standings;
    private final Map<UUID, BukkitTask> recoveries = new HashMap<>();
    private final PartyConfig config;
    private final boolean sumo;
    private final boolean freeForAll;
    private final boolean chooseKits;
    private final PvpKitMenu kitMenu = new PvpKitMenu();
    private PvpKitChoices kitChoices;
    private final Component title;
    private final Set<UUID> fighters = new HashSet<>();
    private final SumoCombat sumoCombat = new SumoCombat();
    private final CountdownFreeze countdownFreeze = new CountdownFreeze();
    private final ArenaProjectiles projectiles = new ArenaProjectiles();
    private final PvpCombat combat = new PvpCombat();
    private final PvpMounts mounts = new PvpMounts();
    private final PvpHarpoon harpoon = new PvpHarpoon();
    private final PvpThunder thunder = new PvpThunder();
    private final PvpAirstrike airstrike = new PvpAirstrike();
    private final PvpShadowArmor shadowArmor = new PvpShadowArmor(this::fighting);
    private final PvpBombs bombs = new PvpBombs(this::bombImpactTarget);
    private final DeathPause deathPause = new DeathPause();
    private final Map<UUID, Hit> lastHit = new HashMap<>();
    private final Random random = new Random();
    private NamespacedKey abilityKey;
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
    private PvpDuelKits selectedKits;
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
    private long ffaResultUntil;
    private List<Component> result = List.of();

    DuelSession(SessionServices services, PartyConfig config, boolean sumo) {
        this(services, config, sumo, PartyRoundContext.standalone(services));
    }

    DuelSession(SessionServices services, PartyConfig config, boolean sumo, boolean freeForAll) {
        this(services, config, sumo, PartyRoundContext.standalone(services), freeForAll);
    }

    DuelSession(
            SessionServices services,
            PartyConfig config,
            boolean sumo,
            boolean freeForAll,
            boolean chooseKits) {
        this(
                services,
                config,
                sumo,
                PartyRoundContext.standalone(services),
                freeForAll,
                chooseKits);
    }

    DuelSession(
            SessionServices services, PartyConfig config, boolean sumo, PartyRoundContext context) {
        this(services, config, sumo, context, false);
    }

    private DuelSession(SessionServices services, PartyConfig config, boolean sumo,
            PartyRoundContext context, boolean freeForAll) {
        this(services, config, sumo, context, freeForAll, false);
    }

    private DuelSession(
            SessionServices services,
            PartyConfig config,
            boolean sumo,
            PartyRoundContext context,
            boolean freeForAll,
            boolean chooseKits) {
        if (chooseKits && (sumo || !freeForAll))
            throw new IllegalArgumentException("Kit selection requires group PvP");
        this.context = context;
        this.chooseKits = chooseKits;
        this.freeForAll = freeForAll;
        this.ffa = context.embedded() || freeForAll;
        this.services = context.services();
        this.config = config;
        this.sumo = sumo;
        title =
                I18n.tr(
                        chooseKits
                                ? "party.rounds.mode.pvp_select"
                                : freeForAll
                                        ? sumo
                                                ? "party.rounds.mode.sumo_ffa"
                                                : "party.rounds.mode.pvp_ffa"
                                        : sumo
                                                ? "party.duels.sumo.title"
                                                : "party.duels.pvp.title");
    }

    @Override
    public void onStart() throws CommandFailure {
        try {
            start();
        } catch (CommandFailure | RuntimeException failure) {
            if (chooseKits) for (Player p : services.participants().onlinePlayers()) kitMenu.close(p);
            countdownFreeze.releaseAll();
            mounts.clear();
            harpoon.clear();
            thunder.clear();
            airstrike.clear();
            bombs.clear();
            shadowArmor.close();
            throw failure;
        }
    }

    private void start() throws CommandFailure {
        abilityKey = new NamespacedKey(services.plugin(), "party_pvp_ability");
        List<UUID> roster = new ArrayList<>();
        for (var participant : services.participants().all()) {
            roster.add(participant.id());
            names.put(participant.id(), participant.name());
        }
        if (roster.isEmpty()) throw new CommandFailure(I18n.tr("party.duels.tournament.participants_required"));
        try {
            selectedKits = PvpDuelKits.fromOptions(services.options());
        } catch (IllegalArgumentException failure) {
            throw new CommandFailure(I18n.message(failure));
        }
        if (selectedKits != null && (ffa || sumo || !selectedKits.matches(roster)))
            throw new CommandFailure(I18n.tr("party.duels.duel.invalid_mode"));
        if (freeForAll && roster.size() > 16) throw new CommandFailure(I18n.tr("party.duels.ffa.player_limit", 16));
        training = roster.size() == 1;
        Collections.shuffle(roster, random);
        if (ffa) standings = new SurvivalStandings(roster);
        else if (!training) tournament = new DuelTournament(roster);
        // Read once so config reload cannot change the rules halfway through a duel.
        roundLength = freeForAll
                ? (sumo ? config.sumoFfaOvertimeSeconds : config.pvpFfaOvertimeSeconds).get()
                : Math.clamp((sumo ? config.sumoRoundSeconds : config.roundSeconds).get(), 20, 300);
        countdown =
                Math.clamp(
                        context.embedded()
                                ? context.options().countdownSeconds()
                                : (sumo ? config.sumoCountdownSeconds : config.countdownSeconds)
                                        .get(),
                        context.embedded() ? 0 : 3,
                        15);
        intermission =
                Math.clamp(
                        (sumo ? config.sumoIntermissionSeconds : config.intermissionSeconds).get(),
                        2,
                        30);
        world = context.createVoid(sumo ? "party_sumo" : "party_pvp");
        services.arena().applyStandardGameRules(world);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, !sumo);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        arena = sumo ? new SumoArena(world) : new PvpArena(world, random);
        arena.build();
        if (arena instanceof PvpArena pvp)
            services.messages().broadcast(text("arena.selected", NamedTextColor.GRAY, pvp.layout().title()));
        if (!sumo) shadowArmor.start(services.plugin());
        bar =
                services.bossBars()
                        .create(
                                text(ffa ? "tournament.ffa_bar" : "tournament.bo3_bar",
                                        NamedTextColor.GOLD, title),
                                BossBar.Color.YELLOW,
                                BossBar.Overlay.PROGRESS);
        services.messages()
                .broadcast(
                        text(freeForAll ? "ffa.rules" : ffa ? "tournament.ffa_rules" : "tournament.bo3_rules", NamedTextColor.GOLD, title));
        if (chooseKits)
            services.messages()
                    .broadcast(
                            text("pvp.choice.rules", NamedTextColor.GRAY, PvpKitChoices.SECONDS));
        else
            services.messages()
                    .broadcast(
                            text(
                                    sumo
                                            ? "sumo.rules"
                                            : selectedKits != null
                                                    ? "pvp.selected_round_rules"
                                                    : "pvp.round_rules",
                                    NamedTextColor.GRAY));
        if (ffa) prepareRound();
        else if (training) {
            services.messages()
                    .broadcast(
                            text("tournament.training_rules", NamedTextColor.YELLOW));
            prepareRound();
        } else nextMatch();
    }

    private static Component text(String key, NamedTextColor color, Object... args) {
        return I18n.tr("party.duels." + key, args).color(color);
    }

    private static Component text(Component value, NamedTextColor color) {
        return value.color(color);
    }

    private Component name(UUID id) {
        String value = names.get(id);
        return value == null ? I18n.tr("party.duels.player") : Component.text(value);
    }

    private Player player(UUID id) {
        return withdrawn.contains(id) ? null : Bukkit.getPlayer(id);
    }

    private boolean fighter(Player p) {
        return fighters.contains(p.getUniqueId()) && !withdrawn.contains(p.getUniqueId());
    }

    private boolean fighting(Player p) {
        return phase == Phase.FIGHT
                && fighter(p)
                && !eliminated.contains(p.getUniqueId())
                && p.getGameMode() == GameMode.SURVIVAL
                && !p.isDead();
    }

    private boolean bombImpactTarget(Player owner, Entity entity) {
        Player target = entity instanceof Player p ? p : mounts.owner(entity);
        return target != null
                && !target.getUniqueId().equals(owner.getUniqueId())
                && fighting(target);
    }

    private void teleport(Player p, Location to) {
        internalTeleport = true;
        try {
            p.teleport(to);
        } finally {
            internalTeleport = false;
        }
    }

    private void spectator(Player p) {
        mounts.remove(p.getUniqueId());
        harpoon.remove(p.getUniqueId());
        thunder.remove(p.getUniqueId());
        airstrike.remove(p.getUniqueId());
        shadowArmor.remove(p.getUniqueId());
        bombs.remove(p.getUniqueId());
        if (p.isDead()) p.spigot().respawn();
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        services.playerState().resetForRound(p, GameMode.SPECTATOR);
        p.setAbsorptionAmount(0);
        teleport(p, arena.spectator());
        p.showBossBar(bar);
    }

    private void nextMatch() {
        kits.clear();
        fighters.clear();
        if (!services.session().isRunning()) return;
        if (playoff) {
            playoffs.removeIf(id -> player(id) == null);
            // The winner remains to face the next tied contender. All playoff matches are BO3.
            if (playoffs.size() <= 1) {
                champion = playoffs.peekFirst();
                if (champion == null) {
                    List<UUID> eligible =
                            names.keySet().stream().filter(id -> player(id) != null).toList();
                    champion =
                            tournament.titleContenders(eligible).stream().findFirst().orElse(null);
                }
                finishTournament();
                return;
            }
            match =
                    new DuelTournament.Match(
                            new DuelTournament.Pair(
                                    playoffs.removeFirst(), playoffs.removeFirst()));
        } else if (scheduleIndex < tournament.schedule().size()) {
            match = new DuelTournament.Match(tournament.schedule().get(scheduleIndex++));
        } else {
            List<UUID> eligible =
                    tournament.standings().stream().filter(id -> !withdrawn.contains(id)).toList();
            if (eligible.isEmpty()) {
                context.finish(EndReason.ALL_PLAYERS_LEFT, PartyRoundResult.empty());
                return;
            }
            List<UUID> tied = tournament.titleContenders(eligible);
            if (tied.size() > 1) {
                playoff = true;
                List<UUID> shuffled = new ArrayList<>(tied);
                Collections.shuffle(shuffled, random);
                playoffs.addAll(shuffled);
                services.messages()
                        .broadcast(
                                text("tournament.tie_announcement", NamedTextColor.GOLD));
                nextMatch();
                return;
            }
            champion = tied.getFirst();
            finishTournament();
            return;
        }
        if (!sumo) {
            opening = selectedKits == null ? PvpClass.opening(random) : selectedKits.opening(match.pair(), random);
            decider = PvpClass.random(random);
            if (selectedKits != null)
                services.messages().broadcast(text("duel.opening", NamedTextColor.GREEN, name(match.pair().first()), opening.getFirst().title, name(match.pair().second()), opening.getLast().title));
        }
        // Forfeits are processed on the next tick, avoiding recursive traversal of the schedule.
        phase = Phase.BREAK;
        deadline = now + 1;
        if (player(match.pair().first()) != null && player(match.pair().second()) != null) {
            services.messages()
                    .broadcast(
                            text(playoff ? "tournament.playoff_match" : "tournament.match",
                                    NamedTextColor.AQUA,
                                    playoff
                                            ? new Object[] {name(match.pair().first()), name(match.pair().second())}
                                            : new Object[] {scheduleIndex, tournament.schedule().size(),
                                                    name(match.pair().first()), name(match.pair().second())}));
            prepareRound();
        }
    }

    private void prepareRound() {
        countdownFreeze.releaseAll();
        projectiles.clearAll();
        mounts.clear();
        harpoon.clear();
        thunder.clear();
        airstrike.clear();
        shadowArmor.clear();
        bombs.clear();
        phase = chooseKits ? Phase.SELECTING : Phase.COUNTDOWN;
        combat.reset();
        deathPause.clear();
        lastHit.clear();
        deadline = now + (chooseKits ? PvpKitChoices.SECONDS : countdown);
        suddenDeath = false;
        eliminated.clear();
        readyAt.clear();
        kits.clear();
        fighters.clear();
        sumoCombat.reset();
        arena.reset();
        int borderSize = sumo ? 53 : PvpArena.BORDER_SIZE;
        services.arena().applyBorder(world, arena.center(), borderSize, borderSize, 0);
        for (Player p : services.participants().onlinePlayers()) spectator(p);
        if (ffa) {
            fighters.addAll(names.keySet());
            if (!sumo && !chooseKits)
                for (UUID id : fighters) kits.put(id, PvpClass.random(random));
        } else if (training) {
            UUID id = names.keySet().iterator().next();
            fighters.add(id);
            if (!sumo) kits.put(id, PvpClass.random(random));
        } else {
            fighters.add(match.pair().first());
            fighters.add(match.pair().second());
            if (!sumo) {
                int round = match.roundsWon();
                kits.put(
                        match.pair().first(),
                        round == 2 ? decider : opening.get(round == 0 ? 0 : 1));
                kits.put(
                        match.pair().second(),
                        round == 2 ? decider : opening.get(round == 0 ? 1 : 0));
            }
        }
        List<UUID> spawnOrder = new ArrayList<>(fighters);
        if (freeForAll) Collections.shuffle(spawnOrder, random);
        int spawnIndex = 0;
        for (UUID id : spawnOrder) {
            Player p = player(id);
            if (p == null) continue;
            p.setSpectatorTarget(null);
            services.playerState().resetForRound(p, GameMode.SURVIVAL);
            p.setAbsorptionAmount(0);
            p.setNoDamageTicks(0);
            PvpClass kit = kits.get(id);
            if (kit != null) kit.equip(p, abilityKey);
            else if (sumo) SumoCombat.equip(p);
            boolean east = !ffa && !training && match.pair().second().equals(id);
            if (!ffa && !training && match.roundsWon() % 2 == 1) east = !east;
            teleport(
                    p,
                    ffa
                            ? arena.spawn(spawnIndex++, spawnOrder.size())
                            : arena.spawn(east));
            if (kit != null) explainKit(p, kit);
            else if (sumo) p.sendMessage(text("sumo.kit_help", NamedTextColor.GREEN));
        }
        for (Player player : services.participants().onlinePlayers())
            countdownFreeze.freeze(player);
        if (chooseKits) {
            kitChoices =
                    new PvpKitChoices(fighters.stream().filter(id -> player(id) != null).toList());
            for (UUID id : fighters) {
                Player p = player(id);
                if (p != null) {
                    p.getInventory().setItem(4, kitMenu.opener(p));
                    p.getInventory().setHeldItemSlot(4);
                    kitMenu.open(p, null);
                }
            }
        }
        updateHud();
        if (ffa && !chooseKits && countdown == 0) onSecondTick(now);
    }

    private void explainKit(Player p, PvpClass kit) {
        p.sendMessage(text("pvp.class_details", NamedTextColor.GREEN, kit.title, kit.description));
        p.sendMessage(text(kit.passiveDescription(), NamedTextColor.AQUA));
        if (kit == PvpClass.DEMOLITIONIST) p.sendMessage(text("pvp.tnt_help", NamedTextColor.GOLD));
        if (kit == PvpClass.RANGER) p.sendMessage(text("pvp.arrow_help", NamedTextColor.GRAY));
        p.sendMessage(
                text(
                        "pvp.ability_details",
                        NamedTextColor.GOLD,
                        kit.ability,
                        kit.abilityDescription(),
                        kit.cooldown));
        p.sendMessage(
                text(
                        kit == PvpClass.SHADOW ? "pvp.shadow_controls" : "pvp.ability_controls",
                        NamedTextColor.GRAY));
    }

    private void startSelectedCountdown() {
        if (phase != Phase.SELECTING || context.ended()) return;
        kits.putAll(kitChoices.finish(random));
        phase = Phase.COUNTDOWN;
        deadline = now + countdown;
        for (UUID id : fighters) {
            Player p = player(id);
            if (p == null) continue;
            p.closeInventory();
            p.setItemOnCursor(null);
            p.getInventory().clear();
            PvpClass kit = kits.get(id);
            if (kit != null) {
                kit.equip(p, abilityKey);
                explainKit(p, kit);
            }
        }
        updateHud();
    }

    @Override
    public void onSecondTick(long secondsElapsed) {
        now = secondsElapsed;
        if (phase == Phase.DONE) return;
        // Preserve the same-tick trade window before the result task locks combat.
        if (!ffa && phase == Phase.FIGHT && resolution != null && !eliminated.isEmpty()) {
            updateHud();
            return;
        }
        if (names.keySet().stream().allMatch(withdrawn::contains)) {
            context.finish(EndReason.ALL_PLAYERS_LEFT, PartyRoundResult.empty());
            return;
        }
        if (!training
                && match != null
                && !match.isComplete()
                && (player(match.pair().first()) == null
                        || player(match.pair().second()) == null)) {
            UUID a = match.pair().first(), b = match.pair().second();
            completeMatch(player(a) == null ? player(b) == null ? null : b : a);
            deadline = now + 1;
            updateHud();
            return;
        }
        if (phase == Phase.SELECTING) {
            if (kitChoices.ready() || now >= deadline) startSelectedCountdown();
            else updateHud();
            return;
        }
        if (phase == Phase.COUNTDOWN)
            PartySounds.countdown(services.participants().onlinePlayers(), (deadline - now) * 20);
        if (phase == Phase.COUNTDOWN && now >= deadline) {
            countdownFreeze.releaseAll();
            phase = Phase.FIGHT;
            fightStarted = now;
            services.messages().broadcast(text("fight", NamedTextColor.RED));
            for (UUID id : fighters) {
                Player p = player(id);
                if (p != null) {
                    p.setNoDamageTicks(0);
                    if (kits.get(id) == PvpClass.LANCER) mounts.spawn(p);
                    if (kits.get(id) == PvpClass.SHADOW) shadowArmor.watch(p);
                    if (kits.get(id) == PvpClass.DEMOLITIONIST)
                        airstrike.watch(p, services.plugin(), abilityKey, this::fighting, arena::contains,
                                () -> Math.max(0, readyAt.getOrDefault(id, 0L) - now));
                    if (kits.get(id) == PvpClass.THUNDERER)
                        thunder.watch(
                                p,
                                services.plugin(),
                                abilityKey,
                                this::fighting,
                                arena::contains,
                                () -> Math.max(0, readyAt.getOrDefault(id, 0L) - now));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1.5f);
                }
            }
        } else if (phase == Phase.BREAK && now >= deadline) {
            if (training || !match.isComplete()) prepareRound();
            else nextMatch();
        } else if (phase == Phase.FIGHT) {
            long elapsed = now - fightStarted;
            arena.tick(elapsed, roundLength);
            if (sumo) {
                for (UUID id : fighters) {
                    Player p = player(id);
                    if (p != null && arena.isOut(p.getLocation())) ringOut(p);
                }
                if (phase != Phase.FIGHT) {
                    updateHud();
                    return;
                }
            }
            if (elapsed >= roundLength && !suddenDeath) {
                suddenDeath = true;
                if (!sumo)
                    services.arena()
                            .applyBorder(world, arena.center(), PvpArena.BORDER_SIZE, freeForAll ? 1 : 7, 30);
                services.messages()
                        .broadcast(
                                text(sumo ? "sumo.overtime" : freeForAll ? "ffa.overtime" : "pvp.overtime", NamedTextColor.RED));
            }
            if (!freeForAll && elapsed >= roundLength + 30) {
                if (ffa) {
                    finishFfa();
                    return;
                } else if (training) {
                    phase = Phase.BREAK;
                    deadline = now + intermission;
                } else if (sumo) finishRound(null);
                else {
                    Player a = player(match.pair().first()), b = player(match.pair().second());
                    int comparison =
                            Double.compare(
                                    a.getHealth() + a.getAbsorptionAmount(),
                                    b.getHealth() + b.getAbsorptionAmount());
                    finishRound(
                            comparison == 0
                                    ? null
                                    : comparison > 0 ? a.getUniqueId() : b.getUniqueId());
                }
            }
        }
        if (!sumo && phase == Phase.FIGHT) {
            projectiles.expire(now * 20);
            mounts.tick(now);
            for (var entry : kits.entrySet()) {
                Player player = player(entry.getKey());
                if (player != null && fighting(player)) entry.getValue().replenishArrows(player);
            }
        }
        updateHud();
    }

    private void finishRound(UUID winner) {
        if (phase != Phase.FIGHT && phase != Phase.RESULT) return;
        phase = Phase.BREAK;
        deadline = now + intermission;
        projectiles.clearAll();
        mounts.clear();
        harpoon.clear();
        thunder.clear();
        airstrike.clear();
        shadowArmor.clear();
        bombs.clear();
        arena.reset();
        for (UUID id : fighters) {
            Player p = player(id);
            if (p != null) spectator(p);
        }
        if (winner == null)
            services.messages()
                    .broadcast(
                            text(sumo ? "sumo.round_draw" : "pvp.round_draw", NamedTextColor.YELLOW));
        else {
            match.winRound(winner);
            services.messages()
                    .broadcast(
                            text("tournament.round_won", NamedTextColor.GREEN,
                                    name(winner), match.wins(match.pair().first()),
                                    match.wins(match.pair().second())));
            if (match.winner() != null) completeMatch(match.winner());
        }
    }

    private void completeMatch(UUID winner) {
        if (resolution != null) {
            resolution.cancel();
            resolution = null;
        }
        countdownFreeze.releaseAll();
        projectiles.clearAll();
        mounts.clear();
        harpoon.clear();
        thunder.clear();
        airstrike.clear();
        shadowArmor.clear();
        bombs.clear();
        boolean forfeit = !match.isComplete();
        if (forfeit) match.forfeit(winner);
        if (!playoff) tournament.record(match.pair(), winner);
        else if (winner != null) playoffs.addFirst(winner);
        if (winner != null) {
            services.messages()
                    .broadcast(
                            text(forfeit
                                            ? playoff ? "tournament.playoff_forfeit" : "tournament.match_forfeit"
                                            : playoff ? "tournament.playoff_won" : "tournament.match_won",
                                    NamedTextColor.GOLD, name(winner)));
        }
        phase = Phase.BREAK;
        deadline = now + intermission;
        kits.clear();
        fighters.clear();
        arena.reset();
        for (Player p : services.participants().onlinePlayers()) spectator(p);
    }

    private void finishTournament() {
        List<UUID> ordered =
                new ArrayList<>(
                        tournament.standings().stream()
                                .sorted(java.util.Comparator.comparing(withdrawn::contains))
                                .toList());
        if (champion != null) {
            ordered.remove(champion);
            ordered.addFirst(champion);
        }
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            UUID id = ordered.get(i);
            lines.add(
                    text(withdrawn.contains(id) ? "tournament.standing_withdrawn" : "tournament.standing",
                            i == 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY,
                            i + 1, name(id), tournament.points(id)));
        }
        result = List.copyOf(lines);
        services.session().endGame(EndReason.FINISHED);
    }

    @Override
    public void onEnd(EndReason reason) {
        if (chooseKits) for (Player p : services.participants().onlinePlayers()) kitMenu.close(p);
        context.hideBars();
        recoveries.values().forEach(BukkitTask::cancel);
        recoveries.clear();
        phase = Phase.DONE;
        countdownFreeze.releaseAll();
        projectiles.clearAll();
        mounts.clear();
        harpoon.clear();
        thunder.clear();
        airstrike.clear();
        shadowArmor.close();
        bombs.clear();
        combat.reset();
        deathPause.clear();
        lastHit.clear();
        if (resolution != null) resolution.cancel();
        if (freeForAll && reason == EndReason.FINISHED && champion != null) services.award("party", champion, 3);
        if (!ffa && reason == EndReason.FINISHED && tournament != null) {
            for (UUID id : names.keySet()) services.award("party", id, tournament.points(id));
        }
        if (arena != null) arena.reset();
    }

    @Override
    public List<Component> summary() {
        return result;
    }

    @Override
    public Component globalResult() {
        return champion == null
                ? null
                : text(freeForAll ? "ffa.global_winner" : "tournament.winner", NamedTextColor.GOLD, name(champion), title);
    }

    private void updateHud() {
        if (bar == null || phase == Phase.DONE) return;
        long remaining =
                phase == Phase.RESULT
                        ? ((ffa ? Math.max(0, ffaResultUntil - Bukkit.getCurrentTick()) : deathPause.remaining(Bukkit.getCurrentTick())) + 19) / 20
                        : phase == Phase.FIGHT
                                ? Math.max(
                                        0,
                                        roundLength + (suddenDeath ? 30 : 0) - (now - fightStarted))
                                : Math.max(0, deadline - now);
        Component barTitle =
                phase == Phase.SELECTING
                        ? text(
                                "pvp.choice.hud",
                                NamedTextColor.GOLD,
                                kitChoices.picked(),
                                kitChoices.size(),
                                remaining)
                        : phase == Phase.COUNTDOWN
                                ? text("hud.fight_countdown", NamedTextColor.GOLD, remaining)
                                : phase == Phase.BREAK
                                        ? text(
                                                "hud.round_countdown",
                                                NamedTextColor.GOLD,
                                                remaining)
                                        : phase == Phase.RESULT
                                                ? text(
                                                        ffa ? "hud.fight_over" : "hud.round_over",
                                                        NamedTextColor.GOLD,
                                                        remaining)
                                                : ffa && suddenDeath
                                                        ? text(
                                                                "hud.ffa_overtime",
                                                                NamedTextColor.GOLD)
                                                        : suddenDeath
                                                                ? text(
                                                                        "hud.overtime",
                                                                        NamedTextColor.GOLD,
                                                                        TimeFormat.clock(remaining))
                                                                : text(
                                                                        "hud.timer",
                                                                        NamedTextColor.GOLD,
                                                                        title,
                                                                        TimeFormat.clock(
                                                                                remaining));
        bar.name(barTitle);
        bar.progress(
                ffa && phase == Phase.FIGHT
                        ? (float) standings.survivors().size() / names.size()
                        : Math.clamp(
                                (float) remaining
                                        / (phase == Phase.FIGHT
                                                ? suddenDeath ? 30 : roundLength
                                                : phase == Phase.SELECTING
                                                        ? PvpKitChoices.SECONDS
                                                        : Math.max(1, countdown)),
                                0,
                                1));
        List<Component> lines = new ArrayList<>();
        if (ffa) {
            lines.add(text("hud.remaining", NamedTextColor.AQUA, standings.survivors().size(), names.size()));
            lines.add(text("hud.ffa_rules", NamedTextColor.WHITE));
            for (UUID id : standings.survivors().stream().limit(8).toList()) lines.add(name(id).color(NamedTextColor.GRAY));
        }
        else if (!training && match != null) {
            lines.add(
                    playoff
                            ? text("hud.playoff", NamedTextColor.AQUA)
                            : text("hud.match", NamedTextColor.AQUA, scheduleIndex, tournament.schedule().size()));
            lines.add(
                    text("hud.score", NamedTextColor.WHITE, name(match.pair().first()),
                            match.wins(match.pair().first()), match.wins(match.pair().second()),
                            name(match.pair().second())));
            for (UUID id : tournament.standings().stream().limit(8).toList()) {
                lines.add(text("hud.points", NamedTextColor.GRAY, name(id), tournament.points(id)));
            }
        } else lines.add(text("hud.training", NamedTextColor.YELLOW));
        for (Player p : services.participants().onlinePlayers()) {
            List<Component> own = new ArrayList<>(lines);
            PvpClass kit = kits.get(p.getUniqueId());
            if (kit != null) {
                own.add(text("hud.class", NamedTextColor.GREEN, kit.title));
                long cooldown =
                        kit == PvpClass.SHADOW
                                ? combat.shadowRemaining(p.getUniqueId(), now)
                                : Math.max(0, readyAt.getOrDefault(p.getUniqueId(), 0L) - now);
                own.add(
                        text(kit == PvpClass.SHADOW
                                        ? cooldown == 0 ? "hud.wand_ready" : "hud.wand_cooldown"
                                        : cooldown == 0 ? "hud.ability_ready" : "hud.ability_cooldown",
                                NamedTextColor.GOLD, cooldown == 0 ? new Object[0] : new Object[] {cooldown}));
            }
            if (sumo) own.add(text("sumo.objective", NamedTextColor.GREEN));
            services.sidebar().update(p, text("hud.title", NamedTextColor.LIGHT_PURPLE, title), own);
            p.showBossBar(bar);
        }
    }

    @Override
    public boolean isInGame(Player player) {
        // Waiting participants stay enrolled, but the camera only follows active fighters.
        return fighter(player) && !eliminated.contains(player.getUniqueId());
    }

    @Override
    public boolean mayFollowPlayers(Player player) {
        return phase != Phase.COUNTDOWN && phase != Phase.SELECTING && !isInGame(player);
    }

    @Override
    public Location spectatorLocation(Player viewer) {
        return arena == null ? null : arena.spectator();
    }

    @Override
    public void onParticipantQuit(Player p) {
        withdraw(p);
    }

    @Override
    public void onParticipantLeave(Player p) {
        withdraw(p);
    }

    private void withdraw(Player p) {
        if (chooseKits) kitMenu.close(p);
        if (phase == Phase.SELECTING && kitChoices != null) kitChoices.withdraw(p.getUniqueId());
        BukkitTask recovery = recoveries.remove(p.getUniqueId());
        if (recovery != null) recovery.cancel();
        context.withdraw(p.getUniqueId());
        if (ffa && standings != null) standings.eliminate(p.getUniqueId(), Bukkit.getCurrentTick());
        countdownFreeze.release(p.getUniqueId());
        projectiles.clear(p.getUniqueId());
        mounts.remove(p.getUniqueId());
        harpoon.remove(p.getUniqueId());
        thunder.remove(p.getUniqueId());
        airstrike.remove(p.getUniqueId());
        shadowArmor.remove(p.getUniqueId());
        bombs.remove(p.getUniqueId());
        combat.forget(p.getUniqueId());
        lastHit.remove(p.getUniqueId());
        deathPause.forget(p.getUniqueId());
        if (phase == Phase.DONE || !names.containsKey(p.getUniqueId())) return;
        withdrawn.add(p.getUniqueId());
        if (ffa) {
            services.messages().broadcast(text("ffa.withdrawn", NamedTextColor.YELLOW, p.getName()));
            scheduleFfaResolution();
            return;
        }
        services.messages()
                .broadcast(
                        text("tournament.withdrawn", NamedTextColor.YELLOW, p.getName()));
    }

    @Override
    public void onParticipantRejoin(Player p) {
        // Reconnecting does not reroll a kit or erase a forfeit.
        withdrawn.add(p.getUniqueId());
        spectator(p);
        if (phase == Phase.COUNTDOWN || phase == Phase.SELECTING) countdownFreeze.freeze(p);
        p.sendMessage(
                text(ffa ? "ffa.spectating" : "tournament.spectating", NamedTextColor.YELLOW));
    }

    @Override
    public void onParticipantJoin(Player p) {
        onParticipantRejoin(p);
    }

    private Player source(Entity entity) {
        if (entity instanceof Player p) return p;
        if (entity instanceof TNTPrimed) return bombs.owner(entity);
        return entity instanceof Projectile projectile
                        && projectile.getShooter() instanceof Player p
                ? p
                : null;
    }

    private boolean bombBlast(EntityDamageByEntityEvent event) {
        return bombs.owner(event.getDamager()) != null
                || bombs.owner(event.getDamageSource().getDirectEntity()) != null;
    }

    private Player source(EntityDamageByEntityEvent event) {
        Player owner = bombs.owner(event.getDamageSource().getDirectEntity());
        return owner == null ? source(event.getDamager()) : owner;
    }

    private boolean validDamage(EntityDamageEvent event, Player victim) {
        if (!fighting(victim)) return false;
        if (event instanceof EntityDamageByEntityEvent hit) {
            Player attacker = source(hit);
            // Airborne projectiles and one TNT blast may trade kills in the same server tick.
            boolean trade =
                    attacker != null
                            && (hit.getDamager() instanceof Projectile || bombBlast(hit))
                            && resolution != null
                            && fighter(attacker)
                            && eliminated.contains(attacker.getUniqueId())
                            && (!ffa || standings.survived(attacker.getUniqueId(), Bukkit.getCurrentTick()) == Bukkit.getCurrentTick());
            return attacker != null && (fighting(attacker) || trade);
        }
        return true;
    }

    private boolean validMountDamage(EntityDamageEvent event, Player owner) {
        if (sumo || !fighting(owner)) return false;
        if (event instanceof EntityDamageByEntityEvent hit) {
            Player attacker = source(hit);
            return attacker != null
                    && fighting(attacker)
                    && mounts.mayDamage(event.getEntity(), attacker);
        }
        return true;
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player owner = mounts.owner(event.getEntity());
        if (owner != null) {
            if (!validMountDamage(event, owner)) event.setCancelled(true);
        } else if (!(event.getEntity() instanceof Player p) || !validDamage(event, p))
            event.setCancelled(true);
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent hit && bombBlast(hit)) {
            Entity direct = event.getDamageSource().getDirectEntity();
            event.setDamage(bombs.blastDamage(bombs.owner(direct) != null ? direct : hit.getDamager(), event.getDamage()));
        }
        Player owner = mounts.owner(event.getEntity());
        if (owner != null) {
            if (!validMountDamage(event, owner)) {
                event.setCancelled(true);
                return;
            }
            if (harpoonDamage(event)) return;
            if (event instanceof EntityDamageByEntityEvent hit
                    && hit.getDamager() instanceof Player attacker) {
                if (kits.get(attacker.getUniqueId()) == PvpClass.SHADOW
                        && PvpClass.shadowWand(attacker.getInventory().getItemInMainHand())) {
                    event.setDamage(0);
                    return;
                }
            }
            if (event.getFinalDamage() >= ((LivingEntity) event.getEntity()).getHealth()) {
                event.setCancelled(true);
                mounts.remove(owner.getUniqueId());
                owner.sendMessage(
                        text("pvp.horse_lost", NamedTextColor.YELLOW));
            }
            return;
        }
        if (!(event.getEntity() instanceof Player p)) return;
        if (sumo) {
            event.setCancelled(true);
            if (!fighting(p)) return;
            if (arena.isOut(p.getLocation())
                    || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                ringOut(p);
                return;
            }
            if (event instanceof EntityDamageByEntityEvent hit
                    && hit.getDamager() instanceof Player attacker
                    && attacker != p
                    && fighting(attacker)
                    && (attacker.getInventory().getItemInMainHand().getType() == Material.AIR
                            || attacker.getInventory().getItemInMainHand().getType()
                                    == Material.STICK)
                    && sumoCombat.acceptHit(p.getUniqueId(), Bukkit.getCurrentTick())) {
                var away = p.getLocation().toVector().subtract(attacker.getLocation().toVector());
                if (away.clone().setY(0).lengthSquared() < 1.0e-6)
                    away = attacker.getLocation().getDirection();
                lastHit.put(
                        p.getUniqueId(), new Hit(attacker.getUniqueId(), Bukkit.getCurrentTick()));
                int level =
                        attacker.getInventory()
                                .getItemInMainHand()
                                .getEnchantmentLevel(Enchantment.KNOCKBACK);
                p.setVelocity(
                        SumoCombat.knockback(away, p.getVelocity(), attacker.isSprinting(), level));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, .5f, 1);
            }
            return;
        }
        if (!validDamage(event, p)) {
            event.setCancelled(true);
            return;
        }
        if (harpoonDamage(event)) return;
        if (event instanceof EntityDamageByEntityEvent hit
                && hit.getDamager() instanceof Player attacker
                && attacker != p) {
            if (kits.get(attacker.getUniqueId()) == PvpClass.SHADOW
                    && PvpClass.shadowWand(attacker.getInventory().getItemInMainHand())) {
                boolean landed = event.getFinalDamage() > 0;
                event.setDamage(0);
                if (combat.shadowStrike(attacker.getUniqueId(), now, landed)) {
                    lastHit.put(
                            p.getUniqueId(),
                            new Hit(attacker.getUniqueId(), Bukkit.getCurrentTick()));
                    p.addPotionEffect(
                            new PotionEffect(
                                    PotionEffectType.BLINDNESS,
                                    PvpCombat.SHADOW_BLINDNESS_TICKS,
                                    0));
                    p.addPotionEffect(
                            new PotionEffect(
                                    PotionEffectType.SLOWNESS, PvpCombat.SHADOW_SLOWNESS_TICKS, 1));
                    p.sendActionBar(
                            text("pvp.shadow_hit", NamedTextColor.DARK_PURPLE));
                    attacker.sendActionBar(
                            text("pvp.shadow_used", NamedTextColor.GOLD));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, .5f, .7f);
                } else if (landed)
                    attacker.sendActionBar(
                            text("pvp.shadow_cooldown", NamedTextColor.RED,
                                    combat.shadowRemaining(attacker.getUniqueId(), now)));
                return;
            }
        }
        if (event instanceof EntityDamageByEntityEvent damage && event.getFinalDamage() > 0) {
            Player attacker = source(damage);
            if (attacker != null && !attacker.getUniqueId().equals(p.getUniqueId()))
                lastHit.put(
                        p.getUniqueId(), new Hit(attacker.getUniqueId(), Bukkit.getCurrentTick()));
        }
        if (event instanceof EntityDamageByEntityEvent hit
                && hit.getDamager() instanceof Player attacker
                && attacker != p
                && event.getFinalDamage() > 0) {
            PvpClass kit = kits.get(attacker.getUniqueId());
            var facing = p.getLocation();
            facing.setPitch(0);
            if (kit == PvpClass.DUELIST
                    && attacker.getInventory().getItemInMainHand().getType() == kit.primaryWeapon()
                    && combat.backstab(
                            attacker.getUniqueId(),
                            now,
                            facing.getDirection(),
                            attacker.getLocation()
                                    .toVector()
                                    .subtract(p.getLocation().toVector()))) {
                event.setDamage(event.getDamage() + 2);
                attacker.sendActionBar(text("pvp.backstab", NamedTextColor.GOLD));
                attacker.playSound(
                        attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, .7f, 1.2f);
            }
            if (kit == PvpClass.THUNDERER
                    && !thunder.resolving(attacker)
                    && attacker.getInventory().getItemInMainHand().getType() == Material.IRON_AXE
                    && combat.staticStrike(attacker.getUniqueId(), now, true)) {
                p.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.GLOWING, PvpCombat.THUNDER_GLOW_TICKS, 0));
                p.getWorld()
                        .spawnParticle(
                                org.bukkit.Particle.ELECTRIC_SPARK,
                                p.getLocation().add(0, 1, 0),
                                8,
                                .2,
                                .3,
                                .2,
                                0);
                attacker.sendActionBar(text("pvp.static", NamedTextColor.AQUA));
            }
            if (kit == PvpClass.BERSERKER && combat.leech(attacker.getUniqueId(), now))
                PvpCombat.heal(attacker, 1);
        }
        // Lethal damage reaches vanilla: PlayerDeathEvent owns elimination and the death screen.
    }

    /** Returns true only for a duplicate spin hit; shield/armour calculation remains native. */
    private boolean harpoonDamage(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent hit)
                || !(hit.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target)
                || kits.get(attacker.getUniqueId()) != PvpClass.HARPOONER
                || !harpoon.active(attacker)) return false;
        if (!harpoon.accept(attacker, target)) {
            event.setCancelled(true);
            return true;
        }
        event.setDamage(PvpHarpoon.DAMAGE);
        if (event.getFinalDamage() > 0 && target instanceof Player victim)
            harpoon.landed(attacker, victim);
        return false;
    }

    private UUID recentKiller(Player victim) {
        Hit hit = lastHit.remove(victim.getUniqueId());
        return hit != null && Bukkit.getCurrentTick() - hit.tick() <= 8 * 20
                ? hit.attacker()
                : null;
    }

    private void announceDeath(Player victim, UUID killer, boolean ringOut) {
        services.messages()
                .broadcast(
                        text(killer == null
                                        ? ringOut ? "death.fell" : "death.died"
                                        : ringOut ? "death.pushed" : "death.killed",
                                NamedTextColor.RED,
                                killer == null ? new Object[] {name(victim.getUniqueId())}
                                        : new Object[] {name(killer), name(victim.getUniqueId())}));
        Player winner = killer == null ? null : player(killer);
        if (winner != null && !winner.isDead())
            winner.showTitle(
                    Title.title(
                            text(ringOut ? "death.you_pushed" : "death.you_killed", NamedTextColor.GOLD),
                            text(name(victim.getUniqueId()), NamedTextColor.WHITE),
                            Title.Times.times(
                                    Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(300))));
    }

    private void ringOut(Player player) {
        if (!fighting(player)) return;
        UUID killer = recentKiller(player);
        announceDeath(player, killer, true);
        player.showTitle(
                Title.title(
                        text("death.eliminated", NamedTextColor.RED),
                        killer == null
                                ? text("death.you_fell", NamedTextColor.WHITE)
                                : text("death.pushed_by", NamedTextColor.WHITE, name(killer)),
                        Title.Times.times(
                                Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(300))));
        eliminate(player);
    }

    private void eliminate(Player p) {
        if (!eliminated.add(p.getUniqueId())) return;
        deathPause.begin(p.getUniqueId(), Bukkit.getCurrentTick());
        if (ffa) standings.eliminate(p.getUniqueId(), Bukkit.getCurrentTick());
        mounts.remove(p.getUniqueId());
        harpoon.remove(p.getUniqueId());
        thunder.remove(p.getUniqueId());
        airstrike.remove(p.getUniqueId());
        shadowArmor.remove(p.getUniqueId());
        bombs.eliminate(p.getUniqueId());
        if (!p.isDead() && sumo) p.setGameMode(GameMode.SPECTATOR);
        if (ffa) {
            services.messages().broadcast(text("ffa.remaining", NamedTextColor.YELLOW, standings.survivors().size()));
            if (sumo) {
                spectator(p);
                scheduleFfaResolution();
                return;
            }
            UUID id = p.getUniqueId();
            BukkitTask previous = recoveries.remove(id);
            if (previous != null) previous.cancel();
            recoveries.put(
                    id,
                    Bukkit.getScheduler()
                            .runTaskLater(
                                    services.plugin(),
                                    () -> {
                                        recoveries.remove(id);
                                        if (phase != Phase.DONE
                                                && !context.ended()
                                                && services.session().isRunning()
                                                && p.isOnline()
                                                && context.eligible().contains(id)
                                                && (p.isDead() || p.getGameMode() != GameMode.SPECTATOR)) spectator(p);
                                    },
                                    DeathPause.TICKS));
            scheduleFfaResolution();
            return;
        }
        if (resolution == null)
            resolution =
                    Bukkit.getScheduler()
                            .runTask(
                                    services.plugin(),
                                    () -> {
                                        if (phase != Phase.FIGHT
                                                || !services.session().isRunning()) {
                                            resolution = null;
                                            return;
                                        }
                                        // One tick for simultaneous deaths, then three seconds to
                                        // read the outcome.
                                        phase = Phase.RESULT;
                                        harpoon.clear();
                                        thunder.clear();
                                        airstrike.clear();
                                        shadowArmor.clear();
                                        bombs.clear();
                                        UUID winner =
                                                training
                                                        ? null
                                                        : eliminated.contains(match.pair().first())
                                                                        && eliminated.contains(
                                                                                match.pair()
                                                                                        .second())
                                                                ? null
                                                                : eliminated.contains(
                                                                                match.pair()
                                                                                        .first())
                                                                        ? match.pair().second()
                                                                        : match.pair().first();
                                        updateHud();
                                        resolution =
                                                Bukkit.getScheduler()
                                                        .runTaskLater(
                                                                services.plugin(),
                                                                () -> {
                                                                    resolution = null;
                                                                    if (phase != Phase.RESULT
                                                                            || !services.session()
                                                                                    .isRunning())
                                                                        return;
                                                                    if (training) {
                                                                        phase = Phase.BREAK;
                                                                        deadline =
                                                                                now + intermission;
                                                                        spectator(p);
                                                                    } else finishRound(winner);
                                                                },
                                                                Math.max(
                                                                        1,
                                                                        deathPause.remaining(
                                                                                Bukkit
                                                                                        .getCurrentTick())));
                                    });
    }

    private void scheduleFfaResolution() {
        if (resolution != null || phase == Phase.DONE || phase == Phase.RESULT) return;
        resolution =
                Bukkit.getScheduler()
                        .runTaskLater(
                                services.plugin(),
                                () -> {
                                    resolution = null;
                                    if (phase == Phase.DONE
                                            || context.ended()
                                            || !services.session().isRunning()) return;
                                    for (UUID id : eliminated) projectiles.clear(id);
                                    boolean test = freeForAll ? training : services.isTest();
                                    if (!standings.decided(test, Bukkit.getCurrentTick())) {
                                        if (!test && standings.survivors().size() == 1)
                                            scheduleFfaResolution();
                                        return;
                                    }
                                    if (chooseKits)
                                        for (Player p : services.participants().onlinePlayers())
                                            kitMenu.close(p);
                                    phase = Phase.RESULT;
                                    ffaResultUntil =
                                            (long) Bukkit.getCurrentTick() + DeathPause.TICKS;
                                    harpoon.clear();
                                    thunder.clear();
                                    airstrike.clear();
                                    shadowArmor.clear();
                                    bombs.clear();
                                    updateHud();
                                    resolution =
                                            Bukkit.getScheduler()
                                                    .runTaskLater(
                                                            services.plugin(),
                                                            () -> {
                                                                resolution = null;
                                                                if (phase == Phase.RESULT
                                                                        && !context.ended()
                                                                        && services.session()
                                                                                .isRunning())
                                                                    finishFfa();
                                                            },
                                                            DeathPause.TICKS);
                                },
                                2);
    }

    private void finishFfa() {
        if (context.ended()) return;
        if (freeForAll) {
            List<UUID> alive = standings.survivors();
            champion = alive.size() == 1 ? alive.getFirst() : null;
            List<Component> lines = new ArrayList<>();
            lines.add(text(champion == null ? "ffa.draw" : "ffa.winner", NamedTextColor.GOLD, champion == null ? new Object[0] : new Object[] {name(champion)}));
            int place = 0;
            for (UUID id : standings.ranking(names.keySet(), Bukkit.getCurrentTick()))
                lines.add(text(id.equals(champion) ? "ffa.standing_winner" : "ffa.standing_out", NamedTextColor.GRAY, ++place, name(id)));
            result = List.copyOf(lines);
            context.finish(EndReason.FINISHED, roundResult());
            return;
        }
        PartyRoundResult ranking = roundResult();
        result =
                ranking.places().stream()
                        .flatMap(List::stream)
                        .map(id ->
                                (Component)
                                        text("tournament.ffa_standing", NamedTextColor.GOLD,
                                                ranking.place(id), name(id), ranking.awards().get(id)))
                        .toList();
        context.finish(EndReason.FINISHED, ranking);
    }

    @Override
    public PartyRoundResult roundResult() {
        return standings == null
                ? PartyRoundResult.empty()
                : standings.result(context.eligible(), Bukkit.getCurrentTick());
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        if (!fighter(p) || eliminated.contains(p.getUniqueId())) return;
        UUID killer = recentKiller(p);
        event.deathMessage(null);
        event.deathScreenMessageOverride(
                I18n.render(killer == null ? text("death.you_died", NamedTextColor.RED)
                        : text("death.killed_by", NamedTextColor.RED, name(killer)), p));
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        announceDeath(p, killer, false);
        eliminate(p);
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (phase != Phase.DONE && eliminated.contains(event.getPlayer().getUniqueId()))
            event.setRespawnLocation(arena.spectator());
    }

    @Override
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        // Clicking Respawn early never puts the defeated player back into the current round.
        if (phase != Phase.DONE && eliminated.contains(event.getPlayer().getUniqueId()))
            spectator(event.getPlayer());
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        if (!fighting(event.getPlayer()) || !arena.isPlaced(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        arena.broken(event.getBlock());
        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!fighting(event.getPlayer())
                || !arena.canPlace(event.getBlock())
                || event.getBlock().getType() != Material.WHITE_WOOL
                || !event.getBlockReplacedState().getType().isAir()) {
            event.setCancelled(true);
            return;
        }
        arena.track(event.getBlock());
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
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (phase == Phase.SELECTING && fighter(event.getPlayer())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND
                    && event.getAction().isRightClick()
                    && event.getItem() != null
                    && event.getItem().getType() == Material.NETHER_STAR)
                kitMenu.open(
                        event.getPlayer(), kitChoices.selected(event.getPlayer().getUniqueId()));
            return;
        }
        if (sumo || !fighting(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (kits.get(event.getPlayer().getUniqueId()) == PvpClass.DEMOLITIONIST
                && PvpBombs.isBomb(event.getItem())) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) return;
            if (event.getAction().isLeftClick()) throwBomb(event.getPlayer());
            else if (event.getAction().isRightClick()) placeBomb(event);
            return;
        }
        // Block events protect the arena. Denying the clicked block here also
        // prevents vanilla wool placement and mining player-placed blocks.
        if (event.getHand() != EquipmentSlot.HAND
                || !event.getAction().isRightClick()
                || event.getItem() == null
                || !event.getItem()
                        .getItemMeta()
                        .getPersistentDataContainer()
                        .has(abilityKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        long remaining = readyAt.getOrDefault(p.getUniqueId(), 0L) - now;
        if (remaining > 0) {
            p.sendActionBar(text("ability.cooldown", NamedTextColor.RED, remaining));
            return;
        }
        PvpClass kit = kits.get(p.getUniqueId());
        List<Player> opponents =
                fighters.stream()
                        .filter(id -> !id.equals(p.getUniqueId()))
                        .map(this::player)
                        .filter(other -> other != null && fighting(other))
                        .toList();
        Player opponent = PartyTargeting.nearestVisible(p, opponents, kit == PvpClass.PYROMANCER ? 8 : 6);
        if (kit == PvpClass.THUNDERER) {
            PvpThunder.Aim aim = PvpThunder.aim(p);
            if (aim == null || !arena.contains(aim.point())) {
                p.sendActionBar(
                        text("ability.thunder_aim", NamedTextColor.YELLOW));
                return;
            }
            if (!thunder.start(
                    p, aim.point(), services.plugin(), this::fighting, () -> thunderTargets(p)))
                return;
        } else if (kit == PvpClass.DEMOLITIONIST) {
            PvpThunder.Aim aim = PvpThunder.aim(p);
            if (aim == null || !PvpAirstrike.validTarget(aim.point(), arena::contains)) {
                p.sendActionBar(text("ability.airstrike_aim", NamedTextColor.YELLOW));
                return;
            }
            if (!airstrike.start(p, aim.point(), services.plugin(), this::fighting, arena::contains, bombs)) return;
            int added = PvpBombs.replenish(p);
            p.sendActionBar(text("ability.airstrike_used", NamedTextColor.GOLD, added));
        } else if (kit == PvpClass.HARPOONER) {
            if (!harpoon.start(p, services.plugin(), this::fighting)) {
                p.sendActionBar(
                        text("ability.trident_required", NamedTextColor.YELLOW));
                return;
            }
        } else if (!(kit == PvpClass.LANCER && mounts.boost(p, now)) && !kit.activate(p, opponent))
            return;
        if (kit == PvpClass.PYROMANCER && opponent != null)
            lastHit.put(opponent.getUniqueId(), new Hit(p.getUniqueId(), Bukkit.getCurrentTick()));
        readyAt.put(p.getUniqueId(), now + kit.cooldown);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1.5f);
        services.messages()
                .broadcast(text("ability.used", NamedTextColor.YELLOW, p.getName(), kit.ability));
    }

    @Override
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (sumo
                || kits.get(player.getUniqueId()) != PvpClass.DEMOLITIONIST
                || !PvpBombs.isBomb(player.getInventory().getItemInMainHand())) return;
        // Works even when vanilla would reject the punch (e.g. invulnerability frames).
        event.setCancelled(true);
        if (fighting(player)) throwBomb(player);
    }

    private void throwBomb(Player player) {
        Location eye = player.getEyeLocation();
        // Avoid creating a grenade on the far side of cover when standing against a wall.
        var obstruction =
                world.rayTraceBlocks(eye, eye.getDirection(), .8, FluidCollisionMode.NEVER, true);
        Location at = obstruction == null ? eye.clone().add(eye.getDirection().multiply(.5)) : eye;
        if (arena.contains(at)) bombs.use(player, at, true, services.plugin());
    }

    private void placeBomb(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getClickedBlock() == null) {
            player.sendActionBar(
                    text("bomb.controls", NamedTextColor.YELLOW));
            return;
        }
        var block = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!arena.canPlace(block)
                || !block.getType().isAir()
                || event.getClickedBlock().getLocation().distanceSquared(player.getEyeLocation())
                        > 36) {
            player.sendActionBar(text("bomb.cannot_place", NamedTextColor.RED));
            return;
        }
        bombs.use(player, block.getLocation().add(.5, 0, .5), false, services.plugin());
    }

    private List<LivingEntity> thunderTargets(Player caster) {
        List<LivingEntity> targets = new ArrayList<>();
        for (UUID id : fighters) {
            Player opponent = player(id);
            if (opponent == null || opponent.equals(caster) || !fighting(opponent)) continue;
            // Put the mount first: killing the rider removes their horse during damage dispatch.
            var horse = mounts.horse(id);
            if (horse != null) targets.add(horse);
            targets.add(opponent);
        }
        return targets;
    }

    @Override
    public void onItemHeld(PlayerItemHeldEvent event) {
        thunder.clearPreview(event.getPlayer().getUniqueId());
        airstrike.clearPreview(event.getPlayer().getUniqueId());
    }

    @Override
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        thunder.clearPreview(event.getPlayer().getUniqueId());
        airstrike.clearPreview(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (internalTeleport || countdownFreeze.hold(event) || !fighter(event.getPlayer())) return;
        if (sumo) {
            if (phase == Phase.FIGHT && arena.isOut(event.getTo())) ringOut(event.getPlayer());
        }
        // PvP's physical barrier wall handles collisions without per-packet position corrections.
    }

    @Override
    public void onPlayerJump(PlayerJumpEvent event) {
        countdownFreeze.onJump(event);
    }

    @Override
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (internalTeleport || !fighter(event.getPlayer()) || eliminated.contains(event.getPlayer().getUniqueId())) return;
        if (sumo
                || !fighting(event.getPlayer())
                || event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || !arena.contains(event.getTo())) event.setCancelled(true);
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (sumo || !(event.getEntity() instanceof Player p) || !fighting(p)) {
            event.setCancelled(true);
            return;
        }
        if (!event.isCancelled()) {
            if (event.getProjectile() instanceof AbstractArrow arrow)
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            projectiles.track(p.getUniqueId(), event.getProjectile(), now * 20);
        }
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) return;
        if (sumo || !fighting(shooter) || harpoon.active(shooter)) {
            event.setCancelled(true);
            return;
        }
        // Bows are tracked by onEntityShootBow; also clean up potions and pearls between rounds.
        if (!(event.getEntity() instanceof AbstractArrow) || event.getEntity() instanceof Trident)
            projectiles.track(shooter.getUniqueId(), event.getEntity(), now * 20);
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        if (sumo
                || !(event.getEntity() instanceof Snowball)
                || !(event.getEntity().getShooter() instanceof Player caster)
                || !fighting(caster)
                || kits.get(caster.getUniqueId()) != PvpClass.CRYOMANCER) return;
        Entity target = event.getHitEntity();
        Player player = target instanceof Player p ? p : mounts.owner(target);
        if (player == null
                || caster == player
                || !fighting(player)
                || !(target instanceof LivingEntity living)) return;
        if (combat.frost(caster.getUniqueId(), Bukkit.getCurrentTick())) {
            PvpCombat.applyFrost(living);
            if (player.getVehicle() instanceof LivingEntity horse && mounts.owner(horse) == player && horse != living)
                PvpCombat.applyFrost(horse);
            caster.playSound(caster.getLocation(), Sound.BLOCK_GLASS_BREAK, .4f, 1.5f);
        }
    }

    @Override
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player caster)
                || sumo
                || !fighting(caster)) {
            event.setCancelled(true);
            return;
        }
        PvpPotion potion = PvpPotion.from(event.getPotion().getItem());
        if (potion == null) {
            event.setCancelled(true);
            return;
        }
        for (var entity : new ArrayList<>(event.getAffectedEntities())) {
            Player owner = entity instanceof Player player ? player : mounts.owner(entity);
            boolean self = owner != null && owner.getUniqueId().equals(caster.getUniqueId());
            boolean active =
                    owner != null
                            && fighting(owner)
                            && (entity instanceof Player || potion.harmful);
            if (!potion.affects(self, active)) event.setIntensity(entity, 0);
        }
    }

    @Override
    public void onEntityMount(EntityMountEvent event) {
        if (mounts.owner(event.getMount()) != null
                && (!(event.getEntity() instanceof Player player)
                        || !fighting(player)
                        || !mounts.mayRide(player, event.getMount()))) event.setCancelled(true);
    }

    @Override
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (mounts.owner(event.getRightClicked()) == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.HAND
                && fighting(player)
                && !player.isSneaking()
                && !player.isInsideVehicle()
                && mounts.mayRide(player, event.getRightClicked()))
            event.getRightClicked().addPassenger(player);
    }

    @Override
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof Entity entity
                && mounts.owner(entity) != null) event.setCancelled(true);
    }

    @Override
    public void onEntityDeath(EntityDeathEvent event) {
        Player owner = mounts.owner(event.getEntity());
        if (owner == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        mounts.remove(owner.getUniqueId());
    }

    @Override
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (sumo || !(event.getEntity() instanceof Player player) || !fighting(player))
            event.setCancelled(true);
    }

    @Override
    public void onDropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onPickupItem(EntityPickupItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onCraftItem(CraftItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        if (chooseKits && kitMenu.owns(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (phase != Phase.SELECTING
                    || !(event.getWhoClicked() instanceof Player p)
                    || !fighter(p)) return;
            PvpClass kit = kitMenu.choice(event.getView().getTopInventory(), p, event.getRawSlot());
            if (kit != null)
                Bukkit.getScheduler()
                        .runTask(
                                services.plugin(),
                                () -> {
                                    if (phase != Phase.SELECTING
                                            || !fighter(p)
                                            || !p.isOnline()
                                            || context.ended()) return;
                                    if (!kitChoices.choose(p.getUniqueId(), kit)) return;
                                    kitMenu.close(p);
                                    p.sendMessage(
                                            text(
                                                    "pvp.choice.selected",
                                                    NamedTextColor.GREEN,
                                                    kit.title));
                                    p.playSound(
                                            p.getLocation(),
                                            Sound.BLOCK_NOTE_BLOCK_PLING,
                                            .7f,
                                            1.5f);
                                    if (kitChoices.ready()) startSelectedCountdown();
                                    else updateHud();
                                });
            return;
        }
        if (event.getSlotType() == InventoryType.SlotType.CRAFTING
                || event.getSlotType() == InventoryType.SlotType.RESULT
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ALL_SLOT
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ONE_SLOT
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ALL_CURSOR
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ONE_CURSOR)
            event.setCancelled(true);
    }

    @Override
    public void onInventoryDrag(InventoryDragEvent event) {
        if (chooseKits && kitMenu.owns(event.getView().getTopInventory())) event.setCancelled(true);
    }
}
