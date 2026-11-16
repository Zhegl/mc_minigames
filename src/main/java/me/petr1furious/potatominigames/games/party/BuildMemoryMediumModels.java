package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Material;

/** Connected furniture, instruments, and small structures for the medium memory round. */
final class BuildMemoryMediumModels {
    private BuildMemoryMediumModels() {}

    static BuildMemoryPattern generate(
            int model, String wood, Material stone, Material accent, Material light) {
        Material planks = Material.valueOf(wood + "_PLANKS"),
                stairs = Material.valueOf(wood + "_STAIRS"),
                slab = Material.valueOf(wood + "_SLAB"),
                fence = Material.valueOf(wood + "_FENCE"),
                wallSign = Material.valueOf(wood + "_WALL_SIGN");
        var b = new BuildMemoryPattern.Builder();
        String key;
        switch (model) {
            case 50 -> {
                key = "medium_canopy_bed";
                for (int x = 1; x <= 3; x++)
                    for (int z = 1; z <= 2; z++) {
                        b.put(planks, x, 0, z);
                        b.put(slab, x, 3, z);
                    }
                for (int x : new int[] {1, 3})
                    for (int z = 1; z <= 2; z++) for (int y = 1; y <= 2; y++) b.put(fence, x, y, z);
                b.put(accent, 2, 1, 1);
                b.put(Material.WHITE_WOOL, 2, 1, 2);
            }
            case 51 -> {
                key = "medium_wardrobe";
                for (int x = 1; x <= 3; x++) {
                    for (int z = 2; z <= 3; z++)
                        for (int y = 0; y <= 2; y++) b.put(planks, x, y, z);
                    b.put(slab, x, 3, 2);
                }
                b.put(wallSign, 1, 1, 1, "facing=north");
                b.put(wallSign, 3, 1, 1, "facing=north");
            }
            case 52 -> {
                key = "medium_dining_set";
                for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) b.put(planks, x, 0, z);
                b.put(fence, 2, 1, 2);
                for (int x = 1; x <= 3; x++) b.put(slab, x, 2, 2);
                for (int x : new int[] {1, 3}) {
                    b.put(stairs, x, 1, 1, "facing=north");
                    b.put(accent, x, 2, 1);
                }
                b.put(stairs, 2, 1, 3, "facing=south");
                b.put(accent, 2, 2, 3);
            }
            case 53 -> {
                key = "medium_semaphore";
                for (int x = 1; x <= 3; x++) b.put(stone, x, 0, 2);
                b.put(stone, 2, 0, 1);
                b.put(stone, 2, 0, 3);
                for (int y = 1; y <= 3; y++) b.put(planks, 2, y, 2);
                b.put(accent, 3, 3, 2);
                b.put(accent, 4, 3, 2);
                b.put(Material.WHITE_WOOL, 4, 2, 2);
                b.put(stone, 1, 1, 2);
                b.put(planks, 1, 2, 2);
                b.put(light, 1, 3, 2);
            }
            case 54 -> {
                key = "medium_portcullis";
                for (int x = 0; x <= 4; x++) {
                    b.put(stone, x, 0, 2);
                    b.put(stone, x, 3, 2);
                    for (int y = 1; y <= 2; y++)
                        b.put(x == 0 || x == 4 ? stone : Material.IRON_BARS, x, y, 2);
                }
                b.put(stairs, 2, 0, 1, "facing=south");
            }
            case 55 -> {
                key = "medium_puppet_theater";
                for (int x = 0; x <= 4; x++) {
                    b.put(planks, x, 0, 2);
                    b.put(planks, x, 3, 2);
                }
                for (int x = 1; x <= 3; x++) b.put(stairs, x, 0, 1, "facing=south");
                for (int x : new int[] {0, 4}) {
                    b.put(planks, x, 1, 2);
                    b.put(accent, x, 2, 2);
                    b.put(accent, x, 2, 1);
                }
                b.put(accent, 2, 1, 2);
                b.put(fence, 2, 2, 2);
                b.put(slab, 2, 3, 1);
            }
            case 56 -> {
                key = "medium_balcony";
                for (int x = 1; x <= 3; x++) {
                    for (int y = 0; y <= 2; y++) b.put(stone, x, y, 3);
                    for (int z = 1; z <= 2; z++) b.put(planks, x, 1, z);
                    b.put(fence, x, 2, 1);
                }
                for (int x : new int[] {1, 3}) for (int z = 1; z <= 2; z++) b.put(fence, x, 0, z);
                b.put(light, 2, 3, 1);
            }
            case 57 -> {
                key = "medium_loom";
                for (int x = 1; x <= 3; x++) {
                    for (int z = 1; z <= 2; z++) b.put(planks, x, 0, z);
                    b.put(fence, x, 1, 1);
                    b.put(planks, x, 3, 2);
                }
                for (int x : new int[] {1, 3}) for (int y = 1; y <= 2; y++) b.put(planks, x, y, 2);
                b.put(accent, 2, 1, 2);
                b.put(accent, 2, 2, 2);
                b.put(slab, 2, 2, 1);
            }
            case 58 -> {
                key = "medium_hanging_bell";
                for (int x = 0; x <= 4; x++) {
                    b.put(stone, x, 0, 2);
                    b.put(planks, x, 3, 2);
                }
                for (int x : new int[] {0, 4}) for (int y = 1; y <= 2; y++) b.put(fence, x, y, 2);
                b.put(Material.GOLD_BLOCK, 2, 2, 2);
                for (int x = 1; x <= 3; x++) b.put(Material.GOLD_BLOCK, x, 1, 2);
            }
            case 59 -> {
                key = "medium_throne";
                for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) b.put(stone, x, 0, z);
                b.put(stairs, 2, 1, 2, "facing=south");
                for (int x : new int[] {1, 3}) {
                    b.put(planks, x, 1, 2);
                    b.put(slab, x, 2, 2);
                }
                for (int x = 1; x <= 3; x++) for (int y = 1; y <= 2; y++) b.put(accent, x, y, 3);
                b.put(Material.GOLD_BLOCK, 2, 3, 3);
                b.put(stairs, 2, 0, 0, "facing=south");
            }
            case 60 -> {
                key = "medium_harp";
                for (int x = 1; x <= 3; x++) {
                    b.put(planks, x, 0, 2);
                    b.put(slab, x, 0, 1);
                }
                for (int y = 1; y <= 3; y++) {
                    b.put(stone, 1, y, 2);
                    b.put(planks, 3, y, 2);
                }
                b.put(accent, 2, 3, 2);
                b.put(Material.IRON_CHAIN, 2, 1, 2);
                b.put(Material.IRON_CHAIN, 2, 2, 2);
            }
            case 61 -> {
                key = "medium_torch_altar";
                for (int x = 1; x <= 3; x++) {
                    for (int z = 1; z <= 3; z++) b.put(stone, x, 0, z);
                    b.put(stone, x, 1, 2);
                }
                b.put(accent, 2, 2, 2);
                b.put(Material.TORCH, 2, 3, 2);
                b.put(Material.TORCH, 1, 2, 2);
                b.put(Material.TORCH, 3, 2, 2);
                b.put(stairs, 2, 0, 0, "facing=south");
            }
            case 62 -> {
                key = "medium_waterwheel";
                for (int x = 1; x <= 3; x++)
                    for (int y = 0; y <= 2; y++) b.put(x == 2 && y == 1 ? stone : planks, x, y, 2);
                b.put(stone, 2, 1, 3);
                b.put(stone, 2, 0, 3);
                b.put(stone, 3, 0, 3);
                b.put(slab, 0, 1, 2);
                b.put(slab, 4, 1, 2);
                b.put(slab, 2, 3, 2);
            }
            case 63 -> {
                key = "medium_telescope";
                for (int x = 1; x <= 3; x++) b.put(stone, x, 0, 2);
                b.put(stone, 2, 0, 1);
                b.put(stone, 2, 0, 3);
                b.put(fence, 2, 1, 2);
                for (int x = 2; x <= 4; x++)
                    for (int y = 2; y <= 3; y++)
                        b.put(x == 4 ? Material.SEA_LANTERN : accent, x, y, 2);
                b.put(planks, 1, 2, 2);
            }
            case 64 -> {
                key = "medium_grandfather_clock";
                for (int x = 1; x <= 3; x++) b.put(stone, x, 0, 2);
                b.put(planks, 2, 1, 2);
                b.put(Material.SEA_LANTERN, 2, 2, 2);
                b.put(accent, 2, 3, 2);
                for (int x : new int[] {1, 3}) for (int y = 1; y <= 2; y++) b.put(fence, x, y, 2);
                b.put(stairs, 1, 3, 2, "facing=east");
                b.put(stairs, 3, 3, 2, "facing=west");
                b.put(wallSign, 2, 2, 1, "facing=north");
                b.put(Material.GOLD_BLOCK, 2, 1, 1);
            }
            case 65 -> {
                key = "medium_kennel";
                for (int x = 1; x <= 3; x++)
                    for (int z = 1; z <= 3; z++) {
                        b.put(planks, x, 0, z);
                        if (x == 2) b.put(planks, x, 2, z);
                        else b.put(stairs, x, 2, z, x == 1 ? "facing=east" : "facing=west");
                    }
                for (int x : new int[] {1, 3}) for (int z = 2; z <= 3; z++) b.put(planks, x, 1, z);
                b.put(planks, 2, 1, 3);
            }
            default -> throw new IllegalArgumentException("Unknown medium model: " + model);
        }
        return new BuildMemoryPattern(I18n.tr("party.rounds.build_memory.model." + key), b.blocks);
    }
}
