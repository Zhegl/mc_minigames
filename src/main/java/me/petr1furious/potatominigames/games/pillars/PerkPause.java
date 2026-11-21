package me.petr1furious.potatominigames.games.pillars;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.Vector;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

/** Pauses a disposable arena without changing the server's tick rate. */
final class PerkPause {
    private record Motion(Vector velocity, boolean gravity, boolean ai, float fallDistance, int fuse) {
        Motion(Entity entity) {
            this(entity.getVelocity(), entity.hasGravity(), entity instanceof Mob mob && mob.hasAI(),
                    entity.getFallDistance(), entity instanceof TNTPrimed tnt ? tnt.getFuseTicks() : 0);
        }

        void hold(Entity entity) {
            if (entity.hasGravity()) entity.setGravity(false);
            if (entity instanceof Mob mob && mob.hasAI()) mob.setAI(false);
            if (entity instanceof TNTPrimed tnt) tnt.setFuseTicks(Math.max(2, fuse + 1));
            if (entity.getVelocity().lengthSquared() != 0) entity.setVelocity(new Vector());
        }

        void restore(Entity entity, World arena) {
            if (!entity.isValid()) return;
            entity.setGravity(gravity);
            if (entity instanceof Mob mob) mob.setAI(ai);
            if (entity instanceof TNTPrimed tnt) tnt.setFuseTicks(fuse);
            if (entity.getWorld().equals(arena)) {
                entity.setVelocity(velocity);
                entity.setFallDistance(fallDistance);
            }
        }
    }

    private final World world;
    private final Map<Entity, Motion> frozen = new HashMap<>();
    private int remaining;

    PerkPause(World world) { this.world = world; }

    boolean active() { return remaining > 0; }

    void start(List<Player> audience) {
        remaining = PerkRules.PAUSE_TICKS;
        var title = Title.title(I18n.tr("pillars.effects.pause.title").color(NamedTextColor.GOLD), Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(50L * remaining), Duration.ZERO));
        for (Player player : audience) {
            player.closeInventory();
            player.clearActiveItem();
            player.showTitle(title);
        }
        hold();
    }

    void tick() {
        if (--remaining == 0) release();
        else hold();
    }

    private void hold() {
        for (Entity entity : world.getEntities()) {
            if (entity.isValid()) frozen.computeIfAbsent(entity, Motion::new).hold(entity);
        }
    }

    void release(Entity entity) {
        Motion motion = frozen.remove(entity);
        if (motion != null) motion.restore(entity, world);
    }

    void release() {
        remaining = 0;
        frozen.forEach((entity, motion) -> motion.restore(entity, world));
        frozen.clear();
    }
}
