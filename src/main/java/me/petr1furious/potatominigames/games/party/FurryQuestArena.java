package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;

final class FurryQuestArena {
    static final int FLOOR = 70;
    private static final int EDGE = 19;
    private static final List<EntityType> SPECIES =
            List.of(EntityType.SHEEP, EntityType.COW, EntityType.PIG, EntityType.CHICKEN, EntityType.GOAT);

    private static final class Walker {
        final Animals body;
        Location destination;
        long nextWander;

        Walker(Animals body, int delay) {
            this.body = body;
            nextWander = delay;
        }
    }

    private final World world;
    private final Random random;
    private final Map<UUID, Walker> herd = new LinkedHashMap<>();

    FurryQuestArena(World world, Random random) {
        this.world = world;
        this.random = random;
    }

    void build() {
        for (int x = -EDGE; x <= EDGE; x++) {
            for (int z = -EDGE; z <= EDGE; z++) {
                block(x, 0, z, Material.GRASS_BLOCK);
                if (Math.abs(x) == EDGE || Math.abs(z) == EDGE) {
                    block(x, 1, z, Material.OAK_FENCE);
                    // Keep jumping goats inside without hiding the herd from the lookout.
                    for (int y = 2; y <= 12; y++) block(x, y, z, Material.BARRIER);
                }
            }
        }
        for (FurryQuest.Site site : FurryQuest.Site.values()) buildLandmark(site);
        // Keep the hidden controllers near the whole herd so every animal stays client-tracked.
        block(0, -3, 0, Material.STONE);
        for (int x = -9; x <= 9; x++) {
            block(x, 14, -EDGE, Material.SPRUCE_PLANKS);
            block(x, 14, EDGE, Material.SPRUCE_PLANKS);
        }
        for (int i = 0; i < 40; i++) {
            Animals body = (Animals) world.spawnEntity(meadow(), SPECIES.get(i % SPECIES.size()));
            body.setAdult();
            body.setAgeLock(true);
            body.setInvulnerable(true);
            body.setRemoveWhenFarAway(false);
            herd.put(body.getUniqueId(), new Walker(body, random.nextInt(100)));
        }
    }

    private void buildLandmark(FurryQuest.Site site) {
        int x = site.x, z = site.z;
        switch (site) {
            case POND -> {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        block(x + dx, -1, z + dz, Material.CLAY);
                        block(x + dx, 0, z + dz, Material.WATER);
                    }
                }
            }
            case HAY -> {
                block(x, 1, z, Material.HAY_BLOCK);
                block(x + 1, 1, z, Material.HAY_BLOCK);
                block(x, 2, z, Material.HAY_BLOCK);
            }
            case TREE -> {
                for (int y = 1; y <= 3; y++) block(x, y, z, Material.OAK_LOG);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) block(x + dx, 4, z + dz, Material.OAK_LEAVES);
                }
                block(x, 5, z, Material.OAK_LEAVES);
            }
            case TROUGH -> {
                for (int dx = -1; dx <= 1; dx++) block(x + dx, 1, z, Material.WATER_CAULDRON);
            }
            case BELL -> {
                block(x, 1, z, Material.STONE_BRICKS);
                block(x, 2, z, Material.BELL);
            }
        }
    }

    private void block(int x, int dy, int z, Material type) {
        world.getBlockAt(x, FLOOR + dy, z).setType(type, false);
    }

    Location lookout(int index, boolean sniper) {
        return new Location(world, sniper ? .5 : index - 5.5, FLOOR + 15,
                sniper ? EDGE + .02 : 1 - EDGE - .02, sniper ? 180 : 0, 36);
    }

    Location landmark(FurryQuest.Site site) {
        return new Location(world, site.x + .5, FLOOR + 1, site.z + .5);
    }

    Location controller() {
        return new Location(world, .5, FLOOR - 2, .5);
    }

    private boolean open(int x, int z) {
        return Math.abs(x) < EDGE && Math.abs(z) < EDGE
                && world.getBlockAt(x, FLOOR, z).getType() == Material.GRASS_BLOCK
                && world.getBlockAt(x, FLOOR + 1, z).isEmpty()
                && world.getBlockAt(x, FLOOR + 2, z).isEmpty();
    }

    /** Clicking a landmark selects a walkable square beside it, including the pond bank. */
    Location destination(Block clicked) {
        int x = clicked.getX(), z = clicked.getZ();
        if (Math.abs(x) >= EDGE || Math.abs(z) >= EDGE) return null;
        Location nearest = null;
        int best = Integer.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int distance = dx * dx + dz * dz;
                if (distance < best && open(x + dx, z + dz)) {
                    best = distance;
                    nearest = new Location(world, x + dx + .5, FLOOR + 1, z + dz + .5);
                }
            }
        }
        return nearest;
    }

    private Location meadow() {
        int x, z;
        do {
            x = random.nextInt(1 - EDGE, EDGE);
            z = random.nextInt(1 - EDGE, EDGE);
        } while (!open(x, z));
        return new Location(world, x + .5, FLOOR + 1, z + .5);
    }

    List<Animals> shuffledHerd() {
        var animals = new ArrayList<>(herd.values().stream().map(w -> w.body).toList());
        Collections.shuffle(animals, random);
        return animals;
    }

    boolean contains(UUID id) { return herd.containsKey(id); }

    private void walk(Walker walker, Location target, long tick) {
        walker.destination = target;
        walker.nextWander = tick + 600;
        walker.body.getPathfinder().moveTo(target, 1.0);
    }

    void step(Animals animal, float yaw, long tick) {
        Location from = animal.getLocation();
        double angle = Math.toRadians(yaw);
        Block ahead = from.add(-3 * Math.sin(angle), 0, 3 * Math.cos(angle)).getBlock();
        Location target = destination(ahead);
        if (target != null) walk(herd.get(animal.getUniqueId()), target, tick);
        else pause(animal, tick);
    }

    void pause(Animals animal, long tick) {
        Walker walker = herd.get(animal.getUniqueId());
        walker.destination = null;
        walker.nextWander = tick + 70;
        animal.getPathfinder().stopPathfinding();
    }

    void tick(long tick) {
        if (tick % 10 != 0) return;
        for (Walker walker : herd.values()) {
            if (walker.destination != null && walker.body.getLocation().distanceSquared(walker.destination) < 2.25) {
                walker.destination = null;
                walker.nextWander = tick + 60 + random.nextInt(100);
            }
            if (tick >= walker.nextWander) {
                // Livestock also visit the mission landmarks and pause between walks.
                Location target = meadow();
                if (random.nextInt(3) == 0) {
                    var sites = FurryQuest.Site.values();
                    var site = sites[random.nextInt(sites.length)];
                    target = destination(landmark(site).getBlock());
                }
                if (target != null) walk(walker, target, tick);
            } else if (walker.destination != null) {
                // A vanilla behaviour (such as grazing) may have interrupted the route.
                var path = walker.body.getPathfinder().getCurrentPath();
                if (path == null || path.getFinalPoint() == null
                        || path.getFinalPoint().distanceSquared(walker.destination) > 2.25)
                    walker.body.getPathfinder().moveTo(walker.destination, 1.0);
            }
        }
    }

    void remove(Animals animal) {
        herd.remove(animal.getUniqueId());
        animal.remove();
    }

    void cleanup() {
        herd.values().forEach(walker -> walker.body.remove());
        herd.clear();
    }
}
