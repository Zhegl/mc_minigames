package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.Goal.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
        BUTTON_RIGHT,
        SPRUCE,
        BIRCH,
        COPPER,
        IRON,
        WEB,
        ITEM,
        FIRST,
        SECOND,
        THIRD
    }

    enum Family {
        SEARCH,
        GATHER,
        MAKE,
        SKILL
    }

    enum Action {
        BREAK,
        CRAFT,
        USE,
        LOOT,
        PLACE,
        WALK,
        SHOOT
    }

    enum Task {
        FLOWER(
                "flower",
                24,
                Family.GATHER,
                Action.BREAK,
                Map.of(POPPY, 1, CORNFLOWER, 1, ALLIUM, 1)),
        CRAFT("craft", 40, Family.MAKE, Action.CRAFT, Map.of(PICKAXE, 1)),
        CROP("crop", 30, Family.GATHER, Action.BREAK, Map.of(WHEAT, 3, CARROT, 3)),
        BUTTON("button", 24, Family.SEARCH, Action.USE, Map.of(BUTTON_LEFT, 1, BUTTON_RIGHT, 1)),
        LOGS("logs", 17, Family.GATHER, Action.BREAK, Map.of(SPRUCE, 2, BIRCH, 2)),
        ORES("ores", 22, Family.GATHER, Action.BREAK, Map.of(COPPER, 2, IRON, 2)),
        WEBS("webs", 21, Family.GATHER, Action.BREAK, Map.of(WEB, 6)),
        LADDER("ladder", 28, Family.MAKE, Action.CRAFT, Map.of(ITEM, 1)),
        BOOTS("boots", 27, Family.MAKE, Action.CRAFT, Map.of(ITEM, 1)),
        STACK("stack", 24, Family.MAKE, Action.PLACE, threeGoals()),
        LIGHT("light", 22, Family.MAKE, Action.PLACE, threeGoals()),
        LEVERS("levers", 19, Family.SEARCH, Action.USE, threeGoals()),
        TREASURE("treasure", 23, Family.SEARCH, Action.LOOT, Map.of(ITEM, 1)),
        CHECKPOINTS("checkpoints", 18, Family.SKILL, Action.WALK, threeGoals()),
        PARKOUR("parkour", 22, Family.SKILL, Action.WALK, threeGoals()),
        TARGETS("targets", 28, Family.SKILL, Action.SHOOT, threeGoals());

        final String key;
        final int seconds;
        final Family family;
        final Action action;
        private final Map<Goal, Integer> goals;

        Task(String key, int seconds, Family family, Action action, Map<Goal, Integer> goals) {
            this.key = key;
            this.seconds = seconds;
            this.family = family;
            this.action = action;
            this.goals = goals;
        }

        int required(Goal goal) {
            return goals.getOrDefault(goal, 0);
        }

        int requiredTotal() {
            return goals.values().stream().mapToInt(Integer::intValue).sum();
        }

        boolean ordered() {
            return this == CHECKPOINTS || this == PARKOUR || this == STACK;
        }

        private static Map<Goal, Integer> threeGoals() {
            return Map.of(FIRST, 1, SECOND, 1, THIRD, 1);
        }
    }

    enum Progress {
        IGNORED,
        PARTIAL,
        COMPLETE
    }

    record Transition(Task task, Set<UUID> completed) {}

    private static final int TICKS_PER_SECOND = 20;
    private static final List<Task> POOL = List.of(Task.values());
    static final int TASK_COUNT = 6;

    private final List<Task> tasks;
    private final Map<UUID, Integer> points = new LinkedHashMap<>();
    private final Map<UUID, EnumMap<Goal, Integer>> progress = new HashMap<>();
    private final Set<UUID> completed = new HashSet<>();
    private int taskIndex;
    private boolean finished;
    private long startedAt;
    private long deadline;

    WaitForItRound(Collection<UUID> players, List<Task> tasks) {
        this.tasks = List.copyOf(tasks);
        if (tasks.isEmpty()
                || tasks.size() > TASK_COUNT
                || new HashSet<>(tasks).size() != tasks.size())
            throw new IllegalArgumentException("Invalid Wait For It task list");
        players.forEach(id -> points.put(id, 0));
        deadline = task().seconds * TICKS_PER_SECOND;
    }

    static List<Task> selectTasks(Random random) {
        var remaining = new ArrayList<>(POOL);
        var selected = new ArrayList<Task>(TASK_COUNT);
        for (Family family : Family.values()) {
            List<Task> options = remaining.stream().filter(task -> task.family == family).toList();
            Task drawn = options.get(random.nextInt(options.size()));
            selected.add(drawn);
            remaining.remove(drawn);
        }
        Collections.shuffle(remaining, random);
        selected.addAll(remaining.subList(0, TASK_COUNT - selected.size()));
        Collections.shuffle(selected, random);
        return List.copyOf(selected);
    }

    Task task() {
        return tasks.get(taskIndex);
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
        var counts = progress.get(player);
        return counts == null ? 0 : counts.getOrDefault(goal, 0);
    }

    int collected(UUID player) {
        var counts = progress.get(player);
        return counts == null ? 0 : counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    boolean canRecord(UUID player, Goal goal, long tick) {
        if (goal == null
                || finished
                || tick < startedAt
                || tick >= deadline
                || !points.containsKey(player)
                || completed(player)
                || count(player, goal) >= task().required(goal)) return false;
        return !task().ordered()
                || switch (goal) {
                    case SECOND -> count(player, FIRST) > 0;
                    case THIRD -> count(player, SECOND) > 0;
                    default -> true;
                };
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
        if (taskIndex + 1 == tasks.size()) {
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
        return POOL.stream()
                .map(task -> task.seconds)
                .sorted(Comparator.reverseOrder())
                .limit(TASK_COUNT)
                .mapToInt(Integer::intValue)
                .sum();
    }
}
