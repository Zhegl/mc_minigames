package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Material;

import java.util.List;
import java.util.Random;

/** Recognizable objects with deliberate silhouettes and material-specific details. */
final class BuildMemoryMaterialModels {
    private BuildMemoryMaterialModels() {}

    static BuildMemoryPattern generate(int model, Random random) {
        var b = new BuildMemoryPattern.Builder();
        String color = List.of("RED", "BLUE", "YELLOW", "LIME").get(random.nextInt(4));
        Material concrete = Material.valueOf(color + "_CONCRETE");
        Material wool = Material.valueOf(color + "_WOOL");
        boolean polished = random.nextBoolean();
        Material andesite = polished ? Material.POLISHED_ANDESITE : Material.ANDESITE;
        Material diorite = polished ? Material.POLISHED_DIORITE : Material.DIORITE;
        Material granite = polished ? Material.POLISHED_GRANITE : Material.GRANITE;
        String sand = random.nextBoolean() ? "SANDSTONE" : "RED_SANDSTONE";
        Material sandstone = Material.valueOf(sand);
        Material sandStairs = Material.valueOf(sand + "_STAIRS");
        Material sandSlab = Material.valueOf(sand + "_SLAB");
        String key;
        switch (model) {
            case 90 -> {
                key = "camera";
                for (int x = 0; x <= 3; x++) for (int y = 0; y <= 1; y++) b.put(concrete, x, y, 2);
                for (int x = 1; x <= 2; x++) b.put(Material.BLACK_CONCRETE, x, 1, 1);
                b.put(Material.POLISHED_ANDESITE_SLAB, 0, 2, 2);
            }
            case 91 -> {
                key = "gift";
                for (int x = 1; x <= 2; x++)
                    for (int y = 0; y <= 1; y++)
                        for (int z = 1; z <= 2; z++)
                            b.put(x == 1 ? Material.WHITE_WOOL : wool, x, y, z);
                b.put(wool, 1, 2, 1);
                b.put(Material.WHITE_WOOL, 2, 2, 2);
            }
            case 92 -> {
                key = "hammer";
                for (int y = 0; y <= 2; y++) b.put(Material.OAK_FENCE, 2, y, 2);
                for (int x = 1; x <= 3; x++) for (int z = 2; z <= 3; z++) b.put(andesite, x, 3, z);
                for (int x : new int[] {1, 3}) b.put(Material.POLISHED_ANDESITE_SLAB, x, 0, 2);
            }
            case 93 -> {
                key = "trophy";
                for (int x = 1; x <= 3; x++)
                    for (int z = 1; z <= 2; z++) {
                        b.put(diorite, x, 0, z);
                        b.put(Material.GOLD_BLOCK, x, 2, z);
                    }
                for (int z = 1; z <= 2; z++) b.put(diorite, 2, 1, z);
            }
            case 94 -> {
                key = "stone_chair";
                for (int x = 1; x <= 3; x++) {
                    for (int z = 2; z <= 3; z++) b.put(granite, x, 0, z);
                    for (int y = 1; y <= 2; y++) b.put(granite, x, y, 3);
                }
                for (int x : new int[] {1, 3}) b.put(Material.POLISHED_GRANITE_SLAB, x, 1, 2);
            }
            case 95 -> {
                key = "desert_arch";
                for (int x : new int[] {1, 3}) {
                    for (int y = 0; y <= 2; y++) b.put(sandstone, x, y, 2);
                    b.put(sandStairs, x, 0, 1, "facing=south");
                }
                for (int x = 1; x <= 3; x++) b.put(sandSlab, x, 3, 2);
            }
            case 96 -> {
                key = "robot";
                for (int x = 1; x <= 3; x++) {
                    for (int z = 1; z <= 2; z++) b.put(concrete, x, 1, z);
                    b.put(concrete, x, 2, 2);
                }
                for (int x : new int[] {1, 3})
                    for (int z = 1; z <= 2; z++) b.put(Material.GRAY_CONCRETE, x, 0, z);
                for (int x : new int[] {1, 3}) b.put(Material.BLACK_CONCRETE, x, 2, 1);
                b.put(Material.IRON_CHAIN, 2, 3, 2);
            }
            case 97 -> {
                key = "duck";
                for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) b.put(wool, x, 1, z);
                for (int x : new int[] {1, 3}) b.put(Material.ORANGE_WOOL, x, 0, 2);
                for (int y = 2; y <= 3; y++) b.put(wool, 2, y, 1);
                b.put(Material.ORANGE_WOOL, 2, 2, 0);
                b.put(wool, 2, 2, 3);
                for (int x : new int[] {0, 4}) b.put(Material.WHITE_WOOL, x, 1, 2);
            }
            case 98 -> {
                key = "anvil";
                for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) b.put(andesite, x, 0, z);
                b.put(Material.POLISHED_ANDESITE, 2, 1, 2);
                for (int x = 0; x <= 4; x++) for (int z = 1; z <= 3; z++) b.put(andesite, x, 2, z);
            }
            case 99 -> {
                key = "snowman";
                for (int x = 1; x <= 3; x++) b.put(diorite, x, 0, 2);
                for (int z : new int[] {1, 3}) b.put(diorite, 2, 0, z);
                for (int x = 1; x <= 3; x++) b.put(diorite, x, 1, 2);
                b.put(diorite, 2, 2, 2);
                b.put(Material.ORANGE_CONCRETE, 2, 2, 1);
                for (int x = 1; x <= 3; x++)
                    for (int z = 1; z <= 3; z++) b.put(Material.BLACK_WOOL, x, 3, z);
            }
            case 100 -> {
                key = "oven";
                for (int x = 1; x <= 3; x++)
                    for (int z = 1; z <= 2; z++) {
                        b.put(granite, x, 0, z);
                        b.put(Material.POLISHED_GRANITE_SLAB, x, 2, z);
                        if (x != 2 || z == 2) b.put(granite, x, 1, z);
                    }
                b.put(Material.SEA_LANTERN, 2, 1, 1);
                b.put(granite, 2, 3, 2);
            }
            case 101 -> {
                key = "sandcastle";
                for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) b.put(sandstone, x, 0, z);
                for (int x : new int[] {1, 3})
                    for (int z : new int[] {1, 3}) {
                        for (int y = 1; y <= 2; y++) b.put(sandstone, x, y, z);
                        b.put(sandSlab, x, 3, z);
                    }
                b.put(sandstone, 2, 1, 2);
                b.put(Material.OAK_FENCE, 2, 2, 2);
                b.put(wool, 2, 3, 2);
            }
            case 102 -> {
                key = "bus";
                for (int x = 0; x <= 4; x++) {
                    for (int z = 2; z <= 4; z++)
                        if (z == 3 || x == 0 || x == 4)
                            b.put(z != 3 ? Material.BLACK_CONCRETE : concrete, x, 0, z);
                    for (int z : new int[] {2, 4})
                        b.put(x == 0 || x == 4 ? concrete : Material.LIGHT_BLUE_CONCRETE, x, 1, z);
                    for (int z = 2; z <= 4; z++) b.put(concrete, x, 2, z);
                }
                for (int x : new int[] {0, 4}) b.put(Material.WHITE_CONCRETE, x, 1, 3);
            }
            case 103 -> {
                key = "sofa";
                for (int x = 1; x <= 5; x++) {
                    for (int z = 2; z <= 4; z++) b.put(wool, x, 0, z);
                    for (int y = 1; y <= 2; y++) b.put(wool, x, y, 4);
                }
                for (int x : new int[] {1, 5}) for (int z = 2; z <= 3; z++) b.put(wool, x, 1, z);
                for (int x = 2; x <= 4; x++) b.put(Material.WHITE_WOOL, x, 1, 3);
            }
            case 104 -> {
                key = "workbench";
                for (int x = 1; x <= 5; x++) {
                    for (int z = 2; z <= 4; z++) b.put(andesite, x, 0, z);
                    for (int y = 1; y <= 2; y++) b.put(andesite, x, y, 4);
                    for (int z = 2; z <= 3; z++) b.put(Material.OAK_PLANKS, x, 1, z);
                    b.put(x == 1 || x == 5 ? Material.IRON_BARS : Material.OAK_FENCE, x, 3, 4);
                }
            }
            case 105 -> {
                key = "fountain";
                for (int x = 1; x <= 5; x++)
                    for (int z = 1; z <= 5; z++) {
                        if ((x == 1 || x == 5) && (z == 1 || z == 5)) continue;
                        b.put(diorite, x, 0, z);
                        if (x == 1 || x == 5 || z == 1 || z == 5)
                            b.put(Material.POLISHED_DIORITE_SLAB, x, 1, z);
                    }
                for (int y = 1; y <= 2; y++) b.put(diorite, 3, y, 3);
                for (int x = 2; x <= 4; x++) b.put(diorite, x, 3, 3);
                for (int z : new int[] {2, 4}) b.put(diorite, 3, 3, z);
                b.put(concrete, 3, 0, 3);
            }
            case 106 -> {
                key = "elephant";
                for (int x = 1; x <= 5; x++)
                    for (int z = 2; z <= 4; z++)
                        for (int y = 1; y <= 2; y++) b.put(granite, x, y, z);
                for (int x : new int[] {1, 5})
                    for (int z : new int[] {2, 4}) b.put(granite, x, 0, z);
                for (int y = 1; y <= 2; y++) for (int z = 2; z <= 4; z++) b.put(granite, 0, y, z);
                b.put(granite, 0, 0, 3);
                for (int z : new int[] {1, 5})
                    for (int y = 1; y <= 2; y++) b.put(Material.POLISHED_GRANITE, 1, y, z);
                for (int x = 2; x <= 4; x++) b.put(granite, x, 3, 3);
                for (int z : new int[] {2, 4}) b.put(Material.BLACK_CONCRETE, 0, 2, z);
            }
            case 107 -> {
                key = "temple";
                for (int x = 1; x <= 5; x++)
                    for (int z = 1; z <= 5; z++) {
                        b.put(sandstone, x, 0, z);
                        if (x == 1 || x == 5 || z == 1 || z == 5 || z == 3)
                            b.put(sandSlab, x, 3, z);
                    }
                for (int x : new int[] {1, 5})
                    for (int z : new int[] {1, 5})
                        for (int y = 1; y <= 2; y++) b.put(sandstone, x, y, z);
            }
            default -> throw new IllegalArgumentException("Unknown material model: " + model);
        }
        return new BuildMemoryPattern(
                I18n.tr("party.memory_materials." + key), b.blocks, model >= 102 ? 7 : 5);
    }
}
