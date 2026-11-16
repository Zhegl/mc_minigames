package me.petr1furious.potatominigames.games.party;

import java.util.Random;

/** Shared chest contents, claimed on the server; menu icons are only viewer-local projections. */
final class MagicChestLoot {
    private final MagicWand[] wands = new MagicWand[2];
    private boolean wool = true;
    final long refillAt;
    MagicChestLoot(Random random, long refillAt) {
        this.refillAt = refillAt;
        for (int i = 0; i < wands.length; i++) wands[i] = MagicWand.loot(random);
    }
    MagicWand wand(int index) { return index >= 0 && index < 2 ? wands[index] : null; }
    boolean wool() { return wool; }
    void takeWand(int index) { wands[index] = null; }
    void takeWool() { wool = false; }
}
