package me.petr1furious.potatominigames.games.party;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class FurryQuestCheck {
    private static final List<UUID> PLAYERS = List.of(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3));

    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }

    private static FurryQuest round() { return new FurryQuest(PLAYERS, new Random(42)); }

    private static Set<FurryQuest.Site> finishRoute(FurryQuest game, UUID id, boolean clockwise) {
        var seen = EnumSet.noneOf(FurryQuest.Site.class);
        while (game.site(id) != null) {
            var site = game.site(id);
            require(seen.add(site), "Each landmark appears once");
            require(!game.act(id, 20, 20, true, false, false), "Outside the zone");
            boolean done = switch (site) {
                case POND -> {
                    for (int i = 0; i < 39; i++)
                        require(!game.act(id, 2, 0, true, true, false), "Drink takes two seconds");
                    game.act(id, 2, 0, false, true, false);
                    for (int i = 0; i < 39; i++)
                        require(!game.act(id, 2, 0, true, true, false), "Interrupted drink resets");
                    yield game.act(id, 2, 0, true, true, false);
                }
                case HAY -> {
                    for (int i = 0; i < 2; i++) {
                        game.act(id, 1, 1, true, false, false);
                        for (int j = 0; j < 4; j++)
                            require(!game.act(id, 1, 1, true, true, false), "A held key counts once");
                    }
                    game.act(id, 1, 1, true, false, false);
                    yield game.act(id, 1, 1, true, true, false);
                }
                case TREE -> {
                    game.act(id, 3, 0, true, false, false);
                    for (int i = 0; i < 4; i++) {
                        game.act(id, 0, 3, true, false, false);
                        require(!game.act(id, 3, 0, true, false, false), "Backtracking is not a lap");
                    }
                    // A hider can walk this close to the one-block trunk, even at the corners.
                    boolean lap = false;
                    for (int step = 0; step <= 96 && !lap; step++) {
                        double angle = (clockwise ? 1 : -1) * step * 2 * Math.PI / 96;
                        lap = game.act(id, 1.2 * Math.cos(angle), 1.2 * Math.sin(angle), true, false, false);
                    }
                    require(lap, "A close lap around the oak completes in either direction");
                    yield lap;
                }
                case TROUGH -> {
                    game.act(id, -2, 0, true, false, false);
                    require(!game.act(id, -2, 1, true, false, false), "Both ends required");
                    yield game.act(id, 2, 0, true, false, false);
                }
                case BELL -> {
                    game.act(id, 12, 0, true, false, true);
                    require(!game.act(id, 2, 0, true, false, true), "Ring on a fresh jump at the bell");
                    game.act(id, 2, 0, true, false, false);
                    yield game.act(id, 2, 0, true, false, true);
                }
                case PARCEL -> {
                    require(game.click(id, FurryQuest.Station.LOOM, false, 0) == FurryQuest.Interaction.IGNORED,
                            "No delivery before pickup");
                    require(game.click(id, FurryQuest.Station.BARN_BARREL, false, 1) == FurryQuest.Interaction.NEXT,
                            "Pick up wool at the barn");
                    game.act(id, 18, 6, true, false, false);
                    require(game.station(id) == FurryQuest.Station.LOOM, "Keep the parcel while crossing the farm");
                    yield game.click(id, FurryQuest.Station.LOOM, false, 22) == FurryQuest.Interaction.COMPLETE;
                }
                case FISH -> {
                    var post = FurryQuest.Station.DOCK_POST;
                    require(game.click(id, post, false, 10) == FurryQuest.Interaction.CAST, "Cast at the dock");
                    require(game.click(id, post, false, 11) == FurryQuest.Interaction.EARLY, "Early reel loses the bite");
                    game.click(id, post, false, 12);
                    long late = game.biteTick(id) + FurryQuest.BITE_WINDOW;
                    require(game.click(id, post, false, late) == FurryQuest.Interaction.CAST, "Missed bite allows recast");
                    yield game.click(id, post, false, game.biteTick(id)) == FurryQuest.Interaction.COMPLETE;
                }
                case CHIMES -> {
                    game.click(id, FurryQuest.Station.BLUE, false, 0);
                    require(game.click(id, FurryQuest.Station.RED, false, 1) == FurryQuest.Interaction.WRONG_NOTE,
                            "Incorrect note resets the tune");
                    for (var note : List.of(FurryQuest.Station.BLUE, FurryQuest.Station.GOLD))
                        require(game.click(id, note, false, 2) == FurryQuest.Interaction.NEXT, "Play notes in order");
                    yield game.click(id, FurryQuest.Station.RED, false, 3) == FurryQuest.Interaction.COMPLETE;
                }
                case SWAP -> {
                    require(game.click(id, FurryQuest.Station.SCARECROW, false, 1) == FurryQuest.Interaction.IGNORED,
                            "Stand beside the scarecrow without swapping");
                    yield game.click(id, FurryQuest.Station.SCARECROW, true, 2) == FurryQuest.Interaction.COMPLETE;
                }
            };
            require(done && game.completed(id) == seen.size(), "One personal completion for " + site);
        }
        require(seen.size() == 5, "All five different tasks");
        return seen;
    }

    public static void main(String[] args) {
        var pool = EnumSet.noneOf(FurryQuest.Site.class);
        for (int seed = 0; seed < 32; seed++) {
            var sample = new FurryQuest(PLAYERS, new Random(seed));
            for (UUID id : PLAYERS) if (!id.equals(sample.sniper())) pool.addAll(finishRoute(sample, id, true));
        }
        require(pool.equals(EnumSet.allOf(FurryQuest.Site.class)), "Every mission can appear and be completed");
        FurryQuest game = round();
        UUID sniper = game.sniper();
        List<UUID> spies = PLAYERS.stream().filter(id -> !id.equals(sniper)).toList();
        UUID first = spies.getFirst(), second = spies.getLast();
        require(game.remaining() == 2 && game.site(sniper) == null, "One sniper and two agents");
        finishRoute(game, first, true);
        require(game.outcome() == null && game.completed(second) == 0, "Personal tasks cannot be pooled");
        require(!game.startFinalHuntIfReady(), "Wait for every living agent");
        require(!game.act(first, 0, 0, true, true, true), "Completed agents cannot score again");
        finishRoute(game, second, false);
        require(game.startFinalHuntIfReady() && game.outcome() == null, "Last route starts the final hunt");
        game.timeout();
        require(game.outcome() == FurryQuest.Outcome.MISSIONS
                        && Set.copyOf(game.result(Set.copyOf(PLAYERS)).places().getFirst()).equals(Set.copyOf(spies)),
                "All surviving agents share first place");

        game = round();
        finishRoute(game, second, false);
        game.out(first);
        require(game.startFinalHuntIfReady(), "Losing the last unfinished agent starts the hunt");
        game.timeout();
        require(game.result(Set.copyOf(PLAYERS)).places().equals(List.of(List.of(second))),
                "A caught agent with unfinished tasks cannot block or share the mission win");

        game = round();
        game.out(first);
        finishRoute(game, second, false);
        require(game.startFinalHuntIfReady(), "Living agents can finish after an earlier elimination");
        game.out(second);
        require(game.outcome() == FurryQuest.Outcome.ALL_CAUGHT && !game.startFinalHuntIfReady(),
                "Catching the last agent during the countdown wins immediately");
        game.timeout();
        require(game.result(Set.copyOf(PLAYERS)).places().equals(List.of(List.of(sniper))), "Sniper keeps the win");

        game = round();
        finishRoute(game, first, true);
        finishRoute(game, second, false);
        require(game.startFinalHuntIfReady(), "Start the hunt with two agents");
        game.out(first);
        require(!game.startFinalHuntIfReady() && game.outcome() == null, "A catch cannot restart the ten seconds");
        game.timeout();
        require(game.result(Set.copyOf(PLAYERS)).places().equals(List.of(List.of(second))),
                "Only agents still alive at the end win");
        game = round();
        game.wrongShot();
        require(game.result(Set.copyOf(PLAYERS)).places().getFirst().containsAll(spies), "Livestock loss");
        game = round();
        game.timeout();
        require(game.outcome() == FurryQuest.Outcome.TIME, "Round timer");
        game = round();
        game.out(sniper);
        require(game.outcome() == FurryQuest.Outcome.SNIPER_LEFT, "Sniper departure");
        var session = new FurryQuestSession(null, null, PartyRoundContext.standalone(null));
        session.restartRoundTimer(session.durationSeconds());
        session.tick = 2999;
        require(session.remainingRoundTicks() == 1, "One tick remains on the original clock");
        session.restartRoundTimer(FurryQuest.FINAL_SECONDS);
        session.tick += 199;
        require(session.remainingRoundTicks() == 1, "The sniper gets the full ten seconds across the old deadline");
        session.tick++;
        require(session.remainingRoundTicks() == 0, "Final hunt ends on tick 200");
        System.out.println("Furry Quest personal routes, final countdown and survivor awards passed");
    }
}
