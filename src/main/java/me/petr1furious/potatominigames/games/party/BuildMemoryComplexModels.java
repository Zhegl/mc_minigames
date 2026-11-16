package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Material;

/** Larger silhouettes with open interiors and deliberately different support arrangements. */
final class BuildMemoryComplexModels {
    private BuildMemoryComplexModels() {}

    static BuildMemoryPattern generate(
            int model, String wood, Material stone, Material accent, Material light) {
        Material planks = Material.valueOf(wood + "_PLANKS");
        Material stairs = Material.valueOf(wood + "_STAIRS");
        Material slab = Material.valueOf(wood + "_SLAB");
        Material fence = Material.valueOf(wood + "_FENCE");
        Material gate = Material.valueOf(wood + "_FENCE_GATE");
        Material wallSign = Material.valueOf(wood + "_WALL_SIGN");
        Material log = Material.valueOf(wood + "_LOG");
        var b = new BuildMemoryPattern.Builder();
        String key;
        switch (model) {
            case 78 -> {
                key = "complex_amphitheater";
                // A recessed stage, two side benches and a raised rear row: 41 blocks.
                for (int x = 1; x <= 5; x++) {
                    b.put(stone, x, 0, 5);
                    if (x == 1 || x == 5) b.put(planks, x, 1, 5);
                    else b.put(stairs, x, 1, 5, "facing=north");
                    b.put(slab, x, 2, 5);
                }
                for (int x : new int[] {1, 5}) {
                    for (int z = 2; z <= 4; z++) b.put(stone, x, 0, z);
                    for (int z = 3; z <= 4; z++)
                        b.put(stairs, x, 1, z, x == 1 ? "facing=east" : "facing=west");
                    b.put(planks, x, 1, 2);
                    b.put(light, x, 2, 2);
                }
                rectangle(b, planks, 2, 4, 0, 2, 4);
                for (int x = 2; x <= 4; x++) b.put(stairs, x, 0, 1, "facing=south");
            }
            case 79 -> {
                key = "complex_clock_facade";
                // The projecting dial and its inscription sit between two lamp pilasters: 40.
                rectangle(b, stone, 1, 5, 0, 3, 4);
                for (int x = 1; x <= 5; x++) {
                    b.put(planks, x, 1, 4);
                    b.put(planks, x, 2, 4);
                    b.put(slab, x, 3, 4);
                }
                for (int x = 2; x <= 4; x++) {
                    b.put(accent, x, 2, 3);
                    b.put(stairs, x, 0, 2, "facing=south");
                }
                b.put(accent, 3, 1, 3);
                for (int x : new int[] {1, 5}) {
                    b.put(stone, x, 1, 3);
                    b.put(stone, x, 2, 3);
                    b.put(light, x, 3, 3);
                }
                b.put(wallSign, 3, 1, 2, "facing=north");
                b.put(Material.SMOOTH_STONE_SLAB, 3, 3, 3);
            }
            case 80 -> {
                key = "complex_train_carriage";
                // Four wheels carry a narrow carriage with windows and an entry gate: 40.
                for (int x : new int[] {1, 5})
                    for (int z = 2; z <= 3; z++) {
                        b.put(stone, x, 0, z);
                        b.put(accent, x, 2, z);
                    }
                for (int x = 2; x <= 4; x++) b.put(log, x, 0, 3, "axis=x");
                rectangle(b, planks, 1, 5, 1, 2, 3);
                rectangle(b, slab, 1, 5, 3, 2, 3);
                b.put(planks, 1, 3, 2);
                for (int x = 2; x <= 4; x++) b.put(Material.IRON_BARS, x, 2, 3);
                for (int x : new int[] {2, 4}) b.put(accent, x, 2, 2);
                b.put(gate, 3, 2, 2, "facing=north");
                b.put(wallSign, 1, 3, 1, "facing=north");
                b.put(stairs, 0, 0, 2, "facing=east");
                b.put(stairs, 6, 0, 2, "facing=west");
            }
            case 81 -> {
                key = "complex_observatory_entrance";
                // A four-column facade frames an instrument under a projecting canopy: 52.
                rectangle(b, stone, 0, 6, 0, 3, 4);
                for (int x : new int[] {0, 2, 4, 6}) {
                    b.put(stone, x, 1, 4);
                    b.put(stone, x, 2, 4);
                }
                for (int x = 0; x <= 6; x++) b.put(planks, x, 3, 4);
                for (int x : new int[] {0, 6}) {
                    b.put(stone, x, 1, 3);
                    b.put(stone, x, 2, 3);
                    b.put(slab, x, 3, 3);
                }
                for (int x = 1; x <= 5; x++) b.put(planks, x, 3, 3);
                for (int x : new int[] {1, 5}) b.put(light, x, 2, 3, "hanging=true");
                for (int x = 2; x <= 4; x++) b.put(stairs, x, 0, 2, "facing=south");
                b.put(accent, 3, 3, 2);
                b.put(wallSign, 3, 3, 1, "facing=north");
                for (int x : new int[] {2, 4}) b.put(Material.IRON_CHAIN, x, 2, 3);
                b.put(planks, 3, 1, 4);
                b.put(accent, 3, 2, 4);
                b.put(Material.IRON_BARS, 3, 2, 3);
            }
            case 82 -> {
                key = "complex_ship_stern";
                // A central keel, broad deck, raised stern and a single bow lamp: 48.
                for (int z = 1; z <= 5; z++) b.put(log, 3, 0, z, "axis=z");
                rectangle(b, planks, 1, 5, 1, 2, 4);
                for (int x = 1; x <= 5; x++) {
                    b.put(accent, x, 2, 4);
                    b.put(fence, x, 3, 4);
                }
                for (int x : new int[] {1, 5}) for (int z = 2; z <= 3; z++) b.put(fence, x, 2, z);
                for (int x = 2; x <= 4; x++) {
                    b.put(planks, x, 1, 5);
                    b.put(planks, x, 2, 5);
                    b.put(slab, x, 3, 5);
                    b.put(stairs, x, 1, 1, "facing=south");
                }
                b.put(fence, 3, 2, 1);
                b.put(light, 3, 3, 1);
            }
            case 83 -> {
                key = "complex_wayside_shrine";
                // A lit niche and offering pedestal beneath a deep shelter: 50.
                rectangle(b, stone, 1, 5, 0, 2, 4);
                for (int x = 1; x <= 5; x++) b.put(stone, x, 1, 4);
                for (int x : new int[] {1, 2, 4, 5}) b.put(accent, x, 2, 4);
                b.put(Material.SEA_LANTERN, 3, 2, 4);
                b.put(stone, 3, 1, 3);
                for (int x : new int[] {1, 5}) {
                    b.put(log, x, 1, 2);
                    b.put(log, x, 2, 2);
                }
                rectangle(b, planks, 1, 5, 3, 2, 4);
                b.put(light, 3, 2, 2, "hanging=true");
                for (int x = 2; x <= 4; x++) b.put(stairs, x, 0, 1, "facing=south");
                b.put(wallSign, 3, 1, 2, "facing=north");
            }
            case 84 -> {
                key = "complex_garden_pergola";
                // Open roof slats cross two beams, supported by four slender posts: 48.
                rectangle(b, stone, 1, 5, 0, 1, 4);
                for (int x : new int[] {1, 5})
                    for (int z : new int[] {1, 4}) {
                        b.put(fence, x, 1, z);
                        b.put(fence, x, 2, z);
                    }
                for (int x = 1; x <= 5; x++) for (int z : new int[] {1, 4}) b.put(planks, x, 3, z);
                for (int x : new int[] {1, 3, 5})
                    for (int z = 2; z <= 3; z++) b.put(planks, x, 3, z);
                for (int x : new int[] {1, 5}) b.put(light, x, 2, 2, "hanging=true");
                b.put(stairs, 2, 1, 4, "facing=north");
                b.put(stairs, 4, 1, 4, "facing=north");
            }
            case 85 -> {
                key = "complex_ruined_aqueduct";
                // Three piers, two pairs of haunches and a broken channel lip: 46.
                for (int x : new int[] {0, 3, 6}) {
                    for (int z = 2; z <= 3; z++) for (int y = 0; y <= 2; y++) b.put(stone, x, y, z);
                    b.put(stone, x, 0, 1);
                    b.put(Material.SMOOTH_STONE_SLAB, x, 3, 1);
                }
                for (int x = 0; x <= 6; x++) {
                    b.put(stone, x, 3, 2);
                    b.put(Material.SMOOTH_STONE_SLAB, x, 3, 3);
                }
                for (int x : new int[] {1, 2, 4, 5})
                    for (int z = 2; z <= 3; z++)
                        b.put(
                                Material.STONE_BRICK_STAIRS,
                                x,
                                2,
                                z,
                                x == 1 || x == 4 ? "facing=east" : "facing=west",
                                "half=top");
            }
            case 86 -> {
                key = "complex_sundial_courtyard";
                // A paved cross links the dial, corner lamps, railings and rear bench: 49.
                for (int x = 1; x <= 5; x++)
                    for (int z = 1; z <= 5; z++)
                        if (x == 1 || x == 5 || z == 1 || z == 5 || x == 3 || z == 3)
                            b.put(stone, x, 0, z);
                for (int x : new int[] {1, 5}) {
                    for (int z = 2; z <= 4; z++) b.put(fence, x, 1, z);
                    for (int z : new int[] {1, 5}) {
                        b.put(stone, x, 1, z);
                        b.put(fence, x, 2, z);
                        b.put(z == 1 ? light : slab, x, 3, z);
                    }
                }
                for (int x = 2; x <= 4; x++) {
                    b.put(stairs, x, 1, 5, "facing=north");
                    b.put(stairs, x, 0, 0, "facing=south");
                }
                b.put(stone, 3, 1, 3);
                b.put(Material.SMOOTH_STONE_SLAB, 3, 2, 3);
                b.put(stairs, 3, 3, 3, "facing=east");
                b.put(gate, 3, 1, 1, "facing=north");
            }
            case 87 -> {
                key = "complex_aviary";
                // An empty bird enclosure with barred walls, two perches and a hanging chain: 52.
                rectangle(b, stone, 1, 5, 0, 2, 4);
                for (int x : new int[] {1, 5})
                    for (int z : new int[] {2, 4}) {
                        b.put(log, x, 1, z);
                        b.put(log, x, 2, z);
                    }
                rectangle(b, planks, 1, 5, 3, 2, 4);
                for (int x = 2; x <= 4; x++)
                    for (int y = 1; y <= 2; y++) b.put(Material.IRON_BARS, x, y, 4);
                for (int x : new int[] {1, 5})
                    for (int y = 1; y <= 2; y++) b.put(Material.IRON_BARS, x, y, 3);
                b.put(gate, 3, 1, 2, "facing=north");
                b.put(Material.IRON_CHAIN, 3, 2, 3);
                for (int x : new int[] {2, 4}) b.put(fence, x, 1, 3);
            }
            case 88 -> {
                key = "complex_arched_staircase";
                // Four ascending flights pass through a transverse arch onto a landing: 49.
                for (int x = 2; x <= 4; x++) {
                    for (int z = 1; z <= 4; z++) {
                        for (int y = 0; y < z - 1; y++) b.put(stone, x, y, z);
                        if (z == 4) b.put(slab, x, 3, z);
                        else b.put(stairs, x, z - 1, z, "facing=south");
                    }
                }
                for (int x : new int[] {1, 5}) {
                    for (int z = 3; z <= 4; z++) for (int y = 0; y <= 2; y++) b.put(stone, x, y, z);
                    b.put(light, x, 3, 4);
                }
                for (int x = 1; x <= 5; x++) b.put(accent, x, 3, 3);
            }
            case 89 -> {
                key = "complex_small_fortress";
                // A hollow keep has four corner merlons and a signed front gate: 52.
                for (int x = 1; x <= 5; x++)
                    for (int z = 1; z <= 4; z++)
                        if (x == 1 || x == 5 || z == 1 || z == 4) {
                            b.put(stone, x, 0, z);
                            if (x != 3 || z != 1) b.put(stone, x, 1, z);
                        }
                for (int x : new int[] {1, 5})
                    for (int z : new int[] {1, 4}) {
                        b.put(stone, x, 2, z);
                        b.put(stone, x, 3, z);
                    }
                for (int x = 2; x <= 4; x++) {
                    b.put(Material.STONE_BRICK_SLAB, x, 2, 4);
                    b.put(planks, x, 2, 1);
                    b.put(stairs, x, 0, 0, "facing=south");
                }
                for (int x : new int[] {1, 5}) {
                    for (int z = 2; z <= 3; z++) b.put(Material.STONE_BRICK_SLAB, x, 2, z);
                    b.put(wallSign, x, 1, 0, "facing=north");
                }
                b.put(accent, 3, 3, 1);
                b.put(gate, 3, 1, 1, "facing=north");
            }
            default -> throw new IllegalArgumentException("Unknown complex model: " + model);
        }
        return new BuildMemoryPattern(I18n.tr("party.rounds.build_memory.model." + key), b.blocks);
    }

    private static void rectangle(
            BuildMemoryPattern.Builder b,
            Material material,
            int x1,
            int x2,
            int y,
            int z1,
            int z2) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) b.put(material, x, y, z);
    }
}
