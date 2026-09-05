package me.petr1furious.potatominigames.core.hud;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

/** Tracks Adventure boss bars so they are reliably hidden when a session ends. */
public final class BossBarService {
    private final Set<BossBar> bars = new HashSet<>();

    public BossBar create(Component name, BossBar.Color color, BossBar.Overlay overlay) {
        BossBar bar = BossBar.bossBar(name, 1.0f, color, overlay);
        bars.add(bar);
        return bar;
    }

    public void showAll(BossBar bar) {
        Bukkit.getServer().showBossBar(bar);
    }

    public void hide(BossBar bar) {
        Bukkit.getServer().hideBossBar(bar);
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
