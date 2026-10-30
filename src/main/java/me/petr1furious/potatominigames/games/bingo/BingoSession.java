package me.petr1furious.potatominigames.games.bingo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.player.Participant;
import me.petr1furious.potatominigames.core.player.PlayerStateService;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.core.text.Summary;
import me.petr1furious.potatominigames.core.text.TimeFormat;
import me.petr1furious.potatominigames.core.world.ArenaBiomes;
import me.petr1furious.potatominigames.games.bingo.board.BoardService;
import me.petr1furious.potatominigames.games.bingo.board.BoardUiService;
import me.petr1furious.potatominigames.games.bingo.board.ItemPoolService;
import me.petr1furious.potatominigames.games.bingo.model.BoardEntry;
import me.petr1furious.potatominigames.games.bingo.model.GameDifficulty;
import me.petr1furious.potatominigames.games.bingo.model.GameMode;
import me.petr1furious.potatominigames.games.bingo.model.TeamState;

final class BingoSession implements GameSession {

    private record Punishment(BukkitTask releaseTask, long releaseAtMillis, Location deathLocation,
            ItemStack[] contents, ItemStack[] armor, ItemStack offHand) {
        int remainingTicks() {
            long remainingMillis = Math.max(0L, releaseAtMillis - System.currentTimeMillis());
            return (int) Math.ceil(remainingMillis / 50.0d);
        }
    }

    private final SessionServices services;
    private final BingoConfig config;
    private final ItemPoolService itemPool;
    private final BoardService boardService;
    private final BoardUiService boardUi;
    private final BingoKit kit;

    private final GameMode mode;
    private GameDifficulty difficulty;
    private BoardService.Board board;
    private final Map<TeamColor, TeamState> teamStates = new EnumMap<>(TeamColor.class);
    /** Lockout: the team that collected each cell first; it alone holds it. */
    private final TeamColor[] owners = new TeamColor[25];
    private final Map<UUID, Punishment> punishments = new HashMap<>();

    private long elapsedSeconds;
    private int finishSeconds;
    private int butfastRewardTeamCount = 1;
    private boolean preStartLocked = false;
    private int dropRemaining;
    private int endGraceRemaining = -1;
    private boolean ended = false;
    private final List<Component> summary = new ArrayList<>();

    private World arenaWorld;
    private int arenaX;
    private int arenaZ;

    /** Start option overriding the configured difficulty; the queue passes it too. */
    static final String DIFFICULTY_OPTION = "difficulty";

    BingoSession(SessionServices services, BingoConfig config, ItemPoolService itemPool,
            BoardService boardService, BoardUiService boardUi, BingoKit kit, GameMode mode) {
        this.services = services;
        this.mode = mode;
        this.config = config;
        this.itemPool = itemPool;
        this.boardService = boardService;
        this.boardUi = boardUi;
        this.kit = kit;
    }

    private TeamColor teamOf(Player player) {
        return services.participants().find(player.getUniqueId()).map(Participant::team).orElse(null);
    }

    private TeamState stateOf(TeamColor color) {
        return color == null ? null : teamStates.get(color);
    }

    @Override
    public void onStart() throws CommandFailure {
        String forced = services.option(DIFFICULTY_OPTION);
        difficulty = forced != null ? config.requireDifficulty(forced) : config.gameDifficulty();

        // Colours were already resolved when the session started.
        teamStates.clear();
        for (Participant participant : services.participants().all()) {
            TeamColor color = participant.team();
            if (color == null) {
                color = services.teams().resolveForStart("bingo", participant);
            }
            teamStates.computeIfAbsent(color, TeamState::new).addMember(participant.name());
        }
        if (teamStates.isEmpty()) {
            throw new CommandFailure("No active teams.");
        }

        try {
            board = boardService.generateBoard(mode, difficulty, itemPool.entries()).board();
        } catch (IllegalStateException exception) {
            throw new CommandFailure(exception.getMessage());
        }

        finishSeconds = difficulty.durationFor(mode);
        butfastRewardTeamCount = Math.max(1, teamStates.size());
        elapsedSeconds = 0;
        endGraceRemaining = -1;

        selectArena();
        buildCage();
        preStartLocked = true;
        dropRemaining = Math.max(0, config.countdownSeconds.get());

        for (Player player : services.participants().onlinePlayers()) {
            preparePlayerForRound(player);
            player.teleport(new Location(arenaWorld, arenaX + 0.5, 221, arenaZ + 0.5));
            TeamColor color = teamOf(player);
            if (color != null) {
                services.sidebar().setNameColor(player, color);
            }
        }

        broadcast(Component.text("Game started: ", NamedTextColor.GREEN)
                .append(Component.text("mode ", NamedTextColor.GREEN))
                .append(Component.text(mode.key(), NamedTextColor.GOLD))
                .append(Component.text(", difficulty ", NamedTextColor.GREEN))
                .append(Component.text(difficulty.key(), NamedTextColor.GOLD)));

        if (dropRemaining <= 0) {
            openFloorNow();
        }
        updateSidebar();
    }

    @Override
    public void onSecondTick(long seconds) {
        elapsedSeconds = seconds;

        if (preStartLocked) {
            dropRemaining--;
            if (dropRemaining <= 0) {
                openFloorNow();
            } else {
                broadcastParticipantsActionbar(
                        Component.text("Dropping in " + dropRemaining + "s", NamedTextColor.YELLOW));
                playParticipantsSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
            }
        }

        runPeriodicPlayerChecks();

        if (endGraceRemaining >= 0) {
            endGraceRemaining--;
            if (endGraceRemaining <= 0) {
                triggerEnd();
                return;
            }
            broadcastParticipantsActionbar(
                    Component.text("Game ending in " + endGraceRemaining + "s", NamedTextColor.YELLOW));
            playParticipantsSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.8f);
        } else if (finishSeconds > 0 && elapsedSeconds >= finishSeconds) {
            triggerEnd();
            return;
        } else {
            String warning = timeWarningMessage();
            if (warning != null) {
                broadcastParticipantsActionbar(Component.text(warning, NamedTextColor.RED));
                playParticipantsSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.9f);
            }
        }

        updateSidebar();
    }

    private void openFloorNow() {
        preStartLocked = false;
        openCageFloor();
        broadcastParticipantsActionbar(Component.text("Go", NamedTextColor.GREEN));
        playParticipantsSound(Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.1f);
    }

    private void triggerEnd() {
        if (!ended) {
            services.session().endGame(EndReason.FINISHED);
        }
    }

    @Override
    public void onEnd(EndReason reason) {
        ended = true;

        List<TeamState> ranking = new ArrayList<>(teamStates.values());
        ranking.sort(teamRankingComparator());
        int points = Math.max(0, ranking.size() - 1);
        for (TeamState state : ranking) {
            for (Participant participant : services.participants().all()) {
                if (participant.team() == state.color()) {
                    services.award("bingo", participant.id(), points);
                }
            }
            if (points > 0) {
                points--;
            }
        }

        summary.clear();
        if (!ranking.isEmpty()) {
            summary.add(Component.text("Ranking", NamedTextColor.GRAY));
            int place = 1;
            for (TeamState state : ranking) {
                summary.add(Summary.rank(place, teamDisplayName(state), teamResultComponent(state)));
                place++;
            }
        }

        for (Punishment punishment : punishments.values()) {
            punishment.releaseTask().cancel();
        }
        punishments.clear();
        playParticipantsSound(Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.0f);
        // No cage teardown: the arena world is deleted after cleanup.
    }

    @Override
    public List<Component> summary() {
        return List.copyOf(summary);
    }

    @Override
    public boolean allowLateJoin() {
        return true;
    }

    @Override
    public void onParticipantJoin(Player player) {
        TeamColor color = teamOf(player);
        if (color == null) {
            color = services.teams().resolveForStart("bingo",
                    services.participants().find(player.getUniqueId()).orElseThrow());
        }
        teamStates.computeIfAbsent(color, TeamState::new).addMember(player.getName());
        services.sidebar().setNameColor(player, color);
        preparePlayerForRound(player);
        // The cage is gone; spawn at the arena surface.
        player.teleport(new Location(arenaWorld, arenaX + 0.5,
                arenaWorld.getHighestBlockYAt(arenaX, arenaZ) + 1.0, arenaZ + 0.5));
        butfastRewardTeamCount = Math.max(butfastRewardTeamCount, activeTeams().size());
        updateSidebar();
        refreshOpenBoards();
    }

    @Override
    public void onParticipantQuit(Player player) {
        clearPunishment(player, false);
        reconcileAfterRosterChange();
        updateSidebar();
    }

    @Override
    public void onParticipantLeave(Player player) {
        clearPunishment(player, false);
        TeamColor color = teamOf(player);
        if (color != null) {
            TeamState state = teamStates.get(color);
            if (state != null) {
                state.removeMember(player.getName());
            }
        }
        reconcileAfterRosterChange();
        updateSidebar();
    }

    @Override
    public void onParticipantRejoin(Player player) {
        Punishment punishment = punishments.get(player.getUniqueId());
        TeamState state = stateOf(teamOf(player));
        if (punishment != null || (state != null && state.finished())) {
            services.playerState().makeSpectator(player, 0);
            player.teleport(spectatorLocation());
            int remainingTicks = punishment == null ? 0 : punishment.remainingTicks();
            if (punishment != null && remainingTicks > 0) {
                player.addPotionEffect(
                        new PotionEffect(PotionEffectType.BLINDNESS, remainingTicks + 40, 1, false, false, false));
            }
        } else {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            applySpeedBonus(player, player.getInventory().getHeldItemSlot());
            runPeriodicPlayerChecks(player);
        }
        updateSidebar();
    }

    private void reconcileAfterRosterChange() {
        List<TeamState> active = activeTeams();
        butfastRewardTeamCount = Math.max(1, active.size());
        if (active.isEmpty()) {
            if (!ended) {
                services.session().endGame(EndReason.ALL_PLAYERS_LEFT);
            }
            return;
        }
        checkRoundCompletion(active);
    }

    private List<TeamState> activeTeams() {
        List<TeamState> result = new ArrayList<>();
        for (TeamState state : teamStates.values()) {
            for (Player player : services.participants().onlinePlayers()) {
                if (teamOf(player) == state.color()) {
                    result.add(state);
                    break;
                }
            }
        }
        return result;
    }

    private void onItemProgress(Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        if (boardUi.isBingoCard(stack) || kit.isStarterItem(stack)) {
            return;
        }
        onItemProgress(player, stack.getType());
    }

    private void onItemProgress(Player player, Material material) {
        if (!services.session().isRunning() || board == null || ended) {
            return;
        }
        if (!services.participants().isParticipant(player)) {
            return;
        }
        TeamState team = stateOf(teamOf(player));
        if (team == null) {
            return;
        }

        boolean changed = false;
        for (int i = 0; i < board.entries().length; i++) {
            BoardEntry target = board.entries()[i];
            if (target == null || !target.matches(material) || team.collected()[i]) {
                continue;
            }
            if (mode == GameMode.LOCKOUT && owners[i] != null) {
                continue;
            }
            int reward = mode == GameMode.BUTFAST ? rewardForIndex(i) : 0;
            team.collected()[i] = true;
            owners[i] = team.color();
            changed = true;

            if (mode == GameMode.BUTFAST) {
                team.addScore(reward);
            }
        }

        if (!changed) {
            return;
        }

        if (mode != GameMode.BUTFAST) {
            team.setScore(computeScore(team));
        }

        broadcast(teamCollectedMessage(player.getName(), team, prettify(material.name())));
        playCollectionSounds(player);
        checkFinish(team);
        updateSidebar();
        refreshOpenBoards();
    }

    private int rewardForIndex(int index) {
        int teamsCollected = 0;
        for (TeamState state : teamStates.values()) {
            if (state.collected()[index]) {
                teamsCollected++;
            }
        }
        return Math.max(1, Math.max(1, butfastRewardTeamCount) - teamsCollected);
    }

    private void checkFinish(TeamState team) {
        boolean won = mode.lineBoard() ? computeBestLine(team.collected()) >= 5
                : allRequiredCollected(team.collected());

        if (won && !team.finished()) {
            team.setFinished(true);
            team.setFinishedAtSeconds((int) elapsedSeconds);
            broadcast(team.color().displayNameComponent()
                    .append(Component.text(mode == GameMode.LOCKOUT ? " got a bingo line!" : " has completed the board.",
                            NamedTextColor.GOLD)));
            moveFinishedTeamToSpectators(team.color());
            playParticipantsSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        checkRoundCompletion(activeTeams());
    }

    private void checkRoundCompletion(List<TeamState> active) {
        if (active.isEmpty() || endGraceRemaining >= 0 || ended) {
            return;
        }
        long finished = active.stream().filter(TeamState::finished).count();
        boolean shouldEnd = switch (mode) {
            case BUTFAST -> finished == active.size();
            // A line wins outright; with every cell taken, the most cells win.
            case LOCKOUT -> finished > 0 || allRequiredCollected(claimedCells());
            default -> finished >= Math.max(1, active.size() - 1);
        };
        if (!shouldEnd) {
            return;
        }
        int graceSeconds = Math.max(0, config.afterGameSeconds.get());
        if (graceSeconds == 0) {
            triggerEnd();
        } else {
            endGraceRemaining = graceSeconds;
            broadcastParticipantsActionbar(
                    Component.text("Game ending in " + endGraceRemaining + "s", NamedTextColor.YELLOW));
            playParticipantsSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.8f);
        }
    }

    private int computeScore(TeamState team) {
        if (mode == GameMode.DEFAULT) {
            return computeBestLine(team.collected());
        }
        int sum = 0;
        for (int i = 0; i < team.collected().length; i++) {
            if (board.entries()[i] != null && team.collected()[i]) {
                sum++;
            }
        }
        return sum;
    }

    private int computeBestLine(boolean[] collected) {
        int[][] lines = {
                { 0, 1, 2, 3, 4 },
                { 5, 6, 7, 8, 9 },
                { 10, 11, 12, 13, 14 },
                { 15, 16, 17, 18, 19 },
                { 20, 21, 22, 23, 24 },
                { 0, 5, 10, 15, 20 },
                { 1, 6, 11, 16, 21 },
                { 2, 7, 12, 17, 22 },
                { 3, 8, 13, 18, 23 },
                { 4, 9, 14, 19, 24 },
                { 0, 6, 12, 18, 24 },
                { 4, 8, 12, 16, 20 }
        };

        int best = 0;
        for (int[] line : lines) {
            int value = 0;
            for (int idx : line) {
                if (board.entries()[idx] != null && collected[idx]) {
                    value++;
                }
            }
            best = Math.max(best, value);
        }
        return best;
    }

    /** Lockout: whether each cell has an owner. */
    private boolean[] claimedCells() {
        boolean[] claimed = new boolean[owners.length];
        for (int i = 0; i < owners.length; i++) {
            claimed[i] = owners[i] != null;
        }
        return claimed;
    }

    private boolean allRequiredCollected(boolean[] collected) {
        for (int i = 0; i < collected.length; i++) {
            if (board.entries()[i] != null && !collected[i]) {
                return false;
            }
        }
        return true;
    }

    private void moveFinishedTeamToSpectators(TeamColor color) {
        for (Player player : services.participants().onlinePlayers()) {
            if (teamOf(player) == color) {
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                player.setFlying(false);
            }
        }
    }

    private boolean shouldCancelLethalDamage(Player player, double finalDamage) {
        if (ended || !services.participants().isParticipant(player)) {
            return false;
        }
        if (punishments.containsKey(player.getUniqueId())) {
            return true;
        }
        return finalDamage >= player.getHealth();
    }

    private void punishPlayerDeath(Player player) {
        UUID id = player.getUniqueId();
        if (punishments.containsKey(id)) {
            return;
        }

        int punishmentSeconds = config.punishmentSeconds.get();
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.setFlying(false);
        if (punishmentSeconds > 0) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.BLINDNESS, punishmentSeconds * 20 + 40, 1, false, false, false));
        }

        ItemStack[] contents = cloneContents(player.getInventory().getContents());
        ItemStack[] armor = cloneContents(player.getInventory().getArmorContents());
        ItemStack offHand = player.getInventory().getItemInOffHand().clone();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.sendActionBar(
                Component.text("You died. Spectating for " + punishmentSeconds + "s.", NamedTextColor.RED));

        Location deathLocation = player.getLocation().clone();
        BukkitTask releaseTask = Bukkit.getScheduler().runTaskLater(services.plugin(),
                () -> clearPunishment(player, true),
                Math.max(1, punishmentSeconds) * 20L);
        punishments.put(id, new Punishment(releaseTask,
                System.currentTimeMillis() + Math.max(1, punishmentSeconds) * 1000L,
                deathLocation, contents, armor, offHand));
    }

    private void clearPunishment(Player player, boolean restorePlayer) {
        Punishment state = punishments.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        state.releaseTask().cancel();
        if (!restorePlayer || !player.isOnline() || !services.participants().isParticipant(player)) {
            return;
        }

        TeamState teamState = stateOf(teamOf(player));
        if (teamState != null && teamState.finished()) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.setFlying(false);
            player.teleport(spectatorLocation());
            return;
        }

        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        var maxHealthAttribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute != null ? maxHealthAttribute.getValue() : 20.0;
        player.setHealth(Math.min(maxHealth, 20.0));
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setFireTicks(0);
        if (state.deathLocation() != null) {
            player.teleport(state.deathLocation());
        }
        if (state.contents() != null) {
            player.getInventory().setContents(cloneContents(state.contents()));
            player.getInventory().setArmorContents(cloneContents(state.armor()));
            player.getInventory().setItemInOffHand(state.offHand() == null ? null : state.offHand().clone());
            if (player.getInventory().getItem(8) == null) {
                player.getInventory().setItem(8, boardUi.createCard());
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 20, 0, false, false, false));
        player.sendActionBar(Component.text("You respawned.", NamedTextColor.GREEN));
    }

    private boolean isPunished(Player player) {
        return punishments.containsKey(player.getUniqueId());
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!services.participants().isParticipant(player)) {
            return;
        }
        if (preStartLocked) {
            event.setCancelled(true);
            return;
        }
        String causeName = event.getCause().name();
        if (causeName.contains("STALACTITE") || causeName.contains("STALAGMITE")) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }
        if (!shouldCancelLethalDamage(player, event.getFinalDamage())) {
            return;
        }
        event.setCancelled(true);
        punishPlayerDeath(player);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (services.participants().isParticipant(attacker) && services.participants().isParticipant(victim)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            onItemProgress(player, event.getItem().getItemStack());
        }
    }

    @Override
    public void onCraftItem(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player && event.getCurrentItem() != null) {
            onItemProgress(player, event.getCurrentItem());
        }
    }

    @Override
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        onItemProgress(event.getPlayer(), event.getItemType());
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (boardUi.isBoardView(event.getView().getTopInventory().getHolder())) {
            event.setCancelled(true);
            handleBoardClick(player, event.getRawSlot());
            return;
        }
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            if (event.getCurrentItem() != null) {
                onItemProgress(player, event.getCurrentItem());
            }
            if (event.getCursor() != null) {
                onItemProgress(player, event.getCursor());
            }
        }
        if (boardUi.isBingoCard(event.getCurrentItem()) || boardUi.isBingoCard(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (boardUi.isBoardView(event.getView().getTopInventory().getHolder())) {
            event.setCancelled(true);
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                return;
            }
        }
        if (boardUi.isBingoCard(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        for (ItemStack stack : event.getNewItems().values()) {
            if (boardUi.isBingoCard(stack)) {
                event.setCancelled(true);
                return;
            }
        }
        for (ItemStack stack : event.getNewItems().values()) {
            if (stack != null) {
                onItemProgress(player, stack);
            }
        }
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean spectator = event.getPlayer().getGameMode() == org.bukkit.GameMode.SPECTATOR;
        if (spectator) {
            if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
                return;
            }
        } else if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !boardUi.isBingoCard(item)) {
            return;
        }
        event.setCancelled(true);
        openBoardFor(event.getPlayer());
    }

    @Override
    public void onItemHeld(PlayerItemHeldEvent event) {
        applySpeedBonus(event.getPlayer(), event.getNewSlot());
    }

    @Override
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (boardUi.isBingoCard(event.getMainHandItem()) || boardUi.isBingoCard(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDropItem(PlayerDropItemEvent event) {
        if (boardUi.isBingoCard(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        if (preStartLocked && services.participants().isParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        if (preStartLocked && services.participants().isParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isPunished(event.getPlayer()) || event.getTo() == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        event.setTo(event.getFrom());
    }

    @Override
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!isPunished(event.getPlayer())) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        event.setCancelled(true);
    }

    void openBoardFor(Player player) {
        if (board == null) {
            player.sendActionBar(Component.text("No active bingo board right now.", NamedTextColor.RED));
            return;
        }
        TeamState team = stateOf(teamOf(player));
        if (team == null) {
            player.sendActionBar(Component.text("You are not participating.", NamedTextColor.RED));
            return;
        }
        boardUi.openBoard(player, board, team, mode, difficulty, this::rewardForIndex, lockoutOwners());
    }

    /** Passed to the board UI, which marks other teams' cells in lockout. */
    private TeamColor[] lockoutOwners() {
        return mode == GameMode.LOCKOUT ? owners : null;
    }

    private void handleBoardClick(Player player, int rawSlot) {
        TeamState team = stateOf(teamOf(player));
        if (team == null || board == null) {
            return;
        }
        BoardUiService.ClickAction action = boardUi.mapClick(player, rawSlot);
        if (action.type() == BoardUiService.ClickActionType.TOGGLE_MODE) {
            boardUi.openBoard(player, board, team, mode, difficulty, this::rewardForIndex, lockoutOwners());
        }
    }

    private void refreshOpenBoards() {
        if (board == null) {
            return;
        }
        for (Player player : services.participants().onlinePlayers()) {
            TeamState team = stateOf(teamOf(player));
            if (team != null) {
                boardUi.refreshOpenBoard(player, board, team, mode, difficulty, this::rewardForIndex, lockoutOwners());
            }
        }
    }

    void returnCard(Player player) {
        if (!boardUi.isBingoCard(player.getInventory().getItem(8))) {
            player.getInventory().setItem(8, boardUi.createCard());
        }
    }

    private void preparePlayerForRound(Player player) {
        clearPunishment(player, false);
        services.playerState().resetForRound(player, org.bukkit.GameMode.SURVIVAL);
        kit.fillStarterItems(player.getInventory());
        player.getInventory().setItem(8, boardUi.createCard());
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.WATER_BREATHING, PotionEffect.INFINITE_DURATION, 0, false, false,
                        false));
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false,
                        false));
        PlayerStateService.unlockAllRecipes(player);
        PlayerStateService.resetAllAdvancements(player);
        player.updateInventory();
        applySpeedBonus(player, player.getInventory().getHeldItemSlot());
    }

    private void applySpeedBonus(Player player, int heldSlot) {
        if (ended || !services.participants().isParticipant(player)
                || player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            player.removePotionEffect(PotionEffectType.SPEED);
            return;
        }
        ItemStack held = player.getInventory().getItem(heldSlot);
        if (boardUi.isBingoCard(held)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    private void runPeriodicPlayerChecks() {
        for (Player player : services.participants().onlinePlayers()) {
            runPeriodicPlayerChecks(player);
        }
    }

    private void runPeriodicPlayerChecks(Player player) {
        applySpeedBonus(player, player.getInventory().getHeldItemSlot());
        if (board == null || ended) {
            return;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            onItemProgress(player, stack);
        }
    }

    /** Play starts at the origin so strongholds, and so the End, are where vanilla puts them. */
    private void selectArena() {
        arenaWorld = services.worlds().create("bingo", World.Environment.NETHER);
        ArenaBiomes.ArenaSpot spot = ArenaBiomes.findArenaCenter(arenaWorld);
        arenaX = spot.location().getBlockX();
        arenaZ = spot.location().getBlockZ();
        services.plugin().getLogger().info("Bingo arena " + spot.describe());
        arenaWorld.setTime(12000L);
        arenaWorld.setStorm(false);
    }

    private void buildCage() {
        fill(arenaX - 2, 220, arenaZ - 2, arenaX + 2, 220, arenaZ + 2, Material.GLASS);
        fill(arenaX - 2, 224, arenaZ - 2, arenaX + 2, 224, arenaZ + 2, Material.GLASS);
        fill(arenaX - 2, 220, arenaZ - 2, arenaX - 2, 224, arenaZ + 2, Material.GLASS);
        fill(arenaX + 2, 220, arenaZ - 2, arenaX + 2, 224, arenaZ + 2, Material.GLASS);
        fill(arenaX - 1, 220, arenaZ - 2, arenaX + 1, 224, arenaZ - 2, Material.GLASS);
        fill(arenaX - 1, 220, arenaZ + 2, arenaX + 1, 224, arenaZ + 2, Material.GLASS);
    }

    private void openCageFloor() {
        fill(arenaX - 1, 220, arenaZ - 1, arenaX + 1, 220, arenaZ + 1, Material.AIR);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        if (arenaWorld == null) {
            return;
        }
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    arenaWorld.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private Location spectatorLocation() {
        return new Location(arenaWorld, arenaX + 0.5, 255, arenaZ + 0.5);
    }

    private void updateSidebar() {
        List<TeamState> teams = new ArrayList<>(teamStates.values());
        teams.sort(teamRankingComparator());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text()
                .append(Component.text("Mode: ", NamedTextColor.GOLD))
                .append(Component.text(mode.key(), NamedTextColor.YELLOW))
                .append(Component.text(" / ", NamedTextColor.GOLD))
                .append(Component.text(difficulty.key(), NamedTextColor.YELLOW))
                .build());
        lines.add(Component.text()
                .append(Component.text("Time: ", NamedTextColor.GOLD))
                .append(Component.text(TimeFormat.clock(elapsedSeconds), NamedTextColor.GREEN))
                .append(Component.text("/", NamedTextColor.GOLD))
                .append(Component.text(TimeFormat.clock(finishSeconds), NamedTextColor.GREEN))
                .build());
        lines.add(Component.text(" "));
        for (TeamState state : teams) {
            lines.add(Component.text()
                    .append(teamDisplayName(state))
                    .append(Component.text(": ", NamedTextColor.GOLD))
                    .append(teamResultComponent(state))
                    .build());
            if (lines.size() >= 15) {
                break;
            }
        }
        for (Player player : services.participants().onlinePlayers()) {
            services.sidebar().update(player, Component.text("Bingo", NamedTextColor.GOLD), lines);
        }
    }

    private Component teamDisplayName(TeamState state) {
        if (state.memberCount() == 1) {
            return Component.text(state.members().iterator().next(), state.color().textColor());
        }
        return state.color().displayNameComponent();
    }

    private Component teamResultComponent(TeamState state) {
        if (mode == GameMode.LOCKOUT) {
            return Component.text(state.score() + (state.finished() ? " BINGO" : ""),
                    state.finished() ? NamedTextColor.GREEN : NamedTextColor.WHITE);
        }
        if (mode != GameMode.DEFAULT) {
            return Component.text(state.score(), NamedTextColor.WHITE);
        }
        if (state.finished() && state.finishedAtSeconds() >= 0) {
            return Component.text(TimeFormat.clock(state.finishedAtSeconds()), NamedTextColor.GREEN);
        }
        return Component.text(state.score() + "/5", NamedTextColor.RED);
    }

    private Component teamCollectedMessage(String nickname, TeamState team, String itemName) {
        return Component.text(nickname, team.color().textColor())
                .append(Component.text(mode == GameMode.LOCKOUT ? " claimed " : " collected ", NamedTextColor.GOLD))
                .append(Component.text(itemName, NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.GOLD));
    }

    private String timeWarningMessage() {
        if (finishSeconds <= 0) {
            return null;
        }
        int left = finishSeconds - (int) elapsedSeconds;
        if (left <= 0) {
            return null;
        }
        if (left % 600 == 0) {
            return left / 60 + " minutes left";
        }
        if (left <= 300 && left % 60 == 0) {
            return left / 60 + " minutes left";
        }
        if (left <= 30 && left % 10 == 0) {
            return left + " seconds left";
        }
        if (left <= 5) {
            return left + " seconds left";
        }
        return null;
    }

    private void broadcast(Component message) {
        services.messages().broadcast(message);
    }

    private void broadcastParticipantsActionbar(Component message) {
        services.messages().broadcastActionBar(services.participants().onlinePlayers(), message);
    }

    private void playParticipantsSound(Sound sound, float volume, float pitch) {
        services.messages().playSound(services.participants().onlinePlayers(), sound, volume, pitch);
    }

    private void playCollectionSounds(Player collector) {
        for (Player player : services.participants().onlinePlayers()) {
            if (player.getUniqueId().equals(collector.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
            }
        }
    }

    private static String prettify(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        if (source == null) {
            return null;
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    private Comparator<TeamState> teamRankingComparator() {
        if (mode == GameMode.LOCKOUT) {
            // The team with a line first, then the most cells.
            return Comparator.comparing(TeamState::finished).reversed()
                    .thenComparing(Comparator.comparingInt(TeamState::score).reversed());
        }
        if (mode != GameMode.DEFAULT) {
            return Comparator.comparingInt(TeamState::score).reversed();
        }
        return Comparator
                .comparing(TeamState::finished).reversed()
                .thenComparingInt(state -> state.finished() ? state.finishedAtSeconds() : Integer.MAX_VALUE)
                .thenComparing(Comparator.comparingInt(TeamState::score).reversed());
    }
}
