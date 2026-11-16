package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Per-life primary weapons. Session permissions and damage attribution remain in the context. */
final class FinalsWeapons {
    private static final int TRIGGER_TICKS = 6;
    private final FinalsCombatContext context;
    private final NamespacedKey itemKey = new NamespacedKey("potatominigames", "finals_weapon");
    private final Map<UUID, Equipped> equipped = new HashMap<>();

    private static final class Equipped {
        final FinalsWeapon weapon;
        final FinalsGunState gun;
        long nextMelee;
        long guardUntil;

        Equipped(FinalsWeapon weapon) {
            this.weapon = weapon;
            this.gun =
                    weapon.isGun()
                            ? new FinalsGunState(
                                    weapon.magazine(), weapon.cadenceTicks(), weapon.reloadTicks())
                            : null;
        }
    }

    FinalsWeapons(FinalsCombatContext context) {
        this.context = context;
    }

    void equip(Player player, FinalsWeapon weapon) {
        if (weapon == null || context.playerClass(player) != weapon.playerClass())
            throw new IllegalArgumentException("Weapon does not belong to the player's class");
        forget(player.getUniqueId());
        equipped.put(player.getUniqueId(), new Equipped(weapon));
        ItemStack item = new ItemStack(weapon.material());
        item.editMeta(meta -> {
            meta.displayName(weapon.display());
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, weapon.id());
            List<Component> lore = new ArrayList<>();
            if (weapon.isGun()) {
                lore.add(I18n.tr("finals.weapon.gun_controls"));
                lore.add(I18n.tr("finals.weapon.magazine", weapon.magazine()));
            } else {
                lore.add(I18n.tr("finals.weapon.melee_controls"));
                if (weapon == FinalsWeapon.DAGGER) lore.add(I18n.tr("finals.weapon.dagger_hint"));
                if (weapon == FinalsWeapon.RIOT_SHIELD)
                    lore.add(I18n.tr("finals.weapon.guard_hint"));
                if (weapon == FinalsWeapon.DUAL_BLADES)
                    lore.add(I18n.tr("finals.weapon.deflect_hint"));
                if (weapon == FinalsWeapon.SLEDGEHAMMER)
                    lore.add(I18n.tr("finals.weapon.hammer_hint"));
                if (weapon == FinalsWeapon.SPEAR) lore.add(I18n.tr("finals.weapon.spear_hint"));
            }
            meta.lore(lore);
        });
        player.getInventory().setItem(0, I18n.item(item, player));
        player.getInventory().setHeldItemSlot(0);
    }

    FinalsWeapon selected(UUID player) {
        Equipped held = equipped.get(player);
        return held == null ? null : held.weapon;
    }

    private boolean holding(Player player, Equipped held) {
        if (player.getInventory().getHeldItemSlot() != 0) return false;
        ItemStack item = player.getInventory().getItemInMainHand();
        return item != null
                && item.hasItemMeta()
                && held.weapon
                        .id()
                        .equals(
                                item.getItemMeta()
                                        .getPersistentDataContainer()
                                        .get(itemKey, PersistentDataType.STRING));
    }

    private boolean usable(Player player, Equipped held) {
        return held != null
                && context.canAct(player)
                && !context.weaponBlocked(player)
                && context.active(player)
                && context.inside(player.getLocation())
                && context.playerClass(player) == held.weapon.playerClass()
                && holding(player, held);
    }

    /**
     * Returns true only when this primary consumed the interaction. Duplicate offhand input is
     * inert.
     */
    boolean interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return false;
        Player player = event.getPlayer();
        Equipped held = equipped.get(player.getUniqueId());
        if (held == null || !holding(player, held)) return false;
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) return false;
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (!usable(player, held)) return true;
        if (held.weapon.isGun()) {
            if (right) {
                if (held.weapon.automatic()) held.gun.hold(context.tick(), TRIGGER_TICKS);
                fire(player, held);
            }
        } else if (left) {
            swing(player, held, null);
        } else if (held.weapon == FinalsWeapon.SLEDGEHAMMER) {
            demolish(player, held);
        } else if (held.weapon == FinalsWeapon.RIOT_SHIELD
                || held.weapon == FinalsWeapon.DUAL_BLADES) {
            held.guardUntil = context.tick() + TRIGGER_TICKS;
        }
        return true;
    }

    boolean reload(Player player) {
        Equipped held = equipped.get(player.getUniqueId());
        if (!usable(player, held) || held.gun == null || !held.gun.reload(context.tick()))
            return false;
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, .45f, 1.5f);
        return true;
    }

    void tick() {
        for (Player player : List.copyOf(context.players())) {
            Equipped held = equipped.get(player.getUniqueId());
            if (held == null) continue;
            if (!context.active(player)) {
                forget(player.getUniqueId());
                continue;
            }
            if (!usable(player, held)) {
                stopFiring(player.getUniqueId());
                continue;
            }
            if (held.gun != null) {
                boolean reloading = held.gun.reloading();
                held.gun.advance(context.tick());
                if (reloading && !held.gun.reloading())
                    player.playSound(
                            player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, .45f, 1.5f);
                if (held.weapon.automatic() && held.gun.held(context.tick())) fire(player, held);
            }
        }
    }

    private void fire(Player source, Equipped held) {
        if (!usable(source, held)) return;
        if (!held.gun.fire(context.tick())) {
            if (held.gun.ammo() == 0) reload(source);
            return;
        }
        context.reveal(source);
        FinalsWeapon weapon = held.weapon;
        Location eye = source.getEyeLocation();
        Map<Player, Double> hits = new LinkedHashMap<>();
        for (int pellet = 0; pellet < weapon.pellets(); pellet++) {
            Vector direction = spread(eye.getDirection(), weapon.spread());
            RayTraceResult hit = trace(source, eye, direction, weapon.range(), .04);
            double distance =
                    hit == null ? weapon.range() : eye.toVector().distance(hit.getHitPosition());
            double device = context.deviceDistance(source, eye, direction, distance);
            if (Double.isFinite(device) && device <= distance) {
                context.attackDevice(
                        source, eye, direction, device + .001, weapon.damageAt(device, false));
                if (pellet == 0) tracer(eye, direction, device);
                continue;
            }
            // One tracer per shot keeps the LMG/shotgun effect bounded.
            if (pellet == 0) tracer(eye, direction, distance);
            if (hit != null
                    && hit.getHitEntity() instanceof Player target
                    && enemy(source, target)) {
                boolean head =
                        hit.getHitPosition().getY() >= target.getBoundingBox().getMaxY() - .42;
                hits.merge(target, weapon.damageAt(distance, head), Double::sum);
            }
        }
        source.getWorld()
                .playSound(
                        source.getLocation(),
                        weapon == FinalsWeapon.SR84
                                ? Sound.ENTITY_FIREWORK_ROCKET_BLAST
                                : Sound.ENTITY_FIREWORK_ROCKET_SHOOT,
                        .65f,
                        weapon.pellets() > 1 ? .7f : weapon == FinalsWeapon.SR84 ? .6f : 1.8f);
        // A shotgun deals one attributed damage event per target, avoiding per-pellet immunity
        // artifacts.
        for (var hit : hits.entrySet()) {
            if (enemy(source, hit.getKey()))
                context.damageBullet(hit.getKey(), source, hit.getValue(), eye);
        }
        if (!hits.isEmpty())
            source.playSound(source.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .35f, 1.7f);
        if (held.gun.ammo() == 0) reload(source);
    }

    private RayTraceResult trace(
            Player source, Location origin, Vector direction, double range, double radius) {
        return context.world()
                .rayTrace(
                        origin,
                        direction,
                        range,
                        FluidCollisionMode.NEVER,
                        true,
                        radius,
                        entity ->
                                entity instanceof Player target
                                        && !target.getUniqueId().equals(source.getUniqueId())
                                        && context.active(target));
    }

    private boolean enemy(Player source, Player target) {
        return !source.getUniqueId().equals(target.getUniqueId())
                && context.active(source)
                && context.active(target)
                && context.canAct(source)
                && !context.friendly(source, target)
                && context.inside(source.getLocation())
                && context.inside(target.getLocation());
    }

    private static Vector spread(Vector forward, double amount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < .0001) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        double angle = random.nextDouble(Math.PI * 2);
        double radius = Math.sqrt(random.nextDouble()) * amount;
        return forward.clone()
                .add(right.multiply(Math.cos(angle) * radius))
                .add(up.multiply(Math.sin(angle) * radius))
                .normalize();
    }

    private void tracer(Location eye, Vector direction, double distance) {
        for (double d = .6; d < Math.min(distance, 45); d += 1.7)
            context.world()
                    .spawnParticle(
                            Particle.CRIT,
                            eye.clone().add(direction.clone().multiply(d)),
                            1,
                            0,
                            0,
                            0,
                            0);
    }

    /** The session bypasses this handler while dispatching its own context.damage call. */
    void melee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player source)) return;
        Equipped held = equipped.get(source.getUniqueId());
        if (held == null) return;
        event.setCancelled(true);
        if (held.weapon.isGun() || !usable(source, held)) return;
        swing(source, held, event.getEntity() instanceof Player target ? target : null);
    }

    private void swing(Player source, Equipped held, Player expected) {
        if (context.tick() < held.nextMelee) return;
        held.nextMelee = context.tick() + held.weapon.cadenceTicks();
        held.guardUntil = 0;
        context.reveal(source);
        RayTraceResult hit =
                trace(
                        source,
                        source.getEyeLocation(),
                        source.getEyeLocation().getDirection(),
                        held.weapon.range(),
                        .15);
        source.getWorld()
                .playSound(
                        source.getLocation(),
                        Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                        .6f,
                        held.weapon == FinalsWeapon.SLEDGEHAMMER ? .65f : 1.3f);
        Location eye = source.getEyeLocation();
        double reach =
                hit == null ? held.weapon.range() : eye.toVector().distance(hit.getHitPosition());
        double device = context.deviceDistance(source, eye, eye.getDirection(), reach);
        if (Double.isFinite(device)
                && device <= reach
                && context.attackDevice(
                        source, eye, eye.getDirection(), device + .001, held.weapon.damage()))
            return;
        if (hit == null
                || !(hit.getHitEntity() instanceof Player target)
                || expected != null && !expected.getUniqueId().equals(target.getUniqueId())
                || !enemy(source, target)) return;
        double damage = held.weapon.damage();
        if (held.weapon == FinalsWeapon.DAGGER
                && behind(
                        target.getLocation().getDirection(),
                        source.getLocation().toVector().subtract(target.getLocation().toVector())))
            damage *= 4;
        context.damageMelee(target, source, damage, eye);
        source.playSound(source.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .35f, 1.7f);
    }

    private void demolish(Player source, Equipped held) {
        if (context.tick() < held.nextMelee) return;
        RayTraceResult hit =
                context.world()
                        .rayTraceBlocks(
                                source.getEyeLocation(),
                                source.getEyeLocation().getDirection(),
                                held.weapon.range(),
                                FluidCollisionMode.NEVER,
                                true);
        if (hit == null || hit.getHitBlock() == null || !context.destructible(hit.getHitBlock()))
            return;
        held.nextMelee = context.tick() + 30;
        context.reveal(source);
        Block center = hit.getHitBlock();
        // Face-connected cross: at most seven arena blocks, checked individually by the session.
        int[][] offsets = {
            {0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
        };
        for (int[] offset : offsets) {
            Block block = center.getRelative(offset[0], offset[1], offset[2]);
            if (context.inside(block.getLocation()) && context.destructible(block))
                context.breakBlock(block);
        }
        context.world().playSound(center.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, .55f, .75f);
    }

    boolean canReflectBullet(Player defender, Player shooter) {
        return canReflectBullet(defender, shooter, shooter.getEyeLocation());
    }

    boolean canReflectBullet(Player defender, Player shooter, Location origin) {
        Equipped held = equipped.get(defender.getUniqueId());
        return held != null && held.weapon == FinalsWeapon.DUAL_BLADES
                && guarding(defender, shooter, held, true, origin) && enemy(defender, shooter);
    }

    /** Called after the session validates the guard; the original hit still deals reduced damage. */
    void reflectBullet(Player defender, Player shooter, double damage) {
        Location origin = defender.getEyeLocation();
        Vector direction = shooter.getEyeLocation().toVector().subtract(origin.toVector());
        double distance = direction.length();
        if (distance > 1e-6) tracer(origin, direction.multiply(1 / distance), distance);
        context.world().playSound(origin, Sound.ITEM_SHIELD_BLOCK, .7f, 1.4f);
        context.reveal(defender);
        // The original ray already reached the defender. Return its aggregated damage once,
        // credited to the defender, through normal protection/shield/kill handling. The return
        // hit deliberately cannot bounce again between two guarding players.
        context.damageReflectedBullet(shooter, defender, damage, origin);
    }

    private boolean guarding(Player target, Player source, Equipped held, boolean ranged, Location origin) {
        if (!usable(target, held) || context.tick() >= held.guardUntil) return false;
        if (ranged && held.weapon == FinalsWeapon.DUAL_BLADES) {
            Location eye = target.getEyeLocation();
            Location incoming = origin == null ? source.getEyeLocation() : origin;
            return precisionBlock(eye.getDirection(), incoming.toVector().subtract(eye.toVector()));
        }
        Vector attacker = source.getLocation().toVector().subtract(target.getLocation().toVector());
        return frontal(target.getLocation().getDirection(), attacker);
    }

    /**
     * Applied by the session once, after team/protection validation, for every attributed enemy
     * hit.
     */
    double incomingMultiplier(Player target, Player source) {
        return incomingMultiplier(target, source, false, false, null);
    }

    double incomingMultiplier(Player target, Player source, boolean ranged, boolean melee, Location origin) {
        Equipped held = equipped.get(target.getUniqueId());
        if (held == null
                || held.weapon != FinalsWeapon.RIOT_SHIELD
                        && held.weapon != FinalsWeapon.DUAL_BLADES
                || !guarding(target, source, held, ranged, origin)) return 1;
        return held.weapon == FinalsWeapon.RIOT_SHIELD ? .35 : melee ? .75 : .7;
    }

    static boolean precisionBlock(Vector facing, Vector towardAttacker) {
        if (facing.lengthSquared() < 1e-8 || towardAttacker.lengthSquared() < 1e-8) return false;
        return facing.clone().normalize().dot(towardAttacker.clone().normalize())
                >= Math.cos(Math.toRadians(14)) - 1e-10;
    }

    static boolean frontal(Vector facing, Vector towardAttacker) {
        return horizontalDot(facing, towardAttacker) > .35;
    }

    static boolean behind(Vector facing, Vector towardAttacker) {
        return horizontalDot(facing, towardAttacker) < -.55;
    }

    private static double horizontalDot(Vector facing, Vector offset) {
        Vector a = facing.clone().setY(0);
        Vector b = offset.clone().setY(0);
        return a.lengthSquared() < .0001 || b.lengthSquared() < .0001
                ? 0
                : a.normalize().dot(b.normalize());
    }

    Component hud(Player player) {
        Equipped held = equipped.get(player.getUniqueId());
        if (held == null) return Component.empty();
        if (held.gun == null)
            return I18n.tr("finals.weapon.melee_hud", held.weapon.display())
                    .color(NamedTextColor.YELLOW);
        if (held.gun.reloading())
            return I18n.tr(
                            "finals.weapon.reloading_hud",
                            held.weapon.display(),
                            (held.gun.reloadRemaining(context.tick()) + 19) / 20)
                    .color(NamedTextColor.GOLD);
        return I18n.tr(
                        "finals.weapon.ammo_hud",
                        held.weapon.display(),
                        held.gun.ammo(),
                        held.weapon.magazine())
                .color(NamedTextColor.YELLOW);
    }

    void stopFiring(UUID player) {
        Equipped held = equipped.get(player);
        if (held == null) return;
        held.guardUntil = 0;
        if (held.gun != null) held.gun.cancelReload();
    }

    void forget(UUID player) {
        Equipped held = equipped.remove(player);
        if (held != null && held.gun != null) held.gun.close();
    }

    void clear() {
        for (Equipped held : equipped.values()) if (held.gun != null) held.gun.close();
        equipped.clear();
    }
}
