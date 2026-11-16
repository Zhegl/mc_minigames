package me.petr1furious.potatominigames.games.finals;

/** Exercises cannot be skipped, repeated or completed by unrelated actions. */
public final class FinalsTutorialCheck {
    public static void main(String[] args) {
        FinalsTutorialProgress progress = new FinalsTutorialProgress();
        int checks = 0;
        for (FinalsTutorialProgress.Action expected : FinalsTutorialProgress.Action.values()) {
            for (FinalsTutorialProgress.Action other : FinalsTutorialProgress.Action.values()) {
                if (other == expected) continue;
                var before = progress.step();
                if (progress.accept(other) || progress.step() != before)
                    throw new AssertionError("Out-of-order action advanced " + before + ": " + other);
                checks++;
            }
            if (!progress.accept(expected)) throw new AssertionError("Expected action rejected: " + expected);
            if (progress.accept(expected)) throw new AssertionError("Duplicate action accepted: " + expected);
            checks += 2;
        }
        if (progress.step() != FinalsTutorialProgress.Step.DONE) throw new AssertionError("Not completed");
        System.out.println("Finals tutorial: " + (checks + 1) + " checks passed");
    }
}
