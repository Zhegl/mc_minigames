package me.petr1furious.potatominigames.games.finals;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.Ballot;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.game.StartOption;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Standalone team cashout game with a fresh arena for every match. */
public final class FinalsGame implements Minigame {
    private final FinalsConfig config;

    private final FinalsMaps maps;
    private final FinalsMapPreview previews;
    private final FinalsLoadouts loadouts;
    private final FinalsEventCommands events;
    private FinalsTutorials tutorials;
    private FinalsSession current;

    public FinalsGame(JavaPlugin plugin, ConfigRegistry configs) {
        config = new FinalsConfig(configs);
        maps = new FinalsMaps(plugin);
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onDisable(PluginDisableEvent event) {
                if (event.getPlugin() == plugin) maps.close();
            }
        }, plugin);
        previews = new FinalsMapPreview(plugin, maps);
        loadouts =
                new FinalsLoadouts(
                        plugin,
                        player -> {
                            if (current != null && current.isInGame(player))
                                current.loadoutChanged(player);
                            if (tutorials != null) tutorials.loadoutChanged(player);
                        });
        tutorials = new FinalsTutorials(plugin, config, loadouts);
        events = new FinalsEventCommands(plugin, () -> current);
    }

    @Override
    public StartOption startOption() {
        return new StartOption("map", maps::names);
    }

    @Override
    public List<Ballot> ballots(String mode) {
        return List.of(
                new Ballot(
                        "map", "map", maps::names, maps::selected, List.of(Material.FILLED_MAP)));
    }

    @Override
    public void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        tutorials.register(root);
        loadouts.register(root);
        events.register(root);
        new FinalsMapCommands(maps, previews).register(root);
    }

    public void configureTutorials(me.petr1furious.potatominigames.core.session.SessionManager sessions,
            me.petr1furious.potatominigames.core.queue.QueueService queues) {
        tutorials.configure(sessions, queues);
    }

    @Override
    public boolean beforeQueue(org.bukkit.entity.Player player, Runnable continueQueue) {
        return tutorials.suggest(player, continueQueue);
    }

    @Override
    public String id() {
        return "finals";
    }

    @Override
    public Component displayName() {
        return I18n.tr("finals.name").color(NamedTextColor.GOLD);
    }

    @Override
    public Component modeTitle(String mode) {
        return mode.equals("default") ? I18n.tr("finals.mode.default") : Component.text(mode);
    }

    @Override
    public Material icon() {
        return Material.GOLD_BLOCK;
    }

    @Override
    public Component description(String mode) {
        return I18n.tr("finals.description");
    }

    @Override
    public int minPlayers(String mode) {
        return 2;
    }

    @Override
    public int maxPlayers(String mode) {
        return 12;
    }

    @Override
    public boolean usesTeams() {
        return true;
    }

    @Override
    public List<TeamColor> teamColors(String mode) {
        return List.of(TeamColor.RED, TeamColor.BLUE);
    }

    @Override
    public GameSession createSession(SessionServices services, String mode) {
        current = new FinalsSession(services, config, maps, loadouts);
        return current;
    }
}
