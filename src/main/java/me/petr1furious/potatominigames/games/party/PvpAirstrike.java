package me.petr1furious.potatominigames.games.party;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** A locked, warned 7x7 drop zone with nine weaker TNT, separate from normal carried charges. */
final class PvpAirstrike {
    static final int WARNING_TICKS = 10, ROW_INTERVAL = 4, FUSE = 18, COUNT = 9;
    static final double HALF_SIZE = 3.5, HEIGHT = 6, MAX_DAMAGE = 9;
    static final float POWER = 1.5f;
    static final class Waves {
        private final int started;
        private int row;
        private boolean cancelled;
        Waves(int started) { this.started = started; }
        int take(int tick) {
            if (cancelled || row >= 3 || tick - started < WARNING_TICKS + row * ROW_INTERVAL) return -1;
            return row++;
        }
        boolean finished() { return cancelled || row == 3; }
        void cancel() { cancelled = true; }
    }
    private static final class Cast {
        final Player caster;
        final Location point;
        final Waves waves;
        BukkitTask task;
        Cast(Player caster, Location point) {
            this.caster = caster; this.point = point.clone(); this.waves = new Waves(Bukkit.getCurrentTick());
        }
    }
    private final PvpThunder preview = new PvpThunder();
    private final Map<UUID, Cast> pending = new HashMap<>();

    static boolean validTarget(Location point, Predicate<Location> allowed) {
        if (!allowed.test(point)) return false;
        for (double x : new double[] {-HALF_SIZE, HALF_SIZE}) for (double z : new double[] {-HALF_SIZE, HALF_SIZE})
            if (!allowed.test(point.clone().add(x, 0, z))) return false;
        return true;
    }
    static List<Location> row(Location point, int row) {
        if (row < 0 || row >= 3) throw new IllegalArgumentException("Invalid airstrike row");
        List<Location> result = new ArrayList<>();
        for (int column = 0; column < 3; column++) result.add(point.clone().add((column - 1) * 3, HEIGHT, (row - 1) * 3));
        return List.copyOf(result);
    }
    void watch(Player player, Plugin plugin, NamespacedKey abilityKey, Predicate<Player> fighting,
            Predicate<Location> allowed, LongSupplier remaining) {
        preview.watch(player, plugin, abilityKey, fighting, point -> validTarget(point, allowed), remaining,
                Material.GUNPOWDER, PvpAirstrike::mark);
    }
    boolean start(Player caster, Location point, Plugin plugin, Predicate<Player> fighting,
            Predicate<Location> allowed, PvpBombs bombs) {
        UUID id = caster.getUniqueId();
        if (pending.containsKey(id) || bombs.activeAirstrike(id) > 0 || !fighting.test(caster)
                || !caster.getWorld().equals(point.getWorld()) || !validTarget(point, allowed)) return false;
        Cast cast = new Cast(caster, point);
        pending.put(id, cast);
        mark(cast.point, null);
        cast.point.getWorld().playSound(cast.point, Sound.ENTITY_TNT_PRIMED, 1, .7f);
        cast.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!fighting.test(caster) || !caster.getWorld().equals(cast.point.getWorld())) { remove(id); return; }
            int tick = Bukkit.getCurrentTick();
            if (tick % 4 == 0) mark(cast.point, null);
            int row = cast.waves.take(tick);
            if (row >= 0) for (Location at : row(cast.point, row)) {
                if (bombs.activeAirstrike(id) >= COUNT) break;
                TNTPrimed tnt = at.getWorld().spawn(at, TNTPrimed.class, charge -> configure(charge, caster));
                if (tnt.isValid()) { bombs.trackAirstrike(caster, tnt); bombs.maintain(plugin); }
            }
            if (cast.waves.finished()) cancelCast(id);
        }, 1, 1);
        return true;
    }
    static void configure(TNTPrimed tnt, Player owner) {
        PvpBombs.configure(tnt, owner, new Vector(0, -.1, 0));
        tnt.setFuseTicks(FUSE);
        tnt.setYield(POWER);
    }
    private static void mark(Location point, Player viewer) {
        for (int side = 0; side < 4; side++) for (int step = 0; step < 8; step++) {
                double offset = -HALF_SIZE + step * (HALF_SIZE * 2 / 8);
                double x = side == 0 ? -HALF_SIZE : side == 2 ? HALF_SIZE : offset;
                double z = side == 1 ? -HALF_SIZE : side == 3 ? HALF_SIZE : offset;
                Location at = point.clone().add(x, .15, z);
                if (viewer == null) point.getWorld().spawnParticle(Particle.FLAME, at, 1, 0, 0, 0, 0, null, true);
                else viewer.spawnParticle(Particle.FLAME, at, 1, 0, 0, 0, 0, null, true);
            }
    }
    void clearPreview(UUID id) { preview.clearPreview(id); }
    private void cancelCast(UUID id) {
        Cast cast = pending.remove(id);
        if (cast != null) { cast.waves.cancel(); if (cast.task != null) cast.task.cancel(); }
    }
    void remove(UUID id) { cancelCast(id); preview.remove(id); }
    void clear() {
        for (UUID id : List.copyOf(pending.keySet())) cancelCast(id);
        preview.clear();
    }
}
