package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import java.util.*;

record BuildMemoryPattern(Component title, Map<Cell, MemoryBlock> blocks) {
    static final int FULL_MODELS = 14;
    static final int MODEL_COUNT = 26;

    record Cell(int x, int y, int z) {}

    BuildMemoryPattern {
        blocks = Map.copyOf(blocks);
    }

    static int buildSize(int stage) {
        return stage >= 4 ? 7 : 5;
    }

    private static List<Integer> tier(int stage) {
        if (stage <= 2) return List.of(14, 15, 16, 17, 18, 19);
        if (stage == 3) return List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
        if (stage <= 5) return List.of(20, 21);
        if (stage <= 7) return List.of(22, 23);
        return List.of(24, 25);
    }

    static List<Integer> deck(Random random) {
        List<Integer> models = new ArrayList<>();
        for (int stage = 1; stage <= 8; stage++) {
            List<Integer> candidates = new ArrayList<>(tier(stage));
            candidates.removeAll(models);
            models.add(candidates.get(random.nextInt(candidates.size())));
        }
        // Keep the opening and the first advanced pair in ascending order of block count.
        for (int start : new int[] {0, 3}) {
            if (generate(start + 1, models.get(start), new Random(0)).blocks().size()
                    > generate(start + 2, models.get(start + 1), new Random(0)).blocks().size())
                Collections.swap(models, start, start + 1);
        }
        return List.copyOf(models);
    }

    static BuildMemoryPattern generate(int stage, Random random) {
        var candidates = tier(stage);
        return generate(stage, candidates.get(random.nextInt(candidates.size())), random);
    }

    static BuildMemoryPattern generate(int stage, int model, Random random) {
        if (stage <= 2 && (model < FULL_MODELS || model >= 20))
            model = FULL_MODELS + Math.floorMod(model, 6);
        String wood = List.of("OAK", "SPRUCE", "BIRCH", "CHERRY").get(random.nextInt(4));
        Material planks = Material.valueOf(wood + "_PLANKS"),
                fence = Material.valueOf(wood + "_FENCE"),
                sign = Material.valueOf(wood + "_SIGN"),
                stairs = Material.valueOf(wood + "_STAIRS"),
                slab = Material.valueOf(wood + "_SLAB");
        Material stone = random.nextBoolean() ? Material.STONE_BRICKS : Material.BRICKS;
        Material accent =
                List.of(
                                Material.RED_TERRACOTTA,
                                Material.CYAN_TERRACOTTA,
                                Material.YELLOW_TERRACOTTA,
                                Material.GREEN_TERRACOTTA)
                        .get(random.nextInt(4));
        Material light = random.nextBoolean() ? Material.LANTERN : Material.SOUL_LANTERN;
        Builder b = new Builder();
        Component title;
        switch (model) {
            case 0 -> {
                title = I18n.tr("party.rounds.build_memory.model.bench");
                for (int x = 1; x <= 3; x++) {
                    b.put(stone, x, 0, 2);
                    b.put(stairs, x, 1, 2, "facing=south");
                }
                b.put(fence, 0, 0, 2);
                b.put(fence, 4, 0, 2);
                b.put(fence, 4, 1, 2);
                b.put(fence, 4, 2, 2);
                b.put(light, 4, 3, 2);
            }
            case 1 -> {
                title = I18n.tr("party.rounds.build_memory.model.market_stall");
                for (int x = 1; x <= 3; x++) {
                    b.put(planks, x, 0, 2);
                    b.put(accent, x, 2, 2);
                }
                for (int x : new int[] {1, 3}) {
                    b.put(fence, x, 1, 2);
                    b.put(slab, x, 2, 1);
                }
                b.put(sign, 2, 1, 2, "rotation=0");
                b.put(light, random.nextBoolean() ? 1 : 3, 0, 1);
            }
            case 2 -> {
                title = I18n.tr("party.rounds.build_memory.model.chain_well");
                for (int x = 1; x <= 3; x++)
                    for (int z = 1; z <= 3; z++) if (x != 2 || z != 2) b.put(stone, x, 0, z);
                for (int x : new int[] {1, 3}) b.put(fence, x, 1, 2);
                for (int x = 1; x <= 3; x++) b.put(slab, x, 2, 2);
                b.put(Material.IRON_CHAIN, 2, 1, 2);
            }
            case 3 -> {
                title = I18n.tr("party.rounds.build_memory.model.lantern_arch");
                for (int x : new int[] {1, 3}) for (int y = 0; y <= 2; y++) b.put(stone, x, y, 2);
                for (int x = 1; x <= 3; x++) b.put(planks, x, 3, 2);
                b.put(Material.IRON_CHAIN, 2, 2, 2);
                b.put(light, 2, 1, 2, "hanging=true");
                b.put(stairs, 1, 0, 1, "facing=south");
                b.put(stairs, 3, 0, 1, "facing=south");
            }
            case 4 -> {
                title = I18n.tr("party.rounds.build_memory.model.railed_bridge");
                for (int x = 1; x <= 3; x++) {
                    b.put(planks, x, 0, 2);
                    b.put(planks, x, 0, 1);
                    b.put(planks, x, 0, 3);
                    b.put(fence, x, 1, 1);
                    b.put(fence, x, 1, 3);
                }
                b.put(stairs, 0, 0, 2, "facing=east");
                b.put(stairs, 4, 0, 2, "facing=west");
                b.put(light, 1, 2, random.nextBoolean() ? 1 : 3);
            }
            case 5 -> {
                title = I18n.tr("party.rounds.build_memory.model.mantel");
                for (int x = 1; x <= 3; x++) for (int y = 0; y <= 1; y++) b.put(stone, x, y, 3);
                b.put(stone, 1, 0, 2);
                b.put(stone, 3, 0, 2);
                for (int x = 1; x <= 3; x++) b.put(slab, x, 2, 3);
                b.put(Material.SEA_LANTERN, 2, 0, 2);
                b.put(stairs, 1, 1, 2, "facing=west");
                b.put(stairs, 3, 1, 2, "facing=east");
            }
            case 6 -> {
                title = I18n.tr("party.rounds.build_memory.model.signal_tower");
                for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) b.put(stone, x, 0, z);
                b.put(planks, 2, 1, 2);
                b.put(Material.REDSTONE_BLOCK, 2, 2, 2);
                b.put(Material.REDSTONE_LAMP, 2, 3, 2);
                b.put(stairs, 2, 0, 0, "facing=south");
            }
            case 7 -> {
                title = I18n.tr("party.rounds.build_memory.model.reading_corner");
                for (int x = 1; x <= 3; x++) {
                    b.put(Material.BOOKSHELF, x, 0, 3);
                    b.put(Material.BOOKSHELF, x, 1, 3);
                    b.put(slab, x, 2, 3);
                }
                b.put(stairs, 1, 0, 1, "facing=west");
                b.put(fence, 2, 0, 1);
                b.put(planks, 2, 1, 1);
                b.put(light, 2, 2, 1);
            }
            case 8 -> {
                title = I18n.tr("party.rounds.build_memory.model.cart");
                for (int x : new int[] {1, 3}) for (int z : new int[] {1, 3}) b.put(stone, x, 0, z);
                b.put(stone, 1, 0, 2);
                b.put(stone, 3, 0, 2);
                for (int x = 1; x <= 3; x++) b.put(planks, x, 1, 2);
                b.put(accent, 1, 2, 2);
                b.put(accent, 2, 2, 2);
                b.put(slab, 3, 2, 2);
                b.put(fence, 2, 1, 1);
                b.put(fence, 2, 1, 0);
                b.put(planks, 2, 0, 1);
                b.put(planks, 2, 0, 0);
            }
            case 9 -> {
                title = I18n.tr("party.rounds.build_memory.model.signpost");
                for (int y = 0; y <= 2; y++) b.put(planks, 2, y, 2);
                for (int x = 0; x <= 4; x++) b.put(planks, x, 2, 2);
                for (int x : new int[] {0, 4}) {
                    b.put(sign, x, 3, 2, "rotation=0");
                    b.put(stone, x, 0, 2);
                    b.put(Material.TORCH, x, 1, 2);
                }
                b.put(stairs, 2, 0, 1, "facing=south");
            }
            case 10 -> {
                title = I18n.tr("party.rounds.build_memory.model.mine_entrance");
                for (int x : new int[] {1, 3}) for (int y = 0; y <= 2; y++) b.put(planks, x, y, 2);
                b.put(planks, 2, 2, 2);
                b.put(light, 2, 1, 2, "hanging=true");
                for (int x = 1; x <= 3; x++) b.put(stone, x, 0, 3);
                b.put(Material.TORCH, 1, 1, 3);
                b.put(Material.IRON_CHAIN, 3, 1, 3);
            }
            case 11 -> {
                title = I18n.tr("party.rounds.build_memory.model.gazebo");
                for (int x : new int[] {1, 3})
                    for (int z : new int[] {1, 3}) {
                        b.put(stone, x, 0, z);
                        b.put(fence, x, 1, z);
                        b.put(slab, x, 2, z);
                    }
                for (int x = 1; x <= 3; x++) b.put(slab, x, 2, 2);
                b.put(light, 2, 0, 2);
            }
            case 12 -> {
                title = I18n.tr("party.rounds.build_memory.model.monument_steps");
                for (int x = 1; x <= 3; x++) {
                    b.put(stairs, x, 0, 1, "facing=south");
                    b.put(stone, x, 0, 2);
                    b.put(slab, x, 1, 2);
                }
                b.put(stone, 2, 0, 3);
                b.put(planks, 2, 1, 3);
                b.put(accent, 2, 2, 3);
                b.put(light, 2, 3, 3);
            }
            case 13 -> {
                title = I18n.tr("party.rounds.build_memory.model.workshop");
                for (int x = 1; x <= 3; x++) b.put(planks, x, 0, 2);
                b.put(Material.CRAFTING_TABLE, 1, 0, 2);
                b.put(stairs, 1, 0, 1, "facing=north");
                for (int x : new int[] {1, 3}) {
                    b.put(planks, x, 0, 3);
                    b.put(fence, x, 1, 3);
                    b.put(planks, x, 2, 3);
                }
                b.put(planks, 2, 2, 3);
                b.put(light, 2, 1, 3, "hanging=true");
                b.put(sign, 3, 1, 2, "rotation=0");
                b.put(stone, 0, 0, 2);
                b.put(Material.TORCH, 0, 1, 2);
            }
            case 14 -> {
                title = I18n.tr("party.rounds.build_memory.model.small_bench");
                for (int x = 1; x <= 3; x++) b.put(stairs, x, 0, 2, "facing=south");
                b.put(fence, 4, 0, 2);
                b.put(fence, 4, 1, 2);
                b.put(light, 4, 2, 2);
            }
            case 15 -> {
                title = I18n.tr("party.rounds.build_memory.model.small_arch");
                for (int x : new int[] {1, 3}) for (int y = 0; y < 2; y++) b.put(stone, x, y, 2);
                for (int x = 1; x <= 3; x++) b.put(slab, x, 2, 2);
                b.put(light, 2, 1, 2, "hanging=true");
            }
            case 16 -> {
                title = I18n.tr("party.rounds.build_memory.model.small_table");
                for (int x : new int[] {1, 3}) b.put(fence, x, 0, 2);
                for (int x = 1; x <= 3; x++) b.put(planks, x, 1, 2);
                b.put(light, 2, 2, 2);
                b.put(stairs, 2, 0, 1, "facing=north");
            }
            case 17 -> {
                title = I18n.tr("party.rounds.build_memory.model.small_sign");
                for (int y = 0; y < 3; y++) b.put(planks, 2, y, 2);
                b.put(planks, 1, 2, 2);
                b.put(sign, 1, 3, 2, "rotation=0");
                b.put(stone, 3, 0, 2);
                b.put(Material.TORCH, 3, 1, 2);
            }
            case 18 -> {
                title = I18n.tr("party.rounds.build_memory.model.small_monument");
                for (int x = 1; x <= 3; x++) b.put(stairs, x, 0, 1, "facing=south");
                for (int y = 0; y < 2; y++) b.put(stone, 2, y, 2);
                b.put(accent, 2, 2, 2);
                b.put(light, 2, 3, 2);
            }
            case 19 -> {
                title = I18n.tr("party.rounds.build_memory.model.small_lamp");
                b.put(stone, 2, 0, 2);
                b.put(fence, 2, 1, 2);
                b.put(planks, 2, 2, 2);
                b.put(planks, 3, 2, 2);
                b.put(light, 3, 1, 2, "hanging=true");
                b.put(stairs, 1, 0, 2, "facing=east");
            }
            case 20 -> {
                title = I18n.tr("party.rounds.build_memory.model.railed_bridge");
                for (int x = 0; x <= 6; x++) {
                    for (int z = 2; z <= 4; z++) b.put(planks, x, 0, z);
                    if (x > 0 && x < 6) {
                        b.put(fence, x, 1, 2);
                        b.put(fence, x, 1, 4);
                    }
                }
                b.put(stairs, 0, 0, 3, "facing=east");
                b.put(stairs, 6, 0, 3, "facing=west");
                b.put(light, 1, 2, 2);
                b.put(light, 5, 2, 4);
            }
            case 21 -> {
                title = I18n.tr("party.rounds.build_memory.model.market_stall");
                for (int x = 1; x <= 5; x++) {
                    b.put(planks, x, 0, 3);
                    b.put(stone, x, 0, 4);
                }
                for (int x : new int[] {1, 5}) for (int y = 1; y <= 2; y++) b.put(fence, x, y, 3);
                for (int x = 0; x <= 6; x++)
                    for (int z = 2; z <= 3; z++) b.put(x % 2 == 0 ? accent : planks, x, 3, z);
                b.put(sign, 2, 1, 3, "rotation=0");
                b.put(light, 4, 1, 3);
            }
            case 22 -> {
                title = I18n.tr("party.rounds.build_memory.model.gazebo");
                for (int x = 1; x <= 5; x++)
                    for (int z = 1; z <= 5; z++)
                        if (x == 1 || x == 5 || z == 1 || z == 5) {
                            b.put(stone, x, 0, z);
                            b.put(slab, x, 3, z);
                        }
                for (int x : new int[] {1, 5})
                    for (int z : new int[] {1, 5})
                        for (int y = 1; y <= 2; y++) b.put(fence, x, y, z);
                b.put(stairs, 3, 0, 0, "facing=south");
                b.put(stairs, 3, 0, 6, "facing=north");
            }
            case 23 -> {
                title = I18n.tr("party.rounds.build_memory.model.mine_entrance");
                for (int x : new int[] {1, 5})
                    for (int z = 2; z <= 4; z++) for (int y = 0; y <= 2; y++) b.put(stone, x, y, z);
                for (int x = 1; x <= 5; x++) for (int z = 2; z <= 4; z++) b.put(planks, x, 3, z);
                for (int z = 0; z <= 6; z++) b.put(planks, 3, 0, z);
                b.put(light, 3, 2, 2, "hanging=true");
                b.put(Material.IRON_CHAIN, 3, 2, 4);
            }
            case 24 -> {
                title = I18n.tr("party.rounds.build_memory.model.workshop");
                for (int x = 0; x <= 6; x++) {
                    b.put(planks, x, 0, 4);
                    b.put(planks, x, 1, 4);
                    b.put(slab, x, 2, 4);
                    b.put(planks, x, 0, 2);
                }
                for (int x : new int[] {0, 6})
                    for (int z = 2; z <= 3; z++)
                        for (int y = 1; y <= 3; y++) b.put(planks, x, y, z);
                for (int x = 1; x <= 5; x++) b.put(slab, x, 3, 3);
                b.put(Material.CRAFTING_TABLE, 1, 0, 2);
                b.put(stone, 5, 0, 2);
                b.put(light, 2, 2, 3, "hanging=true");
                b.put(sign, 4, 1, 2, "rotation=0");
                b.put(stairs, 3, 0, 1, "facing=north");
            }
            case 25 -> {
                title = I18n.tr("party.rounds.build_memory.model.reading_corner");
                for (int x = 0; x <= 6; x++) {
                    b.put(Material.BOOKSHELF, x, 0, 5);
                    b.put(Material.BOOKSHELF, x, 1, 5);
                    b.put(slab, x, 2, 5);
                }
                for (int z = 2; z <= 4; z++) {
                    b.put(Material.BOOKSHELF, 0, 0, z);
                    b.put(Material.BOOKSHELF, 0, 1, z);
                    b.put(slab, 0, 2, z);
                }
                for (int x = 2; x <= 5; x++) {
                    b.put(planks, x, 0, 2);
                    b.put(planks, x, 1, 2);
                }
                b.put(light, 2, 2, 2);
                b.put(sign, 5, 2, 2, "rotation=0");
                b.put(stairs, 3, 0, 1, "facing=north");
                b.put(stairs, 4, 0, 1, "facing=north");
            }
            default -> throw new IllegalArgumentException("Unknown model: " + model);
        }
        return new BuildMemoryPattern(title, b.blocks).rotate(random.nextInt(4));
    }

    int size() {
        return blocks.keySet().stream().anyMatch(c -> c.x() >= 5 || c.z() >= 5) ? 7 : 5;
    }

    Map<Material, Integer> inventory() {
        Map<Material, Integer> result = new EnumMap<>(Material.class);
        blocks.values().forEach(b -> result.merge(b.item(), b.itemCount(), Integer::sum));
        return Map.copyOf(result);
    }

    boolean matches(Map<Cell, MemoryBlock> actual) {
        return sameBlocks(blocks, actual);
    }

    static boolean sameBlocks(Map<Cell, MemoryBlock> expected, Map<Cell, MemoryBlock> actual) {
        return expected.size() == actual.size()
                && expected.entrySet().stream()
                        .allMatch(e ->
                                MemoryBlock.sameForBuild(e.getValue(), actual.get(e.getKey())));
    }

    BuildMemoryPattern rotate(int turns) {
        Map<Cell, MemoryBlock> rotated = new HashMap<>();
        blocks.forEach((c, block) -> {
            int x = c.x, z = c.z;
            for (int i = 0; i < Math.floorMod(turns, 4); i++) {
                int next = size() - 1 - z;
                z = x;
                x = next;
            }
            rotated.put(new Cell(x, c.y, z), block.rotate(turns));
        });
        return new BuildMemoryPattern(title, rotated);
    }

    private static final class Builder {
        final Map<Cell, MemoryBlock> blocks = new LinkedHashMap<>();

        void put(Material m, int x, int y, int z, String... properties) {
            blocks.put(new Cell(x, y, z), MemoryBlock.of(m, properties));
        }
    }
}
