package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.Phase.ACTIVE;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Phase.FINISHED;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Phase.PREVIEW;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Phase.RESULT;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.BUTTON;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.CRAFT;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.CROP;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.FLOWER;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WaitForItCheck {
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CHRIS = new UUID(0, 3);
    private static final List<UUID> PLAYERS = List.of(ALICE, BOB, CHRIS);

    public static void main(String[] args) {
        actionsOnlyScoreDuringTheirOwnTask();
        missingOneTaskLeavesTheNextTaskPlayable();
        completingTheSameNumberOfTasksTiesRegardlessOfSpeed();
        soloPlayersSeeAllFourTasks();
        allTimeoutsFinishWithinTheRoundDuration();
        System.out.println("Wait For It passed");
    }

    private static void actionsOnlyScoreDuringTheirOwnTask() {
        var round = new WaitForItRound(PLAYERS);
        check(round.phase() == PREVIEW && round.task() == FLOWER, "Flower preview comes first");
        check(
                round.secondsLeft(1) == WaitForItRound.PREVIEW_SECONDS,
                "Preview countdown rounds up");
        check(!round.complete(ALICE, FLOWER, round.deadline() - 1), "Preview cannot award points");

        long start = round.deadline();
        advanceAtDeadline(round, PLAYERS);
        check(round.phase() == ACTIVE, "Task opens when the preview ends");
        check(
                !round.complete(ALICE, FLOWER, start - 1),
                "An action from the preview stays invalid");
        check(!round.complete(ALICE, CRAFT, round.deadline() - 2), "Wrong action cannot score");
        check(
                !round.complete(new UUID(0, 4), FLOWER, round.deadline() - 2),
                "Spectators cannot score");
        check(!round.complete(BOB, FLOWER, round.deadline()), "Actions at timeout are too late");
        check(round.complete(ALICE, FLOWER, round.deadline() - 1), "Last tick counts");
        check(
                !round.complete(ALICE, FLOWER, round.deadline() - 1),
                "One point per player per task");
        check(round.points(ALICE) == 1 && round.points(BOB) == 0, "Each score is independent");
    }

    private static void missingOneTaskLeavesTheNextTaskPlayable() {
        var round = new WaitForItRound(PLAYERS);
        advanceAtDeadline(round, PLAYERS);
        advanceAtDeadline(round, PLAYERS);
        check(
                round.phase() == RESULT && !round.completed(BOB),
                "A missed task gets a result phase");
        advanceAtDeadline(round, PLAYERS);
        check(round.task() == CRAFT && round.phase() == PREVIEW, "Craft always follows Flower");
        advanceAtDeadline(round, PLAYERS);
        check(round.complete(BOB, CRAFT, round.deadline() - 1), "A player may score after a miss");
        check(round.points(BOB) == 1, "Later success earns a point");
        advanceAtDeadline(round, PLAYERS);
        advanceAtDeadline(round, PLAYERS);
        check(round.task() == CROP && round.phase() == PREVIEW, "Crop always follows Craft");
        check(!round.completed(BOB), "Only task completion is reset between tasks");
    }

    private static void completingTheSameNumberOfTasksTiesRegardlessOfSpeed() {
        var round = new WaitForItRound(PLAYERS);
        advanceAtDeadline(round, PLAYERS);
        long ends = round.deadline();
        check(round.complete(BOB, FLOWER, ends - 40), "Early point counts");
        check(round.complete(ALICE, FLOWER, ends - 1), "Late point counts");
        check(!round.advance(ends - 1, PLAYERS), "An unfinished player keeps the full window");
        check(round.advance(ends - 1, List.of(ALICE, BOB)), "Departure cannot make everyone wait");
        check(round.phase() == RESULT, "Everybody finishing leads to feedback");
        check(
                round.result(Set.copyOf(PLAYERS))
                        .places()
                        .equals(List.of(List.of(ALICE, BOB), List.of(CHRIS))),
                "Identical scores share the same place even when completion times differ");
        check(
                round.result(Set.of(BOB, CHRIS))
                        .places()
                        .equals(List.of(List.of(BOB), List.of(CHRIS))),
                "Withdrawn players cannot occupy a place");
    }

    private static void soloPlayersSeeAllFourTasks() {
        var round = new WaitForItRound(List.of(ALICE));
        for (var task : List.of(FLOWER, CRAFT, CROP, BUTTON)) {
            check(round.phase() == PREVIEW && round.task() == task, "Every task gets a preview");
            long start = round.deadline();
            advanceAtDeadline(round, List.of(ALICE));
            check(round.complete(ALICE, task, start), "A solo player can complete " + task);
            check(round.advance(start, List.of(ALICE)), "Solo completion starts feedback");
            advanceAtDeadline(round, List.of(ALICE));
        }
        check(
                round.phase() == FINISHED && round.points(ALICE) == 4,
                "A perfect round earns four points");
        check(!round.complete(ALICE, BUTTON, round.deadline()), "Finished round is closed");
    }

    private static void allTimeoutsFinishWithinTheRoundDuration() {
        var round = new WaitForItRound(PLAYERS);
        for (int step = 0; step < 12; step++) advanceAtDeadline(round, PLAYERS);
        check(round.phase() == FINISHED, "Four consecutive misses still finish the round");
        check(
                round.deadline() == WaitForItRound.maxSeconds() * 20L,
                "Round duration covers every phase");
        check(
                round.result(Set.copyOf(PLAYERS)).places().equals(List.of(PLAYERS)),
                "All zero scores tie");
    }

    private static void advanceAtDeadline(WaitForItRound round, List<UUID> players) {
        check(round.advance(round.deadline(), players), "Phase advances on its deadline");
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
