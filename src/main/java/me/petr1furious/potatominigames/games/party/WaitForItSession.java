package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.Goal.*;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.TASK_COUNT;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.*;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        return WaitForItRound.DURATION_SECONDS;
    }

    @Override
    boolean survivalAutoFinish() {
        return standings.survivors().isEmpty();
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
        round.start();
        for (Player player : alivePlayers()) openTask(player);
    }

    @Override
    void playTick(long elapsed) {
        List<Player> players = alivePlayers();
        if (players.stream().anyMatch(player -> round.finished(player.getUniqueId()))
                || !round.running(elapsed)) {
            for (Player player : players) {
                UUID id = player.getUniqueId();
                Component result = round.finished(id)
                        ? I18n.tr(KEY + "finished", seconds(round.finishTime(id)))
                        : completed(id);
                player.sendMessage(text(result));
            }
            finish(List.of());
            return;
        }

        for (Player player : players) {
            if (round.advance(player.getUniqueId())) openTask(player);
            else if (elapsed % 20 == 0) showTimer(player);
        }
    }

    private void openTask(Player player) {
        UUID id = player.getUniqueId();
        var task = round.task(id);
        int bay = bays.get(id);
        clearTaskItems(player);
        clearArrows(player);
        arena.prepare(bay, task, random);
        teleport(player, arena.spawn(bay));
        equipTask(player, task);
        announceTask(player, task);
    }

    private void equipTask(Player player, WaitForItRound.Task task) {
        switch (task) {
            case CRAFT ->
                    give(
                            player,
                            new ItemStack(Material.OAK_PLANKS, 3),
                            new ItemStack(Material.STICK, 2));
            case LADDER -> give(player, new ItemStack(Material.STICK, 7));
            case BOOTS -> give(player, new ItemStack(Material.LEATHER, 4));
            case STAIRS -> give(player, new ItemStack(Material.OAK_PLANKS, 6));
            case EXCAVATE -> give(player, new ItemStack(Material.IRON_SHOVEL));
            case VINES -> give(player, new ItemStack(Material.SHEARS));
            case BOOKS -> give(player, new ItemStack(Material.BOOK, 3));
            case CANDLES -> give(player, new ItemStack(Material.FLINT_AND_STEEL));
            case PLANT -> give(player, new ItemStack(Material.BIRCH_SAPLING, 3));
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

    private void announceTask(Player player, WaitForItRound.Task task) {
        int next = round.completed(player.getUniqueId()) + 1;
        player.showTitle(Title.title(
                text(I18n.tr(KEY + "task_start", next, TASK_COUNT, taskText(task, "name"))),
                taskText(task, "hint").color(NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2),
                        Duration.ofMillis(150))));
        player.sendMessage(text(I18n.tr(KEY + "detail", taskText(task, "name"),
                taskText(task, "detail"))));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .6f, 1.6f);
        showTimer(player);
    }

    @Override
    Component status() {
        return playing() ? I18n.tr(KEY + "race_clock", round.secondsLeft(elapsed()))
                : I18n.tr(KEY + "ready");
    }

    @Override
    List<Component> sidebarLines(Player player, Component line) {
        if (!playing()) return List.of(line);
        if (!alive(player)) return List.of(line, I18n.tr(KEY + "spectating"));
        UUID id = player.getUniqueId();
        var task = round.task(id);
        if (task == null) return List.of(line, completed(id));
        return List.of(
                line.color(NamedTextColor.YELLOW),
                I18n.tr(KEY + "task_number", round.completed(id) + 1, TASK_COUNT)
                        .color(NamedTextColor.GRAY),
                objective(id, task),
                completed(id));
    }

    private void showTimer(Player player) {
        UUID id = player.getUniqueId();
        var task = round.task(id);
        Component line = task == null
                ? completed(id).color(NamedTextColor.GREEN)
                : I18n.tr(KEY + "timer", objective(id, task), round.secondsLeft(elapsed()))
                        .color(NamedTextColor.GOLD);
        player.sendActionBar(line);
    }

    private Component objective(UUID id, WaitForItRound.Task task) {
        return switch (task) {
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
                    I18n.tr(KEY + "task.logs.progress", round.count(id, SPRUCE),
                            round.count(id, BIRCH));
            case ORES ->
                    I18n.tr(KEY + "task.ores.progress", round.count(id, COPPER),
                            round.count(id, IRON));
            case CRAFT, LADDER, BOOTS, STAIRS, TREASURE -> taskText(task, "hint");
            default ->
                    I18n.tr(KEY + "progress", taskText(task, "hint"), round.collected(id),
                            task.requiredTotal());
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
        var task = activeTask(player);
        if (task == null || task.action != WaitForItRound.Action.WALK) return;
        Block floor = to.clone().subtract(0, .06, 0).getBlock();
        if (Math.abs(to.getY() - floor.getY() - 1) < .07)
            record(player, arena.targetAt(bay, task, floor));
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        var task = activeTask(player);
        if (event.getHand() == EquipmentSlot.HAND && task != null) {
            int bay = bays.get(player.getUniqueId());
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
                            && validUse(event, player, task)
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

    private static boolean validUse(PlayerInteractEvent event, Player player, WaitForItRound.Task task) {
        return switch (task) {
            case BOOKS -> event.getMaterial() == Material.BOOK
                    && event.getBlockFace() == BlockFace.SOUTH && !player.isSneaking();
            case CANDLES -> event.getMaterial() == Material.FLINT_AND_STEEL
                    && event.useItemInHand() != Event.Result.DENY;
            default -> true;
        };
    }

    @Override
    protected void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        var task = activeTask(player);
        if (event.isCancelled()
                || task == null || task.action != WaitForItRound.Action.BREAK) {
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
        var task = activeTask(player);
        if (!event.isCancelled()
                && event.canBuild()
                && event.getHand() == EquipmentSlot.HAND
                && task != null && task.action == WaitForItRound.Action.PLACE) {
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
                || activeTask(player) != TARGETS
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
                && arrow.getShooter() instanceof Player player
                && activeTask(player) == TARGETS)
            record(player, arena.targetAt(bays.get(player.getUniqueId()), TARGETS, hit));
        arrow.remove();
    }

    @Override
    protected void handleInventoryClick(InventoryClickEvent event) {
        // CraftItemEvent also reaches this parent handler list. The craft hook owns the result.
        if (event instanceof CraftItemEvent) return;
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        var task = activeTask(player);
        if (task == TREASURE) {
            if (event.isCancelled() || !takeTreasure(player, event)) event.setCancelled(true);
            return;
        }
        if (!canCraft(task, event.getView())
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
        return true;
    }

    @Override
    protected void handleInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !canCraft(activeTask(player), event.getView())
                || !craftingSlotsOnly(event.getRawSlots(), event.getView().getTopInventory().getSize()))
            event.setCancelled(true);
    }

    static boolean craftingSlotsOnly(Set<Integer> slots, int topSize) {
        return !slots.isEmpty() && slots.stream().allMatch(slot -> slot > 0 && slot < topSize);
    }

    @Override
    protected void handleCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !validCraft(activeTask(player), event))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRecipeCrafted(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var task = activeTask(player);
        if (validCraft(task, event)) record(player, task == CRAFT ? PICKAXE : ITEM);
    }

    private static boolean validCraft(WaitForItRound.Task task, CraftItemEvent event) {
        if (!canCraft(task, event.getView())) return false;
        Material result =
                switch (task) {
                    case CRAFT -> Material.WOODEN_PICKAXE;
                    case LADDER -> Material.LADDER;
                    case BOOTS -> Material.LEATHER_BOOTS;
                    case STAIRS -> Material.OAK_STAIRS;
                    default ->
                            throw new IllegalStateException(
                                    "No crafting recipe for " + task);
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

    private static boolean canCraft(WaitForItRound.Task task, InventoryView view) {
        return task != null && task.action == WaitForItRound.Action.CRAFT
                && view.getType() == InventoryType.WORKBENCH;
    }

    private WaitForItRound.Task activeTask(Player player) {
        UUID id = player.getUniqueId();
        if (!playing() || context.ended() || !alive(player)
                || !world.equals(player.getWorld()) || !bays.containsKey(id)
                || !round.running(elapsed())) return null;
        return round.task(id);
    }

    private boolean record(Player player, WaitForItRound.Goal goal) {
        UUID id = player.getUniqueId();
        var task = round.task(id);
        switch (round.record(id, goal, elapsed())) {
            case IGNORED -> {
                return false;
            }
            case PARTIAL ->
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, .6f, 1.4f);
            case COMPLETE -> {
                player.sendMessage(
                        I18n.tr(KEY + "success_task", taskText(task, "name"))
                                .color(NamedTextColor.GREEN));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .75f, 1.35f);
            }
        }
        showTimer(player);
        return true;
    }

    private static Component taskText(WaitForItRound.Task task, String part) {
        return I18n.tr(KEY + "task." + task.key + "." + part);
    }

    private Component completed(UUID player) {
        return I18n.tr(KEY + "completed", round.completed(player), TASK_COUNT)
                .color(NamedTextColor.WHITE);
    }

    private static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.2f", ticks / 20.0);
    }

    private static void clearTaskItems(Player player) {
        player.setItemOnCursor(null);
        player.closeInventory();
        player.getInventory().clear();
    }

    private void clearArrows(Player player) {
        arrows.values().removeIf(arrow -> {
            if (!player.equals(arrow.getShooter())) return false;
            arrow.remove();
            return true;
        });
    }

    @Override
    void playerOut(Player player) {
        clearArrows(player);
    }

    @Override
    public PartyRoundResult roundResult() {
        return round == null ? PartyRoundResult.empty() : round.result(context.eligible());
    }

    @Override
    void cleanup() {
        arrows.values().forEach(AbstractArrow::remove);
        arrows.clear();
        for (Player player : alivePlayers())
            if (player.getWorld().equals(world)) clearTaskItems(player);
    }
}
