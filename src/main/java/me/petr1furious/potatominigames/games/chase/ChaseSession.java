package me.petr1furious.potatominigames.games.chase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.text.TimeFormat;
import me.petr1furious.potatominigames.core.text.Summary;
import me.petr1furious.potatominigames.core.world.ArenaBiomes;
import me.petr1furious.potatominigames.gadgets.Gadget;
import me.petr1furious.potatominigames.gadgets.GadgetRegistry;
import me.petr1furious.potatominigames.gadgets.TrackerCompassGadget;

/** Formerly Ultimate Tag. One round per possible hunter set. */
final class ChaseSession implements GameSession {
    private static final int MAX_HUNTER_SETS = 100;
    private static final int ARENA_SPREAD = 5000;
    private static final int CORNER_INSET = 3;
    private static final int GROUND_SAMPLE_STEP = 4;
    /** Samples either side considered, so ground beside a cliff counts both levels. */
    private static final int GROUND_NEIGHBOURHOOD = 2;
    /** Taller than any generated tree. */
    private static final int MAX_TREE_HEIGHT = 40;
    private static final int ARENA_ATTEMPTS = 20;
    private static final int DRY_SEARCH_RADIUS = 12;

    private enum RoundPhase {
        NONE, FREEZE, HUNTING, INTERMISSION, WAITING_FOR_PLAYERS
    }

    private final SessionServices services;
    private final ChaseConfig config;
    private final GadgetRegistry gadgets;
    private final Random random = new Random();

    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Set<UUID> hunters = new HashSet<>();
    private final Set<UUID> caught = new HashSet<>();
    private final List<List<UUID>> hunterSets = new ArrayList<>();

    private World world;
    private int roundIndex = -1;
    private RoundPhase phase = RoundPhase.NONE;
    private int phaseSecondsLeft;
    private int roundLengthSeconds;
    private BossBar timerBar;
    private boolean ended = false;
    private EndReason endReason;
    private boolean waitingMessageSent = false;

    private Location hunterSpawn;
    private Location runnerSpawn;
    private int[][] groundSamples;
    private int groundOriginX;
    private int groundOriginZ;
    private final Map<UUID, Integer> outOfBounds = new HashMap<>();

    ChaseSession(SessionServices services, ChaseConfig config, GadgetRegistry gadgets) {
        this.services = services;
        this.config = config;
        this.gadgets = gadgets;
    }

    @Override
    public void onStart() throws CommandFailure {
        List<Player> roster = services.participants().onlinePlayers();
        if (roster.size() < 2) {
            throw new CommandFailure("Chase needs at least 2 players.");
        }
        buildHunterSets(roster);

        world = services.worlds().create("chase");
        // No natural mob spawns; time and weather frozen.
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setTime(1000L);
        world.setStorm(false);

        for (Player player : roster) {
            scores.put(player.getUniqueId(), 0);
        }

        timerBar = services.bossBars().create(Component.text("Chase", NamedTextColor.AQUA),
                BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        for (Player player : roster) {
            player.showBossBar(timerBar);
        }
        startNextRound();
    }

    private void buildHunterSets(List<Player> roster) throws CommandFailure {
        List<UUID> ids = new ArrayList<>();
        for (Player player : roster) {
            ids.add(player.getUniqueId());
        }
        int size = Math.max(1, Math.min(config.hunterCount.get(), ids.size() - 1));
        hunterSets.clear();
        collectSubsets(ids, size, 0, new ArrayList<>());
        if (hunterSets.size() > MAX_HUNTER_SETS) {
            throw new CommandFailure("Too many hunter combinations (" + hunterSets.size()
                    + "); raise hunterCount or use fewer players.");
        }
        Collections.shuffle(hunterSets, random);
    }

    private void collectSubsets(List<UUID> ids, int size, int index, List<UUID> current) {
        if (hunterSets.size() > MAX_HUNTER_SETS) {
            return;
        }
        if (current.size() == size) {
            hunterSets.add(List.copyOf(current));
            return;
        }
        if (index == ids.size()) {
            return;
        }
        current.add(ids.get(index));
        collectSubsets(ids, size, index + 1, current);
        current.removeLast();
        collectSubsets(ids, size, index + 1, current);
    }

    @Override
    public void onSecondTick(long secondsElapsed) {
        switch (phase) {
            case FREEZE -> {
                phaseSecondsLeft--;
                updateTimerBar(Component.text("Starting in " + phaseSecondsLeft + "s", NamedTextColor.YELLOW),
                        (float) phaseSecondsLeft / Math.max(1, config.freezeSeconds.get()));
                if (phaseSecondsLeft <= 0) {
                    beginHunting();
                }
            }
            case HUNTING -> {
                phaseSecondsLeft--;
                updateTimerBar(Component.text()
                        .append(Component.text("Round " + (roundIndex + 1) + "/" + hunterSets.size() + "  ",
                                NamedTextColor.AQUA))
                        .append(Component.text(TimeFormat.clock(phaseSecondsLeft), NamedTextColor.WHITE))
                        .build(), (float) phaseSecondsLeft / Math.max(1, roundLengthSeconds));
                enforceAltitude();
                if (phaseSecondsLeft <= 0) {
                    endRound();
                }
            }
            case INTERMISSION -> {
                phaseSecondsLeft--;
                updateTimerBar(Component.text("Next round in " + phaseSecondsLeft + "s", NamedTextColor.GRAY), 1.0f);
                if (phaseSecondsLeft <= 0) {
                    phase = RoundPhase.WAITING_FOR_PLAYERS;
                }
            }
            case WAITING_FOR_PLAYERS -> {
                if (everyoneOnline()) {
                    startNextRound();
                } else if (!waitingMessageSent) {
                    waitingMessageSent = true;
                    broadcast(Component.text("Waiting for players to return.", NamedTextColor.GOLD));
                    updateTimerBar(Component.text("Waiting for players…", NamedTextColor.GRAY), 1.0f);
                }
            }
            case NONE -> {
            }
        }
        updateSidebar();
    }

    private boolean everyoneOnline() {
        for (var participant : services.participants().all()) {
            if (!participant.isOnline()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onEnd(EndReason reason) {
        ended = true;
        endReason = reason;
        phase = RoundPhase.NONE;
        if (reason == EndReason.FINISHED) {
            for (UUID winner : topScorers()) {
                services.points().add("chase", winner, 1);
            }
        }
        if (world != null) {
            services.arena().restoreBorder(world);
        }
    }

    private void startNextRound() {
        roundIndex++;
        waitingMessageSent = false;
        if (roundIndex >= hunterSets.size()) {
            finishGame();
            return;
        }

        caught.clear();
        hunters.clear();
        hunters.addAll(hunterSets.get(roundIndex));

        placeArena();

        List<Player> online = services.participants().onlinePlayers();
        roundLengthSeconds = config.roundLengthFor(online.size());

        List<ItemStack> runnerTools = rollRunnerTools();
        for (Player player : online) {
            services.playerState().resetForRound(player, GameMode.SURVIVAL);
            boolean hunter = hunters.contains(player.getUniqueId());
            player.teleport(hunter ? hunterSpawn : runnerSpawn);
            if (hunter) {
                giveHunterKit(player);
                player.showTitle(Title.title(
                        Component.text("Round " + (roundIndex + 1), NamedTextColor.AQUA),
                        Component.text("Hunter", NamedTextColor.RED)));
            } else {
                giveRunnerKit(player, runnerTools);
                player.showTitle(Title.title(
                        Component.text("Round " + (roundIndex + 1), NamedTextColor.AQUA),
                        Component.text("Runner", NamedTextColor.GREEN)));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, hunter ? 0.7f : 1.4f);
        }

        broadcast(Component.text("Round " + (roundIndex + 1) + "/" + hunterSets.size()
                + " started", NamedTextColor.GOLD));
        broadcast(Component.text()
                .append(hunterNames())
                .append(Component.text(hunters.size() == 1 ? " is the hunter" : " are the hunters",
                        NamedTextColor.GOLD))
                .build());

        phase = RoundPhase.FREEZE;
        phaseSecondsLeft = Math.max(1, config.freezeSeconds.get());
    }

    /**
     * New terrain each round. Corners are checked separately from the centre (biome
     * first, then blocks), since a coastal centre can put a corner at sea.
     */
    private void placeArena() {
        int border = Math.max(16, config.borderSize.get());
        int half = border / 2 - CORNER_INSET;

        int centerX = 0;
        int centerZ = 0;
        for (int attempt = 1; attempt <= ARENA_ATTEMPTS; attempt++) {
            int wantX = random.nextInt(ARENA_SPREAD * 2 + 1) - ARENA_SPREAD;
            int wantZ = random.nextInt(ARENA_SPREAD * 2 + 1) - ARENA_SPREAD;
            Location center = ArenaBiomes.findSuitableNear(world, wantX, wantZ, ARENA_SPREAD);
            centerX = center.getBlockX();
            centerZ = center.getBlockZ();
            boolean last = attempt == ARENA_ATTEMPTS;
            if (!last && !(suitableAt(centerX - half, centerZ - half) && suitableAt(centerX + half, centerZ + half))) {
                continue;
            }
            hunterSpawn = cornerSpawn(centerX - half, centerZ - half, centerX, centerZ, border);
            runnerSpawn = cornerSpawn(centerX + half, centerZ + half, centerX, centerZ, border);
            if (hunterSpawn != null && runnerSpawn != null) {
                break;
            }
            if (last) {
                services.plugin().getLogger().warning("No dry chase arena in " + ARENA_ATTEMPTS
                        + " tries; spawning at " + centerX + "," + centerZ + " anyway");
                if (hunterSpawn == null) {
                    hunterSpawn = world.getHighestBlockAt(centerX - half, centerZ - half).getLocation().add(0.5, 2, 0.5);
                }
                if (runnerSpawn == null) {
                    runnerSpawn = world.getHighestBlockAt(centerX + half, centerZ + half).getLocation().add(0.5, 2, 0.5);
                }
            }
        }

        services.arena().applyBorder(world, new Location(world, centerX, 0, centerZ), border, border, 0);

        sampleGround(centerX, centerZ, border);
        outOfBounds.clear();
    }

    private boolean suitableAt(int x, int z) {
        return ArenaBiomes.isSuitable(world.getBiome(x, world.getSeaLevel(), z));
    }

    /** Nearest dry column inside the border, preferring ground over treetops; null if none. */
    private Location cornerSpawn(int x, int z, int centerX, int centerZ, int border) {
        int reach = border / 2 - 1;
        Block treetop = null;
        for (int radius = 0; radius <= DRY_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue; // ring only
                    }
                    if (Math.abs(x + dx - centerX) > reach || Math.abs(z + dz - centerZ) > reach) {
                        continue; // outside the border
                    }
                    Block top = world.getHighestBlockAt(x + dx, z + dz);
                    if (top.isLiquid()) {
                        continue;
                    }
                    if (!Tag.LEAVES.isTagged(top.getType())) {
                        return top.getLocation().add(0.5, 2, 0.5);
                    }
                    if (treetop == null) {
                        treetop = top;
                    }
                }
            }
        }
        return treetop == null ? null : treetop.getLocation().add(0.5, 2, 0.5);
    }

    /** Taken before building starts, so player towers cannot raise the band. */
    private void sampleGround(int centerX, int centerZ, int border) {
        long startedAt = System.currentTimeMillis();
        groundOriginX = centerX - border / 2;
        groundOriginZ = centerZ - border / 2;
        int size = border / GROUND_SAMPLE_STEP + 1;
        groundSamples = new int[size][size];
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int y = terrainY(groundOriginX + i * GROUND_SAMPLE_STEP, groundOriginZ + j * GROUND_SAMPLE_STEP);
                groundSamples[i][j] = y;
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y);
            }
        }
        services.plugin().getLogger().info("Chase arena at " + centerX + "," + centerZ + " sampled "
                + (size * size) + " ground points (y " + lowest + ".." + highest + ") in "
                + (System.currentTimeMillis() - startedAt) + "ms");
    }

    /** Surface height ignoring trees; a canopy would otherwise lift the band off the forest floor. */
    private int terrainY(int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int stop = Math.max(world.getMinHeight(), y - MAX_TREE_HEIGHT);
        while (y > stop && isTreePart(world.getBlockAt(x, y, z).getType())) {
            y--;
        }
        return y;
    }

    /** Leaves are already excluded by the height map. */
    private static boolean isTreePart(Material type) {
        return Tag.LOGS.isTagged(type)
                || type == Material.MUSHROOM_STEM
                || type == Material.BROWN_MUSHROOM_BLOCK
                || type == Material.RED_MUSHROOM_BLOCK
                || type == Material.BAMBOO
                || type == Material.CACTUS
                || type == Material.BEE_NEST
                || type == Material.MANGROVE_ROOTS;
    }

    /** Min/max over neighbouring samples; a single sample misjudges cliffs and ravines. */
    private int[] groundRangeAt(int x, int z) {
        if (groundSamples == null) {
            return new int[] { world.getSeaLevel(), world.getSeaLevel() };
        }
        int last = groundSamples.length - 1;
        int ci = Math.max(0, Math.min(last, Math.round((x - groundOriginX) / (float) GROUND_SAMPLE_STEP)));
        int cj = Math.max(0, Math.min(last, Math.round((z - groundOriginZ) / (float) GROUND_SAMPLE_STEP)));
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int i = Math.max(0, ci - GROUND_NEIGHBOURHOOD); i <= Math.min(last, ci + GROUND_NEIGHBOURHOOD); i++) {
            for (int j = Math.max(0, cj - GROUND_NEIGHBOURHOOD); j <= Math.min(last, cj + GROUND_NEIGHBOURHOOD); j++) {
                low = Math.min(low, groundSamples[i][j]);
                high = Math.max(high, groundSamples[i][j]);
            }
        }
        return new int[] { low, high };
    }

    private void beginHunting() {
        phase = RoundPhase.HUNTING;
        phaseSecondsLeft = roundLengthSeconds;
        broadcast(Component.text("Hunt started", NamedTextColor.RED));
        services.messages().playSound(services.participants().onlinePlayers(),
                Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
    }

    private List<ItemStack> rollRunnerTools() {
        List<ItemStack> pool = new ArrayList<>(List.of(
                unbreaking(new ItemStack(Material.WOODEN_PICKAXE)),
                unbreaking(new ItemStack(Material.WOODEN_AXE)),
                unbreaking(new ItemStack(Material.STONE_SHOVEL)),
                new ItemStack(Material.COBBLESTONE, 16)));
        Collections.shuffle(pool, random);
        return List.of(pool.get(0), pool.get(1));
    }

    private static ItemStack unbreaking(ItemStack item) {
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return item;
    }

    private void giveHunterKit(Player player) {
        var inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Material.DIAMOND_AXE));
        inventory.setItem(1, new ItemStack(Material.DIAMOND_PICKAXE));
        inventory.setItem(2, new ItemStack(Material.DIAMOND_SHOVEL));
        inventory.setItem(3, new ItemStack(Material.COBBLESTONE, 64));
        inventory.setItem(4, new ItemStack(Material.COOKED_BEEF, 16));
        Gadget tracker = gadgets.get(TrackerCompassGadget.ID);
        if (tracker != null) {
            inventory.setItem(8, tracker.createItem(1));
        }

        int slot = 5;
        for (String gadgetId : config.hunterGadgets.get().split(",")) {
            String trimmed = gadgetId.trim();
            if (trimmed.isEmpty() || slot > 7) {
                continue;
            }
            Gadget gadget = gadgets.get(trimmed);
            if (gadget != null) {
                inventory.setItem(slot++, gadget.createItem(1));
            }
        }
    }

    private void giveRunnerKit(Player player, List<ItemStack> tools) {
        var inventory = player.getInventory();
        inventory.setItem(0, tools.get(0).clone());
        inventory.setItem(1, tools.get(1).clone());
        inventory.setItem(2, new ItemStack(Material.COOKED_BEEF, 16));
    }

    private void catchPlayer(Player player, Component reason) {
        if (!caught.add(player.getUniqueId())) {
            return;
        }
        services.playerState().makeSpectator(player, 0);
        broadcast(reason);
        services.messages().playSound(services.participants().onlinePlayers(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 0.6f);
        checkRoundEnd();
    }

    private List<Player> aliveRunners() {
        List<Player> result = new ArrayList<>();
        for (Player player : services.participants().onlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!hunters.contains(id) && !caught.contains(id)) {
                result.add(player);
            }
        }
        return result;
    }

    private List<Player> aliveHunters() {
        List<Player> result = new ArrayList<>();
        for (Player player : services.participants().onlinePlayers()) {
            UUID id = player.getUniqueId();
            if (hunters.contains(id) && !caught.contains(id)) {
                result.add(player);
            }
        }
        return result;
    }

    private void checkRoundEnd() {
        if (phase != RoundPhase.HUNTING && phase != RoundPhase.FREEZE) {
            return;
        }
        if (aliveRunners().isEmpty() || aliveHunters().isEmpty()) {
            endRound();
        }
    }

    private void endRound() {
        // Survivors score once per hunter; hunters score per catch.
        int hunterCount = hunters.size();
        Map<UUID, Integer> earned = new HashMap<>();
        int caughtRunners = 0;
        for (Player player : services.participants().onlinePlayers()) {
            UUID id = player.getUniqueId();
            if (hunters.contains(id)) {
                continue;
            }
            if (caught.contains(id)) {
                caughtRunners++;
            } else {
                earned.merge(id, hunterCount, Integer::sum);
            }
        }
        for (UUID hunter : hunters) {
            earned.merge(hunter, caughtRunners, Integer::sum);
        }
        earned.forEach((id, gained) -> scores.merge(id, gained, Integer::sum));

        int survivors = aliveRunners().size();
        broadcast(Component.text()
                .append(Component.text("Round " + (roundIndex + 1) + " over: ", NamedTextColor.GOLD))
                .append(Component.text(survivors + " runner(s) survived", NamedTextColor.GREEN))
                .build());
        announceStandings("Current scores:");

        phase = RoundPhase.INTERMISSION;
        phaseSecondsLeft = Math.max(1, config.intermissionSeconds.get());
    }

    private void finishGame() {
        phase = RoundPhase.NONE;
        if (!ended) {
            services.session().endGame(EndReason.FINISHED);
        }
    }

    private List<UUID> topScorers() {
        int best = 0;
        for (int score : scores.values()) {
            best = Math.max(best, score);
        }
        List<UUID> winners = new ArrayList<>();
        if (best <= 0) {
            return winners;
        }
        for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
            if (entry.getValue() == best) {
                winners.add(entry.getKey());
            }
        }
        return winners;
    }

    @Override
    public List<Component> summary() {
        List<Component> lines = new ArrayList<>();
        List<UUID> winners = endReason == EndReason.FINISHED ? topScorers() : List.of();
        if (!winners.isEmpty()) {
            List<Component> names = new ArrayList<>();
            for (UUID id : winners) {
                names.add(Component.text(nameOf(id), NamedTextColor.WHITE));
            }
            lines.add(Summary.winners(names));
            lines.add(Component.empty());
        }
        lines.add(Component.text("Scores", NamedTextColor.GRAY));
        int place = 1;
        for (Map.Entry<UUID, Integer> entry : sortedScores()) {
            lines.add(Summary.rank(place++, Component.text(nameOf(entry.getKey()), NamedTextColor.GRAY),
                    Component.text(entry.getValue(), NamedTextColor.GOLD)));
        }
        return lines;
    }

    private List<Map.Entry<UUID, Integer>> sortedScores() {
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        return sorted;
    }

    private void announceStandings(String header) {
        broadcast(Component.text(header, NamedTextColor.GOLD));
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        int place = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            broadcast(Component.text(place + ". ", NamedTextColor.GOLD)
                    .append(Component.text(nameOf(entry.getKey()),
                            hunters.contains(entry.getKey()) ? NamedTextColor.RED : NamedTextColor.AQUA))
                    .append(Component.text(" — " + entry.getValue(), NamedTextColor.GREEN)));
            place++;
        }
    }

    private String nameOf(UUID id) {
        return services.participants().find(id).map(participant -> participant.name()).orElse("?");
    }

    private Component hunterNames() {
        List<Component> names = new ArrayList<>();
        for (UUID id : hunters) {
            names.add(Component.text(nameOf(id), NamedTextColor.RED));
        }
        return Component.join(JoinConfiguration.commas(true), names);
    }

    @Override
    public Collection<Player> getTrackedPlayers(Player viewer) {
        return hunters.contains(viewer.getUniqueId()) ? aliveRunners() : List.of();
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = event.getDamager() instanceof Player player ? player
                : event.getDamager() instanceof Projectile projectile
                        && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (attacker == null || !services.participants().isParticipant(attacker)
                || !services.participants().isParticipant(victim)) {
            return;
        }
        boolean attackerHunter = hunters.contains(attacker.getUniqueId());
        boolean victimHunter = hunters.contains(victim.getUniqueId());
        if (attackerHunter == victimHunter || phase != RoundPhase.HUNTING) {
            event.setCancelled(true);
            return;
        }
        // Runner hits deal damage but do not tag.
        if (!attackerHunter) {
            return;
        }
        catchPlayer(victim, Component.text()
                .append(Component.text(attacker.getName(), NamedTextColor.RED))
                .append(Component.text(" tagged ", NamedTextColor.GOLD))
                .append(Component.text(victim.getName(), NamedTextColor.AQUA))
                .build());
    }

    /** Death counts as caught, for both sides. */
    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!services.participants().isParticipant(player)) {
            return;
        }
        event.setCancelled(true);
        player.setHealth(20.0);
        if (phase != RoundPhase.HUNTING && phase != RoundPhase.FREEZE) {
            return;
        }
        catchPlayer(player, Component.text()
                .append(Component.text(player.getName(),
                        hunters.contains(player.getUniqueId()) ? NamedTextColor.RED : NamedTextColor.AQUA))
                .append(Component.text(" died", NamedTextColor.GOLD))
                .build());
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (phase != RoundPhase.FREEZE || !services.participants().isParticipant(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
            event.setTo(from);
        }
    }

    /** Out of band: warn, then glow, then damage. Blocks are never refused, so nobody gets sealed in. */
    private void enforceAltitude() {
        int glowAfter = Math.max(1, config.glowAfterSeconds.get());
        int damageAfter = Math.max(glowAfter, config.damageAfterSeconds.get());

        for (Player player : services.participants().onlinePlayers()) {
            UUID id = player.getUniqueId();
            if (caught.contains(id)) {
                clearOutOfBounds(player);
                continue;
            }
            Location at = player.getLocation();
            int[] ground = groundRangeAt(at.getBlockX(), at.getBlockZ());
            boolean tooLow = at.getBlockY() < ground[0] - config.allowedDepth.get();
            boolean tooHigh = at.getBlockY() > ground[1] + config.allowedHeight.get();
            if (!tooLow && !tooHigh) {
                clearOutOfBounds(player);
                continue;
            }

            int seconds = outOfBounds.merge(id, 1, Integer::sum);
            String heading = tooLow ? "Too low." : "Too high.";
            if (seconds >= glowAfter) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
            }
            if (seconds >= damageAfter) {
                player.damage(2.0);
                player.sendActionBar(Component.text(heading + " Return to the arena.", NamedTextColor.RED));
            } else if (seconds >= glowAfter) {
                player.sendActionBar(Component.text(heading + " You are glowing.",
                        NamedTextColor.RED));
            } else {
                player.sendActionBar(Component.text(heading + " Return to the arena within "
                        + (glowAfter - seconds) + "s", NamedTextColor.YELLOW));
            }
        }
    }

    private void clearOutOfBounds(Player player) {
        if (outOfBounds.remove(player.getUniqueId()) != null) {
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
    }

    // The round schedule is fixed at start, so the roster is too.

    @Override
    public void onParticipantQuit(Player player) {
        caught.add(player.getUniqueId());
        checkRoundEnd();
        updateSidebar();
    }

    @Override
    public boolean allowLeave() {
        return false;
    }

    @Override
    public void onParticipantRejoin(Player player) {
        if (timerBar != null) {
            player.showBossBar(timerBar);
        }
        if (phase == RoundPhase.HUNTING || phase == RoundPhase.FREEZE) {
            // Marked caught on quit; back in next round.
            services.playerState().makeSpectator(player, 0);
            player.teleport(runnerSpawn != null ? runnerSpawn : world.getSpawnLocation());
        }
        updateSidebar();
    }

    private void updateTimerBar(Component name, float progress) {
        if (timerBar != null) {
            timerBar.name(name);
            timerBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
        }
    }

    private void updateSidebar() {
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text()
                .append(Component.text("Round: ", NamedTextColor.GOLD))
                .append(Component.text(Math.min(roundIndex + 1, hunterSets.size()) + "/" + hunterSets.size(),
                        NamedTextColor.AQUA))
                .build());
        lines.add(Component.text()
                .append(Component.text("Runners left: ", NamedTextColor.GOLD))
                .append(Component.text(aliveRunners().size(), NamedTextColor.GREEN))
                .build());
        lines.add(Component.text(" "));
        for (Map.Entry<UUID, Integer> entry : sorted) {
            if (lines.size() >= 15) {
                break;
            }
            boolean isHunter = hunters.contains(entry.getKey());
            lines.add(Component.text()
                    .append(Component.text(nameOf(entry.getKey()),
                            isHunter ? NamedTextColor.RED : NamedTextColor.AQUA))
                    .append(Component.text(": " + entry.getValue(), NamedTextColor.WHITE))
                    .build());
        }
        for (Player player : services.participants().onlinePlayers()) {
            services.sidebar().update(player, Component.text("Chase", NamedTextColor.AQUA), lines);
        }
    }

    private void broadcast(Component message) {
        services.messages().broadcast(message);
    }
}
