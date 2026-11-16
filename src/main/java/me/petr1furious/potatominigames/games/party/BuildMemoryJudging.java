package me.petr1furious.potatominigames.games.party;

import java.util.*;

/** Pure grading and timing, independent of the order in which the jury visits platforms. */
final class BuildMemoryJudging {
    record Grade(int correct, int total, Set<BuildMemoryPattern.Cell> errors) {
        Grade {
            errors = Set.copyOf(errors);
        }

        int percent() {
            return total == 0 ? 100 : (int) Math.round(100.0 * correct / total);
        }
    }

    static Grade grade(
            BuildMemoryPattern pattern, Map<BuildMemoryPattern.Cell, MemoryBlock> actual) {
        Set<BuildMemoryPattern.Cell> errors = new HashSet<>(pattern.blocks().keySet());
        errors.addAll(actual.keySet());
        errors.removeIf(
                cell -> MemoryBlock.sameForBuild(pattern.blocks().get(cell), actual.get(cell)));
        int correct =
                (int)
                        pattern.blocks().entrySet().stream()
                                .filter(e ->
                                        MemoryBlock.sameForBuild(
                                                e.getValue(), actual.get(e.getKey())))
                                .count();
        return new Grade(correct, pattern.blocks().size(), errors);
    }

    static final class Progress {
        private Map<BuildMemoryPattern.Cell, MemoryBlock> last = Map.of();
        private long finishedAt = Long.MAX_VALUE;

        void observe(Map<BuildMemoryPattern.Cell, MemoryBlock> actual, long tick) {
            if (BuildMemoryPattern.sameBlocks(last, actual)) return;
            last = Map.copyOf(actual);
            editedAt(tick);
        }

        void editedAt(long tick) {
            finishedAt = tick;
        }

        long finishedAt() {
            return finishedAt;
        }
    }

    static UUID loser(int stage, Map<UUID, Grade> grades, Map<UUID, Long> times) {
        int worst = grades.values().stream().mapToInt(g -> g.errors().size()).max().orElse(0);
        if (grades.isEmpty() || (worst == 0 && stage < 6)) return null;
        // Linked map order is the stable last tie-break for edits in the very same server tick.
        return grades.entrySet().stream()
                .filter(e -> e.getValue().errors().size() == worst)
                .max(Comparator.comparingLong(e -> times.getOrDefault(e.getKey(), Long.MAX_VALUE)))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
