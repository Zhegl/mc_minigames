package me.petr1furious.potatominigames.games.pillars;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

final class PerkDanceFloor {
    private static final int TICKS = 20 * 20;
    private static final Material[] COLORS = {
            Material.MAGENTA_CONCRETE, Material.CYAN_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.PURPLE_CONCRETE, Material.PINK_CONCRETE
    };
    private final PerkTerrain terrain;
    private List<Block> tiles = List.of();
    private List<Player> listeners = List.of();
    private long endsAt;

    PerkDanceFloor(PerkTerrain terrain) {
        this.terrain = terrain;
    }

    boolean start(Player player, long tick, double deathHeight) {
        if (!tiles.isEmpty() || player.getY() - 1 < deathHeight) return false;
        Block floor = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        // Leave the current foothold intact and put the first row against it.
        BlockFace facing = player.getFacing();
        for (int i = 0; i < 4; i++) {
            int x = facing.getModX(), z = facing.getModZ();
            var plan = new ArrayList<Block>(16);
            for (int forward = 1; forward <= 4; forward++) {
                for (int side = -1; side <= 2; side++) {
                    plan.add(floor.getRelative(forward * x - side * z, 0, forward * z + side * x));
                }
            }
            if (terrain.canLease(plan)) {
                endsAt = tick + TICKS;
                tiles = plan;
                for (int tile = 0; tile < tiles.size(); tile++) {
                    terrain.put(tiles.get(tile), color(tile, 0), endsAt, true);
                }
                Location at = plan.get(5).getLocation().add(.5, 1, .5);
                listeners = at.getWorld().getPlayers().stream().filter(p -> p.getLocation().distanceSquared(at) <= 48 * 48).toList();
                for (Player listener : listeners) {
                    listener.playSound(at, Sound.MUSIC_DISC_LAVA_CHICKEN, SoundCategory.RECORDS, 3, 1);
                }
                return true;
            }
            facing = switch (facing) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                default -> BlockFace.NORTH;
            };
        }
        return false;
    }

    private Material color(int tile, long frame) {
        return COLORS[(int) ((tile / 4 + tile % 4 * 2 + frame) % COLORS.length)];
    }

    void tick(long tick) {
        if (tiles.isEmpty()) return;
        long age = tick - endsAt + TICKS;
        if (tick >= endsAt) close();
        else if (age % 5 == 0) {
            for (int tile = 0; tile < tiles.size(); tile++) {
                terrain.recolor(tiles.get(tile), color(tile, age / 5));
            }
        }
    }

    void close() {
        for (Player listener : listeners) {
            if (listener.isOnline()) listener.stopSound(Sound.MUSIC_DISC_LAVA_CHICKEN, SoundCategory.RECORDS);
        }
        listeners = List.of();
        tiles = List.of();
    }
}
