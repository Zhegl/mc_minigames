package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Material;

/** Authored seven-block-wide structures for the advanced memory rounds. */
final class BuildMemoryLargeModels {
    private BuildMemoryLargeModels() {}

    static BuildMemoryPattern generate(
            int model, String wood, Material stone, Material accent, Material light) {
        Material planks = Material.valueOf(wood + "_PLANKS");
        Material fence = Material.valueOf(wood + "_FENCE");
        Material stairs = Material.valueOf(wood + "_STAIRS");
        Material slab = Material.valueOf(wood + "_SLAB");
        var b = new BuildMemoryPattern.Builder();
        String key;
        switch (model) {
            case 66 -> {
                key = "large_watchpost";
                for (int x = 1; x <= 3; x++) {
                    b.put(stairs, x, 0, 2, "facing=south");
                    for (int z = 3; z <= 5; z++) {
                        b.put(stone, x, 0, z);
                        b.put(planks, x, 3, z);
                    }
                }
                for (int x : new int[] {1, 3})
                    for (int z : new int[] {3, 5}) {
                        b.put(fence, x, 1, z);
                        b.put(fence, x, 2, z);
                    }
                b.put(accent, 2, 1, 5);
                b.put(light, 2, 2, 4, "hanging=true");
            }
            case 67 -> {
                key = "large_fortified_gate";
                for (int x : new int[] {1, 5})
                    for (int z = 3; z <= 4; z++)
                        for (int y = 0; y <= 2; y++) b.put(y == 2 ? accent : stone, x, y, z);
                for (int x = 1; x <= 5; x++) for (int z = 3; z <= 4; z++) b.put(planks, x, 3, z);
                for (int x = 2; x <= 4; x++) b.put(stairs, x, 0, 2, "facing=south");
                for (int x : new int[] {0, 6}) {
                    b.put(stone, x, 0, 3);
                    b.put(stone, x, 1, 3);
                    b.put(light, x, 2, 3);
                }
            }
            case 68 -> {
                key = "large_roofed_bench";
                for (int x = 2; x <= 4; x++) {
                    b.put(stairs, x, 0, 3, "facing=south");
                    b.put(accent, x, 1, 4);
                }
                for (int x : new int[] {1, 5})
                    for (int z : new int[] {2, 4})
                        for (int y = 0; y <= 2; y++) b.put(fence, x, y, z);
                for (int x = 1; x <= 5; x++) for (int z = 2; z <= 4; z++) b.put(slab, x, 3, z);
            }
            case 69 -> {
                key = "large_dry_fountain";
                for (int x = 1; x <= 5; x++)
                    for (int z = 1; z <= 5; z++) {
                        if ((x == 1 || x == 5) && (z == 1 || z == 5)) b.put(stone, x, 0, z);
                        else if (z == 1) b.put(stairs, x, 0, z, "facing=north");
                        else if (z == 5) b.put(stairs, x, 0, z, "facing=south");
                        else if (x == 1) b.put(stairs, x, 0, z, "facing=west");
                        else if (x == 5) b.put(stairs, x, 0, z, "facing=east");
                        else b.put(stone, x, 0, z);
                    }
                for (int x = 2; x <= 4; x++) b.put(stone, x, 1, 3);
                b.put(accent, 3, 2, 3);
                b.put(slab, 2, 2, 3);
                b.put(slab, 4, 2, 3);
                b.put(slab, 3, 2, 2);
                b.put(slab, 3, 2, 4);
                b.put(light, 3, 3, 3);
            }
            case 70 -> {
                key = "large_raised_landing";
                for (int x = 2; x <= 4; x++) {
                    b.put(stairs, x, 0, 1, "facing=south");
                    b.put(stone, x, 0, 2);
                    b.put(stairs, x, 1, 2, "facing=south");
                    for (int z = 3; z <= 5; z++) b.put(planks, x, 1, z);
                }
                for (int x : new int[] {2, 4}) {
                    for (int z : new int[] {3, 5}) b.put(stone, x, 0, z);
                    for (int z = 3; z <= 5; z++) b.put(fence, x, 2, z);
                    b.put(light, x, 3, 5);
                }
                b.put(accent, 3, 2, 5);
            }
            case 71 -> {
                key = "large_stable_sign";
                for (int x = 1; x <= 5; x++) {
                    b.put(fence, x, 0, 1);
                    b.put(fence, x, 0, 5);
                    b.put(stairs, x, 2, 5, "facing=south");
                }
                for (int z = 2; z <= 4; z++) {
                    b.put(fence, 1, 0, z);
                    b.put(fence, 5, 0, z);
                    b.put(stone, 4, 0, z);
                }
                b.put(Material.valueOf(wood + "_FENCE_GATE"), 3, 0, 1, "facing=south");
                for (int x = 2; x <= 4; x++) b.put(planks, x, 1, 5);
                b.put(planks, 2, 0, 5);
                b.put(planks, 4, 0, 5);
                b.put(Material.valueOf(wood + "_WALL_SIGN"), 3, 1, 4, "facing=north");
                b.put(Material.HAY_BLOCK, 2, 0, 3);
                b.put(Material.HAY_BLOCK, 3, 0, 3);
                b.put(accent, 2, 1, 3);
                b.put(light, 1, 1, 5);
            }
            case 72 -> {
                key = "large_open_temple";
                for (int x = 1; x <= 5; x++)
                    for (int z = 2; z <= 4; z++) {
                        b.put(stone, x, 0, z);
                        if (x == 1 || x == 5 || z == 2 || z == 4) b.put(slab, x, 3, z);
                    }
                for (int x : new int[] {1, 5})
                    for (int z : new int[] {2, 4}) {
                        b.put(stone, x, 1, z);
                        b.put(stone, x, 2, z);
                    }
                b.put(accent, 3, 1, 3);
            }
            case 73 -> {
                key = "large_windmill";
                for (int x = 2; x <= 4; x++) {
                    b.put(stairs, x, 0, 1, "facing=south");
                    for (int z = 2; z <= 4; z++) b.put(stone, x, 0, z);
                    b.put(planks, x, 1, 3);
                    b.put(planks, x, 2, 3);
                }
                b.put(accent, 3, 3, 3);
                b.put(stairs, 2, 3, 3, "facing=east");
                b.put(stairs, 4, 3, 3, "facing=west");
                for (int x = 1; x <= 5; x++) b.put(slab, x, 2, 2);
                b.put(accent, 3, 2, 2);
                b.put(planks, 3, 1, 2);
                b.put(planks, 3, 3, 2);
                b.put(planks, 3, 1, 4);
                b.put(slab, 1, 0, 3);
                b.put(slab, 5, 0, 3);
            }
            case 74 -> {
                key = "large_railway_arch";
                for (int x : new int[] {1, 5}) {
                    for (int z = 1; z <= 5; z++) b.put(stone, x, 0, z);
                    for (int z : new int[] {2, 4}) {
                        b.put(stone, x, 1, z);
                        b.put(accent, x, 2, z);
                    }
                }
                for (int x = 1; x <= 5; x++) for (int z : new int[] {2, 4}) b.put(planks, x, 3, z);
                for (int x = 2; x <= 4; x++) b.put(planks, x, 3, 3);
                for (int z = 0; z <= 6; z++) b.put(slab, 3, 0, z);
                b.put(light, 2, 2, 2, "hanging=true");
                b.put(light, 4, 2, 2, "hanging=true");
            }
            case 75 -> {
                key = "large_roofed_stairs";
                for (int x = 2; x <= 4; x++) {
                    b.put(stairs, x, 0, 1, "facing=south");
                    for (int z = 2; z <= 4; z++) b.put(stone, x, 0, z);
                    b.put(stairs, x, 1, 2, "facing=south");
                    b.put(planks, x, 1, 3);
                    b.put(planks, x, 1, 4);
                }
                for (int x : new int[] {1, 5}) {
                    b.put(stone, x, 0, 4);
                    b.put(fence, x, 1, 4);
                    b.put(fence, x, 2, 4);
                    b.put(fence, x, 2, 3);
                }
                for (int x = 1; x <= 5; x++)
                    for (int z = 3; z <= 4; z++) b.put(x == 3 ? accent : planks, x, 3, z);
                b.put(light, 3, 2, 4, "hanging=true");
            }
            case 76 -> {
                key = "large_amphitheatre";
                for (int x = 1; x <= 5; x++) {
                    for (int z = 3; z <= 5; z++) b.put(stone, x, 0, z);
                    b.put(stairs, x, 1, 4, "facing=south");
                    b.put(planks, x, 1, 5);
                    b.put(stairs, x, 2, 5, "facing=south");
                    b.put(fence, x, 3, 5);
                }
                for (int x = 2; x <= 4; x++) b.put(planks, x, 0, 2);
                for (int x : new int[] {0, 6}) {
                    b.put(accent, x, 0, 3);
                    b.put(light, x, 1, 3);
                }
            }
            case 77 -> {
                key = "large_belfry";
                for (int x : new int[] {2, 4})
                    for (int z : new int[] {2, 4}) {
                        b.put(stone, x, 0, z);
                        b.put(fence, x, 1, z);
                        b.put(fence, x, 2, z);
                    }
                for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) b.put(planks, x, 3, z);
                b.put(Material.GOLD_BLOCK, 3, 2, 3);
                b.put(Material.IRON_CHAIN, 3, 1, 3);
                for (int x = 1; x <= 5; x++)
                    for (int z = 1; z <= 5; z++)
                        if (x == 1 || x == 5 || z == 1 || z == 5) b.put(accent, x, 0, z);
                b.put(stairs, 3, 0, 0, "facing=south");
            }
            default -> throw new IllegalArgumentException("Unknown large model: " + model);
        }
        return new BuildMemoryPattern(I18n.tr("party.rounds.build_memory.model." + key), b.blocks);
    }
}
