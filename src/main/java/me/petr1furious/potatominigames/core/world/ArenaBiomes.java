package me.petr1furious.potatominigames.core.world;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.util.BiomeSearchResult;

/**
 * Keeps games that generate their own world from starting somewhere unplayable.
 *
 * <p>A fresh seed puts the origin in an arbitrary biome, and an ocean start (or a
 * desert with no wood, or badlands with no animals) ruins the opening minutes. If
 * the origin is unsuitable the arena shifts to the nearest biome that isn't —
 * cheaply, via the biome source, without generating any chunks.
 */
public final class ArenaBiomes {
    /** How far from the origin the arena may be moved to find decent ground. */
    public static final int SEARCH_RADIUS = 3000;

    /**
     * Biomes no game should start in. Oceans have no land at all; deserts have no
     * wood or surface water; badlands have effectively no grass or animals;
     * mushroom fields have neither normal passive mobs nor hostile ones.
     * Shorelines, rivers and the barren peaks are deliberately allowed.
     */
    private static final Set<Biome> UNSUITABLE = Set.of(
            Biome.OCEAN, Biome.DEEP_OCEAN,
            Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN,
            Biome.FROZEN_OCEAN, Biome.DEEP_FROZEN_OCEAN,
            Biome.LUKEWARM_OCEAN, Biome.DEEP_LUKEWARM_OCEAN,
            Biome.WARM_OCEAN,
            Biome.DESERT,
            Biome.BADLANDS, Biome.ERODED_BADLANDS, Biome.WOODED_BADLANDS,
            Biome.MUSHROOM_FIELDS,
            Biome.SWAMP, Biome.MANGROVE_SWAMP);

    /**
     * Biomes that exist in the registry but never as open sky in an overworld: the
     * search must not relocate to one, because the surface above a cave biome can
     * still be the ocean we were trying to escape.
     */
    private static final Set<Biome> NOT_OPEN_SKY = Set.of(
            Biome.DRIPSTONE_CAVES, Biome.LUSH_CAVES, Biome.DEEP_DARK,
            Biome.NETHER_WASTES, Biome.CRIMSON_FOREST, Biome.WARPED_FOREST,
            Biome.SOUL_SAND_VALLEY, Biome.BASALT_DELTAS,
            Biome.THE_END, Biome.END_BARRENS, Biome.END_HIGHLANDS,
            Biome.END_MIDLANDS, Biome.SMALL_END_ISLANDS,
            Biome.THE_VOID);

    private static Biome[] suitable;

    private ArenaBiomes() {
    }

    public static boolean isSuitable(Biome biome) {
        return !UNSUITABLE.contains(biome);
    }

    /** Every biome a game may start in, for feeding to the biome-source search. */
    private static Biome[] suitableBiomes() {
        if (suitable == null) {
            suitable = Registry.BIOME.stream()
                    .filter(biome -> !UNSUITABLE.contains(biome) && !NOT_OPEN_SKY.contains(biome))
                    .toArray(Biome[]::new);
        }
        return suitable;
    }

    /**
     * A playable spot near the given coordinates. Uses the biome source rather than
     * sampling loaded blocks, so scattering arenas around a fresh world costs no
     * chunk generation until players actually arrive.
     */
    public static Location findSuitableNear(World world, int x, int z, int radius) {
        Location around = new Location(world, x, world.getSeaLevel(), z);
        BiomeSearchResult found = world.locateNearestBiome(around, radius, suitableBiomes());
        return found == null ? around : found.getLocation();
    }

    /** Where the arena ended up, and what the origin looked like before deciding. */
    public record ArenaSpot(Location location, Biome biome, Biome originBiome, boolean relocated) {
        public String describe() {
            String where = location.getBlockX() + "," + location.getBlockZ() + " in "
                    + biome.getKey().getKey();
            return relocated
                    ? where + " (moved: origin was " + originBiome.getKey().getKey() + ")"
                    : where;
        }
    }

    /**
     * Where a game should build its arena: the origin when it is playable, else the
     * closest suitable biome within {@link #SEARCH_RADIUS}. Only X/Z are meaningful.
     * Staying within a few thousand blocks keeps strongholds — and therefore the
     * End — where vanilla would put them.
     */
    public static ArenaSpot findArenaCenter(World world) {
        Location origin = new Location(world, 0, world.getSeaLevel(), 0);
        Biome originBiome = world.getBiome(origin);
        if (isSuitable(originBiome)) {
            return new ArenaSpot(origin, originBiome, originBiome, false);
        }

        BiomeSearchResult found = world.locateNearestBiome(origin, SEARCH_RADIUS, suitableBiomes());
        if (found == null) {
            // Nothing playable within range; the origin is all we have.
            return new ArenaSpot(origin, originBiome, originBiome, false);
        }
        return new ArenaSpot(found.getLocation(), found.getBiome(), originBiome, true);
    }
}
