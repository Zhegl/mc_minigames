package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;
import org.bukkit.block.Block;

import java.util.*;

final class ToolRaceArena {
    private static final int SPACING = 8;
    private final World world;
    private final Map<UUID, Column> columns = new LinkedHashMap<>();
    private final int lastX;
    private final int lastZ;

    private record Column(int x, int z) {}

    ToolRaceArena(World world, Collection<UUID> ids) {
        this.world = world;
        int across = Math.max(1, (int) Math.ceil(Math.sqrt(ids.size())));
        int i = 0;
        for (UUID id : ids) {
            columns.put(id, new Column(i % across * SPACING, i / across * SPACING));
            i++;
        }
        lastX = Math.max(0, Math.min(across, i) - 1) * SPACING;
        lastZ = Math.max(0, i - 1) / across * SPACING;
    }

    void build(ToolRaceTrack track) {
        int east = lastX + 2;
        int south = lastZ + 3;
        for (int x = -2; x <= east; x++)
            for (int z = -3; z <= south; z++) {
                world.getBlockAt(x, 25, z).setType(Material.SMOOTH_QUARTZ, false);
                if (x == -2 || x == east || z == -3 || z == south)
                    for (int y = 26; y <= 29; y++)
                        world.getBlockAt(x, y, z).setType(Material.GLASS, false);
            }
        for (Column column : columns.values())
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++)
                    for (int y = 31; y <= 83; y++) {
                        Material material =
                                (x != 0 || z != 0)
                                        ? Material.GLASS
                                        : y <= 80 ? track.block(80 - y) : Material.AIR;
                        world.getBlockAt(column.x() + x, y, column.z() + z).setType(material, false);
                    }
    }

    Location spawn(UUID id) {
        Column column = columns.get(id);
        return new Location(world, column.x() + .5, 81, column.z() + .5);
    }

    Location viewpoint() {
        return new Location(
                world, lastX / 2.0 + .5, 94 + Math.max(lastX, lastZ) / 2.0, lastZ / 2.0 + .5, 0, 90);
    }

    int depth(UUID id, Block block) {
        Column column = columns.get(id);
        return column != null
                        && block.getWorld().equals(world)
                        && block.getX() == column.x()
                        && block.getZ() == column.z()
                        && block.getY() <= 80
                        && block.getY() >= 31
                ? 80 - block.getY()
                : -1;
    }
}
