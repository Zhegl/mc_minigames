package me.petr1furious.potatominigames.games.tanks;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.*;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

/** Standalone armored vehicle battles, integrated with the shared queue and snapshots. */
public final class TanksGame implements Minigame {
    private final TanksConfig config;
    private final Map<UUID, TanksSession> playing = new HashMap<>();

    public TanksGame(ConfigRegistry registry) {
        config = new TanksConfig(registry);
    }

    @Override
    public String id() {
        return "tanks";
    }

    @Override
    public Component displayName() {
        return I18n.tr("tanks.name").color(NamedTextColor.DARK_GREEN);
    }

    @Override
    public Component modeTitle(String mode) {
        return I18n.tr("tanks.mode");
    }

    @Override
    public Component description(String mode) {
        return I18n.tr("tanks.description");
    }

    @Override
    public Material icon() {
        return Material.NETHERITE_INGOT;
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
        return new TanksSession(services, config, this);
    }

    void started(TanksSession session, Collection<UUID> ids) {
        ids.forEach(id -> playing.put(id, session));
    }

    void ended(TanksSession session) {
        playing.values().removeIf(s -> s == session);
    }

    @Override
    public void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        for (String action : List.of("help", "light", "medium", "heavy"))
            root.then(
                    Commands.literal(action)
                            .executes(ctx -> {
                                if (!(ctx.getSource().getSender() instanceof Player p)) {
                                    ctx.getSource()
                                            .getSender()
                                            .sendMessage(I18n.tr("tanks.error.player"));
                                    return 0;
                                }
                                TanksSession session = playing.get(p.getUniqueId());
                                if (session == null) {
                                    p.sendMessage(I18n.tr("tanks.error.not-playing"));
                                    return 0;
                                }
                                session.command(p, action);
                                return 1;
                            }));
    }
}
