package me.petr1furious.potatominigames.games.finals;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Flat, compact practice space: every objective is visible from the starting point. */
final class FinalsTutorialArena implements FinalsMapArena {
    private final World world;
    private final Location[] stations;

    FinalsTutorialArena(World world) {
        this.world = world;
        stations = new Location[] { point(-6, -5), point(6, -5) };
    }

    private Location point(double x, double z) { return new Location(world, x + .5, 65, z + .5); }
    public World world() { return world; }
    public Location center() { return point(0, 0); }
    public Location spawn(int team, int index) { return point(0, 7); }
    public Location vault() { return point(0, 0); }
    public List<Location> vaultLocations() { return List.of(vault()); }
    public Location station(int station) { return stations[station].clone(); }
    public void moveStation(int station, Location point) { stations[station] = point.clone(); }
    public List<Location> cashoutLocations() { return List.of(station(0), station(1)); }
    public void nextVault() { vault().getBlock().setType(Material.EMERALD_BLOCK, false); }
    public void nextCashout(int station) {}
    public boolean contains(Location point) {
        return point.getWorld() == world && Math.abs(point.getX()) < 13 && Math.abs(point.getZ()) < 13
                && point.getY() >= 64 && point.getY() < 100;
    }
    public boolean isCover(Block block) { return false; }
    public int stationAt(Block block) {
        for (int i = 0; i < 2; i++) if (block.equals(station(i).getBlock())) return i;
        return -1;
    }
    public boolean isVault(Block block) { return block.equals(vault().getBlock()); }
    public double borderSize() { return 27; }

    public void build() {
        for (int x = -13; x <= 13; x++) for (int z = -13; z <= 13; z++) {
            world.getBlockAt(x, 63, z).setType(Material.BEDROCK, false);
            Material floor = x == 0 || z == -5 ? Material.WHITE_CONCRETE : Material.SMOOTH_STONE;
            world.getBlockAt(x, 64, z).setType(floor, false);
            if (Math.abs(x) == 13 || Math.abs(z) == 13)
                for (int y = 65; y <= 70; y++) world.getBlockAt(x, y, z).setType(Material.GLASS, false);
        }
        for (Location station : stations) station.clone().subtract(0, 1, 0).getBlock().setType(Material.YELLOW_CONCRETE, false);
        nextVault();
        world.setSpawnLocation(spawn(0, 0));
    }
}
