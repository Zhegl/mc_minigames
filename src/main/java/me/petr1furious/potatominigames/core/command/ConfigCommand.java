package me.petr1furious.potatominigames.core.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.config.ConfigEditor;
import me.petr1furious.potatominigames.core.config.ConfigEditor.Kind;
import me.petr1furious.potatominigames.core.config.ConfigEditor.Node;

import static me.petr1furious.potatominigames.core.command.GameCommands.run;

/**
 * {@code config [path] [value]}: a dotted path mirroring the YAML; a value sets it, {@code default}
 * resets a declared setting, and lists take {@code add|remove <entry>}.
 */
public final class ConfigCommand {
    private static final String DEFAULT = "default";

    private final String root;
    private final ConfigEditor editor;

    /** @param root the command label, for click-to-edit */
    public ConfigCommand(String root, ConfigEditor editor) {
        this.root = root;
        this.editor = editor;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("config")
                .executes(ctx -> run(ctx, c -> show(c, editor.resolve(""))))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggest(ctx, builder))
                        .executes(ctx -> run(ctx, this::execute)));
    }

    private void execute(CommandContext<CommandSourceStack> ctx) throws CommandFailure {
        String input = ctx.getArgument("args", String.class).strip();
        int space = input.indexOf(' ');
        String path = trimPath(space < 0 ? input : input.substring(0, space));
        String value = space < 0 ? "" : input.substring(space + 1).strip();
        Node node = editor.resolve(path);
        if (node == null) {
            throw unknown(path);
        }
        if (value.isEmpty()) {
            show(ctx, node);
            return;
        }
        if (!ctx.getSource().getSender().hasPermission(GameCommands.ADMIN_PERMISSION)) {
            throw new CommandFailure("Changing settings needs " + GameCommands.ADMIN_PERMISSION + ".");
        }
        switch (node.kind()) {
            case SECTION -> throw new CommandFailure(path + " is a section; set a value inside it (options: "
                    + String.join(", ", childNames(node)) + ")");
            case LIST -> editList(ctx, node, value);
            case VALUE -> setValue(ctx, node, value);
        }
    }

    private void setValue(CommandContext<CommandSourceStack> ctx, Node node, String value) throws CommandFailure {
        String old = node.value();
        try {
            if (value.equalsIgnoreCase(DEFAULT)) {
                editor.reset(node);
            } else {
                editor.set(node, value);
            }
        } catch (IllegalArgumentException exception) {
            throw new CommandFailure("Invalid value for " + node.path() + ": " + value + " ("
                    + exception.getMessage() + ")");
        }
        Node updated = editor.resolve(node.path());
        String now = updated != null ? updated.value() : value;
        var line = Component.text()
                .append(Component.text(node.path() + ": ", NamedTextColor.GRAY))
                .append(Component.text(old, NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.GRAY))
                .append(Component.text(now, NamedTextColor.GREEN));
        if (value.equalsIgnoreCase(DEFAULT)) {
            line.append(Component.text(" (default)", NamedTextColor.GRAY));
        } else if (!node.isDeclared()) {
            line.append(Component.text(" (applies to the next game)", NamedTextColor.GRAY));
        }
        ctx.getSource().getSender().sendMessage(line.build());
    }

    private void editList(CommandContext<CommandSourceStack> ctx, Node node, String value) throws CommandFailure {
        int space = value.indexOf(' ');
        String verb = (space < 0 ? value : value.substring(0, space)).toLowerCase(Locale.ROOT);
        String entry = space < 0 ? "" : value.substring(space + 1).strip();
        if (!(verb.equals("add") || verb.equals("remove")) || entry.isEmpty()) {
            throw new CommandFailure(node.path() + " is a list: use " + node.path() + " add <entry> or "
                    + node.path() + " remove <entry>");
        }
        String changed;
        try {
            changed = verb.equals("add") ? editor.listAdd(node, entry) : editor.listRemove(node, entry);
        } catch (IllegalArgumentException exception) {
            throw new CommandFailure(node.path() + ": " + exception.getMessage());
        }
        ctx.getSource().getSender().sendMessage(Component.text()
                .append(Component.text(node.path() + ": ", NamedTextColor.GRAY))
                .append(Component.text((verb.equals("add") ? "+ " : "- ") + changed, NamedTextColor.GREEN))
                .append(Component.text(" (applies to the next game)", NamedTextColor.GRAY))
                .build());
    }

    private CommandFailure unknown(String path) {
        int dot = path.lastIndexOf('.');
        Node parent = editor.resolve(dot < 0 ? "" : path.substring(0, dot));
        if (parent == null || parent.kind() != Kind.SECTION) {
            return new CommandFailure("Unknown config path: " + path);
        }
        return new CommandFailure("Unknown config path: " + path + " (options"
                + (parent.path().isEmpty() ? "" : " under " + parent.path()) + ": "
                + String.join(", ", childNames(parent)) + ")");
    }

    private List<String> childNames(Node section) {
        return editor.children(section).stream().map(child -> lastSegment(child.path())).toList();
    }

    // Display.

    private void show(CommandContext<CommandSourceStack> ctx, Node node) {
        List<Component> lines = new ArrayList<>();
        switch (node.kind()) {
            case VALUE -> lines.add(valueLine(node, node.path(), 0));
            case LIST -> listLines(node, node.path(), 0, lines);
            case SECTION -> {
                int depth = 0;
                if (!node.path().isEmpty()) {
                    lines.add(Component.text(node.path() + ":", NamedTextColor.GOLD));
                    depth = 1;
                }
                sectionLines(node, depth, lines);
                if (lines.isEmpty()) {
                    lines.add(Component.text("Nothing to configure here.", NamedTextColor.GRAY));
                }
            }
        }
        ctx.getSource().getSender().sendMessage(Component.join(JoinConfiguration.newlines(), lines));
    }

    private void sectionLines(Node section, int depth, List<Component> lines) {
        for (Node child : editor.children(section)) {
            String label = lastSegment(child.path());
            switch (child.kind()) {
                case VALUE -> lines.add(valueLine(child, label, depth));
                case LIST -> listLines(child, label, depth, lines);
                case SECTION -> {
                    lines.add(Component.text("  ".repeat(depth) + label + ":", NamedTextColor.GOLD));
                    sectionLines(child, depth + 1, lines);
                }
            }
        }
    }

    private Component valueLine(Node node, String label, int depth) {
        NamedTextColor color = node.type().equals("text") ? NamedTextColor.WHITE : NamedTextColor.AQUA;
        return Component.text()
                .append(Component.text("  ".repeat(depth) + label + ": ", NamedTextColor.GRAY))
                .append(Component.text(node.value(), color))
                .clickEvent(ClickEvent.suggestCommand(command(node.path()) + " "))
                .hoverEvent(HoverEvent.showText(hover(node, "Click to edit")))
                .build();
    }

    private void listLines(Node node, String label, int depth, List<Component> lines) {
        List<String> entries = node.entries();
        String indent = "  ".repeat(depth);
        lines.add(Component.text()
                .append(Component.text(indent + label + ":" + (entries.isEmpty() ? " []" : ""), NamedTextColor.GRAY))
                .clickEvent(ClickEvent.suggestCommand(command(node.path()) + " add "))
                .hoverEvent(HoverEvent.showText(hover(node, "Click to add an entry")))
                .build());
        for (String entry : entries) {
            lines.add(Component.text()
                    .append(Component.text(indent + "  - ", NamedTextColor.GRAY))
                    .append(Component.text(entry, NamedTextColor.WHITE))
                    .clickEvent(ClickEvent.suggestCommand(command(node.path()) + " remove " + entry))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to remove", NamedTextColor.GRAY)))
                    .build());
        }
    }

    private static Component hover(Node node, String action) {
        String fallback = node.defaultValue();
        return Component.text()
                .append(Component.text(node.type(), NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text(fallback != null ? "default: " + fallback : "no default (file only)",
                        NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(action, NamedTextColor.YELLOW))
                .build();
    }

    private String command(String path) {
        return "/" + root + " config " + path;
    }

    // Completion.

    private CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder) {
        String input = builder.getRemaining();
        int space = input.indexOf(' ');
        if (space < 0) {
            return suggestPath(builder, input);
        }
        if (!ctx.getSource().getSender().hasPermission(GameCommands.ADMIN_PERMISSION)) {
            return builder.buildFuture();
        }
        Node node = editor.resolve(trimPath(input.substring(0, space)));
        String value = input.substring(space + 1);
        int start = builder.getStart() + space + 1;
        if (node == null || node.kind() == Kind.SECTION) {
            return builder.buildFuture();
        }
        if (node.kind() == Kind.LIST) {
            int verbEnd = value.indexOf(' ');
            if (verbEnd < 0) {
                SuggestionsBuilder verbs = builder.createOffset(start);
                offer(verbs, "add", "add an entry", value);
                offer(verbs, "remove", "remove an entry", value);
                return verbs.buildFuture();
            }
            SuggestionsBuilder entries = builder.createOffset(start + verbEnd + 1);
            if (value.substring(0, verbEnd).equalsIgnoreCase("remove")) {
                for (String entry : node.entries()) {
                    offer(entries, entry, "listed", value.substring(verbEnd + 1));
                }
            }
            return entries.buildFuture();
        }
        SuggestionsBuilder values = builder.createOffset(start);
        String current = node.value();
        if (node.isDeclared() || node.raw() != null) {
            offer(values, current, "current · " + node.type(), value);
        }
        for (String choice : node.choices()) {
            if (!choice.equalsIgnoreCase(current)) {
                offer(values, choice, node.type(), value);
            }
        }
        if (node.isDeclared() && !DEFAULT.equalsIgnoreCase(current)) {
            offer(values, DEFAULT, "reset to " + node.defaultValue(), value);
        }
        return values.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPath(SuggestionsBuilder builder, String input) {
        int dot = input.lastIndexOf('.');
        Node parent = editor.resolve(dot < 0 ? "" : input.substring(0, dot));
        SuggestionsBuilder segments = builder.createOffset(builder.getStart() + dot + 1);
        if (parent == null || parent.kind() != Kind.SECTION) {
            return segments.buildFuture();
        }
        String typed = input.substring(dot + 1);
        for (Node child : editor.children(parent)) {
            String name = lastSegment(child.path());
            String tooltip = switch (child.kind()) {
                case SECTION -> "section";
                case LIST -> "list of " + child.entries().size();
                case VALUE -> child.type() + " = " + child.value();
            };
            offer(segments, child.kind() == Kind.SECTION ? name + "." : name, tooltip, typed);
        }
        return segments.buildFuture();
    }

    private static void offer(SuggestionsBuilder builder, String text, String tooltip, String typed) {
        if (text.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
            builder.suggest(text, MessageComponentSerializer.message()
                    .serialize(Component.text(tooltip, NamedTextColor.GRAY)));
        }
    }

    private static String trimPath(String path) {
        return path.endsWith(".") ? path.substring(0, path.length() - 1) : path;
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
