package me.petr1furious.potatominigames.games.finals;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.command.GameCommands;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Admin-only custom map import, setup, snapshot and selection commands. */
public final class FinalsMapCommands {
    private final FinalsMaps maps;
    private final FinalsMapPreview previews;

    public FinalsMapCommands(FinalsMaps maps) {
        this(maps, null);
    }

    public FinalsMapCommands(FinalsMaps maps, FinalsMapPreview previews) {
        this.maps = maps;
        this.previews = previews;
    }

    public void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> map =
                Commands.literal("map")
                        .requires(source ->
                                source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                        .executes(ctx -> run(ctx, c -> { send(c, "help"); send(c, "pad_help"); }));
        map.then(
                Commands.literal("list")
                        .executes(ctx ->
                                run(
                                        ctx,
                                        c ->
                                                send(
                                                        c,
                                                        "list",
                                                        String.join(", ", maps.names()),
                                                        String.join(", ", maps.drafts()),
                                                        maps.selected()))));
        map.then(
                Commands.literal("create")
                        .then(
                                Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx ->
                                                run(ctx, this::createHere))
                                        .then(
                                                mapArgument("sourceMap")
                                                        .executes(ctx ->
                                                                run(ctx, this::createShared)))));
        map.then(
                Commands.literal("import")
                        .then(
                                Commands.argument("name", StringArgumentType.word())
                                        .then(
                                                Commands.argument(
                                                                "source",
                                                                StringArgumentType.greedyString())
                                                        .executes(ctx ->
                                                                run(
                                                                        ctx,
                                                                        this::importMap)))));
        map.then(
                Commands.literal("goto")
                        .then(
                                nameArgument()
                                        .executes(ctx ->
                                                run(
                                                        ctx,
                                                        c -> {
                                                            Player player =
                                                                    GameCommands.requirePlayer(c);
                                                            if (!player.teleport(
                                                                    maps.editorLocation(name(c))))
                                                                throw FinalsMaps.failure(
                                                                        "teleport");
                                                            send(c, "goto", name(c));
                                                            showPreview(c);
                                                        }))));
        if (previews != null) {
            RequiredArgumentBuilder<CommandSourceStack, String> preview = nameArgument();
            preview.executes(ctx -> run(ctx, this::showPreview));
            preview.then(Commands.literal("on").executes(ctx -> run(ctx, this::showPreview)));
            preview.then(
                    Commands.literal("off")
                            .executes(ctx ->
                                    run(
                                            ctx,
                                            c -> {
                                                maps.previewDraft(name(c));
                                                previews.hide(GameCommands.requirePlayer(c));
                                                send(c, "preview.disabled");
                                            })));
            map.then(Commands.literal("preview").then(preview));
        }
        for (int corner = 0; corner < 2; corner++) {
            int index = corner;
            map.then(
                    Commands.literal("pos" + (corner + 1))
                            .then(
                                    nameArgument()
                                            .executes(ctx ->
                                                    run(
                                                            ctx,
                                                            c -> {
                                                                Player player =
                                                                        GameCommands.requirePlayer(
                                                                                c);
                                                                var target =
                                                                        player.getTargetBlockExact(
                                                                                128);
                                                                Location point =
                                                                        target == null
                                                                                ? player
                                                                                        .getLocation()
                                                                                : target
                                                                                        .getLocation();
                                                                maps.corner(name(c), index, point);
                                                                send(
                                                                        c,
                                                                        "corner",
                                                                        name(c),
                                                                        index + 1,
                                                                        point.getBlockX(),
                                                                        point.getBlockZ());
                                                            }))));
        }
        for (String operation : new String[] {"spawn", "spawnclear"}) {
            RequiredArgumentBuilder<CommandSourceStack, String> argument = nameArgument();
            for (int team = 0; team < 2; team++) {
                int index = team;
                argument.then(
                        Commands.literal(team == 0 ? "red" : "blue")
                                .executes(ctx ->
                                        run(
                                                ctx,
                                                c -> {
                                                    if (operation.equals("spawn"))
                                                        maps.spawn(
                                                                name(c),
                                                                index,
                                                                GameCommands.requirePlayer(c)
                                                                        .getLocation());
                                                    else maps.clearSpawns(name(c), index);
                                                    send(c, "updated", name(c));
                                                })));
            }
            map.then(Commands.literal(operation).then(argument));
        }
        RequiredArgumentBuilder<CommandSourceStack, String> spawnRemove = nameArgument();
        for (int team = 0; team < 2; team++) {
            int index = team;
            String pointType = team == 0 ? "red_spawn" : "blue_spawn";
            spawnRemove.then(
                    Commands.literal(team == 0 ? "red" : "blue")
                            .then(
                                    Commands.argument("number", IntegerArgumentType.integer(1))
                                            .executes(ctx ->
                                                    run(
                                                            ctx,
                                                            c -> {
                                                                int number = IntegerArgumentType.getInteger(c, "number");
                                                                maps.spawnRemove(name(c), index, number);
                                                                send(c, "point_removed", name(c),
                                                                        I18n.tr("finals.map.point." + pointType, number));
                                                            }))));
        }
        map.then(Commands.literal("spawnremove").then(spawnRemove));
        for (String operation : new String[] {"vault", "vaultadd", "vaultclear"}) {
            map.then(
                    Commands.literal(operation)
                            .then(
                                    nameArgument()
                                            .executes(ctx ->
                                                    run(
                                                            ctx,
                                                            c -> {
                                                                if (operation.equals(
                                                                        "vaultclear")) {
                                                                    maps.vaultClear(name(c));
                                                                } else {
                                                                    Location location =
                                                                            GameCommands
                                                                                    .requirePlayer(
                                                                                            c)
                                                                                    .getLocation();
                                                                    if (operation.equals(
                                                                            "vaultadd"))
                                                                        maps.vaultAdd(
                                                                                name(c), location);
                                                                    else
                                                                        maps.vault(
                                                                                name(c), location);
                                                                }
                                                                send(
                                                                        c,
                                                                        operation + "_updated",
                                                                        name(c));
                                                            }))));
        }
        RequiredArgumentBuilder<CommandSourceStack, String> cashout = nameArgument();
        for (int station = 0; station < 2; station++) {
            int index = station;
            cashout.then(
                    Commands.literal(station == 0 ? "a" : "b")
                            .executes(ctx ->
                                    run(
                                            ctx,
                                            c -> {
                                                maps.station(
                                                        name(c),
                                                        index,
                                                        GameCommands.requirePlayer(c)
                                                                .getLocation());
                                                send(c, "updated", name(c));
                                            })));
        }
        map.then(Commands.literal("cashout").then(cashout));
        for (String operation : new String[] {"cashoutadd", "cashoutclear"}) {
            map.then(
                    Commands.literal(operation)
                            .then(
                                    nameArgument()
                                            .executes(ctx ->
                                                    run(
                                                            ctx,
                                                            c -> {
                                                                if (operation.equals(
                                                                        "cashoutclear"))
                                                                    maps.cashoutClear(name(c));
                                                                else
                                                                    maps.cashoutAdd(
                                                                            name(c),
                                                                            GameCommands
                                                                                    .requirePlayer(
                                                                                            c)
                                                                                    .getLocation());
                                                                send(
                                                                        c,
                                                                        operation + "_updated",
                                                                        name(c));
                                                            }))));
        }
        for (String pointType : new String[] {"vault", "cashout"}) {
            map.then(
                    Commands.literal(pointType + "remove")
                            .then(
                                    nameArgument()
                                            .then(
                                                    Commands.argument("number", IntegerArgumentType.integer(1))
                                                            .executes(ctx ->
                                                                    run(
                                                                            ctx,
                                                                            c -> {
                                                                                int number = IntegerArgumentType.getInteger(c, "number");
                                                                                if (pointType.equals("vault")) maps.vaultRemove(name(c), number);
                                                                                else maps.cashoutRemove(name(c), number);
                                                                                send(c, "point_removed", name(c),
                                                                                        I18n.tr("finals.map.point." + pointType, number));
                                                                            })))));
        }
        map.then(
                Commands.literal("save")
                        .then(
                                nameArgument()
                                        .executes(ctx ->
                                                run(
                                                        ctx,
                                                        c -> {
                                                            maps.saveAsync(
                                                                    name(c),
                                                                    percent -> send(c, "save_progress", name(c), percent),
                                                                    () -> send(c, "saved", name(c)),
                                                                    error -> c.getSource().getSender()
                                                                            .sendMessage(I18n.message(error)
                                                                                    .color(NamedTextColor.RED)));
                                                            send(c, "saving", name(c));
                                                        }))));
        map.then(
                Commands.literal("use")
                        .then(
                                Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            maps.names().stream()
                                                    .filter(value ->
                                                            value.startsWith(
                                                                    builder
                                                                            .getRemainingLowerCase()))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx ->
                                                run(
                                                        ctx,
                                                        c -> {
                                                            maps.select(name(c));
                                                            send(c, "selected", name(c));
                                                        }))));
        map.then(
                Commands.literal("info")
                        .then(
                                nameArgument()
                                        .executes(ctx ->
                                                run(
                                                        ctx,
                                                        c ->
                                                                c.getSource()
                                                                        .getSender()
                                                                        .sendMessage(
                                                                                maps.info(
                                                                                        name(
                                                                                                c)))))));
        var pad = nameArgument();
        for (String kind : new String[] {"up", "targeted"})
            pad.then(Commands.literal(kind).executes(ctx -> run(ctx, c -> {
                maps.jumpPad(name(c), GameCommands.requirePlayer(c).getLocation(), kind.equals("targeted"));
                send(c, "pad_added", name(c));
                showPreview(c);
            })));
        map.then(Commands.literal("jumppad").then(pad));
        map.then(Commands.literal("jumppadtarget").then(nameArgument().then(
                Commands.argument("number", IntegerArgumentType.integer(1)).executes(ctx -> run(ctx, c -> {
                    maps.jumpPadTarget(name(c), IntegerArgumentType.getInteger(c, "number"), GameCommands.requirePlayer(c).getLocation());
                    send(c, "updated", name(c));
                    showPreview(c);
                })))));
        map.then(Commands.literal("jumppadremove").then(nameArgument().then(
                Commands.argument("number", IntegerArgumentType.integer(1)).executes(ctx -> run(ctx, c -> {
                    maps.jumpPadRemove(name(c), IntegerArgumentType.getInteger(c, "number"));
                    send(c, "updated", name(c));
                })))));
        root.then(map);
    }

    private void showPreview(CommandContext<CommandSourceStack> ctx) throws CommandFailure {
        if (previews == null) return;
        previews.show(GameCommands.requirePlayer(ctx), name(ctx));
        send(ctx, "preview.enabled", name(ctx));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> nameArgument() {
        return mapArgument("name");
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> mapArgument(String argument) {
        return Commands.argument(argument, StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    maps.drafts().stream()
                            .filter(value -> value.startsWith(builder.getRemainingLowerCase()))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    private void createHere(CommandContext<CommandSourceStack> ctx) throws CommandFailure {
        maps.create(name(ctx), GameCommands.requirePlayer(ctx).getLocation());
        send(ctx, "created", name(ctx));
        showPreview(ctx);
    }

    private void createShared(CommandContext<CommandSourceStack> ctx) throws CommandFailure {
        String sourceMap = StringArgumentType.getString(ctx, "sourceMap");
        maps.create(name(ctx), sourceMap);
        send(ctx, "created_shared", name(ctx), sourceMap);
        if (ctx.getSource().getExecutor() instanceof Player player
                && player.hasPermission(GameCommands.ADMIN_PERMISSION)
                && player.getWorld().getName().equals(maps.previewDraft(name(ctx)).world))
            showPreview(ctx);
    }

    private void importMap(CommandContext<CommandSourceStack> ctx) {
        String source = importSource(StringArgumentType.getString(ctx, "source"));
        String lower = source.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http:") || lower.startsWith("https:")) {
            maps.importUrlAsync(
                    name(ctx),
                    source,
                    () -> send(ctx, "imported", name(ctx)),
                    error -> ctx.getSource().getSender()
                            .sendMessage(I18n.message(error).color(NamedTextColor.RED)));
            send(ctx, "downloading", name(ctx));
        } else {
            maps.importWorld(name(ctx), source);
            send(ctx, "imported", name(ctx));
        }
    }

    /** Greedy URL arguments also retain the existing quoted local-filename syntax. */
    static String importSource(String value) {
        value = value.trim();
        if (!value.startsWith("\"")) return value;
        com.mojang.brigadier.StringReader reader = new com.mojang.brigadier.StringReader(value);
        try {
            String source = reader.readString();
            reader.skipWhitespace();
            if (reader.canRead()) throw FinalsMaps.failure("source");
            return source;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw FinalsMaps.failure("source");
        }
    }

    private static String name(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "name");
    }

    private static void send(
            CommandContext<CommandSourceStack> ctx, String key, Object... arguments) {
        ctx.getSource().getSender().sendMessage(I18n.tr("finals.map." + key, arguments));
    }

    @FunctionalInterface
    private interface Action {
        void run(CommandContext<CommandSourceStack> context) throws CommandFailure;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Action action) {
        return GameCommands.run(
                ctx,
                c -> {
                    try {
                        action.run(c);
                    } catch (IllegalArgumentException error) {
                        throw new CommandFailure(I18n.message(error));
                    }
                });
    }
}
