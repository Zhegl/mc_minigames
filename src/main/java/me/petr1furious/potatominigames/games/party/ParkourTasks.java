package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.ParkourCourse.*;

import org.bukkit.Material;

import java.util.Map;

/** Native terrain for movement tasks; every structure stays between its two checkpoint rows. */
final class ParkourTasks {
    static void build(Kind kind, Island from, Island to, Map<Pos, Material> plan) {
        int center = (int) from.centerX();
        switch (kind) {
            case LOW_CEILING -> {
                for (int z = from.z() + 7; z < to.z(); z++)
                    for (int x = 0; x < WIDTH; x++) {
                        if (Math.abs(x - center) <= 1 && (z - from.z() - 7) % 3 < 2)
                            plan.put(new Pos(x, from.y(), z), Material.LIGHT_BLUE_CONCRETE);
                        for (int y = from.y() + 1; y <= from.y() + 8; y++)
                            if (Math.abs(x - center) > 1 || y >= from.y() + 3)
                                plan.put(new Pos(x, y, z), OBSTACLE_BLOCK);
                    }
            }
            case RIPTIDE_LAUNCH -> {
                for (int x = Math.max(from.x(), center - 1);
                        x <= Math.min(from.x() + from.width() - 1, center + 1);
                        x++)
                    for (int z = from.z() + 4; z < from.z() + 7; z++)
                        for (int y = from.y() + 1; y <= from.y() + 2; y++)
                            plan.put(new Pos(x, y, z), Material.WATER);
            }
            case HONEY_TRAVERSE -> {
                int side = from.centerX() < WIDTH / 2.0 ? 0 : WIDTH - 1;
                // Entirely after the departure checkpoint: the preceding task cannot use
                // this honey to break a fall. Catch the side only at the end of the drop.
                for (int z = to.z() - 3; z < to.z(); z++) {
                    // Six lower rows leave room to clear the cap with the full player
                    // hitbox, then touch honey before reaching the destination's floor.
                    for (int y = to.y(); y < to.y() + HONEY_CATCH_HEIGHT; y++)
                        plan.put(new Pos(side, y, z), Material.HONEY_BLOCK);
                    // A full solid cap hides the honey's soft landing surface. The 36-block
                    // drop onto this cap is lethal without first making the side catch.
                    plan.put(new Pos(side, to.y() + HONEY_CATCH_HEIGHT, z), OBSTACLE_BLOCK);
                }
            }
            case BOAT_WATER -> {
                for (int z = from.z() + 7; z < to.z(); z++)
                    for (int x = 0; x < WIDTH; x++) {
                        plan.put(new Pos(x, from.y() - 1, z), OBSTACLE_BLOCK);
                        plan.put(new Pos(x, from.y(), z), Material.WATER);
                    }
                for (int x = Math.max(from.x(), center - 1);
                        x <= Math.min(from.x() + from.width() - 1, center + 1);
                        x++)
                    for (int z = from.z() + 4; z < from.z() + 7; z++)
                        plan.put(new Pos(x, from.y(), z), Material.WATER);
                for (int i = 0; i < 3; i++) {
                    int gate = center + (i % 2 == 0 ? (center < WIDTH / 2 ? 8 : -8) : 0);
                    int z = from.z() + 14 + i * 8;
                    for (int x = 0; x < WIDTH; x++)
                        if (Math.abs(x - gate) > 2)
                            for (int y = from.y() + 1; y <= from.y() + 4; y++)
                                plan.put(new Pos(x, y, z), Material.LIGHT_BLUE_CONCRETE);
                }
            }
            case BOAT_ICE -> iceRoad(plan, from, to);
            case WIND_HOPS -> {
                for (int x = center - 1; x <= center + 1; x++)
                    for (int z = from.z() + 13; z < from.z() + 16; z++)
                        plan.put(new Pos(x, from.y() + 3, z), Material.LIGHT_BLUE_CONCRETE);
            }
            default -> {}
        }
    }

    private static void iceRoad(Map<Pos, Material> plan, Island from, Island to) {
        int center = (int) from.centerX(), direction = center < WIDTH / 2 ? 1 : -1;
        int start = from.z() + CHECKPOINT_DEPTH, end = to.z() - 1;
        // Two broad, continuous bends with straight entry/exit tangents. Packed ice
        // retains the boat's native speed advantage while being easier to steer than blue ice.
        for (int z = from.z() + 4; z <= end; z++) {
            double progress = Math.max(0, (double) (z - start) / (end - start));
            int offset = (int) Math.round(8 * (1 - Math.cos(2 * Math.PI * progress)));
            int rowCenter = center + direction * offset;
            int halfWidth = z < start ? Math.min(2, from.width() / 2) : 2;
            for (int x = rowCenter - halfWidth; x <= rowCenter + halfWidth; x++)
                plan.put(new Pos(x, from.y(), z), Material.PACKED_ICE);
            if (z >= start && z < end - 1)
                for (int x : new int[] {rowCenter - 3, rowCenter + 3})
                    for (int y = from.y(); y <= from.y() + 2; y++)
                        plan.put(new Pos(x, y, z), OBSTACLE_BLOCK);
        }
    }

    private ParkourTasks() {}
}
