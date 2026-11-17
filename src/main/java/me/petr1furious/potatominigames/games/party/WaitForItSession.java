package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.TASK_COUNT;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.BUTTON;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.CRAFT;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.CROP;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.FLOWER;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WaitForItSession extends PartySurvivalSession {
    private static final String KEY = "party.wait_for_it.";
    private static final Title.Times FEEDBACK =
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(250));

    private final Map<UUID, Integer> bays = new HashMap<>();
    private WaitForItArena arena;
    private WaitForItRound round;

    WaitForItSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "wait_for_it", context);
    }

    @Override
    void buildArena() {
        round = new WaitForItRound(names.keySet());
        arena = new WaitForItArena(world, names.size());
        arena.build();
    }

    @Override
    void preparePlayer(Player player) {
        // The base session spawns each prepared player in increasing index order.
        bays.put(player.getUniqueId(), bays.size());
    }

    @Override
    Location spawn(int index, int count) {
        return arena.spawn(index);
    }

    @Override
    Location viewpoint() {
        return arena.viewpoint();
    }

    @Override
    double outHeight() {
        return WaitForItArena.FLOOR - 4;
    }

    @Override
    long arenaTime() {
        return 6000L;
    }

    @Override
    int durationSeconds() {
        return WaitForItRound.maxSeconds();
    }

    @Override
    boolean survivalAutoFinish() {
        return false;
    }

    @Override
    boolean podiumAwards() {
        return true;
    }

    @Override
    Component instructions() {
        return I18n.tr(KEY + "instructions", TASK_COUNT);
    }

    @Override
    void beginPlay() {
        showPreview();
    }

    @Override
    void playTick(long elapsed) {
        List<Player> players = alivePlayers();
        if (round.advance(elapsed, players.stream().map(Player::getUniqueId).toList())) {
            switch (round.phase()) {
                case PREVIEW -> showPreview();
                case ACTIVE -> startTask();
                case RESULT -> showTaskResult(players);
                case FINISHED -> {
                    for (Player player : players) player.sendMessage(text(score(player)));
                    finish(List.of());
                    return;
                }
            }
            showTimers(players);
        } else if (elapsed % 20 == 0) {
            showTimers(players);
        }
    }

    private void showPreview() {
        arena.prepare(round.task(), random);
        for (Player player : alivePlayers()) {
            clearTaskItems(player);
            teleport(player, arena.spawn(bays.get(player.getUniqueId())));
            freezeMovement(player);
        }
        announceTitle(
                I18n.tr(KEY + "preview", round.taskNumber(), TASK_COUNT, taskText("name")),
                taskText("hint"),
                WaitForItRound.PREVIEW_SECONDS - 1);
        say(I18n.tr(KEY + "detail", taskText("name"), taskText("detail"), round.task().seconds));
    }

    private void startTask() {
        releaseMovement();
        if (round.task() == CRAFT)
            for (Player player : alivePlayers()) {
                player.getInventory().setItem(0, new ItemStack(Material.OAK_PLANKS, 2));
                player.getInventory().setHeldItemSlot(0);
            }
        announceTitle(I18n.tr(KEY + "go", taskText("name")), taskText("hint"), 2);
        PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, .6f, 1.6f);
    }

    private void showTaskResult(List<Player> players) {
        for (Player player : players) {
            if (round.task() == CRAFT) player.closeInventory();
            if (!round.completed(player.getUniqueId())) {
                player.showTitle(
                        Title.title(
                                I18n.tr(KEY + "missed").color(NamedTextColor.YELLOW),
                                score(player),
                                FEEDBACK));
                player.sendMessage(
                        I18n.tr(KEY + "missed_task", taskText("name"))
                                .color(NamedTextColor.YELLOW));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, .6f, 1f);
            }
        }
    }

    @Override
    Component status() {
        long seconds = round.secondsLeft(elapsed());
        return switch (round.phase()) {
            case PREVIEW -> I18n.tr(KEY + "status.preview", taskText("name"), seconds);
            case ACTIVE -> I18n.tr(KEY + "status.active", taskText("name"), seconds);
            case RESULT, FINISHED -> I18n.tr(KEY + "status.result", taskText("name"));
        };
    }

    @Override
    List<Component> sidebarLines(Player player, Component line) {
        return List.of(
                line.color(NamedTextColor.YELLOW),
                I18n.tr(KEY + "task_number", round.taskNumber(), TASK_COUNT)
                        .color(NamedTextColor.GRAY),
                alive(player) ? score(player) : I18n.tr(KEY + "spectating"));
    }

    private void showTimers(List<Player> players) {
        for (Player player : players) {
            Component line =
                    switch (round.phase()) {
                        case PREVIEW ->
                                I18n.tr(
                                        KEY + "ready",
                                        taskText("hint"),
                                        round.secondsLeft(elapsed()));
                        case ACTIVE ->
                                round.completed(player.getUniqueId())
                                        ? I18n.tr(KEY + "waiting", score(player))
                                        : I18n.tr(
                                                KEY + "timer",
                                                taskText("hint"),
                                                round.secondsLeft(elapsed()));
                        case RESULT, FINISHED -> score(player);
                    };
            player.sendActionBar(line.color(NamedTextColor.GOLD));
        }
    }

    @Override
    protected void handleMove(PlayerMoveEvent event) {
        Integer bay = bays.get(event.getPlayer().getUniqueId());
        if (bay != null && !arena.contains(bay, event.getTo())) {
            Location from = event.getFrom().clone();
            from.setDirection(event.getTo().getDirection());
            event.setTo(from);
        }
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        WaitForItRound.Task task = round.task();
        boolean target =
                event.getHand() == EquipmentSlot.HAND
                        && block != null
                        && canComplete(player, task)
                        && arena.isTarget(bays.get(player.getUniqueId()), task, block);
        if (target) {
            switch (event.getAction()) {
                case LEFT_CLICK_BLOCK -> {
                    if (task == FLOWER || task == CROP) return;
                }
                case RIGHT_CLICK_BLOCK -> {
                    if (task == CRAFT) return;
                    if (task == BUTTON
                            && event.useInteractedBlock() != Event.Result.DENY
                            && complete(player, BUTTON)) return;
                }
                default -> {}
            }
        }
        event.setCancelled(true);
    }

    @Override
    protected void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        WaitForItRound.Task task = round.task();
        if (event.isCancelled()
                || (task != FLOWER && task != CROP)
                || !canComplete(player, task)
                || !arena.isTarget(bays.get(player.getUniqueId()), task, event.getBlock())
                || !complete(player, task)) {
            event.setCancelled(true);
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        player.getInventory()
                .setItem(0, new ItemStack(task == FLOWER ? Material.DANDELION : Material.WHEAT));
    }

    @Override
    protected void handleInventoryClick(InventoryClickEvent event) {
        // Bukkit dispatches crafting through both click hooks. The craft hook owns the result.
        if (event instanceof CraftItemEvent) return;
        if (!(event.getWhoClicked() instanceof Player player)
                || !canComplete(player, CRAFT)
                || !(event.getView().getTopInventory() instanceof CraftingInventory)
                || event.getClickedInventory() == null
                || switch (event.getAction()) {
                    case DROP_ALL_CURSOR, DROP_ONE_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
                    default -> false;
                }) event.setCancelled(true);
    }

    @Override
    protected void handleCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !validCraft(player, event))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSticksCrafted(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !validCraft(player, event)
                || !complete(player, CRAFT)) return;
        // Close after Minecraft commits the click and puts the sticks on the cursor or in
        // inventory.
        Bukkit.getScheduler()
                .runTask(
                        services.plugin(),
                        () -> {
                            if (playing() && alive(player) && round.task() == CRAFT)
                                player.closeInventory();
                        });
    }

    private boolean validCraft(Player player, CraftItemEvent event) {
        return canComplete(player, CRAFT)
                && event.getRecipe().getResult().getType() == Material.STICK
                && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.STICK
                && switch (event.getAction()) {
                    case NOTHING, UNKNOWN -> false;
                    default ->
                            switch (event.getClick()) {
                                case LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT -> true;
                                default -> false;
                            };
                };
    }

    private boolean canComplete(Player player, WaitForItRound.Task task) {
        UUID id = player.getUniqueId();
        return playing()
                && alive(player)
                && world.equals(player.getWorld())
                && bays.containsKey(id)
                && round.phase() == WaitForItRound.Phase.ACTIVE
                && round.task() == task
                && !round.completed(id);
    }

    private boolean complete(Player player, WaitForItRound.Task task) {
        if (!round.complete(player.getUniqueId(), task, elapsed())) return false;
        player.showTitle(
                Title.title(
                        I18n.tr(KEY + "success").color(NamedTextColor.GREEN),
                        score(player),
                        FEEDBACK));
        player.sendMessage(
                I18n.tr(KEY + "success_task", taskText("name")).color(NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .75f, 1.35f);
        return true;
    }

    private Component taskText(String part) {
        return I18n.tr(KEY + "task." + round.task().key + "." + part);
    }

    private Component score(Player player) {
        return I18n.tr(KEY + "score", round.points(player.getUniqueId()), TASK_COUNT)
                .color(NamedTextColor.WHITE);
    }

    private static void clearTaskItems(Player player) {
        player.closeInventory();
        player.setItemOnCursor(null);
        player.getInventory().clear();
    }

    @Override
    public PartyRoundResult roundResult() {
        return round == null ? PartyRoundResult.empty() : round.result(context.eligible());
    }

    @Override
    void cleanup() {
        for (Player player : alivePlayers())
            if (player.getWorld().equals(world)) clearTaskItems(player);
    }
}
