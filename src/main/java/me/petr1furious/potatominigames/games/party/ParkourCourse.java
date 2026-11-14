package me.petr1furious.potatominigames.games.party;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Seven or eight shared, non-repeating native tasks sampled once from the full obstacle pool. */
final class ParkourCourse {
    static final int MIN_LENGTH = 7, MAX_LENGTH = 8;
    static final int WIDTH = 27, PITCH = 28, BOTTOM = 0, TOP = 256;
    static final int BUILD_BOTTOM = 8, BUILD_TOP = 248;
    static final int CLUTCH_SUPPORT_HEIGHT = 3;
    static final int HONEY_CATCH_HEIGHT = 6;
    static final int CHECKPOINT_DEPTH = 7, WATER_RUNUP = 12;
    static final int FLIGHT_ROCKET_POWER = 2;
    // White quartz and gold/emerald are reserved for actual checkpoint islands.
    static final Material OBSTACLE_BLOCK = Material.DEEPSLATE_TILES;

    record Pos(int x, int y, int z) {}

    record Island(int x, int z, int y, int width, int depth) {
        Island(int x, int z, int y, int width) {
            this(x, z, y, width, CHECKPOINT_DEPTH);
        }

        double centerX() {
            return x + width / 2.0;
        }

        boolean landing(double atX, double feetY, double atZ) {
            return atX >= x
                    && atX < x + width
                    && atZ >= z
                    && atZ < z + CHECKPOINT_DEPTH
                    && Math.abs(feetY - (y + 1)) < .3;
        }

        Island narrow() {
            return new Island((int) (centerX() - 1.5), z, y, 3, depth);
        }

        /** Keep the obstacle's original geometry at the far end of an extended dry deck. */
        Island obstacleStart() {
            return depth == CHECKPOINT_DEPTH
                    ? this
                    : new Island(x, z + depth - CHECKPOINT_DEPTH, y, width);
        }
    }

    record Supply(Material material, int count, String potion, String enchantment, int level) {
        Supply(Material material, int count) {
            this(material, count, null, null, 0);
        }
    }

    enum Family {
        BUILD,
        CLIMB,
        DROP,
        LAUNCH,
        PARKOUR,
        RIDE,
        WATER
    }

    enum Kind {
        BRIDGE("bridge", 18, 0, kit(Material.CYAN_WOOL, 22, Material.SHEARS, 1)),
        WIND_WALL("wind_wall", 2, 28, kit(Material.CYAN_WOOL, 36, Material.WIND_CHARGE, 8)),
        WATER_DROP("water_drop", 4, -44, kit(Material.WATER_BUCKET, 1)),
        CHEST_DROP("chest_drop", 4, -56, List.of()),
        SCAFFOLD("scaffold", 2, 24, kit(Material.SCAFFOLDING, 30)),
        LADDER_CLIMB("ladder_climb", 1, 20, kit(Material.LADDER, 24)),
        SLIME_BOUNCE("slime_bounce", 15, -20, kit(Material.SLIME_BLOCK, 4)),
        PEARL("pearl", 32, 0, kit(Material.ENDER_PEARL, 3)),
        SWIM("swim", 20, 0, kit(Material.WATER_BUCKET, 1)),
        LADDER_DROP("ladder_drop", 3, -36, kit(Material.LADDER, 8)),
        WEB_DROP("web_drop", 3, -28, kit(Material.COBWEB, 3, Material.SHEARS, 1)),
        FLIGHT("flight", 50, 64, kit(Material.ELYTRA, 1, Material.FIREWORK_ROCKET, 1)),
        LADDER_TRAVERSE("ladder_traverse", 20, 0, kit(Material.CYAN_WOOL, 12, Material.LADDER, 12)),
        BLOCK_ASCENT("block_ascent", 14, 14, kit(Material.CYAN_WOOL, 32, Material.SHEARS, 1)),
        POT_ASCENT("pot_ascent", 14, 14, kit(Material.CYAN_WOOL, 16, Material.FLOWER_POT, 18)),
        LOW_CEILING("low_ceiling", 20, 0, List.of()),
        RIPTIDE_LAUNCH("riptide_launch", 14, 0, enchanted(Material.TRIDENT, "riptide", 3)),
        WATER_ELEVATOR("water_elevator", 2, 20, kit(Material.WATER_BUCKET, 1)),
        POWDER_SNOW_DROP("powder_snow_drop", 3, -40, kit(Material.POWDER_SNOW_BUCKET, 1)),
        WIND_CHARGE_DROP("wind_charge_drop", 3, -44, kit(Material.WIND_CHARGE, 3)),
        HONEY_TRAVERSE("honey_traverse", 4, -36, List.of()),
        BOAT_WATER("boat_water", 32, 0, kit(Material.OAK_BOAT, 1)),
        BOAT_ICE("boat_ice", 80, 0, kit(Material.OAK_BOAT, 1)),
        WIND_HOPS("wind_hops", 14, 6, kit(Material.WIND_CHARGE, 4));

        final String id;
        final int gap, rise;
        final List<Supply> supplies;

        Kind(String id, int gap, int rise, List<Supply> supplies) {
            this.id = id;
            this.gap = gap;
            this.rise = rise;
            this.supplies = supplies;
        }

        Family family() {
            return switch (this) {
                case BRIDGE, BLOCK_ASCENT, POT_ASCENT -> Family.BUILD;
                case WIND_WALL, SCAFFOLD, LADDER_CLIMB, LADDER_TRAVERSE -> Family.CLIMB;
                case WATER_DROP,
                        CHEST_DROP,
                        LADDER_DROP,
                        WEB_DROP,
                        POWDER_SNOW_DROP,
                        WIND_CHARGE_DROP ->
                        Family.DROP;
                case SLIME_BOUNCE, PEARL, FLIGHT, RIPTIDE_LAUNCH, WIND_HOPS -> Family.LAUNCH;
                case BOAT_WATER, BOAT_ICE -> Family.RIDE;
                case SWIM, WATER_ELEVATOR -> Family.WATER;
                default -> Family.PARKOUR;
            };
        }

        boolean vehicle() {
            return this == BOAT_WATER || this == BOAT_ICE;
        }

        boolean wetStart() {
            return this == SWIM || this == RIPTIDE_LAUNCH || this == BOAT_WATER;
        }

        private static List<Supply> enchanted(Material material, String key, int level) {
            return List.of(new Supply(material, 1, null, key, level));
        }

        String key() {
            return "party.rounds.parkour_race.stage." + id;
        }

        private static List<Supply> kit(Material material, int count) {
            return List.of(new Supply(material, count));
        }

        private static List<Supply> kit(Material a, int n, Material b, int m) {
            return List.of(new Supply(a, n), new Supply(b, m));
        }
    }

    private final List<Kind> sequence;
    private final List<Island> islands;
    private final Map<Pos, Material> blocks;
    private final Map<Pos, String> states;

    ParkourCourse(Random random) {
        this(generate(random));
    }

    private ParkourCourse(ParkourCourse valid) {
        sequence = valid.sequence;
        islands = valid.islands;
        blocks = valid.blocks;
        states = valid.states;
    }

    private static final class InvalidLayout extends IllegalStateException {
        InvalidLayout(String message) {
            super(message);
        }
    }

    private static ParkourCourse generate(Random random) {
        int length = MIN_LENGTH + random.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
        for (int attempt = 0; attempt < 32; attempt++) {
            try {
                return new ParkourCourse(sample(random, length), random.nextBoolean());
            } catch (InvalidLayout invalid) {
                /* Reject blocked compositions before building a world. */
            }
        }
        return new ParkourCourse(
                List.of(
                                Kind.BRIDGE,
                                Kind.WIND_WALL,
                                Kind.WATER_DROP,
                                Kind.SWIM,
                                Kind.BLOCK_ASCENT,
                                Kind.PEARL,
                                Kind.LOW_CEILING,
                                Kind.BOAT_ICE)
                        .subList(0, length),
                false);
    }

    private static List<Kind> sample(Random random, int length) {
        List<Kind> pool = new ArrayList<>(List.of(Kind.values()));
        Collections.shuffle(pool, random);
        List<Kind> result = new ArrayList<>();
        var families = new java.util.EnumMap<Family, Integer>(Family.class);
        for (Kind kind : pool)
            if (families.size() < 4 && !families.containsKey(kind.family())) {
                result.add(kind);
                families.put(kind.family(), 1);
            }
        for (Kind kind : pool)
            if (result.size() < length
                    && !result.contains(kind)
                    && families.getOrDefault(kind.family(), 0) < 2) {
                result.add(kind);
                families.merge(kind.family(), 1, Integer::sum);
            }
        Collections.shuffle(result, random);
        return List.copyOf(result);
    }

    ParkourCourse(List<Kind> selected, boolean mirror) {
        if (selected == null
                || selected.size() < MIN_LENGTH
                || selected.size() > MAX_LENGTH
                || new HashSet<>(selected).size() != selected.size()
                || selected.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Seven or eight distinct tasks required");
        sequence = List.copyOf(selected);
        List<Island> relative = new ArrayList<>();
        final int anchor = mirror ? 20 : 0;
        relative.add(new Island(anchor, 0, 0, 7));
        for (Kind stage : sequence) {
            Island from = relative.getLast();
            if (stage.wetStart()) {
                from =
                        new Island(
                                from.x(),
                                from.z(),
                                from.y(),
                                from.width(),
                                CHECKPOINT_DEPTH + WATER_RUNUP);
                relative.set(relative.size() - 1, from);
            }
            if (stage == Kind.LADDER_TRAVERSE) {
                from = from.narrow();
                relative.set(relative.size() - 1, from);
            }
            Island to =
                    new Island(
                            anchor, from.z() + from.depth() + stage.gap, from.y() + stage.rise, 7);
            relative.add(stage == Kind.LADDER_TRAVERSE ? to.narrow() : to);
        }
        // Translate the whole sampled profile, rather than rejecting down-heavy random sequences.
        int lowest = relative.stream().mapToInt(island -> island.y() - 1).min().orElse(0);
        for (int i = 0; i < sequence.size(); i++)
            if (stage(i) == Kind.SLIME_BOUNCE) lowest = Math.min(lowest, relative.get(i).y() - 37);
        final int offset = 48 - lowest;
        islands =
                relative.stream()
                        .map(i -> new Island(i.x(), i.z(), i.y() + offset, i.width(), i.depth()))
                        .toList();
        Map<Pos, Material> plan = new LinkedHashMap<>();
        Map<Pos, String> data = new LinkedHashMap<>();
        for (int i = 0; i < islands.size(); i++) {
            Island island = islands.get(i);
            for (int x = island.x(); x < island.x() + island.width(); x++)
                for (int z = island.z(); z < island.z() + island.depth(); z++) {
                    plan.put(new Pos(x, island.y() - 1, z), Material.SMOOTH_QUARTZ);
                    plan.put(
                            new Pos(x, island.y(), z),
                            z == island.z() + 3
                                    ? (i == stages() ? Material.EMERALD_BLOCK : Material.GOLD_BLOCK)
                                    : Material.QUARTZ_BRICKS);
                }
        }
        for (int i = 0; i < stages(); i++) {
            Kind kind = stage(i);
            Island from = island(i).obstacleStart(), to = island(i + 1);
            switch (kind) {
                case WIND_WALL, SCAFFOLD, LADDER_CLIMB, WATER_ELEVATOR -> {
                    for (int x = 0; x < WIDTH; x++)
                        for (int y = BUILD_BOTTOM; y <= to.y(); y++)
                            plan.put(new Pos(x, y, to.z() - 1), OBSTACLE_BLOCK);
                }
                case SLIME_BOUNCE -> {
                    int center = (int) from.centerX();
                    for (int x = center - 2; x <= center + 2; x++)
                        for (int z = from.z() + 15; z <= from.z() + 20; z++) {
                            plan.put(new Pos(x, from.y() - 37, z), OBSTACLE_BLOCK);
                            plan.put(new Pos(x, from.y() - 36, z), Material.LIGHT_BLUE_CONCRETE);
                        }
                }
                case SWIM -> swimTunnel(plan, from, to);
                case CHEST_DROP -> {
                    Pos chest = dropChest(i);
                    plan.put(chest, Material.CHEST);
                    data.put(
                            chest,
                            "minecraft:chest[facing="
                                    + (chest.x() > from.centerX() ? "west" : "east")
                                    + ",type=single,waterlogged=false]");
                    // Glass permits native chest opening but leaves no headroom on its lid.
                    // The column's top is above the empty-handed player's jump height.
                    for (int y = chest.y() + 1; y <= from.y() + 3; y++)
                        plan.put(new Pos(chest.x(), y, chest.z()), Material.YELLOW_STAINED_GLASS);
                }
                case POT_ASCENT -> {
                    int center = (int) from.centerX();
                    for (int x = center - 1; x <= center + 1; x++)
                        for (int z = 0; z < 3; z++) {
                            plan.put(
                                    new Pos(x, from.y(), from.z() + 4 + z),
                                    Material.LIGHT_BLUE_CONCRETE);
                            plan.put(new Pos(x, to.y(), to.z() + z), Material.LIGHT_BLUE_CONCRETE);
                        }
                }
                case LADDER_DROP ->
                        clutchWall(plan, from, to, from.centerX() < WIDTH / 2.0 ? 0 : WIDTH - 1);
                default -> ParkourTasks.build(kind, from, to, plan);
            }
        }
        for (Pos pos : plan.keySet())
            if (pos.x() < 0 || pos.x() >= WIDTH || pos.y() < BUILD_BOTTOM || pos.y() >= TOP - 8)
                throw new InvalidLayout("Sampled course exceeds the sealed arena bounds");
        validateClearance(plan);
        blocks = Collections.unmodifiableMap(plan);
        states = Collections.unmodifiableMap(data);
    }

    private void swimTunnel(Map<Pos, Material> plan, Island from, Island to) {
        int center = (int) from.centerX();
        for (int z = from.z() + 7; z < to.z(); z++)
            for (int x = 0; x < WIDTH; x++) {
                plan.put(new Pos(x, from.y(), z), OBSTACLE_BLOCK);
                for (int y = from.y() + 1; y <= from.y() + 10; y++)
                    // Leave the passage absent from protected terrain so players can place,
                    // retrieve and move their own water; the attempt journal restores air.
                    if (Math.abs(x - center) > 1 || y != from.y() + 1)
                        plan.put(new Pos(x, y, z), OBSTACLE_BLOCK);
            }
        // Alternating one-block openings force lateral swimming; three clear rows between
        // baffles leave enough room to turn with the vanilla swimming hitbox.
        for (int gate = 0; gate < 4; gate++) {
            int z = from.z() + 10 + gate * 4;
            int opening = center + (gate % 2 == 0 ? -1 : 1);
            for (int x = center - 1; x <= center + 1; x++)
                if (x != opening)
                    plan.put(new Pos(x, from.y() + 1, z), Material.LIGHT_BLUE_CONCRETE);
        }
    }

    private void clutchWall(Map<Pos, Material> plan, Island from, Island to, int side) {
        // Only the final catch has an anchor; no staircase of catches down the whole fall.
        // Keep the whole destination island free, including the next task's departure.
        for (int z = from.z() + 4; z < to.z(); z++)
            for (int y = to.y(); y < to.y() + CLUTCH_SUPPORT_HEIGHT; y++)
                plan.put(
                        new Pos(side, y, z),
                        z == to.z() - 1 ? Material.LIGHT_BLUE_CONCRETE : OBSTACLE_BLOCK);
    }

    private void validateClearance(Map<Pos, Material> plan) {
        for (Island island : islands) {
            int x = (int) island.centerX(), z = island.z() + 3;
            if (!plan.containsKey(new Pos(x, island.y(), z)))
                throw new InvalidLayout("Missing checkpoint deck");
            bodyClear(plan, x, island.y() + 1, z);
        }
        for (int stage = 0; stage < stages(); stage++)
            if (stage(stage) == Kind.LADDER_TRAVERSE) {
                Island from = island(stage), to = island(stage + 1);
                for (int z = from.z() + 3; z <= to.z() + 3; z++)
                    bodyClear(plan, (int) from.centerX(), from.y() + 1, z);
            }
    }

    private static void bodyClear(Map<Pos, Material> plan, int x, int feet, int z) {
        for (int y = feet; y < feet + 2; y++) {
            Material material = plan.get(new Pos(x, y, z));
            if (material != null && material != Material.WATER)
                throw new InvalidLayout("Protected terrain blocks checkpoint or forward passage");
        }
    }

    int stages() {
        return sequence.size();
    }

    int end() {
        return islands.getLast().z() + islands.getLast().depth();
    }

    Island island(int checkpoint) {
        return islands.get(checkpoint);
    }

    Pos dropChest(int checkpoint) {
        if (checkpoint < 0 || checkpoint >= stages() || stage(checkpoint) != Kind.CHEST_DROP)
            return null;
        Island from = island(checkpoint), to = island(checkpoint + 1);
        int side = from.centerX() < WIDTH / 2.0 ? 1 : -1;
        // Six blocks beyond the take-off edge: outside grounded reach even while
        // overhanging the lip, but reachable after jumping forward toward the landing.
        return new Pos((int) from.centerX() + side * 2, from.y() + 1, to.z() + 2);
    }

    Kind stage(int checkpoint) {
        return sequence.get(checkpoint);
    }

    List<Kind> sequence() {
        return sequence;
    }

    Map<Pos, Material> blocks() {
        return blocks;
    }

    Map<Pos, String> states() {
        return states;
    }

    boolean attempt(int checkpoint, double x, double y, double z) {
        if (checkpoint < 0
                || checkpoint >= stages()
                || !Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)) return false;
        Island from = island(checkpoint), to = island(checkpoint + 1);
        return x >= 0
                && x < WIDTH
                && y >= BUILD_BOTTOM
                && y < TOP - 1
                && z >= from.z()
                // Only the landing pad is reachable with the old kit. The dry run-up and
                // water belong to the next task, after an actual checkpoint landing.
                && z < to.z() + CHECKPOINT_DEPTH;
    }

    double distance(int checkpoint, double x, double z) {
        Island from = island(checkpoint), to = island(checkpoint + 1);
        double dx = to.centerX() - from.centerX(),
                dz = to.z() - from.z(),
                length = Math.hypot(dx, dz);
        return Math.max(
                0,
                Math.min(length, ((x - from.centerX()) * dx + (z - from.z() - 3.5) * dz) / length));
    }

    boolean buildPosition(int checkpoint, Pos pos) {
        if (stage(checkpoint) == Kind.LADDER_DROP || stage(checkpoint) == Kind.CHEST_DROP) {
            // The full-height invisible lane shell must not substitute for the removed wall.
            int bottom = island(checkpoint + 1).y();
            return pos.y() >= bottom && pos.y() < bottom + CLUTCH_SUPPORT_HEIGHT;
        }
        if (stage(checkpoint) != Kind.POT_ASCENT) return true;
        // Keep the staircase away from free anchors on barriers and neighbouring task walls.
        int center = (int) island(checkpoint).centerX();
        return pos.x() >= center - 1 && pos.x() <= center + 1;
    }

    boolean recoverMaterials(int checkpoint) {
        return stage(checkpoint) != Kind.POT_ASCENT;
    }

    List<Supply> supplies(int checkpoint) {
        return checkpoint >= 0 && checkpoint < stages() ? stage(checkpoint).supplies : List.of();
    }
}
