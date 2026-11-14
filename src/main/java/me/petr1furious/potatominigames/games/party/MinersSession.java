package me.petr1furious.potatominigames.games.party;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.SessionServices;
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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
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
import org.bukkit.event.player.PlayerItemConsumeEvent;
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

/** Party FFA: private mining, random auto-upgrades and consumable loot, then underground PvP. */
final class MinersSession implements PartyRoundSession {
    private record Hit(UUID attacker, long tick) {}

    private static final NamespacedKey COMPASS =
            new NamespacedKey("potatominigames", "miners_compass");
    private static final NamespacedKey GOLD_HEALTH = new NamespacedKey("potatominigames", "miners_gold_health");
    private final SessionServices services;
    private final PartyRoundContext context;
    private final PartyConfig config;
    private final Random random = new Random();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, Integer> cells = new HashMap<>();
    private final Set<UUID> connected = new LinkedHashSet<>();
    private final Map<UUID, Hit> lastHit = new HashMap<>();
    private final Map<UUID, Long> deadAt = new HashMap<>();
    private final Map<UUID, Long> gadgetReadyAt = new HashMap<>();
    private final CountdownFreeze freeze = new CountdownFreeze();
    private final ArenaProjectiles shots = new ArenaProjectiles();
    private final PvpBombs bombs = PvpBombs.miners();
    private final MinersDrops drops = new MinersDrops();
    private final MinersGold gold = new MinersGold();
    private final MinersVisibility visibility = new MinersVisibility();
    private final MinersPerks perks = new MinersPerks();
    private MinersZone zone;
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

    MinersSession(SessionServices services, PartyConfig config) {
        this(services, config, PartyRoundContext.standalone(services));
    }

    MinersSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        this.context = context;
        this.services = context.services();
        this.config = config;
    }

    private static Component text(Component value) {
        return value.color(NamedTextColor.GOLD);
    }

    private void say(Component value) {
        services.messages().broadcast(text(value));
    }

    private Component name(UUID id) {
        return names.containsKey(id) ? Component.text(names.get(id)) : I18n.tr("party.rounds.player");
    }

    private Player player(UUID id) {
        return connected.contains(id) ? Bukkit.getPlayer(id) : null;
    }

    private boolean alive(Player player) {
        return standings != null
                && connected.contains(player.getUniqueId())
                && standings.alive(player.getUniqueId())
                && !player.isDead();
    }

    private boolean playing(Player player) {
        return round != null
                && round.mining()
                && alive(player)
                && player.getGameMode() == GameMode.SURVIVAL;
    }

    private boolean fighting(Player player) {
        return round != null && round.fighting() && playing(player);
    }

    private List<Player> survivors() {
        return standings.survivors().stream()
                .map(this::player)
                .filter(p -> p != null && alive(p))
                .toList();
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
        List<UUID> roster = new ArrayList<>();
        for (var participant : services.participants().all()) {
            names.put(participant.id(), participant.name());
            connected.add(participant.id());
            roster.add(participant.id());
        }
        if (roster.isEmpty() || roster.size() > 12)
            throw new CommandFailure(I18n.tr("party.rounds.miners.error.players"));
        Collections.shuffle(roster, random);
        for (int i = 0; i < roster.size(); i++) cells.put(roster.get(i), i);
        standings = new SurvivalStandings(roster);
        finaleSeconds =
                Math.clamp(
                        (context.embedded()
                                ? context.options().minersFinaleSeconds()
                                : config.minersFinaleSeconds.get()),
                        15,
                        120);
        round =
                new MinersRound(
                        Math.clamp(
                                context.embedded()
                                        ? context.options().countdownSeconds()
                                        : config.survivalCountdownSeconds.get(),
                                context.embedded() ? 0 : 3,
                                15),
                        Math.clamp(
                                (context.embedded()
                                        ? context.options().minersPreparationSeconds()
                                        : config.minersPreparationSeconds.get()),
                                10,
                                300),
                        Math.clamp(
                                (context.embedded()
                                        ? context.options().minersCombatSeconds()
                                        : config.minersCombatSeconds.get()),
                                30,
                                600),
                        finaleSeconds);
        world = context.createVoid("party_miners");
        services.arena().applyStandardGameRules(world);
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(6000);
        world.setStorm(false);
        world.setGameRule(GameRules.PVP, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        arena = new MinersArena(world, roster.size(), random);
        services.arena()
                .applyBorder(world, arena.center(), arena.borderSize(), arena.borderSize(), 0);
        bar =
                services.bossBars()
                        .create(
                                text(I18n.tr("party.rounds.miners.preparing")),
                                BossBar.Color.BLUE,
                                BossBar.Overlay.PROGRESS);
        for (Player p : services.participants().onlinePlayers()) {
            respawn(p);
            p.closeInventory();
            p.setItemOnCursor(null);
            services.playerState().resetForRound(p, GameMode.SPECTATOR);
            teleport(p, arena.spectator());
            freeze.freeze(p);
            p.showBossBar(bar);
        }
        hud();
        say(I18n.tr("party.rounds.miners.instructions"));
        if (services.isTest()) say(I18n.tr("party.rounds.miners.test"));
        ticker =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                services.plugin(),
                                () -> {
                                    try {
                                        tickGame();
                                    } catch (RuntimeException error) {
                                        services.plugin()
                                                .getLogger()
                                                .log(
                                                        java.util.logging.Level.SEVERE,
                                                        "Miners match failed",
                                                        error);
                                        context.finish(EndReason.ABORTED, PartyRoundResult.empty());
                                    }
                                },
                                1,
                                1);
    }

    private void teleport(Player p, Location to) {
        internalTeleport = true;
        try {
            p.teleport(to);
        } finally {
            internalTeleport = false;
        }
        p.setFallDistance(0);
        p.setVelocity(new org.bukkit.util.Vector());
    }

    private void respawn(Player p) {
        internalTeleport = true;
        try {
            if (p.isDead()) p.spigot().respawn();
        } finally {
            internalTeleport = false;
        }
    }

    private void equip(Player p) {
        freeze.release(p.getUniqueId());
        respawn(p);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        services.playerState().resetForRound(p, GameMode.SURVIVAL);
        p.setAbsorptionAmount(0);
        p.setSaturation(5);
        p.setNoDamageTicks(0);
        var inv = p.getInventory();
        ItemStack pick = PvpClass.item(Material.IRON_PICKAXE);
        pick.editMeta(meta -> meta.addEnchant(Enchantment.EFFICIENCY, 3, false));
        inv.setItem(0, pick);
        inv.setItem(1, PvpClass.item(Material.STONE_SWORD));
        inv.setItem(2, new ItemStack(Material.COBBLESTONE, 64));
        inv.setHelmet(PvpClass.item(Material.LEATHER_HELMET));
        inv.setChestplate(PvpClass.item(Material.LEATHER_CHESTPLATE));
        inv.setLeggings(PvpClass.item(Material.LEATHER_LEGGINGS));
        inv.setBoots(PvpClass.item(Material.LEATHER_BOOTS));
        ItemStack compass = new ItemStack(Material.COMPASS);
        compass.editMeta(meta -> {
            meta.displayName(text(I18n.tr("party.rounds.miners.compass.name")));
            meta.getPersistentDataContainer().set(COMPASS, PersistentDataType.BYTE, (byte) 1);
        });
        inv.setItem(7, I18n.item(compass, p));
        inv.setHeldItemSlot(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 60000, 0, true, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60000, 0, true, false));
        teleport(p, arena.spawn(cells.get(p.getUniqueId())));
        freeze.freeze(p);
        for (MinersOre ore : MinersOre.values())
            p.sendMessage(ore.description.color(NamedTextColor.GRAY));
        p.sendMessage(text(I18n.tr("party.rounds.miners.loot.instructions")));
    }

    private void tickGame() {
        if (round.phase() == MinersRound.Phase.DONE || !services.session().isRunning()) return;
        tick++;
        perks.tick(tick, arena, services.plugin(), this::playing);
        if (connected.isEmpty()) {
            context.finish(EndReason.ALL_PLAYERS_LEFT, PartyRoundResult.empty());
            return;
        }
        if (round.phase() == MinersRound.Phase.BUILDING && arena.buildSlice(4096)) {
            for (Player p : survivors()) equip(p);
            hud();
            round.ready(tick);
        }
        MinersRound.Phase previous = round.phase();
        if (previous == MinersRound.Phase.MINING && tick >= round.deadline()) arena.openSlice(4096);
        round.advance(tick, arena.opened());
        if (previous != round.phase()) {
            if (round.phase() == MinersRound.Phase.MINING) {
                freeze.releaseAll();
                say(I18n.tr("party.rounds.miners.mining_started"));
            }
            if (round.phase() == MinersRound.Phase.FIGHT) {
                world.setGameRule(GameRules.PVP, true);
                announce(I18n.tr("party.rounds.miners.open"),
                        I18n.tr("party.rounds.miners.open.subtitle"));
                say(I18n.tr("party.rounds.miners.open.instructions"));
            }
            if (round.phase() == MinersRound.Phase.FINALE) {
                visibility.clear();
                // Do not capture a temporary arrow/potion outline as a permanent prior flag.
                for (Player p : survivors()) p.removePotionEffect(PotionEffectType.GLOWING);
                visibility.reveal(survivors());
                // Leave the native border around the full arena so players can mine across the storm.
                zone = new MinersZone(arena.center(), arena.borderSize(), tick, finaleSeconds * 20L);
                announce(I18n.tr("party.rounds.miners.finale"),
                        I18n.tr("party.rounds.miners.finale.subtitle"));
            }
        }
        if (round.phase() == MinersRound.Phase.RESULT) {
            if (tick >= round.deadline()) context.finish(EndReason.FINISHED, roundResult());
            return;
        }
        for (UUID id : List.copyOf(deadAt.keySet()))
            if (tick - deadAt.get(id) >= DeathPause.TICKS) {
                Player p = player(id);
                if (p != null) {
                    respawn(p);
                    if (p.getGameMode() != GameMode.SPECTATOR) spectate(p);
                }
                deadAt.remove(id);
            }
        for (Player p : survivors())
            if (!p.getWorld().equals(world) || p.getLocation().getY() < MinersArena.BASE - 1)
                out(p);
        if (standings.decided(services.isTest(), tick)) {
            result(standings.survivors());
            return;
        }
        if (round.phase() == MinersRound.Phase.FINALE) {
            visibility.reveal(survivors());
            zone.update(tick);
        }
        if (tick % 20 == 0) {
            shots.expire(tick);
            drops.prune();
            for (Player p : survivors()) {
                updateCompass(p);
                double pressure = round.pressure(tick);
                if (pressure > 0 && fighting(p) && zone != null && zone.outside(p.getLocation(), tick))
                    p.damage(pressure);
            }
            hud();
        }
    }

    private void announce(Component title, Component subtitle) {
        Title message =
                Title.title(
                        text(title),
                        subtitle.color(NamedTextColor.WHITE),
                        Title.Times.times(
                                Duration.ofMillis(100),
                                Duration.ofSeconds(3),
                                Duration.ofMillis(200)));
        for (Player p : services.session().audiencePlayers()) p.showTitle(message);
    }

    private Player nearest(Player p) {
        return survivors().stream()
                .filter(other -> other != p && other.getWorld().equals(p.getWorld()))
                .min(
                        java.util.Comparator.comparingDouble(
                                other -> other.getLocation().distanceSquared(p.getLocation())))
                .orElse(null);
    }

    private void updateCompass(Player p) {
        Player target = round.fighting() ? nearest(p) : null;
        Location at =
                target == null ? arena.spawn(cells.get(p.getUniqueId())) : target.getLocation();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (!isCompass(item)) continue;
            item.editMeta(
                    CompassMeta.class,
                    meta -> {
                        meta.setLodestoneTracked(false);
                        meta.setLodestone(at);
                    });
            inv.setItem(i, item);
        }
    }

    private static boolean isCompass(ItemStack item) {
        return item != null
                && item.getType() == Material.COMPASS
                && item.hasItemMeta()
                && item.getItemMeta()
                        .getPersistentDataContainer()
                        .has(COMPASS, PersistentDataType.BYTE);
    }

    private void hud() {
        if (bar == null || round.phase() == MinersRound.Phase.DONE) return;
        Component line = switch (round.phase()) {
            case BUILDING -> I18n.tr("party.rounds.miners.status.building", Math.round(arena.progress() * 100));
            case COUNTDOWN -> I18n.tr("party.rounds.starts_in", round.secondsLeft(tick));
            case MINING -> I18n.tr("party.rounds.miners.status.mining", round.secondsLeft(tick));
            case FIGHT -> I18n.tr("party.rounds.miners.status.fight", round.secondsLeft(tick));
            case FINALE -> round.secondsLeft(tick) == 0
                    ? I18n.tr("party.rounds.miners.status.last_fight")
                    : I18n.tr("party.rounds.miners.status.finale", round.secondsLeft(tick));
            case RESULT -> I18n.tr("party.rounds.miners.status.result", round.secondsLeft(tick));
            default -> Component.empty();
        };
        bar.name(text(I18n.tr("party.rounds.status", PartyRoundFactory.title("miners"), line)));
        bar.progress(round.phase() == MinersRound.Phase.BUILDING ? arena.progress() : 1);
        for (Player p : services.session().audiencePlayers()) {
            p.showBossBar(bar);
            List<Component> lines = new ArrayList<>(List.of(
                    line.color(NamedTextColor.YELLOW),
                    I18n.tr("party.rounds.miners.in_game", standings.survivors().size(), names.size()).color(NamedTextColor.WHITE),
                    I18n.tr("party.rounds.miners.gold.status", gold.bonus(p.getUniqueId())).color(NamedTextColor.GOLD)));
            lines.addAll(perks.hud(p, tick));
            services.sidebar().update(p, text(PartyRoundFactory.title("miners")), lines);
        }
        if (round.phase() != MinersRound.Phase.FINALE && round.phase() != MinersRound.Phase.RESULT)
            visibility.hideNames(services.session().audiencePlayers(), names.values());
    }

    @Override
    public void onSecondTick(long seconds) {
        hud();
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);
        Player p = event.getPlayer();
        Block block = event.getBlock();
        if (!canMine(p, block)) return;
        MinersPerk perk = MinersPerk.from(p.getInventory().getItemInMainHand());
        if (perk != null && perk != MinersPerk.DRILL) return;
        if (perk == MinersPerk.DRILL) {
            consumePerk(p, perk);
            for (MinersArena.Pos pos : MinersMining.drill(MinersArena.Pos.of(block), p.getEyeLocation().getDirection()))
                mineBlock(p, world.getBlockAt(pos.x(), pos.y(), pos.z()));
        } else mineBlock(p, block);
    }

    private boolean canMine(Player p, Block block) {
        if (!playing(p) || !arena.editable(block)
                || !round.fighting() && !arena.owns(block.getLocation(), cells.getOrDefault(p.getUniqueId(), -1))) return false;
        return mineable(block);
    }

    private boolean mineable(Block block) {
        Material type = block.getType();
        return type == Material.STONE || type == MinersArena.OPEN_PARTITION
                || type == Material.COBBLESTONE || type == Material.COBWEB
                || arena.oreAt(block) != null;
    }

    /** Every mining source consumes ore provenance exactly once and uses the same reward rules. */
    private boolean mineBlock(Player p, Block block) {
        if (!canMine(p, block)) return false;
        Material material = block.getType();
        MinersOre ore = arena.takeOre(block);
        Location at = block.getLocation().add(.5, .25, .5);
        block.setType(Material.AIR, false);
        if (ore == MinersOre.GOLD) collectGold(p);
        else if (ore == MinersOre.PERKS) {
            MinersPerk perk = MinersPerk.random(random);
            dropReward(at, perk.item(perk.charges));
            p.sendActionBar(text(I18n.tr("party.rounds.miners.perk.found", I18n.tr(perk.key() + ".name"))));
        } else if (ore != null) {
            boolean lucky = perks.lucky(p.getUniqueId());
            for (MinersLoot.Drop loot : MinersLoot.roll(ore, random, lucky)) reward(p, at, loot);
        } else if (material == Material.STONE && perks.sift(p.getUniqueId(), random)) {
            for (MinersLoot.Drop loot : MinersLoot.sift(random)) reward(p, at, loot);
        } else if (material != Material.COBWEB) giveCobble(p);
        return true;
    }

    private void reward(Player p, Location at, MinersLoot.Drop loot) {
        ItemStack item = loot.item();
        if (!MinersEquipment.apply(p, item, drops)) dropReward(at, item);
    }

    private void dropReward(Location at, ItemStack stack) {
        Item item = world.dropItem(at, stack);
        item.setPickupDelay(8);
        drops.track(item);
    }

    private void consumePerk(Player p, MinersPerk perk) {
        ItemStack held = p.getInventory().getItemInMainHand();
        int remaining = MinersPerk.charges(held) - 1;
        if (remaining > 0) p.getInventory().setItemInMainHand(drops.forViewer(perk.item(remaining), p));
        else if (held.getAmount() > 1) {
            ItemStack rest = held.clone();
            rest.setAmount(held.getAmount() - 1);
            p.getInventory().setItemInMainHand(rest);
        } else p.getInventory().setItemInMainHand(null);
    }

    private void usePerk(Player p, MinersPerk perk, PlayerInteractEvent event) {
        if (perk == MinersPerk.DRILL) return;
        if (perk == MinersPerk.TUNNEL) {
            var hit = p.rayTraceBlocks(6);
            if (hit == null || hit.getHitBlock() == null || !canMine(p, hit.getHitBlock())) {
                p.sendActionBar(text(I18n.tr("party.rounds.miners.perk.aim")));
                return;
            }
            consumePerk(p, perk);
            for (MinersArena.Pos pos : MinersMining.tunnel(MinersArena.Pos.of(hit.getHitBlock()), p.getEyeLocation().getDirection())) {
                Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
                if (!arena.editable(block) || !round.fighting()
                        && !arena.owns(block.getLocation(), cells.getOrDefault(p.getUniqueId(), -1))) break;
                if (block.getType().isAir()) continue;
                if (!mineBlock(p, block)) break;
            }
        } else if (perk == MinersPerk.BOMB) {
            if (event.getClickedBlock() == null) return;
            Block target = event.getClickedBlock().getRelative(event.getBlockFace());
            if (!target.getType().isAir() || !arena.editable(target)
                    || target.getLocation().add(.5, .5, .5).distanceSquared(p.getEyeLocation()) > 36
                    || !round.fighting() && !arena.owns(target.getLocation(), cells.getOrDefault(p.getUniqueId(), -1))) {
                p.sendActionBar(text(I18n.tr("party.rounds.miners.perk.bomb.invalid")));
                return;
            }
            consumePerk(p, perk);
            explodePerk(p, target);
        } else {
            if (!perks.activate(p, perk, tick, arena, services.plugin())) {
                p.sendActionBar(text(I18n.tr("party.rounds.miners.perk.active")));
                return;
            }
            consumePerk(p, perk);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .7f, 1.2f);
        hud();
    }

    private void explodePerk(Player owner, Block target) {
        Location center = target.getLocation().add(.5, .5, .5);
        for (MinersArena.Pos pos : MinersMining.blast(MinersArena.Pos.of(target)))
            mineBlock(owner, world.getBlockAt(pos.x(), pos.y(), pos.z()));
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, .9f);
        if (!round.fighting()) return;
        // Native explosion damage type keeps armour/shields and attributes kills to the owner.
        DamageSource source = DamageSource.builder(DamageType.PLAYER_EXPLOSION)
                .withCausingEntity(owner).withDirectEntity(owner).withDamageLocation(center).build();
        List<Player> targets = new ArrayList<>(survivors());
        targets.remove(owner); targets.add(owner); // Self-damage last allows one blast to trade kills.
        for (Player p : targets) {
            if (!fighting(p) || !p.getWorld().equals(world)) continue;
            double distance = p.getLocation().distance(center);
            if (distance < 4) p.damage(10 * (1 - distance / 4), source);
        }
    }

    private void collectGold(Player p) {
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) return;
        int added = gold.collect(p.getUniqueId(), random);
        if (added == 0) {
            p.sendActionBar(text(I18n.tr("party.rounds.miners.gold.maximum")));
            return;
        }
        double health = p.getHealth();
        max.removeModifier(GOLD_HEALTH);
        max.addTransientModifier(new AttributeModifier(GOLD_HEALTH, gold.bonus(p.getUniqueId()) * 2,
                AttributeModifier.Operation.ADD_NUMBER));
        p.setHealth(Math.min(max.getValue(), health + added * 2));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .6f, added == 2 ? 1.8f : 1.4f);
        p.sendMessage(text(I18n.tr("party.rounds.miners.gold.increased", added, 10 + gold.bonus(p.getUniqueId()))));
    }

    private static void clearGoldHealth(Player p) {
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) return;
        max.removeModifier(GOLD_HEALTH);
        if (!p.isDead() && p.getHealth() > max.getValue()) p.setHealth(max.getValue());
    }

    private void giveCobble(Player p) {
        int count = 0;
        for (ItemStack item : p.getInventory().getContents())
            if (item != null && item.getType() == Material.COBBLESTONE) count += item.getAmount();
        if (count < 128) p.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 1));
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        if (MinersPerk.from(event.getItemInHand()) != null) { event.setCancelled(true); return; }
        Player p = event.getPlayer();
        if (!playing(p)
                || event.getBlock().getType() != Material.COBBLESTONE
                        && !(event.getBlock().getType() == Material.COBWEB && round.fighting())
                || !event.getBlockReplacedState().getType().isAir()
                || !arena.editable(event.getBlock())
                || !round.fighting()
                        && !arena.owns(event.getBlock().getLocation(), cells.get(p.getUniqueId())))
            event.setCancelled(true);
    }

    private Player source(EntityDamageByEntityEvent event) {
        Player owner = bombs.owner(event.getDamageSource().getDirectEntity());
        if (owner == null) owner = bombs.owner(event.getDamager());
        if (owner != null) return owner;
        if (event.getDamager() instanceof Player p) return p;
        return event.getDamager() instanceof Projectile shot
                        && shot.getShooter() instanceof Player p
                ? p
                : null;
    }

    private boolean bombBlast(EntityDamageByEntityEvent event) {
        return bombs.owner(event.getDamager()) != null
                || bombs.owner(event.getDamageSource().getDirectEntity()) != null;
    }

    private boolean validDamage(EntityDamageEvent event, Player victim) {
        if (!fighting(victim)) return false;
        if (!(event instanceof EntityDamageByEntityEvent hit)) return true;
        Player attacker = source(hit);
        if (attacker == null) return false;
        boolean trade =
                (hit.getDamager() instanceof Projectile || bombBlast(hit))
                        && connected.contains(attacker.getUniqueId())
                        && !standings.alive(attacker.getUniqueId())
                        && standings.survived(attacker.getUniqueId(), tick) == tick;
        return fighting(attacker) || trade;
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !validDamage(event, p)) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent hit) {
            if (bombBlast(hit))
                event.setDamage(bombs.blastDamage(hit.getDamager(), event.getDamage()));
            Player attacker = source(hit);
            if (attacker != p && event.getFinalDamage() > 0)
                lastHit.put(p.getUniqueId(), new Hit(attacker.getUniqueId(), tick));
        }
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player p) || !validDamage(event, p))
            event.setCancelled(true);
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        if (!connected.contains(p.getUniqueId()) || !standings.alive(p.getUniqueId())) return;
        Hit hit = lastHit.remove(p.getUniqueId());
        UUID killer = hit != null && tick - hit.tick() <= 160 ? hit.attacker() : null;
        event.deathMessage(null);
        event.deathScreenMessageOverride(
                I18n.render(text(killer == null ? I18n.tr("party.rounds.miners.death")
                        : I18n.tr("party.rounds.miners.killed_by", name(killer))), p));
        event.getDrops().clear();
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        say(killer == null ? I18n.tr("party.rounds.miners.died", p.getName())
                : I18n.tr("party.rounds.miners.killed", name(killer), p.getName()));
        out(p);
    }

    private void out(Player p) {
        if (!standings.eliminate(p.getUniqueId(), tick)) return;
        visibility.stopReveal(p);
        p.removePotionEffect(PotionEffectType.GLOWING);
        freeze.release(p.getUniqueId());
        bombs.eliminate(p.getUniqueId());
        gadgetReadyAt.remove(p.getUniqueId());
        perks.remove(p.getUniqueId());
        deadAt.put(p.getUniqueId(), tick);
        if (!p.isDead()) spectate(p);
    }

    private void spectate(Player p) {
        if (p.isDead()) return;
        visibility.stopReveal(p);
        clearGoldHealth(p);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.closeInventory();
        p.setItemOnCursor(null);
        services.playerState().resetForRound(p, GameMode.SPECTATOR);
        p.setAbsorptionAmount(0);
        teleport(p, arena.spectator());
        p.showBossBar(bar);
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (round.phase() != MinersRound.Phase.DONE
                && !standings.alive(event.getPlayer().getUniqueId()))
            event.setRespawnLocation(arena.spectator());
    }

    @Override
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        if (round.phase() != MinersRound.Phase.DONE
                && !standings.alive(event.getPlayer().getUniqueId())) spectate(event.getPlayer());
    }

    private void result(List<UUID> winners) {
        visibility.clear();
        if (zone != null) zone.clear();
        for (Player p : survivors()) {
            p.removePotionEffect(PotionEffectType.GLOWING);
        }
        champions = List.copyOf(winners);
        round.result(tick);
        shots.clearAll();
        bombs.clear();
        gadgetReadyAt.clear();
        perks.clear();
        drops.clear();
        freeze.releaseAll();
        say(champions.isEmpty() ? I18n.tr("party.rounds.draw")
                : I18n.tr("common.summary.winner", name(champions.getFirst())));
        List<Component> lines = new ArrayList<>();
        int place = 0;
        for (UUID id : standings.ranking(names.keySet(), tick))
            lines.add(text(I18n.tr("party.rounds.miners.result", ++place, name(id))));
        result = List.copyOf(lines);
        for (Player p : survivors()) freeze.freeze(p);
        hud();
    }

    @Override
    public void onParticipantQuit(Player p) {
        leave(p);
    }

    @Override
    public void onParticipantLeave(Player p) {
        leave(p);
    }

    private void leave(Player p) {
        clearGoldHealth(p);
        visibility.release(p.getScoreboard());
        p.removePotionEffect(PotionEffectType.GLOWING);
        visibility.stopReveal(p);
        context.withdraw(p.getUniqueId());
        if (standings == null) return;
        standings.eliminate(p.getUniqueId(), tick);
        connected.remove(p.getUniqueId());
        freeze.release(p.getUniqueId());
        bombs.remove(p.getUniqueId());
        gadgetReadyAt.remove(p.getUniqueId());
        perks.remove(p.getUniqueId());
        shots.clear(p.getUniqueId());
        lastHit.remove(p.getUniqueId());
        p.closeInventory();
        p.setItemOnCursor(null);
    }

    @Override
    public void onParticipantRejoin(Player p) {
        connected.add(p.getUniqueId());
        spectate(p);
        hud();
    }

    @Override
    public void onParticipantJoin(Player p) {
        spectate(p);
        hud();
    }

    @Override
    public boolean isInGame(Player p) {
        return alive(p);
    }

    @Override
    public Location spectatorLocation(Player viewer) {
        return arena == null ? null : arena.spectator();
    }

    @Override
    public boolean mayFollowPlayers(Player viewer) {
        return !alive(viewer) && round != null && round.fighting();
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (internalTeleport || round == null || round.phase() == MinersRound.Phase.DONE) return;
        if (freeze.hold(event) || !alive(event.getPlayer())) return;
        if (round.phase() == MinersRound.Phase.MINING
                && !arena.owns(event.getTo(), cells.get(event.getPlayer().getUniqueId())))
            event.setTo(event.getFrom());
    }

    @Override
    public void onPlayerJump(PlayerJumpEvent event) {
        freeze.onJump(event);
    }

    @Override
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (internalTeleport
                || round == null
                || round.phase() == MinersRound.Phase.DONE
                || !names.containsKey(event.getPlayer().getUniqueId())
                || !standings.alive(event.getPlayer().getUniqueId())) return;
        if (!fighting(event.getPlayer())
                || event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || !arena.contains(event.getTo())
                || !event.getTo().getBlock().isPassable()
                || !event.getTo().clone().add(0, 1, 0).getBlock().isPassable())
            event.setCancelled(true);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!playing(p)) {
            event.setCancelled(true);
            return;
        }
        MinersPerk perk = MinersPerk.from(event.getItem());
        if (perk != null) {
            // The drill spends a charge on BlockBreakEvent, after native mining completes.
            // Cancelling its initial left click prevents that event from ever firing.
            if (perk == MinersPerk.DRILL && event.getHand() == EquipmentSlot.HAND
                    && event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) return;
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND && event.getAction().isRightClick()) usePerk(p, perk, event);
            return;
        }
        MinersGadget gadget = MinersGadget.from(event.getItem());
        if (gadget != null) {
            event.setCancelled(true);
            if (!event.getAction().isRightClick() || event.getHand() != EquipmentSlot.HAND) return;
            if (!fighting(p)) {
                p.sendActionBar(text(I18n.tr("party.rounds.miners.gadget.wait")));
                return;
            }
            long remaining = gadgetReadyAt.getOrDefault(p.getUniqueId(), 0L) - tick;
            if (remaining > 0) {
                p.sendActionBar(text(I18n.tr("party.rounds.miners.gadget.cooldown", (remaining + 19) / 20)));
                return;
            }
            gadget.activate(p);
            gadgetReadyAt.put(p.getUniqueId(), tick + 20);
            ItemStack held = p.getInventory().getItemInMainHand();
            if (held.getAmount() == 1) p.getInventory().setItemInMainHand(null);
            else held.setAmount(held.getAmount() - 1);
            return;
        }
        if (MinersReveal.is(event.getItem()) && !fighting(p)) {
            event.setCancelled(true);
            if (event.getAction().isRightClick())
                p.sendActionBar(text(I18n.tr("party.rounds.miners.reveal.wait")));
            return;
        }
        if (PvpBombs.isBomb(event.getItem())) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND || !fighting(p)) return;
            if (event.getAction().isLeftClick()) throwBomb(p);
            else if (event.getAction().isRightClick() && event.getClickedBlock() != null) {
                var block = event.getClickedBlock().getRelative(event.getBlockFace());
                if (arena.editable(block)
                        && block.getType().isAir()
                        && block.getLocation().distanceSquared(p.getEyeLocation()) <= 36)
                    bombs.use(p, block.getLocation().add(.5, 0, .5), false, services.plugin());
            }
            return;
        }
        if (isCompass(event.getItem()) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            Player target = round.fighting() ? nearest(p) : null;
            p.sendActionBar(text(target == null
                    ? I18n.tr("party.rounds.miners.compass.wait")
                    : I18n.tr("party.rounds.miners.compass.target", name(target.getUniqueId()),
                            Math.round(target.getLocation().distance(p.getLocation())),
                            target.getLocation().getBlockY() - p.getLocation().getBlockY())));
            return;
        }
        if (!round.fighting()
                && event.getItem() != null
                && (event.getItem().getType() == Material.ENDER_PEARL
                        || event.getItem().getType() == Material.WIND_CHARGE))
            event.setCancelled(true);
    }

    @Override
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        if (!PvpBombs.isBomb(event.getPlayer().getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        if (fighting(event.getPlayer())) throwBomb(event.getPlayer());
    }

    private void throwBomb(Player p) {
        Location origin = p.getEyeLocation().subtract(0, .65, 0);
        var direction = p.getEyeLocation().getDirection();
        var hit = world.rayTraceBlocks(origin, direction, .8, FluidCollisionMode.NEVER, true);
        Location at = hit == null ? origin.clone().add(direction.clone().multiply(.35)) : origin;
        if (!arena.chargeFits(at)) at = origin;
        if (arena.chargeFits(at)) bombs.use(p, at, true, services.plugin());
        else p.sendActionBar(text(I18n.tr("party.rounds.miners.tnt.no_space")));
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player p)) {
            event.setCancelled(true);
            return;
        }
        if (!playing(p) || !round.fighting() && !(event.getEntity() instanceof ThrownPotion)) {
            event.setCancelled(true);
            return;
        }
        shots.track(p.getUniqueId(), event.getEntity(), tick);
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player p) || !fighting(p)) {
            event.setCancelled(true);
            return;
        }
        if (event.getProjectile() instanceof AbstractArrow arrow)
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }

    @Override
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (!MinersReveal.is(event.getItem())) return;
        Player caster = event.getPlayer();
        if (!fighting(caster)) {
            event.setCancelled(true);
            caster.sendActionBar(text(I18n.tr("party.rounds.miners.reveal.wait")));
            return;
        }
        for (Player opponent : survivors())
            if (!opponent.getUniqueId().equals(caster.getUniqueId()) && opponent.getWorld().equals(world))
                opponent.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                        MinersReveal.DURATION_TICKS, 0, false, false));
        caster.sendActionBar(text(I18n.tr("party.rounds.miners.reveal.used", MinersReveal.DURATION_TICKS / 20)));
        // Native consumption removes the potion and leaves an empty bottle.
    }

    @Override
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player caster) || !playing(caster)) {
            event.setCancelled(true);
            return;
        }
        PvpPotion potion = PvpPotion.from(event.getPotion().getItem());
        if (potion == null) {
            event.setCancelled(true);
            return;
        }
        for (var target : event.getAffectedEntities()) {
            boolean self = target.equals(caster);
            boolean active = target instanceof Player p && playing(p) && (self || round.fighting());
            if (!potion.affects(self, active)) event.setIntensity(target, 0);
        }
    }

    @Override
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> affected = List.copyOf(event.blockList());
        event.blockList().clear();
        event.setYield(0);
        Player owner = bombs.owner(event.getEntity());
        if (round == null || !round.fighting() || owner == null
                || !world.equals(event.getLocation().getWorld())
                || !connected.contains(owner.getUniqueId())) return;
        boolean activeOwner = fighting(owner);
        boolean finalBlast = !standings.alive(owner.getUniqueId())
                && standings.survived(owner.getUniqueId(), tick) == tick;
        if (!activeOwner && !finalBlast) return;
        for (Block block : affected) {
            if (!arena.editable(block) || !mineable(block)) continue;
            if (activeOwner) mineBlock(owner, block);
            else {
                // Native blast damage can kill the owner before its block callback.
                // The same blast still breaks terrain, but cannot equip or heal a dead player.
                arena.takeOre(block);
                block.setType(Material.AIR, false);
            }
        }
    }

    @Override
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    @Override
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!round.fighting()) event.setCancelled(true);
    }

    @Override
    public void onDropItem(PlayerDropItemEvent event) {
        if (!playing(event.getPlayer())) event.setCancelled(true);
        else {
            ItemStack dropped = drops.unrendered(event.getItemDrop().getItemStack());
            if (isCompass(dropped))
                dropped.editMeta(meta -> meta.displayName(text(I18n.tr("party.rounds.miners.compass.name"))));
            event.getItemDrop().setItemStack(dropped);
            drops.track(event.getItemDrop());
        }
    }

    @Override
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)
                || !playing(p)
                || !round.fighting()
                        && !arena.owns(event.getItem().getLocation(), cells.get(p.getUniqueId())))
            event.setCancelled(true);
        else
            event.getItem().setItemStack(drops.forViewer(event.getItem().getItemStack(), p));
    }

    @Override
    public void onCraftItem(CraftItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)
                || !playing(p)
                || event.getSlotType() == InventoryType.SlotType.CRAFTING
                || event.getSlotType() == InventoryType.SlotType.RESULT) event.setCancelled(true);
    }

    @Override
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)
                || !playing(p)
                || event.getRawSlots().stream()
                        .anyMatch(slot -> slot < event.getView().getTopInventory().getSize()))
            event.setCancelled(true);
    }

    private void cleanup() {
        visibility.clear();
        if (zone != null) zone.clear();
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        freeze.releaseAll();
        bombs.clear();
        gadgetReadyAt.clear();
        perks.clear();
        shots.clearAll();
        drops.clear();
        for (UUID id : names.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) clearGoldHealth(p);
        }
        gold.clear();
        for (Player p : services.participants().onlinePlayers()) {
            p.closeInventory();
            p.setItemOnCursor(null);
        }
        if (round != null) round.done();
    }

    @Override
    public void onEnd(EndReason reason) {
        context.hideBars();
        cleanup();
        if (!context.embedded() && reason == EndReason.FINISHED)
            for (UUID id : champions) services.award("party", id, 3);
    }

    @Override
    public PartyRoundResult roundResult() {
        return standings == null
                ? PartyRoundResult.empty()
                : standings.result(context.eligible(), tick);
    }

    @Override
    public boolean ready() {
        return round != null && round.phase() != MinersRound.Phase.BUILDING;
    }

    @Override
    public List<Component> summary() {
        return result;
    }

    @Override
    public Component globalResult() {
        return champions.isEmpty()
                ? null
                : text(I18n.tr("party.rounds.miners.victory", name(champions.getFirst())));
    }
}
