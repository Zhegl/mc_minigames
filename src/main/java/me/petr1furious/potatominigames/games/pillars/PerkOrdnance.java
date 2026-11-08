package me.petr1furious.potatominigames.games.pillars;

import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.World;
import org.bukkit.Location;

/** Keep the atomic charge's native explosion and kill attribution. */
final class PerkOrdnance {
    private PerkOrdnance() { }
    static void armAtomic(TNTPrimed bomb, Player owner) {
        bomb.setSource(owner);
        bomb.setFuseTicks(PerkRules.ATOMIC_FUSE_TICKS);
        bomb.setYield(PerkRules.ATOMIC_POWER);
        bomb.setIsIncendiary(false);
    }
    static boolean burstDecoy(World world, Location at, Player owner) {
        return world.createExplosion(owner, at, PerkRules.DECOY_POWER, false, false, true);
    }
}
