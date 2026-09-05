package me.petr1furious.potatominigames.games.warfare;

import java.util.List;
import java.util.Locale;

import org.bukkit.entity.Player;

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
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.gadgets.FPVDroneGadget;

import static me.petr1furious.potatominigames.core.command.GameCommands.run;
import static me.petr1furious.potatominigames.core.command.GameCommands.requirePlayer;

public final class WarfareGame implements Minigame {
    private final WarfareConfig config;
    private final ConfigRegistry configs;
    private final FPVDroneGadget fpvDrone;

    public WarfareGame(ConfigRegistry configs, FPVDroneGadget fpvDrone) {
        this.configs = configs;
        this.config = new WarfareConfig(configs);
        this.fpvDrone = fpvDrone;
    }

    @Override
    public String id() {
        return "war";
    }

    @Override
    public Component displayName() {
        return Component.text("Warfare", NamedTextColor.DARK_RED);
    }

    @Override
    public boolean usesTeams() {
        return true;
    }

    @Override
    public boolean usesGadgets() {
        return true;
    }

    @Override
    public GameSession createSession(SessionServices services) {
        if (config.mode.get().equalsIgnoreCase("team_beacon")) {
            return new TeamBeaconWarfareSession(services, config, fpvDrone);
        }
        return new ClassicWarfareSession(services, config, fpvDrone);
    }

    @Override
    public void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("mode")
                .requires(source -> source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(Suggest.strings(List.of("classic", "team_beacon")))
                        .executes(ctx -> run(ctx, c -> {
                            String mode = c.getArgument("mode", String.class).toLowerCase(Locale.ROOT);
                            if (!mode.equals("classic") && !mode.equals("team_beacon")) {
                                throw new CommandFailure("Unknown mode: " + mode);
                            }
                            configs.set("war", "mode", mode);
                            c.getSource().getSender().sendMessage(
                                    Component.text("Warfare mode set to " + mode, NamedTextColor.GREEN));
                        }))));

        root.then(Commands.literal("setcenter")
                .requires(source -> source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                .executes(ctx -> run(ctx, c -> {
                    Player player = requirePlayer(c);
                    configs.set("war", "gameCenter", player.getLocation().getX() + " "
                            + player.getLocation().getY() + " " + player.getLocation().getZ());
                    configs.set("war", "gameWorld", player.getWorld().getName());
                    c.getSource().getSender().sendMessage(Component.text(
                            "Game center set to your position in " + player.getWorld().getName(),
                            NamedTextColor.GREEN));
                })));

        root.then(Commands.literal("beacon")
                .requires(source -> source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                .then(Commands.literal("set")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests(Suggest.strings(
                                        java.util.Arrays.stream(TeamColor.values()).map(TeamColor::id).toList()))
                                .executes(ctx -> run(ctx, c -> {
                                    Player player = requirePlayer(c);
                                    TeamColor team = parseTeam(c.getArgument("team", String.class));
                                    var block = player.getTargetBlockExact(6);
                                    var location = block != null ? block.getLocation()
                                            : player.getLocation().getBlock().getLocation();
                                    config.setBeacon(team, location);
                                    c.getSource().getSender().sendMessage(Component.text()
                                            .append(team.displayNameComponent())
                                            .append(Component.text(" beacon set at " + location.getBlockX() + " "
                                                    + location.getBlockY() + " " + location.getBlockZ(),
                                                    NamedTextColor.GREEN))
                                            .build());
                                }))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests(Suggest.dynamic(
                                        () -> config.teamsWithBeacons().stream().map(TeamColor::id).toList()))
                                .executes(ctx -> run(ctx, c -> {
                                    TeamColor team = parseTeam(c.getArgument("team", String.class));
                                    config.setBeacon(team, null);
                                    c.getSource().getSender().sendMessage(Component.text(
                                            team.displayName() + " beacon cleared", NamedTextColor.GREEN));
                                }))))
                .then(Commands.literal("info")
                        .executes(ctx -> run(ctx, c -> {
                            var teams = config.teamsWithBeacons();
                            if (teams.isEmpty()) {
                                c.getSource().getSender().sendMessage(
                                        Component.text("No beacons configured.", NamedTextColor.GRAY));
                                return;
                            }
                            for (TeamColor team : teams) {
                                var location = config.getBeacon(team);
                                c.getSource().getSender().sendMessage(Component.text()
                                        .append(team.displayNameComponent())
                                        .append(Component.text(": " + location.getWorld().getName() + " "
                                                + location.getBlockX() + " " + location.getBlockY() + " "
                                                + location.getBlockZ(), NamedTextColor.GRAY))
                                        .build());
                            }
                        }))));

        root.then(Commands.literal("kit")
                .requires(source -> source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                .then(Commands.literal("save")
                        .executes(ctx -> run(ctx, c -> {
                            Player player = requirePlayer(c);
                            config.saveGameInventory(player.getInventory().getContents());
                            c.getSource().getSender().sendMessage(
                                    Component.text("Warfare kit saved from your inventory.", NamedTextColor.GREEN));
                        })))
                .then(Commands.literal("load")
                        .executes(ctx -> run(ctx, c -> {
                            Player player = requirePlayer(c);
                            var kit = config.gameInventory();
                            if (kit == null) {
                                throw new CommandFailure("No warfare kit saved.");
                            }
                            player.getInventory().setContents(kit.clone());
                            c.getSource().getSender().sendMessage(
                                    Component.text("Warfare kit loaded.", NamedTextColor.GREEN));
                        }))));
    }

    private static TeamColor parseTeam(String value) throws CommandFailure {
        TeamColor team = TeamColor.fromId(value);
        if (team == null) {
            throw new CommandFailure("Unknown team color: " + value);
        }
        return team;
    }
}
