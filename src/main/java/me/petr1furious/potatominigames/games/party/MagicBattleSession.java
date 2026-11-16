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

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Spell-first SkyWars-style survival on an imported map; standalone or a Party Games round. */
final class MagicBattleSession implements PartyRoundSession {
    private enum Phase {
        BUILDING,
        SELECTING,
        COUNTDOWN,
        FIGHT,
        RESULT,
        DONE
    }

    private record Hit(UUID caster, long tick) {}

    private final SessionServices services;
    private final PartyRoundContext context;
    private final PartyConfig config;
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Set<UUID> connected = new LinkedHashSet<>();
    private final Map<UUID, MagicMana> mana = new HashMap<>();
    private final Map<UUID, Hit> lastHit = new HashMap<>();
    private final Map<UUID, Long> recoveries = new HashMap<>();
    private final Map<UUID, String> languages = new HashMap<>();
    private final Map<MagicBattleArena.Pos, MagicChestLoot> chests = new HashMap<>();
    private final MagicMenus menus = new MagicMenus();
    private final java.util.Random random = new java.util.Random();
    private MagicStarterChoices starters;
    private final CountdownFreeze freeze = new CountdownFreeze();
    private SurvivalStandings standings;
    private MagicBattleArena arena;
    private MagicCombat magic;
    private BossBar bar;
    private BukkitTask ticker;
    private Phase phase = Phase.BUILDING;
    private long tick, deadline, fightAt;
    private int duration, countdown, chestRecharge;
    private boolean overtime, internalTeleport, spellDamage;
    private UUID champion;
    private List<Component> result = List.of();

    MagicBattleSession(SessionServices services, PartyConfig config) {
        this(services, config, PartyRoundContext.standalone(services));
    }

    MagicBattleSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        this.services = context.services();
        this.config = config;
        this.context = context;
    }

    private Component name(UUID id) {
        return names.containsKey(id)
                ? Component.text(names.get(id))
                : I18n.tr("party.magic.player");
    }

    private Player player(UUID id) {
        return connected.contains(id) ? Bukkit.getPlayer(id) : null;
    }

    private boolean alive(Player p) {
        return standings != null
                && connected.contains(p.getUniqueId())
                && standings.alive(p.getUniqueId())
                && !p.isDead();
    }

    private boolean fighting(Player p) {
        return phase == Phase.FIGHT
                && alive(p)
                && p.getWorld().equals(arena.world())
                && p.getGameMode() == GameMode.SURVIVAL;
    }

    private List<Player> survivors() {
        return standings.survivors().stream()
                .map(this::player)
                .filter(p -> p != null && alive(p))
                .toList();
    }

    private void broadcast(Component message) {
        services.messages().broadcast(message.colorIfAbsent(NamedTextColor.GOLD));
    }

    @Override
    public void onStart() throws CommandFailure {
        try {
            start();
        } catch (CommandFailure | RuntimeException error) {
            cleanup();
            throw error;
        }
    }

    private void start() throws CommandFailure {
        for (var p : services.participants().all()) {
            names.put(p.id(), p.name());
            connected.add(p.id());
            mana.put(p.id(), new MagicMana());
        }
        if (names.isEmpty() || names.size() > 12)
            throw new CommandFailure(I18n.tr("party.magic.players"));
        standings = new SurvivalStandings(names.keySet());
        starters = new MagicStarterChoices(names.keySet());
        duration =
                (context.embedded()
                                ? Math.min(120, config.magicSeconds.get())
                                : config.magicSeconds.get())
                        * 20;
        countdown =
                (context.embedded()
                                ? context.options().countdownSeconds()
                                : config.magicCountdown.get())
                        * 20;
        chestRecharge = config.magicChestSeconds.get() * 20;
        var world = context.createVoid("party_magic");
        services.arena().applyStandardGameRules(world);
        world.setViewDistance(6);
        world.setSimulationDistance(4);
        world.setTime(6000);
        world.setStorm(false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        arena = new MagicBattleArena(world);
        services.arena().applyBorder(world, arena.center(), 190, 190, 0);
        magic =
                new MagicCombat(
                        arena,
                        this::fighting,
                        this::survivors,
                        mana,
                        this::spellHit,
                        this::teleport);
        bar =
                services.bossBars()
                        .create(
                                I18n.tr("party.magic.preparing"),
                                BossBar.Color.PURPLE,
                                BossBar.Overlay.PROGRESS);
        for (Player p : services.participants().onlinePlayers()) {
            if (p.isDead()) p.spigot().respawn();
            p.closeInventory();
            p.setItemOnCursor(null);
            services.playerState().resetForRound(p, GameMode.SPECTATOR);
            teleport(p, arena.spectator());
            freeze.freeze(p);
            p.showBossBar(bar);
        }
        broadcast(I18n.tr("party.magic.rules"));
        broadcast(I18n.tr("party.magic.controls"));
        broadcast(I18n.tr("party.magic.map_credit"));
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
                                                        "Magic battle round failed",
                                                        failure);
                                        context.finish(EndReason.ABORTED, PartyRoundResult.empty());
                                    }
                                },
                                1,
                                1);
        hud();
    }

    private boolean teleport(Player p, Location at) {
        internalTeleport = true;
        try {
            return p.teleport(at);
        } finally {
            internalTeleport = false;
        }
    }

    private void equip(Player p, int spawn) {
        freeze.release(p.getUniqueId());
        if (p.isDead()) p.spigot().respawn();
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.closeInventory();
        p.setItemOnCursor(null);
        services.playerState().resetForRound(p, GameMode.SURVIVAL);
        p.setAbsorptionAmount(0);
        p.setHealth(20);
        p.getInventory().setItem(4, MagicItems.opener(p));
        languages.put(p.getUniqueId(), I18n.language(p.locale()));
        p.getInventory().setItem(6, new ItemStack(Material.WHITE_WOOL, 64));
        p.getInventory().setHeldItemSlot(0);
        teleport(p, arena.spawn(spawn));
        freeze.freeze(p);
    }

    private void refreshItems(Player p) {
        var inventory = p.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            MagicWand wand = MagicItems.wand(inventory.getItem(slot));
            if (wand != null) inventory.setItem(slot, MagicItems.wand(wand, p));
            else if (MagicItems.opener(inventory.getItem(slot))) inventory.setItem(slot, MagicItems.opener(p));
        }
        languages.put(p.getUniqueId(), I18n.language(p.locale()));
        var top = p.getOpenInventory().getTopInventory();
        if (menus.starter(top) && phase == Phase.SELECTING) menus.openStarter(p, starters.selected(p.getUniqueId()));
        else if (menus.owns(top) && menus.chest(top) != null) {
            var pos = menus.chest(top);
            if (chests.containsKey(pos)) menus.openLoot(p, pos, chests.get(pos), tick);
        }
    }

    private void finishSelection() {
        var choices = starters.finish();
        phase = Phase.COUNTDOWN;
        deadline = tick + countdown;
        for (Player p : survivors()) {
            menus.close(p);
            p.setItemOnCursor(null);
            p.getInventory().clear();
            List<MagicSpell> spells = choices.getOrDefault(p.getUniqueId(), List.of());
            for (int slot = 0; slot < spells.size(); slot++)
                p.getInventory().setItem(slot, MagicItems.wand(MagicWand.single(spells.get(slot)), p));
            p.getInventory().setItem(6, new ItemStack(Material.WHITE_WOOL, 64));
            p.getInventory().setHeldItemSlot(0);
        }
        hud();
    }

    private void tickGame() {
        if (phase == Phase.DONE || context.ended() || !services.session().isRunning()) return;
        tick++;
        if (connected.isEmpty()) {
            context.finish(EndReason.ALL_PLAYERS_LEFT, PartyRoundResult.empty());
            return;
        }
        if (phase == Phase.BUILDING) {
            if (arena.buildSlice(2048)) {
                List<UUID> order = new ArrayList<>(names.keySet());
                Collections.shuffle(order);
                List<Integer> spawns = new ArrayList<>();
                for (int i = 0; i < 12; i++) spawns.add(i);
                Collections.shuffle(spawns);
                for (int i = 0; i < order.size(); i++) {
                    Player p = player(order.get(i));
                    if (p != null && alive(p)) equip(p, spawns.get(i));
                }
                phase = Phase.SELECTING;
                deadline = tick + MagicStarterChoices.SECONDS * 20L;
                for (Player p : survivors()) menus.openStarter(p, starters.selected(p.getUniqueId()));
            }
        } else if (phase == Phase.SELECTING) {
            if (starters.ready() || tick >= deadline) finishSelection();
        } else if (phase == Phase.COUNTDOWN && tick >= deadline) {
            freeze.releaseAll();
            phase = Phase.FIGHT;
            fightAt = tick;
            broadcast(I18n.tr("party.magic.fight"));
        } else if (phase == Phase.FIGHT) {
            for (Player p : survivors()) {
                if (!p.getWorld().equals(arena.world())) {
                    out(p);
                    continue;
                }
                if (p.getLocation().getY() < 25) {
                    p.damage(1000, DamageSource.builder(DamageType.OUT_OF_WORLD).build());
                    if (standings.alive(p.getUniqueId())) out(p);
                    continue;
                }
                mana.get(p.getUniqueId()).tick();
                if (tick % 20 == 0
                        && Math.hypot(p.getLocation().getX() - .5, p.getLocation().getZ() - .5) < 6
                        && Math.abs(p.getLocation().getY() - 64) < 12)
                    mana.get(p.getUniqueId()).restore(5);
            }
            magic.tick(tick);
            if (!overtime && tick - fightAt >= duration) {
                overtime = true;
                services.arena().applyBorder(arena.world(), arena.center(), 190, 12, 30);
                broadcast(I18n.tr("party.magic.overtime"));
            }
            if (overtime && tick - fightAt >= duration + 600 && tick % 20 == 0)
                for (Player p : survivors())
                    p.damage(2 + (tick - fightAt - duration - 600) / 100.0);
            if (standings.decided(services.isTest(), tick)) finishBattle();
            if (tick % 10 == 0) for (Player p : survivors()) preview(p);
            if (tick % 20 == 0)
                for (var pos : arena.chests())
                    if (!chests.containsKey(pos) || chests.get(pos).refillAt <= tick)
                        arena.world()
                                .spawnParticle(
                                        Particle.ENCHANT,
                                        new Location(
                                                arena.world(),
                                                pos.x() + .5,
                                                pos.y() + 1,
                                                pos.z() + .5),
                                        1,
                                        .2,
                                        .1,
                                        .2,
                                        0);
        } else if (phase == Phase.RESULT && tick >= deadline) {
            context.finish(EndReason.FINISHED, roundResult());
            return;
        }
        for (UUID id : List.copyOf(recoveries.keySet()))
            if (tick >= recoveries.get(id)) {
                Player p = player(id);
                if (p != null) {
                    if (p.isDead()) p.spigot().respawn();
                    if (p.getGameMode() != GameMode.SPECTATOR) spectate(p);
                }
                recoveries.remove(id);
            }
        if (tick % 5 == 0)
            for (Player p : survivors()) {
                if (!I18n.language(p.locale()).equals(languages.get(p.getUniqueId())))
                    refreshItems(p);
                actionBar(p);
            }
        if (tick % 20 == 0 || phase == Phase.BUILDING) hud();
    }

    private void preview(Player p) {
        MagicWand wand = MagicItems.wand(p.getInventory().getItemInMainHand());
        if (wand == null) return;
        // Sneaking previews the left-click spell without changing the wand's contents.
        MagicSpell spell = p.isSneaking() && wand.second() != null ? wand.second() : wand.first();
        if (!spell.aimed()) return;
        var aim = MagicCombat.aim(p, spell.range());
        if (arena.inside(aim.point())) MagicCombat.ring(aim.point(), spell.radius(),
                mana.get(p.getUniqueId()).ready(spell, tick) ? spell.school.color : 0xEF4242, p);
    }

    private void actionBar(Player p) {
        MagicMana state = mana.get(p.getUniqueId());
        if (state == null) return;
        if (magic != null && magic.channelRemaining(p, tick) > 0) {
            p.sendActionBar(
                    I18n.tr("party.magic.channeling", (magic.channelRemaining(p, tick) + 19) / 20));
            return;
        }
        if (magic != null && magic.silenceRemaining(p, tick) > 0) {
            p.sendActionBar(
                    I18n.tr("party.magic.silenced", (magic.silenceRemaining(p, tick) + 19) / 20));
            return;
        }
        MagicWand wand = MagicItems.wand(p.getInventory().getItemInMainHand());
        if (wand != null && wand.enchanted()) {
            p.sendActionBar(I18n.tr("party.magic.action_double", (int) state.mana(),
                    wand.first().title(), (state.remaining(wand.first(), tick) + 19) / 20,
                    wand.second().title(), (state.remaining(wand.second(), tick) + 19) / 20)
                    .color(NamedTextColor.AQUA));
        } else {
            p.sendActionBar(I18n.tr("party.magic.action", (int) state.mana(),
                    wand == null ? I18n.tr("party.magic.select_wand") : wand.first().title(),
                    wand == null ? 0 : (state.remaining(wand.first(), tick) + 19) / 20)
                    .color(NamedTextColor.AQUA));
        }
    }

    private void hud() {
        if (bar == null || phase == Phase.DONE) return;
        Component status =
                switch (phase) {
                    case BUILDING -> I18n.tr("party.magic.preparing");
                    case SELECTING -> I18n.tr("party.magic.starter.waiting", starters.confirmed(),
                            starters.size(), Math.max(0, (deadline - tick + 19) / 20));
                    case COUNTDOWN ->
                            I18n.tr(
                                    "party.magic.countdown",
                                    Math.max(0, (deadline - tick + 19) / 20));
                    case RESULT -> I18n.tr("party.magic.results");
                    default ->
                            overtime
                                    ? I18n.tr("party.magic.overtime_short")
                                    : I18n.tr(
                                            "party.magic.timer",
                                            TimeFormat.clock(
                                                    Math.max(
                                                            0,
                                                            (duration - (tick - fightAt)) / 20)));
                };
        bar.name(status);
        bar.progress(
                phase == Phase.BUILDING
                        ? arena.progress()
                        : (float) standings.survivors().size() / names.size());
        for (Player p : services.session().audiencePlayers()) {
            p.showBossBar(bar);
            List<Component> lines = new ArrayList<>();
            lines.add(status.color(NamedTextColor.YELLOW));
            lines.add(
                    I18n.tr("party.magic.remaining", standings.survivors().size(), names.size())
                            .color(NamedTextColor.WHITE));
            MagicMana state = mana.get(p.getUniqueId());
            if (state != null)
                lines.add(
                        I18n.tr("party.magic.mana", (int) state.mana(), 100)
                                .color(NamedTextColor.AQUA));
            lines.add(I18n.tr("party.magic.fountain").color(NamedTextColor.LIGHT_PURPLE));
            services.sidebar().update(p, I18n.tr("party.magic.title"), lines);
        }
    }

    private void cast(Player p, boolean leftClick) {
        if (!fighting(p)) return;
        MagicWand wand = MagicItems.wand(p.getInventory().getItemInMainHand());
        MagicSpell spell = wand == null ? null : wand.cast(leftClick);
        if (spell == null) return;
        if (magic.silenceRemaining(p, tick) > 0 || magic.channelRemaining(p, tick) > 0) {
            actionBar(p);
            return;
        }
        MagicMana state = mana.get(p.getUniqueId());
        if (!state.ready(spell, tick)) {
            p.sendActionBar(I18n.tr("party.magic.not_ready", spell.mana,
                    (state.remaining(spell, tick) + 19) / 20));
            return;
        }
        if (magic.cast(p, spell, tick)) {
            state.spend(spell, tick);
            actionBar(p);
        } else p.sendActionBar(I18n.tr("party.magic.invalid_target"));
    }

    private void openChest(Player p, MagicBattleArena.Pos pos) {
        MagicChestLoot loot = chests.get(pos);
        if (loot == null || tick >= loot.refillAt) {
            loot = new MagicChestLoot(random, tick + chestRecharge);
            chests.put(pos, loot);
        }
        menus.openLoot(p, pos, loot, tick);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, .5f, 1);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) { event.setCancelled(true); return; }
        if (phase == Phase.SELECTING && alive(p) && MagicItems.opener(event.getItem())
                && event.getAction().isRightClick()) {
            event.setCancelled(true);
            menus.openStarter(p, starters.selected(p.getUniqueId()));
            return;
        }
        if (fighting(p) && event.getAction().isRightClick() && !p.isSneaking()
                && event.getClickedBlock() != null
                && arena.chests().contains(MagicBattleArena.Pos.of(event.getClickedBlock()))
                && event.getClickedBlock().getType() == Material.CHEST) {
            event.setCancelled(true);
            openChest(p, MagicBattleArena.Pos.of(event.getClickedBlock()));
            return;
        }
        if (fighting(p) && event.getClickedBlock() != null) {
            boolean placing = event.getAction().isRightClick() && event.getItem() != null
                    && event.getItem().getType() == Material.WHITE_WOOL;
            boolean mining = event.getAction().isLeftClick() && MagicItems.school(event.getItem()) == null
                    && arena.breakable(event.getClickedBlock());
            if (placing || mining) { event.setCancelled(false); return; }
        }
        event.setCancelled(true);
        if (alive(p)) {
            if (event.getAction().isRightClick()) cast(p, false);
            else if (event.getAction().isLeftClick()) cast(p, true);
        }
    }

    private boolean spellHit(Player caster, Player target, double amount) {
        if (!fighting(caster) || !fighting(target)) return false;
        double before = target.getHealth() + target.getAbsorptionAmount();
        spellDamage = true;
        try {
            target.damage(
                    amount,
                    DamageSource.builder(DamageType.INDIRECT_MAGIC)
                            .withCausingEntity(caster)
                            .withDirectEntity(caster)
                            .build());
        } finally {
            spellDamage = false;
        }
        boolean damaged = target.getHealth() + target.getAbsorptionAmount() < before;
        if (damaged && caster != target)
            caster.playSound(caster.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, .6f, 1.2f);
        return damaged;
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !fighting(p)) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent hit) {
            if (!spellDamage || !(hit.getDamager() instanceof Player caster) || !fighting(caster)) {
                event.setCancelled(true);
                return;
            }
            if (caster != p && event.getDamage() > 0)
                lastHit.put(p.getUniqueId(), new Hit(caster.getUniqueId(), tick));
        }
        if (magic.shielded(p, tick)) event.setDamage(event.getDamage() * .5);
        if (event.getDamage() > 0) {
            magic.interrupt(p);
            if (event.getFinalDamage() > 0
                    && event.getCause() != EntityDamageEvent.DamageCause.VOID
                    && magic.preventDeath(p, event.getFinalDamage(), tick))
                event.setCancelled(true);
        }
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player p)
                || !fighting(p)
                || !spellDamage
                || !(event.getDamager() instanceof Player caster)
                || !fighting(caster)) event.setCancelled(true);
    }

    @Override
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        event.setCancelled(true);
        if (alive(event.getPlayer())) cast(event.getPlayer(), true);
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        if (!names.containsKey(p.getUniqueId()) || !standings.alive(p.getUniqueId())) return;
        Hit hit = lastHit.get(p.getUniqueId());
        UUID killer = hit != null && tick - hit.tick() <= 200 ? hit.caster() : null;
        event.deathMessage(null);
        event.getDrops().clear();
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        Component message =
                killer == null
                        ? I18n.tr("party.magic.death")
                        : I18n.tr("party.magic.killed_by", name(killer));
        event.deathScreenMessageOverride(I18n.render(message, p));
        broadcast(
                killer == null
                        ? I18n.tr("party.magic.died", p.getName())
                        : I18n.tr("party.magic.killed", name(killer), p.getName()));
        out(p);
    }

    private void out(Player p) {
        if (!standings.eliminate(p.getUniqueId(), tick)) return;
        magic.remove(p.getUniqueId());
        menus.close(p);
        freeze.release(p.getUniqueId());
        if (p.isDead()) recoveries.put(p.getUniqueId(), tick + DeathPause.TICKS);
        else spectate(p);
    }

    private void spectate(Player p) {
        if (p.isDead()) return;
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.closeInventory();
        p.setItemOnCursor(null);
        services.playerState().resetForRound(p, GameMode.SPECTATOR);
        p.setAbsorptionAmount(0);
        teleport(p, arena.spectator());
        if (bar != null) p.showBossBar(bar);
    }

    private void finishBattle() {
        phase = Phase.RESULT;
        deadline = tick + DeathPause.TICKS;
        magic.clear();
        freeze.releaseAll();
        champion = standings.survivors().size() == 1 ? standings.survivors().getFirst() : null;
        result = new ArrayList<>();
        result.add(
                champion == null
                        ? I18n.tr("party.magic.draw")
                        : I18n.tr("party.magic.winner", name(champion)));
        int place = 0;
        for (UUID id : standings.ranking(names.keySet(), tick))
            result.add(I18n.tr("party.magic.standing", ++place, name(id)));
        broadcast(result.getFirst());
        for (Player p : survivors()) {
            p.setFireTicks(0);
            freeze.freeze(p);
        }
        hud();
    }

    private void leave(Player p) {
        context.withdraw(p.getUniqueId());
        connected.remove(p.getUniqueId());
        if (starters != null) starters.withdraw(p.getUniqueId());
        if (standings != null) standings.eliminate(p.getUniqueId(), tick);
        if (magic != null) magic.remove(p.getUniqueId());
        freeze.release(p.getUniqueId());
        recoveries.remove(p.getUniqueId());
        p.closeInventory();
        p.setItemOnCursor(null);
    }

    @Override
    public void onParticipantQuit(Player p) {
        leave(p);
    }

    @Override
    public void onParticipantLeave(Player p) {
        leave(p);
    }

    @Override
    public void onParticipantRejoin(Player p) {
        connected.add(p.getUniqueId());
        spectate(p);
    }

    @Override
    public void onParticipantJoin(Player p) {
        spectate(p);
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (arena != null && !standings.alive(event.getPlayer().getUniqueId()))
            event.setRespawnLocation(arena.spectator());
    }

    @Override
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        if (!standings.alive(event.getPlayer().getUniqueId())) {
            recoveries.remove(event.getPlayer().getUniqueId());
            spectate(event.getPlayer());
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!internalTeleport) freeze.hold(event);
    }

    @Override
    public void onPlayerJump(PlayerJumpEvent event) {
        freeze.onJump(event);
    }

    @Override
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!internalTeleport
                && standings != null
                && standings.alive(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (fighting(event.getPlayer())
                && MagicItems.school(event.getPlayer().getInventory().getItemInMainHand()) == null)
            arena.remove(event.getBlock());
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!fighting(event.getPlayer())
                || event.getBlock().getType() != Material.WHITE_WOOL
                || !arena.canBuild(event.getBlock())
                || !event.getBlockReplacedState().getType().isAir()) event.setCancelled(true);
    }

    @Override
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (alive(event.getPlayer())) actionBar(event.getPlayer());
    }

    @Override
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onEntityExplode(EntityExplodeEvent event) {
        if (phase != Phase.FIGHT) event.blockList().clear();
        else event.blockList().removeIf(block -> !arena.breakable(block));
    }

    @Override
    public void onBlockExplode(BlockExplodeEvent event) {
        if (phase != Phase.FIGHT) event.blockList().clear();
        else event.blockList().removeIf(block -> !arena.breakable(block));
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onDropItem(PlayerDropItemEvent event) {
        event.setCancelled(!fighting(event.getPlayer()));
    }

    @Override
    public void onPickupItem(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        event.setCancelled(!(event.getEntity() instanceof Player p) || !fighting(p)
                || (MagicItems.wand(item) == null && item.getType() != Material.WHITE_WOOL));
    }

    @Override
    public void onCraftItem(CraftItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !alive(p)) return;
        var top = event.getView().getTopInventory();
        if (!menus.owns(top)) {
            if (fighting(p) && top.getType() == org.bukkit.event.inventory.InventoryType.CRAFTING)
                event.setCancelled(false);
            return;
        }
        if (!menus.viewer(top, p) || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()
                || (!event.isLeftClick() && !event.isRightClick()) || event.isShiftClick()) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(services.plugin(), () -> {
            if (!alive(p) || p.getOpenInventory().getTopInventory() != top) return;
            if (menus.starter(top) && phase == Phase.SELECTING) {
                if (slot == 22) {
                    if (starters.confirm(p.getUniqueId())) p.closeInventory();
                    else p.sendActionBar(I18n.tr("party.magic.starter.need_one"));
                } else {
                    MagicSpell spell = menus.starterChoice(slot);
                    if (spell == null) return;
                    if (!starters.toggle(p.getUniqueId(), spell))
                        p.sendActionBar(I18n.tr("party.magic.starter.limit"));
                    menus.openStarter(p, starters.selected(p.getUniqueId()));
                }
            } else if (fighting(p) && menus.chest(top) != null) {
                var pos = menus.chest(top);
                var loot = chests.get(pos);
                // Leaving the chest or losing access to its block invalidates an old view.
                Location at = new Location(arena.world(), pos.x() + .5, pos.y() + .5, pos.z() + .5);
                if (p.getEyeLocation().distanceSquared(at) > 64
                        || at.getBlock().getType() != Material.CHEST) { p.closeInventory(); return; }
                if (loot == null || loot != menus.loot(top)) { openChest(p, pos); return; }
                ItemStack reward = null;
                int index = slot == 11 ? 0 : slot == 15 ? 1 : -1;
                if (index >= 0 && loot.wand(index) != null) reward = MagicItems.wand(loot.wand(index), p);
                else if (slot == 13 && loot.wool()) reward = new ItemStack(Material.WHITE_WOOL, 16);
                if (reward == null) return;
                // Reserve an empty slot, so a full inventory never consumes any chest loot.
                if (p.getInventory().firstEmpty() < 0) {
                    p.sendActionBar(I18n.tr("party.magic.loot.full")); return;
                }
                p.getInventory().setItem(p.getInventory().firstEmpty(), reward);
                if (index >= 0) loot.takeWand(index); else loot.takeWool();
                for (Player viewer : survivors()) {
                    var view = viewer.getOpenInventory().getTopInventory();
                    if (menus.owns(view) && pos.equals(menus.chest(view))) menus.openLoot(viewer, pos, loot, tick);
                }
            }
        });
    }

    @Override
    public void onInventoryDrag(InventoryDragEvent event) {
        event.setCancelled(!(event.getWhoClicked() instanceof Player p) || !fighting(p)
                || event.getView().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING);
    }

    @Override
    public void onSecondTick(long seconds) {
        hud();
    }

    @Override
    public boolean isInGame(Player p) {
        return alive(p);
    }

    @Override
    public boolean mayFollowPlayers(Player p) {
        return phase != Phase.BUILDING && phase != Phase.SELECTING && phase != Phase.COUNTDOWN && !alive(p);
    }

    @Override
    public Location spectatorLocation(Player viewer) {
        return arena == null ? null : arena.spectator();
    }

    private void cleanup() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        if (magic != null) magic.clear();
        for (Player p : services.session().audiencePlayers()) menus.close(p);
        freeze.releaseAll();
        recoveries.clear();
        chests.clear();
        mana.clear();
        phase = Phase.DONE;
    }

    @Override
    public void onEnd(EndReason reason) {
        context.hideBars();
        cleanup();
        if (!context.embedded() && reason == EndReason.FINISHED && !services.isTest())
            roundResult().awards().forEach((id, points) -> services.award("party", id, points));
    }

    @Override
    public PartyRoundResult roundResult() {
        return standings == null
                ? PartyRoundResult.empty()
                : standings.result(context.eligible(), tick);
    }

    @Override
    public boolean ready() {
        return phase != Phase.BUILDING;
    }

    @Override
    public List<Component> summary() {
        return List.copyOf(result);
    }

    @Override
    public Component globalResult() {
        return champion == null ? null : I18n.tr("party.magic.global_winner", name(champion));
    }
}
