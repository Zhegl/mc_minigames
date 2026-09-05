package me.petr1furious.potatominigames.core.game;

import org.bukkit.plugin.java.JavaPlugin;

import me.petr1furious.potatominigames.core.player.ParticipantRegistry;
import me.petr1furious.potatominigames.core.player.PlayerStateService;
import me.petr1furious.potatominigames.core.score.PointsService;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.team.TeamService;
import me.petr1furious.potatominigames.core.text.Messages;
import me.petr1furious.potatominigames.core.hud.BossBarService;
import me.petr1furious.potatominigames.core.hud.SidebarService;
import me.petr1furious.potatominigames.core.world.ArenaService;
import me.petr1furious.potatominigames.core.world.SlimeWorldService;
import me.petr1furious.potatominigames.core.world.SpawnFinder;
import me.petr1furious.potatominigames.core.world.ThrowawayWorldService;

/** Everything a {@link GameSession} needs from the core, handed over on creation. */
public record SessionServices(
        JavaPlugin plugin,
        SessionManager sessions,
        ParticipantRegistry participants,
        PlayerStateService playerState,
        TeamService teams,
        SidebarService sidebar,
        BossBarService bossBars,
        ArenaService arena,
        SlimeWorldService slimeWorlds,
        ThrowawayWorldService throwawayWorlds,
        SpawnFinder spawnFinder,
        Messages messages,
        PointsService points) {
}
