package me.petr1furious.potatominigames.games.cs;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Queue wiring and navigable routes through the actual imported Wingman geometry. */
final class CsWingmanCheck {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    static int run() {
        checks = 0;
        CsGame game = new CsGame((CsConfig) null);
        CsSession wingman = (CsSession) game.createSession(null, "wingman");
        check(wingman.wingman() && !wingman.deathmatch(), "Wingman selects Classic round rules");
        check(
                !((CsSession) game.createSession(null, "default")).wingman()
                        && !((CsSession) game.createSession(null, "deathmatch")).wingman(),
                "the two existing modes retain the full map");
        check(
                game.minPlayers("wingman") == game.minPlayers("default")
                        && game.maxPlayers("wingman") == 10
                        && game.teamColors("wingman").equals(game.teamColors("default")),
                "Wingman retains Classic's two teams and supports up to five per side");
        var input = CsWingmanCheck.class.getResourceAsStream("/config.yml");
        var config =
                YamlConfiguration.loadConfiguration(
                        new InputStreamReader(
                                Objects.requireNonNull(input), StandardCharsets.UTF_8));
        check(
                config.getBoolean("queues.cs.wingman.enabled")
                        && config.getInt("queues.cs.wingman.minPlayers") == 2
                        && config.getInt("queues.cs.wingman.maxPlayers") == 10,
                "Wingman ships as an enabled, independently joinable queue");

        var source = CsArena.Blueprint.load();
        Set<CsArena.Pos> blocks = new HashSet<>();
        for (var cell : source.cells()) blocks.add(cell.pos());
        Set<CsArena.Pos> borders = CsWingman.borders();
        check(
                !borders.isEmpty() && borders.size() < 30000,
                "only thin boundaries are generated, not filled forbidden volumes");
        check(
                borders.contains(new CsArena.Pos(0, 82, 18))
                        && borders.contains(new CsArena.Pos(73, 75, 37))
                        && borders.contains(new CsArena.Pos(80, 75, 43))
                        && borders.contains(new CsArena.Pos(93, 75, 57)),
                "T-to-B, tunnels, Mid doors and the CT exit all receive physical seals");
        check(
                !CsWingman.contains(93.5, 75, 57.5) && CsWingman.contains(95.5, 82, 57.5),
                "the lower CT-Mid exit closes without closing Short above it");
        check(
                !CsWingman.contains(114.5, 79, 6.5)
                        && !CsWingman.contains(73.5, 75, 30.5)
                        && !CsWingman.contains(93.5, 75, 45.5),
                "B, dark tunnels and CT-Mid stay outside the playable area");
        blocks.addAll(borders);
        for (int side = 0; side < 2; side++)
            for (var p : CsArena.spawnPoints(side)) {
                for (double dx : new double[] {-.3, .3})
                    for (double dz : new double[] {-.3, .3})
                        check(
                                CsWingman.contains(p.x() + dx, p.y(), p.z() + dz),
                                "the full body of every possible spawn is inside the zone");
                var feet = pos(p);
                check(
                        !blocks.contains(feet)
                                && !blocks.contains(
                                        new CsArena.Pos(feet.x(), feet.y() + 1, feet.z()))
                                && blocks.contains(
                                        new CsArena.Pos(feet.x(), feet.y() - 1, feet.z())),
                        "no boundary obstructs any of the twenty supported spawn candidates");
            }

        Set<CsArena.Pos> floors = new HashSet<>();
        for (var p : blocks) {
            var feet = new CsArena.Pos(p.x(), p.y() + 1, p.z());
            if (p.y() >= 73
                    && p.y() < 90
                    && CsWingman.contains(feet.x() + .5, feet.y(), feet.z() + .5)
                    && !blocks.contains(feet)
                    && !blocks.contains(new CsArena.Pos(feet.x(), feet.y() + 1, feet.z())))
                floors.add(feet);
        }
        for (int side = 0; side < 2; side++) {
            Set<CsArena.Pos> reachable =
                    reachable(pos(CsArena.spawnPoints(side).getFirst()), floors, blocks);
            check(reachable.contains(new CsArena.Pos(103, 82, 88)), "both teams can reach A");
            check(
                    reachable.contains(new CsArena.Pos(30, 78, 65))
                            && reachable.contains(new CsArena.Pos(76, 78, 65))
                            && reachable.contains(new CsArena.Pos(95, 82, 65)),
                    "Long, the Short approach and upper Short remain connected");
            for (int other = 0; other < 2; other++)
                for (var p : CsArena.spawnPoints(other))
                    check(
                            reachable.contains(pos(p)),
                            "all randomized starts share the playable map");
            check(
                    !reachable.contains(new CsArena.Pos(114, 79, 6))
                            && !reachable.contains(new CsArena.Pos(73, 75, 30))
                            && !reachable.contains(new CsArena.Pos(93, 75, 45)),
                    "the forbidden regions cannot be reached through the walking graph");
        }
        return checks;
    }

    private static CsArena.Pos pos(CsArena.Spawn p) {
        return new CsArena.Pos((int) Math.floor(p.x()), (int) Math.ceil(p.y()), (int) Math.floor(p.z()));
    }

    private static Set<CsArena.Pos> reachable(
            CsArena.Pos start, Set<CsArena.Pos> floors, Set<CsArena.Pos> blocks) {
        Set<CsArena.Pos> seen = new HashSet<>();
        ArrayDeque<CsArena.Pos> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            var p = pending.removeFirst();
            if (!seen.add(p)) continue;
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                for (int dy = 1; dy >= -3; dy--) {
                    var next = new CsArena.Pos(p.x() + step[0], p.y() + dy, p.z() + step[1]);
                    if (floors.contains(next)
                            && !seen.contains(next)
                            && CsArena.walkStepClear(blocks, p, next)) pending.add(next);
                }
        }
        return seen;
    }
}
