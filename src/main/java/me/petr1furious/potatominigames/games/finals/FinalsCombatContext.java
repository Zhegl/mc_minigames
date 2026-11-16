package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.UUID;

/** Session-owned permissions and lifecycle for weapons and gadgets. */
interface FinalsCombatContext {
    JavaPlugin plugin();

    World world();

    long tick();

    Collection<Player> players();

    boolean active(Player player);

    boolean canAct(Player player);

    boolean friendly(Player first, Player second);

    boolean inside(Location location);

    default boolean canPlace(Block block) {
        return block != null && inside(block.getLocation());
    }

    boolean destructible(Block block);

    void breakBlock(Block block);

    void track(Entity entity);

    void damage(Player target, Player source, double amount);

    default void damage(Player target, Player source, double amount, Location origin) {
        damage(target, source, amount);
    }

    default void damageMelee(Player target, Player source, double amount, Location origin) {
        damage(target, source, amount, origin);
    }

    /** Gun hits only; generic damage (including a reflected hit) must not inherit this type. */
    default void damageBullet(Player target, Player source, double amount, Location origin) {
        damage(target, source, amount, origin);
    }

    /** Reflected bullets cannot reflect again, but still cause no gunshot knockback. */
    default void damageReflectedBullet(
            Player target, Player source, double amount, Location origin) {
        damage(target, source, amount, origin);
    }

    default boolean weaponBlocked(Player player) {
        return false;
    }

    default boolean gadgetsBlocked(Player player) {
        return false;
    }

    default double deviceDistance(Player source, Location eye, Vector direction, double reach) {
        return Double.POSITIVE_INFINITY;
    }

    default boolean attackDevice(
            Player source, Location eye, Vector direction, double reach, double damage) {
        return false;
    }

    default boolean tryPullCashout(Player source, Location eye, Vector direction, double reach) {
        return false;
    }

    default boolean tryPullCashbox(Player source, Location eye, Vector direction, double reach) {
        return false;
    }

    void reveal(Player player);

    boolean awaitingRespawn(Player player);

    Location deathLocation(UUID player);

    boolean revive(Player teammate, Player reviver);

    /** Let the physical statue's aim/range test select a defibrillator target. */
    default boolean defibrillateAimed(Player reviver) {
        return false;
    }

    boolean carrier(Player player);

    FinalsClass playerClass(Player player);
}
