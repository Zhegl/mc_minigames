/*
 * Based on PillarPeril by MarcPG (https://github.com/MarcPG1905/PillarPeril, Apache-2.0),
 * via the fork at https://github.com/Petr1Furious/PillarPeril. Rewritten in Java for
 * Potato Minigames by Claude, and modified; see NOTICE.
 */
package me.petr1furious.potatominigames.games.pillars;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.player.Participant;
import me.petr1furious.potatominigames.core.text.TimeFormat;

/**
 * One pillars game: everybody spawns on a bedrock pillar high above the arena, is
 * handed a random item every few seconds, and the last one standing wins.
 */
final class PillarsSession implements GameSession {
    private enum Ending {
        FORCE, TIME_OVER, LAST_STANDING, DRAW
    }

    /** What the game tracks about one player, kept across a reconnect. */
    private static final class PillarPlayer {
        final UUID id;
        final String name;
        final ItemPool.GroupState groups;
        int kills;
        Integer deathTick;
        /** The spawn location on top of this player's own pillar. */
        Location pillar;
        /** Where they died, or stood when they disconnected: where respawn puts them back. */
        Location lastLocation;
        Integer disconnectedAt;

        PillarPlayer(Participant participant, List<ItemPool.ItemGroup> groups) {
            this.id = participant.id();
            this.name = participant.name();
            this.groups = new ItemPool.GroupState(groups);
        }

        Player player() {
            return Bukkit.getPlayer(id);
        }
    }

    private static final List<TextColor> ITEM_NOW_COLORS = List.of(TextColor.color(0x00AA22), TextColor.color(0x11FF77));
    private static final List<TextColor> ITEM_TIME_COLORS = List.of(TextColor.color(0x0022FF), TextColor.color(0x3399FF));
    // Kept short, so that every countdown title fully replaces the previous one.
    private static final Title.Times COUNTDOWN_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200));
    /** A death's draw window, and the delay before the dead are moved: 0.95 s. */
    private static final int DEATH_DELAY_TICKS = 19;

    private final SessionServices services;
    private final PillarsGame game;
    private final PillarsConfig config;
    private final PillarsConfig.Mode mode;

    private PillarsArena arena;
    private World world;
    private Location center;
    private double radius;
    private List<ItemPool.PoolItem> items = List.of();
    private List<ItemPool.ItemGroup> groups = List.of();

    private final Map<UUID, PillarPlayer> players = new LinkedHashMap<>();
    private final List<PillarPlayer> alive = new ArrayList<>();

    private long timeLeft;
    private long itemCountdown;
    private long startCountdown;
    private boolean started;
    private boolean ending;
    private Ending endingCause;
    private List<PillarPlayer> winners = List.of();

    private BossBar bossBar;
    private BukkitTask actionBarTask;
    private double actionBarPhase;

    PillarsSession(SessionServices services, PillarsGame game, PillarsConfig config, String modeId) {
        this.services = services;
        this.game = game;
        this.config = config;
        this.mode = config.mode(modeId);
    }

    // --- start ---

    @Override
    public void onStart() throws CommandFailure {
        List<PillarsArena> usable = config.arenas().stream()
                .filter(candidate -> game.slimeWorlds().templateExists(candidate.template()))
                .toList();
        if (usable.isEmpty()) {
            throw new CommandFailure("No pillars arena is set up yet. An admin can import them with "
                    + "/pillars arena import all.");
        }
        arena = usable.get(ThreadLocalRandom.current().nextInt(usable.size()));
        world = services.worlds().cloneSlime(arena.template(), "pillars_game_" + arena.name());
        applyArenaSettings();

        double platformHeight = config.platformHeight.get();
        center = new Location(world, arena.x(), platformHeight + 1.0, arena.z());

        PillarsConfig.Items itemConfig = config.items(services.plugin().getLogger());
        groups = itemConfig.groups();
        items = ItemPool.build(world, itemConfig);
        if (items.isEmpty()) {
            throw new CommandFailure("The pillars item pool is empty; check games/pillars.yml.");
        }

        for (Participant participant : services.participants().all()) {
            Player player = participant.player();
            if (player == null) {
                continue;
            }
            // Somebody still on the death screen cannot be teleported onto a pillar.
            if (player.isDead()) {
                player.spigot().respawn();
            }
            services.playerState().resetForRound(player, GameMode.SURVIVAL);
            player.setSaturation(20.0f);
            healFully(player);
            PillarPlayer pillarPlayer = new PillarPlayer(participant, groups);
            players.put(participant.id(), pillarPlayer);
            alive.add(pillarPlayer);
        }

        radius = alive.size() * config.platformDistanceFactor.get() / (Math.PI * 2);
        List<Location> spots = mode.layout.get().equalsIgnoreCase("random") ? randomLayout() : circularLayout();
        Location centered = center.toCenterLocation();
        for (int i = 0; i < alive.size(); i++) {
            Location spot = spots.get(i);
            placePillar(spot.getBlockX(), spot.getBlockZ());
            // Only X and Z get centered, so players stand on their pillar instead of dropping onto it.
            Location location = spot.toCenterLocation();
            location.setY(spot.getY() + 1.0);
            location.setYaw((float) Math.toDegrees(Math.atan2(-(centered.getX() - location.getX()),
                    centered.getZ() - location.getZ())));
            PillarPlayer pillarPlayer = alive.get(i);
            pillarPlayer.pillar = location;
            Player player = pillarPlayer.player();
            player.teleport(location);
            player.setVelocity(new Vector());
            player.setFallDistance(0);
        }

        if (mode.bossBar.get()) {
            bossBar = services.bossBars().create(Component.empty(), BossBar.Color.PINK, BossBar.Overlay.NOTCHED_10);
            for (PillarPlayer pillarPlayer : alive) {
                pillarPlayer.player().showBossBar(bossBar);
            }
        }
        if (mode.actionBar.get()) {
            actionBarTask = Bukkit.getScheduler().runTaskTimer(services.plugin(), this::tickActionBar, 5L, 5L);
        }

        timeLeft = mode.timeLimitSeconds();
        itemCountdown = mode.cooldownSeconds.get();
        startCountdown = Math.max(0, config.startCountdown.get());
        started = startCountdown <= 0;
        if (!started) {
            for (PillarPlayer pillarPlayer : alive) {
                pillarPlayer.player().setInvulnerable(true);
            }
        }
        // Only now that nothing can fail any more do the game's listeners start answering for it.
        game.setActiveSession(this);
        services.plugin().getLogger().info("Pillars (" + mode.id + ") started in arena " + arena.name() + " with "
                + alive.size() + " players and " + items.size() + " items in the pool");
        updateHud();
        // Without a start countdown the game is running already, so release never gets to do this.
        if (started) {
            cycleItems();
        }
    }

    /** Applies the arena's time of day and game rules to this game's own copy of it. */
    private void applyArenaSettings() {
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        for (Map.Entry<String, Object> entry : arena.gameRules().entrySet()) {
            GameRule<?> rule = resolveGameRule(entry.getKey());
            if (rule == null || !setGameRule(rule, entry.getValue())) {
                services.plugin().getLogger().warning("pillars: arena " + arena.name() + " has an unusable game rule "
                        + entry.getKey() + "=" + entry.getValue());
            }
        }
        if (arena.time() != null) {
            world.setTime(arena.time());
        }
    }

    /** Looks a game rule up by name, accepting the old camel-case spelling as well as the new one. */
    private static GameRule<?> resolveGameRule(String name) {
        String wanted = normalizedRuleName(name);
        for (GameRule<?> rule : Registry.GAME_RULE) {
            String key = normalizedRuleName(rule.getKey().getKey());
            if (key.equals(wanted) || ("do" + key).equals(wanted)) {
                return rule;
            }
        }
        return null;
    }

    private static String normalizedRuleName(String name) {
        return name.chars().filter(Character::isLetterOrDigit).collect(StringBuilder::new,
                StringBuilder::appendCodePoint, StringBuilder::append).toString().toLowerCase(java.util.Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private boolean setGameRule(GameRule<?> rule, Object value) {
        Object converted;
        if (rule.getType() == Boolean.class) {
            converted = value instanceof Boolean bool ? bool
                    : switch (value.toString().trim().toLowerCase(java.util.Locale.ROOT)) {
                        case "true" -> Boolean.TRUE;
                        case "false" -> Boolean.FALSE;
                        default -> null;
                    };
        } else if (rule.getType() == Integer.class) {
            try {
                converted = value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException exception) {
                converted = null;
            }
        } else {
            converted = null;
        }
        if (converted == null) {
            return false;
        }
        world.setGameRule((GameRule<Object>) rule, converted);
        return true;
    }

    private List<Location> circularLayout() {
        List<Location> spots = new ArrayList<>();
        for (int i = 0; i < alive.size(); i++) {
            double angle = Math.PI * 2 * i / alive.size();
            spots.add(spot(center.getX() + radius * Math.cos(angle), center.getZ() + radius * Math.sin(angle)));
        }
        return spots;
    }

    private List<Location> randomLayout() {
        radius *= 1.2;
        List<Location> candidates = new ArrayList<>();
        int extent = (int) radius;
        for (int x = -extent; x < extent; x++) {
            for (int z = -extent; z < extent; z++) {
                if (Math.hypot(x, z) <= radius) {
                    candidates.add(spot(center.getX() + x, center.getZ() + z));
                }
            }
        }
        if (candidates.size() < alive.size()) {
            // A single player has nobody to share an arena with; the middle will do.
            return circularLayout();
        }
        java.util.Collections.shuffle(candidates);
        return candidates.subList(0, alive.size());
    }

    private Location spot(double x, double z) {
        return new Location(world, x, config.platformHeight.get(), z);
    }

    /** Builds one pillar, from the bottom of the world up to the height the players stand at. */
    private void placePillar(int x, int z) {
        Material material = config.platformMaterial.get();
        int top = (int) Math.floor(config.platformHeight.get());
        for (int y = world.getMinHeight(); y <= top; y++) {
            world.getBlockAt(x, y, z).setType(material, false);
        }
    }

    // --- the clock ---

    @Override
    public void onSecondTick(long secondsElapsed) {
        if (ending || alive.isEmpty()) {
            return;
        }
        tickDisconnects();
        if (ending || alive.isEmpty()) {
            return;
        }
        if (!started) {
            tickStartCountdown();
            updateHud();
            return;
        }
        if (itemCountdown <= 0) {
            cycleItems();
        } else if (config.soundEffects.get() && itemCountdown <= config.soundCooldownSeconds.get()) {
            for (PillarPlayer pillarPlayer : alive) {
                Player player = pillarPlayer.player();
                if (player != null) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.2f, 2.0f);
                }
            }
        }
        itemCountdown--;
        timeLeft--;
        updateHud();
        if (timeLeft <= 0) {
            end(Ending.TIME_OVER, List.of());
        }
    }

    private void tickStartCountdown() {
        // The last number needs its own second on screen, so releasing waits for the tick after it.
        if (startCountdown <= 0) {
            release();
            return;
        }
        Component number = Component.text(String.valueOf(startCountdown),
                startCountdown <= 3 ? NamedTextColor.RED : NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
        for (Player player : services.participants().onlinePlayers()) {
            player.showTitle(Title.title(number, Component.text("Get ready!", NamedTextColor.GRAY), COUNTDOWN_TIMES));
        }
        playToAlive(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
        startCountdown--;
    }

    /** Ends the grace period: everyone is let loose and gets their first item. */
    private void release() {
        started = true;
        for (Player player : services.participants().onlinePlayers()) {
            player.setInvulnerable(false);
            player.setVelocity(new Vector());
            player.setFallDistance(0);
            player.showTitle(Title.title(
                    Component.text("GO!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    Component.text("Grab your items and start building!", NamedTextColor.GRAY), COUNTDOWN_TIMES));
        }
        playToAlive(Sound.ENTITY_PLAYER_LEVELUP, 0.75f, 1.2f);
        cycleItems();
    }

    /**
     * Hands everyone their items and restarts the countdown. Also runs the moment the
     * game begins, so the first items are there when players are let loose.
     */
    private void cycleItems() {
        if (mode.id.equals("item-shuffle")) {
            for (PillarPlayer pillarPlayer : alive) {
                Player player = pillarPlayer.player();
                if (player != null) {
                    player.getInventory().clear();
                    giveItems(pillarPlayer, 9);
                }
            }
        }
        for (PillarPlayer pillarPlayer : alive) {
            if (pillarPlayer.player() != null) {
                giveItems(pillarPlayer, 1);
            }
        }
        itemCountdown = mode.cooldownSeconds.get();
    }

    private void giveItems(PillarPlayer pillarPlayer, int count) {
        Player player = pillarPlayer.player();
        for (int i = 0; i < count; i++) {
            ItemPool.PoolItem chosen = ItemPool.randomByWeight(items, pillarPlayer.groups::weightOf);
            pillarPlayer.groups.record(chosen);
            ItemStack stack = chosen.createItemStack();
            player.getInventory().addItem(stack);
        }
        if (config.itemSound.get()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.75f, 1.0f);
        }
    }

    // --- what players see ---

    private Style keyStyle() {
        return Style.style(mode.accentColor(), TextDecoration.BOLD);
    }

    private void updateHud() {
        if (bossBar != null) {
            float progress = itemCountdownProgress();
            bossBar.name(Component.text("=== " + Math.max(0, itemCountdown) + "s ===").style(keyStyle()));
            bossBar.progress(progress);
            bossBar.color(barColor(progress));
        }
        if (!mode.scoreboard.get()) {
            return;
        }
        Component title = MiniMessage.miniMessage().deserialize("<bold><gradient:#71CCF8:#FC91EC:#F87171>Pillars");
        Style value = Style.style(NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false);
        for (PillarPlayer pillarPlayer : players.values()) {
            Player player = pillarPlayer.player();
            if (player == null || services.participants().find(pillarPlayer.id).isEmpty()) {
                continue;
            }
            services.sidebar().update(player, title, List.of(
                    Component.text("Mode: ").style(keyStyle()).append(Component.text(modeName()).style(value)),
                    Component.text("Name: ").style(keyStyle()).append(Component.text(pillarPlayer.name).style(value)),
                    Component.text("Time Left: ").style(keyStyle())
                            .append(Component.text(TimeFormat.clock(Math.max(0, timeLeft))).style(value)),
                    Component.text("Kills: ").style(keyStyle())
                            .append(Component.text(String.valueOf(pillarPlayer.kills)).style(value))));
        }
    }

    private float itemCountdownProgress() {
        float cooldown = Math.max(1, mode.cooldownSeconds.get() - 1);
        return Math.max(0f, Math.min(1f, Math.max(0, itemCountdown) / cooldown));
    }

    private static BossBar.Color barColor(float left) {
        if (left < 0.2) {
            return BossBar.Color.BLUE;
        } else if (left < 0.4) {
            return BossBar.Color.GREEN;
        } else if (left < 0.6) {
            return BossBar.Color.YELLOW;
        } else if (left < 0.8) {
            return BossBar.Color.RED;
        }
        return BossBar.Color.PINK;
    }

    /** The animated "until next item" line, for modes that show it. */
    private void tickActionBar() {
        if (ending || !started) {
            return;
        }
        actionBarPhase += 0.1;
        if (actionBarPhase > 1.0) {
            actionBarPhase -= 2.0;
        }
        boolean now = itemCountdown <= 0;
        List<TextColor> colors = now ? ITEM_NOW_COLORS : ITEM_TIME_COLORS;
        String text = now ? "New item(s)!" : itemCountdown + "s until next item(s)...";
        Component bar = MiniMessage.miniMessage().deserialize("<gradient:" + colors.get(0).asHexString() + ":"
                + colors.get(1).asHexString() + ":" + String.format(java.util.Locale.ROOT, "%.2f", actionBarPhase)
                + ">" + text + "</gradient>");
        for (PillarPlayer pillarPlayer : alive) {
            Player player = pillarPlayer.player();
            if (player != null) {
                player.sendActionBar(bar);
            }
        }
    }

    String modeName() {
        return switch (mode.id) {
            case "chaos" -> "Chaos";
            case "item-shuffle" -> "Item-Shuffle";
            default -> "Original";
        };
    }

    private void playToAlive(Sound sound, float volume, float pitch) {
        if (!config.soundEffects.get()) {
            return;
        }
        for (PillarPlayer pillarPlayer : alive) {
            Player player = pillarPlayer.player();
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private void announce(Component message) {
        services.messages().broadcast(message);
    }

    // --- eliminations ---

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        PillarPlayer victim = players.get(event.getPlayer().getUniqueId());
        // No longer count kills once the game has started ending.
        if (victim == null || ending || !alive.contains(victim)) {
            return;
        }
        Player killer = event.getPlayer().getKiller();
        if (killer != null) {
            PillarPlayer credited = players.get(killer.getUniqueId());
            if (credited != null) {
                credited.kills++;
            }
        }
        eliminate(victim);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        PillarPlayer pillarPlayer = players.get(event.getPlayer().getUniqueId());
        if (pillarPlayer == null || !alive.contains(pillarPlayer) || ending) {
            return;
        }
        if (!started) {
            // Frozen on their pillar until the countdown ends. Only horizontal movement is
            // held, so a player slightly above their pillar settles onto it.
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                Location held = event.getFrom().clone();
                held.setY(event.getTo().getY());
                held.setYaw(event.getTo().getYaw());
                held.setPitch(event.getTo().getPitch());
                event.setTo(held);
            }
            return;
        }
        if (event.getTo().getY() < config.deathHeight()) {
            event.getPlayer().setHealth(0.0);
        }
    }

    private void eliminate(PillarPlayer pillarPlayer) {
        if (ending || !alive.remove(pillarPlayer)) {
            return;
        }
        Player player = pillarPlayer.player();
        // An offline player has nothing to read here, but disconnecting already noted where they were.
        if (player != null) {
            pillarPlayer.lastLocation = player.getLocation().clone();
        }
        pillarPlayer.deathTick = Bukkit.getCurrentTick();
        updateHud();

        boolean win = alive.size() <= 1;
        List<PillarPlayer> survivors = List.copyOf(alive);
        Bukkit.getScheduler().runTaskLater(services.plugin(), () -> {
            if (!ending && win) {
                int lastDeath = players.values().stream().filter(p -> p.deathTick != null)
                        .mapToInt(p -> p.deathTick).max().orElse(0);
                List<PillarPlayer> drawWinners = players.values().stream()
                        .filter(p -> p.deathTick != null && p.deathTick + DEATH_DELAY_TICKS >= lastDeath)
                        .toList();
                if (config.enableDraws.get() && alive.isEmpty() && !drawWinners.isEmpty()) {
                    end(Ending.DRAW, drawWinners);
                } else {
                    end(Ending.LAST_STANDING, survivors);
                }
            }
            // Ending the game already sent everyone back, and an admin respawning them
            // within this very second has put them back on a pillar.
            if (!ending && !alive.contains(pillarPlayer)) {
                placeEliminated(pillarPlayer);
            }
        }, DEATH_DELAY_TICKS);
    }

    /** Eliminated players watch from above the arena until it is over. */
    private void placeEliminated(PillarPlayer pillarPlayer) {
        Player player = pillarPlayer.player();
        // Somebody who left the game in the meantime is no longer ours to move.
        if (player == null || services.participants().find(pillarPlayer.id).isEmpty()) {
            return;
        }
        if (player.isDead()) {
            player.spigot().respawn();
        }
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(center);
        player.sendMessage(Component.text()
                .append(Component.text("You are out! Watching until the game ends. ", NamedTextColor.GRAY))
                .append(Component.text("[Leave]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/play leave"))
                        .hoverEvent(HoverEvent.showText(Component.text("Go back now", NamedTextColor.GRAY))))
                .build());
    }

    /** Where the dead respawn (immediate respawn is on): above the arena, never the template's spawn. */
    Location respawnPoint() {
        return center;
    }

    boolean isParticipant(Player player) {
        return players.containsKey(player.getUniqueId());
    }

    boolean playsIn(World candidate) {
        return world != null && world.equals(candidate);
    }

    // --- admin: respawn and time ---

    /**
     * Puts a player who is out, or stuck, back into the game: for when the game itself
     * got somebody killed, like a death on the very tick it started. They come back
     * where they were, so whatever they built is still theirs to stand on.
     *
     * @return false if they were never part of this game, are offline, or it is over
     */
    boolean respawn(Player player) {
        PillarPlayer pillarPlayer = players.get(player.getUniqueId());
        if (ending || pillarPlayer == null || !player.isOnline()) {
            return false;
        }
        if (player.isDead()) {
            player.spigot().respawn();
        }
        Location destination = respawnLocation(pillarPlayer);
        if (!alive.contains(pillarPlayer)) {
            alive.add(pillarPlayer);
            pillarPlayer.deathTick = null;
            if (bossBar != null) {
                player.showBossBar(bossBar);
            }
        }
        pillarPlayer.disconnectedAt = null;
        player.setGameMode(GameMode.SURVIVAL);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        healFully(player);
        player.teleport(destination);
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        // Everybody else is frozen and protected until the grace period ends, so a late arrival is too.
        player.setInvulnerable(!started);
        announce(Component.text(player.getName() + " is back in the game!", NamedTextColor.GREEN));
        updateHud();
        return true;
    }

    /**
     * Where a respawning player belongs: where they died, if they can actually stand
     * there; otherwise back on their own pillar. Somebody killed while falling, or who
     * fell into the void, would only die again where they were.
     */
    private Location respawnLocation(PillarPlayer pillarPlayer) {
        Location last = pillarPlayer.lastLocation;
        if (last != null && last.getWorld() == world && last.getY() >= config.deathHeight() && canStandAt(last)) {
            return last;
        }
        return pillarPlayer.pillar != null ? pillarPlayer.pillar : center;
    }

    private static boolean canStandAt(Location location) {
        Block feet = location.getBlock();
        if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
            return false;
        }
        for (int depth = 1; depth <= 2; depth++) {
            if (feet.getRelative(0, -depth, 0).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    void addTime(long seconds) {
        timeLeft += seconds;
        announceTimeChange("The game time was extended by " + TimeFormat.words(seconds) + " - "
                + TimeFormat.words(timeLeft) + " left!");
    }

    void removeTime(long seconds) {
        // Clamped at zero, which ends the game on the next tick.
        timeLeft = Math.max(0, timeLeft - seconds);
        announceTimeChange("The game time was reduced by " + TimeFormat.words(seconds) + " - "
                + TimeFormat.words(timeLeft) + " left!");
    }

    void setTime(long seconds) {
        timeLeft = Math.max(0, seconds);
        announceTimeChange("The game time was set to " + TimeFormat.words(timeLeft) + "!");
    }

    private void announceTimeChange(String message) {
        announce(Component.text(message, NamedTextColor.YELLOW));
        playToAlive(Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.5f);
        updateHud();
    }

    long timeLeft() {
        return timeLeft;
    }

    List<Component> info() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Mode: " + modeName() + ", arena: " + arena.name(), NamedTextColor.GRAY));
        lines.add(Component.text("Time left: " + TimeFormat.words(timeLeft)
                + (started ? "" : ", starting in " + startCountdown + "s")
                + ", next item in " + Math.max(0, itemCountdown) + "s", NamedTextColor.GRAY));
        lines.add(Component.text("Item pool: " + items.size() + " item(s), total weight "
                + items.stream().mapToLong(ItemPool.PoolItem::weight).sum(), NamedTextColor.GRAY));
        lines.add(Component.text("Item groups: " + groups.size() + " (" + String.join(", ",
                groups.stream().map(ItemPool.ItemGroup::name).toList()) + ")", NamedTextColor.GRAY));
        List<Component> names = new ArrayList<>();
        for (PillarPlayer pillarPlayer : players.values()) {
            NamedTextColor color = !alive.contains(pillarPlayer) ? NamedTextColor.RED
                    : pillarPlayer.disconnectedAt != null ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
            names.add(Component.text(pillarPlayer.name, color));
        }
        lines.add(Component.text("Players: ", NamedTextColor.GRAY)
                .append(Component.join(net.kyori.adventure.text.JoinConfiguration.commas(true), names)));
        return lines;
    }

    // --- connections ---

    @Override
    public void onParticipantQuit(Player player) {
        PillarPlayer pillarPlayer = players.get(player.getUniqueId());
        if (pillarPlayer == null || ending || !alive.contains(pillarPlayer)) {
            return;
        }
        int grace = config.reconnectGraceSeconds.get();
        if (grace <= 0) {
            eliminate(pillarPlayer);
            return;
        }
        // They keep their spot until the grace period runs out.
        pillarPlayer.disconnectedAt = Bukkit.getCurrentTick();
        pillarPlayer.lastLocation = player.getLocation().clone();
        announce(Component.text(player.getName() + " disconnected and has " + TimeFormat.words(grace)
                + " to reconnect.", NamedTextColor.YELLOW));
    }

    @Override
    public void onParticipantRejoin(Player player) {
        PillarPlayer pillarPlayer = players.get(player.getUniqueId());
        if (pillarPlayer == null || ending) {
            return;
        }
        // Teleports right inside the join event are unreliable.
        Bukkit.getScheduler().runTask(services.plugin(), () -> {
            if (!player.isOnline() || ending) {
                return;
            }
            if (!alive.contains(pillarPlayer)) {
                // They ran out of time while away, so they only watch now.
                placeEliminated(pillarPlayer);
                return;
            }
            player.setInvulnerable(!started);
            if (bossBar != null) {
                player.showBossBar(bossBar);
            }
            // Logging back in below the death height would kill them at once.
            if (pillarPlayer.pillar != null && player.getLocation().getY() < config.deathHeight()) {
                player.teleport(pillarPlayer.pillar);
                player.setVelocity(new Vector());
                player.setFallDistance(0);
            }
            pillarPlayer.disconnectedAt = null;
            announce(Component.text(player.getName() + " is back in the game!", NamedTextColor.YELLOW));
            updateHud();
        });
    }

    @Override
    public void onParticipantLeave(Player player) {
        PillarPlayer pillarPlayer = players.get(player.getUniqueId());
        player.setInvulnerable(false);
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
        if (pillarPlayer != null && alive.contains(pillarPlayer)) {
            eliminate(pillarPlayer);
        }
    }

    /** Eliminates everyone whose reconnect grace period has run out. */
    private void tickDisconnects() {
        int grace = config.reconnectGraceSeconds.get();
        if (grace <= 0) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        for (PillarPlayer pillarPlayer : List.copyOf(alive)) {
            if (pillarPlayer.disconnectedAt != null && (now - pillarPlayer.disconnectedAt) / 20 >= grace) {
                announce(Component.text(pillarPlayer.name + " did not reconnect in time and is out.",
                        NamedTextColor.YELLOW));
                eliminate(pillarPlayer);
            }
        }
    }

    @Override
    public boolean isInGame(Player player) {
        PillarPlayer pillarPlayer = players.get(player.getUniqueId());
        return pillarPlayer != null && alive.contains(pillarPlayer);
    }

    @Override
    public Location spectatorLocation(Player viewer) {
        return center;
    }

    // --- the end ---

    private void end(Ending cause, List<PillarPlayer> winnerList) {
        if (ending) {
            return;
        }
        ending = true;
        endingCause = cause;
        winners = List.copyOf(winnerList);
        String winnerNames = String.join(" & ", winners.stream().map(p -> p.name).toList());
        int winnerKills = winners.stream().mapToInt(p -> p.kills).sum();
        Title title = switch (cause) {
            case FORCE -> Title.title(Component.text("Game forcefully ended!", NamedTextColor.YELLOW),
                    Component.text("It's forced, so there are no winners!", NamedTextColor.RED));
            case TIME_OVER -> Title.title(Component.text("Time's over!", NamedTextColor.GREEN),
                    Component.text("The game ends, because the time is over!", NamedTextColor.YELLOW));
            case LAST_STANDING -> Title.title(Component.text(winnerNames + " wins!", NamedTextColor.GREEN),
                    Component.text("With " + winnerKills + " kill(s)!", NamedTextColor.YELLOW));
            case DRAW -> Title.title(Component.text("Draw!", NamedTextColor.GREEN),
                    Component.text(winnerNames + " died at the same time!", NamedTextColor.YELLOW));
        };
        List<PillarPlayer> ranked = new ArrayList<>(players.values());
        ranked.sort(Comparator.comparingInt((PillarPlayer p) -> p.kills).reversed());
        for (Player player : services.participants().onlinePlayers()) {
            player.showTitle(title);
        }
        announce(Component.text("=== Top Players ===", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        for (int i = 0; i < ranked.size(); i++) {
            announce(MiniMessage.miniMessage().deserialize("<dark_gray>" + (i + 1) + ". <gray>" + ranked.get(i).name
                    + " <dark_gray>(<gold>" + ranked.get(i).kills + "<red>⚔<dark_gray>)"));
        }
        if (cause == Ending.LAST_STANDING || cause == Ending.DRAW) {
            for (PillarPlayer winner : winners) {
                services.points().add(game.id(), winner.id, 1);
            }
        }
        services.plugin().getLogger().info("Pillars ended (" + cause.name().toLowerCase() + ")"
                + (winners.isEmpty() ? "" : ", winners: " + winnerNames));
        services.session().endGame(EndReason.FINISHED);
    }

    @Override
    public void onEnd(EndReason reason) {
        if (!ending) {
            // An admin stopped it, or everybody left.
            end(Ending.FORCE, List.of());
        }
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
        if (bossBar != null) {
            services.bossBars().hide(bossBar);
        }
        for (Player player : services.participants().onlinePlayers()) {
            player.setInvulnerable(false);
        }
        game.clearActiveSession(this);
    }

    @Override
    public Component globalResult() {
        Component name = Component.text("Pillars", NamedTextColor.LIGHT_PURPLE);
        String winnerNames = String.join(" & ", winners.stream().map(p -> p.name).toList());
        if (endingCause == Ending.LAST_STANDING && !winners.isEmpty()) {
            return Component.text().append(Component.text(winnerNames, NamedTextColor.AQUA))
                    .append(Component.text(" won ", NamedTextColor.GREEN)).append(name).build();
        }
        if (endingCause == Ending.DRAW) {
            return Component.text().append(name)
                    .append(Component.text(" ended in a draw between " + winnerNames, NamedTextColor.GREEN)).build();
        }
        if (endingCause == Ending.TIME_OVER) {
            return Component.text().append(name)
                    .append(Component.text(" ended: time is over", NamedTextColor.GREEN)).build();
        }
        return null;
    }

    private static void healFully(Player player) {
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
    }
}
