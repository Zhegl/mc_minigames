package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/** Equal ore quotas in independent 16-cube cells. Build and wall removal are sliced across ticks. */
final class MinersArena {
    static final int SIDE = 16, PITCH = SIDE + 1, BASE = 64, ROOF = BASE + SIDE;
    record Pos(int x, int y, int z) { static Pos of(Block block) { return new Pos(block.getX(), block.getY(), block.getZ()); } }
    private final World world;
    private final int players, columns, rows, maxX, maxZ;
    private final Map<Pos, MinersOre> ores = new HashMap<>();
    private final List<Pos> partitions = new ArrayList<>();
    private int built, removed;
    private boolean opened;

    MinersArena(World world, int players, Random random) {
        if (players < 1 || players > 12) throw new IllegalArgumentException("Miners supports 1–12 players");
        this.world = world; this.players = players;
        columns = (int) Math.ceil(Math.sqrt(players)); rows = (players + columns - 1) / columns;
        maxX = columns * PITCH; maxZ = rows * PITCH;
        for (int cell = 0; cell < players; cell++) {
            List<Pos> eligible = new ArrayList<>();
            int cx = cell % columns * PITCH, cz = cell / columns * PITCH;
            for (int x = 1; x <= SIDE; x++) for (int y = BASE; y < ROOF; y++) for (int z = 1; z <= SIDE; z++) {
                if (!cavity(x, y, z)) eligible.add(new Pos(cx + x, y, cz + z));
            }
            Collections.shuffle(eligible, random);
            int index = 0;
            for (MinersOre ore : MinersOre.values()) for (int i = 0; i < ore.perCell; i++) ores.put(eligible.get(index++), ore);
        }
        for (int x = 1; x < maxX; x++) for (int z = 1; z < maxZ; z++) {
            if ((x % PITCH == 0 || z % PITCH == 0) && neighbours(x, z) >= 2)
                for (int y = BASE; y < ROOF; y++) partitions.add(new Pos(x, y, z));
        }
    }
    private static boolean cavity(int x, int y, int z) {
        return x >= 7 && x <= 9 && z >= 7 && z <= 9 && y >= BASE + 8 && y <= BASE + 10;
    }
    private int neighbours(int x, int z) {
        int count = 0;
        for (int col = (x - 1) / PITCH; col <= x / PITCH; col++)
            for (int row = (z - 1) / PITCH; row <= z / PITCH; row++)
                if (col < columns && row < rows && row * columns + col < players) count++;
        return count;
    }
    int cellAt(int x, int z) {
        if (x <= 0 || z <= 0 || x >= maxX || z >= maxZ || x % PITCH == 0 || z % PITCH == 0) return -1;
        int cell = z / PITCH * columns + x / PITCH;
        return cell < players ? cell : -1;
    }
    int cellAt(Location at) { return at.getWorld().equals(world) ? cellAt(at.getBlockX(), at.getBlockZ()) : -1; }
    boolean contains(Location at) {
        if (!at.getWorld().equals(world) || at.getY() < BASE || at.getY() >= ROOF) return false;
        int x = at.getBlockX(), z = at.getBlockZ();
        return cellAt(x, z) >= 0 || opened && x > 0 && z > 0 && x < maxX && z < maxZ && neighbours(x, z) >= 2;
    }
    boolean editable(Block block) { return contains(block.getLocation()); }
    boolean owns(Location at, int cell) { return at.getY() >= BASE && at.getY() < ROOF && cellAt(at) == cell; }
    Material generated(int x, int y, int z) {
        int cell = cellAt(x, z);
        if (y == BASE - 1 || y == ROOF || cell < 0) return Material.BEDROCK;
        if (cavity(x % PITCH, y, z % PITCH)) return Material.AIR;
        MinersOre ore = ores.get(new Pos(x, y, z));
        return ore == null ? Material.STONE : ore.material;
    }
    int totalBlocks() { return (maxX + 1) * (maxZ + 1) * (SIDE + 2); }
    boolean buildSlice(int budget) {
        for (int i = 0; i < budget && built < totalBlocks(); i++, built++) {
            int x = built % (maxX + 1), z = built / (maxX + 1) % (maxZ + 1);
            int y = BASE - 1 + built / ((maxX + 1) * (maxZ + 1));
            Material material = generated(x, y, z);
            if (material != Material.AIR) world.getBlockAt(x, y, z).setType(material, false);
        }
        return built == totalBlocks();
    }
    float progress() { return (float) built / totalBlocks(); }
    boolean openSlice(int budget) {
        for (int i = 0; i < budget && removed < partitions.size(); i++, removed++) {
            Pos p = partitions.get(removed);
            world.getBlockAt(p.x(), p.y(), p.z()).setType(Material.AIR, false);
        }
        opened = removed == partitions.size();
        return opened;
    }
    boolean opened() { return opened; }
    MinersOre takeOre(Block block) {
        Pos p = Pos.of(block);
        MinersOre ore = ores.remove(p);
        return ore != null && block.getType() == ore.material ? ore : null;
    }
    int oreCount(int cell, MinersOre ore) {
        return (int) ores.entrySet().stream().filter(e -> e.getValue() == ore && cellAt(e.getKey().x(), e.getKey().z()) == cell).count();
    }
    Location spawn(int cell) { return new Location(world, cell % columns * PITCH + 8.5, BASE + 8, cell / columns * PITCH + 8.5); }
    Location center() { return new Location(world, (maxX + 1) / 2.0, BASE + 8, (maxZ + 1) / 2.0); }
    Location spectator() { Location at = center(); at.setY(ROOF + 4); return at; }
    boolean chargeFits(Location at) {
        if (!contains(at)) return false;
        BoundingBox box = new BoundingBox(at.getX() - .49, at.getY(), at.getZ() - .49,
                at.getX() + .49, at.getY() + .98, at.getZ() + .49);
        for (int x = (int) Math.floor(box.getMinX()); x <= Math.floor(box.getMaxX()); x++)
            for (int y = (int) Math.floor(box.getMinY()); y <= Math.floor(box.getMaxY()); y++)
                for (int z = (int) Math.floor(box.getMinZ()); z <= Math.floor(box.getMaxZ()); z++)
                    // Generated stone/ores/bedrock and allowed building blocks are full cubes.
                    if (world.getBlockAt(x, y, z).getType() != Material.AIR
                            && new BoundingBox(x, y, z, x + 1, y + 1, z + 1).overlaps(box)) return false;
        return true;
    }
    int borderSize() { return Math.max(maxX, maxZ) + 1; }
}
