package me.petr1furious.potatominigames.games.party;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class LessIsBetterVotes {
    enum Side { RED, BLUE }

    record Reveal(int red, int blue, Side winner, Map<UUID, Side> choices) {}

    private final Map<UUID, Integer> scores = new LinkedHashMap<>();

    int score(UUID player) {
        return scores.getOrDefault(player, 0);
    }

    Reveal reveal(Map<UUID, Side> choices, int neutralRed, int neutralBlue) {
        int red = neutralRed;
        int blue = neutralBlue;
        for (Side side : choices.values()) {
            if (side == Side.RED) red++;
            else blue++;
        }
        Side winner = red == 0 || blue == 0 || red == blue ? null : red < blue ? Side.RED : Side.BLUE;
        choices.forEach((player, side) -> scores.merge(player, side == winner ? 1 : 0, Integer::sum));
        return new Reveal(red, blue, winner, Map.copyOf(choices));
    }

    PartyRoundResult result(Set<UUID> eligible) {
        var ranked = scores.keySet().stream().filter(eligible::contains).toList();
        return PartyRoundResult.ordered(ranked, Comparator.comparingInt(this::score).reversed());
    }
}
