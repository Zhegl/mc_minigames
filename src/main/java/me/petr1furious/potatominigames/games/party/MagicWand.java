package me.petr1furious.potatominigames.games.party;

import java.util.List;
import java.util.Random;

/** A physical wand owns its spells; personal mana and cooldowns remain separate. */
record MagicWand(MagicSpell first, MagicSpell second) {
    static final List<MagicSpell> STARTERS = List.of(
            MagicSpell.FIREBOLT, MagicSpell.ICE_LANCE, MagicSpell.GALE,
            MagicSpell.STONE_SHOT, MagicSpell.MEND, MagicSpell.RIFT);

    MagicWand {
        if (first == null || second != null && (second == first || second.school != first.school))
            throw new IllegalArgumentException("Invalid wand spells");
    }

    static MagicWand single(MagicSpell spell) { return new MagicWand(spell, null); }
    boolean enchanted() { return second != null; }
    MagicSpell cast(boolean leftClick) { return leftClick ? second : first; }

    static MagicWand loot(Random random) {
        MagicSpell first = MagicSpell.values()[random.nextInt(MagicSpell.values().length)];
        if (random.nextInt(4) != 0) return single(first);
        List<MagicSpell> others = first.school.spells().stream().filter(s -> s != first).toList();
        return new MagicWand(first, others.get(random.nextInt(others.size())));
    }
}
