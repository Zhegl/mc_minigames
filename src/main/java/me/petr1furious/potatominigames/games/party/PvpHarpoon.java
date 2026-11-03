package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Native dry-land spin collisions; a short-lived task only exists while somebody charges. */
final class PvpHarpoon {
    static final int DURATION = 12;
    static final double DAMAGE = 6;
    static final class HitWindow {
        private final int endsAt;
        private final Set<UUID> hit = new HashSet<>();
        HitWindow(int tick) { endsAt = tick + DURATION; }
        boolean active(int tick) { return tick < endsAt; }
        boolean accept(UUID target, int tick) { return active(tick) && hit.add(target); }
    }
    private record Push(Player target, Vector direction) { }
    private static final class Charge {
        final Player player;
        final HitWindow hits;
        final Vector direction;
        Push push;
        Charge(Player player, int tick, Vector direction) {
            this.player = player; this.hits = new HitWindow(tick); this.direction = direction;
        }
    }
    private final Map<UUID, Charge> charges = new HashMap<>();
    private BukkitTask task;

    boolean start(Player player, Plugin plugin, Predicate<Player> fighting) {
        ItemStack trident = null;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.TRIDENT) {
                trident = item;
                if (slot < 9) inventory.setHeldItemSlot(slot);
                break;
            }
        }
        // A thrown trident is a deliberate commitment; the shell cannot create another weapon.
        if (trident == null || player.isInsideVehicle() || charges.containsKey(player.getUniqueId())) return false;
        Vector motion = motion(player.getLocation().getDirection());
        charges.put(player.getUniqueId(), new Charge(player, Bukkit.getCurrentTick(), motion.clone().setY(0)));
        player.startRiptideAttack(DURATION, (float) DAMAGE, trident.clone());
        player.setVelocity(motion);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, .8f, 1);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(fighting), 1, 1);
        return true;
    }
    boolean active(Player player) {
        Charge charge = charges.get(player.getUniqueId());
        return charge != null && charge.hits.active(Bukkit.getCurrentTick());
    }
    boolean accept(Player attacker, LivingEntity target) {
        Charge charge = charges.get(attacker.getUniqueId());
        return charge != null && charge.hits.accept(target.getUniqueId(), Bukkit.getCurrentTick());
    }
    void landed(Player attacker, Player target) {
        Charge charge = charges.get(attacker.getUniqueId());
        if (charge != null) charge.push = new Push(target, charge.direction.clone());
    }
    private void tick(Predicate<Player> fighting) {
        int tick = Bukkit.getCurrentTick();
        charges.values().removeIf(charge -> {
            if (!fighting.test(charge.player)) { stop(charge); return true; }
            // Apply after vanilla knockback; do not bypass shields, armour resistance or death.
            Push push = charge.push;
            charge.push = null;
            if (push != null && fighting.test(push.target())) {
                var resistance = push.target().getAttribute(Attribute.KNOCKBACK_RESISTANCE);
                double value = resistance == null ? 0 : resistance.getValue();
                push.target().setVelocity(knockback(push.direction(), value));
            }
            if (!charge.hits.active(tick)) { stop(charge); return true; }
            return false;
        });
        if (charges.isEmpty()) cancelTask();
    }
    void remove(UUID player) {
        Charge charge = charges.remove(player);
        if (charge != null) stop(charge);
        for (Charge other : charges.values()) {
            if (other.push != null && other.push.target().getUniqueId().equals(player)) other.push = null;
        }
        if (charges.isEmpty()) cancelTask();
    }
    void clear() {
        charges.values().forEach(PvpHarpoon::stop);
        charges.clear(); cancelTask();
    }
    private void cancelTask() {
        if (task != null) { task.cancel(); task = null; }
    }
    private static void stop(Charge charge) {
        // Paper requires a positive duration. Replace the remaining attack with one harmless
        // tick using an empty weapon; vanilla then clears its timer, damage and visual flag.
        // Clearing only the flag would leave the old timer capable of normal melee attacks.
        if (charge.player.isRiptiding())
            charge.player.startRiptideAttack(1, 0, new ItemStack(Material.AIR));
    }
    static Vector motion(Vector facing) {
        Vector horizontal = facing.clone().setY(0);
        if (horizontal.lengthSquared() < 1.0e-6) horizontal = new Vector(0, 0, 1);
        return horizontal.normalize().multiply(1.45).setY(Math.clamp(.15 + facing.getY() * .4, -.15, .35));
    }
    static Vector knockback(Vector direction, double resistance) {
        Vector horizontal = direction.clone().setY(0);
        if (horizontal.lengthSquared() < 1.0e-6) horizontal = new Vector(0, 0, 1);
        double scale = 1 - Math.clamp(resistance, 0, 1);
        return horizontal.normalize().multiply(.65 * scale).setY(.25 * scale);
    }
}
