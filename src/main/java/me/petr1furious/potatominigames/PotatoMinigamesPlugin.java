package me.petr1furious.potatominigames;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import me.petr1furious.potatominigames.core.command.GameCommands;
import me.petr1furious.potatominigames.core.command.PlayCommand;
import me.petr1furious.potatominigames.core.command.PmCommand;
import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.lobby.LobbyService;
import me.petr1furious.potatominigames.core.player.PlayerStateService;
import me.petr1furious.potatominigames.core.queue.QueueMenu;
import me.petr1furious.potatominigames.core.queue.QueueService;
import me.petr1furious.potatominigames.core.score.PointsService;
import me.petr1furious.potatominigames.core.session.GameplayListener;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.session.SpectatorCamera;
import me.petr1furious.potatominigames.core.team.TeamRegistry;
import me.petr1furious.potatominigames.core.team.TeamService;
import me.petr1furious.potatominigames.core.text.Messages;
import me.petr1furious.potatominigames.core.world.DimensionLinkListener;
import me.petr1furious.potatominigames.core.world.SlimeWorldService;
import me.petr1furious.potatominigames.core.world.SpawnFinder;
import me.petr1furious.potatominigames.core.world.ThrowawayWorldService;
import me.petr1furious.potatominigames.gadgets.FPVDroneGadget;
import me.petr1furious.potatominigames.gadgets.GadgetRegistry;
import me.petr1furious.potatominigames.gadgets.GrappleBowGadget;
import me.petr1furious.potatominigames.gadgets.HimarsGadget;
import me.petr1furious.potatominigames.gadgets.InfiniteCrossbowGadget;
import me.petr1furious.potatominigames.gadgets.LocatorGadget;
import me.petr1furious.potatominigames.gadgets.OreshnikGadget;
import me.petr1furious.potatominigames.gadgets.RadarGadget;
import me.petr1furious.potatominigames.gadgets.SessionGadgetContext;
import me.petr1furious.potatominigames.gadgets.TrackerCompassGadget;
import me.petr1furious.potatominigames.games.bingo.BingoGame;
import me.petr1furious.potatominigames.games.chase.ChaseGame;
import me.petr1furious.potatominigames.games.party.PartyGame;
import me.petr1furious.potatominigames.games.dummy.DummyGame;
import me.petr1furious.potatominigames.games.pillars.PillarsGame;
import me.petr1furious.potatominigames.games.warfare.WarfareGame;

public final class PotatoMinigamesPlugin extends JavaPlugin {
    private SessionManager sessions;

    @Override
    public void onEnable() {
        Messages messages = new Messages();
        ConfigRegistry configs = new ConfigRegistry(this);
        PlayerStateService playerState = new PlayerStateService(this);
        TeamRegistry teamRegistry = new TeamRegistry(this);
        TeamService teams = new TeamService(teamRegistry);
        SlimeWorldService slimeWorlds = new SlimeWorldService(this);
        ThrowawayWorldService throwawayWorlds = new ThrowawayWorldService(this);
        SpawnFinder spawnFinder = new SpawnFinder();
        PointsService points = new PointsService(this);

        // Clear worlds left by a crash.
        throwawayWorlds.cleanupStale();

        sessions = new SessionManager(this, playerState, teamRegistry, slimeWorlds, throwawayWorlds, spawnFinder,
                points);
        configs.register(sessions.coreConfig(), "config.yml", "");
        configs.register(slimeWorlds.config(), "config.yml", "asp");

        LobbyService lobby = new LobbyService(this, playerState, sessions);
        configs.register(lobby.config(), "config.yml", "lobby");
        sessions.setLobby(lobby);

        if (sessions.isDummyGameEnabled()) {
            sessions.registerGame(new DummyGame());
        }

        var gadgetContext = new SessionGadgetContext(this, sessions);
        gadgetContext.setLobby(lobby);
        var gadgets = new GadgetRegistry();
        var fpvDrone = new FPVDroneGadget(gadgetContext);
        gadgets.register(new InfiniteCrossbowGadget(gadgetContext));
        gadgets.register(new OreshnikGadget(gadgetContext));
        gadgets.register(new HimarsGadget(gadgetContext));
        gadgets.register(new LocatorGadget(gadgetContext));
        gadgets.register(fpvDrone);
        gadgets.register(new RadarGadget(gadgetContext));
        gadgets.register(new GrappleBowGadget(gadgetContext));
        gadgets.register(new TrackerCompassGadget(gadgetContext));
        for (var gadget : gadgets.all()) {
            configs.register(gadget.config(), "gadgets.yml", gadget.id());
        }
        gadgets.registerEvents(this);
        sessions.addCleanupHook(gadgets::endForPlayers);

        // Registration order is the menu order.
        PillarsGame pillars = new PillarsGame(this, configs, slimeWorlds);
        sessions.registerGame(pillars);
        sessions.registerGame(new BingoGame(this, configs));
        sessions.registerGame(new WarfareGame(configs, fpvDrone));
        sessions.registerGame(new ChaseGame(configs, gadgets));
        sessions.registerGame(new PartyGame(configs));

        QueueService queues = new QueueService(this, sessions, lobby);
        queues.createQueues(configs);
        pillars.setQueues(queues);
        QueueMenu menu = new QueueMenu(this, sessions, queues);
        lobby.setMenuOpener(menu::open);
        SpectatorCamera camera = new SpectatorCamera(this, sessions);
        configs.register(camera.config(), "config.yml", "spectate");

        GameCommands gameCommands = new GameCommands(sessions, teams, points, configs, messages, gadgets);
        PmCommand pmCommand = new PmCommand(this, sessions, configs, messages, gameCommands, queues);
        PlayCommand playCommand = new PlayCommand(sessions, queues, lobby, menu);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(pmCommand.build(), "Potato Minigames admin command.");
            event.registrar().register(playCommand.buildPlay(), "Join a game's queue.");
            event.registrar().register(playCommand.buildLobby(), "Go to the lobby.");
            for (Minigame game : sessions.games()) {
                event.registrar().register(gameCommands.buildGameRoot(game),
                        "Potato Minigames: " + game.id());
            }
        });

        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new GameplayListener(sessions), this);
        pluginManager.registerEvents(lobby, this);
        pluginManager.registerEvents(queues, this);
        pluginManager.registerEvents(menu, this);
        pluginManager.registerEvents(camera, this);
        pluginManager.registerEvents(new DimensionLinkListener(this, throwawayWorlds), this);

        // Snapshots can point into worlds other plugins load.
        getServer().getScheduler().runTask(this, playerState::loadSaved);
    }

    @Override
    public void onDisable() {
        if (sessions != null) {
            sessions.stopAll(EndReason.ABORTED);
        }
    }
}
