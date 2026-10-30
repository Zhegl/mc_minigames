package me.petr1furious.potatominigames.games.pillars;

import java.util.EnumSet;
import java.util.Random;

/** Checks the real drop policy without statistical or timing-dependent assertions. */
public final class SniperDuelCheck {
    public static void main(String[] args) {
        Random opening = new Random(0) {
            @Override
            public int nextInt(int bound) {
                throw new AssertionError("The first six drops must not roll an ammunition category");
            }
        };
        for (int drop = 1; drop <= 5; drop++) {
            check(PillarsSession.sniperDropKind(drop, opening) == PillarsSession.SniperDrop.LOG,
                    "Drop " + drop + " must be a log");
        }
        check(PillarsSession.sniperDropKind(6, opening) == PillarsSession.SniperDrop.CROSSBOW,
                "Exactly the sixth drop must be the guaranteed crossbow");
        for (int drop : new int[] {7, 12}) {
            var outcomes = EnumSet.noneOf(PillarsSession.SniperDrop.class);
            for (int roll = 0; roll < 3; roll++) {
                int value = roll;
                Random fixed = new Random(0) {
                    @Override
                    public int nextInt(int bound) {
                        check(bound == 3, "Later drops must choose among three equally likely categories");
                        return value;
                    }
                };
                outcomes.add(PillarsSession.sniperDropKind(drop, fixed));
            }
            check(outcomes.equals(EnumSet.of(PillarsSession.SniperDrop.ARROW, PillarsSession.SniperDrop.FIREWORK,
                    PillarsSession.SniperDrop.RANDOM)), "Each category must occupy exactly one of the three rolls");
        }
        System.out.println("Sniper Duel: five logs, sixth crossbow and equal later categories verified");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
