package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Tracked native TNT: short fuses, separate throw cooldown and bounded simultaneous charges. */
final class PvpBombs {
    static final int STOCK = 12,
            REFILL = 4,
            FUSE = 30,
            THROW_COOLDOWN = 20,
            PLACE_COOLDOWN = 6,
            MAX_ACTIVE = 4;
    static final int MINERS_THROW_COOLDOWN = 40, MINERS_FUSE = 50;
    static final float POWER = 2;
    static final double MAX_DAMAGE = 16, DAMAGE_MULTIPLIER = 1.4, MINERS_MAX_DAMAGE = 10;
    static final double IMPACT_RADIUS = .49; // Half of native TNT's 0.98-block collision box.
    private static final NamespacedKey KEY = new NamespacedKey("potatominigames", "pvp_bomb");
    static final class Cooldowns {
        private final int throwCooldown;
        Cooldowns() { this(THROW_COOLDOWN); }
        Cooldowns(int throwCooldown) { this.throwCooldown = throwCooldown; }
        private final Map<UUID, Integer> handReady = new HashMap<>(), throwReady = new HashMap<>();
        int remaining(UUID owner, int tick, boolean thrown) {
            return Math.max(0, Math.max(handReady.getOrDefault(owner, 0), thrown ? throwReady.getOrDefault(owner, 0) : 0) - tick);
        }
        void used(UUID owner, int tick, boolean thrown) {
            handReady.put(owner, tick + PLACE_COOLDOWN);
            if (thrown) throwReady.put(owner, tick + throwCooldown);
        }
        void forget(UUID owner) { handReady.remove(owner); throwReady.remove(owner); }
        void clear() { handReady.clear(); throwReady.clear(); }
    }

    private record Bomb(Player owner, TNTPrimed entity, boolean airstrike, Location previous) {}

    private final Map<UUID, Bomb> bombs = new HashMap<>();
    private final Cooldowns cooldowns;
    private final int fuse;
    private final double damageMultiplier, damageLimit;
    private final BiPredicate<Player, Entity> impactTarget;
    private BukkitTask cleanup;

    PvpBombs() {
        this((owner, entity) ->
                entity instanceof Player target
                        && !owner.getUniqueId().equals(target.getUniqueId())
                        && target.getGameMode() == GameMode.SURVIVAL
                        && !target.isDead());
    }

    PvpBombs(BiPredicate<Player, Entity> impactTarget) {
        this(THROW_COOLDOWN, FUSE, DAMAGE_MULTIPLIER, MAX_DAMAGE, impactTarget);
    }

    static PvpBombs miners() {
        return new PvpBombs(MINERS_THROW_COOLDOWN, MINERS_FUSE, 1, MINERS_MAX_DAMAGE, null);
    }

    private PvpBombs(
            int throwCooldown,
            int fuse,
            double damageMultiplier,
            double damageLimit,
            BiPredicate<Player, Entity> impactTarget) {
        cooldowns = new Cooldowns(throwCooldown);
        this.fuse = fuse;
        this.damageMultiplier = damageMultiplier;
        this.damageLimit = damageLimit;
        this.impactTarget = impactTarget;
    }

    static ItemStack item(int amount) {
        return item(amount, THROW_COOLDOWN, FUSE, true);
    }
    /** Miners loot retains its original fuse and has no Demolitionist contact perk. */
    static ItemStack item(int amount, int throwCooldown) {
        return item(amount, throwCooldown, MINERS_FUSE, false);
    }

    private static ItemStack item(int amount, int throwCooldown, int fuse, boolean impact) {
        ItemStack item = new ItemStack(Material.TNT, amount);
        item.editMeta(meta -> {
            meta.displayName(I18n.tr("party.duels.bomb.title").color(NamedTextColor.GOLD));
            List<Component> lore =
                    new ArrayList<>(
                            List.of(
                                    I18n.tr("party.duels.bomb.place").color(NamedTextColor.GRAY),
                                    I18n.tr("party.duels.bomb.throw", throwCooldown / 20)
                                            .color(NamedTextColor.GRAY),
                                    I18n.tr("party.duels.bomb.fuse", fuse / 20.0, MAX_ACTIVE)
                                            .color(NamedTextColor.RED)));
            if (impact) lore.add(I18n.tr("party.duels.bomb.impact").color(NamedTextColor.GOLD));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }
    static boolean isBomb(ItemStack item) {
        return item != null && item.getType() == Material.TNT && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
    static int replenish(Player player) {
        var inventory = player.getInventory();
        int owned = 0;
        for (ItemStack stack : inventory.getContents()) if (isBomb(stack)) owned += stack.getAmount();
        int added = PvpPotion.refillAmount(owned, REFILL, STOCK);
        if (added == 0) return 0;
        return added - inventory.addItem(I18n.item(item(added), player)).values().stream().mapToInt(ItemStack::getAmount).sum();
    }
    /** Called only after placement/throw geometry has passed session validation. */
    boolean use(Player player, Location at, boolean thrown, Plugin plugin) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isBomb(held)) return false;
        UUID id = player.getUniqueId();
        int tick = Bukkit.getCurrentTick();
        int remaining = cooldowns.remaining(id, tick, thrown);
        if (remaining > 0) {
            player.sendActionBar(I18n.tr(thrown ? "party.duels.bomb.throw_cooldown" : "party.duels.bomb.place_cooldown",
                    String.format(java.util.Locale.ROOT, "%.1f", remaining / 20.0)).color(NamedTextColor.YELLOW));
            return false;
        }
        prune();
        if (active(id) >= MAX_ACTIVE) {
            player.sendActionBar(I18n.tr("party.duels.bomb.active_limit").color(NamedTextColor.YELLOW));
            return false;
        }
        Vector velocity = thrown ? throwVelocity(player.getEyeLocation().getDirection()) : new Vector();
        TNTPrimed tnt =
                at.getWorld()
                        .spawn(
                                at,
                                TNTPrimed.class,
                                charge -> {
                                    configure(charge, player, velocity);
                                    charge.setFuseTicks(fuse);
                                });
        if (!tnt.isValid()) return false; // A plugin may cancel the spawn; no ammo/cooldown is spent.
        track(player, tnt, thrown);
        cooldowns.used(id, tick, thrown);
        int amount = held.getAmount() - 1;
        held.setAmount(amount);
        player.getInventory().setItemInMainHand(amount > 0 ? held : null);
        at.getWorld().playSound(at, Sound.ENTITY_TNT_PRIMED, .8f, 1);
        maintain(plugin);
        return true;
    }
    static void configure(TNTPrimed charge, Player owner, Vector velocity) {
        charge.setSource(owner);
        charge.setFuseTicks(FUSE);
        charge.setYield(POWER);
        charge.setIsIncendiary(false);
        charge.setPersistent(false);
        charge.setVelocity(velocity);
    }

    void track(Player owner, TNTPrimed entity) {
        track(owner, entity, false);
    }

    void track(Player owner, TNTPrimed entity, boolean thrown) {
        bombs.put(
                entity.getUniqueId(),
                new Bomb(
                        owner,
                        entity,
                        false,
                        thrown && impactTarget != null
                                ? entity.getLocation().add(0, entity.getHeight() / 2, 0)
                                : null));
    }

    void trackAirstrike(Player owner, TNTPrimed entity) {
        bombs.put(entity.getUniqueId(), new Bomb(owner, entity, true, null));
    }

    Player owner(Entity entity) {
        if (!(entity instanceof TNTPrimed)) return null;
        Bomb bomb = bombs.get(entity.getUniqueId());
        return bomb == null ? null : bomb.owner();
    }
    int active(UUID owner) {
        return active(owner, false);
    }
    int activeAirstrike(UUID owner) { return active(owner, true); }
    private int active(UUID owner, boolean airstrike) {
        return (int) bombs.values().stream().filter(bomb -> bomb.airstrike() == airstrike
                && bomb.owner().getUniqueId().equals(owner) && bomb.entity().isValid()).count();
    }
    double blastDamage(Entity entity, double raw) {
        Bomb bomb = entity == null ? null : bombs.get(entity.getUniqueId());
        return Math.clamp(
                raw * damageMultiplier,
                0,
                bomb != null && bomb.airstrike() ? PvpAirstrike.MAX_DAMAGE : damageLimit);
    }
    void maintain(Plugin plugin) {
        if (cleanup == null) cleanup = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    void tick() {
        prune();
        for (Bomb bomb : List.copyOf(bombs.values())) {
            Location before = bomb.previous();
            TNTPrimed tnt = bomb.entity();
            if (before == null || !tnt.isValid() || tnt.getFuseTicks() <= 0) continue;
            Location at = tnt.getLocation().add(0, tnt.getHeight() / 2, 0);
            if (before.getWorld().equals(at.getWorld())) {
                Location hit =
                        impact(before, at, entity -> impactTarget.test(bomb.owner(), entity));
                if (hit != null) {
                    // Keep the tracked native TNT and source: one ordinary blast, correct
                    // armour/terrain handling and same-tick trade attribution, no extra damage hit.
                    tnt.teleport(hit.subtract(0, tnt.getHeight() / 2, 0));
                    tnt.setVelocity(new Vector());
                    tnt.setGravity(false);
                    tnt.setFuseTicks(0);
                }
            }
            before.setWorld(at.getWorld());
            before.setX(at.getX());
            before.setY(at.getY());
            before.setZ(at.getZ());
        }
    }

    static Location impact(
            Location from, Location to, java.util.function.Predicate<Entity> target) {
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return null;
        Vector travel = to.toVector().subtract(from.toVector());
        double distance = travel.length();
        if (distance > .000001) {
            RayTraceResult hit =
                    from.getWorld()
                            .rayTrace(
                                    from,
                                    travel.multiply(1 / distance),
                                    distance,
                                    FluidCollisionMode.NEVER,
                                    true,
                                    IMPACT_RADIUS,
                                    target);
            if (hit != null && hit.getHitEntity() != null)
                return new Location(
                        from.getWorld(),
                        hit.getHitPosition().getX(),
                        hit.getHitPosition().getY(),
                        hit.getHitPosition().getZ());
        }
        // A just-spawned or motionless charge can already be directly inside a target.
        for (Entity entity : to.getWorld().getNearbyEntities(to, .75, 1.5, .75, target))
            if (entity.getBoundingBox().clone().expand(IMPACT_RADIUS).contains(to.toVector()))
                return to.clone();
        return null;
    }
    void prune() {
        // Do not prune from damage/explode callbacks: native TNT is discarded before applying its blast.
        bombs.values().removeIf(bomb -> !bomb.entity().isValid());
        if (bombs.isEmpty() && cleanup != null) { cleanup.cancel(); cleanup = null; }
    }
    void remove(UUID owner) { remove(owner, false); }
    void eliminate(UUID owner) { remove(owner, true); }
    private void remove(UUID owner, boolean keepExploding) {
        for (var entry : List.copyOf(bombs.entrySet())) {
            Bomb bomb = entry.getValue();
            if (!bomb.owner().getUniqueId().equals(owner) || keepExploding && !bomb.entity().isValid()) continue;
            if (bomb.entity().isValid()) bomb.entity().remove();
            bombs.remove(entry.getKey());
        }
        cooldowns.forget(owner);
        if (bombs.isEmpty() && cleanup != null) { cleanup.cancel(); cleanup = null; }
    }
    void clear() {
        for (Bomb bomb : bombs.values()) if (bomb.entity().isValid()) bomb.entity().remove();
        bombs.clear(); cooldowns.clear();
        if (cleanup != null) { cleanup.cancel(); cleanup = null; }
    }

    static double blastDamage(double raw) {
        return Math.clamp(raw * DAMAGE_MULTIPLIER, 0, MAX_DAMAGE);
    }

    static Vector throwVelocity(Vector facing) {
        Vector direction = facing.clone();
        if (direction.lengthSquared() < 1.0e-6) direction = new Vector(0, 0, 1);
        return direction.normalize().multiply(1.05).add(new Vector(0, .12, 0));
    }
}
