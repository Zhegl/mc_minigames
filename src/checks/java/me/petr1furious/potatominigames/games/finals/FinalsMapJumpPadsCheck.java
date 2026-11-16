package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

public final class FinalsMapJumpPadsCheck {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (self, method, a) -> switch (method.getName()) {
                    case "equals" -> self == a[0]; case "hashCode" -> 1; case "getName" -> "pads"; default -> null;
                });
        Location start = new Location(world, .5, 65, .5), target = new Location(world, 65.5, 83, 25.5);
        var visible = FinalsMapJumpPads.class.getDeclaredMethod("glowVisible", FinalsMapJumpPads.Pad.class, Location.class);
        visible.setAccessible(true);
        var targeted = new FinalsMapJumpPads.Pad(start, target);
        check((boolean) visible.invoke(null, targeted, start.clone().add(16, 0, 0)), "Targeted glow is visible at 16 blocks");
        check(!(boolean) visible.invoke(null, targeted, start.clone().add(16.01, 0, 0)), "Targeted glow is hidden beyond 16 blocks");
        check(!(boolean) visible.invoke(null, targeted, start.clone().add(0, 16.01, 0)), "Glow range includes vertical separation");
        check(!(boolean) visible.invoke(null, new FinalsMapJumpPads.Pad(start, null), start), "Vertical pads never glow, even at zero distance");
        check(FinalsMapJumpPads.UP_SPEED == 3 * FinalsGadgets.JUMP_PAD_SPEED, "Upward pads use triple gadget vertical velocity");
        check(FinalsMapJumpPads.arc(start, target, 0).equals(start), "Arc begins at launch point");
        check(FinalsMapJumpPads.arc(start, target, 1).equals(target), "Arc ends at configured destination");
        check(FinalsMapJumpPads.arc(start, target, .5).getY() > target.getY(), "Targeted trajectory clears the higher roof");
        for (Location goal : List.of(target, target.clone().add(0, -50, 0), start.clone().add(0, 20, 0))) {
            int oldDuration = Math.max(8, (int) Math.ceil((start.distance(goal) + 4 * FinalsMapJumpPads.rise(start, goal)) / 1.5));
            check(Math.abs(FinalsMapJumpPads.duration(start, goal) - oldDuration / 1.5) < 1,
                    "Targeted flights take two thirds of their original duration, rounded to a tick");
            var pads = pads(start, goal);
            TestPlayer player = new TestPlayer(start);
            for (int tick = 0; tick < FinalsMapJumpPads.duration(start, goal) * 3; tick++) {
                pads.tick(List.of(player.player), p -> true, tick);
                check(player.velocity.length() <= 2.700001, "Guided flight respects the faster movement speed bound");
                player.at.add(player.velocity);
                if (player.at.distance(goal) < .5) break;
            }
            check(player.at.distance(goal) < .5, "Velocity-guided flight reaches higher, lower and vertical targets");
        }
        var pads = pads(start, target);
        var delayedPads = pads(start, target);
        TestPlayer delayed = new TestPlayer(start);
        Location actual = start.clone();
        Vector previous = null;
        for (int tick = 0; tick < 35; tick++) {
            if (tick % 3 == 0) {
                delayed.at.setX(actual.getX()); delayed.at.setY(actual.getY()); delayed.at.setZ(actual.getZ());
            }
            delayedPads.tick(List.of(delayed.player), p -> true, tick);
            if (previous != null) check(delayed.velocity.distance(previous) <= .250001,
                    "Delayed movement reports do not cause abrupt mid-flight velocity changes");
            actual.add(delayed.velocity);
            previous = delayed.velocity.clone();
        }
        TestPlayer blocked = new TestPlayer(start);
        for (int tick = 0; tick < 14; tick++) pads.tick(List.of(blocked.player), p -> true, tick);
        check(blocked.velocity.lengthSquared() == 0, "A blocked flight stops instead of repeatedly forcing movement");
        pads.clear();
        TestPlayer inactive = new TestPlayer(start);
        pads.tick(List.of(inactive.player), p -> false, 40);
        check(inactive.velocity.lengthSquared() == 0, "Inactive/downed players cannot use pads");
        var vertical = pads(start, null);
        TestPlayer first = new TestPlayer(start), second = new TestPlayer(start);
        vertical.tick(List.of(first.player, second.player), p -> true, 0);
        check(first.velocity.distance(new Vector(0, 3.15, 0)) < .000001 && first.velocity.equals(second.velocity),
                "Vertical pads launch all eligible players straight upward");
        first.velocity.zero();
        vertical.tick(List.of(first.player), p -> true, 1);
        check(first.velocity.lengthSquared() == 0, "Standing on a pad does not retrigger every tick");
        first.velocity = new Vector(.4, -.2, -.3);
        vertical.tick(List.of(first.player), p -> true, 25);
        check(first.velocity.distance(new Vector(.4, 3.15, -.3)) < .000001,
                "Upward launch preserves horizontal momentum for a smoother transition");
        System.out.println("FinalsMapJumpPadsCheck: " + checks + " checks passed");
    }

    private static FinalsMapJumpPads pads(Location start, Location target) {
        FinalsMapArena arena = (FinalsMapArena) Proxy.newProxyInstance(FinalsMapArena.class.getClassLoader(),
                new Class<?>[]{FinalsMapArena.class}, (self, method, args) ->
                        method.getName().equals("jumpPads") ? List.of(new FinalsMapJumpPads.Pad(start, target)) : null);
        return new FinalsMapJumpPads(arena);
    }
    private static final class TestPlayer {
        final UUID id = UUID.randomUUID();
        final Location at;
        Vector velocity = new Vector();
        final Player player;
        TestPlayer(Location start) {
            at = start.clone();
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (self, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id; case "getLocation" -> at.clone(); case "getWorld" -> at.getWorld();
                        case "getVelocity" -> velocity.clone();
                        case "setVelocity" -> { velocity = ((Vector) args[0]).clone(); yield null; }
                        default -> null;
                    });
        }
    }
}
