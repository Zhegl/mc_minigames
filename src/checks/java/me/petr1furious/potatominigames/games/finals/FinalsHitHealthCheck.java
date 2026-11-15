package me.petr1furious.potatominigames.games.finals;

import java.lang.reflect.*;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

/** Accepted-damage timing and lifecycle for temporary enemy health labels. */
@SuppressWarnings({"deprecation", "removal"})
public final class FinalsHitHealthCheck {
    private static int checks;
    private static long now;
    private static boolean active = true;
    private static Object tracker;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static Object call(String name, Class<?> argType, Object arg) throws Exception {
        Method m = tracker.getClass().getDeclaredMethod(name, argType); m.setAccessible(true);
        return m.invoke(tracker, arg);
    }
    private static boolean visible(Player p) throws Exception { return (boolean) call("visible", Player.class, p); }
    private static void damage(Player p, double amount, boolean cancelled) throws Exception {
        var event = new EntityDamageEvent(p, EntityDamageEvent.DamageCause.CUSTOM, null, amount);
        event.setCancelled(cancelled); call("onDamage", EntityDamageEvent.class, event);
    }
    public static void main(String[] args) throws Exception {
        Class<?> type;
        try { type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsHitHealth"); }
        catch (ClassNotFoundException e) { throw new AssertionError("Finals needs a five-second accepted-hit health reveal tracker", e); }
        var ctor = type.getDeclaredConstructor(Predicate.class, LongSupplier.class); ctor.setAccessible(true);
        tracker = ctor.newInstance((Predicate<Player>) p -> active, (LongSupplier) () -> now);
        UUID id = UUID.randomUUID();
        Player p = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (self, method, a) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    default -> throw new AssertionError(method.getName());
                });
        var handler = type.getDeclaredMethod("onDamage", EntityDamageEvent.class).getAnnotation(EventHandler.class);
        check(handler.priority() == EventPriority.MONITOR && handler.ignoreCancelled(), "Observe accepted damage after cancellation checks");
        check(!visible(p), "Unhit players never expose HP");
        damage(p, 0, false); damage(p, 3, true);
        check(!visible(p), "Zero and cancelled damage never expose HP");
        now = 20; damage(p, 3, false);
        check(visible(p), "Accepted damage starts exposure");
        now = 119; check(visible(p), "Exposure lasts through tick 99");
        now = 120; check(!visible(p), "Exposure ends exactly 100 ticks after the hit");
        now = 200; damage(p, 3, false);
        now = 280; damage(p, 1, false);
        now = 300; check(visible(p), "A further hit refreshes the entire five-second window");
        now = 379; check(visible(p), "Refreshed window lasts to its final tick");
        now = 380; check(!visible(p), "Refreshed window expires");
        active = false; damage(p, 3, false); active = true;
        check(!visible(p), "Inactive contestants cannot acquire exposure");
        damage(p, 2, false); active = false;
        check(!visible(p), "Dead or downed contestants cannot show HP");
        call("forget", UUID.class, id); active = true;
        check(!visible(p), "Respawning or reconnecting cannot inherit a previous life's exposure");
        damage(p, 2, false);
        Method close = type.getDeclaredMethod("close"); close.setAccessible(true); close.invoke(tracker);
        damage(p, 2, false); check(!visible(p), "Match cleanup clears exposure and ignores later damage");
        System.out.println("FinalsHitHealthCheck: " + checks + " checks passed");
    }
}
