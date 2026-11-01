package me.petr1furious.potatominigames.games.pillars;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** One question and its unanswered players. Accessed only on the server thread. */
final class MathChallenge {
    enum Answer { IGNORED, WRONG, CORRECT }

    record Problem(int left, char operator, int right) {
        static Problem random(RandomGenerator random) {
            return switch (random.nextInt(3)) {
                case 0 -> new Problem(random.nextInt(1, 21), '+', random.nextInt(1, 21));
                case 1 -> new Problem(random.nextInt(1, 21), '-', random.nextInt(1, 21));
                default -> new Problem(random.nextInt(2, 13), '×', random.nextInt(2, 13));
            };
        }

        int result() {
            return switch (operator) {
                case '+' -> left + right;
                case '-' -> left - right;
                case '×' -> left * right;
                default -> throw new IllegalArgumentException("Unknown operator: " + operator);
            };
        }

        String expression() { return left + " " + operator + " " + right; }
    }

    private final Problem problem;
    private final Set<UUID> unanswered;

    MathChallenge(Problem problem, Collection<UUID> players) {
        this.problem = problem;
        this.unanswered = new HashSet<>(players);
    }

    Problem problem() { return problem; }
    boolean isWaiting(UUID player) { return unanswered.contains(player); }
    void remove(UUID player) { unanswered.remove(player); }

    Answer answer(UUID player, String message) {
        if (!isWaiting(player)) return Answer.IGNORED;
        int value;
        try {
            value = Integer.parseInt(message.strip());
        } catch (NumberFormatException ignored) {
            return Answer.IGNORED;
        }
        if (value != problem.result()) return Answer.WRONG;
        unanswered.remove(player);
        return Answer.CORRECT;
    }

    /** Consume the failures once, before dealing damage (which can end the session). */
    Set<UUID> finish() {
        Set<UUID> failures = Set.copyOf(unanswered);
        unanswered.clear();
        return failures;
    }
}
