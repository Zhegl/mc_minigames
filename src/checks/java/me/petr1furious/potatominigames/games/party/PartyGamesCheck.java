package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import java.util.*;

public final class PartyGamesCheck {
    public static void main(String[] args) {
        var game = new PartyGame((PartyConfig) null);
        check(!game.ballots("party_games").isEmpty(), "Tournament length is selectable");
        check(game.ballots("pvp").isEmpty(), "Standalone games have no tournament length vote");
        check(PartyGamesSession.roundCount("small", 12, 15) == 4, "Small overrides configured length with four rounds");
        check(PartyGamesSession.roundCount("medium", 12, 15) == 6, "Medium has six rounds");
        check(PartyGamesSession.roundCount("large", 4, 15) == 9, "Large has nine rounds");
        check(PartyGamesSession.roundCount("marathon", 6, 15) == 15, "Marathon plays every eligible game");
        check(PartyGamesSession.roundCount("marathon", 6, 7) == 7, "Marathon follows the configured pool");
        check(PartyGamesSession.roundCount("large", 6, 3) == 3, "Short pools never repeat games to fill a length");
        check(PartyGamesSession.roundCount(null, 11, 15) == 11, "Direct starts without a length preserve admin configuration");
        try {
            PartyGamesSession.roundCount("unknown", 6, 15);
            throw new AssertionError("Unknown match length accepted");
        } catch (IllegalArgumentException expected) {
        }
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        var s = new PartyGamesState(List.of(a, b, c), PartyGamesState.MODES, new Random(1));
        check(
                s.schedule().size() == 15 && new HashSet<>(s.schedule()).size() == 15,
                "Fifteen unique games");
        s.beginRound(0);
        s.disconnect(b);
        s.reconnect(b);
        check(
                s.acceptResult(
                        0, new PartyRoundResult(List.of(List.of(b), List.of(a), List.of(c)))),
                "Current generation result");
        check(
                s.score().points(b) == 0 && s.score().points(a) == 3,
                "Disconnect then rejoin cannot earn this round");
        check(!s.acceptResult(0, new PartyRoundResult(List.of(List.of(a)))), "No duplicate result");
        s.beginRound(1);
        check(!s.acceptResult(0, PartyRoundResult.empty()), "Old callback ignored");
        check(s.nextRoster().contains(b), "Rejoin plays next round");
        s.withdraw(c);
        s.reconnect(c);
        check(!s.nextRoster().contains(c), "Explicit withdrawal permanent");
        s.acceptResult(1, new PartyRoundResult(List.of(List.of(a, b))));
        s.disconnect(a);
        check(s.score().points(a) == 6, "Closed round survives subsequent disconnect");
        for (var modes :
                List.of(
                        List.<String>of(),
                        List.of("party_games"),
                        List.of("unknown"),
                        List.of("anvils", "anvils"))) {
            try {
                new PartyGamesState(List.of(a, b), modes, new Random(1));
                throw new AssertionError("Invalid pool accepted");
            } catch (IllegalArgumentException expected) {
            }
        }
        System.out.println("Party Games state passed");
    }
}
