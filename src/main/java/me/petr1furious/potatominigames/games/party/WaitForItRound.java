package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.Goal.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
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
                Family.GATHER,
                Action.BREAK,
                Map.of(POPPY, 1, CORNFLOWER, 1, ALLIUM, 1)),
        CRAFT("craft", Family.MAKE, Action.CRAFT, Map.of(PICKAXE, 1)),
        CROP("crop", Family.GATHER, Action.BREAK, Map.of(WHEAT, 3, CARROT, 3)),
        BUTTON("button", Family.SEARCH, Action.USE, Map.of(BUTTON_LEFT, 1, BUTTON_RIGHT, 1)),
        LOGS("logs", Family.GATHER, Action.BREAK, Map.of(SPRUCE, 2, BIRCH, 2)),
        ORES("ores", Family.GATHER, Action.BREAK, Map.of(COPPER, 2, IRON, 2)),
        WEBS("webs", Family.GATHER, Action.BREAK, Map.of(WEB, 6)),
        LADDER("ladder", Family.MAKE, Action.CRAFT, Map.of(ITEM, 1)),
        BOOTS("boots", Family.MAKE, Action.CRAFT, Map.of(ITEM, 1)),
        STACK("stack", Family.MAKE, Action.PLACE, threeGoals()),
        LIGHT("light", Family.MAKE, Action.PLACE, threeGoals()),
        LEVERS("levers", Family.SEARCH, Action.USE, threeGoals()),
        TREASURE("treasure", Family.SEARCH, Action.LOOT, Map.of(ITEM, 1)),
        CHECKPOINTS("checkpoints", Family.SKILL, Action.WALK, threeGoals()),
        PARKOUR("parkour", Family.SKILL, Action.WALK, threeGoals()),
        TARGETS("targets", Family.SKILL, Action.SHOOT, threeGoals()),
        EXCAVATE("excavate", Family.GATHER, Action.BREAK, threeGoals()),
        VINES("vines", Family.GATHER, Action.BREAK, threeGoals()),
        GATES("gates", Family.SEARCH, Action.USE, threeGoals()),
        BOOKS("books", Family.MAKE, Action.USE, threeGoals()),
        CANDLES("candles", Family.MAKE, Action.USE, threeGoals()),
        STAIRS("stairs", Family.MAKE, Action.CRAFT, Map.of(ITEM, 1)),
        PLANT("plant", Family.MAKE, Action.PLACE, threeGoals()),
        SLALOM("slalom", Family.SKILL, Action.WALK, threeGoals());

        final String key;
        final Family family;
        final Action action;
        private final Map<Goal, Integer> goals;

        Task(String key, Family family, Action action, Map<Goal, Integer> goals) {
            this.key = key;
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
            return this == CHECKPOINTS || this == PARKOUR || this == STACK || this == SLALOM;
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

    private static final int TICKS_PER_SECOND = 20;
    private static final List<Task> POOL = List.of(Task.values());
    static final int TASK_COUNT = 6;
    static final int DURATION_SECONDS = 180;
    private static final int RACE_TICKS = DURATION_SECONDS * TICKS_PER_SECOND;

    private final List<Task> tasks;
    private final ParkourRace race;
    private final Map<UUID, EnumMap<Goal, Integer>> progress = new HashMap<>();
    // Blocks and inventories change on the next game tick, after the native event commits.
    private final Set<UUID> advancing = new HashSet<>();
    private boolean started;

    WaitForItRound(Collection<UUID> players, List<Task> tasks) {
        this.tasks = List.copyOf(tasks);
        if (tasks.isEmpty()
                || tasks.size() > TASK_COUNT
                || new HashSet<>(tasks).size() != tasks.size())
            throw new IllegalArgumentException("Invalid Wait For It task list");
        race = new ParkourRace(players, tasks.size());
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

    void start() {
        started = true;
    }

    boolean running(long tick) {
        return started && tick >= 0 && tick < RACE_TICKS;
    }

    Task task(UUID player) {
        int completed = race.checkpoint(player);
        if (!started || completed < 0 || completed >= tasks.size() || advancing.contains(player))
            return null;
        return tasks.get(completed);
    }

    boolean advance(UUID player) {
        return advancing.remove(player);
    }

    boolean finished(UUID player) {
        return race.finished(player);
    }

    long finishTime(UUID player) {
        return race.time(player);
    }

    int completed(UUID player) {
        return Math.max(0, race.checkpoint(player));
    }

    long secondsLeft(long tick) {
        return Math.max(0, RACE_TICKS - tick + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
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
        Task task = task(player);
        if (goal == null || task == null || !running(tick)
                || count(player, goal) >= task.required(goal)) return false;
        return !task.ordered()
                || switch (goal) {
                    case SECOND -> count(player, FIRST) > 0;
                    case THIRD -> count(player, SECOND) > 0;
                    default -> true;
                };
    }

    Progress record(UUID player, Goal goal, long tick) {
        if (!canRecord(player, goal, tick)) return Progress.IGNORED;
        Task task = task(player);
        var counts = progress.computeIfAbsent(player, id -> new EnumMap<>(Goal.class));
        counts.merge(goal, 1, Integer::sum);
        if (!task.goals.equals(counts)) return Progress.PARTIAL;
        race.reach(player, race.checkpoint(player) + 1, tick);
        progress.remove(player);
        if (!finished(player)) advancing.add(player);
        return Progress.COMPLETE;
    }

    PartyRoundResult result(Set<UUID> eligible) {
        return race.result(eligible);
    }
}
