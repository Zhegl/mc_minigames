package me.petr1furious.potatominigames.games.cs;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/** Server-authoritative hitscan, hit groups, recovery, movement spread and limited wallbangs. */
final class CsCombat {
    private final CsSession session;
    private final Map<UUID, Long> knifeAt = new HashMap<>(), tagged = new HashMap<>();
    private final Map<UUID, Double> punch = new HashMap<>();
    private final Map<UUID, CsMovement> motion = new HashMap<>();
    private final CsScope scope = new CsScope();
    private final CsTracers tracers = new CsTracers();

    CsCombat(CsSession session) {
        this.session = session;
    }

    void tag(Player p, boolean armoured) {
        tagged.put(p.getUniqueId(), session.now() + 10);
        punch.merge(p.getUniqueId(), armoured ? .15 : 2.0, (a, b) -> Math.min(6, a + b));
    }

    void recordMovement(Player p, Location from, Location to) {
        if (!Objects.equals(from.getWorld(), to.getWorld())) {
            resetMovement(p);
            return;
        }
        motion.computeIfAbsent(p.getUniqueId(), id -> new CsMovement())
                .move(session.now(), to.getX() - from.getX(), to.getZ() - from.getZ());
    }

    void resetMovement(Player p) {
        motion.remove(p.getUniqueId());
    }

    void right(Player p) {
        if (!session.canAct(p) || session.shopOpen(p)) return;
        var gun = session.gun(p);
        if (gun == null) return;
        if (gun.weapon.requiresTriggerRelease()) {
            if (!gun.pressTrigger()) return;
            if (!p.hasActiveItem()) p.startUsingItem(EquipmentSlot.HAND);
        }
        if (gun.weapon.automatic()) gun.heldUntil = session.now() + 6;
        fire(p, gun);
    }

    void releaseTrigger(Player p) {
        var actor = session.actor(p);
        if (actor == null) return;
        for (CsGun gun : new CsGun[] {actor.primary, actor.secondary})
            if (gun != null) gun.releaseTrigger();
    }

    void left(Player p) {
        if (!session.canAct(p) || session.shopOpen(p)) return;
        CsGun gun = session.gun(p);
        if (gun != null && gun.toggleScope(session.now())) {
            if (gun.scoped) movement(p, gun);
            else scope.stop(p);
            session.actionBar(p, CsItems.tr(gun.scoped ? "scope.on" : "scope.off"));
        }
    }

    void switchSlot(Player p, int previous, int next) {
        var a = session.actor(p);
        if (a == null) return;
        var old = a.gun(previous);
        if (old != null) old.switchAway();
        scope.stop(p);
        p.clearActiveItem();
        var gun = a.gun(next);
        if (gun != null) gun.deploy(session.now());
        if (session.canMove(p)) movement(p, gun, next);
    }

    void reload(Player p) {
        var gun = session.gun(p);
        if (session.deathmatch() && session.canAct(p) && gun != null)
            gun.reserve = gun.weapon.reserve;
        if (session.canAct(p) && gun != null && gun.reload(session.now())) {
            scope.stop(p);
            // A held pistol stays latched through reload until the player releases RMB.
            if (!gun.weapon.requiresTriggerRelease()) p.clearActiveItem();
            gun.scoped = false;
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, .5f, 1.4f);
            session.weaponHud(p);
        }
    }

    void tick() {
        tracers.tick(session.now());
        for (Player p : session.players()) {
            var a = session.actor(p);
            if (a == null) continue;
            for (CsGun gun : new CsGun[] {a.primary, a.secondary})
                if (gun != null) gun.tick(session.now());
            CsGun gun = session.gun(p);
            if (!session.canAct(p) || session.shopOpen(p)) {
                stopScope(p);
                releaseTrigger(p);
                if (gun != null && gun.weapon.requiresTriggerRelease()) p.clearActiveItem();
            } else if (gun != null && gun.triggerHeld() && !p.hasActiveItem()) gun.releaseTrigger();
            if (session.canAct(p)
                    && !session.shopOpen(p)
                    && gun != null
                    && gun.weapon.automatic()
                    && session.now() < gun.heldUntil) fire(p, gun);
            punch.computeIfPresent(p.getUniqueId(), (id, n) -> n < .02 ? null : n * .85);
            if (session.canMove(p)) {
                movement(p, gun);
                if (p.isSprinting()) p.setSprinting(false);
            }
        }
    }

    private void movement(Player p, CsGun gun) {
        movement(p, gun, p.getInventory().getHeldItemSlot());
    }

    private void movement(Player p, CsGun gun, int slot) {
        var actor = session.actor(p);
        boolean grenade =
                slot >= 3
                        && slot <= 6
                        && actor != null
                        && actor.utility.getOrDefault(CsMatch.Utility.values()[slot - 3], 0) > 0;
        double speed = .26 * (gun != null ? gun.weapon.mobility : grenade ? .98 : 1);
        if (gun != null
                && session.now() >= gun.lastShot
                && session.now() - gun.lastShot <= Math.ceil(gun.weapon.cadence))
            speed *= gun.weapon.attackMobility();
        if (session.now() < tagged.getOrDefault(p.getUniqueId(), 0L)) speed *= .45;
        if (gun != null && gun.scoped) scope.apply(p, gun.weapon, speed);
        else {
            scope.stop(p);
            if (Math.abs(p.getWalkSpeed() - speed) > .000001) p.setWalkSpeed((float) speed);
        }
    }

    void stopScope(Player p) {
        scope.stop(p);
        var actor = session.actor(p);
        if (actor != null)
            for (CsGun gun : new CsGun[] {actor.primary, actor.secondary})
                if (gun != null) gun.scoped = false;
    }

    @SuppressWarnings("deprecation")
    private void fire(Player p, CsGun gun) {
        if (!session.canAct(p) || session.shopOpen(p) || !gun.fire(session.now())) {
            if (gun.magazine == 0) reload(p);
            return;
        }
        session.attacking(p);
        session.cancelChannel(p.getUniqueId());
        var w = gun.weapon;
        Location eye = p.getEyeLocation();
        double spread = gun.shotSpread();
        if (p.isSneaking()) spread *= .65;
        CsMovement movement = motion.get(p.getUniqueId());
        spread += w.movementSpread(movement == null ? 0 : movement.speed(session.now()));
        if (!p.isOnGround()) spread += .10;
        double recoilPitch = w.recoilPitch(gun.burst, gun.scoped);
        double recoilYaw =
                w.automatic()
                        ? Math.sin((gun.burst - 1) * .72)
                                * Math.min(2.3, (gun.burst - 1) * .12)
                                * (w == CsWeapon.NEGEV && gun.burst >= 12 ? .2 : 1)
                        : 0;
        Location aim = eye.clone();
        aim.setPitch(
                (float)
                        Math.max(
                                -89,
                                eye.getPitch()
                                        - recoilPitch
                                        - punch.getOrDefault(p.getUniqueId(), 0.0)));
        aim.setYaw((float) (eye.getYaw() + recoilYaw));
        Map<HitGroup, Impact> hits = new LinkedHashMap<>();
        for (int pellet = 0; pellet < w.pellets; pellet++) {
            Vector direction = spread(aim.getDirection(), spread);
            Shot shot = trace(p, eye, direction, w.range(), w);
            Impact hit = shot.impact;
            if (pellet == 0 && !w.silenced()) tracer(eye, direction, shot.distance);
            if (hit != null)
                hits.merge(
                        new HitGroup(hit.target.getUniqueId(), hit.head, hit.leg),
                        hit,
                        (a, b) ->
                                new Impact(
                                        a.target,
                                        a.damage + b.damage,
                                        a.head || b.head,
                                        a.leg && b.leg,
                                        Math.min(a.distance, b.distance)));
        }
        CsAudio.shot(session.viewers(), eye, w);
        for (Impact hit : hits.values())
            session.hurt(
                    hit.target,
                    p.getUniqueId(),
                    hit.damage,
                    hit.head,
                    hit.leg,
                    w,
                    CsMatch.KillCause.BULLET);
        if (!hits.isEmpty()) p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, .5f, 1.2f);
        if (gun.magazine == 0) reload(p);
        session.weaponHud(p);
    }

    private record HitGroup(UUID target, boolean head, boolean leg) {}

    private record Impact(
            Player target, double damage, boolean head, boolean leg, double distance) {}

    private record Shot(Impact impact, double distance) {}

    private Shot trace(
            Player shooter, Location origin, Vector direction, double distance, CsWeapon weapon) {
        Location at = origin.clone();
        double travelled = 0, scale = 1;
        int walls = 0;
        while (travelled < distance) {
            RayTraceResult hit =
                    session.world()
                            .rayTrace(
                                    at,
                                    direction,
                                    distance - travelled,
                                    FluidCollisionMode.NEVER,
                                    true,
                                    .025,
                                    e ->
                                            e instanceof Player target
                                                    && !target.getUniqueId()
                                                            .equals(shooter.getUniqueId())
                                                    && session.active(target));
            if (hit == null) return new Shot(null, distance);
            double segment = at.toVector().distance(hit.getHitPosition());
            travelled += segment;
            if (hit.getHitEntity() instanceof Player target) {
                double y = hit.getHitPosition().getY(),
                        height = target.getHeight(),
                        feet = target.getLocation().getY();
                boolean head = y > feet + height - .38, leg = y < feet + .58;
                double multiplier = head ? 4 : leg ? .75 : y < feet + 1.0 ? 1.25 : 1;
                double falloff = weapon.damageScale(travelled);
                return new Shot(
                        new Impact(
                                target,
                                weapon.damage * multiplier * scale * falloff,
                                head,
                                leg,
                                travelled),
                        travelled);
            }
            Block block = hit.getHitBlock();
            if (block == null) return new Shot(null, travelled);
            if (session.wingman() && session.arena().wingmanBoundary(block))
                return new Shot(null, travelled);
            String material = block.getType().name();
            boolean glass = material.contains("GLASS");
            if (glass) session.arena().breakGlass(block);
            double max =
                    glass
                            ? 1.1
                            : material.contains("PLANK")
                                            || material.contains("WOOD")
                                            || material.contains("TRAPDOOR")
                                            || material.contains("FENCE")
                                    ? 1.25
                                    : material.contains("LEAVES")
                                            ? 2.5
                                            : weapon.category == CsWeapon.Category.SNIPER
                                                            || weapon.category
                                                                    == CsWeapon.Category.HEAVY
                                                            || weapon == CsWeapon.AK47
                                                            || weapon == CsWeapon.SG553
                                                    ? .65
                                                    : 0;
            if (walls++ >= 2 || max == 0) return new Shot(null, travelled);
            Location exit =
                    new Location(
                            session.world(),
                            hit.getHitPosition().getX(),
                            hit.getHitPosition().getY(),
                            hit.getHitPosition().getZ());
            double thickness = 0;
            do {
                exit.add(direction.clone().multiply(.1));
                thickness += .1;
            } while (thickness < max && !exit.getBlock().isPassable());
            if (!exit.getBlock().isPassable()) return new Shot(null, travelled);
            scale *= glass ? .9 : .5;
            scale *= Math.max(.25, 1 - thickness * .4);
            at = exit.add(direction.clone().multiply(.05));
            travelled += thickness + .05;
        }
        return new Shot(null, distance);
    }

    private Vector spread(Vector forward, double amount) {
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < .0001) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        double angle = session.random().nextDouble() * Math.PI * 2,
                r = Math.sqrt(session.random().nextDouble()) * amount;
        return forward.clone()
                .add(right.multiply(Math.cos(angle) * r))
                .add(up.multiply(Math.sin(angle) * r))
                .normalize();
    }

    private void tracer(Location from, Vector direction, double distance) {
        tracers.draw(from, direction, distance, session.viewers(), session.now());
    }

    void knife(Player p, boolean heavy, Player expected) {
        if (!session.canAct(p)
                || p.getInventory().getHeldItemSlot() != 2
                || session.now() < knifeAt.getOrDefault(p.getUniqueId(), 0L)) return;
        session.attacking(p);
        knifeAt.put(p.getUniqueId(), session.now() + (heavy ? 20 : 10));
        var hit =
                session.world()
                        .rayTrace(
                                p.getEyeLocation(),
                                p.getEyeLocation().getDirection(),
                                heavy ? 2.7 : 3,
                                FluidCollisionMode.NEVER,
                                true,
                                .12,
                                e ->
                                        e instanceof Player target
                                                && target != p
                                                && session.active(target));
        if (hit == null
                || !(hit.getHitEntity() instanceof Player target)
                || expected != null && target != expected) return;
        Vector to =
                target.getLocation()
                        .toVector()
                        .subtract(p.getLocation().toVector())
                        .setY(0)
                        .normalize();
        boolean back = target.getEyeLocation().getDirection().setY(0).normalize().dot(to) > .55;
        session.hurt(
                target,
                p.getUniqueId(),
                back ? (heavy ? 180 : 90) : (heavy ? 65 : 40),
                false,
                false,
                null,
                CsMatch.KillCause.KNIFE);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, .6f, 1);
    }

    void clear() {
        clear(true);
    }

    void roundEnded() {
        // Let the lethal shot's streak finish its normal lifetime during the loot period.
        clear(false);
    }

    private void clear(boolean visuals) {
        if (visuals) tracers.clear();
        scope.clear();
        for (Player player : session.players()) {
            stopScope(player);
            releaseTrigger(player);
            var gun = session.gun(player);
            if (gun != null && gun.weapon.requiresTriggerRelease()) player.clearActiveItem();
        }
        knifeAt.clear();
        tagged.clear();
        punch.clear();
        motion.clear();
    }
}
