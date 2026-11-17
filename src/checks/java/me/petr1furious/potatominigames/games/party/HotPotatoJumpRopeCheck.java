package me.petr1furious.potatominigames.games.party;

import org.bukkit.util.BoundingBox;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class HotPotatoJumpRopeCheck {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    public static void main(String[] args) {
        potatoRandomizesAndRotatesSeeds();
        potatoRejectsImmediateTradebackAndKeepsItsFuse();
        potatoReplacesAnAbsentCarrier();
        ropeSweepsTheWholeFloorAndSpeedsUp();
        ropeFitsAllFiftySweepsIntoTheRound();
        ropeLeavesTimeToJumpAgainAtTheEndWall();
        ropeTestsThePlayerVolumeThroughoutTheSweep();
    }

    private static void potatoRandomizesAndRotatesSeeds() {
        var seeds = new HashSet<UUID>();
        var roster = List.of(A, B, C);
        for (int seed = 0; seed < 24; seed++) {
            var fuse = new HotPotatoFuse(new Random(seed * 7919L));
            fuse.start(roster, 0, 200);
            seeds.add(fuse.carrier());
        }
        check(seeds.size() == 3, "Any player can get the opening potato");

        var fuse = new HotPotatoFuse(new Random(72));
        seeds.clear();
        for (int round = 0; round < 3; round++) {
            fuse.start(roster, round * 220L, 200);
            check(seeds.add(fuse.carrier()), "Every eligible player seeds before a repeat");
        }
        UUID previous = fuse.carrier();
        fuse.start(roster, 660, 200);
        check(!previous.equals(fuse.carrier()), "A new rotation avoids back-to-back seeds");
    }

    private static void potatoRejectsImmediateTradebackAndKeepsItsFuse() {
        var fuse = new HotPotatoFuse(new Random(0));
        fuse.start(List.of(A), 0, 200);
        check(!fuse.pass(B, C, 10), "A runner cannot pass the potato");
        check(!fuse.pass(A, A, 10), "The carrier cannot pass it to themselves");
        check(fuse.pass(A, B, 20), "The carrier can pass to a nearby runner");
        check(!fuse.pass(B, A, 25), "The sender has brief protection from a tradeback");
        check(fuse.pass(B, C, 25), "Tradeback protection leaves other runners available");
        check(!fuse.pass(C, B, 26), "Protection follows the most recent sender");
        check(fuse.pass(C, B, 65), "Returning the potato works after protection expires");
        check(fuse.remaining(199) == 1, "Successful passes preserve the original deadline");
        check(!fuse.pass(B, A, 200), "A click at the blown fuse cannot save the carrier");
    }

    private static void potatoReplacesAnAbsentCarrier() {
        var fuse = new HotPotatoFuse(new Random(0));
        fuse.start(List.of(A), 0, 200);
        check(!fuse.replaceMissing(List.of(A, B), 10), "The present carrier keeps the potato");
        check(fuse.replaceMissing(List.of(B), 196), "A departure reassigns the potato immediately");
        check(B.equals(fuse.carrier()), "The remaining runner receives the abandoned potato");
        check(
                fuse.remaining(196) >= 80,
                "The new carrier gets time to respond to a late departure");
        check(fuse.replaceMissing(List.of(), 197), "No eligible player clears the active potato");
        check(fuse.carrier() == null, "The potato cannot stick to a disconnected player");
    }

    private static void ropeSweepsTheWholeFloorAndSpeedsUp() {
        for (int seed = 0; seed < 24; seed++) {
            var course = JumpRopeSweep.course(new Random(seed));
            var practice = course.getFirst();
            var opening = course.get(1);
            check(practice.travelTicks() > opening.travelTicks(), "Practice is slower than scoring");
            check(course.get(10).travelTicks() * 2 <= opening.travelTicks(),
                    "By sweep ten, the rope crosses in at most half the opening time");
            check(course.get(6).warningTicks() <= opening.warningTicks() / 2,
                    "Warnings shorten during the first six scored passes");
            for (var pass : course) {
                for (int direction : new int[] {-1, 1}) {
                    double first = direction * pass.position(direction, 0);
                    double last = direction * pass.position(direction, pass.travelTicks());
                    check(first < -9 && last > 9, "Every pass reaches both ends of the court");
                    for (int age = 1; age <= pass.travelTicks(); age++) {
                        check(direction * (pass.position(direction, age)
                                - pass.position(direction, age - 1)) > 0,
                                "All paces travel monotonically without an untelegraphed reversal");
                    }
                }
            }
        }
        var smooth = new JumpRopeSweep.Pass(20, 12, 6, JumpRopeSweep.Pace.STEADY);
        var rush = new JumpRopeSweep.Pass(20, 12, 6, JumpRopeSweep.Pace.RUSH);
        var surge = new JumpRopeSweep.Pass(20, 12, 6, JumpRopeSweep.Pace.SURGE);
        var hesitation = new JumpRopeSweep.Pass(20, 12, 6, JumpRopeSweep.Pace.HESITATE);
        check(rush.position(1, 10) > smooth.position(1, 10)
                        && surge.position(1, 10) < smooth.position(1, 10),
                "Rush and surge have different arrival times at center court");
        double stepBefore = hesitation.position(1, 5) - hesitation.position(1, 4);
        double stepDuring = hesitation.position(1, 10) - hesitation.position(1, 9);
        double stepAfter = hesitation.position(1, 16) - hesitation.position(1, 15);
        check(stepDuring < stepBefore * .6 && stepAfter > stepDuring * 2,
                "Some passes approach, visibly slow down, then lunge");
    }

    private static void ropeFitsAllFiftySweepsIntoTheRound() {
        for (int seed = 0; seed < 24; seed++) {
            var course = JumpRopeSweep.course(new Random(seed));
            check(course.size() == 51, "Fifty scoring sweeps follow practice");
            int total = 0;
            var lateRestTimes = new HashSet<Integer>();
            var lateTravelTimes = new HashSet<Integer>();
            var lateLaunchIntervals = new HashSet<Integer>();
            var latePaces = new HashSet<JumpRopeSweep.Pace>();
            for (int stage = 0; stage < course.size(); stage++) {
                var pass = course.get(stage);
                total += pass.restTicks() + pass.travelTicks();
                check(pass.restTicks() >= pass.warningTicks() && pass.warningTicks() >= 6,
                        "The warning fits inside the pause and has a minimum lead time");
                if (stage >= 12) {
                    lateRestTimes.add(pass.restTicks());
                    lateTravelTimes.add(pass.travelTicks());
                    lateLaunchIntervals.add(course.get(stage - 1).travelTicks() + pass.restTicks());
                    latePaces.add(pass.pace());
                }
            }
            check(total < JumpRopeSweep.durationSeconds(course) * 20L,
                    "A perfect run reaches its fiftieth sweep");
            check(latePaces.size() == 4 && lateRestTimes.size() >= 10 && lateTravelTimes.size() >= 7,
                    "Late sweeps mix four pace profiles, varied travel times and different pauses");
            check(lateLaunchIntervals.size() >= 12,
                    "Late sweeps cannot be played at a constant launch period");
        }
    }

    private static void ropeLeavesTimeToJumpAgainAtTheEndWall() {
        for (int seed = 0; seed < 24; seed++) {
            var course = JumpRopeSweep.course(new Random(seed));
            for (int lane = 0; lane <= 32; lane++) {
                double z = 8.7 * (lane - 16) / 16;
                BoundingBox grounded = box(0, 0, z);
                long previousJump = -20;
                long start = course.getFirst().restTicks() + course.getFirst().travelTicks();
                for (int stage = 1; stage < course.size(); stage++) {
                    var pass = course.get(stage);
                    start += pass.restTicks();
                    int direction = stage % 2 == 0 ? 1 : -1;
                    int contactTick = 1;
                    while (contactTick <= pass.travelTicks()
                            && !JumpRopeSweep.hits(grounded, grounded,
                                    pass.position(direction, contactTick - 1),
                                    pass.position(direction, contactTick), 81)) contactTick++;
                    check(contactTick <= pass.travelTicks(), "The rope reaches every lane");
                    int jumpAt = contactTick - 3;
                    check(start + jumpAt - previousJump >= 12,
                            "A player can land before jumping again, even next to a gate");
                    for (int age = 1; age <= pass.travelTicks(); age++) {
                        BoundingBox from = box(0, jumpHeight(age - 1 - jumpAt), z);
                        BoundingBox to = box(0, jumpHeight(age - jumpAt), z);
                        check(!JumpRopeSweep.hits(from, to, pass.position(direction, age - 1),
                                        pass.position(direction, age), 81),
                                "A normal jump can clear every pace throughout the court");
                    }
                    previousJump = start + jumpAt;
                    start += pass.travelTicks();
                }
            }
        }
    }

    private static double jumpHeight(int age) {
        double height = 0;
        double velocity = .42;
        for (int tick = 0; tick < age; tick++) {
            height += velocity;
            velocity = (velocity - .08) * .98;
        }
        return Math.max(0, height);
    }

    private static void ropeTestsThePlayerVolumeThroughoutTheSweep() {
        BoundingBox grounded = box(0, 0, 0);
        BoundingBox airborne = box(0, .6, 0);
        check(
                JumpRopeSweep.hits(grounded, grounded, -1.2, 1.2, 81),
                "A fast rope cannot tunnel through a grounded player");
        check(
                !JumpRopeSweep.hits(airborne, airborne, -1.2, 1.2, 81),
                "An ordinary jump clears the rope");
        check(
                JumpRopeSweep.hits(grounded, airborne, -.75, .45, 81),
                "A jump started too late still touches the rope");
        check(
                !JumpRopeSweep.hits(grounded, airborne, -1.2, -.3, 81),
                "A jump that clears the rope before horizontal contact succeeds");
        check(
                !JumpRopeSweep.hits(grounded, box(0, 0, .8), .6, 1.4, 81),
                "Swept contact uses relative motion for a runner moving with the rope");
        check(
                JumpRopeSweep.hits(box(5.75, 0, 0), box(5.75, 0, 0), -1.2, 1.2, 81),
                "The rope covers players along the side rail");
    }

    private static BoundingBox box(double x, double rise, double z) {
        return new BoundingBox(x - .3, 81 + rise, z - .3, x + .3, 82.8 + rise, z + .3);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
