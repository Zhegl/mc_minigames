package me.petr1furious.potatominigames.core.text;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** The framed end-of-game block, sent as one chat message so nothing lands in between. */
public final class Summary {
    private static final Component SEPARATOR = Component.text("▬".repeat(24), NamedTextColor.DARK_GRAY);
    private static final String INDENT = "  ";

    private Summary() {
    }

    /**
     * @param body result lines; {@link Component#empty()} for a blank line
     * @param footer buttons, or null for none
     */
    public static Component frame(Component title, List<Component> body, Component footer) {
        TextComponent.Builder lines = Component.text();
        lines.append(SEPARATOR).appendNewline();
        lines.append(Component.text(INDENT)).append(title.decorate(TextDecoration.BOLD)).appendNewline();
        if (!body.isEmpty()) {
            lines.appendNewline();
            for (Component line : body) {
                if (!line.equals(Component.empty())) {
                    lines.append(Component.text(INDENT)).append(line);
                }
                lines.appendNewline();
            }
        }
        if (footer != null) {
            lines.appendNewline().append(Component.text(INDENT)).append(footer).appendNewline();
        }
        lines.append(SEPARATOR);
        return lines.build();
    }

    /** "1. Alice (3⚔)"-style ranking line. */
    public static Component rank(int place, Component name, Component value) {
        TextComponent.Builder line = Component.text()
                .append(Component.text(place + ". ", NamedTextColor.DARK_GRAY))
                .append(name);
        if (value != null) {
            line.append(Component.text(" (", NamedTextColor.DARK_GRAY)).append(value)
                    .append(Component.text(")", NamedTextColor.DARK_GRAY));
        }
        return line.build();
    }

    /** "Winner: Alice" / "Winners: Alice, Bob". */
    public static Component winners(List<Component> names) {
        return Component.text()
                .append(Component.text(names.size() == 1 ? "Winner: " : "Winners: ", NamedTextColor.GREEN))
                .append(Component.join(JoinConfiguration.commas(true), names))
                .build();
    }
}
