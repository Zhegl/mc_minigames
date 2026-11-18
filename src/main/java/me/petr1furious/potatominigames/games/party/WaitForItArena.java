package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.*;

import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Switch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class WaitForItArena {
    static final int FLOOR = 80;
    private static final int SIZE = 15;
    private static final int SPACING = 19;
    private static final int COLUMNS = 4;
    private static final int BED_SIZE = 9;
    private static final Material FLOOR_BLOCK = Material.SMOOTH_STONE;
    private static final Cell WORKBENCH = new Cell(7, 10);
    private static final List<Cell> BEDS = beds();
    private static final List<Cell> LEFT_POSTS = posts(2, 5);
    private static final List<Cell> RIGHT_POSTS = posts(9, 12);
    private static final List<BlockFace> BUTTON_FACES =
            List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST);
    private static final List<Material> BOUQUET =
            List.of(Material.POPPY, Material.CORNFLOWER, Material.ALLIUM);
    private static final List<Material> OTHER_FLOWERS =
            List.of(
                    Material.RED_TULIP,
                    Material.PINK_TULIP,
                    Material.BLUE_ORCHID,
                    Material.OXEYE_DAISY,
                    Material.AZURE_BLUET,
                    Material.ORANGE_TULIP);

    private final World world;
    private final int count;
    private final Setup[] current;
    private final Map<WaitForItRound.Task, Setup> layouts =
            new EnumMap<>(WaitForItRound.Task.class);

    WaitForItArena(World world, int count) {
        this.world = world;
        this.count = count;
        current = new Setup[count];
    }

    private record Cell(int x, int z) {}

    private record Plant(Material type, int age) {}

    private record Button(Cell post, BlockFace facing, Material type) {}

    private record Setup(
            WaitForItRound.Task task, List<Plant> plants, List<Button> buttons,
            WaitForItLayout layout) {}

    void build() {
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        for (int bay = 0; bay < count; bay++)
            for (int x = 0; x < SIZE; x++)
                for (int z = 0; z < SIZE; z++) {
                    block(bay, x, 0, z).setType(FLOOR_BLOCK, false);
                    if (x == 0 || x == SIZE - 1 || z == 0 || z == SIZE - 1) {
                        block(bay, x, 1, z).setType(Material.SMOOTH_SANDSTONE, false);
                        block(bay, x, 2, z).setType(Material.SMOOTH_SANDSTONE, false);
                        block(bay, x, 3, z)
                                .setType(
                                        (x + z) % 4 == 0
                                                ? Material.SHROOMLIGHT
                                                : Material.CUT_SANDSTONE,
                                        false);
                    }
                }
    }

    void prepare(int bay, WaitForItRound.Task task, Random random) {
        // Each player sees the same layout when they reach the task in their own bay.
        Setup next = layouts.computeIfAbsent(task, key -> setup(key, random));
        clear(bay);
        if (task == FLOWER || task == CROP) plant(bay, next.plants(), task == CROP);
        else if (task == BUTTON) buildButtonSearch(bay, next.buttons());
        else if (task.action == WaitForItRound.Action.CRAFT)
            block(bay, WORKBENCH.x, 1, WORKBENCH.z).setType(Material.CRAFTING_TABLE, false);
        else
            for (var fixture : next.layout().tiles.entrySet())
                fixture.getValue().place(block(bay, fixture.getKey()));
        current[bay] = next;
    }

    private static Setup setup(WaitForItRound.Task task, Random random) {
        List<Plant> plants =
                switch (task) {
                    case FLOWER -> bouquet(random);
                    case CROP -> harvest(random);
                    default -> List.of();
                };
        var buttons = task == BUTTON ? switches(random) : List.<Button>of();
        return new Setup(task, plants, buttons, WaitForItLayout.generate(task, random));
    }

    private void clear(int bay) {
        Setup old = current[bay];
        if (old == null) return;
        for (var tile : old.layout().tiles.keySet())
            block(bay, tile).setType(tile.y() == 0 ? FLOOR_BLOCK : Material.AIR, false);
        for (var position : old.layout().placements.keySet())
            block(bay, position).setType(Material.AIR, false);
        if (old.task() == FLOWER || old.task() == CROP)
            for (Cell cell : BEDS) {
                block(bay, cell.x, 1, cell.z).setType(Material.AIR, false);
                block(bay, cell.x, 0, cell.z).setType(FLOOR_BLOCK, false);
            }
        else if (old.task().action == WaitForItRound.Action.CRAFT)
            block(bay, WORKBENCH.x, 1, WORKBENCH.z).setType(Material.AIR, false);
        else if (old.task() == BUTTON) {
            clearPosts(bay, LEFT_POSTS);
            clearPosts(bay, RIGHT_POSTS);
        }
    }

    private void clearPosts(int bay, List<Cell> posts) {
        for (Cell cell : posts) {
            block(bay, cell.x, 1, cell.z).setType(Material.AIR, false);
            block(bay, cell.x, 2, cell.z).setType(Material.AIR, false);
            for (BlockFace face : BUTTON_FACES)
                block(bay, cell.x + face.getModX(), 2, cell.z + face.getModZ())
                        .setType(Material.AIR, false);
        }
    }

    private static List<Plant> bouquet(Random random) {
        List<Plant> layout = new ArrayList<>(BEDS.size());
        for (int i = 0; i < BEDS.size(); i++)
            layout.add(new Plant(OTHER_FLOWERS.get(random.nextInt(OTHER_FLOWERS.size())), -1));
        List<Integer> patches = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(patches, random);
        for (int i = 0; i < BOUQUET.size(); i++)
            layout.set(
                    patches.get(i) * BED_SIZE + random.nextInt(BED_SIZE),
                    new Plant(BOUQUET.get(i), -1));
        return layout;
    }

    private static List<Plant> harvest(Random random) {
        int wheat = CROP.required(WaitForItRound.Goal.WHEAT);
        int carrots = CROP.required(WaitForItRound.Goal.CARROT);
        List<Plant> layout = new ArrayList<>(BEDS.size());
        for (int i = 0; i < wheat; i++) layout.add(new Plant(Material.WHEAT, 7));
        for (int i = 0; i < carrots; i++) layout.add(new Plant(Material.CARROTS, 7));
        while (layout.size() < BEDS.size())
            layout.add(
                    new Plant(
                            random.nextBoolean() ? Material.WHEAT : Material.CARROTS,
                            3 + random.nextInt(3)));
        Collections.shuffle(layout, random);
        return layout;
    }

    private void plant(int bay, List<Plant> plants, boolean crops) {
        Farmland soil = (Farmland) Material.FARMLAND.createBlockData();
        soil.setMoisture(soil.getMaximumMoisture());
        for (int i = 0; i < BEDS.size(); i++) {
            Cell cell = BEDS.get(i);
            Plant plant = plants.get(i);
            Block below = block(bay, cell.x, 0, cell.z);
            Block above = block(bay, cell.x, 1, cell.z);
            if (crops) {
                below.setBlockData(soil, false);
                Ageable plantData = (Ageable) plant.type.createBlockData();
                plantData.setAge(plant.age);
                above.setBlockData(plantData, false);
            } else {
                below.setType(Material.GRASS_BLOCK, false);
                above.setType(plant.type, false);
            }
        }
    }

    private static List<Button> switches(Random random) {
        List<Button> layout = new ArrayList<>();
        addSwitches(layout, LEFT_POSTS, random);
        addSwitches(layout, RIGHT_POSTS, random);
        return layout;
    }

    private static void addSwitches(List<Button> layout, List<Cell> posts, Random random) {
        List<Cell> shuffled = new ArrayList<>(posts);
        Collections.shuffle(shuffled, random);
        for (int i = 0; i < 3; i++)
            layout.add(
                    new Button(
                            shuffled.get(i),
                            BUTTON_FACES.get(random.nextInt(BUTTON_FACES.size())),
                            i == 0 ? Material.STONE_BUTTON : Material.SPRUCE_BUTTON));
    }

    private void buildButtonSearch(int bay, List<Button> switches) {
        buildPosts(bay, LEFT_POSTS);
        buildPosts(bay, RIGHT_POSTS);
        for (Button button : switches) {
            Switch data = (Switch) button.type.createBlockData();
            data.setAttachedFace(FaceAttachable.AttachedFace.WALL);
            data.setFacing(button.facing);
            block(
                            bay,
                            button.post.x + button.facing.getModX(),
                            2,
                            button.post.z + button.facing.getModZ())
                    .setBlockData(data, false);
        }
    }

    private void buildPosts(int bay, List<Cell> posts) {
        for (Cell cell : posts) {
            block(bay, cell.x, 1, cell.z).setType(Material.STRIPPED_SPRUCE_LOG, false);
            block(bay, cell.x, 2, cell.z).setType(Material.SPRUCE_PLANKS, false);
        }
    }

    Location spawn(int bay) {
        return new Location(world, originX(bay) + 7.5, FLOOR + 1, originZ(bay) + 13.5, 180, 0);
    }

    Location viewpoint() {
        int columns = Math.min(count, COLUMNS);
        int rows = (count + COLUMNS - 1) / COLUMNS;
        return new Location(
                world,
                originX(columns - 1) / 2.0 + SIZE / 2.0,
                FLOOR + 45,
                originZ((rows - 1) * COLUMNS) / 2.0 + SIZE / 2.0,
                0,
                88);
    }

    boolean contains(int bay, Location location) {
        return world.equals(location.getWorld())
                && location.getX() >= originX(bay) + 1
                && location.getX() < originX(bay) + SIZE - 1
                && location.getY() > FLOOR
                && location.getY() < FLOOR + 6
                && location.getZ() >= originZ(bay) + 1
                && location.getZ() < originZ(bay) + SIZE - 1;
    }

    boolean isWorkbench(int bay, Block block) {
        return contains(bay, block.getLocation()) && block.getType() == Material.CRAFTING_TABLE;
    }

    boolean isBarrel(int bay, Block block) {
        Setup setup = current[bay];
        return setup != null && setup.task() == TREASURE && owns(bay, block)
                && block.getType() == Material.BARREL;
    }

    WaitForItLayout.Placement placementAt(int bay, Block block) {
        Setup setup = current[bay];
        if (setup == null || !owns(bay, block)) return null;
        return setup.layout().placements.get(relative(bay, block));
    }

    WaitForItRound.Goal targetAt(int bay, WaitForItRound.Task task, Block block) {
        Setup setup = current[bay];
        if (setup == null || setup.task() != task || !owns(bay, block)) return null;
        return switch (task) {
            case FLOWER ->
                    switch (block.getType()) {
                        case POPPY -> WaitForItRound.Goal.POPPY;
                        case CORNFLOWER -> WaitForItRound.Goal.CORNFLOWER;
                        case ALLIUM -> WaitForItRound.Goal.ALLIUM;
                        default -> null;
                    };
            case CROP -> {
                if (!(block.getBlockData() instanceof Ageable mature)
                        || mature.getAge() != mature.getMaximumAge()) yield null;
                yield switch (block.getType()) {
                    case WHEAT -> WaitForItRound.Goal.WHEAT;
                    case CARROTS -> WaitForItRound.Goal.CARROT;
                    default -> null;
                };
            }
            case BUTTON ->
                    block.getType() != Material.STONE_BUTTON
                            ? null
                            : block.getX() - originX(bay) < SIZE / 2
                                    ? WaitForItRound.Goal.BUTTON_LEFT
                                    : WaitForItRound.Goal.BUTTON_RIGHT;
            default -> {
                var target = setup.layout().tiles.get(relative(bay, block));
                yield target != null && target.material() == block.getType() ? target.goal() : null;
            }
        };
    }

    private boolean owns(int bay, Block block) {
        var local = relative(bay, block);
        return world.equals(block.getWorld())
                && local.x() >= 1
                && local.x() < SIZE - 1
                && local.z() >= 1
                && local.z() < SIZE - 1
                && local.y() >= 0
                && local.y() < 5;
    }

    private WaitForItLayout.Point relative(int bay, Block block) {
        return new WaitForItLayout.Point(
                block.getX() - originX(bay), block.getY() - FLOOR, block.getZ() - originZ(bay));
    }

    private Block block(int bay, WaitForItLayout.Point point) {
        return block(bay, point.x(), point.y(), point.z());
    }

    private Block block(int bay, int x, int y, int z) {
        return world.getBlockAt(originX(bay) + x, FLOOR + y, originZ(bay) + z);
    }

    private static int originX(int bay) {
        return bay % COLUMNS * SPACING;
    }

    private static int originZ(int bay) {
        return bay / COLUMNS * SPACING;
    }

    private static List<Cell> beds() {
        List<Cell> cells = new ArrayList<>();
        for (int cx : new int[] {2, 10})
            for (int cz : new int[] {2, 8})
                for (int dx = 0; dx < 3; dx++)
                    for (int dz = 0; dz < 3; dz++) cells.add(new Cell(cx + dx, cz + dz));
        return List.copyOf(cells);
    }

    private static List<Cell> posts(int nearEdge, int nearCenter) {
        List<Cell> cells = new ArrayList<>();
        for (int x : new int[] {nearEdge, nearCenter})
            for (int z : new int[] {3, 7, 10}) cells.add(new Cell(x, z));
        return List.copyOf(cells);
    }
}
