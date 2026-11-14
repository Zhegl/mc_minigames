import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiPredicate;

/** Native menus, item metadata and swept TNT contact; only player transport is substituted. */
final class PartyPvpSmoke {
    private static final String PKG = "me.petr1furious.potatominigames.games.party.";
    private static int assertions;

    static int run(ClassLoader loader, World world, JavaPlugin plugin) throws Exception {
        assertions = 0;
        menus(loader, world, plugin);
        bombs(loader, world);
        return assertions;
    }

    private static void menus(ClassLoader loader, World world, JavaPlugin plugin) throws Exception {
        Client client = new Client(world), rival = new Client(world);
        Object queueMenu =
                HandlerList.getRegisteredListeners(plugin).stream()
                        .map(listener -> listener.getListener())
                        .filter(listener ->
                                listener.getClass().getName().endsWith("core.queue.QueueMenu"))
                        .findFirst()
                        .orElseThrow();
        Object sessions = field(plugin.getClass(), "sessions").get(plugin);
        Collection<?> games =
                (Collection<?>) sessions.getClass().getMethod("games").invoke(sessions);
        Object party =
                games.stream()
                        .filter(game -> game.getClass().getName().equals(PKG + "PartyGame"))
                        .findFirst()
                        .orElseThrow();
        Class<?> minigame =
                Class.forName("me.petr1furious.potatominigames.core.game.Minigame", true, loader);
        queueMenu
                .getClass()
                .getMethod("open", Player.class, minigame)
                .invoke(queueMenu, client.player, party);
        int slot = slot(client.top, "PvP");
        require(slot >= 0, "the native Party menu has one PvP entry");
        click(queueMenu, client, slot);
        Set<String> labels = new HashSet<>();
        for (ItemStack item : client.top.getContents())
            if (item != null && item.getType() != Material.ARROW) labels.add(name(item));
        require(
                labels.equals(Set.of("Классик", "Групповое", "Выбор китов")),
                "native grouped menu exposes all three enabled, localized PvP queues");
        click(queueMenu, client, slot(client.top, "Назад"));
        require(slot(client.top, "PvP") >= 0, "the variant menu returns to Party games");

        Class<?> duel = Class.forName(PKG + "DuelSession", true, loader);
        Class<?> services =
                Class.forName(
                        "me.petr1furious.potatominigames.core.game.SessionServices", true, loader);
        Class<?> config = Class.forName(PKG + "PartyConfig", true, loader);
        Object session =
                constructor(duel, services, config, boolean.class, boolean.class, boolean.class)
                        .newInstance(null, null, false, true, true);
        Object menu = field(duel, "kitMenu").get(session);
        Class<?> kits = Class.forName(PKG + "PvpClass", true, loader);
        method(menu.getClass(), "open", Player.class, kits).invoke(menu, client.player, null);
        Inventory inventory = client.top;
        int count = 0, first = -1;
        for (int i = 0; i < inventory.getSize(); i++) {
            Object choice =
                    method(menu.getClass(), "choice", Inventory.class, Player.class, int.class)
                            .invoke(menu, inventory, client.player, i);
            if (choice == null) continue;
            count++;
            if (first < 0) first = i;
            require(
                    inventory.getItem(i) != null
                            && !name(inventory.getItem(i)).contains("potatominigames."),
                    "each class has a native localized role/weapon icon");
        }
        require(
                count == kits.getEnumConstants().length && count == 15,
                "all fifteen kits are selectable");
        require(
                method(menu.getClass(), "choice", Inventory.class, Player.class, int.class)
                                .invoke(menu, inventory, rival.player, first)
                        == null,
                "a foreign player cannot select from another player's inventory");
        InventoryClickEvent stale =
                new InventoryClickEvent(
                        client.view(),
                        InventoryType.SlotType.CONTAINER,
                        first,
                        ClickType.SHIFT_LEFT,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY);
        method(duel, "onInventoryClick", InventoryClickEvent.class).invoke(session, stale);
        require(
                stale.isCancelled(),
                "a stale class view cannot transfer an icon or start selection");
        InventoryDragEvent drag =
                new InventoryDragEvent(
                        client.view(),
                        new ItemStack(Material.STONE),
                        new ItemStack(Material.STONE),
                        false,
                        Map.of(first, new ItemStack(Material.STONE)));
        method(duel, "onInventoryDrag", InventoryDragEvent.class).invoke(session, drag);
        require(drag.isCancelled(), "native drag events cannot put items into a class menu");
        ItemStack opener =
                (ItemStack)
                        method(menu.getClass(), "opener", Player.class).invoke(menu, client.player);
        require(
                opener.getType() == Material.NETHER_STAR && name(opener).equals("Выбор класса"),
                "the reopen item is localized for its recipient");
        method(menu.getClass(), "close", Player.class).invoke(menu, client.player);
        require(
                client.closed > 0 && client.top != inventory,
                "selection cleanup closes its owned view");
    }

    private static void bombs(ClassLoader loader, World world) throws Exception {
        world.getChunkAt(7, 8).load();
        world.getChunkAt(8, 8).load();
        Class<?> type = Class.forName(PKG + "PvpBombs", true, loader);
        Zombie target =
                world.spawn(
                        new Location(world, 130, 200, 130),
                        Zombie.class,
                        false,
                        zombie -> {
                            zombie.setBaby(false);
                            zombie.setAI(false);
                            zombie.setGravity(false);
                        });
        Client owner = new Client(world);
        Object bombs =
                constructor(type, BiPredicate.class)
                        .newInstance(
                                (BiPredicate<Player, Entity>) (player, entity) -> entity == target);
        Location start = new Location(world, 127, 200, 130);
        TNTPrimed hit = world.spawn(start, TNTPrimed.class);
        TNTPrimed covered = null, placed = null, rain = null;
        var wall = world.getBlockAt(128, 200, 130);
        var old = wall.getBlockData().clone();
        try {
            method(type, "configure", TNTPrimed.class, Player.class, Vector.class)
                    .invoke(null, hit, null, new Vector(1, 0, 0));
            require(
                    hit.getFuseTicks() == 30 && hit.getYield() == 2 && !hit.isIncendiary(),
                    "manual charges have the buffed native fuse and unchanged bounded blast size");
            method(type, "track", Player.class, TNTPrimed.class, boolean.class)
                    .invoke(bombs, owner.player, hit, true);
            hit.teleport(start.clone().add(6, 0, 0));
            method(type, "tick").invoke(bombs);
            require(
                    hit.getFuseTicks() == 0
                            && hit.getVelocity().lengthSquared() == 0
                            && hit.getLocation().getX() < target.getLocation().getX(),
                    "real Paper swept collision arms TNT at a crossed native target, not past it");
            require(
                    method(type, "owner", Entity.class).invoke(bombs, hit) == owner.player,
                    "contact keeps the original tracked owner for the native explosion");
            covered = world.spawn(start, TNTPrimed.class);
            covered.setFuseTicks(30);
            method(type, "track", Player.class, TNTPrimed.class, boolean.class)
                    .invoke(bombs, owner.player, covered, true);
            wall.setType(Material.STONE, false);
            covered.teleport(start.clone().add(6, 0, 0));
            method(type, "tick").invoke(bombs);
            require(
                    covered.getFuseTicks() == 30,
                    "native solid cover prevents a contact hit through a wall");
            placed = world.spawn(target.getLocation(), TNTPrimed.class);
            placed.setFuseTicks(30);
            method(type, "track", Player.class, TNTPrimed.class)
                    .invoke(bombs, owner.player, placed);
            rain = world.spawn(target.getLocation(), TNTPrimed.class);
            method(
                            Class.forName(PKG + "PvpAirstrike", true, loader),
                            "configure",
                            TNTPrimed.class,
                            Player.class)
                    .invoke(null, rain, null);
            method(type, "trackAirstrike", Player.class, TNTPrimed.class)
                    .invoke(bombs, owner.player, rain);
            method(type, "tick").invoke(bombs);
            require(
                    placed.getFuseTicks() == 30 && rain.getFuseTicks() == 18,
                    "placed and airstrike charges keep their fuses even touching a native target");
            require(
                    (double)
                                            method(type, "blastDamage", Entity.class, double.class)
                                                    .invoke(bombs, placed, 100d)
                                    == 16
                            && (double)
                                            method(type, "blastDamage", Entity.class, double.class)
                                                    .invoke(bombs, rain, 100d)
                                    == 9,
                    "native tracked charges use distinct buffed damage caps");
        } finally {
            method(type, "clear").invoke(bombs);
            if (hit.isValid()) hit.remove();
            if (covered != null && covered.isValid()) covered.remove();
            if (placed != null && placed.isValid()) placed.remove();
            if (rain != null && rain.isValid()) rain.remove();
            target.remove();
            wall.setBlockData(old, false);
        }
        require(
                !hit.isValid()
                        && (int)
                                        method(type, "active", UUID.class)
                                                .invoke(bombs, owner.player.getUniqueId())
                                == 0,
                "native contact testing leaves no live charges behind");
    }

    private static void click(Object menu, Client player, int slot) throws Exception {
        require(slot >= 0, "navigation item exists");
        InventoryClickEvent event =
                new InventoryClickEvent(
                        player.view(),
                        InventoryType.SlotType.CONTAINER,
                        slot,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        menu.getClass().getMethod("onClick", InventoryClickEvent.class).invoke(menu, event);
        require(event.isCancelled(), "menu navigation consumes the inventory click");
    }

    private static int slot(Inventory inventory, String label) {
        for (int i = 0; i < inventory.getSize(); i++)
            if (inventory.getItem(i) != null && name(inventory.getItem(i)).equals(label)) return i;
        return -1;
    }

    private static String name(ItemStack item) {
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    private static final class Client {
        final UUID id = UUID.randomUUID();
        final Inventory bottom = Bukkit.createInventory(null, 36);
        final Player player;
        Inventory top = bottom;
        int closed;

        Client(World world) {
            player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> id;
                                                case "locale" -> Locale.forLanguageTag("ru");
                                                case "getLocale" -> "ru_ru";
                                                case "getWorld" -> world;
                                                case "getName" -> "PartyNativeMenu";
                                                case "isOnline" -> true;
                                                case "getOpenInventory" -> view();
                                                case "openInventory" -> {
                                                    top = (Inventory) args[0];
                                                    yield view();
                                                }
                                                case "closeInventory" -> {
                                                    closed++;
                                                    top = bottom;
                                                    yield null;
                                                }
                                                case "hashCode" -> System.identityHashCode(proxy);
                                                case "equals" -> proxy == args[0];
                                                default ->
                                                        throw new AssertionError(
                                                                "Unexpected player transport: "
                                                                        + method.getName());
                                            });
        }

        InventoryView view() {
            Inventory shown = top;
            return (InventoryView)
                    Proxy.newProxyInstance(
                            InventoryView.class.getClassLoader(),
                            new Class<?>[] {InventoryView.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getPlayer" -> player;
                                        case "getTopInventory" -> shown;
                                        case "getBottomInventory" -> bottom;
                                        case "getType" -> InventoryType.CHEST;
                                        case "getInventory" ->
                                                (int) args[0] < shown.getSize() ? shown : bottom;
                                        case "convertSlot" ->
                                                (int) args[0] < shown.getSize()
                                                        ? args[0]
                                                        : (int) args[0] - shown.getSize();
                                        case "getItem" -> shown.getItem((int) args[0]);
                                        case "getCursor" -> new ItemStack(Material.AIR);
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected inventory view: "
                                                                + method.getName());
                                    });
        }
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

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
