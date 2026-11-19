package me.petr1furious.potatominigames.games.party;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

final class FurryQuestArena {
    static final int FLOOR = 70;
    private static final int EDGE = 23;
    static final List<EntityType> SPECIES =
            List.of(EntityType.SHEEP, EntityType.COW, EntityType.PIG, EntityType.CHICKEN, EntityType.GOAT);
    enum Site {
        SURVEY_FLAG(4, 19, Material.RED_WOOL),
        FILE_RED(4, 9, Material.RED_CONCRETE),
        FILE_BLUE(6, 9, Material.BLUE_CONCRETE),
        FILE_YELLOW(5, 10, Material.YELLOW_CONCRETE),
        SAW(-10, -6, Material.STONECUTTER),
        SHELF(-13, 6, Material.BOOKSHELF),
        SIGNPOST(6, 6, Material.CUT_SANDSTONE),
        GRIND(13, 10, Material.GRINDSTONE),
        WELL(5, -9, Material.WATER_CAULDRON),
        KNEAD(6, -16, Material.CRAFTING_TABLE),
        OVEN(10, -16, Material.FURNACE),
        MARKET(1, -6, Material.YELLOW_WOOL),
        WIREBOX(8, -12, Material.COPPER_BLOCK),
        SWITCH(14, -8, Material.LEVER),
        ENGINE(17, -4, Material.PISTON),
        BEACON(17, 4, Material.SEA_LANTERN),
        CRATE(-18, 5, Material.BARREL),
        ARCHIVE(-6, 8, Material.CHISELED_BOOKSHELF),
        TRACK(-10, -9, Material.MUD),
        FLAG(-14, -14, Material.RED_WOOL),
        DIRT_A(-13, -14, Material.COARSE_DIRT),
        DESK(0, 9, Material.CARTOGRAPHY_TABLE),
        MAIL(5, 9, Material.BARREL),
        DYE(9, -10, Material.CAULDRON),
        DIRT_B(-18, -13, Material.ROOTED_DIRT),
        DROP(-20, -10, Material.BARREL),
        BOARD(2, -7, Material.BIRCH_PLANKS),
        DIRT_C(4, -12, Material.COARSE_DIRT),
        TACKLE(-6, -14, Material.BARREL),
        SMOKER(5, -17, Material.SMOKER),
        TABLE(3, -4, Material.BIRCH_PLANKS),
        FORD(-4, -12, Material.SMOOTH_STONE),
        STUMP_W(-14, -5, Material.OAK_LOG),
        STUMP_E(-12, -5, Material.OAK_LOG),
        COAL(-14, 2, Material.COAL_BLOCK),
        BENCH(-9, 2, Material.CRAFTING_TABLE),
        LAMP(2, 12, Material.CHISELED_STONE_BRICKS),
        POST_A(8, 6, Material.CUT_SANDSTONE),
        POST_B(12, 6, Material.CUT_SANDSTONE),
        PARCEL(-16, 12, Material.BARREL),
        LOOM(12, -10, Material.LOOM),
        WHEAT(9, -3, Material.WHEAT),
        SOIL(12, -3, Material.FARMLAND),
        POND(0, -13, Material.WATER),
        GEAR(12, -13, Material.SMITHING_TABLE),
        AXLE(16, 14, Material.LODESTONE),
        LOW(-5, 4, Material.NOTE_BLOCK),
        MID(-3, 4, Material.NOTE_BLOCK),
        HIGH(-1, 4, Material.NOTE_BLOCK);
        final int x, z;
        final Material material;
        Site(int x, int z, Material material) {
            this.x = x;
            this.z = z;
            this.material = material;
        }
    }

    private final World world;
    private final Set<UUID> wild = new HashSet<>();
    private final Random random;
    private record Repair(Material original, long at) {}
    private final Map<Block, Repair> repairs = new HashMap<>();
    private final List<Block> caches = new ArrayList<>();
    private Block liveCache;

    FurryQuestArena(World world, Random random) {
        this.world = world;
        this.random = random;
    }
    Block base(int x, int z) {
        return world.getBlockAt(x, FLOOR, z);
    }
    Block block(Site site) {
        int y = switch (site) {
            case AXLE -> FLOOR + 5;
            case SIGNPOST, POST_A, POST_B, LAMP, POND, SOIL -> FLOOR;
            default -> FLOOR + 1;
        };
        return world.getBlockAt(site.x, y, site.z);
    }

    void build() {
        for (int x = -EDGE; x <= EDGE; x++)
            for (int z = -EDGE; z <= EDGE; z++) {
                boolean edge = Math.abs(x) == EDGE || Math.abs(z) == EDGE;
                base(x, z).setType(
                        Math.abs(x) <= 1 || Math.abs(z) <= 1 ? Material.DIRT_PATH : Material.GRASS_BLOCK, false);
                if (edge) {
                    base(x, z).getRelative(0, 1, 0).setType(Material.OAK_FENCE, false);
                    for (int h = 2; h <= 6; h++) base(x, z).getRelative(0, h, 0).setType(Material.BARRIER, false);
                }
            }
        // Open-front barn around the delivery barrel.
        for (int x = -20; x <= -12; x++)
            for (int z = 9; z <= 16; z++) {
                base(x, z).setType(Material.SPRUCE_PLANKS, false);
                for (int h = 1; h <= 4; h++) {
                    if (x == -20 || z == 16 || (x == -12 && z > 12))
                        base(x, z).getRelative(0, h, 0).setType(Material.RED_TERRACOTTA, false);
                }
                base(x, z)
                        .getRelative(0, 5 + (4 - Math.abs(x + 16)) / 2, 0)
                        .setType(Material.DARK_OAK_PLANKS, false);
            }
        for (int x = -3; x <= 3; x++)
            for (int z = -16; z <= -12; z++) {
                base(x, z).getRelative(0, -1, 0).setType(Material.CLAY, false);
                base(x, z).setType(Material.WATER, false);
            }
        for (int x = 8; x <= 13; x++)
            for (int z = -5; z <= -3; z++) {
                base(x, z).setType(Material.FARMLAND, false);
                if (x != 12)
                    mature(base(x, z).getRelative(0, 1, 0));
            }
        base(10, -4).setType(Material.WATER, false);
        base(10, -4).getRelative(0, 1, 0).setType(Material.AIR, false);
        // Climb the hay bales to reach the axle on the mill platform.
        for (int h = 1; h <= 4; h++) {
            for (int y = 1; y <= h; y++) base(10 + h, 14).getRelative(0, y, 0).setType(Material.HAY_BLOCK, false);
        }
        for (int x = 15; x <= 17; x++)
            for (int z = 13; z <= 15; z++) {
                for (int h = 1; h <= 4; h++)
                    base(x, z).getRelative(0, h, 0).setType(Material.SPRUCE_PLANKS, false);
            }
        for (int h = 6; h <= 9; h++) base(16, 15).getRelative(0, h, 0).setType(Material.STRIPPED_OAK_LOG, false);
        for (int offset = -2; offset <= 2; offset++) {
            base(16 + offset, 15).getRelative(0, 9, 0).setType(Material.WHITE_WOOL, false);
            base(16, 15).getRelative(0, 9 + offset, 0).setType(Material.WHITE_WOOL, false);
        }
        for (Site s : Site.values()) block(s).setType(s.material, false);
        var lever = (org.bukkit.block.data.type.Switch) block(Site.SWITCH).getBlockData();
        lever.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.FLOOR);
        block(Site.SWITCH).setBlockData(lever, false);
        mature(block(Site.WHEAT));
        for (int x : new int[] {7, 9, 10, 11, 13}) base(x, 6).getRelative(0, 1, 0).setType(Material.OAK_FENCE);
        base(Site.LOW.x, Site.LOW.z).setType(Material.RED_CONCRETE, false);
        base(Site.MID.x, Site.MID.z).setType(Material.BLUE_CONCRETE, false);
        base(Site.HIGH.x, Site.HIGH.z).setType(Material.YELLOW_CONCRETE, false);
        for (int[] pad : new int[][] {{-9, -11}, {16, 1}, {0, 17}, {-17, -1}}) {
            Block b = base(pad[0], pad[1]);
            b.setType(Material.GOLD_BLOCK, false);
            caches.add(b.getRelative(0, 1, 0));
        }
        for (int[] stack : new int[][] {{-17, 15}, {-14, 15}, {17, -12}, {-17, -8}}) {
            Block foot = base(stack[0], stack[1]);
            foot.getRelative(0, 1, 0).setType(Material.HAY_BLOCK, false);
            foot.getRelative(1, 1, 0).setType(Material.HAY_BLOCK, false);
            foot.getRelative(0, 2, 0).setType(Material.HAY_BLOCK, false);
        }
        for (int i = 0; i < 40; i++) spawnWild(SPECIES.get(i % SPECIES.size()));
    }

    private static void mature(Block b) {
        b.setType(Material.WHEAT, false);
        Ageable crop = (Ageable) b.getBlockData();
        crop.setAge(crop.getMaximumAge());
        b.setBlockData(crop, false);
    }

    Location spawn(int index, int count) {
        double angle = 2 * Math.PI * (index + .5) / count;
        return new Location(world, .5 + 6 * Math.sin(angle), FLOOR + 1, .5 - 6 * Math.cos(angle));
    }

    Location meadow() {
        for (int i = 0; i < 100; i++) {
            int x = random.nextInt(-21, 22), z = random.nextInt(-21, 22);
            Block ground = base(x, z);
            if (ground.getType() == Material.GRASS_BLOCK && ground.getRelative(0, 1, 0).isEmpty()
                    && ground.getRelative(0, 2, 0).isEmpty())
                return ground.getLocation().add(.5, 1, .5);
        }
        return spawn(0, 1);
    }

    EntityType roll(EntityType previous) {
        var options = new ArrayList<>(SPECIES);
        options.remove(previous);
        return options.get(random.nextInt(options.size()));
    }

    Animals animal(Location at, EntityType type, boolean ai) {
        Animals animal = (Animals) world.spawnEntity(at, type);
        animal.setAdult();
        animal.setAgeLock(true);
        animal.setAI(ai);
        animal.setGravity(ai);
        animal.setSilent(true);
        animal.setCollidable(false);
        animal.setInvulnerable(true);
        animal.setPersistent(false);
        animal.setRemoveWhenFarAway(false);
        if (animal instanceof Sheep sheep)
            sheep.setColor(DyeColor.WHITE);
        return animal;
    }

    private void spawnWild(EntityType type) {
        wild.add(animal(meadow(), type, true).getUniqueId());
    }
    boolean wild(Animals animal) {
        return wild.contains(animal.getUniqueId());
    }
    void replaceWild(Animals animal) {
        wild.remove(animal.getUniqueId());
        animal.remove();
        spawnWild(animal.getType());
    }

    void temporary(Block block, Material material, long tick) {
        Repair prior = repairs.get(block);
        repairs.put(block, new Repair(prior == null ? block.getType() : prior.original(), tick + 45));
        block.setType(material, false);
    }

    void tick(long tick) {
        repairs.entrySet().removeIf(entry -> {
            if (tick < entry.getValue().at())
                return false;
            Block b = entry.getKey();
            Material original = entry.getValue().original();
            if (b.equals(block(Site.WHEAT)))
                mature(b);
            else {
                b.setType(original, false);
            }
            return true;
        });
        if (tick % 300 == 0) {
            Block previous = liveCache;
            if (previous != null)
                previous.setType(Material.AIR, false);
            var options = new ArrayList<>(caches);
            options.remove(previous);
            liveCache = options.get(random.nextInt(options.size()));
            liveCache.setType(Material.CHEST, false);
        }
        if (liveCache != null && tick % 10 == 0)
            world.spawnParticle(Particle.END_ROD, liveCache.getLocation().add(.5, 1.2, .5), 4, .22, .2, .22, .015);
    }

    boolean claim(Block b) {
        if (!b.equals(liveCache))
            return false;
        b.setType(Material.AIR, false);
        liveCache = null;
        return true;
    }

    void guide(Player player, Block target) {
        Location eye = player.getEyeLocation(), to = target.getLocation().add(.5, .8, .5);
        Vector delta = to.toVector().subtract(eye.toVector());
        if (delta.lengthSquared() > 9) {
            delta.normalize();
            for (double d : new double[] {1.4, 2.0, 2.6})
                player.spawnParticle(Particle.END_ROD,
                        eye.clone().add(delta.clone().multiply(d)).subtract(0, .4, 0), 1, 0, 0, 0, 0);
        }
        player.spawnParticle(Particle.HAPPY_VILLAGER, to, 4, .25, .15, .25, 0);
    }

    void cleanup() {
        for (UUID id : wild) {
            Entity mob = world.getEntity(id);
            if (mob != null)
                mob.remove();
        }
        wild.clear();
    }
}
