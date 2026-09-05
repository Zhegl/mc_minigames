package me.petr1furious.potatominigames.core.team;

import java.util.Locale;

import org.bukkit.Material;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum TeamColor {
    WHITE("White", NamedTextColor.WHITE, BossBar.Color.WHITE),
    ORANGE("Orange", NamedTextColor.GOLD, BossBar.Color.YELLOW),
    MAGENTA("Magenta", NamedTextColor.LIGHT_PURPLE, BossBar.Color.PURPLE),
    LIGHT_BLUE("Light Blue", NamedTextColor.BLUE, BossBar.Color.BLUE),
    YELLOW("Yellow", NamedTextColor.YELLOW, BossBar.Color.YELLOW),
    LIME("Lime", NamedTextColor.GREEN, BossBar.Color.GREEN),
    PINK("Pink", NamedTextColor.LIGHT_PURPLE, BossBar.Color.PINK),
    GRAY("Gray", NamedTextColor.DARK_GRAY, BossBar.Color.WHITE),
    LIGHT_GRAY("Light Gray", NamedTextColor.GRAY, BossBar.Color.WHITE),
    CYAN("Cyan", NamedTextColor.DARK_AQUA, BossBar.Color.BLUE),
    PURPLE("Purple", NamedTextColor.DARK_PURPLE, BossBar.Color.PURPLE),
    BLUE("Blue", NamedTextColor.DARK_BLUE, BossBar.Color.BLUE),
    BROWN("Brown", NamedTextColor.GOLD, BossBar.Color.YELLOW),
    GREEN("Green", NamedTextColor.DARK_GREEN, BossBar.Color.GREEN),
    RED("Red", NamedTextColor.RED, BossBar.Color.RED),
    BLACK("Black", NamedTextColor.BLACK, BossBar.Color.WHITE);

    private final String displayName;
    private final NamedTextColor textColor;
    private final BossBar.Color bossBarColor;
    private final Material woolMaterial;

    TeamColor(String displayName, NamedTextColor textColor, BossBar.Color bossBarColor) {
        this.displayName = displayName;
        this.textColor = textColor;
        this.bossBarColor = bossBarColor;
        this.woolMaterial = Material.valueOf(name() + "_WOOL");
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor textColor() {
        return textColor;
    }

    public BossBar.Color bossBarColor() {
        return bossBarColor;
    }

    public Material woolMaterial() {
        return woolMaterial;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayNameComponent() {
        return Component.text(displayName, textColor);
    }

    /** Lenient parse: accepts "light_blue", "light-blue", "Light Blue". Returns null if unknown. */
    public static TeamColor fromId(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (TeamColor color : values()) {
            if (color.id().equals(normalized)) {
                return color;
            }
        }
        return null;
    }
}
