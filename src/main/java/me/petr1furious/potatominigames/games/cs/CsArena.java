package me.petr1furious.potatominigames.games.cs;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** LesRaisins Dust II imported verbatim, with outer clipping and optional Wingman gates. */
final class CsArena {
    record Pos(int x, int y, int z) {}

    record Cell(Pos pos, int state) {}

    record Blueprint(List<String> palette, List<Cell> cells) {
        static Blueprint load() {
            try (var stream = CsArena.class.getResourceAsStream("/maps/cs/dust2.bin.gz")) {
                if (stream == null) throw new IOException("Missing imported Dust II");
                try (var in = new DataInputStream(new GZIPInputStream(stream))) {
                    if (!Arrays.equals(
                            in.readNBytes(8),
                            "CSMAP001".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                        throw new IOException("Invalid CS map signature");
                    int n = in.readInt();
                    if (n < 1 || n > 4096) throw new IOException("Invalid palette");
                    List<String> palette = new ArrayList<>();
                    for (int i = 0; i < n; i++) palette.add(in.readUTF());
                    n = in.readInt();
                    if (n < 1 || n > 500000) throw new IOException("Invalid block count");
                    List<Cell> cells = new ArrayList<>();
                    Set<Pos> seen = new HashSet<>();
                    for (int i = 0; i < n; i++) {
                        Pos p = new Pos(in.readShort(), in.readShort(), in.readShort());
                        int state = in.readUnsignedShort();
                        if (state >= palette.size()
                                || p.x < -25
                                || p.x > 130
                                || p.z < -34
                                || p.z > 119
                                || p.y < 73
                                || p.y > 109
                                || !seen.add(p)) throw new IOException("Invalid imported cell");
                        cells.add(new Cell(p, state));
                    }
                    if (in.read() != -1) throw new IOException("Unexpected map data");
                    return new Blueprint(List.copyOf(palette), List.copyOf(cells));
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load imported Dust II", e);
            }
        }
    }

    private static final class Source {
        static final Blueprint DATA = Blueprint.load();
    }

    record Spawn(double x, double y, double z, float yaw) {}

    // CS2's three T groups and CT arc, fitted to the older Minecraft map's walkable floors.
    // See assets/maps/lesraisins-dust2/SPAWNS-Dust2.md for the reference and adaptation.
    private static final List<List<Spawn>> SPAWNS =
            List.of(
                    List.of(
                            new Spawn(1.5, 82, 21.5, -90),
                            new Spawn(-.5, 82, 22.5, -90),
                            new Spawn(-1.5, 82, 24.5, -90),
                            new Spawn(-.5, 82, 25.5, -90),
                            new Spawn(1.5, 82, 26.5, -90),
                            new Spawn(1.5, 82, 27.5, -90),
                            new Spawn(-.5, 82, 28.5, -90),
                            new Spawn(-1.5, 82, 30.5, -90),
                            new Spawn(-.5, 82, 31.5, -90),
                            new Spawn(1.5, 82, 32.5, -90),
                            new Spawn(1.5, 82, 33.5, -90),
                            new Spawn(-.5, 82, 34.5, -90),
                            new Spawn(-1.5, 82, 36.5, -90),
                            new Spawn(-.5, 82, 37.5, -90),
                            new Spawn(1.5, 82, 38.5, -90)),
                    // Recessed spawn on the original slab floor, behind the CT through-passage.
                    List.of(
                            new Spawn(102.5, 75.5, 60.5, 90),
                            new Spawn(103.5, 75.5, 62.5, 90),
                            new Spawn(103.5, 75.5, 64.5, 90),
                            new Spawn(103.5, 75.5, 66.5, 90),
                            new Spawn(102.5, 75.5, 68.5, 90)));
    private static final org.bukkit.util.BoundingBox[] BUY_ZONES = {
        new org.bukkit.util.BoundingBox(-3, 81, 19, 4, 85, 40),
        new org.bukkit.util.BoundingBox(100, 75, 58, 105, 79, 71)
    };
    private final List<List<Spawn>> spawnOrder = new ArrayList<>();

    static List<Spawn> spawnPoints(int side) {
        return SPAWNS.get(side);
    }

    static boolean buyPosition(int side, double x, double y, double z) {
        return BUY_ZONES[side].contains(x, y, z);
    }

    void shuffleSpawns(Random random) {
        for (var points : spawnOrder) Collections.shuffle(points, random);
    }

    private final World world;
    private final Blueprint source = Source.DATA;
    private final List<BlockData> states;
    private final Map<Pos, BlockData> broken = new LinkedHashMap<>();
    private final List<Pos> clip = new ArrayList<>();
    private final Set<Pos> wingmanBorders;
    private final List<Pos> gates;
    private final boolean wingman;
    private int cursor;
    private boolean spawnsValidated;

    CsArena(World world) {
        this(world, false);
    }

    CsArena(World world, boolean wingman) {
        this.world = world;
        this.wingman = wingman;
        wingmanBorders = wingman ? WingmanBorders.DATA : Set.of();
        gates = List.copyOf(wingmanBorders);
        if (wingman) {
            for (var side : SPAWNS)
                for (var point : side)
                    if (!CsWingman.contains(point.x(), point.y(), point.z()))
                        throw new IllegalStateException("Team spawn outside Wingman area");
        }
        for (var points : SPAWNS) spawnOrder.add(new ArrayList<>(points));
        states = source.palette.stream().map(Bukkit::createBlockData).toList();
        for (int y = 72; y <= 128; y++) {
            for (int x = -26; x <= 131; x++) {
                clip.add(new Pos(x, y, -35));
                clip.add(new Pos(x, y, 120));
            }
            for (int z = -34; z <= 119; z++) {
                clip.add(new Pos(-26, y, z));
                clip.add(new Pos(131, y, z));
            }
        }
    }

    private static final class WingmanBorders {
        static final Set<Pos> DATA = CsWingman.borders();
    }

    boolean buildSlice(int budget) {
        int total = source.cells.size() + clip.size() + gates.size();
        for (int n = 0; n < budget && cursor < total; n++, cursor++) {
            if (cursor < source.cells.size()) {
                var cell = source.cells.get(cursor);
                var p = cell.pos;
                world.getBlockAt(p.x, p.y, p.z).setBlockData(states.get(cell.state), false);
            } else if (cursor < source.cells.size() + clip.size()) {
                var p = clip.get(cursor - source.cells.size());
                world.getBlockAt(p.x, p.y, p.z).setType(Material.BARRIER, false);
            } else {
                var p = gates.get(cursor - source.cells.size() - clip.size());
                Block block = world.getBlockAt(p.x, p.y, p.z);
                // Keep solid architecture, but seal air and passable decorations (signs/vines).
                if (block.isPassable())
                    block.setType(p.y <= 85 ? Material.RED_STAINED_GLASS : Material.BARRIER, false);
            }
        }
        if (cursor == total && wingman && !spawnsValidated) {
            for (var side : SPAWNS)
                for (var point : side)
                    if (spawnLocation(point) == null)
                        throw new IllegalStateException("Obstructed imported Wingman spawn");
            spawnsValidated = true;
        }
        return cursor == total;
    }

    float progress() {
        return (float) cursor / (source.cells.size() + clip.size() + gates.size());
    }

    Location spawn(int side, int index) {
        var points = spawnOrder.get(side);
        for (int n = 0; n < points.size(); n++) {
            Location at = spawnLocation(points.get(Math.floorMod(index + n, points.size())));
            if (at != null) return at;
        }
        throw new IllegalStateException("No safe imported team spawn");
    }

    private static final class DeathmatchSpawns {
        static final List<Spawn> POINTS = buildDeathmatchSpawns(Source.DATA);
    }

    static List<Spawn> deathmatchSpawns() {
        return DeathmatchSpawns.POINTS;
    }

    private static boolean supportedFloor(String state) {
        String name = state.split("\\[", 2)[0].substring("minecraft:".length());
        return Set.of(
                                "stone",
                                "sand",
                                "sandstone",
                                "smooth_sandstone",
                                "cut_sandstone",
                                "gravel",
                                "stone_bricks",
                                "smooth_stone",
                                "cobblestone",
                                "andesite",
                                "diorite",
                                "granite",
                                "polished_andesite",
                                "polished_diorite",
                                "polished_granite")
                        .contains(name)
                || name.endsWith("_concrete")
                || name.endsWith("_terracotta")
                || name.endsWith("_planks")
                || name.endsWith("_slab")
                || name.endsWith("_stairs");
    }

    private static List<Spawn> buildDeathmatchSpawns(Blueprint source) {
        Map<Pos, String> blocks = new HashMap<>();
        for (var cell : source.cells()) blocks.put(cell.pos(), source.palette().get(cell.state()));
        boolean[] full = new boolean[source.palette().size()];
        for (int i = 0; i < full.length; i++) full[i] = supportedFloor(source.palette().get(i));
        Set<Pos> floors = new HashSet<>();
        for (var cell : source.cells()) {
            Pos floor = cell.pos();
            Pos feet = new Pos(floor.x(), floor.y() + 1, floor.z());
            if (feet.y() >= 74
                    && feet.y() <= 89
                    && full[cell.state()]
                    && !blocks.containsKey(feet)
                    && !blocks.containsKey(new Pos(feet.x(), feet.y() + 1, feet.z())))
                floors.add(feet);
        }
        ArrayDeque<Pos> pending = new ArrayDeque<>();
        for (var side : SPAWNS)
            for (var point : side)
                pending.add(
                        new Pos(
                                (int) Math.floor(point.x()),
                                (int) Math.ceil(point.y()),
                                (int) Math.floor(point.z())));
        // Both original bomb platforms are known walkable map regions.
        pending.add(new Pos(103, 82, 88));
        pending.add(new Pos(114, 79, 6));
        for (Pos feet : floors)
            if (feet.y() == 82
                            && feet.x() >= 97
                            && feet.x() <= 111
                            && feet.z() >= 82
                            && feet.z() <= 96
                    || feet.y() == 79
                            && feet.x() >= 107
                            && feet.x() <= 122
                            && feet.z() >= 0
                            && feet.z() <= 14) pending.add(feet);
        Set<Pos> connected = new HashSet<>();
        while (!pending.isEmpty()) {
            Pos p = pending.removeFirst();
            if (!floors.contains(p) || !connected.add(p)) continue;
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                for (int dy = 1; dy >= -3; dy--) {
                    Pos next = new Pos(p.x() + step[0], p.y() + dy, p.z() + step[1]);
                    if (floors.contains(next)
                            && !connected.contains(next)
                            && walkStepClear(blocks.keySet(), p, next)) pending.add(next);
                }
        }
        // Imported streets are hollow: a floor plus headroom also matches the foundation
        // beneath them. Only spawn on exposed upper surfaces, never in those cavities.
        Map<Pos, Integer> tops = new HashMap<>();
        for (Pos p : blocks.keySet()) tops.merge(new Pos(p.x(), 0, p.z()), p.y(), Math::max);
        List<Spawn> result = new ArrayList<>();
        for (Pos p :
                connected.stream()
                        .sorted(
                                Comparator.comparingInt(Pos::x)
                                        .thenComparingInt(Pos::z)
                                        .thenComparingInt(Pos::y))
                        .toList())
            if (p.y() >= 79
                    && tops.get(new Pos(p.x(), 0, p.z())) == p.y() - 1
                    && Math.floorMod(p.x(), 6) == 0
                    && Math.floorMod(p.z(), 6) == 0)
                result.add(new Spawn(p.x() + .5, p.y(), p.z() + .5, 0));
        // Curated team starts also include the legitimate covered CT underpass.
        // This does not admit arbitrary foundation cavities into distributed sampling.
        for (var side : SPAWNS)
            for (var p : side)
                if (result.stream().noneMatch(other -> other.x() == p.x() && other.z() == p.z()))
                    result.add(p);
        return List.copyOf(result);
    }

    static boolean walkStepClear(Set<Pos> blocks, Pos from, Pos to) {
        int rise = to.y() - from.y();
        if (Math.abs(from.x() - to.x()) + Math.abs(from.z() - to.z()) != 1 || rise < -3 || rise > 1)
            return false;
        if (rise > 0 && blocks.contains(new Pos(from.x(), from.y() + 2, from.z()))) return false;
        // A three-block descent must not jump through a thin deck between the endpoints.
        for (int y = to.y(); y <= Math.max(from.y(), to.y()) + 1; y++)
            if (blocks.contains(new Pos(to.x(), y, to.z()))) return false;
        return true;
    }

    private Location spawnLocation(Spawn point) {
        if (wingman && !CsWingman.contains(point.x(), point.y(), point.z())) return null;
        Location at = new Location(world, point.x(), point.y(), point.z(), point.yaw(), 0);
        // A bottom slab shares the feet's block while its upper half is clear.
        boolean slabFloor = at.getY() - at.getBlockY() >= .5
                && at.getBlock().getBlockData() instanceof Slab slab
                && slab.getType() == Slab.Type.BOTTOM;
        if ((!at.getBlock().isPassable() && !slabFloor)
                || !at.clone().add(0, 1, 0).getBlock().isPassable()
                || !at.clone().add(0, 1.79, 0).getBlock().isPassable()
                || at.clone().subtract(0, .05, 0).getBlock().isPassable()) return null;
        return at;
    }

    Location deathmatchSpawn(Collection<Location> opponents, Random random) {
        List<Spawn> points = new ArrayList<>(deathmatchSpawns());
        Collections.shuffle(points, random);
        Location best = null;
        double bestDistance = -1;
        for (var point : points) {
            Location at = spawnLocation(point);
            if (at == null) continue;
            double distance = Double.POSITIVE_INFINITY;
            for (var opponent : opponents) {
                double dx = point.x() - opponent.getX(), dz = point.z() - opponent.getZ();
                distance = Math.min(distance, dx * dx + dz * dz);
            }
            if (distance > bestDistance) {
                bestDistance = distance;
                best = at;
            }
        }
        if (best == null) throw new IllegalStateException("No safe deathmatch spawn");
        best.setDirection(center().toVector().subtract(best.toVector()).setY(0));
        return best;
    }

    Location center() {
        return new Location(world, 52, 85, 43);
    }

    Location spectator() {
        return new Location(world, 52, 115, 43, 0, 70);
    }

    boolean inside(Location at) {
        return at != null
                && world.equals(at.getWorld())
                && at.getX() > -25.7
                && at.getX() < 131
                && at.getZ() > -34.7
                && at.getZ() < 120
                && at.getY() > 72
                && at.getY() < 128
                && !outsideWingman(at);
    }

    boolean outsideWingman(Location at) {
        // Preserve Classic void/out-of-world handling rather than creating a safety floor.
        return wingman
                && at != null
                && world.equals(at.getWorld())
                && at.getY() > 72
                && at.getY() < 128
                && !CsWingman.contains(at.getX(), at.getY(), at.getZ());
    }

    boolean wingmanBoundary(Block block) {
        return wingman
                && world.equals(block.getWorld())
                && wingmanBorders.contains(new Pos(block.getX(), block.getY(), block.getZ()));
    }

    boolean buyZone(Location at, int side) {
        return at != null
                && world.equals(at.getWorld())
                && buyPosition(side, at.getX(), at.getY(), at.getZ());
    }

    int site(Location at) {
        if (at == null || !world.equals(at.getWorld())) return -1;
        if (at.getX() >= 97
                && at.getX() <= 111
                && at.getZ() >= 82
                && at.getZ() <= 96
                && at.getY() >= 81
                && at.getY() <= 88) return 0;
        if (!wingman
                && at.getX() >= 107
                && at.getX() <= 122
                && at.getZ() >= 0
                && at.getZ() <= 14
                && at.getY() >= 78
                && at.getY() <= 85) return 1;
        return -1;
    }

    void breakGlass(Block block) {
        String name = block.getType().name();
        if (wingmanBoundary(block)
                || !inside(block.getLocation())
                || !(name.contains("GLASS") || name.contains("FLOWER_POT"))) return;
        var p = new Pos(block.getX(), block.getY(), block.getZ());
        broken.putIfAbsent(p, block.getBlockData().clone());
        block.setType(Material.AIR, false);
    }

    void reset() {
        for (var e : broken.entrySet()) {
            var p = e.getKey();
            world.getBlockAt(p.x, p.y, p.z).setBlockData(e.getValue(), false);
        }
        broken.clear();
    }
}
