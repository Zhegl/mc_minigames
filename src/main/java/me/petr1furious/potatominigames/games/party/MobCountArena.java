package me.petr1furious.potatominigames.games.party;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

final class MobCountArena {
    static final int FLOOR = 80;
    static final int RADIUS = 14;
    static final int OUTSIDE = -2;
    static final int SEPARATOR = -1;
    private static final double HUB_RADIUS = 4.2;
    private static final double SEPARATOR_CLEARANCE = 1.1;
    private static final List<Material> COLORS = List.of(
            Material.LIGHT_BLUE_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE,
            Material.PINK_CONCRETE, Material.ORANGE_CONCRETE);

    private final World world;
    private final List<TextDisplay> labels = new ArrayList<>();

    MobCountArena(World world) {
        this.world = world;
    }

    static int sectorAt(int x, int z, int sectors) {
        int squaredRadius = x * x + z * z;
        if (squaredRadius > RADIUS * RADIUS) return OUTSIDE;
        if (squaredRadius <= HUB_RADIUS * HUB_RADIUS) return SEPARATOR;

        double arc = Math.TAU / sectors;
        double angle = Math.atan2(z, x);
        int wedge = (int) Math.floor(angle / arc + .5);
        double nearestBoundary = arc / 2 - Math.abs(angle - wedge * arc);
        // The center must clear the ray by more than half a block diagonal in each wedge.
        // This leaves a band of neutral tiles between different wedges, even on diagonals.
        if (Math.sqrt(squaredRadius) * Math.sin(nearestBoundary) <= SEPARATOR_CLEARANCE) return SEPARATOR;
        return Math.floorMod(wedge, sectors);
    }

    Location spawn(int index, int count) {
        return atAngle(Math.TAU * index / count, count == 1 ? 0 : 3.2, FLOOR + 1);
    }

    Location marker(int sector, int sectors) {
        return atAngle(Math.TAU * sector / sectors, RADIUS - 2, FLOOR + 1);
    }

    private Location atAngle(double angle, double radius, double y) {
        return new Location(world, .5 + radius * Math.cos(angle), y, .5 + radius * Math.sin(angle));
    }

    List<Location> countingSpots() {
        List<Location> spots = new ArrayList<>();
        for (int x = -10; x <= 10; x += 2) {
            for (int z = -10; z <= 10; z += 2) {
                if (x * x + z * z <= 11 * 11)
                    spots.add(new Location(world, x + .5, FLOOR + 1.25, z + .5));
            }
        }
        return spots;
    }

    void prepare(MobCountRound round) {
        cleanupLabels();
        int sectors = round.sectors().size();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                int sector = sectorAt(x, z, sectors);
                if (sector == OUTSIDE) continue;
                world.getBlockAt(x, FLOOR + 1, z).setType(Material.AIR, false);
                Material tile = sector == SEPARATOR ? Material.BLACK_CONCRETE : COLORS.get(sector);
                world.getBlockAt(x, FLOOR, z).setType(tile, false);
            }
        }
        for (int i = 0; i < sectors; i++) {
            var species = round.sectors().get(i);
            Location marker = marker(i, sectors);
            int x = marker.getBlockX(), z = marker.getBlockZ();
            world.getBlockAt(x, FLOOR + 1, z).setType(Material.valueOf(species.head), false);
            Location labelLocation = new Location(world, x + .5, FLOOR + 2.4, z + .5);
            TextDisplay label = world.spawn(labelLocation, TextDisplay.class);
            label.text(species.title.color(NamedTextColor.WHITE));
            label.setBillboard(Display.Billboard.CENTER);
            label.setShadowed(true);
            label.setSeeThrough(false);
            labels.add(label);
        }
        world.setSpawnLocation(0, FLOOR + 1, 0);
    }

    void removeWrong(MobCountRound round) {
        int sectors = round.sectors().size();
        int correct = round.sectors().indexOf(round.answer());
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                int sector = sectorAt(x, z, sectors);
                if (sector != OUTSIDE && sector != correct)
                    world.getBlockAt(x, FLOOR, z).setType(Material.AIR, false);
            }
        }
        for (int i = 0; i < sectors; i++) {
            if (i == correct) continue;
            Location marker = marker(i, sectors);
            world.getBlockAt(marker.getBlockX(), FLOOR + 1, marker.getBlockZ()).setType(Material.AIR, false);
            labels.get(i).remove();
        }
    }

    void cleanupLabels() {
        labels.forEach(TextDisplay::remove);
        labels.clear();
    }
}
