package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.lang.reflect.Proxy;
import java.util.*;

public final class AnvilsCheck {
    public static void main(String[] args) throws Exception {
        AnvilRound r = new AnvilRound(12);
        r.start(0);
        check(r.pvpEnabled(), "PvP is enabled from the first wave");
        check(!r.raining() && r.shelters() == 4, "Four shelters before first rain");
        r.advance(199);
        check(!r.raining(), "Full ten seconds to cross the larger arena");
        r.advance(200);
        check(r.raining(), "Rain begins");
        r.advance(320);
        check(
                !r.raining() && r.wave() == 2 && r.shelters() == 4,
                "Second wave keeps four shelters");
        check(r.remaining(320) == 9, "Second relocation is one second shorter");
        r.advance(500);
        check(r.pvpEnabled(), "PvP stays enabled on the second wave");
        r.advance(620);
        check(r.pvpEnabled(), "PvP stays enabled on later waves");
        for (int players : new int[] {1, 4, 12}) {
            var round = new AnvilRound(players);
            round.start(0);
            long tick = 0;
            for (int wave = 1; wave <= 10; wave++) {
                check(
                        round.shelters() == new int[] {4, 4, 3, 3, 2, 2, 1, 1, 1, 1}[wave - 1],
                        "Remove one shelter every two waves, independent of player count");
                int move = new int[] {10, 9, 8, 7, 6, 6, 6, 6, 6, 6}[wave - 1];
                check(
                        round.shelterSize() == (wave < 6 ? 2 : 1),
                        "Shelters shrink to one block from wave six");
                check(round.remaining(tick) == move, "Relocation shortens to a six-second floor");
                check(
                        !round.advance(tick + move * 20 - 1),
                        "Relocation lasts its full announced time");
                tick += move * 20;
                check(round.advance(tick) && round.raining(), "Wave begins at shortened deadline");
                tick += 120;
                round.advance(tick);
            }
        }
        platformGeometry();
        check(
                AnvilRain.batchSize(1) * 20 / AnvilRain.spawnInterval(1) >= 100,
                "Dense rain already from the first wave");
        check(AnvilRain.batchSize(10) > AnvilRain.batchSize(1), "Rain intensifies");
        var a = AnvilArena.layout(8, 2, new Random(1));
        var b = AnvilArena.layout(7, 2, new Random(2));
        check(!a.containsAll(b), "New shelter layout is not just removed old shelter");
        for (int seed = 0; seed < 50; seed++)
            for (int size : new int[] {1, 2})
                for (var s : AnvilArena.layout(14, size, new Random(seed))) {
                    check(
                            s.x() >= -21
                                    && s.x() + size <= 21
                                    && s.z() >= -21
                                    && s.z() + size <= 21,
                            "Shelters stay on floor");
                    check(
                            s.size() == size
                                    && s.bounds().getWidthX() == size
                                    && s.bounds().getWidthZ() == size,
                            "Roof geometry and collision shrink together");
                    check(
                            !Double.isNaN(
                                    AnvilRain.impact(s.x() + .5, s.z() + .5, 90, 80, s.bounds())),
                            "A one-block roof still intercepts rain");
                    if (size == 1)
                        check(
                                Double.isNaN(
                                        AnvilRain.impact(
                                                s.x() + 1.5, s.z() + .5, 90, 80, s.bounds())),
                                "Adjacent former roof cell is no longer sheltered");
                }
        BoundingBox slab = new BoundingBox(0, 83, 0, 1, 83.5, 1),
                body = new BoundingBox(.2, 81, .2, .8, 82.8, .8);
        check(
                AnvilRain.impact(.5, .5, 90, 80, slab) > AnvilRain.impact(.5, .5, 90, 80, body),
                "Slab intercepts before player");
        check(Double.isNaN(AnvilRain.impact(2, .5, 90, 80, slab)), "No imaginary radius shelter");
        check(
                AnvilRain.impact(.5, .5, 84, 81, body) > 0,
                "Swept collision cannot tunnel through body");
        localizedRain();
        System.out.println("Anvil waves passed");
    }

    private static void platformGeometry() {
        var fixture = new PartyArenaCheck.WorldMap();
        var arena = new AnvilArena(fixture.world);
        arena.build();
        int floor = 0;
        for (int x = -22; x <= 21; x++)
            for (int z = -22; z <= 21; z++) {
                var material = fixture.world.getBlockAt(x, 80, z).getType();
                if (x >= -21 && x <= 20 && z >= -21 && z <= 20) {
                    check(
                            material == org.bukkit.Material.SMOOTH_STONE,
                            "Entire 42 by 42 floor exists");
                    floor++;
                    check(
                            !Double.isNaN(
                                    AnvilRain.impact(
                                            x + .5,
                                            z + .5,
                                            84,
                                            79,
                                            arena.collisionBoxes().getLast())),
                            "Collision covers every floor cell");
                } else
                    for (int y = 80; y <= 84; y++)
                        check(
                                fixture.world.getBlockAt(x, y, z).getType()
                                        == org.bukkit.Material.BARRIER,
                                "Continuous outer boundary");
            }
        check(floor == 1764, "Four times the previous 441-block area");
        for (int i = 0; i < 12; i++) {
            var spawn = arena.spawn(i, 12);
            check(
                    fixture.world.getBlockAt(spawn.getBlockX(), 80, spawn.getBlockZ()).getType()
                            == org.bukkit.Material.SMOOTH_STONE,
                    "Spawn over floor");
        }
    }

    private static void localizedRain() throws Exception {
        for (int seed = 0; seed < 8; seed++) {
            RainWorld fixture = new RainWorld();
            Location[] position = {new Location(fixture.world, -9.5, 1000, .5)};
            Player player = target(position, org.bukkit.GameMode.SURVIVAL);
            Player overlapping = target(position, org.bukkit.GameMode.SURVIVAL);
            Location[] secondPosition = {new Location(fixture.world, .5, 1000, -9.5)};
            Player second = target(secondPosition, org.bukkit.GameMode.SURVIVAL);
            Player spectator =
                    target(
                            new Location[] {new Location(fixture.world, 15.5, 1000, 15.5)},
                            org.bukkit.GameMode.SPECTATOR);
            var rain =
                    new AnvilRain(
                            fixture.world,
                            new AnvilArena(fixture.world),
                            new Random(seed),
                            p -> {
                                throw new AssertionError("Players above rain cannot be hit");
                            });
            var sound = AnvilRain.class.getDeclaredField("nextImpactSound");
            sound.setAccessible(true);
            sound.setLong(rain, Long.MAX_VALUE);
            for (int section = 0; section < 3; section++) {
                fixture.impacts.clear();
                fixture.landed = 0;
                fixture.spawned = 0;
                fixture.spawnCells.clear();
                // Four complete sweeps at 72 candidates per call; skip only server-backed audio.
                // Moving the same target without clearing rain must move the dense zone as well.
                position[0].setX(section == 0 ? -9.5 : 9.5);
                List<Player> targets =
                        section == 2
                                ? List.of(spectator)
                                : section == 0
                                        ? List.of(player, overlapping, second, spectator)
                                        : List.of(player, overlapping, spectator);
                for (int step = 0; step < 98; step++) {
                    int tick = step + 1 + step / 19;
                    rain.tick(tick, 6, targets);
                }
                for (int x = -21; x <= 20; x++)
                    for (int z = -21; z <= 20; z++) {
                        double dx = x + .5 - position[0].getX(), dz = z + .5 - position[0].getZ();
                        double dx2 = x + .5 - secondPosition[0].getX(),
                                dz2 = z + .5 - secondPosition[0].getZ();
                        int expected =
                                section < 2
                                                && (dx * dx + dz * dz <= 64
                                                        || section == 0
                                                                && dx2 * dx2 + dz2 * dz2 <= 64)
                                        ? 4
                                        : 1;
                        check(
                                fixture.spawnCells.getOrDefault(x + ":" + z, 0) == expected,
                                "Full density within horizontal radius eight, quarter outside;"
                                        + " seed="
                                        + seed
                                        + " cell="
                                        + x
                                        + ":"
                                        + z);
                    }
                check(fixture.landed > 0, "Rain reaches the floor");
                check(rain.activeCount() <= 2560, "Airborne display count remains bounded");
            }
            rain.clear();
            check(rain.activeCount() == 0, "No anvils survive into relocation");
            fixture.impacts.clear();
            for (int step = 0; step < 95; step++)
                rain.tick(step + 1 + step / 19, 1, List.of(player));
            for (int x = -21; x <= 20; x++)
                for (int z = -21; z <= 20; z++) {
                    double dx = x + .5 - position[0].getX(), dz = z + .5 - position[0].getZ();
                    if (dx * dx + dz * dz <= 64)
                        check(
                                fixture.impacts.contains(x + ":" + z),
                                "Every nearby cell is hit even in a short first wave");
                }
            rain.clear();
        }
    }

    private static Player target(Location[] position, org.bukkit.GameMode mode) {
        return (Player)
                Proxy.newProxyInstance(
                        Player.class.getClassLoader(),
                        new Class[] {Player.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getGameMode" -> mode;
                                    case "getWorld" -> position[0].getWorld();
                                    case "getBoundingBox" ->
                                            new BoundingBox(
                                                    position[0].getX() - .3,
                                                    position[0].getY(),
                                                    position[0].getZ() - .3,
                                                    position[0].getX() + .3,
                                                    position[0].getY() + 1.8,
                                                    position[0].getZ() + .3);
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default ->
                                            throw new AssertionError(
                                                    "Unexpected target call: " + method.getName());
                                });
    }

    private static final class RainWorld {
        final Set<String> impacts = new HashSet<>();
        final Map<String, Integer> spawnCells = new HashMap<>();
        int spawned, landed;
        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class[] {World.class},
                                (proxy, m, a) ->
                                        switch (m.getName()) {
                                            // Display appearance initialization is outside this
                                            // physics/coverage fixture.
                                            case "spawn" -> {
                                                spawned++;
                                                Location at = (Location) a[0];
                                                spawnCells.merge(
                                                        at.getBlockX() + ":" + at.getBlockZ(),
                                                        1,
                                                        Integer::sum);
                                                yield display(((Location) a[0]).clone());
                                            }
                                            case "playSound" -> null;
                                            case "equals" -> proxy == a[0];
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected world call: "
                                                                    + m.getName());
                                        });

        BlockDisplay display(Location initial) {
            Location[] position = {initial};
            boolean[] removed = {false};
            return (BlockDisplay)
                    Proxy.newProxyInstance(
                            BlockDisplay.class.getClassLoader(),
                            new Class[] {BlockDisplay.class},
                            (proxy, m, a) ->
                                    switch (m.getName()) {
                                        case "teleport" -> {
                                            position[0] = ((Location) a[0]).clone();
                                            yield true;
                                        }
                                        case "remove" -> {
                                            check(!removed[0], "Display removed once");
                                            removed[0] = true;
                                            if (position[0].getY() < 83) {
                                                landed++;
                                                int x = position[0].getBlockX(),
                                                        z = position[0].getBlockZ();
                                                check(
                                                        x >= -21 && x <= 20 && z >= -21 && z <= 20,
                                                        "Impact stays inside platform");
                                                impacts.add(x + ":" + z);
                                            }
                                            yield null;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected display call: " + m.getName());
                                    });
        }
    }
}
