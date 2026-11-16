import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import net.kyori.adventure.util.TriState;

/** Playerless native12-vehicle replay, production combat and exact resource cleanup. */
public final class TanksLoadSmoke extends JavaPlugin {
    private static final String PKG = "me.petr1furious.potatominigames.games.tanks.";
    private ClassLoader loader;
    private JavaPlugin main;
    private World world;
    private Object arena, session, match, scope;
    private Class<?> chassisType, actorType, vehicleType, shellType, flightType;
    private final List<Unit> units = new ArrayList<>();
    private final List<String> stages = new ArrayList<>();
    private final Map<String, Method> methods = new HashMap<>();
    private final long[] cpu = new long[200], intervals = new long[199];
    private int checks, baseline, peak, after, expectedEntities, predictions, aimQueries, spottingQueries, shots, peakShells;
    private long previousTick;
    private String metrics = "No replay completed";
    private boolean finished;

    private static final class Unit {
        UUID id;
        Object chassis, actor, vehicle, visual;
        Location origin, at;
        Zombie attributeBody;
        Player transport;
        List<Entity> entities;
        double attributeBaseline;
    }

    @Override public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> guard(() -> {
            main = (JavaPlugin) Bukkit.getPluginManager().getPlugin("PotatoMinigames");
            require(main != null && main.isEnabled(), "production plugin enabled");
            loader = main.getClass().getClassLoader();
            chassisType = type("TanksRules$Chassis");
            actorType = type("TanksMatch$Actor");
            vehicleType = type("TanksSession$Vehicle");
            shellType = type("TanksRules$Shell");
            flightType = type("TanksSession$ShellFlight");
            world = Bukkit.createWorld(new WorldCreator("tanks-load-" + UUID.randomUUID().toString().substring(0, 8))
                    .keepSpawnLoaded(TriState.FALSE).generateStructures(false).generator(new ChunkGenerator() {
                        @Override public Location getFixedSpawnLocation(World w, Random random) { return new Location(w, .5, 81, .5); }
                    }));
            require(world != null, "dedicated empty native load world created");
            world.setViewDistance(3);world.setSimulationDistance(3);world.setTime(18000);
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            match = make(type("TanksMatch"), new Class<?>[]{int.class, int.class, boolean.class}, 20000, 1000, false);
            Class<?> servicesType = outside("core.game.SessionServices");
            Constructor<?> servicesConstructor = servicesType.getDeclaredConstructors()[0];
            Object[] args = new Object[servicesConstructor.getParameterCount()];
            args[0] = main;
            args[10] = make(outside("core.text.Messages"), new Class<?>[0]);
            session = make(type("TanksSession"), new Class<?>[]{servicesType, type("TanksConfig"), type("TanksGame")},
                    servicesConstructor.newInstance(args), null, null);
            set(session, "world", world);set(session, "match", match);set(session, "built", true);
            call(session, "retainArenaChunks");
            require(world.getPluginChunkTickets().getOrDefault(main, List.of()).size() == 72, "production pins72 arena chunks");
            scope = make(type("TanksScope"), new Class<?>[0]);
            arena = make(type("TanksArena"), new Class<?>[]{World.class}, world);
            new BukkitRunnable() {
                @Override public void run() { guard(() -> {
                    if (!(boolean) call(arena, "buildSlice", new Class<?>[]{int.class}, 4096)) return;
                    cancel();
                    Bukkit.getScheduler().runTaskLater(TanksLoadSmoke.this, () -> guard(() -> { spawn(); replay(); }), 5);
                }); }
            }.runTaskTimer(this, 1, 1);
        }));
    }

    @SuppressWarnings("unchecked") private void spawn() throws Exception {
        baseline = world.getEntities().size();
        require(world.getEntitiesByClass(TextDisplay.class).size() == 2,
                "arena has two base labels after registry settling: total="+baseline+", types="+world.getEntities().stream().map(e->e.getType().toString()).toList());
        Map<UUID, Object> vehicles = (Map<UUID, Object>) field(session, "vehicles");
        Map<UUID, String> names = (Map<UUID, String>) field(session, "names");
        Map<UUID, Player> connections = (Map<UUID, Player>) field(session, "connections");
        for (int i = 0; i < 12; i++) {
            Unit u = new Unit();
            u.id = new UUID(610, i);u.chassis = chassisType.getEnumConstants()[i % 3];
            u.actor = call(match, "add", new Class<?>[]{UUID.class, int.class, chassisType}, u.id, i / 6, u.chassis);
            u.origin = (Location) call(arena, "spawn", new Class<?>[]{int.class, int.class}, i / 6, i % 6);
            u.at = u.origin.clone();
            u.visual = make(type("TankVisual"), new Class<?>[]{Location.class, int.class, chassisType}, u.at, i / 6, u.chassis);
            u.entities = new ArrayList<>((Collection<Entity>) call(u.visual, "entities"));
            require(u.entities.stream().noneMatch(Entity::isVisibleByDefault), "native model starts hidden until viewer policy allows it");
            u.vehicle = make(vehicleType, new Class<?>[]{Location.class}, u.at);
            set(u.vehicle, "chassis", u.chassis);set(u.vehicle, "visual", u.visual);set(u.vehicle, "settled", 80);
            vehicles.put(u.id, u.vehicle);names.put(u.id, "Load" + i);
            u.attributeBody = world.spawn(u.at.clone().add(0, 12, 0), Zombie.class, body -> {
                body.setAI(false);body.setGravity(false);body.setInvulnerable(true);body.setSilent(true);body.setPersistent(false);
            });
            var speed = u.attributeBody.getAttribute(Attribute.MOVEMENT_SPEED);
            speed.setBaseValue(.1);
            speed.addTransientModifier(new AttributeModifier(new NamespacedKey("tanks_load", "preserved"), .02, AttributeModifier.Operation.ADD_NUMBER));
            u.attributeBaseline = speed.getValue();
            u.transport = transport(u);
            connections.put(u.id, u.transport);
            units.add(u);
        }
        call(match, "start");
        expectedEntities = baseline + units.stream().mapToInt(u -> u.entities.size() + 1).sum();
        require(expectedEntities > 700 && expectedEntities < 1500, "twelve native models have a bounded entity budget");
        stage("12 native vehicles and12 native scope attributes initialized");
    }

    private void replay() {
        new BukkitRunnable() {
            int age = -20;
            @Override public void run() { guard(() -> {
                long start = System.nanoTime();
                if (age >= 0 && previousTick != 0 && age > 0) intervals[age - 1] = start - previousTick;
                if (age >= 0) previousTick = start;
                set(session, "tick", (long) age + 100);
                for (int i = 0; i < units.size(); i++) {
                    Unit u = units.get(i);
                    double phase = (age + 20) * .025 + i;
                    u.at = u.origin.clone().add(.2 * Math.sin(phase), 0, (i < 6 ? -1 : 1) * (.2 + .15 * Math.cos(phase)));
                    u.at.setYaw(u.origin.getYaw() + age * 1.2f);
                    // Upward cannon elevations keep the load volleys clear of friendly spawn rows.
                    float turret = u.origin.getYaw() + age * 2.0f + i * 11, pitch = -16 + (float) (Math.sin(phase) * 2);
                    set(u.vehicle, "at", u.at);set(u.vehicle, "turretYaw", turret);set(u.vehicle, "turretPitch", pitch);set(u.vehicle, "speed", .12);
                    double max = number(call(u.chassis, "maxHp")).doubleValue();
                    call(u.visual, "update", new Class<?>[]{Location.class, float.class, float.class, double.class, double.class},
                            u.at, turret, pitch, max * (.75 + .2 * Math.sin(phase)), max);
                    if (!(boolean) call(arena, "canDrive", new Class<?>[]{Location.class, chassisType}, u.at, u.chassis))
                        throw new AssertionError("native replay attempted an obstructed hull placement");
                    call(session, "aimAtCrosshair", new Class<?>[]{Player.class, actorType, vehicleType}, u.transport, u.actor, u.vehicle);
                    if (age >= 0) aimQueries++;
                    if (age % 5 == 0) {
                        call(session, "predict", new Class<?>[]{actorType, Location.class}, u.actor, call(u.visual, "muzzle"));
                        if (age >= 0) predictions++;
                    }
                    if (age % 40 == 0) call(scope, "toggle", new Class<?>[]{Player.class, long.class}, u.transport, (long) age + 100);
                    if (age == 0 || age == 120) {
                        int before = shells().size();fire(u);shots += shells().size()-before;
                    }
                }
                peakShells=Math.max(peakShells,shells().size());
                call(session,"flyShells");
                if (age % 5 == 0) for (Unit a : units) for (Unit b : units) if (a != b && (a.id.getLeastSignificantBits() < 6) != (b.id.getLeastSignificantBits() < 6)) {
                    call(session, "canSpot", new Class<?>[]{actorType, actorType}, a.actor, b.actor);
                    if (age >= 0) spottingQueries++;
                }
                call(match, "tick");
                int entities = world.getEntities().size();peak = Math.max(peak, entities);
                if (entities != expectedEntities) throw new AssertionError("entity count drifted during replay: " + entities + " vs " + expectedEntities);
                if (age >= 0) cpu[age] = System.nanoTime() - start;
                if (++age < 200) return;
                cancel();
                summarize();
                combat();
                scopeRestore();
                cleanup();
                Bukkit.getScheduler().runTask(TanksLoadSmoke.this, () -> guard(() -> {
                    after = world.getEntities().size();
                    require(after == baseline, "all vehicle and scope-body entities return exactly to the arena baseline");
                    world.removePluginChunkTickets(main);
                    require(world.getPluginChunkTickets().getOrDefault(main, List.of()).isEmpty(), "production plugin chunk tickets return to zero");
                    finish(null);
                }));
            }); }
        }.runTaskTimer(this, 1, 1);
    }

    private void summarize() {
        long[] sorted = cpu.clone(), spacing = intervals.clone();Arrays.sort(sorted);Arrays.sort(spacing);
        double p50 = sorted[99] / 1e6, p95 = sorted[189] / 1e6, max = sorted[199] / 1e6;
        metrics = String.format(Locale.ROOT,
                "models=12 measuredTicks=200 warmupTicks=20 cpuMs(p50=%.3f,p95=%.3f,max=%.3f) scheduledIntervalMs(p50=%.3f,p95=%.3f,max=%.3f) baselineEntities=%d peakEntities=%d expectedEntities=%d aimQueries=%d predictionQueries=%d spottingQueries=%d shots=%d peakShells=%d",
                p50,p95,max,spacing[99]/1e6,spacing[189]/1e6,spacing[198]/1e6,baseline,peak,expectedEntities,aimQueries,predictions,spottingQueries,shots,peakShells);
        metrics += " nativeAttributeBaselines=" + units.stream().map(u -> u.attributeBaseline).distinct().sorted().toList();
        require(p95 < 40, "local synchronous replay p95 remains within40ms of a50ms server tick");
        require(aimQueries == 2400 && predictions == 480 && spottingQueries == 2880, "load uses actual production aim/prediction/spotting methods at bounded production cadence");
        require(shots == 24 && peakShells <= 12, "two production cannon volleys create24 bounded simultaneous-flight shots");
        stage("200 native replay ticks kept stable entity count; " + metrics);
    }

    @SuppressWarnings("unchecked") private void combat() throws Exception {
        Unit shooter = units.get(0), ally = units.get(1), enemy = units.get(6);
        move(shooter, 0, 90, -18);move(ally, 0, 90, -11);move(enemy, 0, 90, -4);
        ((Map<UUID, Player>) field(session, "connections")).clear();
        ((Map<UUID, Player>) field(session, "connections")).put(shooter.id, shooter.transport);
        ((Random) field(session, "random")).setSeed(841);
        double allyHp = hp(ally), enemyHp = hp(enemy);
        fire(shooter);
        for (int i = 0; i < 8 && !shells().isEmpty(); i++) call(session, "flyShells");
        require(shells().isEmpty() && hp(ally) == allyHp && hp(enemy) == enemyHp,
                "production ally interception consumes the shell without damaging either hull");
        require(number(call(ally.actor, "trackTicks")).intValue() == 0, "ally interception never damages tracks");
        move(ally, 8, 90, -11);reload();
        Object hidden = call(session, "predict", new Class<?>[]{actorType, Location.class}, shooter.actor, call(shooter.visual, "muzzle"));
        require(field(hidden, "target") == null, "production prediction does not expose an unspotted enemy contact");
        List<?> hiddenBlips = (List<?>) call(session, "radarBlips", new Class<?>[]{Player.class}, shooter.transport);
        require(hiddenBlips.size() == 6, "production radar contacts contain only six allies before spotting");
        ((Map<UUID, Long>) field(session, "spottedRed")).put(enemy.id, number(field(session, "tick")).longValue() + 60);
        Object spotted = call(session, "predict", new Class<?>[]{actorType, Location.class}, shooter.actor, call(shooter.visual, "muzzle"));
        require(enemy.id.equals(field(spotted, "target")), "same native enemy becomes a prediction contact only after spotting");
        List<?> visibleBlips = (List<?>) call(session, "radarBlips", new Class<?>[]{Player.class}, shooter.transport);
        require(visibleBlips.size() == 7, "production radar adds exactly the spotted enemy");
        fire(shooter);
        for (int i = 0; i < 12 && !shells().isEmpty(); i++) call(session, "flyShells");
        require(hp(enemy) == enemyHp - 160, "actual production fire and native swept flight penetrate enemy rear armor");
        call(enemy.actor, "base", new Class<?>[]{int.class}, 0);
        for (int i = 0; i < 3; i++) call(match, "tick");
        require(number(call(enemy.actor, "captureTicks")).intValue() > 0, "enemy has capture contribution before module damage");
        Object ap = shellType.getEnumConstants()[0];
        Object flight = make(flightType, new Class<?>[]{UUID.class, chassisType, shellType, Location.class, Vector.class},
                shooter.id, shooter.chassis, ap, shooter.at.clone(), new Vector(0, 0, 2.6));
        Location trackHit = enemy.at.clone().add(0, .4, -1.8);
        call(session, "hitTank", new Class<?>[]{flightType, UUID.class, Location.class}, flight, enemy.id, trackHit);
        require(number(call(enemy.actor, "trackTicks")).intValue() == 70
                        && number(call(enemy.actor, "captureTicks")).intValue() == 0,
                "production low hull hit damages tracks and resets only the damaged capture contribution");
        for (int i = 0; i < 6 && (boolean) call(enemy.actor, "alive"); i++)
            call(session, "hitTank", new Class<?>[]{flightType, UUID.class, Location.class}, flight, enemy.id, trackHit);
        require(!(boolean) call(enemy.actor, "alive") && number(call(shooter.actor, "kills")).intValue() == 1,
                "production destruction creates a wreck and attributes exactly one kill");
        call(session, "hitTank", new Class<?>[]{flightType, UUID.class, Location.class}, flight, enemy.id, trackHit);
        require(number(call(shooter.actor, "kills")).intValue() == 1, "repeated impact on the native wreck cannot award another kill");
        require((boolean) field(enemy.visual, "wrecked"), "logical destruction reaches the actual native renderer");
        stage("production friendly interception, hidden-contact policy, enemy penetration, tracks/capture reset and wreck attribution");
    }

    private void move(Unit u, double x, double y, double z) throws Exception {
        u.at = new Location(world, x, y, z, 0, 0);set(u.vehicle,"at",u.at);set(u.vehicle,"turretYaw",0f);set(u.vehicle,"turretPitch",0f);set(u.vehicle,"speed",0d);set(u.vehicle,"settled",80);
        double max = number(call(u.chassis,"maxHp")).doubleValue();
        call(u.visual,"update",new Class<?>[]{Location.class,float.class,float.class,double.class,double.class},u.at,0f,0f,hp(u),max);
    }
    private double hp(Unit u) throws Exception { return number(call(u.actor,"hp")).doubleValue(); }
    private void fire(Unit u) throws Exception { call(session,"fire",new Class<?>[]{Player.class},u.transport); }
    private void reload() throws Exception { for(int i=0;i<120;i++)call(match,"tick"); }
    @SuppressWarnings("unchecked") private List<Object> shells() throws Exception { return (List<Object>) field(session,"shells"); }
    private void scopeRestore() throws Exception {
        call(scope,"clear");
        for(Unit u:units) {
            var attribute=u.attributeBody.getAttribute(Attribute.MOVEMENT_SPEED);
            require(Math.abs(attribute.getValue()-u.attributeBaseline)<1e-9 && attribute.getModifier(new NamespacedKey("potatominigames","tanks_scope"))==null,
                    "scope clear restores pre-scope native calculation and removes only its modifier: baseline="+u.attributeBaseline+" actual="+attribute.getValue());
            require(attribute.getModifier(new NamespacedKey("tanks_load","preserved"))!=null,"scope clear preserves unrelated native modifiers");
        }
        stage("all12 native scope attributes restored with unrelated modifiers retained");
    }
    private void cleanup() throws Exception {
        if(scope!=null)call(scope,"clear");
        for(Unit u:units) {
            call(u.visual,"remove");u.attributeBody.remove();
            require(u.entities.stream().noneMatch(Entity::isValid),"vehicle cleanup removes every native display and seat");
        }
    }
    private Player transport(Unit u) {
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()) {
            case "getUniqueId" -> u.id;
            case "getLocation" -> u.at.clone();
            case "getEyeLocation" -> u.at.clone().add(0,2.5,0);
            case "getWorld" -> world;
            case "getAttribute" -> args[0]==Attribute.MOVEMENT_SPEED?u.attributeBody.getAttribute(Attribute.MOVEMENT_SPEED):null;
            case "getName" -> "Load"+u.id.getLeastSignificantBits();
            case "isOnline" -> true;
            case "isDead" -> false;
            case "playSound" -> null;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy==args[0];
            case "toString" -> "LoadTransport";
            default -> throw new UnsupportedOperationException("Unexpected transport method "+method.getName());
        });
    }
    private Class<?> type(String name)throws Exception{return Class.forName(PKG+name,true,loader);}
    private Class<?> outside(String name)throws Exception{return Class.forName("me.petr1furious.potatominigames."+name,true,loader);}
    private Object make(Class<?> cls,Class<?>[] signature,Object...args)throws Exception{var ctor=cls.getDeclaredConstructor(signature);ctor.setAccessible(true);return ctor.newInstance(args);}
    private Object call(Object obj,String name)throws Exception{return call(obj,name,new Class<?>[0]);}
    private Object call(Object obj,String name,Class<?>[] signature,Object...args)throws Exception{
        String key=obj.getClass().getName()+"#"+name+Arrays.toString(signature);
        Method method=methods.get(key);if(method==null){method=obj.getClass().getDeclaredMethod(name,signature);method.setAccessible(true);methods.put(key,method);}return method.invoke(obj,args);
    }
    private Object field(Object obj,String name)throws Exception{var f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
    private void set(Object obj,String name,Object value)throws Exception{var f=obj.getClass().getDeclaredField(name);f.setAccessible(true);f.set(obj,value);}
    private static Number number(Object value){return(Number)value;}
    private void require(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private void stage(String label){stages.add(label);getLogger().info("TANKS_LOAD_STAGE_PASS: "+label);}
    private interface Checked{void run()throws Exception;}
    private void guard(Checked action){if(finished)return;try{action.run();}catch(Throwable failure){finish(failure);}}
    private void finish(Throwable failure){
        if(finished)return;finished=true;Bukkit.getScheduler().cancelTasks(this);
        if(failure!=null)try{cleanup();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}
        if(world!=null&&main!=null)world.removePluginChunkTickets(main);
        try{
            Files.createDirectories(getDataFolder().toPath());
            String status=failure==null?"TANKS_LOAD_SMOKE_PASS":"TANKS_LOAD_SMOKE_FAIL";
            Files.writeString(getDataFolder().toPath().resolve("result.txt"),status+": "+checks+" assertions\n"+metrics+"\nafterEntities="+after+"\n"+String.join("\n",stages)+(failure==null?"\n":"\n"+failure));
            if(failure==null)getLogger().info(status+": "+checks+" assertions; "+metrics+" afterEntities="+after);
            else getLogger().log(Level.SEVERE,status,failure);
        }catch(Throwable writing){getLogger().log(Level.SEVERE,"Cannot write load result",writing);}
        finally{Bukkit.getScheduler().runTask(this,Bukkit::shutdown);}
    }
}
