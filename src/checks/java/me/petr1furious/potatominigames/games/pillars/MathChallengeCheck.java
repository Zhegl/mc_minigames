package me.petr1furious.potatominigames.games.pillars;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Exercises independent answers, retries, elimination and one-shot timeout penalties. */
public final class MathChallengeCheck {
    public static void main(String[] args) {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID eliminated = UUID.randomUUID();
        UUID spectator = UUID.randomUUID();
        MathChallenge challenge = new MathChallenge(new MathChallenge.Problem(7, '+', 8),
                List.of(first, second, eliminated));
        check(challenge.answer(spectator, "15") == MathChallenge.Answer.IGNORED,
                "Spectators and other sessions cannot answer");
        for (String text : List.of("hello", "15 apples", "999999999999999999", "")) {
            check(challenge.answer(first, text) == MathChallenge.Answer.IGNORED,
                    "Ordinary chat and invalid integers must be ignored");
        }
        check(challenge.answer(first, "14") == MathChallenge.Answer.WRONG, "Wrong answers allow a retry");
        check(challenge.isWaiting(first), "A wrong answer must not discharge the penalty");
        check(challenge.answer(first, " 15 ") == MathChallenge.Answer.CORRECT, "Trimmed correct answer passes");
        check(!challenge.isWaiting(first), "A correct answer prevents damage");
        check(challenge.isWaiting(second), "Each player must answer independently");
        check(challenge.answer(first, "15") == MathChallenge.Answer.IGNORED, "Duplicate answers are ignored");
        challenge.remove(eliminated);
        check(challenge.answer(eliminated, "15") == MathChallenge.Answer.IGNORED,
                "Leaving or elimination removes the player from the challenge");
        check(challenge.finish().equals(Set.of(second)), "Only unanswered participants fail at timeout");
        check(challenge.finish().isEmpty(), "Failures can only be consumed once");
        check(challenge.answer(second, "15") == MathChallenge.Answer.IGNORED, "Late answers are ignored");

        MathChallenge negative = new MathChallenge(new MathChallenge.Problem(3, '-', 11), List.of(first));
        check(negative.answer(first, "-8") == MathChallenge.Answer.CORRECT, "Negative answers are supported");
        check(new MathChallenge.Problem(12, '×', 11).result() == 132, "Multiplication is correct");

        Set<Character> operators = new java.util.HashSet<>();
        Random random = new Random(4);
        for (int i = 0; i < 1000; i++) {
            MathChallenge.Problem problem = MathChallenge.Problem.random(random);
            operators.add(problem.operator());
            int expected = switch (problem.operator()) {
                case '+' -> problem.left() + problem.right();
                case '-' -> problem.left() - problem.right();
                case '×' -> problem.left() * problem.right();
                default -> throw new AssertionError("Unknown operation");
            };
            check(problem.left() >= 1 && problem.left() <= 20 && problem.right() >= 1 && problem.right() <= 20,
                    "Questions must stay small enough for a ten-second mental calculation");
            check(problem.result() == expected, "Generated problems must have the right answer");
            MathChallenge generated = new MathChallenge(problem, List.of(first));
            check(generated.answer(first, Integer.toString(expected)) == MathChallenge.Answer.CORRECT,
                    "The displayed problem's result must be accepted");
        }
        check(operators.equals(Set.of('+', '-', '×')), "All three operations are generated");
        System.out.println("Math challenge: independent answers, retries, timeout, elimination and generation verified");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
