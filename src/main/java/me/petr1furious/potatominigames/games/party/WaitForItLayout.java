package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.Goal.*;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** A bay's extra fixtures, generated once and copied at the same relative coordinates. */
final class WaitForItLayout {
    private static final List<Point> STATIONS =
            List.of(
                    new Point(3, 1, 4),
                    new Point(7, 1, 3),
                    new Point(11, 1, 4),
                    new Point(3, 1, 9),
                    new Point(7, 1, 8),
                    new Point(11, 1, 9));
    private static final List<WaitForItRound.Goal> STEPS = List.of(FIRST, SECOND, THIRD);
    private static final List<Material> ROUTE_COLORS =
            List.of(Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.RED_CONCRETE);

    record Point(int x, int y, int z) {
        Point above() {
            return new Point(x, y + 1, z);
        }
    }

    record Tile(Material material, WaitForItRound.Goal goal, Material loot) {
        void place(Block block) {
            block.setType(material, false);
            if (material == Material.LEVER) {
                var lever = (Switch) block.getBlockData();
                lever.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
                block.setBlockData(lever, false);
            }
            if (loot != null)
                ((Container) block.getState()).getInventory().setItem(13, new ItemStack(loot));
        }
    }

    record Placement(Material material, WaitForItRound.Goal goal) {}

    final Map<Point, Tile> tiles = new LinkedHashMap<>();
    final Map<Point, Placement> placements = new LinkedHashMap<>();

    static WaitForItLayout generate(WaitForItRound.Task task, Random random) {
        var layout = new WaitForItLayout();
        switch (task) {
            case LOGS ->
                    layout.scatter(
                            random,
                            Material.SPRUCE_LOG,
                            SPRUCE,
                            Material.BIRCH_LOG,
                            BIRCH,
                            Material.OAK_LOG);
            case ORES ->
                    layout.scatter(
                            random,
                            Material.COPPER_ORE,
                            COPPER,
                            Material.IRON_ORE,
                            IRON,
                            Material.COAL_ORE);
            case WEBS -> STATIONS.forEach(point -> layout.set(point, Material.COBWEB, WEB));
            case LEVERS -> layout.levers(random);
            case TREASURE -> layout.treasure(random);
            case STACK -> layout.stack();
            case LIGHT -> layout.lights();
            case CHECKPOINTS -> layout.route(false);
            case PARKOUR -> layout.route(true);
            case TARGETS -> layout.targets();
            default -> {}
        }
        return layout;
    }

    private void scatter(
            Random random,
            Material first,
            WaitForItRound.Goal firstGoal,
            Material second,
            WaitForItRound.Goal secondGoal,
            Material decoy) {
        var locations = new ArrayList<>(STATIONS);
        Collections.shuffle(locations, random);
        for (int i = 0; i < locations.size(); i++) {
            if (i < 2) set(locations.get(i), first, firstGoal);
            else if (i < 4) set(locations.get(i), second, secondGoal);
            else set(locations.get(i), decoy, null);
        }
    }

    private void levers(Random random) {
        var locations = new ArrayList<>(STATIONS);
        Collections.shuffle(locations, random);
        for (int i = 0; i < locations.size(); i++) {
            Point location = locations.get(i);
            set(location, Material.POLISHED_ANDESITE, null);
            if (i < STEPS.size()) set(location.above(), Material.LEVER, STEPS.get(i));
        }
    }

    private void treasure(Random random) {
        int target = random.nextInt(STATIONS.size());
        for (int i = 0; i < STATIONS.size(); i++) {
            Material loot = i == target ? Material.EMERALD : Material.AMETHYST_SHARD;
            tiles.put(STATIONS.get(i), new Tile(Material.BARREL, null, loot));
        }
    }

    private void stack() {
        set(new Point(7, 0, 9), Material.GOLD_BLOCK, null);
        set(new Point(9, 1, 9), Material.SMOOTH_STONE, null);
        List<Material> blocks =
                List.of(Material.RED_CONCRETE, Material.YELLOW_CONCRETE, Material.BLUE_CONCRETE);
        for (int i = 0; i < STEPS.size(); i++)
            placements.put(new Point(7, i + 1, 9), new Placement(blocks.get(i), STEPS.get(i)));
    }

    private void lights() {
        List<Point> pads = List.of(new Point(3, 0, 9), new Point(10, 0, 7), new Point(6, 0, 3));
        for (int i = 0; i < pads.size(); i++) {
            Point pad = pads.get(i);
            set(pad, Material.GOLD_BLOCK, null);
            placements.put(pad.above(), new Placement(Material.TORCH, STEPS.get(i)));
        }
    }

    private void route(boolean raised) {
        int[] xs = raised ? new int[] {7, 7, 7} : new int[] {3, 10, 6};
        int[] zs = raised ? new int[] {10, 7, 4} : new int[] {10, 7, 3};
        for (int i = 0; i < STEPS.size(); i++) {
            int y = raised ? i + 1 : 0;
            for (int dx = 0; dx < 2; dx++)
                for (int dz = 0; dz < 2; dz++)
                    set(new Point(xs[i] + dx, y, zs[i] + dz), ROUTE_COLORS.get(i), STEPS.get(i));
        }
    }

    private void targets() {
        for (int i = 0; i < STEPS.size(); i++) {
            int x = 3 + i * 4;
            set(new Point(x, 1, 3), Material.POLISHED_DEEPSLATE, null);
            set(new Point(x, 2, 3), Material.TARGET, STEPS.get(i));
        }
        for (int x = 2; x <= 12; x++) set(new Point(x, 0, 9), Material.SMOOTH_QUARTZ, null);
    }

    private void set(Point point, Material material, WaitForItRound.Goal goal) {
        tiles.put(point, new Tile(material, goal, null));
    }
}
