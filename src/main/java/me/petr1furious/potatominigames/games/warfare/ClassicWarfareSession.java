package me.petr1furious.potatominigames.games.warfare;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.gadgets.FPVDroneGadget;

/**
 * Classic Warfare: everyone spawns invisible at a random point in the arena
 * disc, hunts the others using the bucketed-distance sidebar and gadgets;
 * last player standing wins.
 */
final class ClassicWarfareSession extends WarfareSessionBase {
    private boolean checkingGameEnd = false;
    private boolean ended = false;

    ClassicWarfareSession(SessionServices services, WarfareConfig config, FPVDroneGadget fpvDrone) {
        super(services, config, fpvDrone);
    }

    @Override
    public void onStart() throws CommandFailure {
        setupArena();
        for (Player player : services.participants().onlinePlayers()) {
            spawnPlayer(player);
        }
        startHud();
    }

    private void spawnPlayer(Player player) {
        Location spawn = services.spawnFinder().findSpawnAround(currentGameCenter(), currentGameRadius());
        if (spawn == null) {
            spawn = gameWorld().getSpawnLocation();
        }
        prepareAlivePlayer(player, spawn, true);
    }

    @Override
    public boolean allowLateJoin() {
        return true;
    }

    @Override
    public void onParticipantJoin(Player player) {
        spawnPlayer(player);
        updateHud();
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        // A death anywhere else (the lobby, say) is not this game's business.
        if (!isPlayerAlive(player)) {
            return;
        }
        event.setCancelled(true);
        Component death = event.deathMessage();
        if (death != null) {
            services.messages().broadcast(death.color(NamedTextColor.GRAY));
        }
        services.playerState().makeSpectator(player, 0);
        scheduleGameEndCheck();
    }

    @Override
    public void onParticipantQuit(Player player) {
        scheduleGameEndCheck();
        updateHud();
    }

    @Override
    public void onParticipantLeave(Player player) {
        scheduleGameEndCheck();
        updateHud();
    }

    @Override
    public void onParticipantRejoin(Player player) {
        updateHud();
    }

    private void scheduleGameEndCheck() {
        if (checkingGameEnd) {
            return;
        }
        checkingGameEnd = true;
        services.plugin().getServer().getScheduler().runTaskLater(services.plugin(), () -> {
            checkingGameEnd = false;
            checkGameEnd();
        }, 60L);
    }

    private void checkGameEnd() {
        if (ended || !services.sessions().isRunning()) {
            return;
        }
        List<Player> alive = alivePlayers();
        if (alive.size() > 1) {
            return;
        }
        ended = true;
        if (alive.isEmpty()) {
            services.messages().broadcast(Component.text("Game over! No players left!", NamedTextColor.RED));
        } else {
            Player winner = alive.getFirst();
            services.messages().broadcast(Component.text("Game over! ", NamedTextColor.RED)
                    .append(Component.text(winner.getName(), NamedTextColor.BLUE))
                    .append(Component.text(" wins!", NamedTextColor.RED)));
            services.points().add("war", winner.getUniqueId(), 1);
        }
        services.sessions().endGame(EndReason.FINISHED);
    }

    @Override
    public Collection<Player> getTrackedPlayers(Player viewer) {
        List<Player> result = new ArrayList<>();
        for (Player other : alivePlayers()) {
            if (other != viewer) {
                result.add(other);
            }
        }
        return result;
    }

    @Override
    protected List<Component> computeHudLines(Player viewer) {
        return buildDistanceLines(viewer);
    }

    @Override
    protected Component hudTitle(Player viewer) {
        return Component.text("Distances", NamedTextColor.GOLD);
    }
}
