package me.petr1furious.potatominigames.core.game;

import org.bukkit.plugin.java.JavaPlugin;

import me.petr1furious.potatominigames.core.hud.BossBarService;
import me.petr1furious.potatominigames.core.hud.SidebarService;
import me.petr1furious.potatominigames.core.player.ParticipantRegistry;
import me.petr1furious.potatominigames.core.player.PlayerStateService;
import me.petr1furious.potatominigames.core.score.PointsService;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.team.TeamService;
import me.petr1furious.potatominigames.core.text.Messages;
import me.petr1furious.potatominigames.core.world.ArenaService;
import me.petr1furious.potatominigames.core.world.SessionWorlds;
import me.petr1furious.potatominigames.core.world.SpawnFinder;

/** Per-session services, except the plugin, player state, spawn finder and points, which are shared. */
public record SessionServices(
        JavaPlugin plugin,
        ActiveSession session,
        ParticipantRegistry participants,
        PlayerStateService playerState,
        TeamService teams,
        SidebarService sidebar,
        BossBarService bossBars,
        ArenaService arena,
        SessionWorlds worlds,
        SpawnFinder spawnFinder,
        Messages messages,
        PointsService points) {
    /** A per-start setting such as "difficulty", or null; fix2-queue carrier. */
    public String option(String key) {
        return session.option(key);
    }
}
