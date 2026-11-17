package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.WaitForItRound.Goal.*;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Progress.*;
import static me.petr1furious.potatominigames.games.party.WaitForItRound.Task.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WaitForItCheck {
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final List<UUID> PLAYERS = List.of(ALICE, BOB);

    public static void main(String[] args) {
        var round = new WaitForItRound(PLAYERS);
        check(
                round.task() == FLOWER && round.secondsLeft(0) == FLOWER.seconds,
                "Flower starts at tick 0");
        check(round.record(ALICE, POPPY, 0) == PARTIAL, "First flower counts at tick 0");
        check(
                round.record(ALICE, POPPY, 1) == IGNORED,
                "Same species cannot replace a different one");
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
        var previous = round.advance(craft, PLAYERS);
        check(
                previous.task() == FLOWER && previous.completed().equals(Set.of(ALICE)),
                "Previous results persist");
        check(
                round.task() == CRAFT && round.secondsLeft(craft) == CRAFT.seconds,
                "Craft opens on timeout tick");
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
        var early = round.advance(craft + 1, List.of(BOB));
        check(
                early.task() == CRAFT && early.completed().equals(Set.of(BOB)),
                "Departures cannot prolong a completed task");
        check(
                round.task() == CROP && round.record(BOB, WHEAT, craft + 1) == PARTIAL,
                "Crop opens on early transition tick");
        check(round.count(ALICE, WHEAT) == 0, "Harvest progress is personal");
        long cropEnd = round.deadline();
        for (int i = 1; i < CROP.required(WHEAT); i++)
            check(round.record(BOB, WHEAT, cropEnd - 1) == PARTIAL, "Wheat counts individually");
        check(
                round.record(BOB, WHEAT, cropEnd - 1) == IGNORED,
                "Extra wheat cannot replace carrots");
        for (int i = 1; i < CROP.required(CARROT); i++)
            check(
                    round.record(BOB, CARROT, cropEnd - 1) == PARTIAL,
                    "Carrots have their own quota");
        check(round.record(BOB, CARROT, cropEnd - 1) == COMPLETE, "Both quotas finish the harvest");
        round.advance(cropEnd, PLAYERS);
        check(
                round.task() == BUTTON && round.record(ALICE, BUTTON_LEFT, cropEnd) == PARTIAL,
                "One button counts");
        check(
                round.record(ALICE, BUTTON_LEFT, cropEnd + 1) == IGNORED,
                "Same button cannot score twice");
        check(
                round.record(ALICE, BUTTON_RIGHT, cropEnd + 1) == COMPLETE,
                "Second button scores the point");

        var timeouts = new WaitForItRound(PLAYERS);
        for (var task : List.of(FLOWER, CRAFT, CROP, BUTTON))
            check(
                    timeouts.advance(timeouts.deadline(), PLAYERS).task() == task,
                    "All four tasks run on timeouts");
        check(
                timeouts.finished() && timeouts.deadline() == WaitForItRound.maxSeconds() * 20L,
                "Maximum duration is exactly four active windows");

        var solo = new WaitForItRound(List.of(ALICE));
        long tick = 0;
        check(
                solo.advance(solo.deadline() - 1, List.of()) == null,
                "Empty active roster cannot skip a task");
        for (var task : List.of(FLOWER, CRAFT, CROP, BUTTON)) {
            check(solo.task() == task && !solo.finished(), "Solo gets every task");
            check(solo.advance(tick, List.of(ALICE)) == null, "New task cannot end before play");
            for (var goal : WaitForItRound.Goal.values())
                for (int i = 0; i < task.required(goal); i++) solo.record(ALICE, goal, tick);
            check(
                    solo.advance(++tick, List.of(ALICE)).task() == task,
                    "Solo completion advances directly");
        }
        check(solo.finished() && solo.points(ALICE) == 4, "Solo can score in all four tasks");
        check(
                solo.record(ALICE, BUTTON_LEFT, tick) == IGNORED && solo.secondsLeft(tick) == 0,
                "Finished round is closed");
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
