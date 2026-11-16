package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Material;

/** Small garden and street objects for the opening memory rounds. */
final class BuildMemoryGardenModels {
    private BuildMemoryGardenModels() {}

    static BuildMemoryPattern generate(
            int model, String wood, Material stone, Material accent, Material light) {
        Material planks = Material.valueOf(wood + "_PLANKS");
        Material fence = Material.valueOf(wood + "_FENCE");
        Material stairs = Material.valueOf(wood + "_STAIRS");
        Material slab = Material.valueOf(wood + "_SLAB");
        Material leaves = wood.equals("CHERRY") ? Material.CHERRY_LEAVES : Material.OAK_LEAVES;
        var b = new BuildMemoryPattern.Builder();
        String key;
        switch (model) {
            case 38 -> {
                key = "garden_tree";
                b.put(Material.valueOf(wood + "_LOG"), 2, 0, 2);
                b.put(Material.valueOf(wood + "_LOG"), 2, 1, 2);
                b.put(leaves, 2, 2, 2);
                b.put(leaves, 1, 2, 2);
                b.put(leaves, 3, 2, 2);
                b.put(leaves, 2, 2, 1);
                b.put(leaves, 2, 2, 3);
                b.put(leaves, 2, 3, 2);
            }
            case 39 -> {
                key = "garden_mushroom";
                b.put(Material.MUSHROOM_STEM, 2, 0, 2);
                b.put(Material.MUSHROOM_STEM, 2, 1, 2);
                b.put(Material.RED_MUSHROOM_BLOCK, 2, 2, 2);
                b.put(Material.RED_MUSHROOM_BLOCK, 1, 2, 2);
                b.put(Material.RED_MUSHROOM_BLOCK, 3, 2, 2);
                b.put(Material.RED_MUSHROOM_BLOCK, 1, 2, 3);
                b.put(Material.RED_MUSHROOM_BLOCK, 2, 2, 3);
            }
            case 40 -> {
                key = "garden_birdhouse";
                b.put(fence, 2, 0, 2);
                b.put(fence, 2, 1, 2);
                b.put(planks, 2, 2, 2);
                b.put(stairs, 1, 3, 2, "facing=east");
                b.put(planks, 2, 3, 2);
                b.put(stairs, 3, 3, 2, "facing=west");
                b.put(Material.valueOf(wood + "_WALL_SIGN"), 2, 2, 1, "facing=north");
            }
            case 41 -> {
                key = "garden_mailbox";
                b.put(fence, 2, 0, 2);
                b.put(accent, 2, 1, 2);
                b.put(accent, 3, 1, 2);
                b.put(stairs, 2, 2, 2, "facing=east");
                b.put(stairs, 3, 2, 2, "facing=west");
                b.put(stone, 1, 0, 2);
            }
            case 42 -> {
                key = "garden_birdbath";
                b.put(stone, 2, 0, 2);
                b.put(stone, 2, 1, 2);
                b.put(Material.SMOOTH_STONE_SLAB, 1, 2, 1);
                b.put(Material.SMOOTH_STONE_SLAB, 2, 2, 1);
                b.put(Material.SMOOTH_STONE_SLAB, 1, 2, 2);
                b.put(Material.SMOOTH_STONE_SLAB, 2, 2, 2);
            }
            case 43 -> {
                key = "garden_sundial";
                for (int x = 1; x <= 2; x++) for (int z = 1; z <= 2; z++) b.put(stone, x, 0, z);
                b.put(stone, 2, 1, 2);
                b.put(Material.SMOOTH_STONE_SLAB, 1, 1, 2);
                b.put(stairs, 2, 2, 2, "facing=east");
            }
            case 44 -> {
                key = "garden_gate";
                for (int x : new int[] {1, 3}) {
                    b.put(fence, x, 0, 2);
                    b.put(fence, x, 1, 2);
                }
                b.put(planks, 2, 0, 2);
                b.put(Material.valueOf(wood + "_FENCE_GATE"), 2, 1, 2, "facing=south");
                b.put(stairs, 2, 0, 1, "facing=south");
            }
            case 45 -> {
                key = "garden_scarecrow";
                b.put(fence, 2, 0, 2);
                b.put(Material.HAY_BLOCK, 2, 1, 2);
                b.put(Material.HAY_BLOCK, 1, 1, 2, "axis=x");
                b.put(Material.HAY_BLOCK, 3, 1, 2, "axis=x");
                b.put(Material.CARVED_PUMPKIN, 2, 2, 2, "facing=north");
                b.put(slab, 2, 3, 2);
            }
            case 46 -> {
                key = "garden_planter";
                for (int x = 1; x <= 3; x++) b.put(planks, x, 0, 2);
                b.put(planks, 1, 0, 3);
                b.put(Material.MOSS_BLOCK, 2, 0, 3);
                b.put(planks, 3, 0, 3);
                b.put(leaves, 2, 1, 3);
            }
            case 47 -> {
                key = "garden_lamp";
                b.put(stone, 2, 0, 2);
                b.put(fence, 2, 1, 2);
                b.put(planks, 2, 2, 2);
                b.put(planks, 3, 2, 2);
                b.put(planks, 3, 2, 1);
                b.put(light, 3, 1, 1, "hanging=true");
                b.put(slab, 2, 3, 2);
            }
            case 48 -> {
                key = "garden_noticeboard";
                for (int x = 1; x <= 3; x++) {
                    b.put(planks, x, 0, 2);
                    b.put(planks, x, 2, 2);
                }
                b.put(fence, 1, 1, 2);
                b.put(fence, 3, 1, 2);
                b.put(Material.valueOf(wood + "_SIGN"), 2, 3, 2, "rotation=0");
            }
            case 49 -> {
                key = "garden_hedge";
                for (int x = 1; x <= 3; x++) b.put(leaves, x, 0, 1);
                b.put(leaves, 3, 0, 2);
                b.put(leaves, 3, 0, 3);
                b.put(leaves, 3, 1, 1);
                b.put(leaves, 3, 1, 2);
            }
            default -> throw new IllegalArgumentException("Unknown garden model: " + model);
        }
        return new BuildMemoryPattern(I18n.tr("party.rounds.build_memory.model." + key), b.blocks);
    }
}
