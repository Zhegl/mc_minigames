package me.petr1furious.potatominigames.core.world;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.util.TriState;

/**
 * Freshly generated worlds that exist only for the duration of one game and are
 * deleted afterwards, so a minigame never leaves a mark on a persistent map.
 *
 * <p>Every managed world is named {@code potato_tmp_<id>} (plus {@code _nether} /
 * {@code _the_end}); nothing outside that prefix is ever unloaded or deleted.
 * Leftovers from a crash are swept on startup.
 */
public final class ThrowawayWorldService {
    public static final String PREFIX = "potato_tmp_";

    private final JavaPlugin plugin;
    private final Random random = new Random();

    /** Base name -> the dimensions currently created for it. */
    private final Map<String, Map<World.Environment, World>> sets = new HashMap<>();
    /** World name -> base name, for resolving a world's sibling dimensions. */
    private final Map<String, String> worldToBase = new HashMap<>();
    /** Base name -> seed, so lazily created dimensions match their overworld. */
    private final Map<String, Long> seeds = new HashMap<>();

    public ThrowawayWorldService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String baseName(String id) {
        return PREFIX + id;
    }

    /**
     * Creates a fresh overworld (and eagerly the extra dimensions requested) with a
     * new random seed, replacing anything left over under the same name.
     */
    public World create(String id, World.Environment... eagerDimensions) {
        destroy(id);
        String base = baseName(id);
        long seed = random.nextLong();
        seeds.put(base, seed);

        World overworld = createDimension(base, World.Environment.NORMAL, seed);
        for (World.Environment environment : eagerDimensions) {
            if (environment != World.Environment.NORMAL) {
                createDimension(base, environment, seed);
            }
        }
        return overworld;
    }

    private World createDimension(String base, World.Environment environment, long seed) {
        String name = dimensionName(base, environment);
        WorldCreator creator = new WorldCreator(name)
                .environment(environment)
                .type(WorldType.NORMAL)
                .generateStructures(true)
                .seed(seed)
                // Spawn chunks are pointless for a world we throw away, and keeping
                // them loaded makes creation noticeably slower.
                .keepSpawnLoaded(TriState.FALSE);

        long startedAt = System.currentTimeMillis();
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Failed to create throwaway world " + name);
        }
        // Saving stays ON deliberately. A game can roam far past render distance, and
        // a chunk that is never written cannot be evicted either: it would either be
        // regenerated (losing the players' changes) or pinned in memory for the whole
        // game. Normal saving gives vanilla semantics; the folder delete at teardown
        // is what keeps the server clean.
        world.setDifficulty(Bukkit.getWorlds().getFirst().getDifficulty());
        sets.computeIfAbsent(base, key -> new HashMap<>()).put(environment, world);
        worldToBase.put(name, base);
        plugin.getLogger().info("Created throwaway world " + name + " (seed " + seed + ") in "
                + (System.currentTimeMillis() - startedAt) + "ms");
        return world;
    }

    private static String dimensionName(String base, World.Environment environment) {
        return switch (environment) {
            case NETHER -> base + "_nether";
            case THE_END -> base + "_the_end";
            default -> base;
        };
    }

    /** The base name of a managed world, or null if this world is not ours. */
    public String baseOf(World world) {
        return world == null ? null : worldToBase.get(world.getName());
    }

    public World dimension(String base, World.Environment environment) {
        Map<World.Environment, World> set = sets.get(base);
        return set == null ? null : set.get(environment);
    }

    /**
     * The requested dimension of a managed world set, generated on first use so a
     * game only pays for the dimensions its players actually visit.
     */
    public World getOrCreateDimension(String base, World.Environment environment) {
        World existing = dimension(base, environment);
        if (existing != null) {
            return existing;
        }
        Long seed = seeds.get(base);
        if (seed == null) {
            return null;
        }
        return createDimension(base, environment, seed);
    }

    /** Unloads (without saving) and deletes every dimension of one world set. */
    public void destroy(String id) {
        String base = baseName(id);
        Map<World.Environment, World> set = sets.remove(base);
        seeds.remove(base);
        if (set != null) {
            for (World world : set.values()) {
                worldToBase.remove(world.getName());
                unloadAndDelete(world);
            }
        }
        // Also sweep folders that exist without a loaded world (e.g. after a crash).
        deleteFoldersFor(base);
    }

    public void destroyAll() {
        for (String base : new LinkedHashSet<>(sets.keySet())) {
            destroy(base.substring(PREFIX.length()));
        }
    }

    private void unloadAndDelete(World world) {
        File folder = world.getWorldFolder();
        evacuate(world);
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("Could not unload throwaway world " + world.getName()
                    + "; leaving its folder for the next startup sweep");
            return;
        }
        deleteRecursively(folder.toPath());
    }

    /** Moves anyone still inside to the main world spawn so the world can unload. */
    private void evacuate(World world) {
        var fallback = Bukkit.getWorlds().getFirst().getSpawnLocation();
        for (Player player : world.getPlayers()) {
            player.teleport(fallback);
        }
    }

    /**
     * Finds throwaway world folders wherever the server actually keeps them.
     * The layout differs by version — Paper 1.21.9+ nests non-primary worlds at
     * {@code world/dimensions/<namespace>/<name>} while older builds put them
     * straight in the world container — so this searches by name rather than
     * assuming a path.
     */
    private List<Path> findThrowawayFolders() {
        Path container = Bukkit.getWorldContainer().toPath();
        try (var stream = Files.walk(container, 4)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(PREFIX))
                    .toList();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not scan for throwaway worlds: " + exception.getMessage());
            return List.of();
        }
    }

    /**
     * Deletes leftover throwaway worlds on startup. Only folders carrying our
     * prefix are touched.
     */
    public int cleanupStale() {
        int removed = 0;
        for (Path folder : findThrowawayFolders()) {
            World loaded = Bukkit.getWorld(folder.getFileName().toString());
            if (loaded != null) {
                evacuate(loaded);
                Bukkit.unloadWorld(loaded, false);
            }
            if (deleteRecursively(folder)) {
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " leftover throwaway world(s)");
        }
        return removed;
    }

    private void deleteFoldersFor(String base) {
        Set<String> names = Set.of(base, base + "_nether", base + "_the_end");
        for (Path folder : findThrowawayFolders()) {
            if (names.contains(folder.getFileName().toString())) {
                deleteRecursively(folder);
            }
        }
    }

    private boolean deleteRecursively(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        if (!path.getFileName().toString().startsWith(PREFIX)) {
            plugin.getLogger().warning("Refusing to delete non-throwaway folder " + path);
            return false;
        }
        try (var stream = Files.walk(path)) {
            List<Path> paths = new ArrayList<>(stream.sorted(Comparator.reverseOrder()).toList());
            for (Path entry : paths) {
                Files.deleteIfExists(entry);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to delete throwaway world folder " + path + ": "
                    + exception.getMessage());
            return false;
        }
    }

    public Set<String> managedBaseNames() {
        return Set.copyOf(sets.keySet());
    }
}
