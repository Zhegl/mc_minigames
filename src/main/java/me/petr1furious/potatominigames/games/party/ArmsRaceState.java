package me.petr1furious.potatominigames.games.party;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** FFA progression: no match schedule, rounds, or per-opponent results. */
final class ArmsRaceState {
    private static final class Score {
        int stage;
        int kills;
        int deaths;
        long advancedAt;
        boolean alive = true;
    }
    private final Map<UUID, Score> scores = new LinkedHashMap<>();
    private final int stages;
    private long sequence;
    private UUID winner;

    ArmsRaceState(Collection<UUID> roster, int stages) {
        if (roster.isEmpty() || stages < 1 || roster.stream().distinct().count() != roster.size()) {
            throw new IllegalArgumentException("Distinct players and a nonempty weapon chain required");
        }
        this.stages = stages;
        roster.forEach(id -> scores.put(id, new Score()));
    }
    int stage(UUID id) { return scores.get(id).stage; }
    int kills(UUID id) { return scores.get(id).kills; }
    int deaths(UUID id) { return scores.get(id).deaths; }
    boolean alive(UUID id) { return scores.containsKey(id) && scores.get(id).alive; }
    void setAlive(UUID id, boolean alive) { scores.get(id).alive = alive; }
    UUID winner() { return winner; }

    /** Returns true only when the killer earned a new step (or the final victory). */
    boolean death(UUID victim, UUID killer, int weaponStage) {
        if (winner != null || !alive(victim)) return false;
        Score dead = scores.get(victim);
        dead.alive = false;
        dead.deaths++;
        if (victim.equals(killer) || !alive(killer)) return false;
        Score attacker = scores.get(killer);
        // Old arrows and delayed fall credit cannot skip a newly equipped weapon.
        if (weaponStage != attacker.stage) return false;
        attacker.kills++;
        attacker.advancedAt = ++sequence;
        if (attacker.stage == stages - 1) winner = killer;
        else attacker.stage++;
        return true;
    }
    List<UUID> leaderboard(Collection<UUID> eligible) {
        return scores.keySet().stream().filter(eligible::contains).sorted(Comparator
                .<UUID>comparingInt(this::kills).reversed()
                .thenComparingInt(this::deaths)
                .thenComparingLong(id -> scores.get(id).advancedAt)).toList();
    }
    UUID timeWinner(Collection<UUID> eligible) {
        List<UUID> ranking = leaderboard(eligible);
        return ranking.isEmpty() || kills(ranking.getFirst()) == 0 ? null : ranking.getFirst();
    }
}
