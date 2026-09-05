package me.petr1furious.potatominigames;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import me.petr1furious.potatominigames.core.command.GameCommands;
import me.petr1furious.potatominigames.core.command.PmCommand;
import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.hud.BossBarService;
import me.petr1furious.potatominigames.core.hud.SidebarService;
import me.petr1furious.potatominigames.core.player.PlayerStateService;
import me.petr1furious.potatominigames.core.score.PointsService;
import me.petr1furious.potatominigames.core.session.GameplayListener;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.team.TeamRegistry;
import me.petr1furious.potatominigames.core.team.TeamService;
import me.petr1furious.potatominigames.core.text.Messages;
import me.petr1furious.potatominigames.core.world.ArenaService;
import me.petr1furious.potatominigames.core.world.SlimeWorldService;
import me.petr1furious.potatominigames.core.world.SpawnFinder;
import me.petr1furious.potatominigames.core.world.ThrowawayWorldService;
import me.petr1furious.potatominigames.games.dummy.DummyGame;

public final class PotatoMinigamesPlugin extends JavaPlugin {
    private SessionManager sessions;

    @Override
    public void onEnable() {
        Messages messages = new Messages();
        ConfigRegistry configs = new ConfigRegistry(this);
        PlayerStateService playerState = new PlayerStateService();
        TeamRegistry teamRegistry = new TeamRegistry(this);
        TeamService teams = new TeamService(teamRegistry);
        SidebarService sidebar = new SidebarService();
        BossBarService bossBars = new BossBarService();
        ArenaService arena = new ArenaService();
        SlimeWorldService slimeWorlds = new SlimeWorldService(this);
        ThrowawayWorldService throwawayWorlds = new ThrowawayWorldService(this);
        SpawnFinder spawnFinder = new SpawnFinder();
        PointsService points = new PointsService(this);

        // A crash can leave scratch worlds behind; clear them before anything runs.
        throwawayWorlds.cleanupStale();

        sessions = new SessionManager(this, playerState, teams, sidebar, bossBars, arena, slimeWorlds,
                throwawayWorlds, spawnFinder, messages, points);
        configs.register(sessions.coreConfig(), "config.yml", "");
        configs.register(slimeWorlds.config(), "config.yml", "asp");

        if (sessions.isDummyGameEnabled()) {
            sessions.registerGame(new DummyGame());
        }

        var gadgetContext = new me.petr1furious.potatominigames.gadgets.SessionGadgetContext(this, sessions);
        var gadgets = new me.petr1furious.potatominigames.gadgets.GadgetRegistry();
        var fpvDrone = new me.petr1furious.potatominigames.gadgets.FPVDroneGadget(gadgetContext);
        gadgets.register(new me.petr1furious.potatominigames.gadgets.InfiniteCrossbowGadget(gadgetContext));
        gadgets.register(new me.petr1furious.potatominigames.gadgets.OreshnikGadget(gadgetContext));
        gadgets.register(new me.petr1furious.potatominigames.gadgets.HimarsGadget(gadgetContext));
        gadgets.register(new me.petr1furious.potatominigames.gadgets.LocatorGadget(gadgetContext));
        gadgets.register(fpvDrone);
        gadgets.register(new me.petr1furious.potatominigames.gadgets.RadarGadget(gadgetContext));
        gadgets.register(new me.petr1furious.potatominigames.gadgets.GrappleBowGadget(gadgetContext));
        gadgets.register(new me.petr1furious.potatominigames.gadgets.TrackerCompassGadget(gadgetContext));
        for (var gadget : gadgets.all()) {
            configs.register(gadget.config(), "gadgets.yml", gadget.id());
        }
        gadgets.registerEvents(this);
        sessions.addCleanupHook(gadgets::endAllForGame);

        sessions.registerGame(new me.petr1furious.potatominigames.games.warfare.WarfareGame(configs, fpvDrone));
        sessions.registerGame(new me.petr1furious.potatominigames.games.bingo.BingoGame(this, configs));
        sessions.registerGame(new me.petr1furious.potatominigames.games.chase.ChaseGame(configs, gadgets));

        GameCommands gameCommands = new GameCommands(sessions, teams, points, configs, messages, gadgets);
        PmCommand pmCommand = new PmCommand(this, sessions, configs, messages, gameCommands);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(pmCommand.build(), "Potato Minigames admin command.");
            for (Minigame game : sessions.games()) {
                event.registrar().register(gameCommands.buildGameRoot(game),
                        "Potato Minigames: " + game.id());
            }
        });

        getServer().getPluginManager().registerEvents(new GameplayListener(sessions), this);
        getServer().getPluginManager().registerEvents(
                new me.petr1furious.potatominigames.core.world.DimensionLinkListener(this, throwawayWorlds), this);
    }

    @Override
    public void onDisable() {
        if (sessions != null) {
            sessions.stopNow(EndReason.ABORTED);
        }
    }
}
