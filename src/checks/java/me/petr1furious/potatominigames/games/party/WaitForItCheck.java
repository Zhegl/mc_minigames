package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.Goal.*;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Progress.*;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class WaitForItCheck {
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final List<UUID> PLAYERS = List.of(ALICE, BOB);

    public static void main(String[] args) {
        variedSamplesKeepTheRoundShort();
        scoringAndTransitions();
        orderedTasksRequireTheFullRoute();
        allGoalsCountExactlyOncePerTask();
        System.out.println("Wait For It passed");
    }

    private static void variedSamplesKeepTheRoundShort() {
        Set<WaitForItRound.Task> seen = EnumSet.noneOf(WaitForItRound.Task.class);
        for (int seed = 0; seed < 120; seed++) {
            var sample = WaitForItRound.selectTasks(new Random(seed));
            check(sample.size() == 6 && Set.copyOf(sample).size() == 6, "Six different tasks");
            check(
                    sample.stream().map(task -> task.family).distinct().count() == 4,
                    "All four families represented");
            seen.addAll(sample);
            var round = new WaitForItRound(PLAYERS, sample);
            long start = 0;
            for (var task : sample) {
                check(
                        round.task() == task && round.secondsLeft(start) == task.seconds,
                        "No gap before any task");
                start = round.deadline();
                check(
                        round.advance(start, PLAYERS).task() == task,
                        "Every sampled task runs through its deadline");
            }
            check(
                    round.finished() && start <= WaitForItRound.maxSeconds() * 20L,
                    "Round stays under hard limit");
        }
        check(
                seen.equals(EnumSet.allOf(WaitForItRound.Task.class)) && seen.size() >= 16,
                "All available tasks appear");
        check(
                WaitForItRound.maxSeconds() < 180,
                "Worst-case active time stays under three minutes");
    }

    private static void scoringAndTransitions() {
        var round = new WaitForItRound(PLAYERS, List.of(FLOWER, CRAFT, CROP, BUTTON));
        check(round.record(ALICE, POPPY, 0) == PARTIAL, "First task starts immediately");
        check(round.record(ALICE, POPPY, 1) == IGNORED, "Duplicate species cannot score");
        check(round.record(ALICE, PICKAXE, 1) == IGNORED, "Wrong goal cannot count");
        check(round.record(new UUID(0, 3), ALLIUM, 1) == IGNORED, "Spectators cannot score");
        check(
                round.record(ALICE, ALLIUM, 1) == PARTIAL && round.points(ALICE) == 0,
                "Partial bouquet");
        long craft = round.deadline();
        check(round.record(BOB, POPPY, craft) == IGNORED, "Deadline is exclusive");
        check(round.record(ALICE, CORNFLOWER, craft - 1) == COMPLETE, "Last active tick counts");
        check(round.record(ALICE, CORNFLOWER, craft - 1) == IGNORED, "One point per task");
        check(round.advance(craft - 1, PLAYERS) == null, "Others keep the full time window");
        check(
                round.advance(craft, PLAYERS).completed().equals(Set.of(ALICE)),
                "Previous results persist");
        check(round.record(BOB, PICKAXE, craft - 1) == IGNORED, "Stale events cannot count");
        check(
                round.record(BOB, PICKAXE, craft) == COMPLETE,
                "Missed players may score immediately");
        check(
                round.result(Set.copyOf(PLAYERS)).places().equals(List.of(PLAYERS)),
                "Equal scores tie");
        check(
                round.result(Set.of(BOB)).places().equals(List.of(List.of(BOB))),
                "Withdrawn players cannot place");
        check(
                round.advance(craft + 1, List.of(BOB)).task() == CRAFT,
                "Departures cannot prolong a completed task");
        check(
                round.record(BOB, WHEAT, craft + 1) == PARTIAL && round.count(ALICE, WHEAT) == 0,
                "Next task starts immediately with personal progress");
    }

    private static void orderedTasksRequireTheFullRoute() {
        for (var task : List.of(CHECKPOINTS, PARKOUR, STACK)) {
            var round = new WaitForItRound(PLAYERS, List.of(task));
            check(
                    round.record(ALICE, THIRD, 0) == IGNORED
                            && round.record(ALICE, SECOND, 0) == IGNORED,
                    "Players cannot skip steps or build out of order");
            check(round.record(ALICE, FIRST, 0) == PARTIAL, "First step counts");
            check(
                    round.record(ALICE, FIRST, 1) == IGNORED
                            && round.record(ALICE, THIRD, 1) == IGNORED,
                    "Repeat or skip cannot advance");
            check(round.record(ALICE, SECOND, 2) == PARTIAL, "Second step counts");
            check(round.record(ALICE, THIRD, 3) == COMPLETE, "All ordered steps earn the point");
        }
        var targets = new WaitForItRound(PLAYERS, List.of(TARGETS));
        check(
                targets.record(ALICE, THIRD, 0) == PARTIAL,
                "Archery targets can be hit in any order");
        check(
                targets.record(ALICE, THIRD, 1) == IGNORED,
                "One target cannot be hit for repeated points");
    }

    private static void allGoalsCountExactlyOncePerTask() {
        for (var task : WaitForItRound.Task.values()) {
            var round = new WaitForItRound(List.of(ALICE), List.of(task));
            check(round.advance(1, List.of()) == null, "An empty roster cannot skip a task");
            for (var goal : WaitForItRound.Goal.values())
                for (int i = 0; i < task.required(goal); i++)
                    check(round.record(ALICE, goal, 0) != IGNORED, "Goal accepted for " + task);
            check(round.points(ALICE) == 1, "Exactly one point for " + task);
            check(
                    round.advance(1, List.of(ALICE)).task() == task && round.finished(),
                    "Early completion ends the task");
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
