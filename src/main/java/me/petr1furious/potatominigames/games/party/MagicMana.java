package me.petr1furious.potatominigames.games.party;

import java.util.EnumMap;
import java.util.Map;

/** A personal mana pool and per-spell clocks. Changing wands cannot reset cooldowns. */
final class MagicMana {
    static final double MAX = 100, REGEN_PER_SECOND = 7;
    private double mana = MAX;
    private long nextCast;
    private final Map<MagicSpell, Long> readyAt = new EnumMap<>(MagicSpell.class);
    double mana() {
        return mana;
    }

    void restore(double amount) {
        mana = Math.clamp(mana + amount, 0, MAX);
    }

    void tick() {
        restore(REGEN_PER_SECOND / 20);
    }

    long remaining(MagicSpell spell, long tick) {
        return Math.max(0, Math.max(nextCast, readyAt.getOrDefault(spell, 0L)) - tick);
    }

    boolean ready(MagicSpell spell, long tick) {
        return tick >= nextCast && remaining(spell, tick) == 0 && mana + 1e-9 >= spell.mana;
    }

    boolean spend(MagicSpell spell, long tick) {
        if (!ready(spell, tick)) return false;
        restore(-spell.mana);
        nextCast = tick + 4;
        readyAt.put(spell, tick + spell.cooldown);
        return true;
    }
}
