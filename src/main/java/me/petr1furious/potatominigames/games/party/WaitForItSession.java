package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.Goal.*;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.TASK_COUNT;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.*;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WaitForItSession extends PartySurvivalSession {
    private static final String KEY = "party.wait_for_it.";

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
        arena.prepare(FLOWER, random);
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
        announceTask(alivePlayers());
    }

    @Override
    void playTick(long elapsed) {
        List<Player> players = alivePlayers();
        var transition = round.advance(elapsed, players.stream().map(Player::getUniqueId).toList());
        if (transition == null) {
            if (elapsed % 20 == 0) players.forEach(this::showTimer);
            return;
        }
        for (Player player : players) {
            if (!transition.completed().contains(player.getUniqueId())) {
                player.sendMessage(
                        I18n.tr(KEY + "missed_task", taskText(transition.task(), "name"))
                                .color(NamedTextColor.YELLOW));
            }
        }
        if (round.finished()) {
            for (Player player : players) player.sendMessage(text(score(player)));
            finish(List.of());
            return;
        }

        // Close a workbench while it still exists and discard its contents before the next task.
        for (Player player : players) clearTaskItems(player);
        arena.prepare(round.task(), random);
        for (Player player : players) {
            teleport(player, arena.spawn(bays.get(player.getUniqueId())));
            if (round.task() == CRAFT) {
                player.getInventory().setItem(0, new ItemStack(Material.OAK_PLANKS, 3));
                player.getInventory().setItem(1, new ItemStack(Material.STICK, 2));
                player.getInventory().setHeldItemSlot(0);
            }
        }
        announceTask(players);
    }

    private void announceTask(List<Player> players) {
        announceTitle(
                I18n.tr(KEY + "task_start", round.taskNumber(), TASK_COUNT, taskText("name")),
                taskText("hint"),
                2);
        say(I18n.tr(KEY + "detail", taskText("name"), taskText("detail"), round.task().seconds));
        PartySounds.play(players, Sound.BLOCK_NOTE_BLOCK_PLING, .6f, 1.6f);
        players.forEach(this::showTimer);
    }

    @Override
    Component status() {
        return I18n.tr(KEY + "timer", taskText("name"), round.secondsLeft(elapsed()));
    }

    @Override
    List<Component> sidebarLines(Player player, Component line) {
        if (!alive(player)) return List.of(line, I18n.tr(KEY + "spectating"));
        return List.of(
                line.color(NamedTextColor.YELLOW),
                I18n.tr(KEY + "task_number", round.taskNumber(), TASK_COUNT)
                        .color(NamedTextColor.GRAY),
                objective(player),
                score(player));
    }

    private void showTimer(Player player) {
        Component line =
                round.completed(player.getUniqueId())
                        ? I18n.tr(KEY + "waiting", score(player)).color(NamedTextColor.GREEN)
                        : I18n.tr(KEY + "timer", objective(player), round.secondsLeft(elapsed()))
                                .color(NamedTextColor.GOLD);
        player.sendActionBar(line);
    }

    private Component objective(Player player) {
        UUID id = player.getUniqueId();
        return switch (round.task()) {
            case FLOWER ->
                    I18n.tr(
                            KEY + "task.flower.progress",
                            round.count(id, POPPY),
                            round.count(id, CORNFLOWER),
                            round.count(id, ALLIUM));
            case CRAFT -> taskText("hint");
            case CROP ->
                    I18n.tr(
                            KEY + "task.crop.progress",
                            round.count(id, WHEAT),
                            CROP.required(WHEAT),
                            round.count(id, CARROT),
                            CROP.required(CARROT));
            case BUTTON ->
                    I18n.tr(
                            KEY + "task.button.progress",
                            round.count(id, BUTTON_LEFT) + round.count(id, BUTTON_RIGHT));
        };
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
        if (event.getHand() == EquipmentSlot.HAND && block != null && canInteract(player)) {
            int bay = bays.get(player.getUniqueId());
            var task = round.task();
            var goal = arena.targetAt(bay, task, block);
            switch (event.getAction()) {
                case LEFT_CLICK_BLOCK -> {
                    if ((task == FLOWER || task == CROP)
                            && round.canRecord(player.getUniqueId(), goal, elapsed())) return;
                }
                case RIGHT_CLICK_BLOCK -> {
                    if (task == CRAFT && arena.isWorkbench(bay, block)) return;
                    if (task == BUTTON
                            && event.useInteractedBlock() != Event.Result.DENY
                            && record(player, goal)) return;
                }
                default -> {}
            }
        }
        event.setCancelled(true);
    }

    @Override
    protected void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        var task = round.task();
        if (event.isCancelled() || (task != FLOWER && task != CROP) || !canInteract(player)) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlock();
        var goal = arena.targetAt(bays.get(player.getUniqueId()), task, block);
        if (!record(player, goal)) {
            event.setCancelled(true);
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        Material gathered = goal == CARROT ? Material.CARROT : block.getType();
        player.getInventory().addItem(new ItemStack(gathered));
    }

    @Override
    protected void handleInventoryClick(InventoryClickEvent event) {
        // CraftItemEvent also reaches this parent event's handler list. The craft hook owns the
        // result.
        if (event instanceof CraftItemEvent) return;
        if (!(event.getWhoClicked() instanceof Player player)
                || !canCraft(player, event)
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
    public void onPickaxeCrafted(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !validCraft(player, event)
                || !record(player, PICKAXE)) return;
        // Commit the native result click before closing. A transition may already have closed it.
        Bukkit.getScheduler()
                .runTask(
                        services.plugin(),
                        () -> {
                            if (playing()
                                    && alive(player)
                                    && round.task() == CRAFT
                                    && round.completed(player.getUniqueId())
                                    && player.getOpenInventory().getType()
                                            == InventoryType.WORKBENCH) {
                                player.closeInventory();
                            }
                        });
    }

    private boolean validCraft(Player player, CraftItemEvent event) {
        return canCraft(player, event)
                && event.getRecipe().getResult().getType() == Material.WOODEN_PICKAXE
                && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.WOODEN_PICKAXE
                && switch (event.getAction()) {
                    case NOTHING, UNKNOWN -> false;
                    default ->
                            switch (event.getClick()) {
                                case LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT -> true;
                                default -> false;
                            };
                };
    }

    private boolean canCraft(Player player, InventoryClickEvent event) {
        return canInteract(player)
                && round.task() == CRAFT
                && event.getView().getType() == InventoryType.WORKBENCH;
    }

    private boolean canInteract(Player player) {
        UUID id = player.getUniqueId();
        return playing()
                && alive(player)
                && world.equals(player.getWorld())
                && bays.containsKey(id)
                && round.secondsLeft(elapsed()) > 0
                && !round.completed(id);
    }

    private boolean record(Player player, WaitForItRound.Goal goal) {
        var progress = round.record(player.getUniqueId(), goal, elapsed());
        switch (progress) {
            case IGNORED -> {
                return false;
            }
            case PARTIAL ->
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, .6f, 1.4f);
            case COMPLETE -> {
                player.sendMessage(
                        I18n.tr(KEY + "success_task", taskText("name"))
                                .color(NamedTextColor.GREEN));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .75f, 1.35f);
            }
        }
        showTimer(player);
        return true;
    }

    private Component taskText(String part) {
        return taskText(round.task(), part);
    }

    private static Component taskText(WaitForItRound.Task task, String part) {
        return I18n.tr(KEY + "task." + task.key + "." + part);
    }

    private Component score(Player player) {
        return I18n.tr(KEY + "score", round.points(player.getUniqueId()), TASK_COUNT)
                .color(NamedTextColor.WHITE);
    }

    private static void clearTaskItems(Player player) {
        player.setItemOnCursor(null);
        player.closeInventory();
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
