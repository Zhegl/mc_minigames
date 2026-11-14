import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * Real native terrain, containers and item metadata; only connected-player transport is
 * substituted.
 */
public final class ParkourSmoke extends JavaPlugin {
    private static final String PKG = "me.petr1furious.potatominigames.games.party.";
    private ClassLoader loader;
    private Object course, arena;
    private World world;
    private int assertions;
    private final UUID alice = new UUID(14, 1), bob = new UUID(14, 2);

    @Override
    public void onEnable() {
        Bukkit.getScheduler()
                .runTask(
                        this,
                        () -> {
                            try {
                                loader =
                                        Objects.requireNonNull(
                                                        Bukkit.getPluginManager()
                                                                .getPlugin("PotatoMinigames"))
                                                .getClass()
                                                .getClassLoader();
                                world = Bukkit.getWorlds().getFirst();
                                Class<?> kind = type("ParkourCourse$Kind");
                                List<Object> selected = new ArrayList<>();
                                for (String name :
                                        List.of(
                                                "CHEST_DROP",
                                                "SWIM",
                                                "BOAT_ICE",
                                                "FLIGHT",
                                                "LADDER_TRAVERSE",
                                                "POT_ASCENT",
                                                "POWDER_SNOW_DROP",
                                                "HONEY_TRAVERSE"))
                                    selected.add(
                                            Arrays.stream(kind.getEnumConstants())
                                                    .filter(k -> k.toString().equals(name))
                                                    .findFirst()
                                                    .orElseThrow());
                                course =
                                        constructor(
                                                        type("ParkourCourse"),
                                                        List.class,
                                                        boolean.class)
                                                .newInstance(selected, false);
                                arena =
                                        constructor(
                                                        type("ParkourArena"),
                                                        World.class,
                                                        Collection.class,
                                                        type("ParkourCourse"))
                                                .newInstance(world, List.of(alice, bob), course);
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            if (!(boolean)
                                                    call(
                                                            arena,
                                                            "buildSlice",
                                                            new Class<?>[] {int.class},
                                                            4096)) return;
                                            cancel();
                                            verify();
                                            getDataFolder().mkdirs();
                                            Files.writeString(
                                                    getDataFolder().toPath().resolve("result.txt"),
                                                    "PARKOUR_SMOKE_PASS: "
                                                            + assertions
                                                            + " native assertions\n");
                                            getLogger()
                                                    .info(
                                                            "PARKOUR_SMOKE_PASS: "
                                                                    + assertions
                                                                    + " native assertions");
                                            Bukkit.shutdown();
                                        } catch (Throwable failure) {
                                            cancel();
                                            fail(failure);
                                        }
                                    }
                                }.runTaskTimer(this, 1, 1);
                            } catch (Throwable failure) {
                                fail(failure);
                            }
                        });
    }

    private void verify() throws Exception {
        Location a =
                (Location)
                        call(
                                arena,
                                "chestLocation",
                                new Class<?>[] {UUID.class, int.class},
                                alice,
                                0);
        Location b =
                (Location)
                        call(
                                arena,
                                "chestLocation",
                                new Class<?>[] {UUID.class, int.class},
                                bob,
                                0);
        require(
                a.getBlock().getState() instanceof Chest
                        && b.getBlock().getState() instanceof Chest,
                "Sliced construction creates actual native chest block entities in both lanes");
        Chest ca = (Chest) a.getBlock().getState(), cb = (Chest) b.getBlock().getState();
        require(
                a.clone().add(0, 1, 0).getBlock().getType() == Material.YELLOW_STAINED_GLASS,
                "The chest lid is covered by a colliding glass block");
        require(!blocked(a), "The real native chest remains openable under glass");
        var above = a.clone().add(0, 1, 0).getBlock();
        var glass = above.getBlockData();
        above.setType(Material.STONE, false);
        require(blocked(a), "The native chest obstruction check detects an opaque replacement");
        above.setBlockData(glass, false);
        require(!blocked(a), "Restoring glass restores native chest access");
        for (UUID id : List.of(alice, bob))
            call(arena, "refillChest", new Class<?>[] {UUID.class, int.class}, id, 0);
        require(
                bucket(ca.getBlockInventory()) && bucket(cb.getBlockInventory()),
                "Each lane receives exactly one native water bucket");
        ca.getBlockInventory().clear();
        require(
                bucket(cb.getBlockInventory()),
                "Taking one player's bucket never changes the rival's container");
        call(arena, "refillChest", new Class<?>[] {UUID.class, int.class}, alice, 0);
        call(arena, "refillChest", new Class<?>[] {UUID.class, int.class}, alice, 0);
        require(
                bucket(ca.getBlockInventory()),
                "Repeated attempt refills clear old contents rather than duplicate buckets");
        inventoryPermissions(ca.getBlockInventory(), cb.getBlockInventory());

        Object swim = call(course, "island", new Class<?>[] {int.class}, 1);
        int x = (int) (double) call(swim, "centerX", new Class<?>[0]);
        int y = (int) call(swim, "y", new Class<?>[0]) + 1;
        int z = (int) call(swim, "z", new Class<?>[0]) + (int) call(swim, "depth", new Class<?>[0]);
        var source = world.getBlockAt(x, y, z);
        var flow = world.getBlockAt(x, y, z + 1);
        require(
                source.getType() == Material.AIR && flow.getType() == Material.AIR,
                "The built swim tunnel starts dry");
        for (var block : List.of(source, flow))
            require(
                    (boolean)
                            call(
                                    arena,
                                    "remember",
                                    new Class<?>[] {
                                        UUID.class,
                                        int.class,
                                        org.bukkit.block.Block.class,
                                        org.bukkit.block.data.BlockData.class
                                    },
                                    alice,
                                    1,
                                    block,
                                    block.getBlockData()),
                    "The passage admits player water and journals original air");
        source.setType(Material.WATER, false);
        flow.setBlockData(Bukkit.createBlockData("minecraft:water[level=3]"), false);
        call(arena, "reset", new Class<?>[] {UUID.class}, alice);
        require(
                source.getType() == Material.AIR && flow.getType() == Material.AIR,
                "Native source and flowing water both roll back to dry air");

        List<?> supplies = (List<?>) call(course, "supplies", new Class<?>[] {int.class}, 3);
        int rockets = 0;
        for (Object supply : supplies) {
            ItemStack item =
                    (ItemStack)
                            method(type("ParkourRaceSession"), "supplyItem", supply.getClass())
                                    .invoke(null, supply);
            if (item.getType() != Material.FIREWORK_ROCKET) continue;
            rockets += item.getAmount();
            FireworkMeta meta = (FireworkMeta) item.getItemMeta();
            require(
                    meta.getPower() == 2 && meta.getEffectsSize() == 0,
                    "The sole rocket has real Flight Duration 2 metadata and no damaging star");
        }
        require(rockets == 1, "The production kit factory creates exactly one rocket");
        catchAndIce();
        assertions +=
                PartyPvpSmoke.run(
                        loader,
                        world,
                        (JavaPlugin)
                                Objects.requireNonNull(
                                        Bukkit.getPluginManager().getPlugin("PotatoMinigames")));
    }

    private void catchAndIce() throws Exception {
        Object honeyFrom = call(course, "island", new Class<?>[] {int.class}, 7);
        Object honeyTo = call(course, "island", new Class<?>[] {int.class}, 8);
        int hy = (int) call(honeyTo, "y", new Class<?>[0]);
        int hz = (int) call(honeyTo, "z", new Class<?>[0]);
        int afterCheckpoint =
                (int) call(honeyFrom, "z", new Class<?>[0])
                        + (int) call(honeyFrom, "depth", new Class<?>[0]);
        require(hz - 3 >= afterCheckpoint, "native honey starts beyond the preceding checkpoint");
        Object iceFrom = call(course, "island", new Class<?>[] {int.class}, 2);
        Object iceTo = call(course, "island", new Class<?>[] {int.class}, 3);
        int iy = (int) call(iceFrom, "y", new Class<?>[0]);
        int start = (int) call(iceFrom, "z", new Class<?>[0]) + 7;
        int end = (int) call(iceTo, "z", new Class<?>[0]) - 1;
        for (int lane = 0; lane < 2; lane++) {
            int shift = lane * 28;
            for (int z = hz - 3; z < hz; z++) {
                for (int y = hy; y < hy + 6; y++)
                    require(
                            world.getBlockAt(shift, y, z).getType() == Material.HONEY_BLOCK,
                            "both built lanes contain the complete short lower honey patch");
                require(
                        world.getBlockAt(shift, hy + 6, z).getType() == Material.DEEPSLATE_TILES
                                && !world.getBlockAt(shift, hy + 6, z).isPassable(),
                        "native solid caps cover every soft honey landing surface");
            }
            for (int z = start; z <= end; z++) {
                int columns = 0;
                for (int x = 0; x < 27; x++) {
                    if (world.getBlockAt(shift + x, iy, z).getType() != Material.PACKED_ICE)
                        continue;
                    columns++;
                    require(
                            world.getBlockAt(shift + x, iy + 1, z).isPassable()
                                    && world.getBlockAt(shift + x, iy + 2, z).isPassable(),
                            "native road collision leaves room for a boat hull and passenger");
                }
                require(
                        columns == 5,
                        "the sliced native ice road stays five blocks wide through bends");
            }
        }
    }

    private void inventoryPermissions(Inventory own, Inventory rival) throws Exception {
        Inventory storage = Bukkit.createInventory(null, 36);
        PlayerInventory bottom =
                (PlayerInventory)
                        Proxy.newProxyInstance(
                                getClassLoader(),
                                new Class<?>[] {PlayerInventory.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getItem" -> storage.getItem((int) args[0]);
                                            case "equals" -> proxy == args[0];
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected player inventory call: "
                                                                    + method.getName());
                                        });
        boolean[] grounded = {false};
        Player player =
                (Player)
                        Proxy.newProxyInstance(
                                getClassLoader(),
                                new Class<?>[] {Player.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getUniqueId" -> alice;
                                            case "getWorld" -> world;
                                            case "getGameMode" -> GameMode.SURVIVAL;
                                            case "isDead" -> false;
                                            case "isOnGround" -> grounded[0];
                                            case "getInventory" -> bottom;
                                            case "getName" -> "ParkourSmoke";
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected player call: "
                                                                    + method.getName());
                                        });
        Object context =
                method(
                                type("PartyRoundContext"),
                                "standalone",
                                Class.forName(
                                        "me.petr1furious.potatominigames.core.game.SessionServices",
                                        true,
                                        loader))
                        .invoke(null, new Object[] {null});
        Object session =
                constructor(
                                type("ParkourRaceSession"),
                                Class.forName(
                                        "me.petr1furious.potatominigames.core.game.SessionServices",
                                        true,
                                        loader),
                                type("PartyConfig"),
                                type("PartyRoundContext"))
                        .newInstance(null, null, context);
        field(type("PartySurvivalSession"), "world").set(session, world);
        field(type("PartySurvivalSession"), "playing").set(session, true);
        field(type("PartySurvivalSession"), "standings")
                .set(
                        session,
                        constructor(type("SurvivalStandings"), Collection.class)
                                .newInstance(List.of(alice, bob)));
        ((Set<UUID>) field(type("PartySurvivalSession"), "connected").get(session))
                .addAll(List.of(alice, bob));
        field(type("ParkourRaceSession"), "course").set(session, course);
        field(type("ParkourRaceSession"), "arena").set(session, arena);
        Object race =
                constructor(type("ParkourRace"), Collection.class, int.class)
                        .newInstance(List.of(alice, bob), 7);
        field(type("ParkourRaceSession"), "race").set(session, race);
        InventoryView view = view(player, own, bottom), other = view(player, rival, bottom);
        require(open(session, view), "The falling owner can open the native chest inventory");
        require(
                click(session, view, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0),
                "The native first-hotbar-key bucket transfer is permitted");
        require(
                click(
                        session,
                        view,
                        ClickType.SHIFT_LEFT,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY,
                        -1),
                "A native shift-click can also take the bucket");
        require(
                !click(session, view, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 8),
                "Taking loot cannot swap away the checkpoint clock");
        require(
                !open(session, other)
                        && !click(
                                session,
                                other,
                                ClickType.NUMBER_KEY,
                                InventoryAction.HOTBAR_SWAP,
                                0),
                "A rival chest is rejected by both open and click handlers");
        grounded[0] = true;
        require(
                !open(session, view)
                        && !click(
                                session,
                                view,
                                ClickType.NUMBER_KEY,
                                InventoryAction.HOTBAR_SWAP,
                                0),
                "Standing players cannot take midair loot");
        grounded[0] = false;
        call(race, "reach", new Class<?>[] {UUID.class, int.class, long.class}, alice, 1, 100L);
        require(
                !open(session, view)
                        && !click(
                                session,
                                view,
                                ClickType.NUMBER_KEY,
                                InventoryAction.HOTBAR_SWAP,
                                0),
                "Advancing the checkpoint invalidates the previous chest view");
        powderSnowPlacement(session, player, race);
    }

    private void powderSnowPlacement(Object session, Player player, Object race) throws Exception {
        Object swim = call(course, "island", new Class<?>[] {int.class}, 1);
        int x = (int) (double) call(swim, "centerX", new Class<?>[0]);
        int y = (int) call(swim, "y", new Class<?>[0]) + 1;
        int z =
                (int) call(swim, "z", new Class<?>[0])
                        + (int) call(swim, "depth", new Class<?>[0])
                        + 1;
        Block wrongTask = world.getBlockAt(x, y, z);
        var before = wrongTask.getState();
        wrongTask.setType(Material.POWDER_SNOW, false);
        var denied =
                new BlockPlaceEvent(
                        wrongTask,
                        before,
                        wrongTask.getRelative(BlockFace.DOWN),
                        new ItemStack(Material.POWDER_SNOW_BUCKET),
                        player,
                        true,
                        EquipmentSlot.HAND);
        call(session, "handleBlockPlace", new Class<?>[] {BlockPlaceEvent.class}, denied);
        require(denied.isCancelled(), "A powder snow bucket cannot place snow on the swim task");
        wrongTask.setBlockData(before.getBlockData(), false);

        for (int checkpoint = 2; checkpoint <= 6; checkpoint++)
            call(
                    race,
                    "reach",
                    new Class<?>[] {UUID.class, int.class, long.class},
                    alice,
                    checkpoint,
                    checkpoint * 100L);
        Location goal =
                (Location)
                        call(arena, "checkpoint", new Class<?>[] {UUID.class, int.class}, alice, 7);
        Block snow = goal.getBlock();
        before = snow.getState();
        require(
                snow.getType() == Material.AIR,
                "The snow clutch has clear air above its landing deck");
        snow.setType(Material.POWDER_SNOW, false);
        var placed =
                new BlockPlaceEvent(
                        snow,
                        before,
                        snow.getRelative(BlockFace.DOWN),
                        new ItemStack(Material.POWDER_SNOW_BUCKET),
                        player,
                        true,
                        EquipmentSlot.HAND);
        call(session, "handleBlockPlace", new Class<?>[] {BlockPlaceEvent.class}, placed);
        require(
                !placed.isCancelled()
                        && (boolean)
                                call(
                                        arena,
                                        "changed",
                                        new Class<?>[] {UUID.class, Block.class},
                                        alice,
                                        snow),
                "The real BlockPlaceEvent path accepts and journals the snow bucket on its own"
                    + " task");
        call(arena, "reset", new Class<?>[] {UUID.class}, alice);
        require(snow.getType() == Material.AIR, "Clock reset removes the placed powder snow");

        Block floor = snow.getRelative(BlockFace.DOWN);
        before = floor.getState();
        floor.setType(Material.POWDER_SNOW, false);
        var terrain =
                new BlockPlaceEvent(
                        floor,
                        before,
                        floor.getRelative(BlockFace.DOWN),
                        new ItemStack(Material.POWDER_SNOW_BUCKET),
                        player,
                        true,
                        EquipmentSlot.HAND);
        call(session, "handleBlockPlace", new Class<?>[] {BlockPlaceEvent.class}, terrain);
        require(
                terrain.isCancelled(),
                "The snow bucket cannot replace protected checkpoint terrain");
        floor.setBlockData(before.getBlockData(), false);

        Location other =
                (Location)
                        call(arena, "checkpoint", new Class<?>[] {UUID.class, int.class}, bob, 7);
        Block foreign = other.getBlock();
        before = foreign.getState();
        foreign.setType(Material.POWDER_SNOW, false);
        var rival =
                new BlockPlaceEvent(
                        foreign,
                        before,
                        foreign.getRelative(BlockFace.DOWN),
                        new ItemStack(Material.POWDER_SNOW_BUCKET),
                        player,
                        true,
                        EquipmentSlot.HAND);
        call(session, "handleBlockPlace", new Class<?>[] {BlockPlaceEvent.class}, rival);
        require(rival.isCancelled(), "The snow bucket cannot modify another racer's lane");
        foreign.setBlockData(before.getBlockData(), false);
    }

    private InventoryView view(Player player, Inventory top, PlayerInventory bottom) {
        return (InventoryView)
                Proxy.newProxyInstance(
                        getClassLoader(),
                        new Class<?>[] {InventoryView.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getPlayer" -> player;
                                    case "getTopInventory" -> top;
                                    case "getBottomInventory" -> bottom;
                                    case "getType" -> InventoryType.CHEST;
                                    case "getInventory" ->
                                            (int) args[0] < top.getSize() ? top : bottom;
                                    case "convertSlot" -> args[0];
                                    case "getItem" -> top.getItem((int) args[0]);
                                    case "getCursor" -> new ItemStack(Material.AIR);
                                    default ->
                                            throw new AssertionError(
                                                    "Unexpected inventory view call: "
                                                            + method.getName());
                                });
    }

    private boolean open(Object session, InventoryView view) throws Exception {
        var event = new InventoryOpenEvent(view);
        call(session, "onInventoryOpen", new Class<?>[] {InventoryOpenEvent.class}, event);
        return !event.isCancelled();
    }

    private boolean click(
            Object session, InventoryView view, ClickType click, InventoryAction action, int hotbar)
            throws Exception {
        var event =
                new InventoryClickEvent(
                        view, InventoryType.SlotType.CONTAINER, 13, click, action, hotbar);
        call(session, "handleInventoryClick", new Class<?>[] {InventoryClickEvent.class}, event);
        return !event.isCancelled();
    }

    private boolean bucket(Inventory inventory) {
        return Arrays.stream(inventory.getContents())
                        .filter(Objects::nonNull)
                        .filter(item -> !item.isEmpty())
                        .mapToInt(item ->
                                item.getType() == Material.WATER_BUCKET ? item.getAmount() : 1000)
                        .sum()
                == 1;
    }

    private boolean blocked(Location at) throws Exception {
        Object handle = world.getClass().getMethod("getHandle").invoke(world);
        Class<?> pos = Class.forName("net.minecraft.core.BlockPos");
        return (boolean)
                Class.forName("net.minecraft.world.level.block.ChestBlock")
                        .getMethod(
                                "isChestBlockedAt",
                                Class.forName("net.minecraft.world.level.LevelAccessor"),
                                pos)
                        .invoke(
                                null,
                                handle,
                                pos.getConstructor(int.class, int.class, int.class)
                                        .newInstance(
                                                at.getBlockX(), at.getBlockY(), at.getBlockZ()));
    }

    private Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(PKG + name, true, loader);
    }

    private static Constructor<?> constructor(Class<?> type, Class<?>... args) throws Exception {
        var c = type.getDeclaredConstructor(args);
        c.setAccessible(true);
        return c;
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        var m = type.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        return method(target.getClass(), name, types).invoke(target, args);
    }

    private void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private void fail(Throwable failure) {
        getLogger().log(Level.SEVERE, "PARKOUR_SMOKE_FAIL", failure);
        Bukkit.shutdown();
    }
}
