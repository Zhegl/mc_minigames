package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Telegraph a fixed ground strike; visual lightning never owns damage or changes terrain. */
final class PvpThunder {
    static final int DELAY = 20;
    static final int RANGE = 48;
    static final double RADIUS = 3;
    static final double CORE_RADIUS = 1;
    static final double DAMAGE = 8;
    static final double EDGE_DAMAGE = 3;
    record Aim(Block block, Location point) { }
    static final class Strike {
        private final Vector center;
        private final int firesAt;
        private boolean finished;
        Strike(Vector center, int tick) { this.center = center.clone(); firesAt = tick + DELAY; }
        boolean take(int tick) {
            if (finished || tick < firesAt) return false;
            finished = true;
            return true;
        }
        void cancel() { finished = true; }
        boolean contains(Vector feet) {
            double x = feet.getX() - center.getX(), z = feet.getZ() - center.getZ();
            double height = feet.getY() - center.getY();
            return x * x + z * z <= RADIUS * RADIUS && height >= -.5 && height <= 2.5;
        }
        double damage(Vector feet) {
            if (!contains(feet)) return 0;
            double x = feet.getX() - center.getX(), z = feet.getZ() - center.getZ();
            double fraction = Math.clamp((Math.hypot(x, z) - CORE_RADIUS) / (RADIUS - CORE_RADIUS), 0, 1);
            return DAMAGE + (EDGE_DAMAGE - DAMAGE) * fraction;
        }
    }
    private static final class Cast {
        final Player caster;
        final Location point;
        final Strike strike;
        final int started;
        BukkitTask task;
        Cast(Player caster, Location point, int tick) {
            this.caster = caster; this.point = point.clone(); this.started = tick;
            this.strike = new Strike(point.toVector(), tick);
        }
    }
    private final Map<UUID, Cast> pending = new HashMap<>();
    private final Set<UUID> resolving = new HashSet<>();
    private record Preview(Player player, Block block) { }
    private final Map<UUID, Preview> previews = new HashMap<>();
    private final Map<UUID, BukkitTask> watchers = new HashMap<>();

    /** Every face selects the first solid block, with a predictable strike at its top centre. */
    static Aim aim(Player caster) {
        Location eye = caster.getEyeLocation();
        var hit = caster.getWorld().rayTraceBlocks(eye, eye.getDirection(), RANGE, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) return null;
        Block block = hit.getHitBlock();
        return new Aim(block, block.getLocation().add(.5, 1.05, .5));
    }
    /** One cheap ray every four ticks, only while the tagged rod is in the main hand. */
    void watch(Player player, Plugin plugin, NamespacedKey abilityKey, Predicate<Player> fighting,
            Predicate<Location> allowed, LongSupplier remaining) {
        UUID id = player.getUniqueId();
        remove(id);
        watchers.put(id, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!fighting.test(player)) { remove(id); return; }
            var item = player.getInventory().getItemInMainHand();
            if (item.getType() != Material.LIGHTNING_ROD || !item.hasItemMeta()
                    || !item.getItemMeta().getPersistentDataContainer().has(abilityKey, PersistentDataType.BYTE)) {
                clearPreview(id); return;
            }
            Aim aim = aim(player);
            if (aim == null || !allowed.test(aim.point())) { clearPreview(id); return; }
            var highlight = (remaining.getAsLong() == 0 ? Material.GOLD_BLOCK : Material.REDSTONE_BLOCK).createBlockData();
            showPreview(player, aim, highlight);
            mark(aim.point(), player);
            player.spawnParticle(Particle.END_ROD, aim.point().clone().add(0, .2, 0), 2, .05, .12, .05, 0, null, true);
        }, 1, 4));
    }
    /** Client-only colour: restore live block data, including changes made while aiming. */
    void showPreview(Player player, Aim aim, BlockData highlight) {
        UUID id = player.getUniqueId();
        Preview previous = previews.get(id);
        if (previous != null && !previous.block().equals(aim.block())) clearPreview(id);
        previews.put(id, new Preview(player, aim.block()));
        player.sendBlockChange(aim.block().getLocation(), highlight);
    }
    void clearPreview(UUID id) {
        Preview preview = previews.remove(id);
        if (preview != null && preview.player().isOnline() && preview.player().getWorld().equals(preview.block().getWorld()))
            preview.player().sendBlockChange(preview.block().getLocation(), preview.block().getBlockData());
    }
    boolean start(Player caster, Location point, Plugin plugin, Predicate<Player> fighting,
            Supplier<List<LivingEntity>> targets) {
        UUID id = caster.getUniqueId();
        if (pending.containsKey(id)) return false;
        Cast cast = new Cast(caster, point, Bukkit.getCurrentTick());
        pending.put(id, cast);
        point.getWorld().playSound(point, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, .65f, 1.8f);
        mark(point, null);
        cast.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!fighting.test(caster) || !caster.getWorld().equals(cast.point.getWorld())) { remove(id); return; }
            int tick = Bukkit.getCurrentTick();
            if (!cast.strike.take(tick)) {
                if ((tick - cast.started) % 3 == 0) mark(cast.point, null);
                return;
            }
            cancelCast(id); // Keep the aim preview alive after firing; cancel before damage can re-enter cleanup.
            cast.point.getWorld().strikeLightningEffect(cast.point);
            resolving.add(id);
            try {
                for (LivingEntity target : targets.get()) {
                    if (!fighting.test(caster)) break;
                    if (target.equals(caster) || !target.isValid() || target.isDead()
                            || !target.getWorld().equals(cast.point.getWorld())
                            || !cast.strike.contains(target.getLocation().toVector()) || !exposed(cast.point, target)) continue;
                    // Native armour, shields, invulnerability frames and kill attribution still apply.
                    target.damage(cast.strike.damage(target.getLocation().toVector()), caster);
                }
            } finally { resolving.remove(id); }
        }, 1, 1);
        return true;
    }
    boolean resolving(Player caster) { return resolving.contains(caster.getUniqueId()); }
    void remove(UUID caster) {
        cancelCast(caster);
        BukkitTask watcher = watchers.remove(caster);
        if (watcher != null) watcher.cancel();
        clearPreview(caster);
    }
    private void cancelCast(UUID caster) {
        Cast cast = pending.remove(caster);
        if (cast == null) return;
        cast.strike.cancel();
        if (cast.task != null) cast.task.cancel();
    }
    void clear() {
        Set<UUID> owned = new HashSet<>(pending.keySet());
        owned.addAll(watchers.keySet()); owned.addAll(previews.keySet());
        for (UUID caster : owned) remove(caster);
    }
    static boolean exposed(Location point, LivingEntity target) {
        Location from = point.clone().add(0, .2, 0);
        Vector direction = target.getLocation().add(0, Math.min(.8, target.getHeight() / 2), 0)
                .toVector().subtract(from.toVector());
        double distance = direction.length();
        return distance < 1.0e-6 || point.getWorld().rayTraceBlocks(from, direction.normalize(), distance,
                FluidCollisionMode.NEVER, true) == null;
    }
    private static void mark(Location point, Player viewer) {
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI / 10;
            double x = point.getX() + Math.cos(angle) * RADIUS, y = point.getY() + .12;
            double z = point.getZ() + Math.sin(angle) * RADIUS;
            if (viewer == null) point.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, 1, 0, 0, 0, 0, null, true);
            else viewer.spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, 1, 0, 0, 0, 0, null, true);
        }
    }
}
