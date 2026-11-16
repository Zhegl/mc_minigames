package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import java.util.*;

public final class BuildMemoryCheck {
    public static void main(String[] args) {
        judging();
        signOrientations();
        centeredModels();
        materialSilhouettes();
        smallCatalog();
        laterVariety();
        check(
                BuildMemoryPattern.buildSize(3) == 5 && BuildMemoryPattern.buildSize(4) == 7,
                "The large workspace unlocks at round four");
        for (int seed = 0; seed < 30; seed++)
            check(
                    BuildMemoryPattern.generate(4, new Random(seed)).blocks().size() >= 30,
                    "Round four already uses a full advanced model");
        for (int stage = 1; stage <= 8; stage++)
            check(
                    BuildMemorySession.buildSeconds(stage)
                            == new int[] {20, 25, 30, 35, 40, 45, 50, 55}[stage - 1],
                    "Construction starts at twenty seconds and gains five per stage");
        for (int stage = 1; stage <= 8; stage++)
            check(
                    BuildMemorySession.memorizeSeconds(stage) == 11 + stage,
                    "Memorization gains one second every round");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), spectator = UUID.randomUUID();
        check(
                !BuildMemorySession.allReady(List.of(first, second), Set.of(first, spectator)),
                "One unfinished survivor prevents early review");
        check(
                BuildMemorySession.allReady(List.of(first, second), Set.of(first, second)),
                "All survivors can finish early");
        check(
                BuildMemorySession.allReady(List.of(first), Set.of(first)),
                "A disconnected player no longer prevents review");
        check(
                !BuildMemorySession.allReady(List.of(), Set.of()),
                "Empty roster does not start review");
        check(
                BuildMemoryPattern.smallModels().stream()
                        .anyMatch(model ->
                                BuildMemoryPattern.generate(1, model, new Random(0)).blocks().size()
                                        > 9),
                "Opening rounds include recognizable simple models larger than nine blocks");
        Set<Component> openingModels = new HashSet<>();
        for (int seed = 0; seed < 1000; seed++)
            openingModels.add(BuildMemoryPattern.generate(1, new Random(seed * 104729L)).title());
        check(openingModels.size() == 36, "Thirty-six opening models vary between matches");
        for (int stage = 1; stage <= 8; stage++)
            for (int seed = 0; seed < 50; seed++) {
                var p = BuildMemoryPattern.generate(stage, new Random(seed));
                int minimumTop = stage <= 2 ? 1 : 2;
                check(
                        stage <= 2
                                ? p.blocks().size() >= 4
                                : p.blocks().size() >= (stage >= 4 ? 30 : 11)
                                        && p.blocks().size() <= 56,
                        "Difficulty comes from progressively larger complete models");
                check(
                        p.blocks().keySet().stream().anyMatch(c -> c.y() >= minimumTop),
                        "Opening models have multiple levels, later models have taller"
                                + " silhouettes");
                check(
                        !I18n.plain(p.title(), Locale.ENGLISH).isBlank()
                                && p.inventory().size() <= 12,
                        "Named model with coherent thematic materials");
                check(
                        p.inventory().values().stream().mapToInt(Integer::intValue).sum()
                                == p.blocks().size(),
                        "Exact kit");
                check(p.rotate(1).rotate(3).blocks().equals(p.blocks()), "Rotation round trip");
                for (var c : p.blocks().keySet()) {
                    check(
                            c.x() >= 0
                                    && c.x() < (stage >= 4 ? 7 : 5)
                                    && c.z() >= 0
                                    && c.z() < (stage >= 4 ? 7 : 5)
                                    && c.y() >= 0
                                    && c.y() < 4,
                            "Bounds");
                }
                Set<BuildMemoryPattern.Cell> reachable = new HashSet<>();
                p.blocks().keySet().stream().filter(c -> c.y() == 0).forEach(reachable::add);
                boolean changed;
                do {
                    changed = false;
                    for (var c : p.blocks().keySet())
                        if (!reachable.contains(c)
                                && reachable.stream()
                                        .anyMatch(v ->
                                                Math.abs(c.x() - v.x())
                                                                + Math.abs(c.y() - v.y())
                                                                + Math.abs(c.z() - v.z())
                                                        == 1)) {
                            reachable.add(c);
                            changed = true;
                        }
                } while (changed);
                check(
                        reachable.size() == p.blocks().size(),
                        "Can place every block from the floor without extra scaffolding");
                var actual = new HashMap<>(p.blocks());
                check(p.matches(actual), "Exact succeeds");
                var firstCell = actual.keySet().iterator().next();
                actual.remove(firstCell);
                check(!p.matches(actual), "One missing fails");
                actual.put(firstCell, MemoryBlock.of(Material.BEDROCK));
                check(!p.matches(actual), "One wrong fails");
            }
        Set<Material> materials = new HashSet<>();
        for (int model = 0; model < BuildMemoryPattern.MODEL_COUNT; model++) {
            Set<Map<BuildMemoryPattern.Cell, MemoryBlock>> variants = new HashSet<>();
            for (int seed = 0; seed < 40; seed++) {
                var p = BuildMemoryPattern.generate(4, model, new Random(seed * 104729L));
                variants.add(p.blocks());
                check(
                        p.blocks()
                                .keySet()
                                .equals(
                                        BuildMemoryPattern.generate(
                                                        8, model, new Random(seed * 104729L))
                                                .blocks()
                                                .keySet()),
                        "A model never gains stage-dependent random paving or posts");
                materials.addAll(p.inventory().keySet());
                for (var entry : p.blocks().entrySet()) {
                    var support = entry.getValue().support(entry.getKey());
                    check(
                            support == null || support.y() == -1 || p.blocks().containsKey(support),
                            "Every attachment has support");
                }
            }
            check(
                    variants.size() >= (model < 26 ? 8 : 4),
                    "Same model varies materials and orientation: "
                            + model
                            + " ("
                            + variants.size()
                            + ")");
        }
        for (String suffix :
                List.of("_STAIRS", "_SLAB", "_SIGN", "_FENCE", "_CHAIN", "TORCH", "LAMP"))
            check(
                    materials.stream().anyMatch(m -> m.name().endsWith(suffix)),
                    "Includes decorative family " + suffix);
        for (String family :
                List.of("CONCRETE", "WOOL", "ANDESITE", "DIORITE", "GRANITE", "SANDSTONE"))
            check(
                    materials.stream().anyMatch(m -> m.name().contains(family)),
                    "Includes requested material family " + family);
        for (int seed = 0; seed < 30; seed++) {
            var deck = BuildMemoryPattern.deck(new Random(seed));
            check(new HashSet<>(deck.subList(0, 8)).size() == 8, "No repeated model in a match");
            int previousSize = 0;
            for (int stage = 1; stage <= 8; stage++) {
                var pattern =
                        BuildMemoryPattern.generate(stage, deck.get(stage - 1), new Random(seed));
                check(
                        pattern.blocks().size() >= previousSize,
                        "Deck difficulty never drops to a smaller model");
                previousSize = pattern.blocks().size();
            }
            for (int stage = 1; stage <= 2; stage++)
                check(
                        BuildMemoryPattern.smallModels().contains(deck.get(stage - 1)),
                        "Actual game deck starts with two small models");
        }
        var stair = MemoryBlock.of(Material.OAK_STAIRS, "facing=north", "half=top");
        check(
                stair.rotate(1).properties().get("facing").equals("east"),
                "Rotates facing with geometry");
        check(stair.rotate(4).equals(stair), "Stair state rotation round trip");
        var location = new BuildMemoryPattern.Cell(2, 0, 2);
        var reference = new BuildMemoryPattern(Component.text("Stair"), Map.of(location, stair));
        check(!reference.matches(Map.of(location, stair.rotate(1))), "Wrong facing fails");
        check(
                !reference.matches(
                        Map.of(location, MemoryBlock.of(Material.OAK_STAIRS, "half=bottom"))),
                "Wrong half fails");
        check(
                MemoryBlock.of(Material.OAK_WALL_SIGN).item() == Material.OAK_SIGN,
                "Wall sign refunds a placeable sign");
        check(
                MemoryBlock.of(Material.OAK_SLAB, "type=double").itemCount() == 2,
                "Double slab refunds two items");
        System.out.println("Build memory variants, difficulty, exact kits and orientations passed");
    }

    private static void laterVariety() {
        Set<Integer> ids = new HashSet<>();
        Set<String> shapes = new HashSet<>();
        for (var group :
                List.of(
                        BuildMemoryPattern.smallModels(),
                        BuildMemoryPattern.mediumModels(),
                        BuildMemoryPattern.advancedModels())) {
            check(group.size() == 36, "Each difficulty catalog has thirty-six models");
            for (int model : group) {
                check(ids.add(model), "Difficulty catalogs do not overlap");
                var pattern = BuildMemoryPattern.generate(4, model, new Random(0));
                check(
                        pattern.rotate(1).rotate(3).blocks().equals(pattern.blocks()),
                        "Every model rotates around a stable workspace: " + model);
                check(
                        pattern.rotate(1).size() == pattern.size(),
                        "Rotation preserves the workspace size");
                check(
                        shapes.add(structureSignature(pattern)),
                        "A model is only a recolor or rotation: " + model);
                Set<BuildMemoryPattern.Cell> reachable = new HashSet<>();
                pattern.blocks().keySet().stream().filter(c -> c.y() == 0).forEach(reachable::add);
                boolean changed;
                do {
                    changed = false;
                    for (var cell : pattern.blocks().keySet())
                        if (!reachable.contains(cell)
                                && reachable.stream()
                                        .anyMatch(c ->
                                                Math.abs(c.x() - cell.x())
                                                                + Math.abs(c.y() - cell.y())
                                                                + Math.abs(c.z() - cell.z())
                                                        == 1)) {
                            reachable.add(cell);
                            changed = true;
                        }
                } while (changed);
                check(
                        reachable.size() == pattern.blocks().size(),
                        "Model needs extra scaffolding: " + model);
            }
        }
        check(
                ids.size() == BuildMemoryPattern.MODEL_COUNT,
                "Every model is assigned to a difficulty");
        Set<Component> medium = new HashSet<>(), advanced = new HashSet<>();
        for (int seed = 0; seed < 1000; seed++) {
            medium.add(BuildMemoryPattern.generate(3, new Random(seed * 104729L)).title());
            for (int stage : new int[] {4, 6, 8}) {
                var pattern = BuildMemoryPattern.generate(stage, new Random(seed * 7919L));
                advanced.add(pattern.title());
                int min = stage == 4 ? 30 : stage == 6 ? 38 : 44;
                int max = stage == 4 ? 36 : stage == 6 ? 42 : 56;
                check(
                        pattern.blocks().size() >= min && pattern.blocks().size() <= max,
                        "Advanced tier has the intended block budget: stage " + stage);
            }
        }
        check(medium.size() == 36, "Thirty-six medium models are reachable in round three");
        check(advanced.size() == 36, "Thirty-six advanced models are reachable in later rounds");
        System.out.println("Build Memory catalog: 36 simple, 36 medium, 36 advanced models");
    }

    private static void centeredModels() {
        for (int model = 0; model < BuildMemoryPattern.MODEL_COUNT; model++) {
            var pattern = BuildMemoryPattern.generate(4, model, new Random(model));
            for (int size : new int[] {pattern.size(), 7})
                for (int turn = 0; turn < 4; turn++) {
                    var centered = pattern.rotate(turn).centered(size);
                    check(
                            centered.inventory().equals(pattern.inventory()),
                            "Centering preserves the exact kit");
                    check(centered.centered(size).equals(centered), "Centering is idempotent");
                    checkCentered(centered, model);
                }
            checkCentered(pattern, model);
        }
    }

    private static void materialSilhouettes() {
        Set<String> shapes = new HashSet<>();
        for (int model = 90; model < BuildMemoryPattern.MODEL_COUNT; model++) {
            var pattern = BuildMemoryPattern.generate(4, model, new Random(0));
            String canonical = null;
            for (int turn = 0; turn < 4; turn++) {
                var cells = pattern.rotate(turn).blocks().keySet();
                int minX = cells.stream().mapToInt(BuildMemoryPattern.Cell::x).min().orElseThrow();
                int minZ = cells.stream().mapToInt(BuildMemoryPattern.Cell::z).min().orElseThrow();
                String shape =
                        cells.stream()
                                .map(c -> (c.x() - minX) + ":" + c.y() + ":" + (c.z() - minZ))
                                .sorted()
                                .toList()
                                .toString();
                if (canonical == null || shape.compareTo(canonical) < 0) canonical = shape;
            }
            check(
                    shapes.add(canonical),
                    "New material models have distinct silhouettes even without color: " + model);
        }
    }

    private static void checkCentered(BuildMemoryPattern pattern, int model) {
        var cells = pattern.blocks().keySet();
        int minX = cells.stream().mapToInt(BuildMemoryPattern.Cell::x).min().orElseThrow();
        int maxX = cells.stream().mapToInt(BuildMemoryPattern.Cell::x).max().orElseThrow();
        int minZ = cells.stream().mapToInt(BuildMemoryPattern.Cell::z).min().orElseThrow();
        int maxZ = cells.stream().mapToInt(BuildMemoryPattern.Cell::z).max().orElseThrow();
        check(
                Math.abs(minX - (pattern.size() - 1 - maxX)) <= 1
                        && Math.abs(minZ - (pattern.size() - 1 - maxZ)) <= 1,
                "Model footprint is centered in its workspace: " + model);
    }

    private static String structureSignature(BuildMemoryPattern pattern) {
        String canonical = null;
        for (int turn = 0; turn < 4; turn++) {
            var rotated = pattern.rotate(turn);
            int minX =
                    rotated.blocks().keySet().stream()
                            .mapToInt(BuildMemoryPattern.Cell::x)
                            .min()
                            .orElseThrow();
            int minZ =
                    rotated.blocks().keySet().stream()
                            .mapToInt(BuildMemoryPattern.Cell::z)
                            .min()
                            .orElseThrow();
            List<String> cells = new ArrayList<>();
            rotated.blocks()
                    .forEach((c, b) -> {
                        String material = b.material().name();
                        for (String suffix :
                                List.of(
                                        "WALL_SIGN",
                                        "FENCE_GATE",
                                        "PLANKS",
                                        "STAIRS",
                                        "SLAB",
                                        "FENCE",
                                        "SIGN",
                                        "LOG",
                                        "LEAVES"))
                            if (material.endsWith("_" + suffix)) {
                                material = suffix;
                                break;
                            }
                        var properties = new TreeMap<>(b.properties());
                        if (material.endsWith("SIGN")) {
                            properties.remove("rotation");
                            properties.remove("facing");
                        }
                        cells.add(
                                (c.x() - minX)
                                        + ":"
                                        + c.y()
                                        + ":"
                                        + (c.z() - minZ)
                                        + "="
                                        + material
                                        + properties);
                    });
            Collections.sort(cells);
            String signature = cells.toString();
            if (canonical == null || signature.compareTo(canonical) < 0) canonical = signature;
        }
        return canonical;
    }

    private static void smallCatalog() {
        Set<String> shapes = new HashSet<>();
        for (int model : BuildMemoryPattern.smallModels()) {
            var pattern = BuildMemoryPattern.generate(1, model, new Random(0));
            check(pattern.blocks().size() >= 4, "Small model block budget: " + model);
            String canonical = structureSignature(pattern);
            check(
                    shapes.add(canonical),
                    "Small models are distinct beyond wood palette, rotation and translation: "
                            + model);
        }
        check(shapes.size() == 36, "Thirty-six separate small structures");
        check(
                MemoryBlock.of(Material.OAK_LEAVES).dataString().contains("persistent=true"),
                "Tree and hedge samples cannot decay");
        var gate = MemoryBlock.of(Material.OAK_FENCE_GATE, "facing=west");
        check(!MemoryBlock.sameForBuild(gate, gate.rotate(1)), "Gate direction still matters");
        check(
                MemoryBlock.sameForBuild(gate, gate.rotate(2)),
                "Identical closed gate faces are equivalent");
        check(
                !MemoryBlock.sameForBuild(
                        gate, MemoryBlock.of(Material.OAK_FENCE_GATE, "facing=west", "open=true")),
                "Gate open state still matters");
    }

    private static void signOrientations() {
        var cell = new BuildMemoryPattern.Cell(2, 1, 2);
        for (Material material :
                List.of(
                        Material.OAK_SIGN,
                        Material.CHERRY_SIGN,
                        Material.OAK_WALL_SIGN,
                        Material.CHERRY_WALL_SIGN)) {
            var sign = MemoryBlock.of(material);
            var expected = Map.of(cell, sign);
            var pattern = new BuildMemoryPattern(Component.text("Sign"), expected);
            for (int rotation = 0; rotation < 16; rotation++) {
                var actual =
                        Map.of(
                                cell,
                                MemoryBlock.of(
                                        material,
                                        "rotation=" + rotation,
                                        "facing="
                                                + List.of("north", "south", "east", "west")
                                                        .get(rotation % 4)));
                check(pattern.matches(actual), "Sign orientation does not affect matching");
                var grade = BuildMemoryJudging.grade(pattern, actual);
                check(
                        grade.errors().isEmpty() && grade.percent() == 100 && grade.correct() == 1,
                        "Jury gives every sign direction full credit");
                var progress = new BuildMemoryJudging.Progress();
                progress.observe(expected, 10);
                progress.observe(actual, 20);
                check(
                        progress.finishedAt() == 10,
                        "Sign orientation alone cannot delay completion");
            }
            check(
                    !pattern.matches(Map.of(cell, MemoryBlock.of(Material.BIRCH_SIGN))),
                    "Sign material and attachment type still matter");
            check(
                    !pattern.matches(Map.of(new BuildMemoryPattern.Cell(1, 1, 2), sign)),
                    "Sign position still matters");
        }
        var wall = MemoryBlock.of(Material.OAK_WALL_SIGN, "facing=east");
        check(
                wall.properties().get("facing").equals("east")
                        && wall.support(cell).equals(new BuildMemoryPattern.Cell(1, 1, 2)),
                "Ignored grading orientation remains available to render and refund attachments");
    }

    private static void judging() {
        var a = new BuildMemoryPattern.Cell(0, 0, 0);
        var b = new BuildMemoryPattern.Cell(1, 0, 0);
        var c = new BuildMemoryPattern.Cell(2, 0, 0);
        var stone = MemoryBlock.of(Material.STONE);
        var pattern = new BuildMemoryPattern(Component.text("test"), Map.of(a, stone, b, stone));
        var perfect = BuildMemoryJudging.grade(pattern, pattern.blocks());
        check(
                perfect.percent() == 100 && perfect.errors().isEmpty(),
                "Exact build: 100%, zero errors");
        var missing = BuildMemoryJudging.grade(pattern, Map.of(a, stone));
        check(
                missing.percent() == 50 && missing.errors().equals(Set.of(b)),
                "Missing cell is highlighted");
        var wrong =
                BuildMemoryJudging.grade(
                        pattern, Map.of(a, stone, b, MemoryBlock.of(Material.DIRT), c, stone));
        check(
                wrong.percent() == 50 && wrong.errors().equals(Set.of(b, c)),
                "Wrong cell counts once; extra counts separately");
        UUID fast = UUID.randomUUID(), slow = UUID.randomUUID();
        Map<UUID, Long> times = Map.of(fast, 20L, slow, 40L);
        check(
                BuildMemoryJudging.loser(5, Map.of(fast, perfect, slow, perfect), times) == null,
                "Perfect rounds before six eliminate nobody");
        check(
                slow.equals(
                        BuildMemoryJudging.loser(6, Map.of(fast, perfect, slow, perfect), times)),
                "Round six eliminates the slowest perfect builder");
        check(
                fast.equals(BuildMemoryJudging.loser(1, Map.of(fast, wrong, slow, missing), times)),
                "More errors matters before speed");
        check(
                slow.equals(BuildMemoryJudging.loser(1, Map.of(fast, wrong, slow, wrong), times)),
                "Equal errors eliminate just the later finisher");
        var progress = new BuildMemoryJudging.Progress();
        progress.observe(Map.of(a, stone), 10);
        progress.observe(pattern.blocks(), 20);
        progress.observe(pattern.blocks(), 90);
        check(progress.finishedAt() == 20, "Waiting for judging does not change completion time");
        progress.observe(Map.of(a, stone), 100);
        progress.observe(pattern.blocks(), 110);
        check(
                progress.finishedAt() == 110,
                "Breaking and repairing replaces the old completion time");
        progress.observe(Map.of(a, stone), 120);
        progress.editedAt(120);
        progress.observe(pattern.blocks(), 120);
        check(
                progress.finishedAt() == 120,
                "Repair within the same tick still updates the finish time");
        progress.editedAt(130);
        progress.observe(pattern.blocks(), 130);
        check(
                progress.finishedAt() == 130,
                "Removing a temporary extra block updates completion even if the net build is"
                        + " unchanged");
    }
}
