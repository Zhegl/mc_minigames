package me.petr1furious.potatominigames.games.party;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Opaque, sealed rooms with an 11 x 8 x 11 editable volume and a viewing apron. */
final class GarticPhoneArena {
  record Cell(int x, int y, int z) {}

  private final World world;
  private final Map<UUID, Location> origins = new LinkedHashMap<>();

  GarticPhoneArena(World world, List<UUID> roster) {
    this.world = world;
    for (int i = 0; i < roster.size(); i++)
      origins.put(roster.get(i), new Location(world, (i % 4) * 32, 81, (i / 4) * 32));
  }

  void build() {
    for (Location c : origins.values())
      for (int x = -3; x <= 13; x++)
        for (int z = -5; z <= 13; z++)
          for (int y = 80; y <= 89; y++) {
            Material material;
            if (y == 80)
              material =
                  x >= 0 && x <= 10 && z >= 0 && z <= 10
                      ? Material.LIGHT_BLUE_CONCRETE
                      : Material.SMOOTH_QUARTZ;
            else if (y == 89) material = Material.SEA_LANTERN;
            else if (x == -3 || x == 13 || z == -5 || z == 13) material = Material.WHITE_CONCRETE;
            else continue;
            world.getBlockAt(c.getBlockX() + x, y, c.getBlockZ() + z).setType(material, false);
          }
  }

  Location spawn(UUID id) {
    return origins.get(id).clone().add(5.5, 0, -2.5);
  }

  boolean inside(UUID id, Location location) {
    Location c = origins.get(id);
    if (c == null || !world.equals(location.getWorld())) return false;
    double x = location.getX() - c.getX(), z = location.getZ() - c.getZ();
    return x >= -2.5
        && x <= 12.5
        && z >= -4.5
        && z <= 12.5
        && location.getY() >= 81
        && location.getY() < 88;
  }

  boolean editable(UUID id, Block block) {
    Location c = origins.get(id);
    if (c == null || !world.equals(block.getWorld())) return false;
    int x = block.getX() - c.getBlockX(), z = block.getZ() - c.getBlockZ();
    return x >= 0 && x <= 10 && z >= 0 && z <= 10 && block.getY() >= 81 && block.getY() <= 88;
  }

  Map<Cell, Material> capture(UUID id) {
    Map<Cell, Material> result = new HashMap<>();
    Location c = origins.get(id);
    for (int x = 0; x <= 10; x++)
      for (int y = 0; y < 8; y++)
        for (int z = 0; z <= 10; z++) {
          Material type = world.getBlockAt(c.getBlockX() + x, 81 + y, c.getBlockZ() + z).getType();
          if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR)
            result.put(new Cell(x, y, z), type);
        }
    return Map.copyOf(result);
  }

  void show(UUID id, Map<Cell, Material> snapshot) {
    Location c = origins.get(id);
    for (int x = 0; x <= 10; x++)
      for (int y = 0; y < 8; y++)
        for (int z = 0; z <= 10; z++)
          world
              .getBlockAt(c.getBlockX() + x, 81 + y, c.getBlockZ() + z)
              .setType(snapshot.getOrDefault(new Cell(x, y, z), Material.AIR), false);
  }
}
