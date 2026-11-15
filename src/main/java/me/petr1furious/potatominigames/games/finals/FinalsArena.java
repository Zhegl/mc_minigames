package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** A small mirrored city block; only the generated cover can be destroyed. */
public final class FinalsArena implements FinalsMapArena {
    public static final int FLOOR_Y = 64;
    public static final int HALF_SIZE = 24;
    private static final int WALL_TOP = FLOOR_Y + 16;
    private final World world;
    private final RandomGenerator random;
    private final Set<Position> cover = new HashSet<>();
    private static final int[] VAULT_X = {-10, 0, 10};
    private static final Position[] CASHOUTS = {
        new Position(0, FLOOR_Y + 1, -15),
        new Position(0, FLOOR_Y + 1, 15),
        new Position(-18, FLOOR_Y + 1, 16),
        new Position(18, FLOOR_Y + 1, -16)
    };
    private int activeVault = -1;
    private int[] activeCashouts;
    private final Location[] stationPositions = new Location[2];
    private boolean built;

    private record Position(int x, int y, int z) {
        static Position of(Block block) {
            return new Position(block.getX(), block.getY(), block.getZ());
        }
    }

    public FinalsArena(World world) {
        this(world, ThreadLocalRandom.current());
    }

    FinalsArena(World world, RandomGenerator random) {
        this.world = Objects.requireNonNull(world, "world");
        this.random = Objects.requireNonNull(random, "random");
    }

    public World world() {
        return world;
    }

    public double borderSize() {
        return HALF_SIZE * 2 + 1;
    }

    public void build() {
        if (built) return;
        cover.clear();
        for (int x = -HALF_SIZE; x <= HALF_SIZE; x++) {
            for (int z = -HALF_SIZE; z <= HALF_SIZE; z++) {
                set(x, FLOOR_Y - 1, z, Material.BEDROCK);
                boolean street = Math.abs(x) <= 3 || Math.abs(z) <= 3;
                Material paving = street ? Material.GRAY_CONCRETE : Material.STONE_BRICKS;
                if (street && ((x == 0 && Math.abs(z) % 4 < 2) || (z == 0 && Math.abs(x) % 4 < 2)))
                    paving = Material.WHITE_CONCRETE;
                set(x, FLOOR_Y, z, paving);
                if (Math.abs(x) == HALF_SIZE || Math.abs(z) == HALF_SIZE) {
                    for (int y = FLOOR_Y + 1; y <= WALL_TOP; y++) {
                        set(
                                x,
                                y,
                                z,
                                y <= FLOOR_Y + 2 ? Material.POLISHED_DEEPSLATE : Material.BARRIER);
                    }
                }
            }
        }

        for (int side : new int[] {-1, 1}) {
            Material teamColor = side < 0 ? Material.RED_CONCRETE : Material.BLUE_CONCRETE;
            for (int x = 19; x <= 21; x++) {
                for (int z = -8; z <= 8; z++) set(side * x, FLOOR_Y, z, teamColor);
            }
            for (int quarter : new int[] {-1, 1}) {
                building(side, quarter);
                for (int offset = -1; offset <= 1; offset++) {
                    cover(side * 6, FLOOR_Y + 1, quarter * 3 + offset, Material.SMOOTH_STONE);
                    cover(side * (10 + offset), FLOOR_Y + 1, quarter * 17, Material.CUT_SANDSTONE);
                    cover(side * (10 + offset), FLOOR_Y + 2, quarter * 17, Material.CUT_SANDSTONE);
                }
                // Street furniture breaks up the long outside lanes without blocking spawns.
                for (int y = 1; y <= 3; y++) cover(side * 20, FLOOR_Y + y, quarter * 12, teamColor);
                cover(side * 20, FLOOR_Y + 4, quarter * 12, Material.SEA_LANTERN);
            }
        }

        for (int x : VAULT_X) objective(x, 0, Material.AIR, Material.LIME_CONCRETE);
        for (Position point : CASHOUTS) {
            objective(point.x(), point.z(), Material.AIR, Material.YELLOW_CONCRETE);
            // The body can drop through its pad onto the protected bedrock below.
            cover.add(new Position(point.x(), FLOOR_Y, point.z()));
        }
        built = true;
        nextVault();
        activeCashouts = FinalsCashoutLocations.initial(CASHOUTS.length, random);
        // Keep fallback teleports clear of every possible vault marker.
        world.setSpawnLocation(0, FLOOR_Y + 1, 2);
    }

    private void building(int side, int quarter) {
        Material accent = quarter < 0 ? Material.CYAN_CONCRETE : Material.ORANGE_CONCRETE;
        for (int x = 7; x <= 13; x++) {
            for (int z = 6; z <= 12; z++) {
                for (int level = 1; level <= 8; level++) {
                    boolean deck = level == 4 || level == 8;
                    boolean edge = x == 7 || x == 13 || z == 6 || z == 12;
                    if (!deck && !edge) continue;
                    boolean doorway =
                            (level == 1 || level == 2)
                                    && ((x == 7 && (z == 8 || z == 9))
                                            || (z == 6 && (x == 9 || x == 10)));
                    // The fourth step opens directly onto the upper storey.
                    doorway |= x == 13 && z == 8 && (level == 5 || level == 6);
                    if (doorway) continue;
                    Material material = deck ? Material.SMOOTH_STONE : Material.WHITE_CONCRETE;
                    if (!deck && (level == 3 || level == 7)) material = accent;
                    if ((level == 2 || level == 6)
                            && ((x == 7 || x == 13) && (z == 7 || z == 11)
                                    || (z == 6 || z == 12) && (x == 8 || x == 12)))
                        material = Material.GLASS;
                    cover(side * x, FLOOR_Y + level, quarter * z, material);
                }
            }
        }
        // Two-wide exterior stairways connect the street, upper floor and roof.
        // Solid steps remain usable from both sides even after neighboring cover is removed.
        for (int step = 1; step <= 8; step++) {
            for (int x = 14; x <= 15; x++) {
                cover(side * x, FLOOR_Y + step, quarter * (step + 4), Material.POLISHED_ANDESITE);
            }
        }
        // Short roof corners give rooftop players cover while leaving the stair exit clear.
        for (int x = 7; x <= 9; x++) {
            cover(side * x, FLOOR_Y + 9, quarter * 6, accent);
            cover(side * x, FLOOR_Y + 9, quarter * 12, accent);
        }
    }

    private void objective(int x, int z, Material block, Material color) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(
                        x + dx,
                        FLOOR_Y,
                        z + dz,
                        Math.abs(dx) == 2 || Math.abs(dz) == 2
                                ? color
                                : Material.POLISHED_BLACKSTONE);
            }
        }
        set(x, FLOOR_Y + 1, z, block);
        for (int side : new int[] {-1, 1}) set(x + side * 2, FLOOR_Y, z, Material.SEA_LANTERN);
    }

    private void set(int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }

    private void cover(int x, int y, int z, Material material) {
        set(x, y, z, material);
        cover.add(new Position(x, y, z));
    }

    public Location center() {
        // Border placement uses this point too; remain above every possible central marker.
        return new Location(world, .5, FLOOR_Y + 2, .5);
    }

    public Location spawn(int team, int memberIndex) {
        if (team < 0 || team > 1) throw new IllegalArgumentException("Team must be 0 or 1");
        int side = team == 0 ? -1 : 1;
        return new Location(
                world,
                side * 20 + .5,
                FLOOR_Y + 1,
                Math.floorMod(memberIndex, 8) * 2 - 6.5,
                side < 0 ? -90 : 90,
                0);
    }

    @Override
    public List<Location> spawnLocations(int team) {
        return java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> spawn(team, index)).toList();
    }

    public Location vault() {
        if (activeVault < 0)
            throw new IllegalStateException("Build the arena before using its vault");
        return new Location(world, VAULT_X[activeVault] + .5, FLOOR_Y + 1, .5);
    }

    public List<Location> vaultLocations() {
        return java.util.Arrays.stream(VAULT_X)
                .mapToObj(x -> new Location(world, x + .5, FLOOR_Y + 1, .5))
                .toList();
    }

    public void nextVault() {
        if (!built) throw new IllegalStateException("Build the arena before selecting its vault");
        int next = FinalsVaultLocations.nextIndex(VAULT_X.length, activeVault, random);
        if (activeVault >= 0) set(VAULT_X[activeVault], FLOOR_Y + 1, 0, Material.AIR);
        activeVault = next;
        set(VAULT_X[activeVault], FLOOR_Y + 1, 0, Material.EMERALD_BLOCK);
    }

    public Location station(int station) {
        if (station < 0 || station > 1)
            throw new IllegalArgumentException("Station must be 0 or 1");
        if (activeCashouts == null)
            throw new IllegalStateException("Build the arena before using its cashouts");
        if (stationPositions[station] != null) return stationPositions[station].clone();
        Position point = CASHOUTS[activeCashouts[station]];
        return new Location(world, point.x() + .5, point.y(), point.z() + .5);
    }

    public void moveStation(int station, Location position) {
        if (station < 0 || station > 1) throw new IllegalArgumentException("Station must be 0 or 1");
        stationPositions[station] = position.clone();
    }

    public List<Location> cashoutLocations() {
        return java.util.Arrays.stream(CASHOUTS)
                .map(point -> new Location(world, point.x() + .5, point.y(), point.z() + .5))
                .toList();
    }

    public void nextCashout(int station) {
        if (station < 0 || station > 1)
            throw new IllegalArgumentException("Station must be 0 or 1");
        if (activeCashouts == null)
            throw new IllegalStateException("Build the arena before replacing its cashouts");
        int current = activeCashouts[station];
        int next =
                FinalsCashoutLocations.nextIndex(
                        CASHOUTS.length, current, activeCashouts[1 - station], random);
        activeCashouts[station] = next;
        stationPositions[station] = null;
    }

    public boolean contains(Location location) {
        return location != null
                && world.equals(location.getWorld())
                && location.getX() >= -HALF_SIZE + 1
                && location.getX() < HALF_SIZE
                && location.getZ() >= -HALF_SIZE + 1
                && location.getZ() < HALF_SIZE
                && location.getY() >= FLOOR_Y
                && location.getY() < WALL_TOP;
    }

    public boolean isCover(Block block) {
        if (block == null || !world.equals(block.getWorld()) || !cover.contains(Position.of(block)))
            return false;
        return switch (block.getType()) {
            case AIR, CAVE_AIR, VOID_AIR -> false;
            default -> true;
        };
    }

    public int stationAt(Block block) {
        if (block == null || !world.equals(block.getWorld()) || activeCashouts == null) return -1;
        for (int slot = 0; slot < 2; slot++) {
            Location point = station(slot);
            if (block.getX() == point.getBlockX() && block.getY() == point.getBlockY()
                    && block.getZ() == point.getBlockZ()) return slot;
        }
        return -1;
    }

    public boolean isVault(Block block) {
        return block != null
                && activeVault >= 0
                && world.equals(block.getWorld())
                && block.getX() == VAULT_X[activeVault]
                && block.getY() == FLOOR_Y + 1
                && block.getZ() == 0;
    }
}
