package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Team objectives and the first event that decides the round. */
final class FurryQuest {
    static final int SECONDS = 105;
    static final int HOLD_TICKS = 40;

    enum Site {
        POND(-10, -9), HAY(10, -9), TREE(-10, 10), TROUGH(10, 10);

        final int x, z;
        Site(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    enum Outcome { MISSIONS, LIVESTOCK, ALL_CAUGHT, TIME, SNIPER_LEFT }

    private final Random random;
    private final UUID sniper;
    private final List<UUID> team;
    private final Map<UUID, Site> assignments = new LinkedHashMap<>();
    private final Map<UUID, Integer> progress = new LinkedHashMap<>();
    private final int required;
    private int completed;
    private Outcome outcome;

    FurryQuest(Collection<UUID> players, Random random) {
        if (players.size() < 2) throw new IllegalArgumentException("Furry Quest requires two players");
        this.random = random;
        var ids = new ArrayList<>(players);
        sniper = ids.remove(random.nextInt(ids.size()));
        team = List.copyOf(ids);
        required = team.size() + 2;
        for (UUID id : team) assign(id);
    }

    UUID sniper() { return sniper; }
    int remaining() { return assignments.size(); }
    int required() { return required; }
    int completed() { return completed; }
    Site site(UUID id) { return assignments.get(id); }
    Outcome outcome() { return outcome; }

    private void assign(UUID id) {
        var choices = new ArrayList<>(List.of(Site.values()));
        choices.remove(assignments.get(id));
        assignments.put(id, choices.get(random.nextInt(choices.size())));
        progress.put(id, 0);
    }

    boolean linger(UUID id, boolean nearby) {
        if (outcome != null || !assignments.containsKey(id)) return false;
        int ticks = nearby ? progress.get(id) + 1 : 0;
        progress.put(id, ticks);
        if (ticks < HOLD_TICKS) return false;
        if (++completed == required) outcome = Outcome.MISSIONS;
        else assign(id);
        return true;
    }

    void out(UUID id) {
        if (outcome != null) return;
        if (sniper.equals(id)) outcome = Outcome.SNIPER_LEFT;
        else if (assignments.remove(id) != null && assignments.isEmpty()) outcome = Outcome.ALL_CAUGHT;
        progress.remove(id);
    }

    void wrongShot() {
        if (outcome == null) outcome = Outcome.LIVESTOCK;
    }

    void timeout() {
        if (outcome == null) outcome = Outcome.TIME;
    }

    PartyRoundResult result(Set<UUID> eligible) {
        if (outcome == null) return PartyRoundResult.empty();
        List<UUID> winners = switch (outcome) {
            case ALL_CAUGHT, TIME -> List.of(sniper);
            default -> team;
        };
        return new PartyRoundResult(List.of(winners)).eligible(eligible);
    }
}
