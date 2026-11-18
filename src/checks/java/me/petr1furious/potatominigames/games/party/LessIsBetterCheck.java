package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;
import static me.petr1furious.potatominigames.games.party.LessIsBetterVotes.Side.BLUE;
import static me.petr1furious.potatominigames.games.party.LessIsBetterVotes.Side.RED;

import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LessIsBetterCheck {
    public static void main(String[] args) {
        check(LessIsBetterSession.platformAt(-8.5, 82, .5) == RED, "The raised red platform is a vote");
        check(LessIsBetterSession.platformAt(8.5, 82, .5) == BLUE, "The raised blue platform is a vote");
        check(LessIsBetterSession.platformAt(8.5, 83.25, .5) == BLUE, "Jumping over the same pad still counts");
        check(LessIsBetterSession.platformAt(.5, 81, .5) == null, "The central start is a neutral position");
        check(LessIsBetterSession.platformAt(-3.5, 82, .5) == RED, "The edge of the red pad counts");
        check(LessIsBetterSession.platformAt(-3, 82, .5) == null, "The stairs are outside both platforms");
        check(LessIsBetterSession.platformAt(8.5, 81, .5) == null, "The deck height cannot register an elevated vote");
        check(LessIsBetterSession.platformAt(8.5, 82, 8) == null, "Standing past the blue platform is neutral");

        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3), d = new UUID(0, 4);
        var votes = new LessIsBetterVotes();
        var players = Set.of(a, b, c, d);
        var reveal = votes.reveal(Map.of(a, RED, b, BLUE, c, BLUE), 0, 0);
        check(reveal.red() == 1 && reveal.blue() == 2 && reveal.winner() == RED,
                "Only the smaller occupied platform wins");
        check(votes.score(a) == 1 && votes.score(b) == 0 && votes.score(c) == 0,
                "Only players who picked the minority score");
        check(votes.result(players).places().equals(List.of(List.of(a), List.of(b, c)))
                        || votes.result(players).places().equals(List.of(List.of(a), List.of(c, b))),
                "Equal scores share a place, and spectators or idle players earn no place");
        check(votes.reveal(Map.of(), 0, 0).choices().isEmpty(), "A vote never carries into the next ballot");

        check(votes.reveal(Map.of(a, BLUE, d, RED), 0, 0).winner() == null && votes.score(a) == 1,
                "An even vote awards no points");
        check(votes.reveal(Map.of(a, BLUE), 0, 0).winner() == null, "An empty side cannot win");
        check(votes.reveal(Map.of(b, BLUE), 2, 0).winner() == BLUE && votes.score(b) == 1,
                "Neutral votes let a novice practice alone without earning a player place");
        checkPrivateVoting();
        System.out.println("Less Is Better platform boundaries, private voting and standings passed");
    }

    private static void checkPrivateVoting() {
        var a = new Viewer(true);
        var b = new Viewer(false);
        var c = new Viewer(true);
        a.externalHidden.add(c.player);
        var visibility = new LessIsBetterVisibility(null);
        visibility.hide(a.player);
        visibility.hide(b.player);
        visibility.hide(c.player);
        check(!a.player.canSee(b.player) && !b.player.canSee(a.player) && !c.player.canSee(a.player),
                "Voters cannot see each other before the reveal");
        check(!a.collidable && !b.collidable && !c.collidable, "Hidden voters cannot push each other");
        a.externalHidden.remove(c.player);
        check(!a.player.canSee(c.player), "An external plugin releasing its hold cannot reveal a voter early");
        a.externalHidden.add(c.player);
        visibility.remove(c.player);
        check(c.collidable && c.player.canSee(a.player) && b.player.canSee(c.player)
                        && !a.player.canSee(c.player) && !a.player.canSee(b.player),
                "Departures restore only the visibility changed by this round");
        visibility.clear();
        check(a.collidable && !b.collidable && a.player.canSee(b.player) && b.player.canSee(a.player),
                "Reveal restores both directions and each player's original collision setting");
    }

    private static final class Viewer {
        final Set<Player> hidden = new HashSet<>();
        final Set<Player> externalHidden = new HashSet<>();
        final Player player;
        boolean collidable;

        Viewer(boolean collidable) {
            this.collidable = collidable;
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (self, method, args) -> switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        case "canSee" -> !hidden.contains(args[0]) && !externalHidden.contains(args[0]);
                        case "isCollidable" -> this.collidable;
                        case "setCollidable" -> { this.collidable = (boolean) args[0]; yield null; }
                        case "hidePlayer" -> { hidden.add((Player) args[1]); yield null; }
                        case "showPlayer" -> { hidden.remove(args[1]); yield null; }
                        default -> throw new AssertionError(method);
                    });
        }
    }
}
