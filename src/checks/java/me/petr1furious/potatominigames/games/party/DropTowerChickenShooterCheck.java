package me.petr1furious.potatominigames.games.party;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DropTowerChickenShooterCheck {
    public static void main(String[] args) {
        markedLandingsAndMisses();
        finishTimesAndProgress();
        chickenWaves();
        chickenScoring();
        System.out.println(
                "Drop Tower landings and rankings, Chicken Shooter waves and scoring passed");
    }

    private static void markedLandingsAndMisses() {
        var pads = DropTowerCourse.PADS;
        check(pads.size() >= 4, "A tower needs several separate landings");
        for (int i = 1; i < pads.size(); i++) {
            var current = pads.get(i - 1);
            var next = pads.get(i);
            double distance = Math.abs(current.x() - next.x()) + Math.abs(current.z() - next.z());
            check(
                    current.x() == next.x() || current.z() == next.z(),
                    "Each marked edge aims straight at its landing");
            check(current.floor() - next.floor() >= 8, "Drops need time to steer");
            check(
                    distance > current.radius() + next.radius(),
                    "Landing must be visible past the ledge");
            checkWalkingLanding(current, next, distance);
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
    }

    private static void checkWalkingLanding(
            DropTowerCourse.Pad current, DropTowerCourse.Pad next, double distance) {
        int airTicks = 0;
        double fall = 0;
        double velocity = 0;
        while (fall < current.floor() - next.floor()) {
            fall += velocity;
            velocity = (velocity + .08) * .98;
            airTicks++;
        }
        // A standing player still has support while their 0.3-block half-width overlaps the ledge.
        double ledge = current.radius() + .5 + .3;
        double near = distance - next.radius() - .5;
        double far = distance + next.radius() + .5;
        check(
                ledge + .13 * airTicks > near && ledge + .23 * (airTicks + 1) < far,
                "Holding forward at normal walking speed reaches the next pad without"
                    + " overshooting");
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
        var opener = ChickenGallery.birds(0).getFirst();
        check(opener.x(0) == 0 && opener.x(50) == 0 && opener.z() <= 8, "Close centered opener");
        var wave = ChickenGallery.birds(1);
        check(wave.size() == ChickenGallery.birds(0).size(), "Equal target count each wave");
        check(wave.stream().anyMatch(bird -> bird.x(0) != bird.x(25)), "Later chickens move");
        for (int i = 0; i < ChickenGallery.WAVES; i++)
            for (var bird : ChickenGallery.birds(i))
                for (int tick = 0; tick < 100; tick++)
                    check(
                            Math.abs(bird.x(tick)) < 7 && bird.z() > 4 && bird.z() < 20,
                            "Birds stay inside their own range and beyond the shooting deck");
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
