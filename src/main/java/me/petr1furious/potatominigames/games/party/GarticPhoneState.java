package me.petr1furious.potatominigames.games.party;

import java.util.*;
import java.util.function.Function;

/** Fixed-roster telephone chains. All mutations happen on the server thread. */
final class GarticPhoneState<T> {
  enum Phase {
    PROMPT,
    BUILD,
    GUESS,
    REVEAL
  }

  record Step<T>(UUID author, String text, T build) {}

  private final List<UUID> roster;
  private final List<List<Step<T>>> chains = new ArrayList<>();
  private final Map<UUID, String> text = new HashMap<>();
  private final Set<UUID> ready = new HashSet<>();
  private int turn;

  GarticPhoneState(List<UUID> roster) {
    if (roster.isEmpty() || roster.size() > 12 || new HashSet<>(roster).size() != roster.size())
      throw new IllegalArgumentException("Gartic Phone requires 1-12 distinct players");
    this.roster = List.copyOf(roster);
    for (UUID ignored : roster) chains.add(new ArrayList<>());
  }

  int turn() {
    return turn;
  }

  int turns() {
    return Math.max(3, roster.size());
  }

  Phase phase() {
    if (turn >= turns()) return Phase.REVEAL;
    return turn == 0 ? Phase.PROMPT : turn % 2 == 1 ? Phase.BUILD : Phase.GUESS;
  }

  int chainIndex(UUID player) {
    int index = roster.indexOf(player);
    if (index < 0) throw new IllegalArgumentException("Player is not in the chain roster");
    return Math.floorMod(index - turn, roster.size());
  }

  Step<T> previous(UUID player) {
    return chains.get(chainIndex(player)).getLast();
  }

  List<Step<T>> chain(int index) {
    return List.copyOf(chains.get(index));
  }

  boolean ready(UUID player) {
    return ready.contains(player);
  }

  boolean allReady(Collection<UUID> active) {
    return !active.isEmpty() && ready.containsAll(active);
  }

  boolean submitText(UUID player, int expectedTurn, String value) {
    if (expectedTurn != turn
        || !roster.contains(player)
        || ready(player)
        || (phase() != Phase.PROMPT && phase() != Phase.GUESS)) return false;
    String clean = value.replaceAll("\\p{Cntrl}", " ").strip();
    if (clean.isBlank()) return false;
    clean =
        clean.substring(
            0, clean.offsetByCodePoints(0, Math.min(120, clean.codePointCount(0, clean.length()))));
    text.put(player, clean);
    ready.add(player);
    return true;
  }

  boolean readyBuild(UUID player, int expectedTurn) {
    return expectedTurn == turn
        && phase() == Phase.BUILD
        && roster.contains(player)
        && ready.add(player);
  }

  void endTurn(Function<UUID, T> capture) {
    if (phase() == Phase.REVEAL) throw new IllegalStateException("All turns have finished");
    for (UUID player : roster) {
      Step<T> step =
          phase() == Phase.BUILD
              ? new Step<>(player, "", Objects.requireNonNull(capture.apply(player)))
              : new Step<>(player, text.getOrDefault(player, ""), null);
      chains.get(chainIndex(player)).add(step);
    }
    text.clear();
    ready.clear();
    turn++;
  }
}
