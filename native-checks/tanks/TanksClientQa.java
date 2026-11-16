import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

/** Manual, op-only placement/inspection helper for a private two-client production playtest. */
public final class TanksClientQa extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { sender.sendMessage("QA: operator only."); return true; }
        try {
            Object session = session(sender);
            if (args.length == 1 && args[0].equalsIgnoreCase("state")) state(sender, session);
            else if (args.length == 2 && args[0].equalsIgnoreCase("range")) range(sender, session, Integer.parseInt(args[1]));
            else if (args.length == 3 && args[0].equalsIgnoreCase("base")) base(sender, session, team(args[1]), team(args[2]));
            else sender.sendMessage("QA: /tanksqa state | range <12|40|70> | base <moving red|blue> <base red|blue>");
        } catch (Exception failure) {
            Throwable cause = failure;
            while (cause.getCause() != null) cause = cause.getCause();
            sender.sendMessage("QA: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
        return true;
    }

    private Object session(CommandSender sender) throws Exception {
        JavaPlugin production = (JavaPlugin) Bukkit.getPluginManager().getPlugin("PotatoMinigames");
        require(production != null && production.isEnabled(), "production plugin is not enabled");
        Object manager = get(production, "sessions");
        List<Object> candidates = new ArrayList<>();
        for (Object active : (Collection<?>) call(manager, "activeSessions")) {
            Object game = call(active, "gameSession");
            if (game == null || !game.getClass().getSimpleName().equals("TanksSession")) continue;
            if (!(boolean) get(game, "built") || (boolean) get(game, "closed")) continue;
            World world = (World) get(game, "world");
            if (sender instanceof Player player && (player.getWorld() != world || !vehicles(game).containsKey(player.getUniqueId()))) continue;
            candidates.add(game);
        }
        require(candidates.size() == 1, "exactly one active tank session in the caller's world is required");
        return candidates.getFirst();
    }

    private void state(CommandSender sender, Object session) throws Exception {
        World world = (World) get(session, "world");
        Object match = get(session, "match");
        JavaPlugin production = (JavaPlugin) Bukkit.getPluginManager().getPlugin("PotatoMinigames");
        int tickets = world.getPluginChunkTickets().getOrDefault(production, List.of()).size();
        sender.sendMessage("QA world=" + world.getName() + " tick=" + get(session, "tick")
                + " started=" + call(match, "started") + " finished=" + call(match, "finished")
                + " capture(red/blue)=" + call(match, "captureProgress", new Class<?>[] {int.class}, 0)
                + "/" + call(match, "captureProgress", new Class<?>[] {int.class}, 1)
                + " entities=" + world.getEntities().size() + " retainedChunks=" + tickets);
        for (Object actor : actors(session)) {
            UUID id = (UUID) call(actor, "id");
            Player player = connections(session).get(id);
            Object vehicle = vehicles(session).get(id), visual = get(vehicle, "visual");
            int parts = ((Collection<?>) call(visual, "entities")).size();
            Location at = (Location) get(vehicle, "at");
            Entity seat = (Entity) call(visual, "seat");
            Location seated = seat.getLocation();
            sender.sendMessage("QA " + (player == null ? id : player.getName())
                    + " team=" + call(actor, "team") + " class=" + call(actor, "chassis")
                    + " hp=" + call(actor, "hp") + " alive=" + call(actor, "alive")
                    + " online=" + call(actor, "online") + " reload=" + call(actor, "reloadTicks")
                    + " shell=" + call(actor, "shell") + " scope=" + (player != null && (boolean) call(get(session, "scopes"), "active", new Class<?>[] {Player.class}, player))
                    + " base=" + call(actor, "base") + " parts=" + parts
                    + String.format(Locale.ROOT, " pos=%.2f,%.2f,%.2f hull=%.1f turret=%s/%s speed=%s seat=%.2f,%.2f,%.2f mounted=%s", at.getX(), at.getY(), at.getZ(), at.getYaw(), get(vehicle, "turretYaw"), get(vehicle, "turretPitch"), get(vehicle, "speed"), seated.getX(), seated.getY(), seated.getZ(), player != null && player.getVehicle() == seat));
        }
    }

    private void range(CommandSender sender, Object session, int range) throws Exception {
        require(range == 12 || range == 40 || range == 70, "range must be 12, 40 or 70");
        List<Object> actors = onlineActors(session);
        require(actors.size() == 2 && !call(actors.get(0), "team").equals(call(actors.get(1), "team")), "range requires exactly two alive connected tanks on opposite teams");
        World world = (World) get(session, "world");
        for (Object actor : actors) {
            int side = (int) call(actor, "team");
            Location at = new Location(world, 40, 65, side == 0 ? -range / 2.0 : range / 2.0, side == 0 ? 0 : 180, 0);
            require(clear(session, actor, at, false), "requested range footprint is blocked");
        }
        for (Object actor : actors) {
            int side = (int) call(actor, "team");
            move(session, actor, new Location(world, 40, 65, side == 0 ? -range / 2.0 : range / 2.0, side == 0 ? 0 : 180, 0));
        }
        call(session, "visibility");
        sender.sendMessage("QA placed two current tanks " + range + " blocks apart at x=40; HP/reload/shell/class preserved.");
    }

    private void base(CommandSender sender, Object session, int moving, int target) throws Exception {
        Object arena = get(session, "arena");
        Location center = (Location) call(arena, "base", new Class<?>[] {int.class}, target);
        int moved = 0;
        for (Object actor : onlineActors(session)) {
            if ((int) call(actor, "team") != moving) continue;
            Location chosen = null;
            for (int slot = 0; slot < 6; slot++) {
                Location at = center.clone().add((slot % 3 - 1) * 4, 0, (slot / 3 - .5) * 5);
                at.setYaw(moving == 0 ? 0 : 180);
                at.setPitch(0);
                if (clear(session, actor, at, true)) { chosen = at; break; }
            }
            require(chosen != null, "no nonoverlapping full-tank slot remains on that capture disk");
            move(session, actor, chosen);
            moved++;
        }
        require(moved > 0, "requested moving team has no alive connected tank");
        call(session, "visibility");
        sender.sendMessage("QA moved " + moved + " current " + (moving == 0 ? "red" : "blue") + " tank(s) into " + (target == 0 ? "red" : "blue") + " base; production capture rules remain active.");
    }

    private boolean clear(Object session, Object actor, Location at, boolean avoidTanks) throws Exception {
        Object chassis = call(actor, "chassis");
        return (boolean) call(get(session, "arena"), "canDrive", new Class<?>[] {Location.class, chassis.getClass()}, at, chassis)
                && (!avoidTanks || (boolean) call(session, "clearOfTanks", new Class<?>[] {UUID.class, Location.class}, call(actor, "id"), at));
    }

    private void move(Object session, Object actor, Location at) throws Exception {
        UUID id = (UUID) call(actor, "id");
        Player player = connections(session).get(id);
        Object vehicle = vehicles(session).get(id), visual = get(vehicle, "visual");
        call(get(session, "scopes"), "stop", new Class<?>[] {Player.class}, player);
        set(vehicle, "at", at);
        set(vehicle, "speed", 0.0);
        set(vehicle, "trackSpeed", 0.0);
        set(vehicle, "turretYaw", at.getYaw());
        set(vehicle, "turretPitch", 0f);
        double max = ((Number) call(call(actor, "chassis"), "maxHp")).doubleValue();
        call(visual, "update", new Class<?>[] {Location.class, float.class, float.class, double.class, double.class}, at, at.getYaw(), 0f, call(actor, "hp"), max);
        call(session, "mount", new Class<?>[] {Player.class, actor.getClass(), vehicle.getClass(), boolean.class}, player, actor, vehicle, false);
    }

    private List<Object> onlineActors(Object session) throws Exception {
        List<Object> result = new ArrayList<>();
        Object match = get(session, "match");
        require(!(boolean) call(match, "finished"), "battle has already ended");
        for (Object actor : actors(session)) {
            Player player = connections(session).get(call(actor, "id"));
            if ((boolean) call(actor, "alive") && (boolean) call(actor, "online") && player != null && player.isOnline() && player.getWorld() == get(session, "world")) result.add(actor);
        }
        return result;
    }

    @SuppressWarnings("unchecked") private Map<UUID, Object> vehicles(Object session) throws Exception { return (Map<UUID, Object>) get(session, "vehicles"); }
    @SuppressWarnings("unchecked") private Map<UUID, Player> connections(Object session) throws Exception { return (Map<UUID, Player>) get(session, "connections"); }
    private Collection<?> actors(Object session) throws Exception { return (Collection<?>) call(get(session, "match"), "actors"); }
    private int team(String value) { if (value.equalsIgnoreCase("red")) return 0; if (value.equalsIgnoreCase("blue")) return 1; throw new IllegalArgumentException("team/base must be red or blue"); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static Object get(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static void set(Object object, String name, Object value) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
    private static Object call(Object object, String name) throws Exception { return call(object, name, new Class<?>[0]); }
    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception { Method method = object.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(object, args); }
}
