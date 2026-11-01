package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class MobCountArena {
    static final int FLOOR = 80, GRID = 6, MIN = -11, MAX = 10, PLATFORMS = GRID * GRID;
    private final World world;
    private final List<TextDisplay> labels = new ArrayList<>();
    MobCountArena(World world) { this.world = world; }
    static int platformAt(int x, int z) {
        int dx = x - MIN, dz = z - MIN;
        if (x < MIN || x > MAX || z < MIN || z > MAX || dx % 4 >= 2 || dz % 4 >= 2) return -1;
        return dz / 4 * GRID + dx / 4;
    }
    static int x(int platform) { return MIN + platform % GRID * 4; }
    static int z(int platform) { return MIN + platform / GRID * 4; }
    Location spawn(int index) { return new Location(world, x(index % PLATFORMS) + 1.5, FLOOR + 1, z(index % PLATFORMS) + 1.5); }
    Location nearest(Location location) {
        int best = 0;
        double distance = Double.MAX_VALUE;
        for (int i = 0; i < PLATFORMS; i++) {
            double dx = location.getX() - (x(i) + 1.5), dz = location.getZ() - (z(i) + 1.5);
            if (dx * dx + dz * dz < distance) { distance = dx * dx + dz * dz; best = i; }
        }
        return spawn(best);
    }
    void prepare(MobCountRound round) {
        cleanupLabels();
        for (int x = MIN; x <= MAX; x++) for (int z = MIN; z <= MAX; z++) {
            world.getBlockAt(x, FLOOR + 1, z).setType(Material.AIR, false);
            world.getBlockAt(x, FLOOR, z).setType(platformAt(x, z) < 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.SMOOTH_QUARTZ, false);
        }
        for (int i = 0; i < PLATFORMS; i++) {
            var species = round.platforms().get(i);
            world.getBlockAt(x(i), FLOOR + 1, z(i)).setType(Material.valueOf(species.head), false);
            TextDisplay label = world.spawn(new Location(world, x(i) + .5, FLOOR + 2.2, z(i) + .5), TextDisplay.class);
            label.text(Component.text(species.title, NamedTextColor.WHITE));
            label.setBillboard(Display.Billboard.CENTER);
            label.setShadowed(true);
            label.setSeeThrough(false);
            label.setViewRange(.5f);
            labels.add(label);
        }
        world.setSpawnLocation(0, FLOOR + 1, 0);
    }
    void removeGlass() {
        for (int x = MIN; x <= MAX; x++) for (int z = MIN; z <= MAX; z++) {
            if (platformAt(x, z) < 0) world.getBlockAt(x, FLOOR, z).setType(Material.AIR, false);
        }
    }
    boolean correct(Location location, MobCountRound round) {
        int platform = platformAt(location.getBlockX(), location.getBlockZ());
        // Use server position, not Player.isOnGround(), which trusts a client-controlled flag.
        boolean landed = Math.abs(location.getY() - (FLOOR + 1)) < .06
                || platform >= 0 && location.getBlockX() == x(platform) && location.getBlockZ() == z(platform)
                && Math.abs(location.getY() - (FLOOR + 1.5)) < .06;
        return location.getWorld().equals(world) && landed
                && platform >= 0 && round.platforms().get(platform) == round.answer();
    }
    void removeWrong(MobCountRound round) {
        for (int i = 0; i < PLATFORMS; i++) {
            if (round.platforms().get(i) == round.answer()) continue;
            for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
                world.getBlockAt(x(i) + dx, FLOOR, z(i) + dz).setType(Material.AIR, false);
            }
            world.getBlockAt(x(i), FLOOR + 1, z(i)).setType(Material.AIR, false);
            labels.get(i).remove();
        }
    }
    void cleanupLabels() { labels.forEach(TextDisplay::remove); labels.clear(); }
}
