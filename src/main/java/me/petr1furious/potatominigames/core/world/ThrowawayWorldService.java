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
import org.bukkit.generator.ChunkGenerator;

import net.kyori.adventure.util.TriState;

/**
 * Per-game generated worlds, deleted afterwards. Only worlds prefixed {@code potato_tmp_}
 * are ever unloaded or deleted; crash leftovers are swept on startup.
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

    /** New random seed; replaces any leftover world of the same name. */
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

    /** Empty disposable world for generated arenas; uses the same crash cleanup as survival games. */
    public World createVoid(String id) {
        destroy(id);
        String base = baseName(id);
        long seed = random.nextLong();
        seeds.put(base, seed);
        World world = Bukkit.createWorld(new WorldCreator(base).seed(seed).generateStructures(false)
                .generator(new ChunkGenerator() {
                    @Override
                    public org.bukkit.Location getFixedSpawnLocation(World world, Random random) {
                        return new org.bukkit.Location(world, .5, 81, .5);
                    }
                }));
        if (world == null) {
            throw new IllegalStateException("Failed to create arena world " + base);
        }
        sets.computeIfAbsent(base, key -> new HashMap<>()).put(World.Environment.NORMAL, world);
        worldToBase.put(base, base);
        return world;
    }

    private World createDimension(String base, World.Environment environment, long seed) {
        String name = dimensionName(base, environment);
        WorldCreator creator = new WorldCreator(name)
                .environment(environment)
                .type(WorldType.NORMAL)
                .generateStructures(true)
                .seed(seed)
                // Keeping spawn chunks loaded slows creation.
                .keepSpawnLoaded(TriState.FALSE);

        long startedAt = System.currentTimeMillis();
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Failed to create throwaway world " + name);
        }
        // Saving stays on: unsaved chunks cannot be evicted, so they would be pinned
        // in memory or regenerated without the players' changes.
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

    /** Null if no world set exists for this id. */
    public Long seed(String id) {
        return seeds.get(baseName(id));
    }

    /** Null if the world is not managed. */
    public String baseOf(World world) {
        return world == null ? null : worldToBase.get(world.getName());
    }

    public World dimension(String base, World.Environment environment) {
        Map<World.Environment, World> set = sets.get(base);
        return set == null ? null : set.get(environment);
    }

    /** Generated on first use. */
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

    /** Dead players cannot be teleported, so they are respawned first. */
    private void evacuate(World world) {
        var fallback = Bukkit.getWorlds().getFirst().getSpawnLocation();
        for (Player player : world.getPlayers()) {
            if (player.isDead()) {
                player.spigot().respawn();
            }
            player.teleport(fallback);
        }
    }

    /** Searches by name: Paper 1.21.9+ nests worlds under {@code world/dimensions/<ns>/}. */
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

    /** Deletes leftover throwaway worlds on startup. */
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
