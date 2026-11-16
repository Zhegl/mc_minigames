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
        for (int stage = 1; stage <= 2; stage++)
            for (int seed = 0; seed < 100; seed++)
                check(
                        BuildMemoryPattern.generate(stage, new Random(seed)).blocks().size() <= 9,
                        "First two rounds exclude models larger than nine blocks");
        Set<Component> openingModels = new HashSet<>();
        for (int seed = 0; seed < 100; seed++)
            openingModels.add(BuildMemoryPattern.generate(1, new Random(seed)).title());
        check(openingModels.size() == 6, "Opening model varies between matches");
        for (int stage = 1; stage <= 8; stage++)
            for (int seed = 0; seed < 50; seed++) {
                var p = BuildMemoryPattern.generate(stage, new Random(seed));
                check(
                        stage <= 2
                                ? p.blocks().size() <= 9
                                : p.blocks().size() >= (stage >= 4 ? 30 : 11)
                                        && p.blocks().size() <= 48,
                        "Difficulty comes from progressively larger complete models");
                check(
                        p.blocks().keySet().stream().anyMatch(c -> c.y() >= 2),
                        "Every stage starts with a three-dimensional model");
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
                    variants.size() >= 8,
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
                        BuildMemoryPattern.generate(stage, deck.get(stage - 1), new Random(seed))
                                        .blocks()
                                        .size()
                                <= 9,
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
