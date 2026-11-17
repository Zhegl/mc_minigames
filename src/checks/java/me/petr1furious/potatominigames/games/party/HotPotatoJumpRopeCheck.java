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
        check(JumpRopeSweep.position(1, 0, 50) < -9, "The forward rope starts behind the lane");
        check(JumpRopeSweep.position(1, 51, 50) > 9, "The forward rope reaches the far edge");
        check(JumpRopeSweep.position(-1, 0, 50) > 9, "The return rope starts on the far side");
        check(JumpRopeSweep.position(-1, 51, 50) < -9, "The return rope reaches the near edge");
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
