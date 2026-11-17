package me.petr1furious.potatominigames.games.party;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DropTowerChickenShooterCheck {
    public static void main(String[] args) {
        markedLandingsAndMisses();
        isolatedLanes();
        finishTimesAndProgress();
        chickenWaves();
        chickenScoring();
        System.out.println(
                "Drop Tower landings and rankings, Chicken Shooter waves and scoring passed");
    }

    private static void markedLandingsAndMisses() {
        var pads = DropTowerCourse.PADS;
        check(pads.size() == 12, "The tower has eleven drops");
        check(
                pads.get(1).radius() == 2 && pads.get(8).radius() == 0,
                "The tower teaches using wide pads before the single-block landings");
        int detours = 0;
        for (int i = 1; i < pads.size(); i++) {
            var current = pads.get(i - 1);
            var next = pads.get(i);
            check(current.floor() - next.floor() >= 8, "Drops need time to steer");
            var baffle = DropTowerCourse.BAFFLES.get(i - 1);
            if (baffle == null) {
                check(
                        landingWindows(current, next, null, 0) >= 3,
                        "Several input timings reach landing " + i);
            } else {
                detours++;
                check(
                        landingWindows(current, next, baffle, 0) == 0,
                        "The middle route is obstructed");
                check(
                        landingWindows(current, next, baffle, -.99) >= 3
                                && landingWindows(current, next, baffle, .99) >= 3,
                        "Either side of the bar reaches landing " + i);
            }
            check(next.landed(next.x() + .5, next.floor() + 1, next.z() + .5), "Pad center lands");
            check(
                    !next.landed(next.x() + next.radius() + 1.1, next.floor() + 1, next.z() + .5),
                    "Edge miss");
            check(
                    !next.landed(next.x() + .5, next.floor() + 1.2, next.z() + .5),
                    "Airborne approach");
            check(!next.missed(next.floor() + 1.1), "Still time to land");
            check(next.missed(next.floor() + .8), "Fall below the pad returns to the checkpoint");
        }
        check(detours >= 2, "The course has two different bar detours");
    }

    private static int landingWindows(
            DropTowerCourse.Pad start,
            DropTowerCourse.Pad goal,
            DropTowerCourse.Baffle baffle,
            double lateral) {
        int successful = 0;
        for (int releaseAfter = 0; releaseAfter <= 25; releaseAfter++) {
            if (canLand(start, goal, baffle, lateral, releaseAfter, false)
                    || canLand(start, goal, baffle, lateral, releaseAfter, true)) successful++;
        }
        return successful;
    }

    private static boolean canLand(
            DropTowerCourse.Pad start,
            DropTowerCourse.Pad goal,
            DropTowerCourse.Baffle baffle,
            double lateral,
            int releaseAfter,
            boolean sprint) {
        double distance = Math.hypot(goal.x() - start.x(), goal.z() - start.z());
        double dx = (goal.x() - start.x()) / distance;
        double dz = (goal.z() - start.z()) / distance;
        double x = start.x() + .5 - dz * lateral;
        double z = start.z() + .5 + dx * lateral;
        double speed = 0;
        // Vanilla acceleration with ground friction, then 0.91 air friction.
        while (Math.abs(x - start.x() - .5) < start.radius() + .8
                && Math.abs(z - start.z() - .5) < start.radius() + .8) {
            speed += sprint ? .1274 : .098;
            x += dx * speed;
            z += dz * speed;
            speed *= .546;
        }
        double fall = 0;
        double velocity = 0;
        for (int tick = 0; fall < start.floor() - goal.floor(); tick++) {
            double from = start.floor() + 1 - fall;
            fall += velocity;
            velocity = (velocity + .08) * .98;
            speed += tick < releaseAfter ? sprint ? .02548 : .0196 : 0;
            x += dx * speed;
            z += dz * speed;
            speed *= .91;
            if (baffle != null
                    && from >= baffle.floor() + 1
                    && start.floor() - fall <= baffle.floor()
                    && baffle.overlaps(x, z)) return false;
        }
        return goal.landed(x, goal.floor() + 1, z);
    }

    private static void isolatedLanes() {
        for (int i = 0; i < 12; i++) {
            var lane = DropTowerCourse.Lane.at(i);
            for (var pad : DropTowerCourse.PADS) {
                check(
                        lane.contains(
                                lane.x() + pad.x() - pad.radius(),
                                lane.z() + pad.z() - pad.radius()),
                        "Pad's near edge is inside its own lane");
                check(
                        lane.contains(
                                lane.x() + pad.x() + pad.radius() + 1,
                                lane.z() + pad.z() + pad.radius() + 1),
                        "Pad's far edge is inside its own lane");
            }
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
        int last = DropTowerCourse.PADS.size() - 1;
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
