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
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
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
    private final Map<UUID, AbstractArrow> arrows = new HashMap<>();
    private WaitForItArena arena;
    private WaitForItRound round;

    WaitForItSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "wait_for_it", context);
    }

    @Override
    void buildArena() {
        round = new WaitForItRound(names.keySet(), WaitForItRound.selectTasks(random));
        arena = new WaitForItArena(world, names.size());
        arena.build();
        arena.prepare(round.task(), random);
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
        var players = alivePlayers();
        players.forEach(this::equipTask);
        announceTask(players);
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
            if (!transition.completed().contains(player.getUniqueId()))
                player.sendMessage(
                        I18n.tr(KEY + "missed_task", taskText(transition.task(), "name"))
                                .color(NamedTextColor.YELLOW));
        }
        if (round.finished()) {
            for (Player player : players) player.sendMessage(text(score(player)));
            finish(List.of());
            return;
        }

        // Close containers before rebuilding their blocks and remove tools from the previous task.
        players.forEach(WaitForItSession::clearTaskItems);
        clearArrows();
        arena.prepare(round.task(), random);
        for (Player player : players) {
            teleport(player, arena.spawn(bays.get(player.getUniqueId())));
            equipTask(player);
        }
        announceTask(players);
    }

    private void equipTask(Player player) {
        switch (round.task()) {
            case CRAFT ->
                    give(
                            player,
                            new ItemStack(Material.OAK_PLANKS, 3),
                            new ItemStack(Material.STICK, 2));
            case LADDER -> give(player, new ItemStack(Material.STICK, 7));
            case BOOTS -> give(player, new ItemStack(Material.LEATHER, 4));
            case LOGS -> give(player, new ItemStack(Material.IRON_AXE));
            case ORES -> give(player, new ItemStack(Material.STONE_PICKAXE));
            case WEBS -> give(player, new ItemStack(Material.IRON_SWORD));
            case STACK ->
                    give(
                            player,
                            new ItemStack(Material.RED_CONCRETE),
                            new ItemStack(Material.YELLOW_CONCRETE),
                            new ItemStack(Material.BLUE_CONCRETE));
            case LIGHT -> give(player, new ItemStack(Material.TORCH, 3));
            case TARGETS -> {
                give(player, new ItemStack(Material.BOW));
                player.getInventory().setItem(9, new ItemStack(Material.ARROW, 24));
            }
            default -> {}
        }
    }

    private static void give(Player player, ItemStack... items) {
        for (int slot = 0; slot < items.length; slot++)
            player.getInventory().setItem(slot, items[slot]);
        player.getInventory().setHeldItemSlot(0);
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
            case CROP ->
                    I18n.tr(
                            KEY + "task.crop.progress",
                            round.count(id, WHEAT),
                            CROP.required(WHEAT),
                            round.count(id, CARROT),
                            CROP.required(CARROT));
            case BUTTON -> I18n.tr(KEY + "task.button.progress", round.collected(id));
            case LOGS ->
                    I18n.tr(
                            KEY + "task.logs.progress",
                            round.count(id, SPRUCE),
                            round.count(id, BIRCH));
            case ORES ->
                    I18n.tr(
                            KEY + "task.ores.progress",
                            round.count(id, COPPER),
                            round.count(id, IRON));
            case CRAFT, LADDER, BOOTS, TREASURE -> taskText("hint");
            default ->
                    I18n.tr(
                            KEY + "progress",
                            taskText("hint"),
                            round.collected(id),
                            round.task().requiredTotal());
        };
    }

    @Override
    protected void handleMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Integer bay = bays.get(player.getUniqueId());
        if (bay == null || event.isCancelled()) return;
        Location to = event.getTo();
        if (!arena.contains(bay, to)) {
            Location from = event.getFrom().clone();
            from.setDirection(to.getDirection());
            event.setTo(from);
            return;
        }
        if (round.task().action != WaitForItRound.Action.WALK || !canInteract(player)) return;
        Block floor = to.clone().subtract(0, .06, 0).getBlock();
        if (Math.abs(to.getY() - floor.getY() - 1) < .07)
            record(player, arena.targetAt(bay, round.task(), floor));
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (event.getHand() == EquipmentSlot.HAND && canInteract(player)) {
            int bay = bays.get(player.getUniqueId());
            var task = round.task();
            switch (event.getAction()) {
                case RIGHT_CLICK_AIR -> {
                    if (task == TARGETS && event.getMaterial() == Material.BOW) return;
                }
                case LEFT_CLICK_BLOCK -> {
                    if (block != null
                            && task.action == WaitForItRound.Action.BREAK
                            && round.canRecord(
                                    player.getUniqueId(),
                                    arena.targetAt(bay, task, block),
                                    elapsed())) return;
                }
                case RIGHT_CLICK_BLOCK -> {
                    if (task == TARGETS && event.getMaterial() == Material.BOW) return;
                    if (block != null && useBlock(event, player, bay, task, block)) return;
                }
                default -> {}
            }
        }
        event.setCancelled(true);
    }

    private boolean useBlock(
            PlayerInteractEvent event,
            Player player,
            int bay,
            WaitForItRound.Task task,
            Block block) {
        return switch (task.action) {
            case CRAFT -> arena.isWorkbench(bay, block);
            case LOOT -> arena.isBarrel(bay, block);
            case USE ->
                    event.useInteractedBlock() != Event.Result.DENY
                            && record(player, arena.targetAt(bay, task, block));
            case PLACE ->
                    event.getBlockFace() == BlockFace.UP
                            && placementAllowed(
                                    player,
                                    bay,
                                    block.getRelative(BlockFace.UP),
                                    event.getMaterial());
            default -> false;
        };
    }

    @Override
    protected void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        var task = round.task();
        if (event.isCancelled()
                || task.action != WaitForItRound.Action.BREAK
                || !canInteract(player)) {
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
        Material gathered =
                switch (goal) {
                    case CARROT -> Material.CARROT;
                    case COPPER -> Material.RAW_COPPER;
                    case IRON -> Material.RAW_IRON;
                    case WEB -> Material.STRING;
                    default -> block.getType();
                };
        player.getInventory().addItem(new ItemStack(gathered));
    }

    private boolean placementAllowed(Player player, int bay, Block block, Material material) {
        var placement = arena.placementAt(bay, block);
        return placement != null
                && placement.material() == material
                && round.canRecord(player.getUniqueId(), placement.goal(), elapsed());
    }

    @Override
    protected void handleBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!event.isCancelled()
                && event.canBuild()
                && event.getHand() == EquipmentSlot.HAND
                && round.task().action == WaitForItRound.Action.PLACE
                && canInteract(player)) {
            int bay = bays.get(player.getUniqueId());
            if (placementAllowed(player, bay, block, block.getType())
                    && record(player, arena.placementAt(bay, block).goal())) return;
        }
        event.setCancelled(true);
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)
                || !canInteract(player)
                || round.task() != TARGETS
                || !(event.getProjectile() instanceof AbstractArrow arrow)) {
            event.setCancelled(true);
            return;
        }
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrows.put(arrow.getUniqueId(), arrow);
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        var arrow = arrows.remove(event.getEntity().getUniqueId());
        if (arrow == null) return;
        Block hit = event.getHitBlock();
        if (hit != null
                && round.task() == TARGETS
                && arrow.getShooter() instanceof Player player
                && canInteract(player))
            record(player, arena.targetAt(bays.get(player.getUniqueId()), TARGETS, hit));
        arrow.remove();
    }

    @Override
    protected void handleInventoryClick(InventoryClickEvent event) {
        // CraftItemEvent also reaches this parent handler list. The craft hook owns the result.
        if (event instanceof CraftItemEvent) return;
        if (!(event.getWhoClicked() instanceof Player player) || !canInteract(player)) {
            event.setCancelled(true);
            return;
        }
        if (round.task() == TREASURE) {
            if (event.isCancelled() || !takeTreasure(player, event)) event.setCancelled(true);
            return;
        }
        if (!canCraft(player, event)
                || event.getClickedInventory() == null
                || switch (event.getAction()) {
                    case DROP_ALL_CURSOR, DROP_ONE_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
                    default -> false;
                }) event.setCancelled(true);
    }

    private boolean takeTreasure(Player player, InventoryClickEvent event) {
        var inventory = event.getView().getTopInventory();
        Location container = inventory.getLocation();
        if (event.getView().getType() != InventoryType.BARREL
                || event.getClickedInventory() != inventory
                || container == null
                || !arena.isBarrel(bays.get(player.getUniqueId()), container.getBlock())
                || event.getCurrentItem() == null
                || event.getCurrentItem().getType() != Material.EMERALD
                || !takeClick(event)
                || !record(player, ITEM)) return false;
        closeCompletedView(player, TREASURE, InventoryType.BARREL);
        return true;
    }

    @Override
    protected void handleCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !validCraft(player, event))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRecipeCrafted(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !validCraft(player, event)) return;
        var task = round.task();
        if (record(player, task == CRAFT ? PICKAXE : ITEM))
            closeCompletedView(player, task, InventoryType.WORKBENCH);
    }

    private void closeCompletedView(Player player, WaitForItRound.Task task, InventoryType type) {
        // Commit the native result click before closing. A transition may already have closed it.
        Bukkit.getScheduler()
                .runTask(
                        services.plugin(),
                        () -> {
                            if (playing()
                                    && alive(player)
                                    && round.task() == task
                                    && round.completed(player.getUniqueId())
                                    && player.getOpenInventory().getType() == type)
                                player.closeInventory();
                        });
    }

    private boolean validCraft(Player player, CraftItemEvent event) {
        if (!canCraft(player, event)) return false;
        Material result =
                switch (round.task()) {
                    case CRAFT -> Material.WOODEN_PICKAXE;
                    case LADDER -> Material.LADDER;
                    case BOOTS -> Material.LEATHER_BOOTS;
                    default ->
                            throw new IllegalStateException(
                                    "No crafting recipe for " + round.task());
                };
        return event.getRecipe().getResult().getType() == result
                && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == result
                && takeClick(event);
    }

    private static boolean takeClick(InventoryClickEvent event) {
        return switch (event.getAction()) {
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
                && round.task().action == WaitForItRound.Action.CRAFT
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
        switch (round.record(player.getUniqueId(), goal, elapsed())) {
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

    private void clearArrows() {
        arrows.values().forEach(AbstractArrow::remove);
        arrows.clear();
    }

    @Override
    public PartyRoundResult roundResult() {
        return round == null ? PartyRoundResult.empty() : round.result(context.eligible());
    }

    @Override
    void cleanup() {
        clearArrows();
        for (Player player : alivePlayers())
            if (player.getWorld().equals(world)) clearTaskItems(player);
    }
}
