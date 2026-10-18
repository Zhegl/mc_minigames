package me.petr1furious.potatominigames.games.chase;

import org.bukkit.Material;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.gadgets.GadgetRegistry;

public final class ChaseGame implements Minigame {
    private final ChaseConfig config;
    private final GadgetRegistry gadgets;

    public ChaseGame(ConfigRegistry configs, GadgetRegistry gadgets) {
        this.config = new ChaseConfig(configs);
        this.gadgets = gadgets;
    }

    @Override
    public String id() {
        return "chase";
    }

    @Override
    public Component displayName() {
        return Component.text("Chase", NamedTextColor.AQUA);
    }

    @Override
    public boolean usesGadgets() {
        return true;
    }

    @Override
    public Material icon() {
        return Material.COMPASS;
    }

    @Override
    public Component description(String mode) {
        return Component.text("Hunters track runners by compass");
    }

    @Override
    public GameSession createSession(SessionServices services, String mode) {
        return new ChaseSession(services, config, gadgets);
    }
}
