package me.petr1furious.potatominigames.games.party;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Fair geometry, bounded rollback, sequential checkpoint timing and real menu/factory integration.
 */
public final class ParkourRaceCheck {
    public static void main(String[] args) throws ReflectiveOperationException {
        model();
        sampling();
        neighbouringTasks();
        waterEntryIsolation();
        chestDrop();
        expandedTasks();
        vehicleIsolation();
        navigation();
        geometry();
        swimRoute();
        honeyCatch();
        iceRoute();
        staircase();
        flightBoundary();
        rollback();
        damage();
        integration();
        System.out.println(
                "Parkour Sprint: shared course, sealed visible lanes, finite vanilla kits,"
                        + " sequential checkpoints, timed ranking and attempt rollback passed");
    }

    private static void model() {
        for (int stages : List.of(7, 8)) model(stages);
    }

    private static void model(int stages) {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        ParkourRace race = new ParkourRace(List.of(a, b, c), stages);
        check(
                race.result(Set.of(a, b, c)).places().equals(List.of(List.of(a, b, c))),
                "Starting racers tie");
        check(
                !race.reach(a, 2, 0) && !race.reach(new UUID(9, 9), 1, 0),
                "Cannot skip checkpoints or join a running race");
        race.position(a, 10);
        check(
                race.result(Set.of(a, b)).place(a) == 1 && race.result(Set.of(a, b)).place(b) == 2,
                "Live distance ranks racers within a section");
        race.reset(a);
        check(
                race.result(Set.of(a, b)).places().equals(List.of(List.of(a, b))),
                "Reset discards progress after the last checkpoint");
        check(race.reach(a, 1, 50) && !race.reach(a, 1, 51), "Checkpoint activates once");
        race.position(a, 8);
        race.reset(a);
        check(
                race.checkpoint(a) == 1 && race.result(Set.of(a, b)).place(a) == 1,
                "Reset preserves the reached checkpoint");
        for (int checkpoint = 2; checkpoint <= stages; checkpoint++)
            check(race.reach(a, checkpoint, checkpoint * 100), "Sequential progress");
        check(
                race.finished(a) && race.time(a) == stages * 100L && !race.reset(a),
                "Finish time is elapsed race time, never a reset section timer");
        for (int checkpoint = 1; checkpoint <= stages; checkpoint++)
            race.reach(b, checkpoint, checkpoint * 100);
        check(
                race.result(Set.of(a, b, c)).places().equals(List.of(List.of(a, b), List.of(c))),
                "Equal finish ticks share positions");
        check(
                race.result(Set.of(b, c)).awards().get(b) == 3,
                "Withdrawn players cannot occupy podium places");
        race.close();
        check(!race.reach(c, 1, 700) && !race.reset(c), "No progress or reset after cleanup");
    }

    private static ParkourCourse course(ParkourCourse.Kind... kinds) {
        return layout(List.of(kinds), false);
    }

    private static ParkourCourse layout(List<ParkourCourse.Kind> selected, boolean mirror) {
        // Keep focused fixture sections at their original indices; append neutral tasks.
        var full = new java.util.ArrayList<>(selected);
        for (var kind :
                List.of(
                        ParkourCourse.Kind.PEARL,
                        ParkourCourse.Kind.BRIDGE,
                        ParkourCourse.Kind.BOAT_ICE,
                        ParkourCourse.Kind.BOAT_WATER,
                        ParkourCourse.Kind.WATER_ELEVATOR,
                        ParkourCourse.Kind.WIND_HOPS,
                        ParkourCourse.Kind.LOW_CEILING,
                        ParkourCourse.Kind.WIND_WALL,
                        ParkourCourse.Kind.SWIM))
            if (full.size() < ParkourCourse.MIN_LENGTH && !full.contains(kind)) full.add(kind);
        return new ParkourCourse(full, mirror);
    }

    private static ParkourCourse buildingCourse() {
        return course(
                ParkourCourse.Kind.BRIDGE,
                ParkourCourse.Kind.WIND_WALL,
                ParkourCourse.Kind.WATER_DROP,
                ParkourCourse.Kind.SCAFFOLD,
                ParkourCourse.Kind.LADDER_CLIMB,
                ParkourCourse.Kind.LADDER_TRAVERSE);
    }

    private static void sampling() {
        Set<ParkourCourse.Kind> seen = new java.util.HashSet<>();
        Set<List<ParkourCourse.Kind>> orders = new java.util.HashSet<>();
        Set<Integer> lengths = new java.util.HashSet<>();
        for (int seed = 0; seed < 256; seed++) {
            ParkourCourse sampled = new ParkourCourse(new Random(seed * 0x9E3779B97F4A7C15L));
            check(
                    sampled.stages() >= 7
                            && sampled.stages() <= 8
                            && sampled.sequence().size() == sampled.stages()
                            && new java.util.HashSet<>(sampled.sequence()).size()
                                    == sampled.stages(),
                    "Each race samples seven or eight tasks without replacement");
            var families =
                    new java.util.EnumMap<ParkourCourse.Family, Integer>(
                            ParkourCourse.Family.class);
            for (var kind : sampled.sequence()) families.merge(kind.family(), 1, Integer::sum);
            check(
                    families.size() >= 4 && families.values().stream().allMatch(n -> n <= 2),
                    "random races combine at least four movement families without overloading one"
                            + " mechanic");
            lengths.add(sampled.stages());
            seen.addAll(sampled.sequence());
            orders.add(sampled.sequence());
            check(
                    sampled.blocks()
                                    .equals(
                                            new ParkourCourse(
                                                            new Random(seed * 0x9E3779B97F4A7C15L))
                                                    .blocks())
                            && sampled.states()
                                    .equals(
                                            new ParkourCourse(
                                                            new Random(seed * 0x9E3779B97F4A7C15L))
                                                    .states()),
                    "All lanes reproduce the same selected order and geometry");
            for (var pos : sampled.blocks().keySet())
                check(
                        pos.y() >= ParkourCourse.BUILD_BOTTOM && pos.y() < ParkourCourse.TOP - 8,
                        "Random vertical profiles fit inside the sealed arena");
            for (int checkpoint = 0; checkpoint <= sampled.stages(); checkpoint++) {
                var island = sampled.island(checkpoint);
                if (checkpoint < sampled.stages()) {
                    var next = sampled.island(checkpoint + 1);
                    check(
                            next.z() > island.z() && next.centerX() == island.centerX(),
                            "Every sampled task continues straight ahead without a sideways turn");
                }
                int x = (int) island.centerX();
                check(
                        sampled.blocks().get(new ParkourCourse.Pos(x, island.y(), island.z() + 3))
                                != null,
                        "Every randomized checkpoint has a permanent deck");
                check(
                        !sampled.blocks()
                                        .containsKey(
                                                new ParkourCourse.Pos(
                                                        x, island.y() + 1, island.z() + 3))
                                && !sampled.blocks()
                                        .containsKey(
                                                new ParkourCourse.Pos(
                                                        x, island.y() + 2, island.z() + 3)),
                        "Every randomized checkpoint has a clear spawn body");
            }
        }
        check(
                seen.size() == ParkourCourse.Kind.values().length && seen.size() == 24,
                "Every original and new building task is eligible");
        check(lengths.equals(Set.of(7, 8)), "Both course lengths are generated");
        check(orders.size() > 100, "The seven/eight-task order varies between games");
        ParkourCourse traverse = buildingCourse();
        var from = traverse.island(5);
        var to = traverse.island(6);
        check(
                to.centerX() == from.centerX()
                        && to.z() - from.z() - from.depth() == 20
                        && to.y() == from.y()
                        && from.width() == 3
                        && to.width() == 3,
                "Ladder traverse crosses a twenty-block gap straight ahead between narrow ledges");
        check(
                traverse.supplies(5)
                        .equals(
                                List.of(
                                        new ParkourCourse.Supply(Material.CYAN_WOOL, 12),
                                        new ParkourCourse.Supply(Material.LADDER, 12))),
                "The traverse grants exactly twelve blocks and twelve ladders");
        check(
                traverse.distance(5, to.centerX(), to.z() + 3.5) == to.z() - from.z(),
                "Forward traverse distance counts in live race ranking");
    }

    private static void neighbouringTasks() {
        for (var first : ParkourCourse.Kind.values())
            for (var second : ParkourCourse.Kind.values()) {
                if (first == second) continue;
                List<ParkourCourse.Kind> selected =
                        new java.util.ArrayList<>(List.of(first, second));
                for (var kind : ParkourCourse.Kind.values())
                    if (selected.size() < ParkourCourse.MIN_LENGTH && !selected.contains(kind))
                        selected.add(kind);
                for (boolean mirror : List.of(false, true)) {
                    ParkourCourse course = layout(selected, mirror);
                    for (int stage = 0; stage < course.stages(); stage++)
                        if (course.stage(stage) == ParkourCourse.Kind.LADDER_TRAVERSE) {
                            var from = course.island(stage);
                            var to = course.island(stage + 1);
                            for (int z = from.z() + 3; z <= to.z() + 3; z++)
                                for (int y = from.y() + 1; y <= from.y() + 2; y++)
                                    check(
                                            !course.blocks()
                                                    .containsKey(
                                                            new ParkourCourse.Pos(
                                                                    (int) from.centerX(), y, z)),
                                            "neighbouring task terrain must not block the forward"
                                                    + " route");
                        }
                    if (first == ParkourCourse.Kind.LADDER_DROP) {
                        var from = course.island(0);
                        var goal = course.island(1);
                        int wall =
                                from.centerX() < ParkourCourse.WIDTH / 2.0
                                        ? 0
                                        : ParkourCourse.WIDTH - 1;
                        for (int y = goal.y(); y <= from.y() + 4; y++) {
                            var anchor = new ParkourCourse.Pos(wall, y, from.z() + 7);
                            check(
                                    (course.blocks().get(anchor) == Material.DEEPSLATE_TILES)
                                            == (y < goal.y() + 3),
                                    "Clutch anchors exist only in the bottom three rows");
                            check(
                                    course.buildPosition(0, anchor) == (y < goal.y() + 3),
                                    "Invisible dividers cannot support a series of upper catches");
                        }
                        check(
                                !course.buildPosition(
                                        0, new ParkourCourse.Pos(wall, goal.y() - 1, from.z() + 7)),
                                "Clutch placement stays at the final catch rather than below it");
                    }
                    if (first == ParkourCourse.Kind.LADDER_DROP
                            && second == ParkourCourse.Kind.LADDER_TRAVERSE) {
                        var from = course.island(0);
                        var goal = course.island(1);
                        int wall =
                                from.centerX() < ParkourCourse.WIDTH / 2.0
                                        ? 0
                                        : ParkourCourse.WIDTH - 1;
                        check(
                                course.blocks()
                                                .get(
                                                        new ParkourCourse.Pos(
                                                                wall, goal.y() + 1, goal.z() - 1))
                                        == Material.LIGHT_BLUE_CONCRETE,
                                "clutch wall has a visible terminal stripe before the goal");
                        for (int z = goal.z(); z < goal.z() + 7; z++)
                            check(
                                    !course.blocks()
                                            .containsKey(
                                                    new ParkourCourse.Pos(wall, goal.y() + 1, z)),
                                    "descent wall stops before every row of the next checkpoint");
                    }
                }
            }
    }

    private static void waterEntryIsolation() {
        for (var wet :
                List.of(
                        ParkourCourse.Kind.SWIM,
                        ParkourCourse.Kind.RIPTIDE_LAUNCH,
                        ParkourCourse.Kind.BOAT_WATER))
            for (var previous : ParkourCourse.Kind.values()) {
                if (previous == wet) continue;
                for (boolean mirror : List.of(false, true)) {
                    var course = layout(List.of(previous, wet), mirror);
                    var landing = course.island(1);
                    var goal = course.island(2);
                    check(
                            goal.z() - landing.z() - landing.depth() == wet.gap
                                    && goal.y() - landing.y() == wet.rise,
                            "The dry run-up preserves the water task's original gap and height");
                    check(
                            course.island(0).depth() == (previous.wetStart() ? 19 : 7)
                                    && landing.depth() == 19,
                            "Only wet starts gain the twelve-block extension, including narrow"
                                    + " traverse checkpoints");
                    for (int x = landing.x(); x < landing.x() + landing.width(); x++)
                        for (int z = landing.z(); z < landing.z() + 16; z++) {
                            var deck =
                                    course.blocks().get(new ParkourCourse.Pos(x, landing.y(), z));
                            check(
                                    deck != null && deck != Material.WATER,
                                    "Incoming racers have a solid dry landing and run-up");
                            for (int y = landing.y() + 1; y <= landing.y() + 2; y++)
                                check(
                                        course.blocks().get(new ParkourCourse.Pos(x, y, z))
                                                != Material.WATER,
                                        "Future water cannot absorb the previous task's fall");
                        }
                    int waterCells = 0;
                    for (var entry : course.blocks().entrySet()) {
                        var pos = entry.getKey();
                        if (entry.getValue() != Material.WATER
                                || pos.z() < landing.z()
                                || pos.z() >= goal.z()) continue;
                        waterCells++;
                        check(
                                pos.z() >= landing.z() + 16,
                                "The next water begins beyond the entire dry arrival zone");
                        check(
                                !course.attempt(0, pos.x() + .5, pos.y() + .1, pos.z() + .5)
                                        && course.attempt(
                                                1, pos.x() + .5, pos.y() + .1, pos.z() + .5),
                                "A dive into future water is rejected until the checkpoint is"
                                        + " reached");
                    }
                    check(
                            (waterCells > 0) == (wet != ParkourCourse.Kind.SWIM),
                            "Boat and trident starts keep native water; the swim task starts dry");
                    check(
                            landing.landing(landing.centerX(), landing.y() + 1, landing.z() + 3.5)
                                    && !landing.landing(
                                            landing.centerX(), landing.y() + 1, landing.z() + 16.5),
                            "The goal marker and checkpoint remain on the dry front pad");
                }
            }
    }

    private static void chestDrop() throws ReflectiveOperationException {
        for (boolean mirror : List.of(false, true)) {
            var course = layout(List.of(ParkourCourse.Kind.CHEST_DROP), mirror);
            var from = course.island(0);
            var to = course.island(1);
            var chest = course.dropChest(0);
            check(
                    course.supplies(0).isEmpty()
                            && chest != null
                            && chest.y() == from.y() + 1
                            && from.y() - to.y() == 56,
                    "The empty-handed chest clutch offers its bucket at take-off height");
            check(
                    course.blocks().get(chest) == Material.CHEST
                            && Math.abs(chest.x() + .5 - from.centerX()) == 2
                            && chest.z() >= to.z()
                            && chest.z() < to.z() + 7,
                    "The native chest is in reach beside the fall line above the landing island");
            double edge = from.z() + from.depth();
            check(
                    chest.z() - (edge + .3) > 4.5,
                    "Even a player overhanging the starting lip cannot reach the chest on foot");
            double dx = chest.x() + .5 - from.centerX();
            double dy = chest.y() + .5 - (from.y() + 2 + 1.62);
            double dz = chest.z() + .1 - (edge + 3);
            check(
                    dx * dx + dy * dy + dz * dz < 4.5 * 4.5,
                    "After jumping three blocks forward the chest face is within native reach");
            for (int y = chest.y() + 1; y <= from.y() + 3; y++) {
                check(
                        course.blocks().get(new ParkourCourse.Pos(chest.x(), y, chest.z()))
                                == Material.YELLOW_STAINED_GLASS,
                        "Glass blocks chest-lid landings without blocking native chest opening");
                check(
                        !course.blocks()
                                .containsKey(
                                        new ParkourCourse.Pos((int) from.centerX(), y, chest.z())),
                        "The falling player's body has a clear path beside the covered chest");
            }
            UUID id = new UUID(13, 1), rival = new UUID(13, 2);
            FakeWorld world = new FakeWorld();
            ParkourArena arena = new ParkourArena(world.world, List.of(id, rival), course);
            Location own = arena.chestLocation(id, 0), other = arena.chestLocation(rival, 0);
            check(
                    arena.isChest(id, 0, own)
                            && !arena.isChest(id, 0, other)
                            && !arena.isChest(id, 1, own)
                            && !arena.isChest(rival, 0, own),
                    "Chest access is tied to the owner's lane and active checkpoint");
            check(
                    arena.mutable(id, 0, world.block((int) from.centerX(), to.y() + 1, to.z() + 3))
                            && !arena.mutable(
                                    id, 0, world.block((int) from.centerX(), chest.y(), chest.z())),
                    "Water can clutch at the bottom but cannot stop the fall at the chest column");
            boolean[] grounded = {false};
            Player player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> id;
                                                case "getWorld" -> world.world;
                                                case "getGameMode" -> GameMode.SURVIVAL;
                                                case "isDead" -> false;
                                                case "isOnGround" -> grounded[0];
                                                default ->
                                                        throw new AssertionError(
                                                                "Unexpected chest access call: "
                                                                        + method.getName());
                                            });
            ParkourRaceSession session =
                    new ParkourRaceSession(null, null, PartyRoundContext.standalone(null));
            session.world = world.world;
            session.standings = new SurvivalStandings(List.of(id, rival));
            session.connected.addAll(List.of(id, rival));
            field(PartySurvivalSession.class, "playing").set(session, true);
            field(ParkourRaceSession.class, "course").set(session, course);
            field(ParkourRaceSession.class, "arena").set(session, arena);
            ParkourRace race = new ParkourRace(List.of(id, rival), course.stages());
            field(ParkourRaceSession.class, "race").set(session, race);
            var access =
                    ParkourRaceSession.class.getDeclaredMethod(
                            "chestAccess", Player.class, Location.class);
            access.setAccessible(true);
            check(
                    (boolean) access.invoke(session, player, own)
                            && !(boolean) access.invoke(session, player, other),
                    "A falling player can open their chest but cannot loot a rival's chest");
            grounded[0] = true;
            check(
                    !(boolean) access.invoke(session, player, own),
                    "Grounded players cannot loot the midair chest");
            grounded[0] = false;
            race.reach(id, 1, 100);
            check(
                    !(boolean) access.invoke(session, player, own),
                    "Old chest access ends on the next checkpoint");
        }
    }

    private static void navigation() {
        check(
                ParkourGuide.direction(0, 0, 10).equals("↑")
                        && ParkourGuide.direction(0, -10, 0).equals("→")
                        && ParkourGuide.direction(0, 10, 0).equals("←")
                        && ParkourGuide.direction(0, 0, -10).equals("↓"),
                "navigation arrow follows the player's view rather than a fixed compass");
        check(
                ParkourGuide.direction(-90, 10, 0).equals("↑")
                        && ParkourGuide.direction(360, 0, 10).equals("↑"),
                "yaw wrapping and sideways courses point ahead when aligned");
        check(
                ParkourGuide.direction(45, 0, 0).equals("●"),
                "vertical-only target is not given a misleading horizontal direction");
        for (boolean mirror : List.of(false, true)) {
            ParkourCourse course =
                    layout(
                            List.of(
                                    ParkourCourse.Kind.LADDER_TRAVERSE,
                                    ParkourCourse.Kind.BRIDGE,
                                    ParkourCourse.Kind.WIND_WALL,
                                    ParkourCourse.Kind.WATER_DROP,
                                    ParkourCourse.Kind.PEARL,
                                    ParkourCourse.Kind.FLIGHT),
                            mirror);
            UUID id = new UUID(8, 1);
            FakeWorld world = new FakeWorld();
            ParkourArena arena = new ParkourArena(world.world, List.of(id), course);
            Location start = arena.checkpoint(id, 0), target = arena.checkpoint(id, 1);
            check(
                    start.getDirection()
                                    .dot(target.toVector().subtract(start.toVector()).normalize())
                            > .99,
                    "clock/spawn faces the forward traverse goal in both mirrored layouts");
        }
        Location rotated = new Location(null, 23.5, 100, 50.5, 90, 0);
        Location frame = ParkourGuide.frameOrigin(rotated, 3);
        check(
                frame.getYaw() == 0 && frame.getX() == 22 && frame.getZ() == 47,
                "narrow goal overlay remains aligned independently of the checkpoint yaw");
        me.petr1furious.potatominigames.i18n.I18n.register();
        try {
            for (double rise : new double[] {-44, 0, 64}) {
                var message =
                        me.petr1furious.potatominigames.i18n.I18n.tr(
                                "party.rounds.parkour_race.navigation",
                                "↑",
                                2,
                                7,
                                50,
                                ParkourGuide.height(rise));
                for (var locale :
                        List.of(java.util.Locale.ENGLISH, java.util.Locale.forLanguageTag("ru")))
                    check(
                            !me.petr1furious.potatominigames.i18n.I18n.plain(message, locale)
                                    .contains("potatominigames."),
                            "navigation and vertical hints render in both languages");
            }
        } finally {
            me.petr1furious.potatominigames.i18n.I18n.unregister();
        }
    }

    private static void expandedTasks() {
        var course =
                course(
                        ParkourCourse.Kind.LOW_CEILING,
                        ParkourCourse.Kind.RIPTIDE_LAUNCH,
                        ParkourCourse.Kind.BOAT_WATER,
                        ParkourCourse.Kind.BOAT_ICE,
                        ParkourCourse.Kind.HONEY_TRAVERSE,
                        ParkourCourse.Kind.WIND_HOPS);
        for (int stage = 0; stage < course.stages(); stage++) {
            var from = course.island(stage).obstacleStart();
            var to = course.island(stage + 1);
            check(
                    course.attempt(stage, to.centerX(), to.y() + 1, to.z() + 3.5),
                    "every new task goal remains in the current attempt, not beyond its boundary");
            if (course.stage(stage) == ParkourCourse.Kind.BOAT_WATER) {
                for (int row = 0; row < 3; row++) {
                    int z = from.z() + 14 + row * 8;
                    int open = 0;
                    for (int x = 0; x < ParkourCourse.WIDTH; x++)
                        if (!course.blocks().containsKey(new ParkourCourse.Pos(x, from.y() + 2, z)))
                            open++;
                    check(open == 5, "boat gates have five clear columns for hull and passenger");
                }
            }
        }
        var trident = course.supplies(1).getFirst();
        check(
                trident.material() == Material.TRIDENT
                        && "riptide".equals(trident.enchantment())
                        && trident.level() == 3,
                "water launch receives a real vanilla Riptide trident");
        me.petr1furious.potatominigames.i18n.I18n.register();
        try {
            for (var kind : ParkourCourse.Kind.values())
                for (String suffix : List.of(".name", ".hint")) {
                    var text = me.petr1furious.potatominigames.i18n.I18n.tr(kind.key() + suffix);
                    for (var locale :
                            List.of(
                                    java.util.Locale.ENGLISH,
                                    java.util.Locale.forLanguageTag("ru")))
                        check(
                                !me.petr1furious.potatominigames.i18n.I18n.plain(text, locale)
                                        .contains("potatominigames."),
                                "every task name and mechanic hint is localized");
                }
        } finally {
            me.petr1furious.potatominigames.i18n.I18n.unregister();
        }
    }

    @SuppressWarnings("unchecked")
    private static void vehicleIsolation() throws ReflectiveOperationException {
        UUID owner = new UUID(11, 1), other = new UUID(11, 2);
        FakeWorld world = new FakeWorld();
        var course =
                course(
                        ParkourCourse.Kind.BOAT_ICE,
                        ParkourCourse.Kind.BRIDGE,
                        ParkourCourse.Kind.WIND_WALL,
                        ParkourCourse.Kind.WATER_DROP,
                        ParkourCourse.Kind.SCAFFOLD,
                        ParkourCourse.Kind.PEARL);
        ParkourArena arena = new ParkourArena(world.world, List.of(owner, other), course);
        boolean[] ejected = {false}, removed = {false}, stopped = {false};
        var boat =
                (org.bukkit.entity.Boat)
                        Proxy.newProxyInstance(
                                org.bukkit.entity.Boat.class.getClassLoader(),
                                new Class<?>[] {org.bukkit.entity.Boat.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getWorld" -> world.world;
                                            case "isValid" -> !removed[0];
                                            case "eject" -> {
                                                ejected[0] = true;
                                                yield true;
                                            }
                                            case "remove" -> {
                                                removed[0] = true;
                                                yield null;
                                            }
                                            case "setVelocity" -> {
                                                stopped[0] = true;
                                                yield null;
                                            }
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == args[0];
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected vehicle call: "
                                                                    + method.getName());
                                        });
        ParkourRaceSession session =
                new ParkourRaceSession(null, null, PartyRoundContext.standalone(null));
        session.world = world.world;
        session.standings = new SurvivalStandings(List.of(owner, other));
        session.connected.addAll(List.of(owner, other));
        field(PartySurvivalSession.class, "playing").set(session, true);
        field(ParkourRaceSession.class, "course").set(session, course);
        field(ParkourRaceSession.class, "arena").set(session, arena);
        field(ParkourRaceSession.class, "race")
                .set(session, new ParkourRace(List.of(owner, other), course.stages()));
        ((Map<UUID, Set<org.bukkit.entity.Entity>>)
                        field(ParkourRaceSession.class, "entities").get(session))
                .put(owner, new java.util.HashSet<>(Set.of(boat)));
        for (UUID id : List.of(owner, other)) {
            Player p =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> id;
                                                case "getWorld" -> world.world;
                                                case "getGameMode" -> GameMode.SURVIVAL;
                                                case "isDead" -> false;
                                                default ->
                                                        throw new AssertionError(
                                                                "Unexpected vehicle passenger call:"
                                                                        + " "
                                                                        + method.getName());
                                            });
            var enter = new org.bukkit.event.vehicle.VehicleEnterEvent(boat, p);
            session.vehicleEnter(enter);
            check(
                    enter.isCancelled() == !id.equals(owner),
                    "vehicle accepts its racing owner and rejects a rival passenger");
        }
        var move =
                new org.bukkit.event.vehicle.VehicleMoveEvent(
                        boat, arena.checkpoint(owner, 0), arena.checkpoint(other, 0));
        session.vehicleMove(move);
        check(
                stopped[0]
                        && ((Set<UUID>) field(ParkourRaceSession.class, "failed").get(session))
                                .contains(owner),
                "vehicle motion outside the owner's attempt is stopped and queues a reset");
        var clear = ParkourRaceSession.class.getDeclaredMethod("clearEntities", UUID.class);
        clear.setAccessible(true);
        clear.invoke(session, owner);
        check(
                ejected[0] && removed[0],
                "attempt cleanup ejects passengers before removing their vehicle");
    }

    private static void geometry() {
        ParkourCourse course = buildingCourse();
        for (int stage = 0; stage < course.stages(); stage++) {
            var from = course.island(stage);
            var to = course.island(stage + 1);
            List<ParkourCourse.Supply> supplies = course.supplies(stage);
            check(supplies.size() >= 1 && supplies.size() <= 2, "Kits remain task-specific");
            for (var item : supplies)
                check(item.count() > 0 && item.count() <= 64, "Finite vanilla stacks");
            check(
                    course.attempt(stage, from.centerX(), from.y() + 1, from.z() + 3.5),
                    "Every spawn is inside its active task");
            check(
                    !course.attempt(stage, ParkourCourse.WIDTH, from.y() + 1, from.z()),
                    "Neighbour lane excluded");
            check(
                    !course.attempt(stage, to.centerX(), to.y() + 1, to.z() + 7),
                    "Future section excluded");
        }
        check(
                course.supplies(2)
                        .equals(List.of(new ParkourCourse.Supply(Material.WATER_BUCKET, 1))),
                "Water drops have no bypass kit");
        FakeWorld world = new FakeWorld();
        UUID a = new UUID(1, 1), b = new UUID(1, 2);
        ParkourArena arena = new ParkourArena(world.world, List.of(a, b), course);
        var start = course.island(0);
        check(
                world.type((int) start.centerX(), start.y(), 3) == Material.GOLD_BLOCK
                        && world.type((int) start.centerX() + ParkourCourse.PITCH, start.y(), 3)
                                == Material.GOLD_BLOCK,
                "Spawn islands precede sliced construction");
        boolean ready = false;
        while (!ready) {
            int before = world.writes;
            ready = arena.buildSlice(211);
            check(world.writes - before <= 211, "Construction respects per-tick budget");
        }
        for (var entry : course.blocks().entrySet()) {
            var pos = entry.getKey();
            check(
                    world.type(pos.x(), pos.y(), pos.z()) == entry.getValue()
                            && world.type(pos.x() + ParkourCourse.PITCH, pos.y(), pos.z())
                                    == entry.getValue(),
                    "Lanes are identical");
        }
        for (int y = ParkourCourse.BOTTOM; y <= ParkourCourse.TOP; y++)
            for (int z = -1; z <= course.end(); z++)
                check(
                        world.type(ParkourCourse.WIDTH, y, z) == Material.BARRIER,
                        "Continuous invisible divider");
        check(
                world.type(3, ParkourCourse.TOP, 12) == Material.BARRIER
                        && world.type(3, ParkourCourse.BOTTOM, 12) == Material.BARRIER
                        && world.type(3, 70, -1) == Material.BARRIER
                        && world.type(3, 70, course.end()) == Material.BARRIER,
                "Shell prevents bypass above, below or around lanes");
        check(
                arena.owner(new Location(world.world, ParkourCourse.WIDTH + .5, 70, 3)) == null
                        && arena.owner(arena.checkpoint(b, 0)).equals(b),
                "Divider cells have no owner");
        check(
                !arena.mutable(a, 0, world.block((int) start.centerX(), start.y(), 3)),
                "Course floor is protected");
        ParkourCourse extras =
                course(
                        ParkourCourse.Kind.SWIM,
                        ParkourCourse.Kind.SLIME_BOUNCE,
                        ParkourCourse.Kind.BLOCK_ASCENT,
                        ParkourCourse.Kind.POT_ASCENT,
                        ParkourCourse.Kind.BRIDGE,
                        ParkourCourse.Kind.PEARL);
        var swim = extras.island(0).obstacleStart();
        int center = (int) swim.centerX();
        check(
                !extras.blocks().containsKey(new ParkourCourse.Pos(center, swim.y() + 1, swim.z() + 8))
                        && extras.blocks()
                                        .get(
                                                new ParkourCourse.Pos(
                                                        center, swim.y() + 2, swim.z() + 8))
                                == Material.DEEPSLATE_TILES,
                "The dry swim passage and its protected ceiling follow the sampled index");
        var bounce = extras.island(1);
        check(
                extras.blocks()
                                .get(
                                        new ParkourCourse.Pos(
                                                (int) bounce.centerX(),
                                                bounce.y() - 36,
                                                bounce.z() + 18))
                        == Material.LIGHT_BLUE_CONCRETE,
                "Rebound pad follows the sampled source island");
    }

    private static void swimRoute() {
        for (boolean mirror : List.of(false, true)) {
            var course =
                    layout(
                            List.of(
                                    ParkourCourse.Kind.BRIDGE,
                                    ParkourCourse.Kind.WIND_WALL,
                                    ParkourCourse.Kind.WATER_DROP,
                                    ParkourCourse.Kind.SWIM,
                                    ParkourCourse.Kind.BLOCK_ASCENT,
                                    ParkourCourse.Kind.PEARL),
                            mirror);
            var from = course.island(3).obstacleStart();
            int center = (int) from.centerX(), start = from.z() + 7, end = course.island(4).z() - 1;
            check(
                    course.supplies(3)
                            .equals(List.of(new ParkourCourse.Supply(Material.WATER_BUCKET, 1))),
                    "The dry swim task grants one full water bucket");
            check(
                    course.blocks().entrySet().stream()
                            .noneMatch(entry ->
                                    entry.getValue() == Material.WATER
                                            && entry.getKey().z() >= from.z() + 4
                                            && entry.getKey().z() <= end),
                    "Both the entrance and the full tunnel start without generated water");
            var pending = new java.util.ArrayDeque<ParkourCourse.Pos>();
            var seen = new java.util.HashSet<ParkourCourse.Pos>();
            pending.add(new ParkourCourse.Pos(center, from.y() + 1, start));
            while (!pending.isEmpty()) {
                var pos = pending.removeFirst();
                if (pos.z() < start
                        || pos.z() > end
                        || course.blocks().containsKey(pos)
                        || !seen.add(pos)) continue;
                for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                    pending.add(
                            new ParkourCourse.Pos(pos.x() + step[0], pos.y(), pos.z() + step[1]));
            }
            check(
                    seen.contains(new ParkourCourse.Pos(center, from.y() + 1, end)),
                    "The dry passage stays connected through every baffle in both lane layouts");
            int gates = 0, lastOpening = 0;
            for (int z = start; z <= end; z++) {
                int blocked = 0, opening = 0;
                for (int x = center - 1; x <= center + 1; x++)
                    if (course.blocks().get(new ParkourCourse.Pos(x, from.y() + 1, z))
                            == Material.LIGHT_BLUE_CONCRETE) blocked++;
                    else opening = x;
                if (blocked == 0) continue;
                check(
                        blocked == 2
                                && Math.abs(opening - center) == 1
                                && (gates == 0 || lastOpening != opening),
                        "Each baffle leaves one full-width water cell, alternating left and right");
                lastOpening = opening;
                gates++;
            }
            check(gates == 4, "The tunnel requires four swimming detours");
            FakeWorld world = new FakeWorld();
            UUID id = new UUID(12, 1);
            ParkourArena arena = new ParkourArena(world.world, List.of(id), course);
            check(
                    !arena.mutable(id, 3, world.block(center + 2, from.y() + 1, start))
                            && !arena.mutable(id, 3, world.block(center, from.y() + 1, start + 3)),
                    "Water placement cannot replace the shell or a swimming barrier");
            List<Block> water =
                    List.of(
                            world.block(center, from.y() + 1, from.z() + 5),
                            world.block(center, from.y() + 1, start + 1),
                            world.block(center, from.y() + 1, start + 4));
            for (Block block : water) {
                check(
                        block.getType() == Material.AIR
                                && arena.remember(id, 3, block, block.getBlockData()),
                        "The bucket and water flow can modify the dry entry and passage");
                block.setType(Material.WATER, false);
            }
            Block source = water.getFirst();
            check(
                    arena.remember(id, 3, source, source.getBlockData()),
                    "The source can be retrieved without replacing its original air baseline");
            source.setType(Material.AIR, false);
            arena.reset(id);
            for (Block block : water)
                check(
                        block.getType() == Material.AIR && !arena.changed(id, block),
                        "Reset removes placed sources and flow and restores a completely dry"
                                + " tunnel");
        }
    }

    private static void honeyCatch() {
        for (boolean mirror : List.of(false, true)) {
            var course =
                    layout(
                            List.of(
                                    ParkourCourse.Kind.WATER_DROP,
                                    ParkourCourse.Kind.HONEY_TRAVERSE,
                                    ParkourCourse.Kind.BRIDGE),
                            mirror);
            var from = course.island(1);
            var to = course.island(2);
            int count = 0;
            for (var entry : course.blocks().entrySet()) {
                if (entry.getValue() != Material.HONEY_BLOCK) continue;
                var p = entry.getKey();
                count++;
                check(
                        p.z() >= from.z() + from.depth()
                                && p.z() >= to.z() - 3
                                && p.z() < to.z()
                                && p.y() >= to.y()
                                && p.y() < to.y() + ParkourCourse.HONEY_CATCH_HEIGHT,
                        "honey exists only at the final catch, never inside the preceding goal");
                check(
                        !course.attempt(0, p.x() + .5, p.y() + 1, p.z() + .5),
                        "the previous task cannot use future honey to cushion its fall");
                if (p.y() == to.y() + ParkourCourse.HONEY_CATCH_HEIGHT - 1) {
                    check(
                            course.blocks().get(new ParkourCourse.Pos(p.x(), p.y() + 1, p.z()))
                                    == ParkourCourse.OBSTACLE_BLOCK,
                            "a full solid cap covers every honey top landing surface");
                    double fall = from.y() + 1 - (p.y() + 2);
                    check(
                            fall - 3 >= 20,
                            "landing on the cap without a side catch causes a failed attempt");
                }
            }
            check(
                    count == 3 * ParkourCourse.HONEY_CATCH_HEIGHT,
                    "the final honey patch is short and gives a full hitbox room below the cap");
            check(
                    to.z() - (from.z() + from.depth()) <= 4,
                    "the short catch remains within an ordinary falling jump's forward reach");
        }
    }

    private static void iceRoute() {
        for (boolean mirror : List.of(false, true)) {
            var course = layout(List.of(ParkourCourse.Kind.BOAT_ICE), mirror);
            var from = course.island(0);
            var to = course.island(1);
            int center = (int) from.centerX(), start = from.z() + 7, end = to.z() - 1;
            var distances = new HashMap<ParkourCourse.Pos, Integer>();
            var pending = new java.util.ArrayDeque<ParkourCourse.Pos>();
            var first = new ParkourCourse.Pos(center, from.y(), start);
            distances.put(first, 0);
            pending.add(first);
            while (!pending.isEmpty()) {
                var pos = pending.removeFirst();
                for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    var next = new ParkourCourse.Pos(pos.x() + step[0], pos.y(), pos.z() + step[1]);
                    if (next.z() < start
                            || next.z() > end
                            || course.blocks().get(next) != Material.PACKED_ICE
                            || distances.containsKey(next)) continue;
                    distances.put(next, distances.get(pos) + 1);
                    pending.add(next);
                }
            }
            Integer length = distances.get(new ParkourCourse.Pos(center, from.y(), end));
            check(
                    length != null && length >= end - start + 20,
                    "The ice road is connected and requires broad bends rather than a"
                            + " straight shortcut");
            for (int row : new int[] {start, end}) {
                int width = 0;
                for (int x = 0; x < ParkourCourse.WIDTH; x++)
                    if (course.blocks().get(new ParkourCourse.Pos(x, from.y(), row))
                            == Material.PACKED_ICE) width++;
                check(
                        width == 5,
                        "The five-block ice road joins both checkpoint islands without a gap");
            }
            Integer previous = null;
            for (int z = start; z <= end; z++) {
                int sum = 0, width = 0;
                for (int x = 0; x < ParkourCourse.WIDTH; x++)
                    if (course.blocks().get(new ParkourCourse.Pos(x, from.y(), z))
                            == Material.PACKED_ICE) {
                        sum += x;
                        width++;
                        check(
                                !course.blocks()
                                                .containsKey(
                                                        new ParkourCourse.Pos(x, from.y() + 1, z))
                                        && !course.blocks()
                                                .containsKey(
                                                        new ParkourCourse.Pos(x, from.y() + 2, z)),
                                "hull and passenger have clear space across the full road width");
                    }
                check(width == 5, "road stays five blocks wide through both bends");
                int middle = sum / width;
                check(
                        previous == null || Math.abs(previous - middle) <= 1,
                        "the centerline progresses forward smoothly without right-angle runs");
                if (z < start + 3 || z > end - 3)
                    check(middle == center, "entry and exit have straight steering runups");
                if (z < end - 1)
                    for (int x : new int[] {middle - 3, middle + 3})
                        check(
                                course.blocks().get(new ParkourCourse.Pos(x, from.y() + 2, z))
                                        == ParkourCourse.OBSTACLE_BLOCK,
                                "outer guardrails prevent cutting the curves or sliding off");
                previous = middle;
            }
            for (var entry : course.blocks().entrySet()) {
                var pos = entry.getKey();
                if (entry.getValue() == Material.PACKED_ICE && pos.z() >= start && pos.z() <= end)
                    check(
                            distances.containsKey(pos),
                            "All ice corners belong to one connected drivable road");
            }
        }
    }

    private static void staircase() {
        for (boolean mirror : List.of(false, true)) {
            ParkourCourse course =
                    layout(
                            List.of(
                                    ParkourCourse.Kind.BLOCK_ASCENT,
                                    ParkourCourse.Kind.POT_ASCENT,
                                    ParkourCourse.Kind.LADDER_TRAVERSE,
                                    ParkourCourse.Kind.SWIM,
                                    ParkourCourse.Kind.LADDER_DROP,
                                    ParkourCourse.Kind.PEARL),
                            mirror);
            var from = course.island(1);
            var to = course.island(2);
            int center = (int) from.centerX();
            check(
                    to.z() - from.z() - 7 == 14 && to.y() - from.y() == 14,
                    "Flower-pot staircase combines forward travel with a full-height ascent");
            check(
                    course.supplies(1)
                            .equals(
                                    List.of(
                                            new ParkourCourse.Supply(Material.CYAN_WOOL, 16),
                                            new ParkourCourse.Supply(Material.FLOWER_POT, 18))),
                    "The pot task grants small flower pots and a limited block budget");
            check(
                    course.recoverMaterials(0) && !course.recoverMaterials(1),
                    "Pot supports cannot be recycled into an unlimited blocks-only route");
            UUID id = new UUID(7, 1);
            FakeWorld world = new FakeWorld();
            ParkourArena arena = new ParkourArena(world.world, List.of(id), course);
            while (!arena.buildSlice(4096)) {}
            for (int step = 1; step <= 14; step++) {
                Block pot = world.block(center, from.y() + step, from.z() + 5 + step);
                Block wool = world.block(center, from.y() + step, from.z() + 6 + step);
                check(
                        world.type(center, from.y() + step - 1, from.z() + 5 + step)
                                == (step == 1 ? Material.LIGHT_BLUE_CONCRETE : Material.CYAN_WOOL),
                        "Each small pot has the previous stair block beneath it");
                check(
                        arena.remember(id, 1, pot, pot.getBlockData()),
                        "Native pot placements fit the active section, including a narrow goal");
                pot.setType(Material.FLOWER_POT, false);
                check(
                        arena.remember(id, 1, wool, wool.getBlockData()),
                        "The adjacent diagonal wool block fits without modifying course geometry");
                wool.setType(Material.CYAN_WOOL, false);
            }
            check(
                    !arena.mutable(id, 1, world.block(center + 2, from.y() + 1, from.z() + 6))
                            && !arena.mutable(
                                    id, 1, world.block(center - 2, from.y() + 1, from.z() + 6)),
                    "Placement cannot use side barriers or walls as free staircase anchors");
            check(
                    world.type(center, to.y(), to.z()) == Material.LIGHT_BLUE_CONCRETE,
                    "The destination marks the permitted central construction columns");
            arena.reset(id);
            check(
                    world.type(center, to.y(), to.z() - 1) == Material.AIR,
                    "Clock rollback removes the final pot stair before refreshing resources");
        }
    }

    private static void rollback() {
        UUID id = new UUID(2, 1);
        FakeWorld world = new FakeWorld();
        ParkourCourse course = buildingCourse();
        ParkourArena arena = new ParkourArena(world.world, List.of(id), course);
        while (!arena.buildSlice(4096)) {}
        Block block = world.block(3, course.island(0).y() + 2, 12);
        check(arena.remember(id, 0, block, block.getBlockData()), "Record before placement");
        block.setType(Material.CYAN_WOOL, false);
        arena.remember(id, 0, block, block.getBlockData());
        block.setType(Material.WATER, false);
        arena.reset(id);
        check(
                block.getType() == Material.AIR && !arena.changed(id, block),
                "Repeated changes restore the original baseline");
        arena.remember(id, 0, block, block.getBlockData());
        block.setType(Material.CYAN_WOOL, false);
        arena.reset(id);
        Block next = world.block(3, course.island(1).y() + 2, course.island(1).z() + 4);
        arena.remember(id, 1, next, next.getBlockData());
        next.setType(Material.LADDER, false);
        arena.reset(id);
        check(
                block.getType() == Material.AIR && next.getType() == Material.AIR,
                "Task transition cannot carry blocks or fluids");
        int accepted = 0;
        for (int y = 220; y < 240; y++)
            for (int x = 0; x < ParkourCourse.WIDTH; x++)
                for (int z = 0; z < 32; z++) {
                    Block at = world.block(x, y, z);
                    if (arena.remember(id, 0, at, at.getBlockData())) accepted++;
                }
        check(accepted == ParkourArena.MAX_CHANGES, "Journal remains bounded in wider lanes");
        arena.reset(id);
        check(arena.remember(id, 0, block, block.getBlockData()), "Reset frees the journal budget");
        arena.clear();
    }

    @SuppressWarnings("unchecked")
    private static void flightBoundary() throws ReflectiveOperationException {
        UUID id = new UUID(3, 1);
        FakeWorld world = new FakeWorld();
        ParkourCourse course =
                course(
                        ParkourCourse.Kind.FLIGHT,
                        ParkourCourse.Kind.LADDER_TRAVERSE,
                        ParkourCourse.Kind.BRIDGE,
                        ParkourCourse.Kind.WATER_DROP,
                        ParkourCourse.Kind.SCAFFOLD,
                        ParkourCourse.Kind.PEARL);
        ParkourArena arena = new ParkourArena(world.world, List.of(id), course);
        Location[] at = {arena.checkpoint(id, 0)};
        check(
                course.supplies(0)
                        .equals(
                                List.of(
                                        new ParkourCourse.Supply(Material.ELYTRA, 1),
                                        new ParkourCourse.Supply(Material.FIREWORK_ROCKET, 1))),
                "The flight task has exactly one consumable boost, restored by resetting the kit");
        boolean[] grounded = {false}, gliding = {true};
        Player p =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getUniqueId" -> id;
                                            case "getWorld" -> world.world;
                                            case "getLocation" -> at[0].clone();
                                            case "getGameMode" -> GameMode.SURVIVAL;
                                            case "isDead" -> false;
                                            case "isOnGround" -> grounded[0];
                                            case "isGliding" -> gliding[0];
                                            case "setGliding" -> {
                                                gliding[0] = (boolean) args[0];
                                                yield null;
                                            }
                                            case "setVelocity" -> null;
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected flight call: "
                                                                    + method.getName());
                                        });
        ParkourRaceSession session =
                new ParkourRaceSession(null, null, PartyRoundContext.standalone(null));
        session.world = world.world;
        session.standings = new SurvivalStandings(List.of(id));
        session.connected.add(id);
        field(PartySurvivalSession.class, "playing").set(session, true);
        ParkourRace race = new ParkourRace(List.of(id), course.stages());
        field(ParkourRaceSession.class, "course").set(session, course);
        field(ParkourRaceSession.class, "arena").set(session, arena);
        field(ParkourRaceSession.class, "race").set(session, race);
        Set<UUID> failed = (Set<UUID>) field(ParkourRaceSession.class, "failed").get(session);
        Set<UUID> skipped = (Set<UUID>) field(ParkourRaceSession.class, "skipped").get(session);
        var goal = course.island(1);
        var through =
                new org.bukkit.event.player.PlayerMoveEvent(
                        p,
                        at[0],
                        new Location(world.world, goal.centerX(), goal.y() + 15, goal.z() + 8));
        session.onPlayerMove(through);
        check(
                through.isCancelled()
                        && !gliding[0]
                        && failed.contains(id)
                        && race.checkpoint(id) == 0,
                "Rocket-speed movement past an unlanded checkpoint is rejected immediately");
        failed.clear();
        skipped.clear();
        gliding[0] = true;
        var future = arena.checkpoint(id, 2);
        var skip = new org.bukkit.event.player.PlayerMoveEvent(p, at[0], future);
        session.onPlayerMove(skip);
        check(
                skip.isCancelled() && failed.contains(id),
                "Cannot fly over the traverse to a future checkpoint without landing first");
        failed.clear();
        skipped.clear();
        at[0] = arena.checkpoint(id, 1);
        world.block(at[0].getBlockX(), goal.y(), at[0].getBlockZ())
                .setType(Material.GOLD_BLOCK, false);
        grounded[0] = true;
        gliding[0] = true;
        check(
                !arena.landed(p, 1),
                "A gliding player cannot claim the checkpoint even with a ground flag");
        grounded[0] = false;
        gliding[0] = false;
        check(!arena.landed(p, 1), "A fly-by at deck height is not a landing");
        grounded[0] = true;
        check(arena.landed(p, 1), "Real grounded landing is accepted");
        race.reach(id, 1, 10);
        var stale = new org.bukkit.event.entity.EntityToggleGlideEvent(p, true);
        session.glide(stale);
        check(stale.isCancelled(), "Old elytra cannot restart gliding on the next non-flight task");
        check(
                arena.attempt(id, 1, future),
                "Lateral checkpoint becomes accessible only after the preceding landing");
    }

    private static java.lang.reflect.Field field(Class<?> type, String name)
            throws ReflectiveOperationException {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @SuppressWarnings("unchecked")
    private static void damage() throws ReflectiveOperationException {
        UUID id = new UUID(4, 1);
        FakeWorld world = new FakeWorld();
        Player p =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getUniqueId" -> id;
                                            case "getWorld" -> world.world;
                                            case "getHealth" -> 20.0;
                                            case "getGameMode" -> GameMode.SURVIVAL;
                                            case "isDead" -> false;
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected clutch player call: "
                                                                    + method.getName());
                                        });
        ParkourRaceSession session =
                new ParkourRaceSession(null, null, PartyRoundContext.standalone(null));
        session.world = world.world;
        session.standings = new SurvivalStandings(List.of(id, new UUID(4, 2)));
        session.connected.add(id);
        var playing = PartySurvivalSession.class.getDeclaredField("playing");
        playing.setAccessible(true);
        playing.set(session, true);
        var raceField = ParkourRaceSession.class.getDeclaredField("race");
        raceField.setAccessible(true);
        ParkourRace race = new ParkourRace(List.of(id), ParkourCourse.MIN_LENGTH);
        race.reach(id, 1, 1);
        race.reach(id, 2, 2);
        raceField.set(session, race);
        var failedField = ParkourRaceSession.class.getDeclaredField("failed");
        failedField.setAccessible(true);
        Set<UUID> failures = (Set<UUID>) failedField.get(session);
        EntityDamageEvent lethal =
                new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL, null, 41);
        session.onEntityDamage(lethal);
        check(
                lethal.isCancelled() && failures.contains(id) && race.checkpoint(id) == 2,
                "Failed water drops queue a reset without granting the destination checkpoint or"
                        + " killing the player");
        failures.clear();
        EntityDamageEvent cushioned =
                new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL, null, 4);
        session.onEntityDamage(cushioned);
        check(
                !cushioned.isCancelled() && failures.isEmpty(),
                "Native nonlethal landing mitigation remains meaningful");
        EntityDamageEvent collision =
                new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FLY_INTO_WALL, null, 25);
        session.onEntityDamage(collision);
        check(
                collision.isCancelled() && failures.contains(id),
                "Lethal flight collisions also restart the attempt");
        EntityDamageEvent pvp =
                new EntityDamageEvent(p, EntityDamageEvent.DamageCause.ENTITY_ATTACK, null, 5);
        session.onEntityDamage(pvp);
        check(pvp.isCancelled(), "Combat damage cannot interfere with the traversal task");
    }

    private static void integration() {
        PartyGame game = new PartyGame((PartyConfig) null);
        check(
                game.modes().contains("parkour_race")
                        && game.maxPlayers("parkour_race") == 12
                        && game.icon("parkour_race") == Material.CLOCK,
                "Standalone menu mode is registered");
        check(
                game.createSession(null, "parkour_race") instanceof ParkourRaceSession,
                "Queue creates the right session");
        PartyRoundContext context =
                new PartyRoundContext(
                        null, List.of(), PartyRoundOptions.party(), (reason, result) -> {});
        check(
                PartyRoundFactory.create("parkour_race", null, null, context)
                                instanceof ParkourRaceSession
                        && PartyGamesState.MODES.contains("parkour_race"),
                "Embedded and standalone share the race implementation");
        try (var stream = ParkourRaceCheck.class.getResourceAsStream("/config.yml")) {
            var queues =
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(stream, StandardCharsets.UTF_8));
            check(
                    queues.getBoolean("queues.party.parkour_race.enabled")
                            && queues.getInt("queues.party.parkour_race.minPlayers") == 2
                            && queues.getInt("queues.party.parkour_race.maxPlayers") == 12,
                    "Default queue is enabled");
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
        try (var stream = ParkourRaceCheck.class.getResourceAsStream("/games/party.yml")) {
            var config =
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(stream, StandardCharsets.UTF_8));
            check(
                    config.getInt("parkourRace.durationSeconds") == 240
                            && List.of(config.getString("partyGames.modes").split(","))
                                    .contains("parkour_race"),
                    "Four-minute default and mixed-match pool are configured");
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static BlockData data(Material material) {
        return (BlockData)
                Proxy.newProxyInstance(
                        BlockData.class.getClassLoader(),
                        new Class<?>[] {BlockData.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getMaterial" -> material;
                                    case "clone" -> data(material);
                                    default ->
                                            throw new AssertionError(
                                                    "Unexpected block data call: "
                                                            + method.getName());
                                });
    }

    private static final class FakeWorld {
        final Map<ParkourCourse.Pos, BlockData> blocks = new HashMap<>();
        int writes;
        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getBlockAt" -> {
                                                if (args[0] instanceof Location at)
                                                    yield block(
                                                            at.getBlockX(),
                                                            at.getBlockY(),
                                                            at.getBlockZ());
                                                yield block(
                                                        (int) args[0],
                                                        (int) args[1],
                                                        (int) args[2]);
                                            }
                                            case "equals" -> proxy == args[0];
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected world call: "
                                                                    + method.getName());
                                        });

        Material type(int x, int y, int z) {
            var value = blocks.get(new ParkourCourse.Pos(x, y, z));
            return value == null ? Material.AIR : value.getMaterial();
        }

        Block block(int x, int y, int z) {
            var pos = new ParkourCourse.Pos(x, y, z);
            return (Block)
                    Proxy.newProxyInstance(
                            Block.class.getClassLoader(),
                            new Class<?>[] {Block.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getX" -> x;
                                        case "getY" -> y;
                                        case "getZ" -> z;
                                        case "getWorld" -> world;
                                        case "getType" -> type(x, y, z);
                                        case "isPassable" ->
                                                type(x, y, z) == Material.AIR
                                                        || type(x, y, z) == Material.WATER;
                                        case "getLocation" -> new Location(world, x, y, z);
                                        case "getBlockData" ->
                                                blocks.getOrDefault(pos, data(Material.AIR));
                                        case "setType" -> {
                                            blocks.put(pos, data((Material) args[0]));
                                            writes++;
                                            yield null;
                                        }
                                        case "setBlockData" -> {
                                            blocks.put(pos, (BlockData) args[0]);
                                            writes++;
                                            yield null;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected block call: "
                                                                + method.getName());
                                    });
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
