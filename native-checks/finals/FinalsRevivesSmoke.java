import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.util.*;

/** Native statue entities, gravity, carried inventory, hopper protection and committed revivals. */
public final class FinalsRevivesSmoke {
    private final JavaPlugin smoke, main;
    private final World world = Bukkit.getWorlds().getFirst();
    private final Map<Block, BlockState> originals = new LinkedHashMap<>();
    private final List<P> players = new ArrayList<>();
    private final Set<UUID> downed = new HashSet<>();
    private Object helper;
    private long now;
    private int checks, completed;
    private boolean completedDefib;
    private final Location fallback = new Location(world, 970.5, 101.2, 970.5);

    private FinalsRevivesSmoke(JavaPlugin smoke, JavaPlugin main) {
        this.smoke = smoke;
        this.main = main;
    }

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsRevivesSmoke(smoke, main).run();
    }

    private Class<?> type(String name) throws Exception {
        return Class.forName(
                "me.petr1furious.potatominigames.games.finals." + name,
                true,
                main.getClass().getClassLoader());
    }

    private static Object zero(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        return null;
    }

    private static Object call(Object receiver, String name, Object... args) throws Exception {
        for (Method m : receiver.getClass().getDeclaredMethods())
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                try {
                    return m.invoke(receiver, args);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof Exception x) throw x;
                    if (e.getCause() instanceof Error x) throw x;
                    throw e;
                }
            }
        throw new NoSuchMethodException(name);
    }

    private static Object field(Object receiver, String name) throws Exception {
        Field f = receiver.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(receiver);
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private P find(Player player) {
        return players.stream().filter(p -> p.player == player).findFirst().orElseThrow();
    }

    private void block(int x, int y, int z, Material material) {
        Block b = world.getBlockAt(x, y, z);
        originals.putIfAbsent(b, b.getState());
        b.setType(material, false);
    }

    private Object statue(UUID target) throws Exception {
        return ((Map<?, ?>) field(helper, "statues")).get(target);
    }

    private Item item(UUID target) throws Exception {
        return (Item) field(statue(target), "item");
    }

    private Location point(UUID target) throws Exception {
        return (Location) call(helper, "location", target);
    }

    private void tick(long tick) throws Exception {
        now = tick;
        call(helper, "tick");
    }

    private void nativeItemTick(Item item) throws Exception {
        Object handle = item.getClass().getMethod("getHandle").invoke(item);
        handle.getClass().getMethod("tick").invoke(handle);
    }

    private int run() throws Exception {
        P carrier = new P("Carrier", 0, new Location(world, 970.5, 101, 968.5));
        P medic = new P("Medic", 0, new Location(world, 968.5, 101, 968.5));
        P target = new P("Downed", 0, fallback.clone());
        P enemy = new P("Enemy", 1, carrier.location.clone());
        players.addAll(List.of(carrier, medic, target, enemy));
        Class<?> ctx = type("FinalsRevives$Context");
        Object context =
                Proxy.newProxyInstance(
                        ctx.getClassLoader(),
                        new Class<?>[] {ctx},
                        (o, m, a) ->
                                switch (m.getName()) {
                                    case "plugin" -> main;
                                    case "world" -> world;
                                    case "tick" -> now;
                                    case "players" ->
                                            players.stream()
                                                    .filter(p -> p.online)
                                                    .map(p -> p.player)
                                                    .toList();
                                    case "canAct" -> {
                                        P p = find((Player) a[0]);
                                        yield p.online && p.active && !downed.contains(p.id);
                                    }
                                    case "downed" -> downed.contains((UUID) a[0]);
                                    case "friendly" -> {
                                        P p = find((Player) a[0]);
                                        yield players.stream()
                                                .anyMatch(
                                                        t -> t.id.equals(a[1]) && t.team == p.team);
                                    }
                                    case "inside" -> {
                                        Location p = (Location) a[0];
                                        yield p.getWorld() == world
                                                && p.getX() > 965
                                                && p.getX() < 985
                                                && p.getZ() > 965
                                                && p.getZ() < 985
                                                && p.getY() > 99
                                                && p.getY() < 115;
                                    }
                                    case "fallback" -> fallback.clone();
                                    case "recovery" -> a[0].equals(carrier.id) ? carrier.location.clone() : fallback.clone();
                                    case "safeRecovery" -> a[0] != null;
                                    case "complete" -> {
                                        UUID id = (UUID) a[0];
                                        check(
                                                downed.contains(id),
                                                "Completion sees an actually downed target");
                                        completed++;
                                        completedDefib = (boolean) a[3];
                                        downed.remove(id);
                                        call(helper, "remove", id);
                                        yield true;
                                    }
                                    default -> zero(m.getReturnType());
                                });
        Constructor<?> ctor = type("FinalsRevives").getDeclaredConstructor(ctx);
        ctor.setAccessible(true);
        helper = ctor.newInstance(context);
        try {
            for (int x = 966; x <= 984; x++)
                for (int z = 966; z <= 984; z++) block(x, 100, z, Material.STONE);
            for (int x = 968; x <= 973; x++)
                for (int y = 101; y <= 106; y++)
                    for (int z = 967; z <= 973; z++) block(x, y, z, Material.AIR);
            downed.add(target.id);
            call(helper, "down", target.player, new Location(world, 970.5, 104, 970.5));
            Item physical = item(target.id);
            Interaction click = (Interaction) field(statue(target.id), "interaction");
            check(
                    physical.isValid()
                            && click.isValid()
                            && (boolean) call(helper, "matches", physical)
                            && (boolean) call(helper, "matches", click),
                    "A death creates both a native trophy and an interaction target");
            check(
                    physical.hasGravity() && physical.isInvulnerable() && !physical.isPersistent(),
                    "The statue uses native gravity and cannot be damaged or persisted");
            check(
                    !physical.canPlayerPickup()
                            && !physical.canMobPickup()
                            && physical.isUnlimitedLifetime(),
                    "Statues cannot enter inventories by automatic pickup or expire with item age");
            for (int i = 0; i < 40; i++) {
                nativeItemTick(physical);
                tick(i);
            }
            check(
                    physical.getLocation().getY() >= 101 && physical.getLocation().getY() < 101.5,
                    "The real ItemEntity falls onto native terrain without phasing through its"
                            + " floor");
            check(
                    click.getLocation().distanceSquared(physical.getLocation()) < 1e-8,
                    "The native interaction follows the falling statue");
            InventoryPickupItemEvent hopper =
                    new InventoryPickupItemEvent(
                            Bukkit.createInventory(null, InventoryType.HOPPER), physical);
            Bukkit.getPluginManager().callEvent(hopper);
            check(
                    hopper.isCancelled(),
                    "The helper's registered listener prevents hopper theft of the physical"
                            + " statue");

            enemy.aim = point(target.id).add(0, .4, 0);
            call(helper, "interact", enemy.player);
            check(
                    !(boolean) call(helper, "carrying", enemy.id) && physical.isValid(),
                    "An enemy cannot pick up an allied revive statue");
            target.online = false;
            call(helper, "disconnected", target.id, false);
            check(
                    point(target.id) != null && physical.isValid(),
                    "Temporary target disconnection preserves the actual statue");
            carrier.offhand = new ItemStack(Material.STONE_BUTTON);
            carrier.aim = point(target.id).add(0, .4, 0);
            check(
                    (boolean) call(helper, "interact", carrier.player)
                            && (boolean) call(helper, "carrying", carrier.id),
                    "A teammate may move an offline player's preserved statue");
            call(helper, "interact", carrier.player);
            check(
                    (boolean) call(helper, "carrying", carrier.id),
                    "Duplicate same-tick air/entity interaction cannot immediately drop a newly"
                            + " picked-up statue");
            check(
                    !physical.isValid() && !click.isValid(),
                    "Picking up a statue removes its world entities without making an inventory"
                            + " duplicate");
            check(
                    carrier.offhand.getType() == Material.PLAYER_HEAD
                            && carrier.offhand.getType() != Material.TOTEM_OF_UNDYING,
                    "The carried trophy cannot trigger vanilla totem resurrection");
            check(
                    Math.abs(point(target.id).getY() - carrier.location.getY() - .8) < 1e-7,
                    "Carried statue labels use a distinct elevated position");
            check(
                    (boolean) call(helper, "throwCarried", carrier.player),
                    "Q throws the carried statue");
            Item thrown = item(target.id);
            check(
                    thrown.isValid() && thrown.getVelocity().length() > 1,
                    "Throwing applies real forward force to a native item");
            check(
                    !(boolean) call(helper, "throwCarried", carrier.player),
                    "Duplicate native/drop Q input cannot throw or duplicate the statue twice");
            check(
                    carrier.offhand.getType() == Material.STONE_BUTTON,
                    "Throwing restores the pre-carry offhand item");
            thrown.teleport(new Location(world, 970.5, world.getMinHeight() - 10, 970.5));
            tick(41);
            check(
                    !thrown.isValid()
                            && item(target.id).isValid()
                            && point(target.id).distanceSquared(carrier.location.clone().add(0, .25, 0)) < 1e-7,
                    "A thrown statue lost below map bounds returns to its carrier's departure point");
            Item recovered = item(target.id);
            recovered.teleport(new Location(world, 970.5, 98, 970.5));
            tick(42);
            check(!recovered.isValid() && point(target.id).distanceSquared(carrier.location.clone().add(0, .25, 0)) < 1e-7,
                    "Crossing the custom death floor above the world bottom also returns the statue");
            check(item(target.id).getVelocity().lengthSquared() == 0,
                    "A recovered statue loses its falling or thrown velocity");
            item(target.id).teleport(fallback);
            tick(43);

            target.online = true;
            carrier.sneaking = true;
            carrier.aim = point(target.id).add(0, .4, 0);
            call(helper, "interact", carrier.player);
            check(
                    (boolean) call(helper, "busy", carrier.id),
                    "An aimed sneak-right-click begins a manual channel");
            call(helper, "interrupt", carrier.id);
            tick(121);
            check(
                    completed == 0 && !(boolean) call(helper, "busy", carrier.id),
                    "Damage/attack interruption prevents stale native-channel completion");
            carrier.aim = point(target.id).add(0, .4, 0);
            call(helper, "interact", carrier.player);
            block(970, 102, 969, Material.STONE);
            tick(201);
            check(
                    completed == 0 && !(boolean) call(helper, "busy", carrier.id),
                    "A real wall appearing between reviver and statue cancels the hold");
            block(970, 102, 969, Material.AIR);

            carrier.sneaking = false;
            carrier.aim = point(target.id).add(0, .4, 0);
            call(helper, "interact", carrier.player);
            check(
                    (boolean) call(helper, "carrying", carrier.id),
                    "A free statue can be picked up again after channel cancellation");
            medic.aim = point(target.id).add(0, .4, 0);
            medic.slot = 2;
            check(
                    (boolean) call(helper, "defibrillateAimed", medic.player),
                    "Defibrillator directly selects and charges the actual ally-carried statue");
            tick(210);
            check(
                    !(boolean) call(helper, "defibrillateAimed", medic.player),
                    "Repeated right-click cannot restart or reconsume an accepted native"
                        + " defibrillator charge");
            tick(220);
            check(
                    completed == 0 && (boolean) call(helper, "busy", medic.id),
                    "Native Defibrillator does not skip its one-second charge");
            tick(221);
            check(
                    !(boolean) call(helper, "busy", medic.id)
                            && !(boolean) call(helper, "carrying", carrier.id)
                            && item(target.id).isValid(),
                    "A completed charge releases the carried statue into its rebuilding phase");
            check(
                    carrier.offhand.getType() == Material.STONE_BUTTON,
                    "Committed revival restores the carrier's offhand exactly once");
            downed.add(medic.id);
            medic.active = false;
            call(helper, "down", medic.player, medic.location);
            medic.online = false;
            call(helper, "disconnected", medic.id, false);
            tick(280);
            check(
                    completed == 0,
                    "Committed native revival still waits the full rebuilding duration");
            tick(281);
            check(
                    completed == 1 && completedDefib && point(target.id) == null,
                    "Committed Defibrillator survives reviver death/disconnect and tolerates"
                            + " reentrant Session removal");

            downed.add(target.id);
            call(helper, "down", target.player, fallback);
            carrier.sneaking = true;
            carrier.aim = point(target.id).add(0, .4, 0);
            call(helper, "interact", carrier.player);
            tick(360);
            check(completed == 1, "Manual revival does not finish early");
            tick(361);
            check(
                    completed == 2 && !completedDefib,
                    "Manual revival completes through the full-health callback");
            downed.add(target.id);
            call(helper, "down", target.player, fallback);
            tick(1000);
            check(
                    completed == 2 && point(target.id) != null,
                    "Individual timer unlock leaves the native statue revivable indefinitely");
            downed.clear();
            tick(1001);
            check(
                    point(target.id) == null && point(medic.id) == null,
                    "A full-team wipe or actual spawn removes all no-longer-downed statues");
            call(helper, "clear");
            check(
                    HandlerList.getRegisteredListeners(main).stream()
                            .noneMatch(listener -> listener.getListener() == helper),
                    "Cleanup unregisters its hopper listener");
            check(
                    ((Map<?, ?>) field(helper, "statues")).isEmpty()
                            && ((Map<?, ?>) field(helper, "channels")).isEmpty()
                            && ((Map<?, ?>) field(helper, "rebuilds")).isEmpty(),
                    "Cleanup releases every statue, channel and pending rebuild");
            smoke.getLogger().info("FINALS_REVIVES_SMOKE_PASS: " + checks + " assertions");
            return checks;
        } finally {
            call(helper, "clear");
            originals.values().forEach(state -> state.update(true, false));
        }
    }

    private final class P {
        final UUID id = UUID.randomUUID();
        final String name;
        final int team;
        final Player player;
        Location location, aim;
        boolean online = true, active = true, sneaking;
        int slot;
        ItemStack offhand = new ItemStack(Material.AIR);

        P(String name, int team, Location point) {
            this.name = name;
            this.team = team;
            location = point;
            PlayerInventory inventory =
                    (PlayerInventory)
                            Proxy.newProxyInstance(
                                    PlayerInventory.class.getClassLoader(),
                                    new Class<?>[] {PlayerInventory.class},
                                    (o, m, a) ->
                                            switch (m.getName()) {
                                                case "getHeldItemSlot" -> slot;
                                                case "getItemInOffHand" -> offhand;
                                                case "setItemInOffHand" -> {
                                                    offhand = (ItemStack) a[0];
                                                    yield null;
                                                }
                                                default -> zero(m.getReturnType());
                                            });
            player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (o, m, a) ->
                                            switch (m.getName()) {
                                                case "getUniqueId" -> id;
                                                case "getName" -> name;
                                                case "getWorld" -> world;
                                                case "getLocation" -> location.clone();
                                                case "getEyeLocation" -> {
                                                    Location eye = location.clone().add(0, 1.62, 0);
                                                    if (aim != null)
                                                        eye.setDirection(
                                                                aim.toVector()
                                                                        .subtract(eye.toVector()));
                                                    yield eye;
                                                }
                                                case "isOnline" -> online;
                                                case "isSneaking" -> sneaking;
                                                case "getInventory" -> inventory;
                                                case "locale" -> Locale.ENGLISH;
                                                case "equals" -> o == a[0];
                                                case "hashCode" -> id.hashCode();
                                                default -> zero(m.getReturnType());
                                            });
        }
    }
}
