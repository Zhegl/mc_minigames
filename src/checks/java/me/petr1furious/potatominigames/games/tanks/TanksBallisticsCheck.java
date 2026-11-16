package me.petr1furious.potatominigames.games.tanks;

/** Swept oriented hull tests, including high-speed shots and inside origins. */
public final class TanksBallisticsCheck {
    public static void main(String[] args) {
        check(
                TanksBallistics.intersection(0, 1, -20, 0, 1, 20, 0, 0, 0, 0) < .5,
                "fast shell hits hull");
        check(
                Double.isInfinite(TanksBallistics.intersection(5, 1, -20, 5, 1, 20, 0, 0, 0, 0)),
                "side miss");
        check(
                Double.isInfinite(TanksBallistics.intersection(0, 4, -20, 0, 4, 20, 0, 0, 0, 0)),
                "overhead miss");
        check(TanksBallistics.intersection(0, 1, 0, 0, 1, 20, 0, 0, 0, 0) == 0, "inside origin");
        check(
                TanksBallistics.intersection(-20, 1, 0, 20, 1, 0, 0, 0, 0, 90) < .5,
                "rotated hull hit");
        check(
                Double.isInfinite(TanksBallistics.intersection(2, 1, -20, 2, 1, 20, 0, 0, 0, 0)),
                "outside track width");
        check(
                TanksBallistics.intersection(2, 1, -20, 2, 1, 20, 0, 0, 0, 90) < .5,
                "rotated length covers shot");
        check(TanksBallistics.smokeCrosses(0, 0, 0, 20, 0, 0, 10, 0, 0, 4), "smoke intersects ray");
        check(!TanksBallistics.smokeCrosses(0, 8, 0, 20, 8, 0, 10, 0, 0, 4), "smoke misses ray");
        check(
                Double.isInfinite(
                        TanksBallistics.intersection(
                                0, 2.8, -20, 0, 2.8, 20, 0, 0, 0, 0, TanksRules.Chassis.HEAVY, 0)),
                "no invisible armor above turret");
        check(
                Double.isInfinite(
                        TanksBallistics.intersection(
                                1.5,
                                .5,
                                -20,
                                1.5,
                                .5,
                                20,
                                0,
                                0,
                                0,
                                0,
                                TanksRules.Chassis.LIGHT,
                                0)),
                "light hull uses actual smaller silhouette");
        check(
                TanksBallistics.intersection(
                                1.5, .5, -20, 1.5, .5, 20, 0, 0, 0, 0, TanksRules.Chassis.HEAVY, 0)
                        < .5,
                "heavy hull is wider");
        check(
                Double.isInfinite(
                        TanksBallistics.intersection(
                                1.4,
                                1.8,
                                -20,
                                1.4,
                                1.8,
                                20,
                                0,
                                0,
                                0,
                                0,
                                TanksRules.Chassis.HEAVY,
                                0)),
                "air beside turret is a miss");
        var aim = TanksBallistics.aim(0, 1.5, 3, 0, 1.8, 12);
        check(Math.abs(aim.yaw()) < .001 && aim.pitch() < 0, "gun compensates close target height");
        var point = TanksBallistics.aim(0, 2, 0, -30, 2, 0);
        check(Math.abs(point.yaw() - 90) < .001, "aim follows Minecraft yaw convention");
        check(
                !TanksBallistics.visibleToTeam(0, false, 1, true, 0, 10),
                "unspotted enemy stays hidden for same-team dead observers");
        check(TanksBallistics.visibleToTeam(0, false, 0, true, 0, 10), "allies remain visible");
        check(
                TanksBallistics.visibleToTeam(0, true, 1, true, 0, 10),
                "external observer can see battle");
        check(
                !TanksBallistics.overlap(
                        0, 0, 0, TanksRules.Chassis.LIGHT, 3, 0, 0, TanksRules.Chassis.LIGHT),
                "light tanks can pass side by side without invisible gap");
        check(
                TanksBallistics.overlap(
                        0, 0, 0, TanksRules.Chassis.HEAVY, 3, 0, 0, TanksRules.Chassis.HEAVY),
                "heavy tracks cannot overlap");
        check(
                !TanksBallistics.overlap(
                        0, 0, 0, TanksRules.Chassis.HEAVY, 3.5, 0, 0, TanksRules.Chassis.HEAVY),
                "heavy tracks pass with visible gap");
        check(
                TanksBallistics.overlap(
                        0, 0, 90, TanksRules.Chassis.HEAVY, 4, 0, 90, TanksRules.Chassis.HEAVY),
                "rotated hull length is respected");
        check(
                Double.isInfinite(
                        TanksBallistics.hullIntersection(
                                0, 1.56, 0, 0, 1.56, 3.8, 0, 0, 0, 0, TanksRules.Chassis.HEAVY)),
                "level gun clears own hull");
        check(
                Double.isFinite(
                        TanksBallistics.hullIntersection(
                                0, 1.56, 0, 0, .64, 3.7, 0, 0, 0, 0, TanksRules.Chassis.HEAVY)),
                "depressed gun cannot fire through own deck");
        for (double range : new double[] {12, 50, 70, 100, 130}) {
            var solution = TanksBallistics.ballisticAim(0, 1.5, 0, 0, 1.5, range);
            double pitch = Math.toRadians(solution.pitch()),
                    vx = Math.cos(pitch) * TanksBallistics.SHELL_SPEED;
            double vy = -Math.sin(pitch) * TanksBallistics.SHELL_SPEED, y = 1.5, z = 0;
            for (int step = 0; step < 65; step++) {
                if (z + vx >= range) {
                    y += vy * (range - z) / vx;
                    break;
                }
                z += vx;
                y += vy;
                vy -= TanksBallistics.GRAVITY;
            }
            check(
                    Math.abs(y - 1.5) < .025,
                    "ballistic crosshair converges at " + range + " blocks");
        }
        var turretHit =
                TanksBallistics.armorHit(
                        TanksRules.Chassis.LIGHT,
                        TanksRules.Shell.AP,
                        TanksRules.Chassis.HEAVY,
                        0,
                        90,
                        -1,
                        1.8,
                        0,
                        1,
                        0);
        var hullHit =
                TanksBallistics.armorHit(
                        TanksRules.Chassis.LIGHT,
                        TanksRules.Shell.AP,
                        TanksRules.Chassis.HEAVY,
                        0,
                        90,
                        -1,
                        .8,
                        0,
                        1,
                        0);
        check(
                !turretHit.penetrated() && turretHit.side() == TanksRules.ArmorSide.FRONT,
                "rotated turret uses its own front plate");
        check(
                hullHit.penetrated() && hullHit.side() == TanksRules.ArmorSide.SIDE,
                "hull side remains independent of turret");
        System.out.println("TanksBallisticsCheck: 31 checks passed");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
