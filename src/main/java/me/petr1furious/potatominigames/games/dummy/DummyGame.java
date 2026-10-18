package me.petr1furious.potatominigames.games.dummy;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.text.TimeFormat;

/** Development game for exercising the session lifecycle. */
public final class DummyGame implements Minigame {
    private static final int ROUND_SECONDS = 30;

    @Override
    public String id() {
        return "dummy";
    }

    @Override
    public Component displayName() {
        return Component.text("Dummy", NamedTextColor.LIGHT_PURPLE);
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
    public GameSession createSession(SessionServices services, String mode) {
        return new DummySession(services);
    }

    private static final class DummySession implements GameSession {
        private final SessionServices services;
        private BossBar timerBar;

        DummySession(SessionServices services) {
            this.services = services;
        }

        @Override
        public void onStart() {
            timerBar = services.bossBars().create(
                    Component.text("Dummy round", NamedTextColor.LIGHT_PURPLE),
                    BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            for (Player player : services.participants().onlinePlayers()) {
                services.playerState().resetForRound(player, GameMode.ADVENTURE);
                player.getInventory().addItem(new ItemStack(Material.STICK));
                player.showBossBar(timerBar);
                if (services.participants().find(player.getUniqueId()).orElseThrow().team() != null) {
                    services.sidebar().setNameColor(player,
                            services.participants().find(player.getUniqueId()).orElseThrow().team());
                }
            }
            updateHud(0);
        }

        @Override
        public void onSecondTick(long secondsElapsed) {
            updateHud(secondsElapsed);
            if (secondsElapsed >= ROUND_SECONDS) {
                services.messages().broadcast(
                        Component.text("Dummy round complete", NamedTextColor.GREEN));
                services.session().endGame(EndReason.FINISHED);
            }
        }

        @Override
        public void onParticipantRejoin(Player player) {
            if (timerBar != null) {
                player.showBossBar(timerBar);
            }
        }

        @Override
        public void onEnd(EndReason reason) {
            for (Player player : services.participants().onlinePlayers()) {
                services.points().add("dummy", player.getUniqueId(), 1);
            }
        }

        private void updateHud(long elapsed) {
            long remaining = Math.max(0, ROUND_SECONDS - elapsed);
            if (timerBar != null) {
                timerBar.progress((float) remaining / ROUND_SECONDS);
                timerBar.name(Component.text("Dummy round — " + TimeFormat.clock(remaining),
                        NamedTextColor.LIGHT_PURPLE));
            }
            for (Player player : services.participants().onlinePlayers()) {
                services.sidebar().update(player,
                        Component.text("Dummy", NamedTextColor.LIGHT_PURPLE),
                        List.of(
                                Component.text("Time left: ", NamedTextColor.GOLD)
                                        .append(Component.text(TimeFormat.clock(remaining),
                                                NamedTextColor.AQUA)),
                                Component.text("Players: ", NamedTextColor.GOLD)
                                        .append(Component.text(services.participants().size(),
                                                NamedTextColor.AQUA))));
            }
        }
    }
}
