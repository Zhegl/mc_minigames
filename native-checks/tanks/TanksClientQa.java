import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.FluidCollisionMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Development-only placement and inspection for a private production playtest.
 * Drive uses bounded server input events; it neither sends OS keys nor proves physical-key input.
 */
public final class TanksClientQa extends JavaPlugin {
    private final Map<UUID, DriveRun> drives = new HashMap<>();
    private final Map<CommandSender, ProbeRun> probes = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { sender.sendMessage("QA: operator only."); return true; }
        try {
            Object session = session(sender);
            if (args.length == 1 && args[0].equalsIgnoreCase("state")) state(sender, session);
            else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) spawn(sender, session, team(args[1]));
            else if (args.length == 4 && args[0].equalsIgnoreCase("drive")) drive(sender, session, team(args[1]), direction(args[2]), ticks(args[3]));
            else if (args.length == 2 && args[0].equalsIgnoreCase("probe")) probe(sender, session, ticks(args[1]));
            else if (args.length == 2 && args[0].equalsIgnoreCase("view")) view(sender, session, Integer.parseInt(args[1]));
            else if (args.length == 2 && args[0].equalsIgnoreCase("range")) range(sender, session, Integer.parseInt(args[1]));
            else if (args.length == 1 && args[0].equalsIgnoreCase("cover")) placement(sender, session, 12, true);
            else if (args.length == 3 && args[0].equalsIgnoreCase("angle")) angle(sender, session, team(args[1]), args[2]);
            else if (args.length == 3 && args[0].equalsIgnoreCase("base")) base(sender, session, team(args[1]), team(args[2]));
            else sender.sendMessage("QA: /tanksqa state | spawn <red|blue> | drive <red|blue> <forward|reverse|left|right|forward-left|forward-right> <ticks1..100> | probe <delayTicks1..100> | view <4..10> | range <12|40|70|86> | cover | angle <red|blue> <front|side|rear> | base <moving red|blue> <base red|blue>");
        } catch (Exception failure) {
            Throwable cause = failure;
            while (cause.getCause() != null) cause = cause.getCause();
            sender.sendMessage("QA: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
        return true;
    }

    @Override public void onDisable() {
        for (DriveRun run : new HashSet<>(drives.values())) run.stop("helper disabled", false);
        for (ProbeRun run : new ArrayList<>(probes.values())) run.cancel();
        probes.clear();
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
        report(sender, "QA world=" + world.getName() + " tick=" + get(session, "tick")
                + " started=" + call(match, "started") + " finished=" + call(match, "finished")
                + " capture(red/blue)=" + call(match, "captureProgress", new Class<?>[] {int.class}, 0)
                + "/" + call(match, "captureProgress", new Class<?>[] {int.class}, 1)
                + " entities=" + world.getEntities().size() + " retainedChunks=" + tickets
                + " worldViewDistance=" + world.getViewDistance()
                + " worldSendViewDistance=" + world.getSendViewDistance()
                + " worldSimulationDistance=" + world.getSimulationDistance());
        for (Object actor : actors(session)) {
            UUID id = (UUID) call(actor, "id");
            Player player = connections(session).get(id);
            Object vehicle = vehicles(session).get(id), visual = get(vehicle, "visual");
            int parts = ((Collection<?>) call(visual, "entities")).size();
            Location at = (Location) get(vehicle, "at");
            Entity seat = (Entity) call(visual, "seat");
            Location seated = seat.getLocation();
            report(sender, "QA " + (player == null ? id : player.getName())
                    + " team=" + call(actor, "team") + " class=" + call(actor, "chassis")
                    + " hp=" + call(actor, "hp") + " alive=" + call(actor, "alive")
                    + " online=" + call(actor, "online") + " reload=" + call(actor, "reloadTicks")
                    + " trackTicks=" + call(actor, "trackTicks") + " smokeTicks=" + call(actor, "smokeTicks")
                    + " repairKits=" + call(actor, "repairKits") + " smokeCharges=" + call(actor, "smokeCharges")
                    + " shell=" + call(actor, "shell") + " scope=" + (player != null && (boolean) call(get(session, "scopes"), "active", new Class<?>[] {Player.class}, player))
                    + " base=" + call(actor, "base") + " parts=" + parts
                    + String.format(Locale.ROOT, " pos=%.2f,%.2f,%.2f hull=%.1f turret=%s/%s speed=%s seat=%.2f,%.2f,%.2f mounted=%s", at.getX(), at.getY(), at.getZ(), at.getYaw(), get(vehicle, "turretYaw"), get(vehicle, "turretPitch"), get(vehicle, "speed"), seated.getX(), seated.getY(), seated.getZ(), player != null && player.getVehicle() == seat));
            if (player != null) report(sender, String.format(Locale.ROOT,
                    "QA camera %s feetY=%.3f eyeY=%.3f eyeHeight=%.3f pose=%s yaw=%.2f pitch=%.2f passenger=%s",
                    player.getName(), player.getY(), player.getEyeLocation().getY(), player.getEyeHeight(),
                    player.getPose(), player.getYaw(), player.getPitch(), seat.getPassengers().contains(player)));
        }
        for (Object audience : (Collection<?>) call(call(get(session, "services"), "session"), "audiencePlayers")) {
            Player viewer = (Player) audience;
            if (!viewer.isOnline() || viewer.getWorld() != world) continue;
            report(sender, "QA viewer=" + viewer.getName()
                    + " clientViewDistance=" + viewer.getClientViewDistance()
                    + " playerViewDistance=" + viewer.getViewDistance()
                    + " playerSendViewDistance=" + viewer.getSendViewDistance()
                    + " playerSimulationDistance=" + viewer.getSimulationDistance()
                    + " chunk=" + (viewer.getLocation().getBlockX() >> 4) + ","
                    + (viewer.getLocation().getBlockZ() >> 4));
            for (Object actor : actors(session)) {
                UUID id = (UUID) call(actor, "id");
                Player driver = connections(session).get(id);
                Object visual = get(vehicles(session).get(id), "visual");
                int visible = 0, total = 0, trackedForViewer = 0, trackerMemberships = 0, sentParts = 0;
                for (Object part : (Collection<?>) call(visual, "entities")) {
                    Entity entity = (Entity) part;
                    total++;
                    if (viewer.canSee(entity)) visible++;
                    var trackedBy = entity.getTrackedBy();
                    trackerMemberships += trackedBy.size();
                    if (trackedBy.contains(viewer)) trackedForViewer++;
                    Location position = entity.getLocation();
                    if (viewer.isChunkSent(chunkKey(position.getBlockX() >> 4, position.getBlockZ() >> 4))) sentParts++;
                }
                Location target = (Location) get(vehicles(session).get(id), "at");
                int targetChunkX = target.getBlockX() >> 4, targetChunkZ = target.getBlockZ() >> 4;
                report(sender, "QA visibility viewer=" + viewer.getName() + " target="
                        + (driver == null ? id : driver.getName()) + " driverCanSee="
                        + (driver != null && viewer.canSee(driver))
                        + " driverTrackedByViewer=" + (driver != null && driver.getTrackedBy().contains(viewer))
                        + " modelCanSee=" + visible + "/" + total
                        + " modelTrackedByViewer=" + trackedForViewer + "/" + total
                        + " modelTrackerMemberships=" + trackerMemberships
                        + " modelChunkSent=" + sentParts + "/" + total
                        + " hullChunk=" + targetChunkX + "," + targetChunkZ
                        + " hullChunkSent=" + viewer.isChunkSent(chunkKey(targetChunkX, targetChunkZ)));
            }
        }
    }

    /** Paper accepts ChunkPos.asLong layout; using the key avoids loading an unsent chunk. */
    private static long chunkKey(int x, int z) { return (x & 0xffffffffL) | ((long) z << 32); }

    /** Isolated-match configuration experiment; does not change spotting or game outcomes. */
    private void view(CommandSender sender, Object session, int distance) throws Exception {
        require(distance >= 4 && distance <= 10, "view distance must be between 4 and 10 chunks");
        current(sender, session, false);
        World world = (World) get(session, "world");
        int beforeView = world.getViewDistance(), beforeSend = world.getSendViewDistance();
        world.setViewDistance(distance);
        world.setSendViewDistance(distance);
        sender.sendMessage("QA development-only match-world rendering experiment: view " + beforeView + "->"
                + world.getViewDistance() + " send " + beforeSend + "->" + world.getSendViewDistance()
                + "; HP/capture/spotting unchanged. Chunk delivery and tracking settle asynchronously; use probe 20.");
        state(sender, session);
    }

    private void spawn(CommandSender sender, Object session, int side) throws Exception {
        List<Object> selected = new ArrayList<>();
        for (Object actor : onlineActors(session)) if ((int) call(actor, "team") == side) selected.add(actor);
        require(selected.size() == 1, "spawn requires exactly one alive connected tank on the selected team");
        Object actor = selected.getFirst();
        Location at = (Location) call(get(session, "arena"), "spawn", new Class<?>[]{int.class, int.class}, side, 0);
        require(clear(session, actor, at, true), "original spawn footprint or another tank blocks the move; nothing moved");
        move(session, actor, at);
        call(session, "visibility");
        sender.sendMessage("QA returned current " + (side == 0 ? "red" : "blue")
                + " tank to its validated original spawn; HP/reload/shell/class preserved.");
    }

    private enum Direction { FORWARD, REVERSE, LEFT, RIGHT, FORWARD_LEFT, FORWARD_RIGHT, NEUTRAL }

    private record FixtureInput(Direction direction) implements Input {
        @Override public boolean isForward() { return direction == Direction.FORWARD || direction == Direction.FORWARD_LEFT || direction == Direction.FORWARD_RIGHT; }
        @Override public boolean isBackward() { return direction == Direction.REVERSE; }
        @Override public boolean isLeft() { return direction == Direction.LEFT || direction == Direction.FORWARD_LEFT; }
        @Override public boolean isRight() { return direction == Direction.RIGHT || direction == Direction.FORWARD_RIGHT; }
        @Override public boolean isJump() { return false; }
        @Override public boolean isSneak() { return false; }
        @Override public boolean isSprint() { return false; }
    }

    private static final Input NEUTRAL = new FixtureInput(Direction.NEUTRAL);
    private record DriveTarget(UUID id, Object actor, Object vehicle, Player player) {}

    private void drive(CommandSender sender, Object session, int side, Direction direction, int duration) throws Exception {
        current(sender, session, true);
        List<DriveTarget> selected = new ArrayList<>();
        for (Object actor : onlineActors(session)) if ((int) call(actor, "team") == side) {
            UUID id = (UUID) call(actor, "id");
            selected.add(new DriveTarget(id, actor, vehicles(session).get(id), connections(session).get(id)));
        }
        require(!selected.isEmpty(), "requested team has no alive connected tank");
        Method handler = inputHandler(session);
        for (DriveTarget target : selected) {
            DriveRun previous = drives.get(target.id);
            if (previous != null) previous.stop("replaced by another input fixture", true);
        }
        DriveRun run = new DriveRun(sender, session, selected, handler, direction, duration);
        for (DriveTarget target : selected) drives.put(target.id, run);
        run.runTaskTimer(this, 1, 1);
        sender.sendMessage("QA server-input fixture started: " + (side == 0 ? "red" : "blue")
                + " " + direction.name().toLowerCase(Locale.ROOT).replace('_', '-') + " for " + duration
                + " ticks; production PlayerInputEvent handler, no OS/physical-key automation.");
    }

    private final class DriveRun extends BukkitRunnable {
        private final CommandSender sender;
        private final Object session;
        private final List<DriveTarget> targets;
        private final Method handler;
        private final Input input;
        private final int duration;
        private int applied;
        private boolean stopped;

        DriveRun(CommandSender sender, Object session, List<DriveTarget> targets, Method handler, Direction direction, int duration) {
            this.sender = sender;this.session = session;this.targets = List.copyOf(targets);
            this.handler = handler;this.input = new FixtureInput(direction);this.duration = duration;
        }

        @Override public void run() {
            if (stopped) return;
            try {
                current(sender, session, true);
                Object match = get(session, "match");
                World world = (World) get(session, "world");
                for (DriveTarget target : targets) {
                    require(connections(session).get(target.id) == target.player
                                    && call(match, "actor", new Class<?>[]{UUID.class}, target.id) == target.actor
                                    && vehicles(session).get(target.id) == target.vehicle,
                            "target connection or session actor changed");
                    require(target.player.isOnline() && !target.player.isDead()
                                    && target.player.getWorld() == world
                                    && (boolean) call(target.actor, "online") && (boolean) call(target.actor, "alive"),
                            "target went offline, died or changed world");
                }
                // Release on the following tick, after the last input has occupied one full interval.
                if (applied == duration) { stop("completed", true); return; }
                for (DriveTarget target : targets) handler.invoke(session, new PlayerInputEvent(target.player, input));
                applied++;
            } catch (Exception failure) { stop("cancelled: " + rootCause(failure), true); }
        }

        void stop(String reason, boolean notify) {
            if (stopped) return;
            stopped = true;
            cancel();
            String reset = "input released for matching connections";
            for (DriveTarget target : targets) {
                drives.remove(target.id, this);
                try {
                    if (connections(session).get(target.id) == target.player)
                        handler.invoke(session, new PlayerInputEvent(target.player, NEUTRAL));
                } catch (Exception failure) { reset = "input reset unavailable: " + rootCause(failure); }
            }
            if (notify) sender.sendMessage("QA server-input fixture " + reason + " after " + applied
                    + " ticks; " + reset + ". This is not a physical held-key result.");
        }
    }

    private void probe(CommandSender sender, Object session, int delay) throws Exception {
        current(sender, session, false);
        ProbeRun previous = probes.remove(sender);
        if (previous != null) previous.cancel();
        ProbeRun run = new ProbeRun(sender, session);
        probes.put(sender, run);
        run.runTaskLater(this, delay);
        report(sender, "QA one-shot state probe scheduled in " + delay + " ticks; replaces this operator's prior pending probe.");
    }

    private final class ProbeRun extends BukkitRunnable {
        private final CommandSender sender;
        private final Object session;
        ProbeRun(CommandSender sender, Object session) { this.sender = sender;this.session = session; }
        @Override public void run() {
            if (!probes.remove(sender, this)) return;
            try { current(sender, session, false);state(sender, session); }
            catch (Exception failure) { report(sender, "QA state probe cancelled: " + rootCause(failure)); }
        }
    }

    private void current(CommandSender sender, Object expected, boolean liveBattle) throws Exception {
        require(isEnabled() && sender.isOp(), "helper or operator access is no longer active");
        if (sender instanceof Player player) require(player.isOnline(), "requesting operator disconnected");
        require(session(sender) == expected, "active session changed");
        Object match = get(expected, "match");
        require(!(boolean) call(match, "finished"), "battle finished");
        if (liveBattle) require((boolean) call(match, "started"), "battle has not started");
    }

    private Method inputHandler(Object session) {
        Method found = null;
        for (Method method : session.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != PlayerInputEvent.class
                    || !method.isAnnotationPresent(EventHandler.class)) continue;
            require(found == null, "production has multiple PlayerInputEvent handlers");
            found = method;
        }
        require(found != null, "production PlayerInputEvent handler is unavailable");
        found.setAccessible(true);
        return found;
    }

    private Direction direction(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "forward" -> Direction.FORWARD;
            case "reverse" -> Direction.REVERSE;
            case "left" -> Direction.LEFT;
            case "right" -> Direction.RIGHT;
            case "forward-left" -> Direction.FORWARD_LEFT;
            case "forward-right" -> Direction.FORWARD_RIGHT;
            default -> throw new IllegalArgumentException("direction must be forward, reverse, left, right, forward-left or forward-right");
        };
    }

    private int ticks(String value) {
        int result = Integer.parseInt(value);
        require(result >= 1 && result <= 100, "ticks must be between 1 and 100");
        return result;
    }

    private static String rootCause(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return failure.getClass().getSimpleName() + ": " + failure.getMessage();
    }

    /** QA snapshots are also retained in the private server log; no process arguments are logged. */
    private void report(CommandSender sender, String line) {
        sender.sendMessage(line);
        if (sender != Bukkit.getConsoleSender()) getLogger().info(line);
    }

    private void range(CommandSender sender, Object session, int range) throws Exception {
        require(range == 12 || range == 40 || range == 70 || range == 86, "range must be 12, 40, 70 or 86");
        placement(sender, session, range, false);
    }

    private record Lane(Location red, Location blue) {}

    private void placement(CommandSender sender, Object session, int range, boolean cover) throws Exception {
        List<Object> actors = onlineActors(session);
        require(actors.size() == 2 && !call(actors.get(0), "team").equals(call(actors.get(1), "team")), "range requires exactly two alive connected tanks on opposite teams");
        Object red = actors.stream().filter(actor -> {
            try { return (int) call(actor, "team") == 0; }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }).findFirst().orElseThrow();
        Object blue = actors.get(0) == red ? actors.get(1) : actors.get(0);
        Lane lane = lane(session, red, blue, range, cover);
        require(lane != null, "no validated " + (cover ? "covered" : "open") + " lane of requested length exists; nothing moved");
        require(Math.abs(lane.red.distance(lane.blue) - range) < .000001,
                "validated lane does not match the exact requested hull-center range");
        for (Object actor : actors) {
            int side = (int) call(actor, "team");
            move(session, actor, side == 0 ? lane.red : lane.blue);
        }
        call(session, "visibility");
        report(sender, String.format(Locale.ROOT, "QA %s range=%d red=(%.1f,%.1f) blue=(%.1f,%.1f) yaw=%.0f/%.0f hullDistance=%.6f; spotting uses hull centers, not eye positions; HP/reload/shell/class preserved.",
                cover ? "native hard cover between muzzles" : "validated full-width open lane",
                range, lane.red.getX(), lane.red.getZ(), lane.blue.getX(), lane.blue.getZ(), lane.red.getYaw(), lane.blue.getYaw(), lane.red.distance(lane.blue)));
    }

    private Lane lane(Object session, Object red, Object blue, int range, boolean cover) throws Exception {
        World world = (World) get(session, "world");
        List<Lane> candidates = new ArrayList<>();
        if (cover) {
            for (int z : new int[] {-27, 27}) candidates.add(vertical(world, 0, z, range));
            for (int z = -35; z <= 35; z += 2) for (int x = -44; x <= 44; x += 4)
                candidates.add(vertical(world, x, z, range));
        } else {
            for (int x = 0; x <= 52; x += 4) {
                candidates.add(vertical(world, x, 0, range));
                if (x != 0) candidates.add(vertical(world, -x, 0, range));
            }
            for (int z = 0; z <= 42; z += 2) for (int sign : new int[] {-1, 1})
                candidates.add(new Lane(new Location(world, -range / 2.0, 65, z * sign, -90, 0),
                        new Location(world, range / 2.0, 65, z * sign, 90, 0)));
        }
        for (Lane candidate : candidates) {
            if (!clear(session, red, candidate.red, false) || !clear(session, blue, candidate.blue, false)) continue;
            Location a = muzzle(session, red, candidate.red), b = muzzle(session, blue, candidate.blue);
            boolean blocked = blocked(a, b);
            if (blocked != cover) continue;
            if (!cover && !corridor(session, red, blue, candidate)) continue;
            return candidate;
        }
        return null;
    }

    private Lane vertical(World world, double x, double centerZ, int range) {
        return new Lane(new Location(world, x, 65, centerZ - range / 2.0, 0, 0),
                new Location(world, x, 65, centerZ + range / 2.0, 180, 0));
    }

    private boolean blocked(Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        return from.getWorld().rayTraceBlocks(from, delta.clone().normalize(), delta.length(), FluidCollisionMode.NEVER, true) != null;
    }

    private boolean corridor(Object session, Object red, Object blue, Lane lane) throws Exception {
        Vector delta = lane.blue.toVector().subtract(lane.red.toVector());
        int steps = (int) Math.ceil(delta.length() * 2);
        for (int step = 0; step <= steps; step++) {
            Location at = lane.red.clone().add(delta.clone().multiply((double) step / steps));
            if (!clear(session, red, at, false)) return false;
            at.setYaw(lane.blue.getYaw());
            if (!clear(session, blue, at, false)) return false;
            // Keep the command's firing lane clear for elevated camera rays and low shell arcs too.
            for (int x = (int) Math.floor(at.getX() - .5); x <= Math.floor(at.getX() + .5); x++)
                for (int z = (int) Math.floor(at.getZ() - .5); z <= Math.floor(at.getZ() + .5); z++)
                    for (int y = 65; y <= 71; y++) if (!at.getWorld().getBlockAt(x, y, z).isPassable()) return false;
        }
        return true;
    }

    private Location muzzle(Object session, Object actor, Location at) throws Exception {
        Object chassis = call(actor, "chassis"), visual = get(vehicles(session).get(call(actor, "id")), "visual");
        Method method = visual.getClass().getDeclaredMethod("muzzleAt", Location.class, float.class, float.class, chassis.getClass());
        method.setAccessible(true);
        return (Location) method.invoke(null, at, at.getYaw(), 0f, chassis);
    }

    private void angle(CommandSender sender, Object session, int moving, String facing) throws Exception {
        int offset = switch (facing.toLowerCase(Locale.ROOT)) {
            case "front" -> 0; case "side" -> 90; case "rear" -> 180;
            default -> throw new IllegalArgumentException("angle must be front, side or rear relative to the nearest opponent");
        };
        List<Object> current = onlineActors(session);
        int moved = 0;
        for (Object actor : current) {
            if ((int) call(actor, "team") != moving) continue;
            Location at = ((Location) get(vehicles(session).get(call(actor, "id")), "at")).clone();
            Location nearest = null;
            for (Object enemy : current) if ((int) call(enemy, "team") != moving) {
                Location position = (Location) get(vehicles(session).get(call(enemy, "id")), "at");
                if (nearest == null || position.distanceSquared(at) < nearest.distanceSquared(at)) nearest = position;
            }
            require(nearest != null, "an alive connected opponent is required");
            at.setYaw((float) Math.toDegrees(Math.atan2(at.getX() - nearest.getX(), nearest.getZ() - at.getZ())) + offset);
            require(clear(session, actor, at, true), "requested hull angle is blocked; that tank was not moved");
            move(session, actor, at);
            moved++;
        }
        require(moved > 0, "requested team has no alive connected tank");
        call(session, "visibility");
        sender.sendMessage("QA angled " + moved + " current tank(s) " + facing + " to nearest opponent; ordinary armor rules unchanged.");
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
        DriveRun driving = drives.get(id);
        if (driving != null) driving.stop("cancelled by QA reposition", true);
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
