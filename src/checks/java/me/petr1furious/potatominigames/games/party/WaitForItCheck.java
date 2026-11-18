package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.Goal.*;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Progress.*;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.*;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class WaitForItCheck {
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID SPECTATOR = new UUID(0, 99);
    private static final List<UUID> PLAYERS = List.of(ALICE, BOB);
    private static final long CUTOFF = WaitForItRound.DURATION_SECONDS * 20L;

    public static void main(String[] args) {
        racersFollowTheSameCourseIndependently();
        sharedGoalCannotSkipTransition();
        oneClockAndExclusiveCutoff();
        standingsByTimeAndCompletedTasks();
        variedSamplesKeepTheRaceShort();
        orderedTasksRequireTheFullRoute();
        allGoalsCountExactlyOncePerTask();
        advancingRebuildsOnlyTheRacersBay();
        craftingDragStaysInTheGrid();
        System.out.println("Wait For It race, cutoff, rankings, bay isolation, crafting and 24-task pool passed");
    }

    private static void advancingRebuildsOnlyTheRacersBay() {
        var blocks = new PartyArenaCheck.WorldMap();
        var arena = new WaitForItArena(blocks.world, 2);
        int secondBay = arena.spawn(1).getBlockX() - arena.spawn(0).getBlockX();
        int floor = WaitForItArena.FLOOR;
        var firstWorkbench = blocks.block(7, floor + 1, 10);
        var secondWorkbench = blocks.block(secondBay + 7, floor + 1, 10);
        check(firstWorkbench.getType() == Material.AIR && secondWorkbench.getType() == Material.AIR,
                "No task fixture exists until a bay opens");
        arena.prepare(0, CHECKPOINTS, new Random(5));
        arena.prepare(1, CHECKPOINTS, new Random(7));
        check(blocks.block(3, floor, 10).getType() == Material.LIME_CONCRETE
                        && blocks.block(secondBay + 3, floor, 10).getType() == Material.LIME_CONCRETE,
                "Both bays see the same task");
        arena.prepare(0, STAIRS, new Random(8));
        check(firstWorkbench.getType() == Material.CRAFTING_TABLE
                        && secondWorkbench.getType() == Material.AIR,
                "Only the advancing racer receives the next task");
        check(blocks.block(3, floor, 10).getType() == Material.SMOOTH_STONE
                        && blocks.block(secondBay + 3, floor, 10).getType() == Material.LIME_CONCRETE,
                "A bay transition does not clear another racer's current task");
        arena.prepare(1, STAIRS, new Random(9));
        check(secondWorkbench.getType() == Material.CRAFTING_TABLE,
                "The second racer gets their workbench when they advance");
    }

    private static void craftingDragStaysInTheGrid() {
        check(WaitForItSession.craftingSlotsOnly(Set.of(1, 2, 3, 9), 10),
                "A normal right drag across matrix slots is allowed");
        check(!WaitForItSession.craftingSlotsOnly(Set.of(0, 1), 10)
                        && !WaitForItSession.craftingSlotsOnly(Set.of(9, 10), 10)
                        && !WaitForItSession.craftingSlotsOnly(Set.of(), 10),
                "Dragging across the output or player inventory is rejected");
    }

    private static void racersFollowTheSameCourseIndependently() {
        var round = new WaitForItRound(PLAYERS, List.of(FLOWER, CRAFT, CROP));
        check(
                round.task(ALICE) == null && round.task(BOB) == null && !round.running(0),
                "The countdown has no active task");
        check(
                round.record(ALICE, POPPY, 0) == IGNORED && !round.advance(ALICE),
                "Countdown input and advance are rejected");
        round.start();
        check(
                round.task(ALICE) == FLOWER && round.task(BOB) == FLOWER,
                "The same first task is available at go");
        check(
                round.record(ALICE, POPPY, -1) == IGNORED,
                "Input before the starting tick never counts");
        check(
                round.task(SPECTATOR) == null && round.record(SPECTATOR, POPPY, 0) == IGNORED,
                "Spectators cannot interact with the course");
        check(
                round.record(ALICE, POPPY, 0) == PARTIAL && round.collected(BOB) == 0,
                "Goal progress belongs to one racer");
        check(
                round.record(ALICE, POPPY, 1) == IGNORED
                        && round.record(ALICE, PICKAXE, 1) == IGNORED,
                "Repeated and unrelated goals do not count");
        check(
                !round.advance(ALICE) && round.completed(ALICE) == 0,
                "A partial task cannot advance");
        check(
                round.record(ALICE, ALLIUM, 2) == PARTIAL
                        && round.record(ALICE, CORNFLOWER, 3) == COMPLETE,
                "The last required flower clears the task");
        check(
                round.task(ALICE) == null && round.record(ALICE, PICKAXE, 3) == IGNORED,
                "The next task waits for the native event to finish");
        check(
                round.advance(ALICE) && !round.advance(ALICE) && !round.advance(BOB),
                "Only the completing racer gets one transition on the next tick");
        check(
                round.task(ALICE) == CRAFT && round.task(BOB) == FLOWER,
                "One racer can prepare task two while the opponent searches task one");
        check(
                round.completed(ALICE) == 1
                        && round.collected(ALICE) == 0
                        && round.record(ALICE, PICKAXE, 4) == COMPLETE,
                "Task two is playable on the next tick with clean progress");
        check(
                round.advance(ALICE) && round.task(ALICE) == CROP && round.task(BOB) == FLOWER,
                "One racer can lead by several tasks");
        check(
                round.record(ALICE, WHEAT, 5) == PARTIAL && round.count(BOB, WHEAT) == 0,
                "Partial progress in later tasks remains personal");
        completeTask(round, BOB, FLOWER, 200);
        check(
                round.advance(BOB) && round.task(BOB) == CRAFT && round.task(ALICE) == CROP,
                "The trailing racer receives the same course sequence");
    }

    private static void sharedGoalCannotSkipTransition() {
        var round = new WaitForItRound(PLAYERS, List.of(LADDER, BOOTS));
        round.start();
        check(round.record(ALICE, ITEM, 10) == COMPLETE, "First crafted item completes the task");
        check(
                round.record(ALICE, ITEM, 10) == IGNORED && round.completed(ALICE) == 1,
                "A second native callback cannot claim the next task's shared goal");
        check(
                round.advance(ALICE) && round.record(ALICE, ITEM, 11) == COMPLETE,
                "A fresh craft can complete the next active task");
        check(
                round.finished(ALICE) && round.finishTime(ALICE) == 11 && round.task(ALICE) == null,
                "The last goal sets the exact finish tick and ends interaction");
        check(
                !round.advance(ALICE) && round.record(ALICE, ITEM, 12) == IGNORED,
                "Finishing does not queue another task or change the result");
    }

    private static void oneClockAndExclusiveCutoff() {
        var round = new WaitForItRound(PLAYERS, List.of(CRAFT, BUTTON));
        round.start();
        check(
                round.secondsLeft(0) == WaitForItRound.DURATION_SECONDS && round.running(0),
                "The full shared race clock starts at go");
        check(
                round.secondsLeft(CUTOFF - 20) == 1 && round.secondsLeft(CUTOFF - 1) == 1,
                "The final second stays visible through the last playable tick");
        check(
                round.task(BOB) == CRAFT && !round.advance(BOB),
                "Waiting never skips the current task");
        check(
                round.record(ALICE, PICKAXE, CUTOFF - 2) == COMPLETE && round.advance(ALICE),
                "A racer can still clear task one near the shared cutoff");
        check(
                round.task(ALICE) == BUTTON && round.task(BOB) == CRAFT,
                "Two racers can reach the cutoff on different tasks");
        check(
                round.record(ALICE, BUTTON_LEFT, CUTOFF - 1) == PARTIAL
                        && round.record(ALICE, BUTTON_RIGHT, CUTOFF - 1) == COMPLETE,
                "The last playable tick can finish a racer without a new task deadline");
        check(
                round.finishTime(ALICE) == CUTOFF - 1 && round.running(CUTOFF - 1),
                "The final tick of play is recorded exactly");
        check(
                !round.running(CUTOFF)
                        && round.secondsLeft(CUTOFF) == 0
                        && round.secondsLeft(CUTOFF + 1) == 0,
                "All racers share one exclusive cutoff with a clamped timer");
        check(
                round.record(BOB, PICKAXE, CUTOFF) == IGNORED
                        && round.completed(BOB) == 0
                        && round.finishTime(BOB) == -1,
                "The cutoff cannot be extended by completing a late task");
    }

    private static void standingsByTimeAndCompletedTasks() {
        UUID tiedFinish = new UUID(0, 3);
        UUID oneTaskAndSomeProgress = new UUID(0, 4);
        UUID oneTask = new UUID(0, 5);
        UUID noTasks = new UUID(0, 6);
        var roster = List.of(ALICE, BOB, tiedFinish, oneTaskAndSomeProgress, oneTask, noTasks);
        var round = new WaitForItRound(roster, List.of(CRAFT, BUTTON));
        round.start();
        completeTask(round, oneTaskAndSomeProgress, CRAFT, 10);
        check(
                round.advance(oneTaskAndSomeProgress),
                "An unfinished racer can attempt the next task");
        completeTask(round, oneTask, CRAFT, 11);
        completeTask(round, tiedFinish, CRAFT, 80);
        check(round.advance(tiedFinish), "First transition for the tied racer");
        completeTask(round, BOB, CRAFT, 100);
        check(round.advance(BOB), "First transition for Bob");
        completeTask(round, ALICE, CRAFT, 300);
        check(round.advance(ALICE), "First transition for Alice");
        completeTask(round, ALICE, BUTTON, 400);
        completeTask(round, tiedFinish, BUTTON, 400);
        completeTask(round, BOB, BUTTON, 800);
        check(
                round.record(oneTaskAndSomeProgress, BUTTON_LEFT, CUTOFF - 1) == PARTIAL,
                "The incomplete second task remains a partial objective at cutoff");
        var result = round.result(Set.copyOf(roster));
        check(
                result.places()
                        .equals(
                                List.of(
                                        List.of(ALICE, tiedFinish),
                                        List.of(BOB),
                                        List.of(oneTaskAndSomeProgress, oneTask),
                                        List.of(noTasks))),
                "Finishers rank by time, then unfinished racers by tasks cleared, with shared"
                    + " places");
        check(
                result.place(BOB) == 3 && result.place(oneTask) == 4 && result.place(noTasks) == 6,
                "Tied groups occupy the corresponding competition places");
        var eligible = round.result(Set.of(tiedFinish, oneTask, SPECTATOR));
        check(
                eligible.places().equals(List.of(List.of(tiedFinish), List.of(oneTask)))
                        && eligible.place(ALICE) == 0
                        && eligible.place(SPECTATOR) == 0,
                "Withdrawn racers and spectators cannot place");
    }

    private static void variedSamplesKeepTheRaceShort() {
        Set<WaitForItRound.Task> seen = EnumSet.noneOf(WaitForItRound.Task.class);
        for (int seed = 0; seed < 256; seed++) {
            var sample = WaitForItRound.selectTasks(new Random(seed));
            check(sample.size() == 6 && Set.copyOf(sample).size() == 6, "Six distinct tasks");
            check(
                    sample.stream().map(task -> task.family).distinct().count()
                            == WaitForItRound.Family.values().length,
                    "Every task family is represented");
            seen.addAll(sample);
            var round = new WaitForItRound(PLAYERS, sample);
            round.start();
            long firstTick = 0;
            for (UUID racer : PLAYERS) {
                for (int stage = 0; stage < sample.size(); stage++) {
                    completeTask(round, racer, sample.get(stage), firstTick + 2 * stage);
                    check(
                            round.completed(racer) == stage + 1,
                            "Each task adds exactly one completed stage");
                    check(
                            round.advance(racer) == (stage + 1 < sample.size()),
                            "Only non-final tasks queue the next task");
                }
                firstTick += 40;
            }
            check(
                    round.finished(ALICE)
                            && round.finished(BOB)
                            && round.finishTime(ALICE) == 10
                            && round.finishTime(BOB) == 50,
                    "The last goal, without a forced wait, records each racer’s time");
        }
        check(
                seen.size() == 24 && seen.equals(EnumSet.allOf(WaitForItRound.Task.class)),
                "Every one of the 24 tasks appears across the samples");
        check(WaitForItRound.DURATION_SECONDS == 180, "Race cap remains three minutes");
    }

    private static void orderedTasksRequireTheFullRoute() {
        for (var task : List.of(CHECKPOINTS, PARKOUR, STACK, SLALOM)) {
            var round = new WaitForItRound(PLAYERS, List.of(task));
            round.start();
            check(
                    round.record(ALICE, THIRD, 0) == IGNORED
                            && round.record(ALICE, SECOND, 0) == IGNORED,
                    "Racers cannot skip steps in " + task);
            check(round.record(ALICE, FIRST, 0) == PARTIAL, "First step counts");
            check(
                    round.record(ALICE, FIRST, 1) == IGNORED
                            && round.record(ALICE, THIRD, 1) == IGNORED,
                    "Repeating or skipping cannot advance");
            check(round.record(ALICE, SECOND, 2) == PARTIAL, "Second step counts");
            check(round.record(ALICE, THIRD, 3) == COMPLETE, "All ordered steps complete the task");
        }
        var targets = new WaitForItRound(PLAYERS, List.of(TARGETS));
        targets.start();
        check(
                targets.record(ALICE, THIRD, 0) == PARTIAL,
                "Archery targets can be hit in any order");
        check(
                targets.record(ALICE, THIRD, 1) == IGNORED,
                "One target cannot be hit repeatedly for credit");
    }

    private static void allGoalsCountExactlyOncePerTask() {
        for (var task : WaitForItRound.Task.values()) {
            var round = new WaitForItRound(List.of(ALICE), List.of(task));
            round.start();
            check(!round.advance(ALICE), "An empty objective cannot skip a task");
            completeTask(round, ALICE, task, 1);
            check(
                    round.completed(ALICE) == 1
                            && round.finished(ALICE)
                            && round.finishTime(ALICE) == 1,
                    "One task is cleared and timed for " + task);
            check(
                    !round.advance(ALICE) && round.task(ALICE) == null,
                    "The final task leaves no further objective");
        }
    }

    private static void completeTask(
            WaitForItRound round, UUID player, WaitForItRound.Task task, long tick) {
        check(round.task(player) == task, "Expected active task " + task);
        int remaining = task.requiredTotal();
        for (var goal : WaitForItRound.Goal.values()) {
            for (int i = 0; i < task.required(goal); i++) {
                var progress = --remaining == 0 ? COMPLETE : PARTIAL;
                check(round.record(player, goal, tick) == progress, "Goal " + goal + " on " + task);
            }
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
