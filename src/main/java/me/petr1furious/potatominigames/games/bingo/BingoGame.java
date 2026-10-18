package me.petr1furious.potatominigames.games.bingo;

import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.command.GameCommands;
import me.petr1furious.potatominigames.core.command.Suggest;
import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.games.bingo.board.BoardService;
import me.petr1furious.potatominigames.games.bingo.board.BoardUiService;
import me.petr1furious.potatominigames.games.bingo.board.DependencyCostService;
import me.petr1furious.potatominigames.games.bingo.board.ItemPoolService;
import me.petr1furious.potatominigames.games.bingo.model.GameMode;

import static me.petr1furious.potatominigames.core.command.GameCommands.run;
import static me.petr1furious.potatominigames.core.command.GameCommands.requirePlayer;

public final class BingoGame implements Minigame {
    private final ConfigRegistry configs;
    private final BingoConfig config;
    private final ItemPoolService itemPool;
    private final DependencyCostService dependencyCosts;
    private final BoardService boardService;
    private final BoardUiService boardUi;
    private final BingoKit kit;

    private BingoSession activeSession;

    public BingoGame(JavaPlugin plugin, ConfigRegistry configs) {
        this.configs = configs;
        this.config = new BingoConfig(configs);
        this.itemPool = new ItemPoolService(plugin);
        this.dependencyCosts = new DependencyCostService();
        this.boardService = new BoardService(dependencyCosts);
        this.boardUi = new BoardUiService(plugin);
        this.kit = new BingoKit(new NamespacedKey(plugin, "starter_item"));
        loadItemPool();
    }

    private void loadItemPool() {
        itemPool.load();
        dependencyCosts.setNodes(itemPool.nodes());
        dependencyCosts.refresh();
    }

    @Override
    public String id() {
        return "bingo";
    }

    @Override
    public Component displayName() {
        return Component.text("Bingo", NamedTextColor.GOLD);
    }

    @Override
    public List<String> modes() {
        return GameMode.keys();
    }

    @Override
    public String defaultMode() {
        return config.gameMode().key();
    }

    @Override
    public Material icon() {
        return Material.FILLED_MAP;
    }

    @Override
    public Component description(String mode) {
        return Component.text(switch (mode) {
            case "butfast" -> "First team to collect an item scores it";
            case "collectall" -> "Collect every item on a 3x3 card";
            default -> "Collect a line of items on your 5x5 card";
        });
    }

    @Override
    public int minPlayers(String mode) {
        return 1;
    }

    @Override
    public boolean usesTeams() {
        return true;
    }

    @Override
    public List<String> difficulties() {
        return config.difficultyKeys();
    }

    @Override
    public String defaultDifficulty() {
        return config.gameDifficulty().key();
    }

    @Override
    public GameSession createSession(SessionServices services, String mode) {
        activeSession = new BingoSession(services, config, itemPool, boardService, boardUi, kit,
                GameMode.fromKey(mode));
        return activeSession;
    }

    @Override
    public void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("mode")
                .requires(source -> source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(Suggest.strings(List.of("default", "butfast", "collectall")))
                        .executes(ctx -> run(ctx, c -> {
                            String raw = c.getArgument("mode", String.class).toLowerCase(Locale.ROOT);
                            try {
                                GameMode.fromKey(raw);
                            } catch (IllegalArgumentException exception) {
                                throw new CommandFailure("Unknown mode: " + raw);
                            }
                            configs.set("bingo", "mode", raw);
                            c.getSource().getSender().sendMessage(
                                    Component.text("Bingo mode set to " + raw, NamedTextColor.GREEN));
                        }))));

        root.then(Commands.literal("difficulty")
                .requires(source -> source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                .then(Commands.argument("difficulty", StringArgumentType.word())
                        .suggests(Suggest.dynamic(config::difficultyKeys))
                        .executes(ctx -> run(ctx, c -> {
                            String raw = c.getArgument("difficulty", String.class).toLowerCase(Locale.ROOT);
                            config.requireDifficulty(raw);
                            configs.set("bingo", "difficulty", raw);
                            c.getSource().getSender().sendMessage(
                                    Component.text("Bingo difficulty set to " + raw, NamedTextColor.GREEN));
                        }))));

        root.then(Commands.literal("returncard")
                .executes(ctx -> run(ctx, c -> {
                    Player player = requirePlayer(c);
                    if (activeSession == null) {
                        throw new CommandFailure("No bingo game is active.");
                    }
                    activeSession.returnCard(player);
                    player.sendMessage(Component.text("Bingo card returned to slot 9.", NamedTextColor.GREEN));
                })));
    }
}
