package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

final class BuildMemorySession extends PartySurvivalSession {
    private enum Phase {
        SHOW,
        BUILD,
        REVIEW
    }

    private final int stages;
    private BuildMemoryArena arena;
    private BuildMemoryJury jury;
    private Phase phase = Phase.SHOW;
    private int stage = 1;
    private long deadline;
    private Component modelTitle = Component.empty();
    private final Map<UUID, BuildMemoryPattern> patterns = new LinkedHashMap<>();
    private List<Integer> models;
    private static final int REVIEW_TICKS = 60;
    private final Map<UUID, BuildMemoryJudging.Progress> progress = new LinkedHashMap<>();
    private final Set<UUID> dirty = new LinkedHashSet<>();
    private final Set<UUID> ready = new HashSet<>();
    private final Map<UUID, BuildMemoryJudging.Grade> grades = new LinkedHashMap<>();
    private List<UUID> reviewOrder = List.of();
    private int reviewIndex;
    private boolean verdictShown;

    BuildMemorySession(SessionServices s, PartyConfig c, PartyRoundContext context) {
        super(s, c, "build_memory", context);
        stages = c.buildMemoryStages.get();
    }

    void buildArena() {
        arena = new BuildMemoryArena(world, names.keySet());
        arena.build();
        jury = new BuildMemoryJury(world);
    }

    Location spawn(int i, int n) {
        return arena.spawn(new ArrayList<>(names.keySet()).get(i));
    }

    Location viewpoint() {
        return new Location(world, .5, 100, .5, 0, 75);
    }

    double outHeight() {
        return 76;
    }

    int durationSeconds() {
        int seconds = 10;
        for (int round = 1; round <= stages; round++)
            seconds +=
                    memorizeSeconds(round)
                            + buildSeconds(round)
                            + Math.max(1, names.size()) * (REVIEW_TICKS / 20)
                            + 3;
        return seconds;
    }

    static int buildSeconds(int stage) {
        return 20 + 5 * (stage - 1);
    }

    static int memorizeSeconds(int stage) {
        return 12 + stage - 1;
    }

    static boolean allReady(Collection<UUID> players, Set<UUID> ready) {
        return !players.isEmpty() && ready.containsAll(players);
    }

    boolean podiumAwards() {
        return true;
    }

    Component instructions() {
        return I18n.tr("party.rounds.build_memory.instructions");
    }

    void beginPlay() {
        models = BuildMemoryPattern.deck(random);
        showPattern();
    }

    private void showPattern() {
        phase = Phase.SHOW;
        jury.reset();
        arena.screens(false);
        patterns.clear();
        progress.clear();
        dirty.clear();
        grades.clear();
        ready.clear();
        arena.resize(BuildMemoryPattern.buildSize(stage));
        BuildMemoryPattern shared =
                BuildMemoryPattern.generate(stage, models.get(stage - 1), random);
        modelTitle = shared.title();
        for (Player p : alivePlayers()) {
            var pattern =
                    shared.rotate(random.nextInt(4)).centered(BuildMemoryPattern.buildSize(stage));
            patterns.put(p.getUniqueId(), pattern);
            progress.put(p.getUniqueId(), new BuildMemoryJudging.Progress());
            teleport(p, arena.spawn(p.getUniqueId()));
            clearKit(p);
            arena.show(p.getUniqueId(), pattern);
        }
        deadline = elapsed() + memorizeSeconds(stage) * 20L;
        announceTitle(I18n.tr("party.rounds.build_memory.memorize"), modelTitle, 2);
        say(I18n.tr("party.rounds.build_memory.orientation", stage));
        PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, .7f, .9f);
    }

    void playTick(long elapsed) {
        if (phase == Phase.BUILD) {
            for (UUID id : dirty) progress.get(id).observe(arena.capture(id), elapsed);
            dirty.clear();
            if (allReady(alivePlayers().stream().map(Player::getUniqueId).toList(), ready))
                deadline = elapsed;
        }
        if (elapsed < deadline) {
            if (phase != Phase.REVIEW) PartySounds.countdown(alivePlayers(), deadline - elapsed);
            return;
        }
        switch (phase) {
            case SHOW -> {
                phase = Phase.BUILD;
                arena.screens(true);
                for (Player p : alivePlayers()) {
                    arena.clear(p.getUniqueId());
                    clearKit(p);
                    p.getInventory().setItem(6, PvpClass.item(Material.IRON_PICKAXE));
                    p.getInventory().setItem(7, PvpClass.item(Material.IRON_AXE));
                    p.getInventory().setItem(5, PvpClass.item(Material.SHEARS));
                    ItemStack done = new ItemStack(Material.EMERALD);
                    done.editMeta(meta ->
                            meta.displayName(I18n.tr("party.rounds.build_memory.ready_item")));
                    p.getInventory().setItem(8, I18n.item(done, p));
                    patterns.get(p.getUniqueId())
                            .inventory()
                            .forEach((m, n) -> p.getInventory().addItem(new ItemStack(m, n)));
                }
                deadline = elapsed + buildSeconds(stage) * 20L;
                announceTitle(I18n.tr("party.rounds.build_memory.build"), modelTitle, 2);
                say(I18n.tr("party.rounds.build_memory.reproduce"));
                PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, .7f, 1.5f);
            }
            case BUILD -> {
                phase = Phase.REVIEW;
                standings.holdResults(true);
                Set<UUID> alive = new HashSet<>();
                for (Player p : alivePlayers()) {
                    UUID id = p.getUniqueId();
                    alive.add(id);
                    var actual = arena.capture(id);
                    progress.get(id).observe(actual, elapsed);
                    grades.put(id, BuildMemoryJudging.grade(patterns.get(id), actual));
                    clearKit(p);
                }
                // Platform insertion order follows the circle, independent of build speed.
                reviewOrder = names.keySet().stream().filter(alive::contains).toList();
                reviewIndex = 0;
                verdictShown = false;
                arena.screens(false);
                inspectNext(elapsed);
            }
            case REVIEW -> {
                if (reviewIndex < reviewOrder.size()) inspectNext(elapsed);
                else if (!verdictShown) {
                    jury.stopBeam();
                    Map<UUID, BuildMemoryJudging.Grade> eligible = new LinkedHashMap<>();
                    Map<UUID, Long> times = new HashMap<>();
                    for (Player p : alivePlayers()) {
                        UUID id = p.getUniqueId();
                        if (grades.containsKey(id)) {
                            eligible.put(id, grades.get(id));
                            times.put(id, progress.get(id).finishedAt());
                        }
                    }
                    UUID loser = BuildMemoryJudging.loser(stage, eligible, times);
                    if (loser != null) {
                        say(
                                I18n.tr(
                                        eligible.get(loser).errors().isEmpty()
                                                ? "party.rounds.build_memory.slowest"
                                                : "party.rounds.build_memory.worst",
                                        names.get(loser)));
                        Player player = Bukkit.getPlayer(loser);
                        if (player != null) eliminate(player);
                    } else say(I18n.tr("party.rounds.build_memory.all_correct"));
                    verdictShown = true;
                    deadline = elapsed + 40;
                } else {
                    standings.holdResults(false);
                    if (stage >= stages || standings.decided(services.isTest(), gameTick()))
                        finish(standings.survivors());
                    else {
                        stage++;
                        showPattern();
                    }
                }
            }
        }
    }

    private void inspectNext(long elapsed) {
        if (reviewIndex >= reviewOrder.size()) {
            deadline = elapsed;
            return;
        }
        UUID id = reviewOrder.get(reviewIndex++);
        var grade = grades.get(id);
        jury.inspect(
                arena.reviewCenter(id),
                grade.errors().stream().map(cell -> arena.cellLocation(id, cell)).toList());
        say(
                I18n.tr(
                        "party.rounds.build_memory.grade",
                        names.get(id),
                        grade.errors().size(),
                        grade.percent(),
                        grade.correct(),
                        grade.total()));
        deadline = elapsed + REVIEW_TICKS;
    }

    @Override
    void cleanup() {
        if (jury != null) jury.close();
        dirty.clear();
    }

    private void clearKit(Player p) {
        p.closeInventory();
        p.setItemOnCursor(null);
        p.getInventory().clear();
    }

    private boolean building(Player p) {
        return playing() && phase == Phase.BUILD && alive(p) && !ready.contains(p.getUniqueId());
    }

    @Override
    protected void handleBlockPlace(BlockPlaceEvent e) {
        if (!building(e.getPlayer())
                || arena.localCell(e.getPlayer().getUniqueId(), e.getBlock()) == null
                || !e.getBlockReplacedState().getType().isAir()
                || !patterns.get(e.getPlayer().getUniqueId())
                        .inventory()
                        .containsKey(MemoryBlock.capture(e.getBlock().getBlockData()).item()))
            e.setCancelled(true);
        else dirty.add(e.getPlayer().getUniqueId());
    }

    @Override
    protected void handleBlockBreak(BlockBreakEvent e) {
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!building(p)
                || arena.localCell(p.getUniqueId(), e.getBlock()) == null
                || e.getBlock().getType().isAir()) return;
        var refund = arena.remove(p.getUniqueId(), e.getBlock(), blocks -> canRefund(p, blocks));
        if (refund.isEmpty()) {
            p.sendMessage(text(I18n.tr("party.rounds.build_memory.refund_space")));
            return;
        }
        for (MemoryBlock block : refund)
            p.getInventory().addItem(new ItemStack(block.item(), block.itemCount()));
        // Removal has already happened. Preserve even a break-and-repair within one server tick.
        progress.get(p.getUniqueId()).observe(arena.capture(p.getUniqueId()), elapsed());
        progress.get(p.getUniqueId()).editedAt(elapsed());
        dirty.add(p.getUniqueId());
        e.setDropItems(false);
        e.setExpToDrop(0);
    }

    private boolean canRefund(Player player, List<MemoryBlock> blocks) {
        var contents = player.getInventory().getStorageContents();
        int freeSlots = 0;
        for (ItemStack stack : contents) if (stack == null || stack.getType().isAir()) freeSlots++;
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (MemoryBlock block : blocks)
            counts.merge(block.item(), block.itemCount(), Integer::sum);
        for (var entry : counts.entrySet()) {
            ItemStack item = new ItemStack(entry.getKey());
            int remaining = entry.getValue();
            for (ItemStack stack : contents)
                if (stack != null && stack.isSimilar(item))
                    remaining -= Math.max(0, stack.getMaxStackSize() - stack.getAmount());
            if (remaining > 0)
                freeSlots -= (remaining + item.getMaxStackSize() - 1) / item.getMaxStackSize();
        }
        return freeSlots >= 0;
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (playing()
                && phase == Phase.BUILD
                && alive(player)
                && e.getHand() == EquipmentSlot.HAND
                && e.getAction().isRightClick()
                && e.getItem() != null
                && e.getItem().getType() == Material.EMERALD
                && player.getInventory().getHeldItemSlot() == 8) {
            e.setCancelled(true);
            if (ready.add(player.getUniqueId())) {
                say(I18n.tr("party.rounds.build_memory.player_ready", player.getName()));
                player.playSound(
                        player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .7f, 1.2f);
            }
            return;
        }
        if (!building(e.getPlayer())) {
            e.setCancelled(true);
            return;
        }
        // Denying block use also short-circuits native block placement on Paper.
        // Let right clicks reach placement; handleBlockPlace protects the build area and kit.
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (e.getClickedBlock() == null
                    || arena.localCell(e.getPlayer().getUniqueId(), e.getClickedBlock()) == null)
                e.setCancelled(true);
        } else if (!e.getAction().isRightClick()) e.setCancelled(true);
        else if (e.getClickedBlock() != null
                && arena.localCell(player.getUniqueId(), e.getClickedBlock()) != null)
            // Native gate toggles happen after this event; observe the resulting state next tick.
            dirty.add(player.getUniqueId());
    }

    @org.bukkit.event.EventHandler
    public void onSignOpen(io.papermc.paper.event.player.PlayerOpenSignEvent e) {
        if (e.getPlayer().getWorld().equals(world)) e.setCancelled(true);
    }

    @org.bukkit.event.EventHandler
    public void onAttachmentPhysics(BlockPhysicsEvent e) {
        if (!e.getBlock().getWorld().equals(world)) return;
        // Corrections remove dependent decorations and refund them atomically in arena.remove.
        // Native physics would drop their items, bypassing the exact kit and pickup protection.
        String name = e.getBlock().getType().name();
        if (name.endsWith("SIGN") || name.endsWith("TORCH") || name.endsWith("LANTERN"))
            e.setCancelled(true);
    }

    @Override
    protected void handleInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)
                || !building(p)
                || (e.getClickedInventory() == p.getInventory() && e.getSlot() == 8)
                || e.getHotbarButton() == 8
                || e.getSlotType() == InventoryType.SlotType.CRAFTING
                || e.getSlotType() == InventoryType.SlotType.RESULT
                || e.getAction().name().contains("DROP")
                || e.getAction() == InventoryAction.CLONE_STACK) e.setCancelled(true);
    }

    Component status() {
        return I18n.tr(
                "party.rounds.build_memory.status",
                stage,
                I18n.tr(
                        switch (phase) {
                            case SHOW -> "party.rounds.build_memory.show";
                            case BUILD -> "party.rounds.build_memory.building";
                            case REVIEW -> "party.rounds.build_memory.review";
                        }),
                Math.max(0, (deadline - elapsed() + 19) / 20));
    }
}
