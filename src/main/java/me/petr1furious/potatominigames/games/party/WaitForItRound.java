package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.Goal.*;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class WaitForItRound {
    enum Goal {
        POPPY,
        CORNFLOWER,
        ALLIUM,
        PICKAXE,
        WHEAT,
        CARROT,
        BUTTON_LEFT,
        BUTTON_RIGHT
    }

    enum Task {
        FLOWER("flower", 24, Map.of(POPPY, 1, CORNFLOWER, 1, ALLIUM, 1)),
        CRAFT("craft", 40, Map.of(PICKAXE, 1)),
        CROP("crop", 30, Map.of(WHEAT, 3, CARROT, 3)),
        BUTTON("button", 24, Map.of(BUTTON_LEFT, 1, BUTTON_RIGHT, 1));

        final String key;
        final int seconds;
        private final Map<Goal, Integer> goals;

        Task(String key, int seconds, Map<Goal, Integer> goals) {
            this.key = key;
            this.seconds = seconds;
            this.goals = goals;
        }

        int required(Goal goal) {
            return goals.getOrDefault(goal, 0);
        }
    }

    enum Progress {
        IGNORED,
        PARTIAL,
        COMPLETE
    }

    record Transition(Task task, Set<UUID> completed) {}

    private static final int TICKS_PER_SECOND = 20;
    private static final List<Task> TASKS = List.of(Task.values());
    static final int TASK_COUNT = TASKS.size();

    private final Map<UUID, Integer> points = new LinkedHashMap<>();
    private final Map<UUID, EnumMap<Goal, Integer>> progress = new HashMap<>();
    private final Set<UUID> completed = new HashSet<>();
    private int taskIndex;
    private boolean finished;
    private long startedAt;
    private long deadline = Task.FLOWER.seconds * TICKS_PER_SECOND;

    WaitForItRound(Collection<UUID> players) {
        players.forEach(id -> points.put(id, 0));
    }

    Task task() {
        return TASKS.get(taskIndex);
    }

    int taskNumber() {
        return taskIndex + 1;
    }

    boolean finished() {
        return finished;
    }

    long deadline() {
        return deadline;
    }

    long secondsLeft(long tick) {
        return finished
                ? 0
                : Math.max(0, deadline - tick + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    boolean completed(UUID player) {
        return completed.contains(player);
    }

    int points(UUID player) {
        return points.getOrDefault(player, 0);
    }

    int count(UUID player, Goal goal) {
        var progressForPlayer = progress.get(player);
        return progressForPlayer == null ? 0 : progressForPlayer.getOrDefault(goal, 0);
    }

    boolean canRecord(UUID player, Goal goal, long tick) {
        return goal != null
                && !finished
                && tick >= startedAt
                && tick < deadline
                && points.containsKey(player)
                && !completed(player)
                && count(player, goal) < task().required(goal);
    }

    Progress record(UUID player, Goal goal, long tick) {
        if (!canRecord(player, goal, tick)) return Progress.IGNORED;
        var counts = progress.computeIfAbsent(player, id -> new EnumMap<>(Goal.class));
        counts.merge(goal, 1, Integer::sum);
        if (!task().goals.equals(counts)) return Progress.PARTIAL;
        completed.add(player);
        points.merge(player, 1, Integer::sum);
        return Progress.COMPLETE;
    }

    Transition advance(long tick, Collection<UUID> playing) {
        if (finished || tick < startedAt) return null;
        if (tick < deadline && (playing.isEmpty() || !completed.containsAll(playing))) return null;

        var transition = new Transition(task(), Set.copyOf(completed));
        if (taskIndex + 1 == TASK_COUNT) {
            finished = true;
        } else {
            taskIndex++;
            startedAt = tick;
            deadline = tick + task().seconds * TICKS_PER_SECOND;
            completed.clear();
            progress.clear();
        }
        return transition;
    }

    PartyRoundResult result(Set<UUID> eligible) {
        return PartyRoundResult.ordered(
                points.keySet().stream().filter(eligible::contains).toList(),
                Comparator.comparingInt(this::points).reversed());
    }

    static int maxSeconds() {
        return TASKS.stream().mapToInt(task -> task.seconds).sum();
    }
}
