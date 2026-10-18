package me.petr1furious.potatominigames.core.world;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.util.BiomeSearchResult;

/**
 * Moves a generated world's arena off unplayable biomes, using the biome source so no
 * chunks are generated.
 */
public final class ArenaBiomes {
    public static final int SEARCH_RADIUS = 3000;

    /** No land, wood, water or mobs. Shores, rivers and peaks are allowed. */
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

    /** Underground biomes: the surface above one can still be ocean. */
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

    private static Biome[] suitableBiomes() {
        if (suitable == null) {
            suitable = Registry.BIOME.stream()
                    .filter(biome -> !UNSUITABLE.contains(biome) && !NOT_OPEN_SKY.contains(biome))
                    .toArray(Biome[]::new);
        }
        return suitable;
    }

    public static Location findSuitableNear(World world, int x, int z, int radius) {
        Location around = new Location(world, x, world.getSeaLevel(), z);
        BiomeSearchResult found = world.locateNearestBiome(around, radius, suitableBiomes());
        return found == null ? around : found.getLocation();
    }

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
     * Origin if playable, else the closest suitable biome within {@link #SEARCH_RADIUS}.
     * Only X/Z are meaningful. A small radius keeps strongholds where vanilla expects.
     */
    public static ArenaSpot findArenaCenter(World world) {
        Location origin = new Location(world, 0, world.getSeaLevel(), 0);
        Biome originBiome = world.getBiome(origin);
        if (isSuitable(originBiome)) {
            return new ArenaSpot(origin, originBiome, originBiome, false);
        }

        BiomeSearchResult found = world.locateNearestBiome(origin, SEARCH_RADIUS, suitableBiomes());
        if (found == null) {
            return new ArenaSpot(origin, originBiome, originBiome, false);
        }
        return new ArenaSpot(found.getLocation(), found.getBiome(), originBiome, true);
    }
}
