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

/**
 * Tracks one session's Adventure boss bars so they are reliably hidden when it ends.
 * "Show to all" means the session's audience — its players and spectators — and
 * {@link #sync} shows a newly arrived spectator everything the session has up.
 */
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

    /** Shows every bar of this session to a spectator, who watches the whole game rather than one side of it. */
    public void sync(Player player) {
        for (BossBar bar : bars) {
            player.showBossBar(bar);
        }
    }

    /** Hides this session's bars from one player who is leaving it. */
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
