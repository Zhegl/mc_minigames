import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Real Paper worlds, display metadata and particle/sound registries; no player connections. */
public final class TanksNativeSmoke extends JavaPlugin {
    private static final String PKG = "me.petr1furious.potatominigames.games.tanks.";
    private ClassLoader production;
    private JavaPlugin productionPlugin;
    private Class<?> chassisType, visualType, arenaType;
    private World world;
    private Object arena;
    private int assertions;
    private final List<String> stages = new ArrayList<>();

    @Override public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                JavaPlugin main = (JavaPlugin) Bukkit.getPluginManager().getPlugin("PotatoMinigames");
                require(main != null && main.isEnabled(), "production plugin enabled");
                productionPlugin = main;
                production = main.getClass().getClassLoader();
                chassisType = type("TanksRules$Chassis");
                visualType = type("TankVisual");
                arenaType = type("TanksArena");
                world = Bukkit.createWorld(new WorldCreator("tanks-native-arena-" + UUID.randomUUID().toString().substring(0, 8))
                        .generateStructures(false).keepSpawnLoaded(TriState.FALSE)
                        .generator(new ChunkGenerator() {
                            @Override public Location getFixedSpawnLocation(World w, Random random) {
                                return new Location(w, .5, 81, .5);
                            }
                        }));
                require(world != null, "fresh empty arena world created");
                world.setViewDistance(3);
                world.setSimulationDistance(3);
                world.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
                world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
                world.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);
                // This fixture has no players; keep every tested chunk loaded and entity-ticking.
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 3; z++)
                    world.addPluginChunkTicket(x, z, this);
                checkProductionChunkRetention();
                stage("production arena chunk tickets retain all 72 chunks and release cleanly");
                arena = construct(arenaType, new Class<?>[]{World.class}, world);
                require(number(call(arena, "progress")).doubleValue() == 0, "arena starts unbuilt");
                buildArena();
            } catch (Throwable failure) { finish(failure); }
        });
    }

    private void buildArena() {
        new BukkitRunnable() {
            private float previous;
            @Override public void run() {
                try {
                    boolean done = (boolean) call(arena, "buildSlice", new Class<?>[]{int.class}, 4096);
                    float progress = number(call(arena, "progress")).floatValue();
                    require(progress > previous && progress <= 1, "bounded arena slices advance monotonically");
                    require(progress - previous <= .0243f, "one slice visits at most 4096 arena cells");
                    previous = progress;
                    if (!done) return;
                    cancel();
                    Bukkit.getScheduler().runTask(TanksNativeSmoke.this, () -> {
                        try {
                            checkArena();
                            stage("native arena construction, twelve spawn volumes and collision");
                            for (Object chassis : chassisType.getEnumConstants()) checkVisual(chassis);
                            stage("all chassis spawn, independent transforms, muzzle, wreck and removal");
                            checkProductionFire();
                            stage("production fire, typed FLASH/tracer particles, sounds and muzzle obstruction");
                            checkNativeScope();
                            stage("production scope native movement modifiers, debounce and cleanup");
                            checkScopedSidebar();
                            stage("scoped sidebar hides, retains its board, and restores after zoom");
                            for (Object cue : type("TanksAudio$Cue").getEnumConstants()) {
                                org.bukkit.Sound sound = (org.bukkit.Sound) call(cue, "sound");
                                world.playSound(new Location(world, 0, 70, 0), sound, org.bukkit.SoundCategory.PLAYERS, .1f, 1);
                                require(sound != null, "production audio cue resolves in native sound registry: " + cue);
                            }
                            stage("all production audio cues resolve against native sound registries");
                            checkSeatGeometry();
                        } catch (Throwable failure) { finish(failure); }
                    });
                } catch (Throwable failure) { cancel(); finish(failure); }
            }
        }.runTaskTimer(this, 1, 1);
    }

    private void checkProductionChunkRetention() throws Exception {
        Class<?> servicesType = typeOutside("core.game.SessionServices");
        Constructor<?> serviceConstructor = servicesType.getDeclaredConstructors()[0];
        Object[] arguments = new Object[serviceConstructor.getParameterCount()];
        arguments[0] = productionPlugin;
        Object services = serviceConstructor.newInstance(arguments);
        Object session = construct(type("TanksSession"), new Class<?>[]{servicesType, type("TanksConfig"), type("TanksGame")},
                services, null, null);
        set(session, "world", world);
        require(tickets(productionPlugin).isEmpty(), "production starts without arena chunk tickets");
        try {
            call(session, "retainArenaChunks");
            var retained = tickets(productionPlugin);
            require(retained.size() == 72, "production retains all 72 arena chunks");
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 3; z++) {
                final int cx = x, cz = z;
                require(retained.stream().anyMatch(chunk -> chunk.getX() == cx && chunk.getZ() == cz),
                        "production retains arena chunk " + x + "/" + z);
            }
            call(session, "retainArenaChunks");
            require(tickets(productionPlugin).size() == 72,
                    "retaining an already resident arena is idempotent");
        } finally { world.removePluginChunkTickets(productionPlugin); }
        require(tickets(productionPlugin).isEmpty(), "native cleanup releases production arena tickets");
        require(tickets(this).size() == 72, "cleanup preserves a different plugin's test tickets");
    }

    private void checkSeatGeometry() throws Exception {
        Class<?> avatar = Class.forName("net.minecraft.world.entity.Avatar");
        Field nativeAttachmentField = avatar.getDeclaredField("DEFAULT_VEHICLE_ATTACHMENT");
        nativeAttachmentField.setAccessible(true);
        Object nativeAttachment = nativeAttachmentField.get(null);
        double playerAttachmentY = nativeAttachment.getClass().getField("y").getDouble(nativeAttachment);
        Field nativeStandingField = avatar.getDeclaredField("STANDING_DIMENSIONS");
        nativeStandingField.setAccessible(true);
        Object nativeStanding = nativeStandingField.get(null);
        double playerEyeHeight = ((Number) nativeStanding.getClass().getMethod("eyeHeight").invoke(nativeStanding)).doubleValue();
        near(playerAttachmentY, .6, "native Avatar vehicle attachment subtracts .6 from Player feet");
        near(playerEyeHeight, 1.62, "native standing Avatar eye height comes from native dimensions");
        List<Object> visuals = new ArrayList<>();
        List<ArmorStand> riders = new ArrayList<>();
        List<Entity> seats = new ArrayList<>();
        double[] offsets = new double[3];
        Object[] chassis = chassisType.getEnumConstants();
        for (int i = 0; i < chassis.length; i++) {
            Object visual = construct(visualType, new Class<?>[]{Location.class, int.class, chassisType},
                    new Location(world, i * 8, 65, -41), 0, chassis[i]);
            Entity seat = (Entity) call(visual, "seat");
            ArmorStand rider = world.spawn(seat.getLocation(), ArmorStand.class, stand -> {
                stand.setGravity(false);stand.setVisible(false);stand.setMarker(true);stand.setPersistent(false);
            });
            require(seat.addPassenger(rider), "geometry fixture mounts an actual native passenger");
            visuals.add(visual);seats.add(seat);riders.add(rider);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                for (int i = 0; i < chassis.length; i++) {
                    Entity seat = seats.get(i);ArmorStand rider = riders.get(i);
                    double roof = 65 + renderedRoof(visuals.get(i));
                    near(seat.getLocation().getY(), roof - .74,
                            "native Interaction anchor accounts for Player's .6 riding attachment");
                    double nativePlayerEye = seat.getLocation().getY() - playerAttachmentY + playerEyeHeight;
                    near(nativePlayerEye - roof, .28,
                            "native Avatar attachment and eye dimensions place standing camera above the turret roof");
                    offsets[i] = rider.getLocation().getY() - seat.getLocation().getY();
                    require(rider.getVehicle() == seat && Double.isFinite(offsets[i]), "native Interaction passenger settles on its actual attachment");
                    stage("native seat geometry " + chassis[i] + ": seatY=" + seat.getLocation().getY()
                            + " riderY=" + rider.getLocation().getY() + " riderHeight=" + rider.getHeight()
                            + " attachmentOffsetY=" + offsets[i] + "; ArmorStand transport, no Player-eye assumption");
                    update(visuals.get(i), new Location(world, i * 8 + 4, 65, -39, 90, 0), 90, 0, 500, 1000);
                }
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    Throwable failure = null;
                    try {
                        for (int i = 0; i < chassis.length; i++) {
                            Entity seat = seats.get(i);ArmorStand rider = riders.get(i);
                            double scale = switch (i) { case 0 -> .82; case 1 -> .92; default -> 1; };
                            near(seat.getLocation().getX(), i * 8 + 4 + .35 * scale, "native Interaction anchor follows hull steering");
                            near(rider.getLocation().getX(), seat.getLocation().getX(), "settled passenger follows native Interaction X");
                            near(rider.getLocation().getZ(), seat.getLocation().getZ(), "settled passenger follows native Interaction Z");
                            near(rider.getLocation().getY() - seat.getLocation().getY(), offsets[i], "native attachment offset is stable through motion");
                            int owned = ((Collection<?>) call(visuals.get(i), "entities")).size();
                            for (int reconnect = 0; reconnect < 3; reconnect++) {
                                Entity discarded = seat;
                                discarded.eject();
                                discarded.remove();
                                seat = (Entity) call(visuals.get(i), "seat");
                                require(seat != discarded && seat.isValid(), "missing logout seat is recovered before native remount");
                                Collection<?> entities = (Collection<?>) call(visuals.get(i), "entities");
                                require(entities.size() == owned && entities.contains(seat) && !entities.contains(discarded),
                                        "seat recovery swaps exactly one owned entity without recreating the hull");
                                require(seat instanceof Interaction && !(seat instanceof LivingEntity)
                                                && seat.isInvulnerable() && !seat.isPersistent() && !seat.isVisibleByDefault(),
                                        "recovered Interaction retains hidden nonliving seat configuration");
                                require(seat.addPassenger(rider) && rider.getVehicle() == seat,
                                        "each recovered seat accepts an actual native passenger");
                                update(visuals.get(i), new Location(world, i * 8 + 5 + reconnect, 65, -38, 90, 0), 110, 0, 500, 1000);
                                require(rider.getVehicle() == seat && seat.getPassengers().contains(rider),
                                        "native movement retains the recovered seat passenger");
                            }
                            seats.set(i, seat);
                        }
                        stage("all chassis recover three deleted logout seats and retain actual native passengers through movement");
                    } catch (Throwable problem) { failure = problem; }
                    finally {
                        for (Object visual : visuals) try { call(visual, "remove"); } catch (Throwable ignored) { }
                        for (ArmorStand rider : riders) rider.remove();
                    }
                    finish(failure);
                }, 2);
            } catch (Throwable failure) {
                for (Object visual : visuals) try { call(visual, "remove"); } catch (Throwable ignored) { }
                for (ArmorStand rider : riders) rider.remove();
                finish(failure);
            }
        }, 2);
    }

    private Collection<org.bukkit.Chunk> tickets(JavaPlugin plugin) {
        return world.getPluginChunkTickets().getOrDefault(plugin, List.of());
    }

    private void checkArena() throws Exception {
        require(number(call(arena, "progress")).doubleValue() == 1, "arena completes all slices");
        long labels = world.getEntitiesByClass(TextDisplay.class).size();
        require(labels == 2, "arena builds two native base labels (actual=" + labels + ", all=" + world.getEntities().size() + ")");
        int leafCount = 0;
        for (int x = -64; x < 64; x++) for (int z = -64; z < 64; z++) for (int y = 65; y <= 74; y++) {
            if (world.getBlockAt(x, y, z).getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves) {
                leafCount++;
                require(leaves.isPersistent(), "arena foliage remains stable under native random ticks at " + x + "," + y + "," + z);
            }
        }
        require(leafCount > 0, "native leaf persistence assertion covers actual arena foliage");
        require((boolean) call(arena, "buildSlice", new Class<?>[]{int.class}, 1), "completed build remains complete");
        require(world.getEntitiesByClass(TextDisplay.class).size() == labels, "repeated completion does not duplicate labels");
        for (int team = 0; team < 2; team++) {
            for (int slot = 0; slot < 6; slot++) {
                Location spawn = (Location) call(arena, "spawn", new Class<?>[]{int.class, int.class}, team, slot);
                require(spawn.getY() == 65 && canDrive(spawn), "spawn has support and whole-hull clearance: " + team + "/" + slot);
                spawn.setYaw(45);
                require(canDrive(spawn), "spawn permits rotated hull: " + team + "/" + slot);
            }
            Location base = (Location) call(arena, "base", new Class<?>[]{int.class}, team);
            require(canDrive(base), "capture disk is driveable: " + team);
        }
        require(!canDrive(new Location(world, 0, 65, 27)), "spawn berm blocks native hull collision");
        require(!canDrive(new Location(world, 8, 65, 3)), "central masonry blocks native hull collision");
        require(!canDrive(new Location(world, 63, 65, 0)), "arena boundary rejects an overhanging hull");
        require(!canDrive(new Location(world, 0, 66, -41)), "unsupported vertical hull displacement is rejected");
        Location free = new Location(world, 0, 65, -41);
        require(canDrive(free), "obstruction fixture starts on a clear spawn lane");
        var roof = world.getBlockAt(0, 67, -41);
        var roofBefore = roof.getBlockData();
        try {
            roof.setType(Material.STONE, false);
            require(!canDrive(free), "native overhead block prevents driving the hull volume through it");
            require(canDrive(free, chassisType.getEnumConstants()[0]), "light turret fits beneath native clearance above its smaller profile");
        } finally { roof.setBlockData(roofBefore, false); }
        var floor = world.getBlockAt(0, 64, -41);
        var floorBefore = floor.getBlockData();
        try {
            floor.setType(Material.AIR, false);
            require(!canDrive(free), "missing native support rejects a partially unsupported hull");
        } finally { floor.setBlockData(floorBefore, false); }
        require(canDrive(free), "restoring floor and roof restores the valid lane");
        var narrow = world.getBlockAt(2, 65, -41);
        var narrowBefore = narrow.getBlockData();
        try {
            narrow.setType(Material.STONE, false);
            Location gap = new Location(world, .5, 65, -41);
            require(canDrive(gap, chassisType.getEnumConstants()[0]), "light hull clears native wall beside a narrow lane");
            require(!canDrive(gap, chassisType.getEnumConstants()[2]), "heavy hull cannot overlap the same native narrow-lane wall");
        } finally { narrow.setBlockData(narrowBefore, false); }
        var cover = world.rayTraceBlocks(new Location(world, 0, 66.5, -37), new Vector(0, 0, 1),
                74, FluidCollisionMode.NEVER, true);
        require(cover != null && cover.getHitBlock() != null && cover.getHitBlock().isSolid(),
                "native base-to-base shot encounters solid sightline cover");
    }

    private boolean canDrive(Location location) throws Exception {
        return (boolean) call(arena, "canDrive", new Class<?>[]{Location.class}, location);
    }

    private boolean canDrive(Location location, Object chassis) throws Exception {
        return (boolean) call(arena, "canDrive", new Class<?>[]{Location.class, chassisType}, location, chassis);
    }

    private void checkVisual(Object chassis) throws Exception {
        Location at = new Location(world, 0, 65, -41, 0, 0);
        Object visual = construct(visualType, new Class<?>[]{Location.class, int.class, chassisType}, at, 0, chassis);
        List<Entity> spawned = new ArrayList<>(entities(visual));
        ArmorStand rider = world.spawn(at.clone().add(0, 1, 0), ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setPersistent(false);
        });
        try {
            require(spawned.size() > 40 && spawned.stream().filter(BlockDisplay.class::isInstance).count() > 35,
                    chassis + " contains a recognizable native multi-part tracked hull");
            for (Entity entity : spawned) {
                require(entity.isValid() && !entity.isPersistent() && !entity.hasGravity(),
                        chassis + " parts are live transient gravity-free entities");
                if (entity instanceof Display display) {
                    require(display.getInterpolationDuration() > 0 && display.getTeleportDuration() > 0,
                            chassis + " native displays interpolate motion");
                }
            }
            Entity seat = (Entity) call(visual, "seat");
            require(seat instanceof Interaction && !(seat instanceof LivingEntity)
                            && ((Interaction) seat).getInteractionWidth() == 0
                            && ((Interaction) seat).getInteractionHeight() == 0
                            && !((Interaction) seat).isResponsive() && seat.isInvulnerable(),
                    chassis + " nonliving zero-size interaction seat avoids native mount health");
            require(seat.addPassenger(rider) && rider.getVehicle() == seat, "native seat accepts a passenger entity");
            double reach = switch (chassis.toString()) { case "LIGHT" -> 2.4846; case "MEDIUM" -> 3.2476; default -> 3.88; };
            double height = switch (chassis.toString()) { case "LIGHT" -> 1.22; case "MEDIUM" -> 1.42; default -> 1.56; };
            Location muzzle = (Location) call(visual, "muzzle");
            near(muzzle.getX(), 0, "forward muzzle X");
            near(muzzle.getY(), 65 + height, "muzzle aligns with class turret pivot");
            near(muzzle.getZ(), -41 + reach, "muzzle reaches the visible barrel end");
            List<?> parts = (List<?>) field(visual, "parts");
            List<Matrix4f> zero = matrices(parts);
            at.setYaw(90);
            update(visual, at, 0, 0, 500, 1000);
            checkFrames(parts, zero, true, false, false, "hull steering");
            List<Matrix4f> hullTurn = matrices(parts);
            update(visual, at, 90, 0, 500, 1000);
            checkFrames(parts, hullTurn, false, true, true, "turret traverse");
            muzzle = (Location) call(visual, "muzzle");
            near(muzzle.getX(), -reach, "turret yaw rotates barrel and muzzle toward -X");
            near(muzzle.getZ(), -41, "turret yaw leaves no stale forward muzzle offset");
            List<Matrix4f> turretTurn = matrices(parts);
            update(visual, at, 90, 12, 500, 1000);
            checkFrames(parts, turretTurn, false, false, true, "gun elevation");
            muzzle = (Location) call(visual, "muzzle");
            near(muzzle.getY(), 65 + height - Math.sin(Math.toRadians(12)) * reach, "gun elevation and muzzle share a pivot");
            at.add(2, 0, 1);
            update(visual, at, 90, 12, 200, 1000);
            require(rider.getVehicle() == seat, "native seat retains passenger through steering and translation");
            for (Entity entity : spawned) if (entity instanceof BlockDisplay) {
                near(entity.getLocation().getX(), 2, "display anchor follows translation");
                near(entity.getLocation().getZ(), -40, "display anchor follows translation on Z");
            }
            call(visual, "wreck");
            require(seat.getPassengers().isEmpty() && rider.getVehicle() == null, "wreck ejects its native passenger");
            require(spawned.stream().filter(TextDisplay.class::isInstance).map(TextDisplay.class::cast)
                    .anyMatch(text -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(text.text()).contains("✕")), "wreck health label changes to its destroyed marker");
            call(visual, "wreck");
            require(entities(visual).size() == spawned.size(), "repeated wreck does not leak extra entities");
        } finally {
            call(visual, "remove");
            call(visual, "remove");
            rider.remove();
            require(entities(visual).isEmpty() && spawned.stream().noneMatch(Entity::isValid),
                    chassis + " removal clears every native display, label and seat");
        }
    }

    private double renderedRoof(Object levelVisual) throws Exception {
        float roof = Float.NEGATIVE_INFINITY;
        for (Matrix4f matrix : matrices((List<?>) field(levelVisual, "parts"))) {
            for (int x = 0; x <= 1; x++)
                for (int y = 0; y <= 1; y++)
                    for (int z = 0; z <= 1; z++)
                        roof = Math.max(roof, matrix.transformPosition(new Vector3f(x, y, z)).y);
        }
        return roof;
    }

    private List<Matrix4f> matrices(List<?> parts) throws Exception {
        List<Matrix4f> values = new ArrayList<>();
        for (Object part : parts) {
            Transformation transform = ((BlockDisplay) field(part, "display")).getTransformation();
            values.add(new Matrix4f().translation(transform.getTranslation()).rotate(transform.getLeftRotation())
                    .scale(transform.getScale()).rotate(transform.getRightRotation()));
        }
        return values;
    }

    private void checkScopedSidebar() throws Exception {
        var title = net.kyori.adventure.text.Component.text("Iron Valley");
        var locale = java.util.Locale.ENGLISH;
        Object view = construct(typeOutside("core.hud.SidebarService$View"),
                new Class<?>[]{net.kyori.adventure.text.Component.class, java.util.Locale.class}, title, locale);
        var board = (org.bukkit.scoreboard.Scoreboard) field(view, "scoreboard");
        var slot = org.bukkit.scoreboard.DisplaySlot.SIDEBAR;
        var objective = board.getObjective(slot);
        var signature = new Class<?>[]{List.class, java.util.Locale.class};
        call(view, "setLines", signature, List.of(title), locale);
        call(view, "setLines", signature, List.of(), locale);
        require(board.getObjective(slot) == null && board.getObjective("sidebar") != null,
                "zoom frees the sidebar slot and preserves its objective");
        call(view, "setLines", signature, List.of(title), locale);
        var restored = board.getObjective(slot);
        require(restored != null && objective.getName().equals(restored.getName()),
                "leaving zoom restores the sidebar objective");
    }

    private void checkNativeScope() throws Exception {
        var first = world.spawn(new Location(world, 0, 70, -41), org.bukkit.entity.Zombie.class, zombie -> {
            zombie.setAI(false);
            zombie.setSilent(true);
            zombie.setPersistent(false);
        });
        var replacement = world.spawn(new Location(world, 0, 70, -41), org.bukkit.entity.Zombie.class, zombie -> {
            zombie.setAI(false);
            zombie.setSilent(true);
            zombie.setPersistent(false);
        });
        Object scope = construct(type("TanksScope"), new Class<?>[0]);
        var key = new org.bukkit.NamespacedKey("potatominigames", "tanks_scope");
        var unrelated = new org.bukkit.NamespacedKey("tanks_native_smoke", "unrelated");
        try {
            var attribute = first.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
            var newAttribute = replacement.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
            require(attribute != null && newAttribute != null, "scope fixture uses real native movement attributes");
            attribute.setBaseValue(.1);
            newAttribute.setBaseValue(.1);
            attribute.addTransientModifier(new org.bukkit.attribute.AttributeModifier(unrelated, .02,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
            double baseline = attribute.getValue(), replacementBaseline = newAttribute.getValue();
            UUID id = UUID.randomUUID();
            Player transport = scopeTransport(id, attribute);
            Player reconnected = scopeTransport(id, newAttribute);
            require((boolean) call(scope, "toggle", new Class<?>[]{Player.class, long.class}, transport, 100L),
                    "native scope accepts the first toggle");
            require((boolean) call(scope, "active", new Class<?>[]{Player.class}, transport), "native scope reports active ownership");
            near(attribute.getValue(), baseline * .2, "native scalar modifier produces20percent movement ratio for FOV zoom");
            require(attribute.getModifier(key) != null && attribute.getModifier(key).getAmount() == -.8,
                    "native scope installs its own keyed modifier");
            require(!(boolean) call(scope, "toggle", new Class<?>[]{Player.class, long.class}, transport, 101L),
                    "same-click duplicate scope event is debounced");
            near(attribute.getValue(), baseline * .2, "debounced scope event does not stack or remove zoom");
            require((boolean) call(scope, "toggle", new Class<?>[]{Player.class, long.class}, transport, 104L),
                    "later accepted click toggles zoom off");
            near(attribute.getValue(), baseline, "native toggle-off restores original movement calculation");
            require(attribute.getModifier(key) == null && attribute.getModifier(unrelated) != null,
                    "scope removes only its own modifier");
            call(scope, "toggle", new Class<?>[]{Player.class, long.class}, transport, 108L);
            call(scope, "clear");
            near(attribute.getValue(), baseline, "scope cleanup restores native attribute calculation");
            require(!(boolean) call(scope, "active", new Class<?>[]{Player.class}, transport), "scope cleanup clears ownership");
            call(scope, "toggle", new Class<?>[]{Player.class, long.class}, transport, 112L);
            call(scope, "toggle", new Class<?>[]{Player.class, long.class}, reconnected, 116L);
            near(attribute.getValue(), baseline, "reconnect clears the old native connection's modifier");
            near(newAttribute.getValue(), replacementBaseline * .2, "reconnect applies zoom only to the replacement connection");
            call(scope, "stop", new Class<?>[]{Player.class}, transport);
            require((boolean) call(scope, "active", new Class<?>[]{Player.class}, reconnected),
                    "stale connection cleanup cannot clear replacement ownership");
            near(newAttribute.getValue(), replacementBaseline * .2, "stale connection cleanup preserves the replacement's native modifier");
            call(scope, "clear");
            near(newAttribute.getValue(), replacementBaseline, "final scope cleanup removes replacement zoom");
            call(scope, "stop", new Class<?>[]{Player.class}, reconnected);
            near(newAttribute.getValue(), replacementBaseline, "repeated scope stop is idempotent");
        } finally {
            call(scope, "clear");
            first.remove();
            replacement.remove();
        }
    }

    private Player scopeTransport(UUID id, org.bukkit.attribute.AttributeInstance attribute) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getAttribute" -> args[0] == org.bukkit.attribute.Attribute.MOVEMENT_SPEED ? attribute : null;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "NativeScopeTransport";
            default -> throw new UnsupportedOperationException("Unexpected scope transport call: " + method.getName());
        });
    }

    private void update(Object visual, Location at, float yaw, float pitch, double hp, double max) throws Exception {
        call(visual, "update", new Class<?>[]{Location.class, float.class, float.class, double.class, double.class},
                at, yaw, pitch, hp, max);
    }

    private void checkFrames(List<?> parts, List<Matrix4f> before, boolean hull, boolean turret, boolean gun, String action) throws Exception {
        List<Matrix4f> after = matrices(parts);
        for (int i = 0; i < parts.size(); i++) {
            String frame = field(parts.get(i), "frame").toString();
            boolean changed = !before.get(i).equals(after.get(i), .00001f);
            boolean expected = switch (frame) { case "HULL" -> hull; case "TURRET" -> turret; default -> gun; };
            require(changed == expected, action + " changes only the expected native " + frame + " frame");
        }
    }

    @SuppressWarnings("unchecked") private void checkProductionFire() throws Exception {
        Class<?> sessionType = type("TanksSession"), matchType = type("TanksMatch"), vehicleType = type("TanksSession$Vehicle");
        Object chassis = chassisType.getEnumConstants()[0];
        UUID id = UUID.randomUUID();
        Location at = new Location(world, 0, 65, -41, 0, 0);
        Object match = construct(matchType, new Class<?>[]{int.class, int.class, boolean.class}, 1000, 100, true);
        Object actor = call(match, "add", new Class<?>[]{UUID.class, int.class, chassisType}, id, 0, chassis);
        call(match, "start");
        Object session = construct(sessionType, new Class<?>[]{typeOutside("core.game.SessionServices"), type("TanksConfig"), type("TanksGame")}, null, null, null);
        Object visual = construct(visualType, new Class<?>[]{Location.class, int.class, chassisType}, at, 0, chassis);
        try {
            Object vehicle = construct(vehicleType, new Class<?>[]{Location.class}, at);
            set(vehicle, "visual", visual);
            set(vehicle, "chassis", chassis);
            set(session, "world", world);
            set(session, "match", match);
            set(session, "built", true);
            ((Map<UUID, Object>) field(session, "vehicles")).put(id, vehicle);
            Player transport = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getLocation" -> at.clone();
                case "getWorld" -> world;
                case "getName" -> "NativeTransport";
                case "isOnline" -> true;
                case "isDead" -> false;
                case "playSound" -> null;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> "NativeTransport";
                default -> throw new UnsupportedOperationException("Unexpected simulated transport call: " + method.getName());
            });
            ((Map<UUID, Player>) field(session, "connections")).put(id, transport);
            call(session, "fire", new Class<?>[]{Player.class}, transport);
            List<?> shells = (List<?>) field(session, "shells");
            require(shells.size() == 1, "production fire accepts native particles/sounds and creates one shell");
            Location origin = (Location) field(shells.getFirst(), "at");
            require(origin.distanceSquared((Location) call(visual, "muzzle")) < .000001, "production shell starts at the rendered muzzle");
            require(number(call(actor, "reloadTicks")).intValue() > 0, "native fire consumes the logical loaded shell");
            call(session, "flyShells");
            require(((Location) field(shells.getFirst(), "at")).distanceSquared(origin) > 1, "native tracer accepts typed DUST data and advances shell");
            call(session, "impact", new Class<?>[]{Location.class, boolean.class}, at.clone().add(0, 1, 4), false);
            call(session, "impact", new Class<?>[]{Location.class, boolean.class}, at.clone().add(0, 1, 4), true);
            int reload = number(call(actor, "reloadTicks")).intValue();
            for (int tick = 0; tick < reload; tick++) call(match, "tick");
            shells.clear();
            var obstruction = world.getBlockAt(0, 66, -40);
            var before = obstruction.getBlockData();
            try {
                obstruction.setType(Material.STONE, false);
                call(session, "fire", new Class<?>[]{Player.class}, transport);
                require(shells.isEmpty(), "native muzzle-adjacent wall stops the shot before shell creation");
                require(number(call(actor, "reloadTicks")).intValue() > 0, "obstructed cannon shot still consumes reload");
            } finally { obstruction.setBlockData(before, false); }
        } finally { call(visual, "remove"); }
    }

    private Class<?> type(String suffix) throws Exception { return Class.forName(PKG + suffix, true, production); }
    private Class<?> typeOutside(String suffix) throws Exception { return Class.forName("me.petr1furious.potatominigames." + suffix, true, production); }
    private Object construct(Class<?> type, Class<?>[] signature, Object... args) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor(signature);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
    private Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private Object call(Object target, String name, Class<?>[] signature, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, signature);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    @SuppressWarnings("unchecked") private Collection<Entity> entities(Object visual) throws Exception {
        return (Collection<Entity>) call(visual, "entities");
    }
    private static Number number(Object value) { return (Number) value; }
    private void near(double actual, double expected, String label) {
        require(Math.abs(actual - expected) < .0001, label + ": expected=" + expected + ", actual=" + actual);
    }
    private void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
    }
    private void stage(String value) { stages.add(value); getLogger().info("TANKS_NATIVE_STAGE_PASS: " + value); }
    private void finish(Throwable failure) {
        try {
            Files.createDirectories(getDataFolder().toPath());
            String status = failure == null ? "TANKS_NATIVE_SMOKE_PASS" : "TANKS_NATIVE_SMOKE_FAIL";
            Files.writeString(getDataFolder().toPath().resolve("result.txt"), status + ": " + assertions
                    + " assertions\n" + String.join("\n", stages) + (failure == null ? "\n" : "\n" + failure));
            if (failure == null) getLogger().info(status + ": " + assertions + " assertions");
            else getLogger().log(Level.SEVERE, status + ": " + assertions + " assertions", failure);
        } catch (Throwable writing) { getLogger().log(Level.SEVERE, "Cannot write native result", writing); }
        finally { Bukkit.getScheduler().runTask(this, Bukkit::shutdown); }
    }
}
