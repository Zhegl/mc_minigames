package me.petr1furious.potatominigames.games.party;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class WaitForItRound {
    enum Task {
        FLOWER("flower", 16),
        CRAFT("craft", 25),
        CROP("crop", 18),
        BUTTON("button", 20);

        final String key;
        final int seconds;

        Task(String key, int seconds) {
            this.key = key;
            this.seconds = seconds;
        }
    }

    enum Phase {
        PREVIEW,
        ACTIVE,
        RESULT,
        FINISHED
    }

    private static final int TICKS_PER_SECOND = 20;
    static final int PREVIEW_SECONDS = 6;
    private static final int RESULT_SECONDS = 3;
    private static final List<Task> TASKS = List.of(Task.values());
    static final int TASK_COUNT = TASKS.size();

    private final Map<UUID, Integer> points = new LinkedHashMap<>();
    private final Set<UUID> completed = new HashSet<>();
    private int taskIndex;
    private Phase phase = Phase.PREVIEW;
    private long phaseStartedAt;
    private long deadline = PREVIEW_SECONDS * TICKS_PER_SECOND;

    WaitForItRound(Collection<UUID> players) {
        players.forEach(id -> points.put(id, 0));
    }

    Task task() {
        return TASKS.get(taskIndex);
    }

    int taskNumber() {
        return taskIndex + 1;
    }

    Phase phase() {
        return phase;
    }

    long deadline() {
        return deadline;
    }

    long secondsLeft(long tick) {
        return Math.max(0, deadline - tick + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    boolean completed(UUID player) {
        return completed.contains(player);
    }

    int points(UUID player) {
        return points.getOrDefault(player, 0);
    }

    boolean complete(UUID player, Task task, long tick) {
        if (phase != Phase.ACTIVE
                || task != task()
                || tick < phaseStartedAt
                || tick >= deadline
                || !points.containsKey(player)
                || !completed.add(player)) return false;
        points.computeIfPresent(player, (id, score) -> score + 1);
        return true;
    }

    boolean advance(long tick, Collection<UUID> playing) {
        if (phase == Phase.FINISHED) return false;
        if (tick < deadline && (phase != Phase.ACTIVE || !completed.containsAll(playing)))
            return false;

        phaseStartedAt = tick;
        switch (phase) {
            case PREVIEW -> {
                phase = Phase.ACTIVE;
                deadline = tick + task().seconds * TICKS_PER_SECOND;
            }
            case ACTIVE -> {
                phase = Phase.RESULT;
                deadline = tick + RESULT_SECONDS * TICKS_PER_SECOND;
            }
            case RESULT -> {
                if (taskIndex + 1 == TASK_COUNT) {
                    phase = Phase.FINISHED;
                } else {
                    taskIndex++;
                    completed.clear();
                    phase = Phase.PREVIEW;
                    deadline = tick + PREVIEW_SECONDS * TICKS_PER_SECOND;
                }
            }
            case FINISHED -> throw new IllegalStateException("Round already finished");
        }
        return true;
    }

    PartyRoundResult result(Set<UUID> eligible) {
        return PartyRoundResult.ordered(
                points.keySet().stream().filter(eligible::contains).toList(),
                Comparator.comparingInt(this::points).reversed());
    }

    static int maxSeconds() {
        return TASK_COUNT * (PREVIEW_SECONDS + RESULT_SECONDS)
                + TASKS.stream().mapToInt(task -> task.seconds).sum();
    }
}
