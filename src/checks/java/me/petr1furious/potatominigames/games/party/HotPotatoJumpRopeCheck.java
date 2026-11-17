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
        check(JumpRopeSweep.ticks(0) > JumpRopeSweep.ticks(1), "Practice is slower than scoring");
        check(JumpRopeSweep.ticks(3) > JumpRopeSweep.ticks(6), "Later stages speed up");
        int last = JumpRopeSweep.ticks(50);
        check(JumpRopeSweep.position(1, 0, 50) < -9, "The forward rope starts behind the lane");
        check(JumpRopeSweep.position(1, last, 50) > 9, "The forward rope reaches the far edge");
        check(JumpRopeSweep.position(-1, 0, 50) > 9, "The return rope starts on the far side");
        check(JumpRopeSweep.position(-1, last, 50) < -9, "The return rope reaches the near edge");
        double openingStep = JumpRopeSweep.position(1, 1, 50) - JumpRopeSweep.position(1, 0, 50);
        double closingStep =
                JumpRopeSweep.position(1, last, 50) - JumpRopeSweep.position(1, last - 1, 50);
        check(closingStep > openingStep * 1.4, "The late rope accelerates into its approach");
    }

    private static void ropeFitsAllFiftySweepsIntoTheRound() {
        check(JumpRopeSweep.SCORED_SWEEPS == 50, "Fifty scoring sweeps follow practice");
        Random random = new Random(118);
        long total = 0;
        var latePauses = new HashSet<Integer>();
        for (int stage = 0; stage <= 50; stage++) {
            int pause = JumpRopeSweep.pauseTicks(stage, random);
            total += pause + JumpRopeSweep.ticks(stage);
            check(pause >= JumpRopeSweep.warningTicks(stage), "The warning fits inside the pause");
            if (stage >= 35) latePauses.add(pause);
        }
        check(
                total < JumpRopeSweep.durationSeconds() * 20L,
                "A perfect run reaches its fiftieth sweep");
        check(latePauses.size() >= 4, "The final sweeps vary the time between launches");
        check(
                JumpRopeSweep.warningTicks(50) < JumpRopeSweep.warningTicks(1),
                "The late audible warning is shorter");
    }

    private static void ropeLeavesTimeToJumpAgainAtTheEndWall() {
        Random random = new Random(950);
        for (double z : new double[] {-8.65, 0.15, 8.65}) {
            BoundingBox grounded = box(0, 0, z);
            long previousJump = -20;
            long start = 0;
            for (int stage = 1; stage <= 50; stage++) {
                start += JumpRopeSweep.pauseTicks(stage, random);
                int direction = stage % 2 == 0 ? 1 : -1;
                int ticks = JumpRopeSweep.ticks(stage);
                int contactTick = 1;
                while (contactTick <= ticks
                        && !JumpRopeSweep.hits(
                                grounded,
                                grounded,
                                JumpRopeSweep.position(direction, contactTick - 1, stage),
                                JumpRopeSweep.position(direction, contactTick, stage),
                                81)) contactTick++;
                check(contactTick <= ticks, "The rope reaches each player's lane");
                int jumpAt = contactTick - 3;
                check(
                        start + jumpAt - previousJump >= 12,
                        "A player can land before jumping again");
                for (int age = 1; age <= ticks; age++) {
                    BoundingBox from = box(0, jumpHeight(age - 1 - jumpAt), z);
                    BoundingBox to = box(0, jumpHeight(age - jumpAt), z);
                    check(
                            !JumpRopeSweep.hits(
                                    from,
                                    to,
                                    JumpRopeSweep.position(direction, age - 1, stage),
                                    JumpRopeSweep.position(direction, age, stage),
                                    81),
                            "A regular jump can clear each sweep at the ends and the middle");
                }
                previousJump = start + jumpAt;
                start += ticks;
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
