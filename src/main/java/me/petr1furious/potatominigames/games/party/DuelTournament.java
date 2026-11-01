package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure tournament rules, independent of world events and player connectivity. */
public final class DuelTournament {
    public record Pair(UUID first, UUID second) {
        public boolean contains(UUID id) { return first.equals(id) || second.equals(id); }
        public UUID opponent(UUID id) {
            if (!contains(id)) throw new IllegalArgumentException("Not in this duel");
            return first.equals(id) ? second : first;
        }
    }
    public static final class Match {
        private final Pair pair;
        private int firstWins;
        private int secondWins;
        private boolean forfeited;
        public Match(Pair pair) { this.pair = pair; }
        public Pair pair() { return pair; }
        public int wins(UUID id) {
            if (!pair.contains(id)) throw new IllegalArgumentException("Not in this duel");
            return pair.first().equals(id) ? firstWins : secondWins;
        }
        public int roundsWon() { return firstWins + secondWins; }
        public boolean isComplete() { return forfeited || firstWins == 2 || secondWins == 2; }
        public UUID winner() { return firstWins == 2 ? pair.first() : secondWins == 2 ? pair.second() : null; }
        public void winRound(UUID id) {
            if (isComplete() || !pair.contains(id)) throw new IllegalArgumentException("Invalid round result");
            if (pair.first().equals(id)) firstWins++; else secondWins++;
        }
        public void forfeit(UUID winner) {
            if (isComplete() || winner != null && !pair.contains(winner)) throw new IllegalArgumentException("Invalid forfeit");
            forfeited = true;
            if (winner != null) {
                if (pair.first().equals(winner)) firstWins = 2; else secondWins = 2;
            }
        }
    }

    private final Map<UUID, Integer> points = new LinkedHashMap<>();
    private final Map<Pair, UUID> results = new HashMap<>();
    private final List<Pair> schedule;

    public DuelTournament(List<UUID> roster) {
        if (roster.size() < 2 || roster.stream().distinct().count() != roster.size()) {
            throw new IllegalArgumentException("At least two distinct players required");
        }
        roster.forEach(id -> points.put(id, 0));
        schedule = schedule(roster);
    }
    /** Circle rotation: every pair exactly once; odd rosters have one bye per tour. */
    public static List<Pair> schedule(List<UUID> roster) {
        List<UUID> ring = new ArrayList<>(roster);
        if (ring.size() % 2 != 0) ring.add(null);
        List<Pair> pairs = new ArrayList<>();
        for (int round = 0; round < ring.size() - 1; round++) {
            for (int i = 0; i < ring.size() / 2; i++) {
                UUID a = ring.get(i), b = ring.get(ring.size() - 1 - i);
                if (a != null && b != null) pairs.add(round % 2 == 0 ? new Pair(a, b) : new Pair(b, a));
            }
            ring.add(1, ring.removeLast());
        }
        return List.copyOf(pairs);
    }
    public List<Pair> schedule() { return schedule; }
    public int points(UUID id) { return points.getOrDefault(id, 0); }
    public void record(Pair pair, UUID winner) {
        if (!schedule.contains(pair) || results.containsKey(pair) || winner != null && !pair.contains(winner)) {
            throw new IllegalArgumentException("Invalid or duplicate match result");
        }
        // null means both players withdrew; neither receives points.
        results.put(pair, winner);
        if (winner != null) points.merge(winner, 3, Integer::sum);
    }
    private int headToHead(UUID id, Collection<UUID> eligible) {
        int score = 0;
        for (var result : results.entrySet()) {
            if (id.equals(result.getValue())
                    && eligible.contains(result.getKey().opponent(id))
                    && points(id) == points(result.getKey().opponent(id))) score++;
        }
        return score;
    }
    public List<UUID> standings() {
        return standings(points.keySet());
    }
    private List<UUID> standings(Collection<UUID> eligible) {
        return points.keySet().stream().filter(eligible::contains).sorted(Comparator
                .<UUID>comparingInt(this::points).reversed()
                .thenComparing(Comparator.<UUID>comparingInt(id -> headToHead(id, eligible)).reversed())).toList();
    }
    /** Equal points and equal head-to-head: play additional BO3s for the title. */
    public List<UUID> titleContenders() {
        return titleContenders(points.keySet());
    }
    public List<UUID> titleContenders(Collection<UUID> eligible) {
        List<UUID> ordered = standings(eligible);
        if (ordered.isEmpty()) return List.of();
        UUID top = ordered.getFirst();
        return ordered.stream().filter(id -> points(id) == points(top)
                && headToHead(id, eligible) == headToHead(top, eligible)).toList();
    }
}
