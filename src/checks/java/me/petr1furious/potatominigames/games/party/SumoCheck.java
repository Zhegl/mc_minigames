package me.petr1furious.potatominigames.games.party;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/** Exercise the production platform, overtime, knockback and hit-window rules without a server. */
public final class SumoCheck {
    public static void main(String[] args) {
        platformChecks();
        combatChecks();
        System.out.println("Party Sumo: original 19-block platform, safe spawns, ring-outs, complete overtime/reset, stronger Knockback I impulses and bounded hit windows passed");
    }

    private static void platformChecks() {
        Map<PvpArena.Position, Material> blocks = new HashMap<>();
        World[] world = new World[1];
        world[0] = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getBlockAt" -> block(world[0], (int) args[0], (int) args[1], (int) args[2], blocks);
                case "setSpawnLocation" -> true;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "Sumo check world";
                default -> throw new AssertionError("Unexpected world operation: " + method.getName());
            };
        });
        SumoArena arena = new SumoArena(world[0]);
        arena.build();
        Map<PvpArena.Position, Material> initial = new HashMap<>(blocks);
        check(initial.size() == 253, "Original radius-nine platform has exactly 253 floor blocks");
        for (var entry : initial.entrySet()) {
            var p = entry.getKey();
            check(p.y() == SumoArena.FLOOR, "Only one floor layer, with no hidden floor or walls");
            check(SumoArena.onPlatform(p.x(), p.z(), SumoArena.RADIUS), "Platform stays circular");
            check(initial.get(new PvpArena.Position(-p.x(), p.y(), -p.z())) == entry.getValue(), "Symmetric playing surface");
        }
        check(!blocks.containsKey(new PvpArena.Position(SumoArena.RADIUS, SumoArena.FLOOR, SumoArena.RADIUS)), "No square corner ledges");
        for (boolean east : new boolean[] {false, true}) {
            Location spawn = arena.spawn(east);
            check(initial.containsKey(new PvpArena.Position(spawn.getBlockX(), SumoArena.FLOOR, spawn.getBlockZ())), "Spawn above solid platform");
            check(!arena.isOut(spawn), "Spawn not a ring-out");
            check(spawn.toVector().subtract(arena.center().toVector()).length() <= SumoArena.RADIUS - 5,
                    "Both original spawns retain five blocks of radial recovery space");
        }
        check(!arena.isOut(new Location(world[0], SumoArena.RADIUS + 2, 81, 0)), "Running off edge is not a loss until falling below platform");
        check(arena.isOut(new Location(world[0], SumoArena.RADIUS + 2, 79.4, 0)), "Falling below platform is a loss");
        check(!arena.canPlace(block(world[0], 0, 81, 0, blocks)), "Sumo disallows building");
        check(!arena.isPlaced(block(world[0], 0, 80, 0, blocks)), "Platform blocks never breakable");
        arena.tick(44, 45);
        check(blocks.equals(initial), "No shrinking before overtime");
        int previous = SumoArena.RADIUS;
        for (int elapsed = 45; elapsed <= 45 + SumoArena.OVERTIME_SECONDS; elapsed++) {
            int radius = SumoArena.radiusAt(elapsed, 45);
            check(radius <= previous && radius >= -1, "Overtime only shrinks");
            arena.tick(elapsed, 45);
            for (var p : initial.keySet()) {
                check((blocks.get(p) != Material.AIR) == SumoArena.onPlatform(p.x(), p.z(), radius), "Only current-radius floor remains");
            }
            previous = radius;
        }
        check(SumoArena.radiusAt(45 + SumoArena.OVERTIME_SECONDS - 3, 45) == -1,
                "Original platform vanishes with three seconds left for players to fall before timeout");
        check(blocks.values().stream().allMatch(m -> m == Material.AIR), "Overtime ultimately removes all camping space");
        arena.reset();
        check(blocks.equals(initial), "Every round restores the entire original platform and its colours");
        arena.tick(1000, 45); arena.reset();
        check(blocks.equals(initial), "Repeated shrink/reset does not leave holes");
    }

    private static void combatChecks() {
        Vector away = new Vector(2, 9, 0), current = new Vector(0, 0, 0);
        Vector normal = SumoCombat.knockback(away, current, false, 1);
        Vector sprint = SumoCombat.knockback(away, current, true, 1);
        check(normal.getX() > 0 && normal.getZ() == 0 && normal.getY() > 0, "Stick pushes away with a low upward hop");
        check(sprint.getX() > normal.getX(), "Sprint has stronger knockback");
        Vector bare = SumoCombat.knockback(away, current, false, 0);
        check(normal.getX() > bare.getX(), "Knockback I actually increases the custom impulse over bare hands");
        check(SumoCombat.knockback(away, current, false, 20).equals(normal), "Unexpected higher enchants cannot bypass the balance cap");
        check(SumoCombat.knockback(away, current, false, -1).equals(bare), "Negative levels cannot reverse or amplify knockback");
        check(away.equals(new Vector(2, 9, 0)) && current.equals(new Vector()), "Combat calculations do not mutate input vectors");
        Vector mirrored = SumoCombat.knockback(new Vector(-2, 0, 0), current, false, 1);
        check(mirrored.getX() == -normal.getX() && mirrored.getY() == normal.getY(), "Spawn sides have equal knockback");
        Vector fast = SumoCombat.knockback(away, new Vector(100, 100, 100), true, 1);
        check(fast.clone().setY(0).length() <= .950001 && fast.getY() <= .36, "Repeated hits cannot cause runaway velocity");
        Vector overlapping = SumoCombat.knockback(new Vector(), current, false, 1);
        check(Double.isFinite(overlapping.getX()) && Double.isFinite(overlapping.getZ()), "Overlapping players cannot produce NaN velocity");
        SumoCombat combat = new SumoCombat();
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        check(combat.acceptHit(a, 100), "First hit accepted");
        check(!combat.acceptHit(a, 100) && !combat.acceptHit(a, 109), "Hit spam within hurt window ignored");
        check(combat.acceptHit(b, 100), "Opponent has independent hit window");
        check(combat.acceptHit(a, 110), "Can hit again when hurt window expires");
        combat.reset();
        check(combat.acceptHit(a, 111), "Next round clears old hit immunity");
        combat.reset();
        check(combat.acceptHit(a, Integer.MAX_VALUE - 5) && combat.acceptHit(a, Integer.MIN_VALUE + 5), "Hit cooldown survives server tick counter wrap");
    }

    private static Block block(World world, int x, int y, int z, Map<PvpArena.Position, Material> blocks) {
        var pos = new PvpArena.Position(x, y, z);
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getWorld" -> world;
                case "getX" -> x;
                case "getY" -> y;
                case "getZ" -> z;
                case "getType" -> blocks.getOrDefault(pos, Material.AIR);
                case "setType" -> { blocks.put(pos, (Material) args[0]); yield null; }
                default -> throw new AssertionError("Unexpected block operation: " + method.getName());
            };
        });
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
