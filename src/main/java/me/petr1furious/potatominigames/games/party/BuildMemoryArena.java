package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;
import org.bukkit.block.Block;

import java.util.*;

final class BuildMemoryArena {
    private final World world;
    private int size = 5;
    private final Map<UUID, Location> centers = new LinkedHashMap<>();

    BuildMemoryArena(World world, Collection<UUID> roster) {
        this.world = world;
        int i = 0, n = roster.size();
        double radius = Math.max(16, n * 3.2);
        for (UUID id : roster) {
            double angle = 2 * Math.PI * i++ / n;
            centers.put(
                    id,
                    new Location(
                            world,
                            Math.round(radius * Math.cos(angle)),
                            81,
                            Math.round(radius * Math.sin(angle))));
        }
    }

    void resize(int size) {
        if (size != 5 && size != 7) throw new IllegalArgumentException("Build size must be 5 or 7");
        if (this.size == size) return;
        centers.keySet().forEach(this::clear);
        this.size = size;
        for (Location c : centers.values()) floor(c);
    }

    private void floor(Location c) {
        for (int x = -4; x <= 8; x++)
            for (int z = -4; z <= 8; z++)
                world.getBlockAt(c.getBlockX() + x, 80, c.getBlockZ() + z)
                        .setType(
                                x >= 0 && x < size && z >= 0 && z < size
                                        ? Material.LIGHT_BLUE_CONCRETE
                                        : Material.SMOOTH_QUARTZ,
                                false);
    }

    void build() {
        for (int x = 1; x <= 3; x++)
            for (int z = 1; z <= 3; z++)
                world.getBlockAt(x, 83, z)
                        .setType(
                                x == 2 && z == 2
                                        ? Material.SEA_LANTERN
                                        : Material.PRISMARINE_BRICKS,
                                false);
        for (Location c : centers.values()) {
            floor(c);
            for (int x = -4; x <= 8; x++)
                for (int z = -4; z <= 8; z++)
                    if (x == -4 || x == 8 || z == -4 || z == 8)
                        for (int y = 81; y <= 88; y++)
                            world.getBlockAt(c.getBlockX() + x, y, c.getBlockZ() + z)
                                    .setType(Material.BARRIER, false);
        }
    }

    Location spawn(UUID id) {
        return centers.get(id).clone().add(size / 2.0, 0, -2.5);
    }

    Location reviewCenter(UUID id) {
        return centers.get(id).clone().add(size / 2.0, 1.5, size / 2.0);
    }

    Location cellLocation(UUID id, BuildMemoryPattern.Cell cell) {
        return centers.get(id).clone().add(cell.x(), cell.y(), cell.z());
    }

    BuildMemoryPattern.Cell localCell(UUID owner, Block block) {
        Location c = centers.get(owner);
        if (c == null || !block.getWorld().equals(world)) return null;
        int x = block.getX() - c.getBlockX(),
                y = block.getY() - 81,
                z = block.getZ() - c.getBlockZ();
        return x >= 0 && x < size && y >= 0 && y < 4 && z >= 0 && z < size
                ? new BuildMemoryPattern.Cell(x, y, z)
                : null;
    }

    void show(UUID id, BuildMemoryPattern p) {
        if (p.size() > size) resize(p.size());
        clear(id);
        Location c = centers.get(id);
        p.blocks()
                .forEach((cell, m) ->
                        world.getBlockAt(
                                        c.getBlockX() + cell.x(),
                                        81 + cell.y(),
                                        c.getBlockZ() + cell.z())
                                .setBlockData(Bukkit.createBlockData(m.dataString()), false));
        // Derive fence connections and powered lamps just as the player's build does.
        for (var cell : p.blocks().keySet()) {
            Block block =
                    world.getBlockAt(
                            c.getBlockX() + cell.x(), 81 + cell.y(), c.getBlockZ() + cell.z());
            var data = block.getBlockData();
            if (data instanceof org.bukkit.block.data.type.Fence fence) {
                for (var face : fence.getAllowedFaces()) {
                    Block neighbor = block.getRelative(face);
                    fence.setFace(
                            face,
                            neighbor.getType().name().endsWith("_FENCE")
                                    || neighbor.getType().isOccluding()
                                    || neighbor.getBlockData()
                                                    instanceof org.bukkit.block.data.type.Gate gate
                                            && (face.getModX() != 0
                                                    ? gate.getFacing().getModZ() != 0
                                                    : gate.getFacing().getModX() != 0));
                }
                block.setBlockData(fence, false);
            } else if (block.getType() == Material.IRON_BARS
                    && data instanceof org.bukkit.block.data.MultipleFacing bars) {
                for (var face : bars.getAllowedFaces()) {
                    Block neighbor = block.getRelative(face);
                    bars.setFace(
                            face,
                            neighbor.getType() == Material.IRON_BARS
                                    || neighbor.getType().isOccluding());
                }
                block.setBlockData(bars, false);
            } else if (block.getType() == Material.REDSTONE_LAMP
                    && data instanceof org.bukkit.block.data.Lightable lamp) {
                lamp.setLit(
                        p.blocks().entrySet().stream()
                                .anyMatch(e ->
                                        e.getValue().material() == Material.REDSTONE_BLOCK
                                                && Math.abs(e.getKey().x() - cell.x())
                                                                + Math.abs(
                                                                        e.getKey().y() - cell.y())
                                                                + Math.abs(
                                                                        e.getKey().z() - cell.z())
                                                        == 1));
                block.setBlockData(lamp, false);
            }
        }
    }

    void clear(UUID id) {
        Location c = centers.get(id);
        for (int x = 0; x < size; x++)
            for (int y = 81; y < 85; y++)
                for (int z = 0; z < size; z++)
                    world.getBlockAt(c.getBlockX() + x, y, c.getBlockZ() + z)
                            .setType(Material.AIR, false);
    }

    Map<BuildMemoryPattern.Cell, MemoryBlock> capture(UUID id) {
        Map<BuildMemoryPattern.Cell, MemoryBlock> result = new HashMap<>();
        Location c = centers.get(id);
        for (int x = 0; x < size; x++)
            for (int y = 0; y < 4; y++)
                for (int z = 0; z < size; z++) {
                    Material m =
                            world.getBlockAt(c.getBlockX() + x, 81 + y, c.getBlockZ() + z)
                                    .getType();
                    if (m != Material.AIR && m != Material.CAVE_AIR && m != Material.VOID_AIR)
                        result.put(
                                new BuildMemoryPattern.Cell(x, y, z),
                                MemoryBlock.capture(
                                        world.getBlockAt(
                                                        c.getBlockX() + x,
                                                        81 + y,
                                                        c.getBlockZ() + z)
                                                .getBlockData()));
                }
        return result;
    }

    List<MemoryBlock> remove(
            UUID id, Block block, java.util.function.Predicate<List<MemoryBlock>> canRefund) {
        var cell = localCell(id, block);
        if (cell == null) return List.of();
        var current = capture(id);
        Set<BuildMemoryPattern.Cell> removed = new HashSet<>(Set.of(cell));
        boolean changed;
        do {
            changed = false;
            for (var entry : current.entrySet()) {
                var support = entry.getValue().support(entry.getKey());
                if (support != null && removed.contains(support))
                    changed |= removed.add(entry.getKey());
            }
        } while (changed);
        List<MemoryBlock> refund =
                removed.stream().map(current::get).filter(Objects::nonNull).toList();
        if (!canRefund.test(refund)) return List.of();
        Location c = centers.get(id);
        for (var position : removed) {
            var value = current.get(position);
            if (value == null) continue;
            world.getBlockAt(
                            c.getBlockX() + position.x(),
                            81 + position.y(),
                            c.getBlockZ() + position.z())
                    .setType(Material.AIR, false);
        }
        return refund;
    }

    void screens(boolean show) {
        for (Location c : centers.values())
            for (int x = -4; x <= 8; x++)
                for (int z = -4; z <= 8; z++)
                    if (x == -4 || x == 8 || z == -4 || z == 8)
                        for (int y = 81; y <= 88; y++)
                            world.getBlockAt(c.getBlockX() + x, y, c.getBlockZ() + z)
                                    .setType(
                                            show ? Material.BLACK_CONCRETE : Material.BARRIER,
                                            false);
    }
}
