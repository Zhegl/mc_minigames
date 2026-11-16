import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Native items and damage events; fake player transport dispatches through the real Session. */
public final class FinalsBulletReflectionSmoke {
    private final JavaPlugin main;
    private final ClassLoader loader;
    private final World nativeWorld = Bukkit.getWorlds().getFirst();
    private World world;
    private Object session, weapons, context, knockback, regeneration;
    private P shooter, defender, unrelated;
    private DamageHook duringDamage;
    private static final Vector MOVEMENT = new Vector(.11, .17, -.09);
    private long tick = 100;
    private int checks, damageDepth;

    private FinalsBulletReflectionSmoke(JavaPlugin main) {
        this.main = main;
        loader = main.getClass().getClassLoader();
    }

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsBulletReflectionSmoke(main).run();
    }

    @SuppressWarnings("unchecked")
    private int run() throws Exception {
        world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "getUID" -> nativeWorld.getUID();
                    case "getName" -> "bullet_reflection_fixture";
                    case "rayTrace" -> {
                        Location eye = (Location) args[0];
                        Vector direction = (Vector) args[1];
                        double reach = (double) args[2], radius = (double) args[5];
                        java.util.function.Predicate<Entity> filter = (java.util.function.Predicate<Entity>) args[6];
                        RayTraceResult hit = defender.bounds().expand(radius).rayTrace(eye.toVector(), direction, reach);
                        yield hit == null || !filter.test(defender.player) ? null
                                : new RayTraceResult(hit.getHitPosition(), defender.player);
                    }
                    case "spawnParticle", "playSound" -> null;
                    default -> zero(method.getReturnType());
                });
        shooter = new P("Shooter", 0, 100, 0, 0);
        defender = new P("Defender", 0, 100.7, 2, 180);
        unrelated = new P("Unrelated", 8, 100, 8, 0);
        Object active = bare(type("me.petr1furious.potatominigames.core.session.ActiveSession"));
        set(active, "phase", enumeration("me.petr1furious.potatominigames.core.session.GamePhase", "RUNNING"));
        Object services = make("me.petr1furious.potatominigames.core.game.SessionServices",
                main, active, null, null, null, null, null, null, null, null, null, null);
        session = make("FinalsSession", services, null);
        Class<?> arenaType = type("FinalsMapArena");
        Object arena = Proxy.newProxyInstance(loader, new Class<?>[] {arenaType}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "contains" -> ((Location) args[0]).getWorld() == world;
                    case "world" -> world;
                    default -> zero(method.getReturnType());
                });
        set(session, "world", world);
        set(session, "arena", arena);
        Map<UUID, Integer> roster = (Map<UUID, Integer>) get(session, "roster");
        roster.put(shooter.id, 0); roster.put(defender.id, 1);
        ((Set<UUID>) get(session, "connected")).addAll(roster.keySet());
        set(session, "state", make("FinalsState", roster, 0L));
        context = call(session, "combatContext");
        weapons = make("FinalsWeapons", context);
        set(session, "weapons", weapons);
        try { knockback = get(session, "bulletKnockback"); }
        catch (NoSuchFieldException beforeFix) { /* Old artifacts still exercise the damage regression. */ }
        if (knockback != null) call(knockback, "start", main);
        try {
            reset("AKM", "DUAL_BLADES");
            guard(defender); shoot();
            near(defender.health, 98.04, "A precision block reduces incoming bullet damage by thirty percent");
            near(shooter.health, 97.2, "The real AKM hit reflects its full body-shot damage");
            require(shooter.pushes.equals(List.of(new Pushes(true, true))) && shooter.velocity.equals(MOVEMENT),
                    "Reflected gunfire cancels both native knockback events without changing existing movement");
            require(shooter.lastDamager == defender.player && shooter.damageEvents == 1,
                    "The reflected event credits the blade wielder exactly once");
            require(!(boolean) get(session, "applyingCombatDamage")
                            && !(boolean) get(session, "applyingBulletDamage"),
                    "Nested reflected damage restores both production dispatch flags");

            reset("AKM", "DUAL_BLADES"); shoot();
            near(defender.health, 97.2, "Dual Blades do not reflect without an active guard");
            require(defender.pushes.equals(List.of(new Pushes(true, true))) && defender.velocity.equals(MOVEMENT),
                    "A normal gun hit cancels Bukkit and Paper knockback while preserving existing movement");
            near(shooter.health, 100, "An unguarded hit does not damage the shooter");

            reset("AKM", "DUAL_BLADES"); defender.location.setYaw(0);
            guard(defender); shoot();
            near(defender.health, 97.2, "A shot arriving behind the blades bypasses the guard");
            near(shooter.health, 100, "Rear shots are not reflected");

            for (float angle : new float[]{-14.01f, -14f, 14f, 14.01f}) {
                for (boolean vertical : new boolean[]{false, true}) {
                    reset("AKM", "DUAL_BLADES");
                    defender.location.setY(shooter.location.getY());
                    defender.location.setPitch(0);
                    if (vertical) defender.location.setPitch(angle);
                    else defender.location.setYaw(180 + angle);
                    guard(defender);
                    call(context, "damageBullet", defender.player, shooter.player, 2.8d, shooter.eye());
                    boolean inside = Math.abs(angle) <= 14;
                    near(defender.health, inside ? 98.04 : 97.2,
                            "Ranged damage reduction uses the precision cone in both axes");
                    near(shooter.health, inside ? 97.2 : 100,
                            "Only shots inside the inclusive precision cone reflect");
                }
            }

            reset("AKM", "DUAL_BLADES"); guard(defender);
            tick += 6; set(session, "tick", tick); shoot();
            near(defender.health, 97.2, "The expired guard cannot reflect a later shot");

            reset("MODEL_1887", "DUAL_BLADES"); guard(defender); shoot();
            near(defender.health, 89.92, "A guarded shotgun blast still deals reduced damage");
            near(shooter.health, 85.6, "All eight body pellets are reflected as one aggregate hit");
            require(shooter.damageEvents == 1 && defender.damageEvents == 1,
                    "Shotgun aggregation produces one incoming and one reflected damage event");
            require(shooter.pushes.equals(List.of(new Pushes(true, true))) && shooter.velocity.equals(MOVEMENT),
                    "An aggregated shotgun reflection also suppresses both knockback paths");

            reset("AKM", "RIOT_SHIELD"); guard(defender); shoot();
            near(defender.health, 99.02, "Riot Shield retains its existing frontal damage reduction");
            near(shooter.health, 100, "Riot Shield does not acquire blade reflection");

            reset("SWORD", "DUAL_BLADES"); guard(defender);
            EntityDamageByEntityEvent melee = event(shooter.player, defender.player, 1);
            call(session, "onEntityDamageByEntity", melee);
            require(melee.isCancelled(), "The native melee attack uses the custom weapon dispatch");
            near(defender.health, 96.55, "Melee damage is reduced by exactly twenty-five percent instead of reflecting");
            near(shooter.health, 100, "The sword attacker takes no reflected melee damage");
            require(defender.pushes.equals(List.of(new Pushes(false, false))) && !defender.velocity.equals(MOVEMENT),
                    "Native melee knockback is not cancelled by the bullet-only listener");

            reset("AKM", "DUAL_BLADES"); guard(defender);
            call(context, "damage", defender.player, shooter.player, 8d, shooter.eye());
            near(defender.health, 94.4, "Generic gadget damage remains ordinary guarded damage");
            near(shooter.health, 100, "Explosions and other generic damage are not reflected");
            require(defender.pushes.equals(List.of(new Pushes(false, false))) && !defender.velocity.equals(MOVEMENT),
                    "Generic damage remains free to change the target's velocity");

            reset("DUAL_BLADES", "DUAL_BLADES"); guard(shooter); guard(defender);
            call(context, "damageBullet", defender.player, shooter.player, 4d, shooter.eye());
            near(defender.health, 97.2, "The first guarded defender reflects the bullet while taking reduced damage");
            near(shooter.health, 97.2, "Return damage may be reduced by a guard but is never reflected again");
            require(shooter.damageEvents == 1 && defender.damageEvents == 1,
                    "Two guarding players cannot recurse or duplicate reflected hits");

            reset("AKM", "DUAL_BLADES"); shooter.health = 1;
            guard(defender); shoot();
            require(shooter.health == 0 && shooter.killer == defender.player,
                    "Lethal reflection reaches Player.damage with the defender as the killer source");

            reset("AKM", "DUAL_BLADES"); roster.put(defender.id, 0); guard(defender); shoot();
            near(shooter.health, 100, "Friendly fire cannot create reflected damage");
            near(defender.health, 100, "Friendly fire remains blocked");

            reset("AKM", "DUAL_BLADES");
            ((Map<UUID, Long>) get(session, "protectedUntil")).put(defender.id, tick + 10);
            guard(defender); shoot();
            near(shooter.health, 100, "Spawn protection prevents reflection damage");
            near(defender.health, 100, "Spawn protection still prevents incoming gun damage");
            knockbackScopeChecks();
            regenerationChecks();
            return checks;
        } finally {
            call(weapons, "clear");
            if (knockback != null) call(knockback, "close");
            if (regeneration != null) call(regeneration, "close");
        }
    }

    @SuppressWarnings("unchecked")
    private void regenerationChecks() throws Exception {
        regeneration = get(session, "regeneration");
        call(regeneration, "start", main);
        Map<UUID, Object> classes = (Map<UUID, Object>) get(session, "classes");
        String[] builds = {"LIGHT", "MEDIUM", "HEAVY"};
        for (int index = 0; index < builds.length; index++) {
            reset("AKM", "DUAL_BLADES");
            Object playerClass = enumeration("FinalsClass", builds[index]);
            classes.put(defender.id, playerClass);
            defender.health = 10;
            defender.maxHealth = (double) call(playerClass, "health");
            call(regeneration, "reset", defender.id, tick - 1000);
            long hitAt = tick;
            shoot();
            near(defender.health, 7.2, builds[index] + " loses health through the real gun damage path");
            setTick(hitAt + 159 + index * 20);
            call(regeneration, "tick", defender.player, playerClass);
            near(defender.health, 7.2, builds[index] + " gun damage restarts its full regeneration delay");
            setTick(hitAt + 160 + index * 20);
            call(regeneration, "tick", defender.player, playerClass);
            near(defender.health, 7.4, builds[index] + " begins regenerating at its class-specific deadline");
        }

        reset("AKM", "DUAL_BLADES");
        Object medium = enumeration("FinalsClass", "MEDIUM");
        shooter.health = defender.health = 10;
        shooter.maxHealth = defender.maxHealth = 25;
        call(regeneration, "reset", shooter.id, tick - 1000);
        call(regeneration, "reset", defender.id, tick - 1000);
        long reflectedAt = tick;
        guard(defender); shoot();
        call(regeneration, "tick", defender.player, medium);
        call(regeneration, "tick", shooter.player, medium);
        near(defender.health, 8.04, "Reduced incoming damage still delays the blade defender's regeneration");
        near(shooter.health, 7.2, "The actual reflected hit delays regeneration for the shooter");
        setTick(reflectedAt + 180);
        call(regeneration, "tick", shooter.player, medium);
        near(shooter.health, 7.4, "Reflected damage follows the same nine-second Medium recovery delay");

        for (boolean protectedSpawn : new boolean[] {false, true}) {
            reset("AKM", "DUAL_BLADES");
            defender.health = 10;
            defender.maxHealth = 25;
            call(regeneration, "reset", defender.id, tick - 1000);
            if (protectedSpawn)
                ((Map<UUID, Long>) get(session, "protectedUntil")).put(defender.id, tick + 10);
            else ((Map<UUID, Integer>) get(session, "roster")).put(defender.id, 0);
            // Bypass the early combat-context filter so the cancelled event reaches MONITOR.
            defender.player.damage(2.8, shooter.player);
            require(defender.damageEvents == 1, "The cancelled hit reaches Session validation and the MONITOR event phase");
            call(regeneration, "tick", defender.player, medium);
            near(defender.health, 10.2, (protectedSpawn ? "Spawn-protected" : "Friendly")
                    + " cancelled gunfire does not reset an elapsed regeneration delay");
        }
    }

    private void setTick(long value) throws Exception { tick = value; set(session, "tick", tick); }

    private void knockbackScopeChecks() throws Exception {
        reset("AKM", "DUAL_BLADES");
        duringDamage = (target, source) -> {
            if (damageDepth != 1) return;
            require(probe(defender, unrelated.player, false).equals(new Pushes(false, false)),
                    "A different attacking entity is not suppressed inside the bullet's scope");
            require(probe(unrelated, shooter.player, false).equals(new Pushes(false, false)),
                    "A different target is not suppressed inside the bullet's scope");
            require(probe(defender, shooter.player, true).equals(new Pushes(false, false)),
                    "Explosion knockback is allowed even for the currently scoped pair");
            call(context, "damage", defender.player, shooter.player, 1d, shooter.eye());
            require(defender.pushes.equals(List.of(new Pushes(false, false))),
                    "Nested generic damage temporarily clears the outer bullet suppression");
            require(probe(defender, shooter.player, false).equals(new Pushes(true, true)),
                    "The outer bullet's suppression is restored after nested generic damage returns");
        };
        try { shoot(); } finally { duringDamage = null; }
        require(defender.pushes.equals(List.of(new Pushes(false, false), new Pushes(true, true))),
                "Only the nested generic hit changes velocity during the real gun dispatch");
        require(probe(defender, shooter.player, false).equals(new Pushes(false, false)),
                "Both event types regain ordinary knockback after the gun call returns");

        reset("AKM", "DUAL_BLADES");
        duringDamage = (target, source) -> { throw new IllegalStateException("native knockback fixture failure"); };
        boolean failed = false;
        try { call(context, "damageBullet", defender.player, shooter.player, 2d, shooter.eye()); }
        catch (IllegalStateException expected) { failed = true; }
        finally { duringDamage = null; }
        require(failed && probe(defender, shooter.player, false).equals(new Pushes(false, false)),
                "An exception from Player.damage cannot leak knockback suppression");
        require(!(boolean) get(session, "applyingCombatDamage") && !(boolean) get(session, "applyingBulletDamage"),
                "An exceptional bullet also restores the Session's damage dispatch flags");
        call(knockback, "close");
        call(context, "damageBullet", defender.player, shooter.player, 2d, shooter.eye());
        require(defender.pushes.equals(List.of(new Pushes(false, false))),
                "Closing the session-owned listener unregisters both native event handlers");
    }

    private interface DamageHook { void run(P target, Player source) throws Exception; }
    private record Pushes(boolean bukkit, boolean paper) {}

    private Pushes probe(P target, Entity source, boolean explosion) {
        Vector impulse = new Vector(.3, .2, .1);
        EntityKnockbackByEntityEvent bukkit = new EntityKnockbackByEntityEvent(target.player, source,
                explosion ? EntityKnockbackEvent.KnockbackCause.EXPLOSION : EntityKnockbackEvent.KnockbackCause.ENTITY_ATTACK,
                .4, impulse.clone(), target.velocity.clone().add(impulse));
        io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent paper =
                new io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent(target.player,
                        explosion ? io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.EXPLOSION
                                : io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK,
                        source, impulse.clone());
        Bukkit.getPluginManager().callEvent(bukkit);
        Bukkit.getPluginManager().callEvent(paper);
        return new Pushes(bukkit.isCancelled(), paper.isCancelled());
    }

    @SuppressWarnings("unchecked")
    private void reset(String gun, String defense) throws Exception {
        tick += 100; set(session, "tick", tick);
        ((Map<?, ?>) get(session, "protectedUntil")).clear();
        ((Map<UUID, Integer>) get(session, "roster")).put(defender.id, 1);
        shooter.location.setYaw(0); defender.location.setYaw(180);
        shooter.location.setPitch(0); defender.location.setPitch(0);
        defender.location.setY(100.7);
        defender.location.setDirection(shooter.eye().toVector().subtract(defender.eye().toVector()));
        if (gun.equals("DUAL_BLADES"))
            shooter.location.setDirection(defender.eye().toVector().subtract(shooter.eye().toVector()));
        shooter.reset(); defender.reset();
        for (var entry : Map.of(shooter, gun, defender, defense).entrySet()) {
            Object weapon = enumeration("FinalsWeapon", entry.getValue());
            ((Map<UUID, Object>) get(session, "classes")).put(entry.getKey().id, call(weapon, "playerClass"));
            call(weapons, "equip", entry.getKey().player, weapon);
        }
    }

    private void guard(P player) throws Exception { interact(player); }
    private void shoot() throws Exception { interact(shooter); }
    private void interact(P player) throws Exception {
        PlayerInteractEvent input = new PlayerInteractEvent(player.player, Action.RIGHT_CLICK_AIR,
                player.hand, null, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
        require((boolean) call(weapons, "interact", input) && input.isCancelled(),
                "The actual equipped weapon consumes primary right-click input");
    }

    private final class P {
        final UUID id = UUID.randomUUID();
        final Player player;
        final PlayerInventory inventory;
        final AttributeInstance maximumHealth;
        final Location location;
        ItemStack hand;
        int slot, damageEvents;
        double health = 100, maxHealth = 100;
        Player lastDamager, killer;
        Vector velocity = MOVEMENT.clone();
        final List<Pushes> pushes = new ArrayList<>();

        P(String name, double x, double y, double z, float yaw) {
            location = new Location(world, x, y, z, yaw, 0);
            maximumHealth = (AttributeInstance) Proxy.newProxyInstance(AttributeInstance.class.getClassLoader(),
                    new Class<?>[] {AttributeInstance.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getAttribute" -> Attribute.MAX_HEALTH;
                        case "getValue", "getBaseValue", "getDefaultValue" -> maxHealth;
                        default -> zero(method.getReturnType());
                    });
            inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                    new Class<?>[] {PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "setItem" -> { if ((int) args[0] == 0) hand = (ItemStack) args[1]; yield null; }
                        case "getItemInMainHand" -> hand;
                        case "getHeldItemSlot" -> slot;
                        case "setHeldItemSlot" -> { slot = (int) args[0]; yield null; }
                        default -> zero(method.getReturnType());
                    });
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "equals" -> proxy == args[0]; case "hashCode" -> id.hashCode();
                        case "getUniqueId" -> id; case "getName", "toString" -> name;
                        case "getWorld" -> world; case "getLocation" -> location.clone();
                        case "getEyeLocation" -> eye(); case "getBoundingBox" -> bounds();
                        case "getInventory" -> inventory; case "getGameMode" -> GameMode.SURVIVAL;
                        case "isOnline" -> true; case "isDead" -> health <= 0;
                        case "locale" -> Locale.ENGLISH; case "getHealth" -> health;
                        case "getMaxHealth" -> maxHealth;
                        case "setHealth" -> { health = ((Number) args[0]).doubleValue(); yield null; }
                        case "getAttribute" -> args[0] == Attribute.MAX_HEALTH ? maximumHealth : null;
                        case "getKiller" -> killer;
                        case "getVelocity" -> velocity.clone();
                        case "setVelocity" -> { velocity = ((Vector) args[0]).clone(); yield null; }
                        case "damage" -> { damage(((Number) args[0]).doubleValue(), (Player) args[1]); yield null; }
                        default -> zero(method.getReturnType());
                    });
        }

        Location eye() { return location.clone().add(0, 1.62, 0); }
        BoundingBox bounds() { return new BoundingBox(location.getX()-.3, location.getY(), location.getZ()-.3,
                location.getX()+.3, location.getY()+1.8, location.getZ()+.3); }
        void reset() { health = maxHealth = 100; damageEvents = 0; lastDamager = null; killer = null;
            velocity = MOVEMENT.clone(); pushes.clear(); }
        void damage(double amount, Player source) throws Exception {
            if (++damageDepth > 4) throw new AssertionError("Reflected damage recursed");
            try {
                damageEvents++;
                if (duringDamage != null) duringDamage.run(this, source);
                EntityDamageByEntityEvent event = event(source, player, amount);
                call(session, "onEntityDamageByEntity", event);
                call(session, "onEntityDamage", event);
                // This fixture's Session is outside the global registry, so global routing is inert.
                // The registered MONITOR listener still observes the real, fully validated event.
                if (regeneration != null) Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled() && event.getFinalDamage() > 0) {
                    Pushes result = probe(this, source, false);
                    pushes.add(result);
                    if (!result.bukkit()) velocity.add(new Vector(.3, .2, .1));
                    if (!result.paper()) velocity.add(new Vector(.3, .2, .1));
                    lastDamager = source;
                    health = Math.max(0, health-event.getFinalDamage());
                    if (health == 0) killer = source;
                }
            } finally { damageDepth--; }
        }
    }

    private static EntityDamageByEntityEvent event(Player source, Player target, double amount) {
        return new EntityDamageByEntityEvent(source, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.GENERIC).build(), amount);
    }
    private Class<?> type(String name) throws Exception {
        return Class.forName(name.contains(".") ? name : "me.petr1furious.potatominigames.games.finals."+name, true, loader);
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private Object enumeration(String name, String value) throws Exception {
        return Enum.valueOf((Class) type(name), value);
    }
    private Object make(String name, Object... args) throws Exception {
        for (Constructor<?> constructor : type(name).getDeclaredConstructors()) {
            if (constructor.getParameterCount() != args.length) continue;
            constructor.setAccessible(true); return constructor.newInstance(args);
        }
        throw new NoSuchMethodException(name);
    }
    private static Object bare(Class<?> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static Object call(Object object, String name, Object... args) throws Exception {
        for (Method method : object.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            method.setAccessible(true);
            try { return method.invoke(object, args); }
            catch (InvocationTargetException error) {
                if (error.getCause() instanceof Exception cause) throw cause;
                if (error.getCause() instanceof Error cause) throw cause;
                throw error;
            }
        }
        throw new NoSuchMethodException(object.getClass().getName()+"."+name);
    }
    private static Object zero(Class<?> type) {
        if (type == boolean.class) return false; if (type == int.class) return 0;
        if (type == long.class) return 0L; if (type == double.class) return 0d;
        if (type == float.class) return 0f; return null;
    }
    private void near(double actual, double expected, String message) {
        require(Math.abs(actual-expected)<.00001, message+": expected "+expected+", got "+actual);
    }
    private void require(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
}
