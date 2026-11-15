package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsMapData.*;

import com.infernalsuite.asp.api.loaders.SlimeLoader;

import me.petr1furious.potatominigames.core.world.SessionWorlds;
import me.petr1furious.potatominigames.core.world.SlimeWorldService;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A disposable template clone (or legacy snapshot), with protected setup anchors. */
final class FinalsImportedArena implements FinalsMapArena {
    private World world;
    private final Saved map;
    private final FinalsMapSnapshot snapshot;
    private FinalsMapSnapshot.Work buildWork;
    private org.bukkit.scheduler.BukkitTask buildTask;
    private Supplier<FinalsMapSnapshot.Work> migration;
    private SessionWorlds worlds;
    private SlimeLoader loader;
    private String templateName, cloneName;
    private SlimeWorldService.CloneWork cloneWork;
    private boolean migrationStarted;
    private List<BlockState> vaultBlocks;
    private int activeVault = -1;
    private int[] activeCashouts;
    private final Location[] stationPositions = new Location[2];
    private boolean built;

    FinalsImportedArena(World world, Saved map, Structure structure) {
        this(world, map, FinalsMapSnapshot.legacy(map.bounds(), structure));
    }

    FinalsImportedArena(World world, Saved map, FinalsMapSnapshot snapshot) {
        this.world = world;
        this.map = map;
        this.snapshot = snapshot;
    }

    FinalsImportedArena(
            World world,
            Saved map,
            FinalsMapSnapshot snapshot,
            Supplier<FinalsMapSnapshot.Work> migration) {
        this(world, map, snapshot);
        this.migration = migration;
    }

    FinalsImportedArena(
            Saved map,
            SessionWorlds worlds,
            SlimeLoader loader,
            String templateName,
            String cloneName) {
        this(null, map, (FinalsMapSnapshot) null);
        this.worlds = worlds;
        this.loader = loader;
        this.templateName = templateName;
        this.cloneName = cloneName;
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public void build() {
        if (built) return;
        cancelBuild();
        if (loader != null) {
            world = worlds.cloneSlime(loader, templateName, cloneName);
            validateWorld();
            finishBuild();
            return;
        }
        buildWork = snapshot.placement(null, world);
        try {
            buildWork.finish();
            if (migration != null) {
                FinalsMapSnapshot.Work conversion = migration.get();
                if (conversion != null) {
                    buildWork = conversion;
                    buildWork.finish();
                }
            }
            finishBuild();
        } finally {
            buildWork.cancel();
            buildWork = null;
        }
    }

    @Override
    public void buildAsync(
            JavaPlugin plugin, Runnable success, Consumer<RuntimeException> failure) {
        if (built) {
            success.run();
            return;
        }
        cancelBuild();
        if (loader != null) {
            cloneWork =
                    worlds.cloneSlimeAsync(
                            loader,
                            templateName,
                            cloneName,
                            loaded -> {
                                world = loaded;
                                validateWorld();
                                finishBuild();
                                success.run();
                            },
                            failure);
            return;
        }
        migrationStarted = false;
        buildWork = snapshot.placement(plugin, world);
        try {
            buildTask =
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                long deadline = System.nanoTime() + 4_000_000L;
                                do {
                                    if (buildWork.step()) {
                                        if (!migrationStarted && migration != null) {
                                            migrationStarted = true;
                                            FinalsMapSnapshot.Work conversion = migration.get();
                                            if (conversion != null) {
                                                buildWork = conversion;
                                                continue;
                                            }
                                        }
                                        cancelBuild();
                                        finishBuild();
                                        success.run();
                                        return;
                                    }
                                } while (!buildWork.waiting() && System.nanoTime() < deadline);
                            } catch (RuntimeException error) {
                                cancelBuild();
                                failure.accept(error);
                            }
                        }
                    }.runTaskTimer(plugin, 1, 1);
        } catch (RuntimeException error) {
            cancelBuild();
            throw error;
        }
    }

    @Override
    public void cancelBuild() {
        if (cloneWork != null) {
            cloneWork.cancel();
            cloneWork = null;
        }
        if (buildTask != null) {
            buildTask.cancel();
            buildTask = null;
        }
        if (buildWork != null) {
            buildWork.cancel();
            buildWork = null;
        }
    }

    private void validateWorld() {
        world.setGameRule(org.bukkit.GameRules.COMMAND_BLOCKS_WORK, false);
        world.setGameRule(org.bukkit.GameRules.SPAWNER_BLOCKS_WORK, false);
        if (map.bounds().minY() < world.getMinHeight()
                || map.bounds().maxY() >= world.getMaxHeight()) throw FinalsMaps.failure("height");
    }

    private void finishBuild() {
        for (JumpPad pad : map.jumpPads())
            world.getBlockAt(pad.origin().bx(), pad.origin().by() - 1, pad.origin().bz())
                    .setType(Material.SLIME_BLOCK, false);
        vaultBlocks =
                map.vaults().stream()
                        .map(point ->
                                world.getBlockAt(point.bx(), point.by(), point.bz()).getState())
                        .toList();
        activeCashouts =
                FinalsCashoutLocations.initial(map.cashouts().size(), ThreadLocalRandom.current());
        Point spawn = map.red().getFirst();
        world.setSpawnLocation(spawn.bx(), spawn.by(), spawn.bz());
        built = true;
        nextVault();
    }

    private void marker(Point point, Material material) {
        world.getBlockAt(point.bx(), point.by(), point.bz()).setType(material, false);
    }

    @Override
    public Location center() {
        Bounds b = map.bounds();
        return new Location(
                world,
                (b.minX() + (double) b.maxX() + 1) / 2,
                b.maxY() + 1,
                (b.minZ() + (double) b.maxZ() + 1) / 2);
    }

    @Override
    public double borderSize() {
        Bounds b = map.bounds();
        return Math.max(b.maxX() - b.minX() + 1, b.maxZ() - b.minZ() + 1);
    }

    @Override
    public Location spawn(int team, int memberIndex) {
        if (team < 0 || team > 1) throw new IllegalArgumentException("Team must be 0 or 1");
        List<Point> points = team == 0 ? map.red() : map.blue();
        return points.get(Math.floorMod(memberIndex, points.size())).at(world);
    }

    @Override
    public List<Location> spawnLocations(int team) {
        if (team < 0 || team > 1) throw new IllegalArgumentException("Team must be 0 or 1");
        return (team == 0 ? map.red() : map.blue()).stream().map(point -> point.at(world)).toList();
    }

    @Override
    public List<FinalsMapJumpPads.Pad> jumpPads() {
        return map.jumpPads().stream().map(p -> new FinalsMapJumpPads.Pad(
                p.origin().at(world), p.target() == null ? null : p.target().at(world))).toList();
    }

    @Override
    public Location vault() {
        if (activeVault < 0)
            throw new IllegalStateException("Build the arena before using its vault");
        return map.vaults().get(activeVault).at(world);
    }

    @Override
    public List<Location> vaultLocations() {
        return map.vaults().stream().map(point -> point.at(world)).toList();
    }

    @Override
    public void nextVault() {
        if (!built) throw new IllegalStateException("Build the arena before selecting its vault");
        int next =
                FinalsVaultLocations.nextIndex(
                        map.vaults().size(), activeVault, ThreadLocalRandom.current());
        if (next == activeVault) return;
        if (activeVault >= 0) vaultBlocks.get(activeVault).update(true, false);
        activeVault = next;
        marker(map.vaults().get(activeVault), Material.EMERALD_BLOCK);
    }

    @Override
    public Location station(int index) {
        if (index < 0 || index > 1) throw new IllegalArgumentException("Station must be 0 or 1");
        if (activeCashouts == null)
            throw new IllegalStateException("Build the arena before using its cashouts");
        if (stationPositions[index] != null) return stationPositions[index].clone();
        return map.cashouts().get(activeCashouts[index]).at(world);
    }

    @Override
    public void moveStation(int station, Location position) {
        if (station < 0 || station > 1)
            throw new IllegalArgumentException("Station must be 0 or 1");
        stationPositions[station] = position.clone();
    }

    @Override
    public List<Location> cashoutLocations() {
        return map.cashouts().stream().map(point -> point.at(world)).toList();
    }

    @Override
    public void nextCashout(int station) {
        if (station < 0 || station > 1)
            throw new IllegalArgumentException("Station must be 0 or 1");
        if (activeCashouts == null)
            throw new IllegalStateException("Build the arena before replacing its cashouts");
        int current = activeCashouts[station];
        int next =
                FinalsCashoutLocations.nextIndex(
                        map.cashouts().size(),
                        current,
                        activeCashouts[1 - station],
                        ThreadLocalRandom.current());
        activeCashouts[station] = next;
        stationPositions[station] = null;
    }

    @Override
    public boolean contains(Location location) {
        if (location == null
                || world == null
                || !world.equals(location.getWorld())
                || !Double.isFinite(location.getX())
                || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ())) return false;
        Bounds b = map.bounds();
        return location.getX() >= b.minX()
                && location.getX() < b.maxX() + 1d
                && location.getY() >= b.minY()
                && location.getY() < b.maxY() + 1d
                && location.getZ() >= b.minZ()
                && location.getZ() < b.maxZ() + 1d;
    }

    @Override
    public boolean isCover(Block block) {
        if (block == null || !contains(block.getLocation()) || block.getY() <= map.bounds().minY())
            return false;
        for (Point spawn : concat(map.red(), map.blue())) if (protects(block, spawn)) return false;
        for (JumpPad pad : map.jumpPads())
            if (block.getX() == pad.origin().bx() && block.getY() == pad.origin().by() - 1
                    && block.getZ() == pad.origin().bz()) return false;
        for (Point objective : map.vaults()) {
            if (protects(block, objective)) return false;
        }
        return switch (block.getType()) {
            case AIR, CAVE_AIR, VOID_AIR, WATER, LAVA, BEDROCK, BARRIER -> false;
            default -> true;
        };
    }

    private boolean protects(Block block, Point anchor) {
        return Math.abs(block.getX() - anchor.bx()) <= 1
                && Math.abs(block.getZ() - anchor.bz()) <= 1
                && block.getY() <= anchor.by() + 1;
    }

    @Override
    public int stationAt(Block block) {
        if (activeCashouts == null || block == null || !world.equals(block.getWorld())) return -1;
        for (int slot = 0; slot < 2; slot++) {
            Location point = station(slot);
            if (block.getX() == point.getBlockX()
                    && block.getY() == point.getBlockY()
                    && block.getZ() == point.getBlockZ()) return slot;
        }
        return -1;
    }

    @Override
    public boolean isVault(Block block) {
        return activeVault >= 0 && matches(block, map.vaults().get(activeVault));
    }

    private boolean matches(Block block, Point point) {
        return block != null
                && world.equals(block.getWorld())
                && block.getX() == point.bx()
                && block.getY() == point.by()
                && block.getZ() == point.bz();
    }
}
