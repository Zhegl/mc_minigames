package me.petr1furious.potatominigames.games.party;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class LessIsBetterVotes {
    enum Side { RED, BLUE }

    record Reveal(int red, int blue, Side winner, Map<UUID, Side> choices) {}

    private final Map<UUID, Side> choices = new LinkedHashMap<>();
    private final Map<UUID, Integer> scores = new LinkedHashMap<>();

    void pick(UUID player, Side side) {
        choices.put(player, side);
    }

    Side choice(UUID player) {
        return choices.get(player);
    }

    int score(UUID player) {
        return scores.getOrDefault(player, 0);
    }

    Reveal reveal(Set<UUID> eligible, int neutralRed, int neutralBlue) {
        choices.keySet().retainAll(eligible);
        int red = neutralRed;
        int blue = neutralBlue;
        for (Side side : choices.values()) {
            if (side == Side.RED) red++;
            else blue++;
        }
        Side winner = red == 0 || blue == 0 || red == blue ? null : red < blue ? Side.RED : Side.BLUE;
        choices.forEach((player, side) -> scores.merge(player, side == winner ? 1 : 0, Integer::sum));
        var reveal = new Reveal(red, blue, winner, Map.copyOf(choices));
        choices.clear();
        return reveal;
    }

    PartyRoundResult result(Set<UUID> eligible) {
        var ranked = scores.keySet().stream().filter(eligible::contains).toList();
        return PartyRoundResult.ordered(ranked, Comparator.comparingInt(this::score).reversed());
    }
}
