package me.petr1furious.potatominigames.games.pillars;

/** The rising plane is independent of movement, arena height and blocks surrounding a player. */
public final class FloodModifierCheck {
    public static void main(String[] args) {
        for (int feet : new int[] {201, 64, -30}) {
            int initial = feet - 20;
            check(FloodModifier.surfaceAfterTicks(feet, 0) == initial, "Start twenty blocks below player feet");
            check(FloodModifier.surfaceAfterTicks(feet, 199) == initial, "No early rise before ten seconds");
            check(FloodModifier.surfaceAfterTicks(feet, 200) == initial + 1, "First rise after exactly 200 ticks");
            check(FloodModifier.surfaceAfterTicks(feet, 399) == initial + 1, "Level persists until the next interval");
            check(FloodModifier.surfaceAfterTicks(feet, 400) == initial + 2, "Consecutive intervals add one block each");
            check(!FloodModifier.touches(feet, FloodModifier.surfaceAfterTicks(feet, 3999)),
                    "An unmoving player is safe until the surface reaches their feet");
            check(FloodModifier.touches(feet, FloodModifier.surfaceAfterTicks(feet, 4000)),
                    "An unmoving player is hit at the twentieth rise, including inside a box");
            check(FloodModifier.touches(initial, initial), "Contact at the exact water surface is lethal");
            check(!FloodModifier.touches(Math.nextUp((double) initial), initial), "Above the surface remains safe");
            check(FloodModifier.touches(initial - 30, initial), "Being below the initial layer is not a refuge");
        }
        check(FloodModifier.surfaceAfterTicks(201, Long.MAX_VALUE) == Integer.MAX_VALUE,
                "A long-running flood cannot wrap downwards");
        System.out.println("Flood: initial height, exact rise boundaries, stationary contact and submerged kills verified");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
