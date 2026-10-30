package me.petr1furious.potatominigames.core.session;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;

/**
 * With {@code spectate.followPlayers}, spectators of a game and players knocked out of it
 * (see {@link me.petr1furious.potatominigames.core.game.GameSession#mayFollowPlayers}) watch the
 * remaining players through their eyes. Sneaking, which would let go, moves on to the next one;
 * when the watched player dies or leaves, so does the camera.
 */
public final class SpectatorCamera implements Listener {
    /** Closer than this the client already has the target loaded; further away, teleport first. */
    private static final double NEAR = 32.0;

    private final JavaPlugin plugin;
    private final SessionManager sessions;

    private final PropertyBag config = new PropertyBag("spectate");
    private final Property<Boolean> followPlayers = config.define("followPlayers", PropertyCodec.BOOL, true);

    /** Last watched player per viewer, so sneaking goes round in turn. */
    private final Map<UUID, UUID> lastTarget = new HashMap<>();
    /** Set while this class moves the camera, whose own stop events are not a sneak. */
    private boolean switching;

    public SpectatorCamera(JavaPlugin plugin, SessionManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public PropertyBag config() {
        return config;
    }

    /** Re-attaches anyone whose target is gone, and refreshes the hint. */
    private void tick() {
        if (!followPlayers.get()) {
            lastTarget.clear();
            return;
        }
        Set<UUID> watching = new HashSet<>();
        for (ActiveSession session : sessions.activeSessions()) {
            if (!session.isRunning()) {
                continue;
            }
            for (Player viewer : session.audiencePlayers()) {
                if (!isFollower(session, viewer)) {
                    continue;
                }
                watching.add(viewer.getUniqueId());
                List<Player> targets = session.followTargets(viewer);
                Entity current = viewer.getSpectatorTarget();
                if (current instanceof Player target && targets.contains(target)) {
                    hint(viewer, target, targets);
                } else if (!targets.isEmpty()) {
                    follow(viewer, next(viewer, targets), targets);
                }
            }
        }
        lastTarget.keySet().retainAll(watching);
    }

    private static boolean isFollower(ActiveSession session, Player viewer) {
        if (viewer.getGameMode() != GameMode.SPECTATOR || session.gameSession() == null) {
            return false;
        }
        return session.isSpectator(viewer)
                || session.isParticipant(viewer) && session.gameSession().mayFollowPlayers(viewer);
    }

    /** The one after the last watched, in name order. */
    private Player next(Player viewer, List<Player> targets) {
        UUID last = lastTarget.get(viewer.getUniqueId());
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).getUniqueId().equals(last)) {
                return targets.get((i + 1) % targets.size());
            }
        }
        return targets.getFirst();
    }

    private void follow(Player viewer, Player target, List<Player> targets) {
        switching = true;
        try {
            if (!viewer.getWorld().equals(target.getWorld())
                    || viewer.getLocation().distanceSquared(target.getLocation()) > NEAR * NEAR) {
                viewer.teleport(target.getLocation());
            }
            viewer.setSpectatorTarget(target);
        } finally {
            switching = false;
        }
        lastTarget.put(viewer.getUniqueId(), target.getUniqueId());
        hint(viewer, target, targets);
    }

    private static void hint(Player viewer, Player target, List<Player> targets) {
        var bar = Component.text()
                .append(Component.text("Watching ", NamedTextColor.GRAY))
                .append(Component.text(target.getName(), NamedTextColor.AQUA));
        if (targets.size() > 1) {
            bar.append(Component.text(" • sneak for the next (" + (targets.indexOf(target) + 1) + "/"
                    + targets.size() + ")", NamedTextColor.GRAY));
        }
        viewer.sendActionBar(bar.build());
    }

    /** Sneaking lets go of the target; move on to the next one instead. Also fires when the target dies. */
    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        if (switching || !followPlayers.get()) {
            return;
        }
        Player viewer = event.getPlayer();
        Optional<ActiveSession> session = sessions.sessionOf(viewer);
        if (session.isEmpty() || !session.get().isRunning() || !isFollower(session.get(), viewer)) {
            return;
        }
        // The camera cannot be moved from inside its own change.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline() || !session.get().isRunning() || !isFollower(session.get(), viewer)) {
                return;
            }
            List<Player> targets = session.get().followTargets(viewer);
            if (!targets.isEmpty()) {
                follow(viewer, next(viewer, targets), targets);
            }
        });
    }
}
