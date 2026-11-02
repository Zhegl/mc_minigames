package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.Component;

/** At most one vulnerable, owner-only mount per lancer, created only when combat starts. */
final class PvpMounts {
    static final double HEALTH = 16;
    static final double SPEED = .25;
    static final double BOOST_SPEED = .30;
    static final double JUMP = .4;
    static final double MAX_SPEAR_DAMAGE = 8;
    private static final class Mount {
        final Player owner;
        final Horse horse;
        long boostedUntil;
        Mount(Player owner, Horse horse) { this.owner = owner; this.horse = horse; }
    }
    private final Map<UUID, Mount> mounts = new HashMap<>();

    void spawn(Player owner) {
        if (mounts.containsKey(owner.getUniqueId())) return;
        Horse horse = owner.getWorld().spawn(owner.getLocation(), Horse.class, mount -> {
            mount.setAdult();
            mount.setAgeLock(true);
            mount.setOwner(owner);
            mount.setTamed(true);
            mount.setColor(Horse.Color.BROWN);
            mount.setStyle(Horse.Style.WHITEFIELD);
            mount.setAware(false);
            mount.setRemoveWhenFarAway(false);
            mount.setPersistent(false);
            mount.setCanPickupItems(false);
            mount.setBreed(false);
            mount.setLootTable(null);
            mount.customName(Component.text("Конь • " + owner.getName()));
            mount.getAttribute(Attribute.MAX_HEALTH).setBaseValue(HEALTH);
            mount.setHealth(HEALTH);
            mount.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(SPEED);
            mount.setJumpStrength(JUMP);
            mount.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            mount.getInventory().setArmor(null);
        });
        track(owner, horse);
        horse.addPassenger(owner);
    }

    void track(Player owner, Horse horse) {
        remove(owner.getUniqueId());
        mounts.put(owner.getUniqueId(), new Mount(owner, horse));
    }
    Horse horse(UUID owner) {
        Mount mount = mounts.get(owner);
        return mount == null ? null : mount.horse;
    }
    Player owner(Entity entity) {
        for (Mount mount : mounts.values()) if (mount.horse.equals(entity)) return mount.owner;
        return null;
    }
    boolean mayRide(Entity rider, Entity horse) {
        Player owner = owner(horse);
        return owner != null && owner.equals(rider);
    }
    /** An enemy can kill the mount, its owner cannot damage it to exploit damage effects. */
    boolean mayDamage(Entity horse, Player attacker) {
        Player owner = owner(horse);
        return owner != null && attacker != null && !owner.equals(attacker);
    }
    boolean boost(Player owner, long now) {
        Mount mount = mounts.get(owner.getUniqueId());
        if (mount == null || !mount.horse.isValid() || !mount.horse.equals(owner.getVehicle())) return false;
        mount.boostedUntil = now + 3;
        mount.horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(BOOST_SPEED);
        return true;
    }
    void tick(long now) {
        for (Mount mount : java.util.List.copyOf(mounts.values())) {
            if (!mount.horse.isValid() || !mount.owner.isOnline()) {
                remove(mount.owner.getUniqueId());
                continue;
            }
            if (mount.boostedUntil > 0 && now >= mount.boostedUntil) {
                mount.boostedUntil = 0;
                mount.horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(SPEED);
            }
            // Slowing a rider also slows their horse, so control classes can counter a charge.
            var slow = mount.owner.getPotionEffect(PotionEffectType.SLOWNESS);
            if (slow != null && mount.horse.equals(mount.owner.getVehicle())) mount.horse.addPotionEffect(slow);
        }
    }
    void remove(UUID owner) {
        Mount mount = mounts.remove(owner);
        if (mount == null) return;
        mount.horse.eject();
        mount.horse.remove();
    }
    void clear() {
        for (UUID owner : java.util.List.copyOf(mounts.keySet())) remove(owner);
    }
    static double spearDamage(double rawDamage) { return Math.min(rawDamage, MAX_SPEAR_DAMAGE); }
}
