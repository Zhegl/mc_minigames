package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Material;

/** Compact furniture and workshop models for the opening rounds. */
final class BuildMemoryFurnitureModels {
    private BuildMemoryFurnitureModels() {}

    static BuildMemoryPattern generate(
            int model, String wood, Material stone, Material accent, Material light) {
        Material planks = Material.valueOf(wood + "_PLANKS"),
                stairs = Material.valueOf(wood + "_STAIRS"),
                slab = Material.valueOf(wood + "_SLAB"),
                fence = Material.valueOf(wood + "_FENCE"),
                sign = Material.valueOf(wood + "_SIGN");
        var b = new BuildMemoryPattern.Builder();
        String key;
        switch (model) {
            case 26 -> {
                key = "furniture_armchair";
                b.put(planks, 1, 0, 2);
                b.put(stairs, 2, 0, 2, "facing=south");
                b.put(planks, 3, 0, 2);
                b.put(planks, 2, 0, 3);
                b.put(slab, 1, 1, 2);
                b.put(slab, 3, 1, 2);
                b.put(accent, 2, 1, 3);
            }
            case 27 -> {
                key = "furniture_bookcase";
                for (int x = 1; x <= 2; x++) {
                    b.put(Material.BOOKSHELF, x, 0, 2);
                    b.put(Material.BOOKSHELF, x, 1, 2);
                    b.put(slab, x, 2, 2);
                }
            }
            case 28 -> {
                key = "furniture_writing_desk";
                b.put(fence, 1, 0, 2);
                b.put(planks, 2, 0, 2);
                b.put(fence, 3, 0, 2);
                for (int x = 1; x <= 3; x++) b.put(planks, x, 1, 2);
                b.put(slab, 1, 1, 3);
                b.put(stairs, 2, 0, 1, "facing=north");
                b.put(light, 3, 2, 2);
            }
            case 29 -> {
                key = "furniture_coat_stand";
                for (int x = 1; x <= 3; x++) b.put(planks, x, 0, 2);
                b.put(fence, 2, 1, 2);
                for (int x = 1; x <= 3; x++) b.put(fence, x, 2, 2);
                b.put(accent, 2, 3, 2);
            }
            case 30 -> {
                key = "furniture_cupboard";
                for (int x = 1; x <= 2; x++) {
                    b.put(planks, x, 0, 2);
                    b.put(planks, x, 1, 2);
                }
                b.put(stairs, 1, 2, 2, "facing=east");
                b.put(stairs, 2, 2, 2, "facing=west");
                b.put(planks, 1, 0, 1);
                b.put(stairs, 2, 0, 1, "facing=south");
                b.put(sign, 1, 1, 1, "rotation=0");
            }
            case 31 -> {
                key = "furniture_piano";
                for (int x = 1; x <= 3; x++) {
                    b.put(Material.BLACK_CONCRETE, x, 0, 3);
                    b.put(Material.BLACK_CONCRETE, x, 1, 3);
                    b.put(Material.QUARTZ_SLAB, x, 1, 2);
                }
            }
            case 32 -> {
                key = "furniture_oven";
                for (int x = 1; x <= 2; x++) {
                    b.put(x == 1 ? Material.MAGMA_BLOCK : stone, x, 0, 2);
                    b.put(stone, x, 0, 3);
                    b.put(stone, x, 1, 3);
                    b.put(Material.STONE_BRICK_SLAB, x, 1, 2);
                }
                b.put(stone, 2, 2, 3);
            }
            case 33 -> {
                key = "furniture_workbench";
                b.put(Material.CRAFTING_TABLE, 1, 0, 2);
                b.put(planks, 2, 0, 2);
                b.put(stone, 3, 0, 2);
                b.put(fence, 3, 1, 2);
                b.put(planks, 3, 2, 2);
                b.put(planks, 2, 2, 2);
                b.put(light, 2, 1, 2, "hanging=true");
            }
            case 34 -> {
                key = "furniture_brazier";
                for (int x = 1; x <= 3; x++) b.put(stone, x, 0, 2);
                b.put(Material.IRON_BARS, 1, 1, 2);
                b.put(Material.MAGMA_BLOCK, 2, 1, 2);
                b.put(Material.IRON_BARS, 3, 1, 2);
                b.put(Material.TORCH, 2, 2, 2);
            }
            case 35 -> {
                key = "furniture_log_rack";
                Material log = Material.valueOf(wood + "_LOG");
                for (int x = 1; x <= 3; x++) b.put(log, x, 0, 2, "axis=z");
                for (int x = 1; x <= 2; x++) b.put(log, x, 1, 2, "axis=z");
                for (int x : new int[] {0, 4}) {
                    b.put(fence, x, 0, 2);
                    b.put(fence, x, 1, 2);
                }
            }
            case 36 -> {
                key = "furniture_pedestal";
                for (int x = 1; x <= 3; x++) b.put(stone, x, 0, 2);
                b.put(planks, 2, 1, 2);
                b.put(accent, 2, 2, 2);
                b.put(slab, 2, 3, 2);
                b.put(sign, 3, 1, 2, "rotation=0");
            }
            case 37 -> {
                key = "furniture_staircase_shelf";
                b.put(slab, 1, 0, 2);
                b.put(Material.BOOKSHELF, 2, 0, 2);
                b.put(Material.BOOKSHELF, 3, 0, 2);
                b.put(slab, 2, 1, 2);
                b.put(Material.BOOKSHELF, 3, 1, 2);
                b.put(slab, 3, 2, 2);
            }
            default -> throw new IllegalArgumentException("Unknown furniture model: " + model);
        }
        return new BuildMemoryPattern(I18n.tr("party.rounds.build_memory.model." + key), b.blocks);
    }
}
