import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.games.finals.*;

/** Native cashbox recovery and jump-pad display lifecycle in a disposable test world. */
public final class FinalsObjectiveRecoverySmoke {
    private static Object call(Object object, String name, Object... args) throws Exception {
        for (Method m : object.getClass().getDeclaredMethods())
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true); return m.invoke(object, args);
            }
        throw new NoSuchMethodException(name);
    }
    private static Object field(Object object, String name, Object... value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true);
        if (value.length > 0) f.set(object, value[0]);
        return f.get(object);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        World world = Bukkit.getWorlds().getFirst();
        Location ledge = new Location(world, 1100.5, 110, 1100.5);
        Location vault = new Location(world, 1110.5, 110, 1100.5);
        List<BlockState> originals = List.of(ledge.clone().subtract(0, 1, 0).getBlock().getState(),
                vault.clone().subtract(0, 1, 0).getBlock().getState());
        for (BlockState b : originals) b.getBlock().setType(Material.STONE, false);
        var definitions = List.of(new FinalsMapJumpPads.Pad(ledge, vault), new FinalsMapJumpPads.Pad(vault, null));
        FinalsMapArena arena = (FinalsMapArena) Proxy.newProxyInstance(FinalsMapArena.class.getClassLoader(),
                new Class<?>[]{FinalsMapArena.class}, (o, m, a) -> switch (m.getName()) {
                    case "world" -> world;
                    case "contains" -> ((Location) a[0]).getWorld().equals(world);
                    case "deathHeight" -> 90d;
                    case "vault" -> vault.clone();
                    case "jumpPads" -> definitions;
                    default -> throw new AssertionError(m.getName());
                });
        Class<?> sessionType = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsSession", true,
                main.getClass().getClassLoader());
        Constructor<?> ctor = Arrays.stream(sessionType.getDeclaredConstructors()).filter(c -> c.getParameterCount() == 2).findFirst().orElseThrow();
        ctor.setAccessible(true);
        Object session = ctor.newInstance(new SessionServices(main, null, null, null, null, null, null, null, null, null, null, null), null);
        Constructor<?> padsCtor = FinalsMapJumpPads.class.getDeclaredConstructor(FinalsMapArena.class);
        padsCtor.setAccessible(true); Object pads = padsCtor.newInstance(arena);
        int checks = 0;
        try {
            UUID actor = UUID.randomUUID();
            FinalsState state = new FinalsState(Map.of(actor, 0), 0);
            state.activateVault(actor, 0); state.tick(100);
            field(session, "world", world); field(session, "arena", arena); field(session, "state", state);
            field(session, "cashboxRecovery", ledge.clone());
            field(session, "cashboxLocation", ledge.clone().add(5, -21, 0));
            call(session, "createCashbox", new Vector(1, -2, 0));
            Item falling = (Item) field(session, "cashbox");
            call(session, "ensureCashbox");
            Item returned = (Item) field(session, "cashbox");
            require(!falling.isValid() && returned.isValid(), "Crossing custom death height replaces the falling cashbox"); checks++;
            require(returned.getLocation().distanceSquared(ledge.clone().add(0, .25, 0)) < 1e-8,
                    "Cashbox returns to departure ledge, not its vault"); checks++;
            require(returned.getVelocity().lengthSquared() == 0, "Recovery clears the fall/throw force"); checks++;
            returned.teleport(ledge.clone().add(1, -20, 0));
            call(session, "ensureCashbox");
            require(returned.isValid(), "Cashbox exactly at the death height is not recovered early"); checks++;
            returned.remove(); call(session, "ensureCashbox");
            require(((Item) field(session, "cashbox")).getLocation().distanceSquared(ledge.clone().add(0, .25, 0)) < 1e-8,
                    "A lost native item also returns to its saved departure point"); checks++;
            call(pads, "start");
            Map<?, ?> outlines = (Map<?, ?>) field(pads, "outlines");
            require(outlines.size() == 2, "Both upward and targeted pads have native outlines"); checks++;
            List<BlockDisplay> displays = outlines.values().stream().map(BlockDisplay.class::cast).toList();
            for (BlockDisplay display : displays) {
                require(display.isValid() && display.isGlowing() && !display.hasGravity() && !display.isPersistent(),
                        "Pad outline is a nonpersistent, glowing display without physics"); checks++;
                require(display.getBrightness().getBlockLight() == 15 && display.getTransformation().getScale().y < .1,
                        "Pad display is bright and remains a thin surface"); checks++;
            }
            call(pads, "clear");
            require(displays.stream().noneMatch(Entity::isValid), "Ending the match removes every pad glow"); checks++;
            smoke.getLogger().info("FINALS_OBJECTIVE_RECOVERY_PASS: " + checks);
            return checks;
        } finally {
            call(session, "removeCashbox"); call(pads, "clear");
            for (BlockState b : originals) b.update(true, false);
        }
    }
}
