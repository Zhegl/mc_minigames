package me.petr1furious.potatominigames.games.warfare;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.command.GameCommands;
import me.petr1furious.potatominigames.core.command.Suggest;
import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.Ballot;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.game.StartOption;
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

    /**
     * Team beacon mode only has the colours whose beacon is set up; with maps, on every map the
     * mode can play, since the map is only known at the start.
     */
    @Override
    public List<TeamColor> teamColors(String mode) {
        if (!mode.equalsIgnoreCase("team_beacon")) {
            return List.of(TeamColor.values());
        }
        if (config.maps().isEmpty()) {
            return config.teamsWithConfiguredBeacons();
        }
        List<WarfareMap> usable = config.mapsFor(mode);
        if (usable.isEmpty()) {
            return List.of();
        }
        List<TeamColor> colours = new ArrayList<>(usable.getFirst().beaconTeams());
        for (WarfareMap map : usable) {
            colours.retainAll(map.beaconTeams());
        }
        return colours;
    }

    /** The map, among those the mode can play; random on a tie. */
    @Override
    public List<Ballot> ballots(String mode) {
        return List.of(new Ballot(WarfareSessionBase.MAP_OPTION, "map",
                () -> config.mapsFor(mode).stream().map(WarfareMap::name).toList(), null,
                List.of(Material.FILLED_MAP)));
    }

    /** {@code /war start [mode] [players] [map]}. */
    @Override
    public StartOption startOption() {
        return new StartOption(WarfareSessionBase.MAP_OPTION,
                () -> config.maps().stream().map(WarfareMap::name).toList());
    }

    @Override
    public boolean usesGadgets() {
        return true;
    }

    @Override
    public List<String> modes() {
        return List.of("classic", "team_beacon");
    }

    @Override
    public String defaultMode() {
        return config.mode.get().equalsIgnoreCase("team_beacon") ? "team_beacon" : "classic";
    }

    @Override
    public Material icon() {
        return Material.TNT;
    }

    @Override
    public Component description(String mode) {
        return Component.text(mode.equals("team_beacon")
                ? "Defend your team's beacon, respawn until it falls"
                : "Invisible free-for-all with gadgets");
    }

    @Override
    public GameSession createSession(SessionServices services, String mode) {
        if (mode.equalsIgnoreCase("team_beacon")) {
            return new TeamBeaconWarfareSession(services, config, fpvDrone);
        }
        return new ClassicWarfareSession(services, config, fpvDrone);
    }

    @Override
    public void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
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

        root.then(Commands.literal("map")
                .requires(source -> source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                .then(Commands.literal("list")
                        .executes(ctx -> run(ctx, this::listMaps)))
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> run(ctx, c -> addMap(c, null)))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10000))
                                        .executes(ctx -> run(ctx, c -> addMap(c,
                                                c.getArgument("radius", Integer.class)))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(Suggest.dynamic(this::mapNames))
                                .executes(ctx -> run(ctx, c -> {
                                    WarfareMap map = requireMap(c.getArgument("name", String.class));
                                    config.removeMap(map.name());
                                    c.getSource().getSender().sendMessage(Component.text(
                                            "Map " + map.name() + " removed", NamedTextColor.GREEN));
                                })))));

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
                                    WarfareMap map = requireMapAt(player);
                                    if (map != null) {
                                        config.setMapBeacon(map, team, location);
                                    } else {
                                        config.setBeacon(team, location);
                                    }
                                    c.getSource().getSender().sendMessage(Component.text()
                                            .append(team.displayNameComponent())
                                            .append(Component.text(" beacon set at " + location.getBlockX() + " "
                                                    + location.getBlockY() + " " + location.getBlockZ()
                                                    + (map != null ? " on " + map.name() : ""),
                                                    NamedTextColor.GREEN))
                                            .build());
                                }))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests(Suggest.strings(
                                        java.util.Arrays.stream(TeamColor.values()).map(TeamColor::id).toList()))
                                .executes(ctx -> run(ctx, c -> {
                                    TeamColor team = parseTeam(c.getArgument("team", String.class));
                                    WarfareMap map = requireMapAt(requirePlayer(c));
                                    if (map != null) {
                                        config.setMapBeacon(map, team, null);
                                    } else {
                                        config.setBeacon(team, null);
                                    }
                                    c.getSource().getSender().sendMessage(Component.text(
                                            team.displayName() + " beacon cleared"
                                                    + (map != null ? " on " + map.name() : ""),
                                            NamedTextColor.GREEN));
                                }))))
                .then(Commands.literal("info")
                        .executes(ctx -> run(ctx, c -> {
                            if (!config.maps().isEmpty()) {
                                listMaps(c);
                                return;
                            }
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

    /**
     * The map whose template (opened with {@code /swp goto <template>}) or game copy the player
     * stands in; null outside every map, where beacons and the center are the map-less settings.
     */
    private WarfareMap mapAt(Player player) {
        return config.maps().stream().filter(map -> map.isWorldOf(player.getWorld())).findFirst().orElse(null);
    }

    /** With maps, beacons belong to one: the player must stand in it. Null without maps. */
    private WarfareMap requireMapAt(Player player) throws CommandFailure {
        if (config.maps().isEmpty()) {
            return null;
        }
        WarfareMap map = mapAt(player);
        if (map == null) {
            throw new CommandFailure("Stand in a map to set its beacons: /swp goto <template> (maps: "
                    + String.join(", ", mapNames()) + ").");
        }
        return map;
    }

    private List<String> mapNames() {
        return config.maps().stream().map(WarfareMap::name).toList();
    }

    private WarfareMap requireMap(String name) throws CommandFailure {
        WarfareMap map = config.map(name);
        if (map == null) {
            throw new CommandFailure("Unknown map: " + name + " (maps: " + String.join(", ", mapNames()) + ")");
        }
        return map;
    }

    /** Centered on the player, in the slime template they stand in; keeps beacons when it replaces a map. */
    private void addMap(CommandContext<CommandSourceStack> ctx, Integer radius) throws CommandFailure {
        Player player = requirePlayer(ctx);
        String name = ctx.getArgument("name", String.class);
        WarfareMap existing = config.map(name);
        String template = existing != null && existing.isWorldOf(player.getWorld()) ? existing.template()
                : player.getWorld().getName();
        if (player.getWorld().equals(org.bukkit.Bukkit.getWorlds().getFirst())) {
            throw new CommandFailure("Stand in the map's slime template first: /swp goto <template>.");
        }
        Location location = player.getLocation();
        int chosenRadius = radius != null ? radius : existing != null ? existing.radius() : config.gameRadius.get();
        config.saveMap(existing != null ? existing.name() : name, template,
                new Vector(location.getBlockX(), location.getBlockY(), location.getBlockZ()), chosenRadius);
        ctx.getSource().getSender().sendMessage(Component.text("Map " + name + " (template " + template
                + ") centered on you, radius " + chosenRadius, NamedTextColor.GREEN));
    }

    private void listMaps(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        List<WarfareMap> maps = config.maps();
        if (maps.isEmpty()) {
            sender.sendMessage(Component.text("No maps; the game plays in " + config.gameWorld.get()
                    + ". Add one with /war map add <name> standing in its slime template.", NamedTextColor.GRAY));
            return;
        }
        for (WarfareMap map : maps) {
            Vector center = map.center();
            var line = Component.text()
                    .append(Component.text(map.name(), NamedTextColor.WHITE))
                    .append(Component.text(" — template " + map.template() + ", center " + center.getBlockX() + " "
                            + center.getBlockY() + " " + center.getBlockZ() + ", radius " + map.radius()
                            + ", beacons: ", NamedTextColor.GRAY));
            List<TeamColor> teams = map.beaconTeams();
            if (teams.isEmpty()) {
                line.append(Component.text("none", NamedTextColor.DARK_GRAY));
            }
            for (int i = 0; i < teams.size(); i++) {
                line.append(teams.get(i).displayNameComponent());
                if (i + 1 < teams.size()) {
                    line.append(Component.text(", ", NamedTextColor.GRAY));
                }
            }
            sender.sendMessage(line.build());
        }
    }

    private static TeamColor parseTeam(String value) throws CommandFailure {
        TeamColor team = TeamColor.fromId(value);
        if (team == null) {
            throw new CommandFailure("Unknown team color: " + value);
        }
        return team;
    }
}
