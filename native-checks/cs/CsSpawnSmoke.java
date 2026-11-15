import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Slab;

import java.lang.reflect.*;
import java.util.*;

/** Builds the actual imported map and exercises both production spawn selectors on Paper. */
final class CsSpawnSmoke {
    private static int assertions;

    private record Spot(int x, int y, int z) {}

    private static final Set<Spot> CT_UNDERPASS =
            Set.of(
                    new Spot(102, 75, 60),
                    new Spot(103, 75, 62),
                    new Spot(103, 75, 64),
                    new Spot(103, 75, 66),
                    new Spot(102, 75, 68));

    static int run(ClassLoader loader, World world) throws Exception {
        assertions = 0;
        Class<?> type =
                Class.forName("me.petr1furious.potatominigames.games.cs.CsArena", true, loader);
        Constructor<?> constructor = type.getDeclaredConstructor(World.class);
        constructor.setAccessible(true);
        Object arena = constructor.newInstance(world);
        Method build = method(type, "buildSlice", int.class);
        while (!(boolean) build.invoke(arena, 4096)) {}

        Method spawn = method(type, "spawn", int.class, int.class);
        Method inside = method(type, "inside", Location.class);
        Method shuffle = method(type, "shuffleSpawns", Random.class);
        Random random = new Random(1432);
        List<Location> opponents = new ArrayList<>();
        for (int round = 0; round < 4; round++) {
            shuffle.invoke(arena, random);
            for (int side = 0; side < 2; side++) {
                Set<Location> positions = new HashSet<>();
                for (int slot = 0; slot < (side == 0 ? 15 : 5); slot++) {
                    Location at = (Location) spawn.invoke(arena, side, slot);
                    safe(at);
                    require((boolean) inside.invoke(arena, at), "Classic start is inside the map");
                    require(
                            at.getY() == (side == 0 ? 82 : 75.5),
                            "Classic uses the T street and covered CT start, not Short above it");
                    if (side == 1)
                        require(
                                world.getBlockAt(at.getBlockX(), 80, at.getBlockZ())
                                        .getType()
                                        .isSolid(),
                                "CT starts below the native underpass roof");
                    require(positions.add(at), "Shuffled classic slots do not overlap");
                    opponents.add(at);
                }
            }
        }
        Location first = (Location) spawn.invoke(arena, 0, 0);
        var previous = first.getBlock().getBlockData();
        try {
            first.getBlock().setType(Material.STONE, false);
            Location fallback = (Location) spawn.invoke(arena, 0, 0);
            safe(fallback);
            require(!fallback.equals(first), "Classic skips a newly obstructed slot");
        } finally {
            first.getBlock().setBlockData(previous, false);
        }

        List<?> candidates = (List<?>) method(type, "deathmatchSpawns").invoke(null);
        require(candidates.size() >= 24, "Deathmatch keeps distributed surface candidates");
        List<Location> locations = new ArrayList<>();
        for (Object point : candidates) {
            Class<?> record = point.getClass();
            Location at =
                    new Location(
                            world,
                            (double) method(record, "x").invoke(point),
                            (double) method(record, "y").invoke(point),
                            (double) method(record, "z").invoke(point));
            safe(at);
            require((boolean) inside.invoke(arena, at), "Deathmatch candidate is inside the map");
            locations.add(at);
        }
        Method deathmatch = method(type, "deathmatchSpawn", Collection.class, Random.class);
        for (int life = 0; life < 20; life++) {
            Location at = (Location) deathmatch.invoke(arena, opponents, random);
            safe(at);
            double distance = nearest(at, opponents);
            for (Location candidate : locations)
                require(
                        distance + .00001 >= nearest(candidate, opponents),
                        "Deathmatch chooses the safest eligible surface by opponent distance");
            opponents.add(at);
        }
        Location selected = (Location) deathmatch.invoke(arena, List.of(), new Random(775));
        previous = selected.getBlock().getBlockData();
        try {
            selected.getBlock().setType(Material.STONE, false);
            Location fallback = (Location) deathmatch.invoke(arena, List.of(), new Random(775));
            safe(fallback);
            require(
                    fallback.getBlockX() != selected.getBlockX()
                            || fallback.getBlockZ() != selected.getBlockZ(),
                    "Deathmatch skips an obstructed candidate instead of ending the match");
        } finally {
            selected.getBlock().setBlockData(previous, false);
        }
        wingman(loader, world, type);
        return assertions;
    }

    private static void wingman(ClassLoader loader, World world, Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor(World.class, boolean.class);
        constructor.setAccessible(true);
        Object arena = constructor.newInstance(world, true);
        Class<?> layout =
                Class.forName("me.petr1furious.potatominigames.games.cs.CsWingman", true, loader);
        Set<?> borders = (Set<?>) method(layout, "borders").invoke(null);
        Map<Location, org.bukkit.block.data.BlockData> original = new LinkedHashMap<>();
        for (Object p : borders) {
            Class<?> pos = p.getClass();
            Location at =
                    new Location(
                            world,
                            (int) method(pos, "x").invoke(p),
                            (int) method(pos, "y").invoke(p),
                            (int) method(pos, "z").invoke(p));
            original.put(at, at.getBlock().getBlockData().clone());
        }
        try {
            Method build = method(type, "buildSlice", int.class);
            while (!(boolean) build.invoke(arena, 4096)) {}
            Method spawn = method(type, "spawn", int.class, int.class);
            Method inside = method(type, "inside", Location.class);
            for (int side = 0; side < 2; side++) {
                Set<Location> distinct = new HashSet<>();
                for (int slot = 0; slot < (side == 0 ? 15 : 5); slot++) {
                    Location at = (Location) spawn.invoke(arena, side, slot);
                    safe(at);
                    require(distinct.add(at), "Wingman preserves every distinct Classic spawn");
                    for (double dx : new double[] {-.3, .3})
                        for (double dz : new double[] {-.3, .3})
                            require(
                                    (boolean) inside.invoke(arena, at.clone().add(dx, 0, dz)),
                                    "Every possible Wingman start, including its body, is inside");
                }
            }
            for (Location at :
                    List.of(
                            new Location(world, 0, 82, 18),
                            new Location(world, 73, 75, 37),
                            new Location(world, 93, 75, 57))) {
                require(
                        at.getBlock().getType() == Material.RED_STAINED_GLASS,
                        "Open forbidden passages are sealed by visible native collision blocks");
                method(type, "breakGlass", org.bukkit.block.Block.class)
                        .invoke(arena, at.getBlock());
                method(type, "reset").invoke(arena);
                require(
                        at.getBlock().getType() == Material.RED_STAINED_GLASS,
                        "Gunfire, explosions and round resets cannot remove Wingman gates");
                require(!(boolean) inside.invoke(arena, at), "A sealed passage is outside play");
            }
            Location shortAt = new Location(world, 95.5, 82, 57.5);
            require(
                    (boolean) inside.invoke(arena, shortAt)
                            && shortAt.getBlock().isPassable()
                            && shortAt.clone().add(0, 1, 0).getBlock().isPassable(),
                    "The native upper Short passage stays open above the CT exit gate");
            Method site = method(type, "site", Location.class);
            require(
                    (int) site.invoke(arena, new Location(world, 103, 82, 88)) == 0
                            && (int) site.invoke(arena, new Location(world, 114, 79, 6)) == -1,
                    "Wingman accepts A and rejects B for bomb planting");
        } finally {
            // Later CS fixture groups continue on the unrestricted Classic map.
            original.forEach((at, data) -> at.getBlock().setBlockData(data, false));
        }
    }

    private static double nearest(Location at, Collection<Location> opponents) {
        double result = Double.POSITIVE_INFINITY;
        for (Location opponent : opponents) {
            double dx = at.getX() - opponent.getX(), dz = at.getZ() - opponent.getZ();
            result = Math.min(result, dx * dx + dz * dz);
        }
        return result;
    }

    private static void safe(Location at) throws Exception {
        require(
                at.getY() >= 79
                        || CT_UNDERPASS.contains(
                                new Spot(at.getBlockX(), at.getBlockY(), at.getBlockZ())),
                "Only the verified CT underpass may spawn below the foundation cutoff");
        require(
                (at.getBlock().isPassable()
                                || (at.getY() - at.getBlockY() == .5
                                        && at.getBlock().getBlockData() instanceof Slab slab
                                        && slab.getType() == Slab.Type.BOTTOM))
                        && at.clone().add(0, 1, 0).getBlock().isPassable()
                        && at.clone().add(0, 1.79, 0).getBlock().isPassable(),
                "Native feet and head collision checks accept the respawn");
        require(
                !at.clone().subtract(0, .05, 0).getBlock().isPassable(),
                "Native floor check finds support, including slabs and stairs");
        Object level = at.getWorld().getClass().getMethod("getHandle").invoke(at.getWorld());
        Class<?> boxType = Class.forName("net.minecraft.world.phys.AABB");
        Object body = boxType.getConstructor(double.class, double.class, double.class,
                        double.class, double.class, double.class)
                .newInstance(at.getX() - .3, at.getY() + .00001, at.getZ() - .3,
                        at.getX() + .3, at.getY() + 1.8, at.getZ() + .3);
        require((boolean) level.getClass().getMethod("noCollision", boxType).invoke(level, body),
                "Full player-sized body clears native block collision shapes");
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
