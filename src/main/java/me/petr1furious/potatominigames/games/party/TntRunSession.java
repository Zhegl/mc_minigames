package me.petr1furious.potatominigames.games.party;

import org.bukkit.Location;
import me.petr1furious.potatominigames.core.game.SessionServices;

final class TntRunSession extends PartySurvivalSession {
    private TntRunArena arena;
    private final int duration, collapse;
    TntRunSession(SessionServices services, PartyConfig config) {
        super(services, config, "tnt_run", "TNT Run");
        duration = Math.clamp(config.tntRunSeconds.get(), 30, 600);
        collapse = Math.clamp(config.tntRunCollapseTicks.get(), 3, 40);
    }
    @Override void buildArena() { arena = new TntRunArena(world); arena.build(); }
    @Override Location spawn(int index, int count) { return arena.spawn(index, count); }
    @Override Location viewpoint() { return new Location(world, .5, TntRunArena.FLOORS[0] + 12, .5); }
    @Override double outHeight() { return TntRunArena.FLOORS[2] - 4; }
    @Override int durationSeconds() { return duration; }
    @Override String instructions() { return "TNT Run: пол исчезает под ногами! Три яруса, ни одного второго шанса. Беги и прыгай — последний выживший побеждает."; }
    @Override void playTick(long elapsed) {
        int delay = TntRunArena.delay(collapse, elapsed);
        for (var player : alivePlayers()) arena.step(player.getLocation(), elapsed, delay);
        arena.tick(elapsed, TntRunArena.radius(elapsed));
    }
    @Override String status() { return "Пол исчезает за " + String.format(java.util.Locale.ROOT, "%.2f", TntRunArena.delay(collapse, elapsed()) / 20.0) + " с"; }
}
