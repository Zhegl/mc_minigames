package me.petr1furious.potatominigames.games.party;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class PvpAirstrikeCheck {
    static void run() {
        var waves = new PvpAirstrike.Waves(100);
        check(
                waves.take(100) == -1 && waves.take(109) == -1,
                "No TNT before a full half-second warning");
        check(
                waves.take(110) == 0 && waves.take(110) == -1,
                "First row drops once after the warning");
        check(
                waves.take(113) == -1 && waves.take(114) == 1 && waves.take(114) == -1,
                "Second row follows four ticks later");
        check(
                waves.take(117) == -1 && waves.take(118) == 2 && waves.finished(),
                "Third row completes the nine-charge strike");
        check(
                waves.take(119) == -1 && waves.take(1000) == -1,
                "Finished strikes cannot create extra charges");
        var cancelled = new PvpAirstrike.Waves(10);
        cancelled.cancel();
        check(cancelled.take(1000) == -1 && cancelled.finished(), "Death or reset during warning cancels every future row");
        var partial = new PvpAirstrike.Waves(10);
        check(partial.take(20) == 0, "First wave can be released independently");
        partial.cancel();
        check(partial.take(1000) == -1, "Death between waves prevents the remaining rows");
        var wrap = new PvpAirstrike.Waves(Integer.MAX_VALUE - 10);
        check(
                wrap.take(Integer.MAX_VALUE - 1) == -1
                        && wrap.take(Integer.MAX_VALUE) == 0
                        && wrap.take(Integer.MIN_VALUE + 2) == -1
                        && wrap.take(Integer.MIN_VALUE + 3) == 1,
                "Warning timing survives tick-counter wrap");
        Location point = new Location(null, .5, 81.05, .5), original = point.clone();
        Set<String> positions = new HashSet<>();
        for (int row = 0; row < 3; row++) for (Location at : PvpAirstrike.row(point, row)) {
                check(at.getY() == point.getY() + 6, "Charges start six blocks above the aimed surface");
                check(Math.abs(at.getX() - point.getX()) <= 3 && Math.abs(at.getZ() - point.getZ()) <= 3,
                        "Every charge lands inside the indicated seven-block square");
                check(positions.add(at.getX() + ":" + at.getZ()), "Rows never duplicate a landing point");
            }
        check(positions.size() == PvpAirstrike.COUNT && point.equals(original), "Nine unique positions preserve the locked target");
        check(PvpAirstrike.validTarget(point, at -> Math.abs(at.getX()) < 10 && Math.abs(at.getZ()) < 10), "A square fully inside the arena is accepted");
        check(!PvpAirstrike.validTarget(new Location(null, 8, 81, 0), at -> Math.abs(at.getX()) <= 10 && Math.abs(at.getZ()) <= 10),
                "Squares crossing the arena edge are rejected before consuming ammo or cooldown");
        check(!PvpAirstrike.validTarget(point, at -> false), "A forbidden centre is rejected");
        check(
                PvpAirstrike.POWER < PvpBombs.POWER
                        && PvpAirstrike.MAX_DAMAGE == 9
                        && PvpAirstrike.FUSE == 18,
                "Airstrike charges are weaker with a predictable 0.9-second fuse");
        var miners = new PvpBombs.Cooldowns(PvpBombs.MINERS_THROW_COOLDOWN);
        UUID owner = new UUID(0, 1);
        miners.used(owner, 100, true);
        check(miners.remaining(owner, 139, true) == 1 && miners.remaining(owner, 140, true) == 0,
                "Changing the PvP class does not change Miners' two-second throw cooldown");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
