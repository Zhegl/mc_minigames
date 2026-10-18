package me.petr1furious.potatominigames.games.warfare;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Standing inside a concrete-lined shaft grants flight. */
public class LiftHandler {

    private final int liftCheckHeight = 6;

    private final int minLiftWallRatioA = 3;
    private final int minLiftWallRatioB = 4;

    private final Map<UUID, Long> lastSoundTime = new HashMap<>();

    private final char[][][] liftPatterns = {
            { { '?', 'x', 'x', '?' }, { 'x', 'o', 'o', 'x' }, { 'x', 'o', 'o', 'x' }, { '?', 'x', 'x', '?' } },
            { { '?', '?', 'x', '?', '?' }, { '?', 'x', 'o', 'x', '?' }, { 'x', 'o', 'o', 'o', 'x' },
                    { '?', 'x', 'o', 'x', '?' }, { '?', '?', 'x', '?', '?' } } };

    private int checkLiftSide(Location location, Material liftMaterial, boolean directionUp) {
        int liftWallCount = 0;
        boolean haveLiftMaterial = false;

        for (int i = 0; i < liftCheckHeight; i++) {
            Location blockLocation = location.clone();
            if (directionUp) {
                blockLocation.add(0, i, 0);
            } else {
                blockLocation.add(0, -i, 0);
            }

            Block block = blockLocation.getBlock();
            if (block.getType() != Material.AIR) {
                liftWallCount++;
                if (block.getType() == liftMaterial) {
                    haveLiftMaterial = true;
                }
            }
        }

        if (!haveLiftMaterial && liftWallCount != 0) {
            return -1;
        }

        return liftWallCount;
    }

    private boolean isPlayerInLiftAtPosition(Location location, Material liftMaterial, int x, int z, int patternIndex) {
        for (boolean directionUp : new boolean[] { false, true }) {
            boolean good = true;

            int totalLiftWallCount = 0;
            int wallPatternCount = 0;

            for (int i = 0; i < liftPatterns[patternIndex].length; i++) {
                for (int j = 0; j < liftPatterns[patternIndex][i].length; j++) {
                    if (liftPatterns[patternIndex][i][j] == '?') {
                        continue;
                    }

                    int liftWallCount = checkLiftSide(location.clone().add(i - x, 0, j - z), liftMaterial, directionUp);
                    if (liftWallCount == -1) {
                        good = false;
                        break;
                    }

                    if (liftPatterns[patternIndex][i][j] == 'o' && liftWallCount != 0) {
                        good = false;
                        break;
                    }

                    if (liftPatterns[patternIndex][i][j] == 'x') {
                        totalLiftWallCount += liftWallCount;
                        wallPatternCount++;
                    }
                }

                if (!good) {
                    break;
                }
            }

            if (good
                    && totalLiftWallCount * minLiftWallRatioB >= (wallPatternCount * liftCheckHeight)
                            * minLiftWallRatioA) {
                return true;
            }
        }

        return false;
    }

    private boolean isPlayerInLift(Player player, Material liftMaterial, int patternIndex) {
        Location playerLocation = player.getLocation();

        for (int i = 0; i < liftPatterns[patternIndex].length; i++) {
            for (int j = 0; j < liftPatterns[patternIndex][i].length; j++) {
                if (liftPatterns[patternIndex][i][j] == 'o') {
                    if (isPlayerInLiftAtPosition(playerLocation, liftMaterial, i, j, patternIndex)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isPlayerInAnyLift(Player player, Material liftMaterial) {
        for (int i = 0; i < liftPatterns.length; i++) {
            if (isPlayerInLift(player, liftMaterial, i)) {
                return true;
            }
        }

        return false;
    }

    /** Call only while a game is running; the caller checks drone mode and lift enablement. */
    public void handleLift(Player player, Material liftMaterial) {
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        boolean playerInLift = isPlayerInAnyLift(player, liftMaterial);
        player.setAllowFlight(playerInLift);
        player.setFlying(playerInLift);

        if (playerInLift) {
            long currentTime = System.currentTimeMillis();
            long lastTime = lastSoundTime.getOrDefault(player.getUniqueId(), 0L);
            if (currentTime - lastTime >= 1000) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 4.0f, 1.0f);
                }
                lastSoundTime.put(player.getUniqueId(), currentTime);
            }
        }
    }

    public void clear() {
        lastSoundTime.clear();
    }
}
