package me.petr1furious.potatominigames.games.finals;

/** Progress follows observed game actions, never elapsed lesson time. */
final class FinalsTutorialProgress {
    enum Step { OPEN, CARRY, DEPOSIT, PAYOUT, STEAL, LIGHT, MEDIUM, HEAVY, WEAPON, GADGET, SPECIALIZATION, DONE }
    enum Action { VAULT_OPENED, BOX_PICKED_UP, BOX_DEPOSITED, CASHOUT_PAID, CASHOUT_STOLEN,
        LIGHT_TRIED, MEDIUM_TRIED, HEAVY_TRIED, WEAPON_CHANGED, GADGET_CHANGED, SPECIALIZATION_CHANGED }

    private Step step = Step.OPEN;

    Step step() { return step; }

    boolean accept(Action action) {
        if (step == Step.DONE || action.ordinal() != step.ordinal()) return false;
        step = Step.values()[step.ordinal() + 1];
        return true;
    }
}
