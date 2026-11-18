package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.player.ParticipantRegistry;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DropTowerChickenShooterCheck {
    public static void main(String[] args) throws ReflectiveOperationException {
        DropTowerRoutesCheck.generatedCourses();
        playerKeepsView();
        isolatedLanes();
        finishTimesAndProgress();
        chickenWaves();
        chickenScoring();
        System.out.println(
                "Drop Tower landings and rankings, Chicken Shooter waves and scoring passed");
    }

    private static void playerKeepsView() throws ReflectiveOperationException {
        var map = new PartyArenaCheck.WorldMap();
        var world = map.world;
        UUID id = id(1);
        Location[] position = {new Location(world, 0, 199, 0, 123, -14)};
        boolean[] grounded = {false};
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> "Racer";
                    case "getLocation" -> position[0].clone();
                    case "getWorld" -> world;
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "isOnline" -> true;
                    case "isDead" -> false;
                    case "isOnGround" -> grounded[0];
                    case "teleport" -> {
                        position[0] = ((Location) args[0]).clone();
                        yield true;
                    }
                    case "setFallDistance", "setVelocity", "playSound", "sendActionBar", "spawnParticle" -> null;
                    default -> throw new AssertionError(method);
                });
        Field server = Bukkit.class.getDeclaredField("server");
        server.setAccessible(true);
        Object original = server.get(null);
        try {
            server.set(null, Proxy.newProxyInstance(Server.class.getClassLoader(),
                    new Class<?>[] {Server.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getPlayer") && id.equals(args[0])) return player;
                        throw new AssertionError(method);
                    }));
            ParticipantRegistry participants = new ParticipantRegistry();
            participants.add(player);
            var services = new SessionServices(null, null, participants, null, null, null,
                    null, null, null, null, null, null);
            var round = new DropTowerSession(services, null, PartyRoundContext.standalone(services));
            round.world = world;
            round.names.put(id, "Racer");
            round.names.put(id(2), "Second racer");
            round.connected.add(id);
            round.standings = new SurvivalStandings(Set.of(id));
            round.buildArena();
            identicalBuiltLanes(map, round.course);
            var spawn = round.spawn(0, 1);
            check(spawn.getYaw() == 123 && spawn.getPitch() == -14, "Opening spawn keeps the player's view");
            var start = round.course.pads().getFirst();
            Location takeoff = new Location(world, start.x() + start.radius() + 1.4,
                    start.floor() + 1, start.z() + .5, 95, 21);
            grounded[0] = true;
            position[0] = takeoff.clone();
            round.playTick(0);
            check(position[0].equals(takeoff), "Leaving the lip with the last grounded flag still set permits walking off");
            grounded[0] = false;
            var first = round.course.pads().get(1);
            var landing = new Location(world, first.x() + .8, first.floor() + 1,
                    first.z() + .9, -66, 31);
            position[0] = landing.clone();
            round.playTick(1);
            position[0] = landing.clone().add(0, -40, 0);
            round.playTick(2);
            check(samePosition(position[0], spawn), "An airborne fly-through cannot save a checkpoint");
            grounded[0] = true;
            position[0] = landing.clone();
            round.playTick(3);
            check(position[0].equals(landing), "A successful landing does not reposition or aim the player");
            position[0] = landing.clone().add(0, -40, 0);
            round.playTick(4);
            check(samePosition(position[0], new Location(world, first.x() + .5, first.floor() + 1, first.z() + .5)),
                    "A miss returns to the latest checkpoint");
            check(position[0].getYaw() == -66 && position[0].getPitch() == 31,
                    "Checkpoint retries keep the player's view");
            Location retry = position[0].clone();
            position[0] = spawn.clone();
            round.playTick(5);
            check(samePosition(position[0], retry), "Landing back on an earlier pad restores the current checkpoint");
            for (int stage = 1; stage < round.course.jumps().size(); stage++) {
                var bars = round.course.jumps().get(stage).baffles();
                if (!bars.isEmpty()) {
                    var bar = bars.getFirst();
                    position[0] = new Location(world, bar.minX() + .5, bar.floor() + 1, bar.minZ() + .5, 72, 34);
                    round.playTick(5 + stage);
                    var current = round.course.pads().get(stage);
                    check(samePosition(position[0], new Location(world, current.x() + .5, current.floor() + 1,
                            current.z() + .5)), "A grounded baffle hit restores the current checkpoint");
                    check(position[0].getYaw() == 72 && position[0].getPitch() == 34, "Baffle retries preserve the view");
                    break;
                }
                var next = round.course.pads().get(stage + 1);
                position[0] = new Location(world, next.x() + .5, next.floor() + 1, next.z() + .5);
                round.playTick(5 + stage);
            }
        } finally {
            server.set(null, original);
        }
    }

    private static boolean samePosition(Location a, Location b) {
        return a.getWorld() == b.getWorld() && a.getX() == b.getX()
                && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    private static void identicalBuiltLanes(PartyArenaCheck.WorldMap map, DropTowerCourse course) {
        var first = DropTowerCourse.Lane.at(0);
        var second = DropTowerCourse.Lane.at(1);
        Map<String, Material> firstBlocks = new HashMap<>(), secondBlocks = new HashMap<>();
        for (var block : map.blocks.entrySet()) {
            String[] coords = block.getKey().split(":");
            int x = Integer.parseInt(coords[0]), z = Integer.parseInt(coords[2]);
            if (first.contains(x + .5, z + .5))
                firstBlocks.put((x - first.x()) + ":" + coords[1] + ":" + (z - first.z()), block.getValue());
            else if (second.contains(x + .5, z + .5))
                secondBlocks.put((x - second.x()) + ":" + coords[1] + ":" + (z - second.z()), block.getValue());
            else throw new AssertionError("Block outside both lanes at " + block.getKey());
        }
        check(!firstBlocks.isEmpty() && firstBlocks.equals(secondBlocks),
                "Both racers must see the same blocks and cues after translating their lane origin");
        Set<String> solids = new HashSet<>();
        for (var pad : course.pads())
            for (int dx = -pad.radius(); dx <= pad.radius(); dx++)
                for (int dz = -pad.radius(); dz <= pad.radius(); dz++)
                    if (pad.tile(dx, dz)) solids.add((pad.x() + dx) + ":" + pad.floor() + ":" + (pad.z() + dz));
        for (var jump : course.jumps())
            for (var baffle : jump.baffles())
                for (int x = baffle.minX(); x <= baffle.maxX(); x++)
                    for (int z = baffle.minZ(); z <= baffle.maxZ(); z++)
                        solids.add(x + ":" + baffle.floor() + ":" + z);
        check(firstBlocks.keySet().equals(solids), "The built lane must match the geometry used for route checks");
    }

    private static void isolatedLanes() {
        for (int i = 0; i < 12; i++) {
            var lane = DropTowerCourse.Lane.at(i);
            for (int j = 0; j < i; j++) {
                var other = DropTowerCourse.Lane.at(j);
                check(
                        Math.max(Math.abs(lane.x() - other.x()), Math.abs(lane.z() - other.z()))
                                >= 2 * DropTowerCourse.LANE_RADIUS + 2,
                        "Two lanes have distinct playable bounds");
            }
        }
    }

    private static void finishTimesAndProgress() {
        UUID fast = id(1), slow = id(2), tie = id(3), progress = id(4), start = id(5);
        Set<UUID> roster = Set.of(fast, slow, tie, progress, start);
        int last = DropTowerCourse.JUMPS;
        ParkourRace race = new ParkourRace(roster, last);
        check(!race.reach(progress, 2, 1), "A later pad cannot skip a checkpoint");
        check(race.reach(progress, 1, 5) && race.reset(progress), "First pad saves a checkpoint");
        check(race.checkpoint(progress) == 1, "Retry preserves the reached pad");
        for (int pad = 1; pad <= last; pad++) {
            check(race.reach(fast, pad, pad * 20L), "Fast finish");
            check(race.reach(tie, pad, pad * 20L), "Tied finish");
            check(race.reach(slow, pad, pad * 30L), "Slow finish");
        }
        PartyRoundResult result = race.result(roster);
        check(result.place(fast) == 1 && result.place(tie) == 1, "Exact tick ties share first");
        check(
                result.place(slow) == 3 && result.place(progress) == 4 && result.place(start) == 5,
                "Completion time precedes unfinished checkpoint progress");
        check(race.result(Set.of(slow, start)).place(slow) == 1, "Withdrawn players are excluded");
        race.close();
        check(!race.reach(start, 1, 200), "Closed race rejects progress");
    }

    private static void chickenWaves() {
        check(
                ChickenGallery.wave(0) == 0 && ChickenGallery.wave(99) == 0,
                "Opening birds stay five seconds");
        check(ChickenGallery.wave(100) == 1, "Every lane restocks on the same tick");
        var opener = ChickenGallery.WAVES.getFirst();
        var center = opener.birds().getFirst();
        check(center.x(0) == 0 && center.x(50) == 0 && center.z(0) <= 8, "Close centered opener");
        var late = ChickenGallery.WAVES.getLast();
        check(late.scale() < 1 && opener.scale() >= 1.8, "Target hitboxes shrink after the opener");
        check(
                late.ticks() <= 65 && late.ticks() < opener.ticks(),
                "Later waves demand faster shots");
        check(
                late.birds().getFirst().z(0) >= 11
                        && late.birds().getFirst().z(0) != late.birds().getFirst().z(20),
                "Later chickens change depth and start farther away");
        int elapsed = 0;
        for (int i = 0; i < ChickenGallery.WAVES.size(); i++) {
            var wave = ChickenGallery.WAVES.get(i);
            check(
                    ChickenGallery.wave(elapsed) == i
                            && ChickenGallery.wave(elapsed + wave.ticks() - 1) == i,
                    "Every lane uses the same wave window");
            check(
                    wave.birds().size() == opener.birds().size(),
                    "Equal target count in each lane and wave");
            for (var bird : wave.birds())
                for (int tick = 0; tick < wave.ticks(); tick++)
                    check(
                            Math.abs(bird.x(tick)) < 7 && bird.z(tick) > 4 && bird.z(tick) < 21,
                            "Birds stay inside their own range and beyond the shooting deck");
            elapsed += wave.ticks();
        }
        check(
                elapsed == ChickenGallery.DURATION_TICKS && elapsed % 20 == 0,
                "The round clock includes the complete final wave");
    }

    private static void chickenScoring() {
        UUID one = id(1), two = id(2), three = id(3), spectator = id(4);
        UUID firstBird = id(11), secondBird = id(12), expired = id(13);
        ChickenGallery gallery = new ChickenGallery(List.of(one, two, three));
        gallery.addBird(firstBird, one);
        gallery.addBird(secondBird, two);
        gallery.addBird(expired, one);
        gallery.removeBird(expired);
        check(
                !gallery.hit(two, firstBird) && !gallery.hit(spectator, firstBird),
                "Only the lane owner can score");
        check(!gallery.hit(one, expired), "A despawned target cannot score");
        check(
                gallery.hit(one, firstBird) && !gallery.hit(one, firstBird),
                "A bird scores exactly once");
        check(gallery.hit(two, secondBird), "Same scoring for every lane");
        PartyRoundResult ranking = gallery.result(Set.of(one, two, three, spectator));
        check(gallery.score(one) == 1 && gallery.score(two) == 1, "Each bird is worth one point");
        check(
                ranking.place(one) == 1 && ranking.place(two) == 1 && ranking.place(three) == 3,
                "Equal scores share place and zero scores remain eligible");
        check(
                ranking.place(spectator) == 0 && gallery.result(Set.of(two)).place(one) == 0,
                "Spectators and withdrawn players cannot place");
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }

    private static void check(boolean pass, String description) {
        if (!pass) throw new AssertionError(description);
    }
}
