package me.petr1furious.potatominigames.games.party;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class FurryQuestCheck {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);
    private static final List<UUID> PLAYERS = List.of(A, B, C);

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    private static FurryQuest round() {
        return new FurryQuest(PLAYERS, new Random(42));
    }

    public static void main(String[] args) {
        FurryQuest game = round();
        UUID sniper = game.sniper();
        List<UUID> spies = PLAYERS.stream().filter(id -> !id.equals(sniper)).toList();
        UUID first = spies.getFirst(), second = spies.getLast();
        require(game.remaining() == 2 && game.required() == 4);
        require(game.site(sniper) == null && game.site(first) != null);
        for (int i = 0; i < 39; i++) require(!game.linger(first, true));
        game.linger(first, false);
        for (int i = 0; i < 39; i++) require(!game.linger(first, true));
        var old = game.site(first);
        require(game.linger(first, true) && game.site(first) != old);
        game.out(first);
        require(game.remaining() == 1 && !game.linger(first, true));
        for (int i = 0; i < 120; i++) game.linger(second, true);
        require(game.outcome() == FurryQuest.Outcome.MISSIONS);
        game.timeout();
        require(game.result(Set.of(sniper, second)).places().equals(List.of(List.of(second))));

        game = round();
        game.out(first);
        game.out(second);
        require(game.outcome() == FurryQuest.Outcome.ALL_CAUGHT);
        require(game.result(Set.copyOf(PLAYERS)).places().equals(List.of(List.of(sniper))));
        game = round();
        game.wrongShot();
        require(game.outcome() == FurryQuest.Outcome.LIVESTOCK);
        require(game.result(Set.copyOf(PLAYERS)).places().getFirst().containsAll(spies));
        game = round();
        game.timeout();
        require(game.outcome() == FurryQuest.Outcome.TIME);
        game = round();
        game.out(sniper);
        require(game.outcome() == FurryQuest.Outcome.SNIPER_LEFT);
        System.out.println("Furry Quest round checks passed");
    }
}
