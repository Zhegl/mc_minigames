package me.petr1furious.potatominigames.core.hud;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

/** One session's boss bars, shown to its audience and hidden when it ends. */
public final class BossBarService {
    private final Set<BossBar> bars = new HashSet<>();
    private final Supplier<Collection<Player>> audience;

    public BossBarService(Supplier<Collection<Player>> audience) {
        this.audience = audience;
    }

    public BossBar create(Component name, BossBar.Color color, BossBar.Overlay overlay) {
        BossBar bar = BossBar.bossBar(name, 1.0f, color, overlay);
        bars.add(bar);
        return bar;
    }

    public void showAll(BossBar bar) {
        bars.add(bar);
        for (Player player : audience.get()) {
            player.showBossBar(bar);
        }
    }

    /** Spectators see every bar. */
    public void sync(Player player) {
        for (BossBar bar : bars) {
            player.showBossBar(bar);
        }
    }

    public void hideFrom(Player player) {
        for (BossBar bar : List.copyOf(bars)) {
            player.hideBossBar(bar);
        }
    }

    public void hide(BossBar bar) {
        for (var player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bar);
        }
        bars.remove(bar);
    }

    public void hideAll() {
        for (BossBar bar : Set.copyOf(bars)) {
            hide(bar);
        }
    }
}
