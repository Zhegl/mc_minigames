package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.util.Vector;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.List;

/** No health damage: deterministic knockback, with an independent hurt window per victim. */
final class SumoCombat {
    static final int HIT_INTERVAL_TICKS = 10;
    private final Map<UUID, Integer> lastHit = new HashMap<>();

    static void equip(Player player) {
        ItemStack stick = new ItemStack(Material.STICK);
        stick.editMeta(meta -> {
            meta.displayName(I18n.tr("party.duels.sumo.stick").color(NamedTextColor.GOLD));
            meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
            meta.lore(List.of(I18n.tr("party.duels.sumo.stick_lore").color(NamedTextColor.GRAY)));
        });
        player.getInventory().setItem(0, I18n.item(stick, player));
        player.getInventory().setHeldItemSlot(0);
    }

    boolean acceptHit(UUID victim, int tick) {
        Integer previous = lastHit.get(victim);
        if (previous != null && tick - previous < HIT_INTERVAL_TICKS) return false;
        lastHit.put(victim, tick);
        return true;
    }
    void reset() { lastHit.clear(); }

    boolean hit(Player victim, Vector away, boolean sprinting, int knockbackLevel, int tick) {
        if (!acceptHit(victim.getUniqueId(), tick)) return false;
        victim.setVelocity(knockback(away, victim.getVelocity(), sprinting, knockbackLevel));
        victim.playHurtAnimation(0f);
        victim.getWorld().playSound(victim.getLocation(), "entity.player.hurt", org.bukkit.SoundCategory.PLAYERS, .8f, 1f);
        return true;
    }

    static Vector knockback(Vector away, Vector current, boolean sprinting, int knockbackLevel) {
        Vector direction = away.clone().setY(0);
        if (direction.lengthSquared() < 1.0e-6) direction = new Vector(1, 0, 0);
        // Damage is cancelled by the session, so vanilla cannot apply the enchantment itself.
        // Keep one controlled impulse, rather than stacking native and custom knockback.
        int level = Math.clamp(knockbackLevel, 0, 1);
        double strength = .5 + .4 * level + (sprinting ? .2 : 0);
        double cap = .8 + .4 * level;
        Vector motion = direction.normalize().multiply(strength)
                .add(current.clone().setY(0).multiply(.25));
        if (motion.lengthSquared() > cap * cap) motion.normalize().multiply(cap);
        // A low hop leaves room to recover on the platform despite the stronger push.
        motion.setY(Math.max(.30, Math.min(.36, current.getY() * .5 + .30)));
        return motion;
    }
}
