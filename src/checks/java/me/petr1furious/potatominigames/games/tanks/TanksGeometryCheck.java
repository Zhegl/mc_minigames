package me.petr1furious.potatominigames.games.tanks;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Runs the real arena builder and footprint tests without starting a server. */
public final class TanksGeometryCheck {
    public static void main(String[] args) {
        WorldMap map = new WorldMap();
        TanksArena arena = new TanksArena(map.world);
        check(arena.progress() == 0, "starts unbuilt");
        int slices = 0;
        boolean complete;
        do {
            map.writes = 0;
            complete = arena.buildSlice(321);
            check(map.writes <= 321, "slice never exceeds the supplied block budget");
            check(++slices < 2000, "bounded builder makes progress");
        } while (!complete);
        check(arena.progress() == 1, "completed progress");
        check(arena.buildSlice(1), "completion is idempotent");
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 6; i++) {
                Location spawn = arena.spawn(side, i);
                check(arena.canDrive(spawn), "all twelve full hull spawns are clear");
                check(spawn.getY() == 65, "ground and hull convention");
            }
            check(arena.canDrive(arena.base(side)), "base is driveable");
        }
        for (int i = 0; i < 6; i++) {
            Location red = arena.spawn(0, i), blue = arena.spawn(1, i);
            check(
                    red.getX() == -blue.getX() && red.getZ() == -blue.getZ(),
                    "matched spawn slots have rotationally identical cover access");
        }
        for (int red = 0; red < 6; red++)
            for (int blue = 0; blue < 6; blue++) {
                Location from = arena.spawn(0, red), to = arena.spawn(1, blue);
                boolean blocked = false;
                for (int step = 1; step < 200; step++) {
                    double t = step / 200.0;
                    int x = (int) Math.floor(from.getX() + (to.getX() - from.getX()) * t);
                    int z = (int) Math.floor(from.getZ() + (to.getZ() - from.getZ()) * t);
                    if (map.block(x, 66, z).getType() != Material.AIR) {
                        blocked = true;
                        break;
                    }
                }
                check(
                        blocked,
                        "spawn pair " + red + "/" + blue + " has a cannon-height sightline break");
            }
        ArrayDeque<Point> frontier = new ArrayDeque<>();
        HashSet<Point> visited = new HashSet<>(), reachable = new HashSet<>();
        frontier.add(new Point(0, -37));
        while (!frontier.isEmpty()) {
            Point point = frontier.removeFirst();
            if (!visited.add(point)
                    || Math.abs(point.x) > 56
                    || Math.abs(point.z) > 43
                    || !arena.canDrive(new Location(map.world, point.x, 65, point.z))) continue;
            reachable.add(point);
            frontier.add(new Point(point.x + 2, point.z));
            frontier.add(new Point(point.x - 2, point.z));
            frontier.add(new Point(point.x, point.z + 2));
            frontier.add(new Point(point.x, point.z - 2));
        }
        check(reachable.contains(new Point(0, 37)), "tank-width routes connect both capture bases");
        for (int side = 0; side < 2; side++)
            for (int i = 0; i < 6; i++) {
                Location spawn = arena.spawn(side, i);
                check(
                        reachable.contains(new Point(spawn.getBlockX(), spawn.getBlockZ())),
                        "every spawn can join the battle");
            }
        check(
                !arena.canDrive(new Location(map.world, 63, 65, 0)),
                "whole hull stays inside border");
        check(!arena.canDrive(new Location(map.world, 0, 66, -37)), "reject vertical escapes");
        check(
                !arena.canDrive(new Location(map.world, 0, Double.NaN, -37)),
                "reject invalid vertical coordinates");
        check(
                !arena.canDrive(new Location(map.world, Double.MAX_VALUE, 65, 0)),
                "reject huge coordinates without iterating");
        Location open = new Location(map.world, 0, 65, -37);
        map.set(1, 67, -37, Material.STONE);
        check(!arena.canDrive(open), "headroom is checked across the entire footprint");
        map.set(1, 67, -37, Material.AIR);
        map.set(1, 65, -35, Material.STONE);
        check(!arena.canDrive(open), "corner clipping is blocked");
        map.set(1, 65, -35, Material.AIR);
        map.set(0, 64, -37, Material.AIR);
        check(!arena.canDrive(open), "hull cannot bridge missing support");
        map.set(0, 64, -37, Material.STONE);
        check(arena.canDrive(open), "cleared space restores movement");
        Location corridor = new Location(map.world, .5, 65, -37.5);
        for (int z = -41; z <= -34; z++) map.set(2, 65, z, Material.STONE);
        check(
                arena.canDrive(corridor, TanksRules.Chassis.LIGHT),
                "light tank fits a three-block corridor");
        check(
                !arena.canDrive(corridor, TanksRules.Chassis.HEAVY),
                "heavy tank is blocked by the same corridor");
        for (int z = -41; z <= -34; z++) map.set(2, 65, z, Material.AIR);
        map.set(0, 67, -37, Material.STONE);
        check(
                arena.canDrive(open, TanksRules.Chassis.LIGHT),
                "low light silhouette fits under a high overhang");
        check(
                !arena.canDrive(open, TanksRules.Chassis.HEAVY),
                "heavy turret requires more headroom");
        map.set(0, 67, -37, Material.AIR);
        check(TanksArena.footprintOverlaps(0, 0, 0, 1, 2), "forward corner overlaps");
        check(!TanksArena.footprintOverlaps(0, 0, 0, 2, 0), "narrow side excludes nearby cells");
        check(TanksArena.footprintOverlaps(0, 0, 90, 2, 0), "turning rotates the full footprint");
        check(!TanksArena.footprintOverlaps(0, 0, 90, 0, 2), "rotated side excludes nearby cells");
        Location muzzle =
                TankVisual.muzzleAt(
                        new Location(map.world, 0, 65, 0), 0, 0, TanksRules.Chassis.MEDIUM);
        check(
                muzzle.getZ() > 3 && Math.abs(muzzle.getX()) < 1e-6,
                "yaw zero cannon faces +Z beyond hull");
        Location west = TankVisual.muzzleAt(open, 90, -10, TanksRules.Chassis.MEDIUM);
        check(
                west.getX() < -3 && west.getY() > muzzle.getY(),
                "Minecraft yaw and negative pitch point west/up");
        check(
                TankVisual.muzzleAt(open, 0, 0, TanksRules.Chassis.HEAVY).getZ()
                        > TankVisual.muzzleAt(open, 0, 0, TanksRules.Chassis.LIGHT).getZ(),
                "class silhouettes have distinct barrel reach");
        System.out.println("Tanks geometry checks passed");
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Point(int x, int z) {}

    static final class WorldMap {
        final Map<String, Material> blocks = new HashMap<>();
        int writes;
        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getBlockAt" ->
                                                    block(
                                                            (int) args[0],
                                                            (int) args[1],
                                                            (int) args[2]);
                                            case "spawn" ->
                                                    null; // Geometry check: display callbacks
                                            // belong to native rendering checks.
                                            case "equals" -> proxy == args[0];
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "getUID" -> new java.util.UUID(0, 42);
                                            default ->
                                                    throw new AssertionError(
                                                            "World: " + method.getName());
                                        });

        void set(int x, int y, int z, Material material) {
            blocks.put(x + ":" + y + ":" + z, material);
        }

        Block block(int x, int y, int z) {
            String key = x + ":" + y + ":" + z;
            return (Block)
                    Proxy.newProxyInstance(
                            Block.class.getClassLoader(),
                            new Class<?>[] {Block.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "setType" -> {
                                            writes++;
                                            blocks.put(key, (Material) args[0]);
                                            yield null;
                                        }
                                        case "getType" -> blocks.getOrDefault(key, Material.AIR);
                                        case "isPassable" ->
                                                blocks.getOrDefault(key, Material.AIR)
                                                        == Material.AIR;
                                        case "isSolid" ->
                                                blocks.getOrDefault(key, Material.AIR)
                                                        != Material.AIR;
                                        default ->
                                                throw new AssertionError(
                                                        "Block: " + method.getName());
                                    });
        }
    }
}
