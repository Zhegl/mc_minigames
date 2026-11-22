package me.petr1furious.potatominigames.games.pillars;

import org.bukkit.entity.Player;
import org.bukkit.entity.LargeFireball;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.World;
import org.bukkit.Location;

/** Keep the atomic charge's native explosion and kill attribution. */
final class PerkOrdnance {
    private PerkOrdnance() { }
    static LargeFireball fireball(Location at, ProjectileSource shooter, Vector direction) {
        return at.getWorld().spawn(at, LargeFireball.class, ball -> {
            ball.setShooter(shooter);
            slowAcceleration(ball);
            ball.setVelocity(direction.clone().multiply(1.2 / 3));
            ball.setYield(2);
            ball.setIsIncendiary(false);
        });
    }

    private static void slowAcceleration(LargeFireball ball) {
        // CraftFireball.setAcceleration updates the current motion. Native ticks add accelerationPower separately.
        try {
            var handle = ball.getClass().getMethod("getHandle").invoke(ball);
            var power = handle.getClass().getField("accelerationPower");
            power.setDouble(handle, power.getDouble(handle) / 3);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not slow the Paper fireball", error);
        }
    }

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
