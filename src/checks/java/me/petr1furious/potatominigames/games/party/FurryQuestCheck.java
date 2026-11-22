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

    private static void finishRoute(FurryQuest game, UUID id) {
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
                    game.act(id, 0, -3, true, false, false);
                    game.act(id, -3, 0, true, false, false);
                    game.act(id, 0, 3, true, false, false);
                    yield game.act(id, 3, 0, true, false, false);
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
            };
            require(done && game.completed(id) == seen.size(), "One personal completion for " + site);
        }
        require(seen.size() == 5, "All five different tasks");
    }

    public static void main(String[] args) {
        FurryQuest game = round();
        UUID sniper = game.sniper();
        List<UUID> spies = PLAYERS.stream().filter(id -> !id.equals(sniper)).toList();
        UUID first = spies.getFirst(), second = spies.getLast();
        require(game.remaining() == 2 && game.site(sniper) == null, "One sniper and two agents");
        finishRoute(game, first);
        require(game.outcome() == null && game.completed(second) == 0, "Personal tasks cannot be pooled");
        require(!game.act(first, 0, 0, true, true, true), "Completed agents cannot score again");
        game.out(first);
        finishRoute(game, second);
        require(game.outcome() == FurryQuest.Outcome.MISSIONS, "Both routes complete");
        game.timeout();
        require(game.result(Set.of(sniper, second)).places().equals(List.of(List.of(second))), "Eligible winners");

        game = round();
        game.out(first);
        finishRoute(game, second);
        require(game.outcome() == null && game.remaining() == 1, "An unfinished agent still counts");
        game.out(second);
        require(game.outcome() == FurryQuest.Outcome.ALL_CAUGHT, "Sniper catches all agents");
        game = round();
        game.wrongShot();
        require(game.result(Set.copyOf(PLAYERS)).places().getFirst().containsAll(spies), "Livestock loss");
        game = round();
        game.timeout();
        require(game.outcome() == FurryQuest.Outcome.TIME, "Round timer");
        game = round();
        game.out(sniper);
        require(game.outcome() == FurryQuest.Outcome.SNIPER_LEFT, "Sniper departure");
        System.out.println("Furry Quest individual routes and all five task interactions passed");
    }
}
