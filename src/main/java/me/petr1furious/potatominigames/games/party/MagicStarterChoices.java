package me.petr1furious.potatominigames.games.party;

import java.util.*;

/** Arena-side starter selection, at most two basic wands, locked before countdown. */
final class MagicStarterChoices {
    static final int SECONDS = 30;
    private final Set<UUID> players;
    private final Map<UUID, List<MagicSpell>> picks = new HashMap<>();
    private final Set<UUID> confirmed = new HashSet<>();
    private boolean locked;

    MagicStarterChoices(Collection<UUID> players) { this.players = new LinkedHashSet<>(players); }
    List<MagicSpell> selected(UUID player) { return List.copyOf(picks.getOrDefault(player, List.of())); }
    boolean toggle(UUID player, MagicSpell spell) {
        if (locked || spell == null || !players.contains(player) || !MagicWand.STARTERS.contains(spell)) return false;
        List<MagicSpell> choice = picks.computeIfAbsent(player, ignored -> new ArrayList<>());
        if (!choice.remove(spell)) {
            if (choice.size() >= 2) return false;
            choice.add(spell);
        }
        confirmed.remove(player);
        return true;
    }
    boolean confirm(UUID player) {
        if (locked || !players.contains(player) || selected(player).isEmpty()) return false;
        confirmed.add(player);
        return true;
    }
    boolean ready() { return !players.isEmpty() && confirmed.containsAll(players); }
    int confirmed() { return confirmed.size(); }
    int size() { return players.size(); }
    void withdraw(UUID player) { players.remove(player); picks.remove(player); confirmed.remove(player); }
    Map<UUID, List<MagicSpell>> finish() {
        locked = true;
        Map<UUID, List<MagicSpell>> result = new HashMap<>();
        for (UUID player : players) {
            List<MagicSpell> choice = selected(player);
            result.put(player, choice.isEmpty() ? List.of(MagicSpell.FIREBOLT, MagicSpell.ICE_LANCE) : choice);
        }
        return Map.copyOf(result);
    }
}
