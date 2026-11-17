package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;
import static me.petr1furious.potatominigames.games.party.LessIsBetterVotes.Side.BLUE;
import static me.petr1furious.potatominigames.games.party.LessIsBetterVotes.Side.RED;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class LessIsBetterCheck {
    public static void main(String[] args) {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3), d = new UUID(0, 4);
        var votes = new LessIsBetterVotes();
        var players = Set.of(a, b, c, d);
        votes.pick(a, RED);
        votes.pick(b, BLUE);
        votes.pick(c, RED);
        votes.pick(c, BLUE);
        var reveal = votes.reveal(players, 0, 0);
        check(reveal.red() == 1 && reveal.blue() == 2 && reveal.winner() == RED,
                "Changing a private choice replaces it, and only the smaller occupied side wins");
        check(votes.score(a) == 1 && votes.score(b) == 0 && votes.score(c) == 0,
                "Only players who picked the minority score");
        check(votes.result(players).places().equals(List.of(List.of(a), List.of(b, c)))
                        || votes.result(players).places().equals(List.of(List.of(a), List.of(c, b))),
                "Equal scores share a place, and spectators or idle players earn no place");
        check(votes.choice(a) == null, "A revealed vote never carries forward");

        votes.pick(a, BLUE);
        votes.pick(d, RED);
        check(votes.reveal(players, 0, 0).winner() == null && votes.score(a) == 1,
                "An even vote awards no points");
        votes.pick(a, BLUE);
        votes.pick(d, RED);
        check(votes.reveal(Set.of(a), 0, 0).winner() == null,
                "An empty side cannot win, and a disconnected vote does not count");
        votes.pick(b, BLUE);
        check(votes.reveal(Set.of(b), 2, 0).winner() == BLUE && votes.score(b) == 1,
                "Neutral votes let a novice practice alone without earning a player place");
        System.out.println("Less Is Better private choices, neutral votes and standings passed");
    }
}
