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
        dispersionGeometry();
        nearContactMarker();
        System.out.println(
                "TanksBallisticsCheck: collision, aim and sampled95% dispersion checks passed");
    }

    private static void nearContactMarker() {
        var start = new TanksBallistics.Point(8, 66.42, -12);
        for (float yaw : new float[] {0, 125})
            for (float pitch : new float[] {0, -14}) {
                var normal = TanksBallistics.spreadDirection(yaw, pitch, 0, 0, 0);
                for (double gap : new double[] {0, .001, .01, .02, .05, .06, .1, .12, .5}) {
                    var impact =
                            new TanksBallistics.Point(
                                    start.x() + normal.x() * gap,
                                    start.y() + normal.y() * gap,
                                    start.z() + normal.z() * gap);
                    var plane = TanksBallistics.markerPlane(start, impact, normal);
                    double forward =
                            (plane.x() - start.x()) * normal.x()
                                    + (plane.y() - start.y()) * normal.y()
                                    + (plane.z() - start.z()) * normal.z();
                    check(
                            forward >= gap * .5 - 1e-9,
                            "near-contact reticle plane must not fall behind the muzzle");
                    var contour =
                            TanksBallistics.dispersionContour(
                                    start, yaw, pitch, 1.5, plane, normal);
                    check(
                            java.util.Arrays.stream(contour).allMatch(java.util.Objects::nonNull),
                            "all16 reticle points remain projectable at a near-contact wall");
                    if (gap >= .12)
                        check(
                                Math.abs(distance(plane, impact) - .06) < 1e-9,
                                "normal-distance impact markers keep their existing six-centimeter"
                                    + " offset");
                }
            }
    }

    private static void dispersionGeometry() {
        var start = new TanksBallistics.Point(0, 0, 0);
        var plane = new TanksBallistics.Point(0, 0, 40);
        var normal = new TanksBallistics.Direction(0, 0, 1);
        var heavy = TanksBallistics.dispersionContour(start, 0, 0, 4.65, plane, normal);
        double extent =
                java.util.Arrays.stream(heavy)
                        .mapToDouble(p -> Math.hypot(p.x(), p.y()))
                        .max()
                        .orElseThrow();
        check(
                extent > 3.25,
                "moving heavy dispersion must not be hidden by a two-block marker cap");
        for (var chassis : TanksRules.Chassis.values())
            for (double range : new double[] {12, 40, 70, 130}) {
                var solution = TanksBallistics.ballisticAim(0, 0, 0, 0, 0, range);
                float pitch = (float) solution.pitch();
                for (int mode = 0; mode < 4; mode++) {
                    double speed =
                            mode == 0 ? 0 : mode == 3 ? chassis.speed() * .5 : chassis.speed();
                    double lag = mode >= 2 ? 35 : 0;
                    double spread = TanksRules.dispersion(chassis, speed, lag, mode == 0 ? 100 : 0);
                    plane = new TanksBallistics.Point(0, 0, range);
                    var contour =
                            TanksBallistics.dispersionContour(
                                    start, 0, pitch, spread, plane, normal);
                    check(
                            contour.length == TanksBallistics.RETICLE_POINTS,
                            "reticle has a fixed particle budget");
                    for (int i = 0; i < contour.length; i++) {
                        double angle = i * Math.PI * 2 / contour.length;
                        var direction =
                                TanksBallistics.spreadDirection(
                                        0,
                                        pitch,
                                        spread,
                                        TanksBallistics.CONFIDENCE_95 * Math.cos(angle),
                                        TanksBallistics.CONFIDENCE_95 * Math.sin(angle));
                        var actual = referenceFlight(direction, range);
                        check(
                                distance(contour[i], actual) < 1e-5,
                                "reticle boundary matches actual gravity flight at "
                                        + chassis
                                        + "/"
                                        + range
                                        + "/"
                                        + mode);
                    }
                    java.util.Random random = new java.util.Random(773);
                    int inside = 0, covered = 0, samples = 6000;
                    for (int sample = 0; sample < samples; sample++) {
                        double gy = random.nextGaussian(), gp = random.nextGaussian();
                        var direction = TanksBallistics.spreadDirection(0, pitch, spread, gy, gp);
                        var actual = referenceFlight(direction, range);
                        var projected =
                                TanksBallistics.projectShot(start, direction, plane, normal);
                        check(
                                distance(actual, projected) < 1e-6,
                                "projection follows actual sampled shot segments");
                        if (gy * gy + gp * gp
                                <= TanksBallistics.CONFIDENCE_95 * TanksBallistics.CONFIDENCE_95)
                            inside++;
                        if (insidePolygon(actual, contour)) covered++;
                        float oldYaw = (float) (gy * spread * .45),
                                oldPitch = pitch + (float) (gp * spread * .45);
                        double yr = Math.toRadians(oldYaw),
                                pr = Math.toRadians(oldPitch),
                                horizontal = Math.cos(pr);
                        check(
                                direction.x() == -horizontal * Math.sin(yr)
                                        && direction.y() == -Math.sin(pr)
                                        && direction.z() == horizontal * Math.cos(yr),
                                "Gaussian firing distribution and float precision remain"
                                        + " unchanged");
                    }
                    check(
                            Math.abs(inside / (double) samples - .95) < .01,
                            "seeded Gaussian confidence is95%");
                    check(
                            Math.abs(covered / (double) samples - .95) < .015,
                            "projected16-point contour covers approximately95% of actual shots");
                }
            }
        var angled = TanksBallistics.spreadDirection(125, -14, 0, 0, 0);
        var tiltedPlane =
                new TanksBallistics.Point(angled.x() * 70, angled.y() * 70 - 4, angled.z() * 70);
        var incoming = new TanksBallistics.Direction(angled.x(), angled.y() - .25, angled.z());
        var tilted = TanksBallistics.dispersionContour(start, 125, -14, 5.2, tiltedPlane, incoming);
        for (var p : tilted)
            check(
                    p != null
                            && Math.abs(
                                            (p.x() - tiltedPlane.x()) * incoming.x()
                                                    + (p.y() - tiltedPlane.y()) * incoming.y()
                                                    + (p.z() - tiltedPlane.z()) * incoming.z())
                                    < 1e-6,
                    "oblique incoming impact plane projection remains coherent");
    }

    private static TanksBallistics.Point referenceFlight(
            TanksBallistics.Direction direction, double range) {
        double x = 0,
                y = 0,
                z = 0,
                vx = direction.x() * TanksBallistics.SHELL_SPEED,
                vy = direction.y() * TanksBallistics.SHELL_SPEED,
                vz = direction.z() * TanksBallistics.SHELL_SPEED;
        for (int age = 0; age < 65; age++) {
            if (z + vz >= range) {
                double t = (range - z) / vz;
                return new TanksBallistics.Point(x + vx * t, y + vy * t, range);
            }
            x += vx;
            y += vy;
            z += vz;
            vy -= TanksBallistics.GRAVITY;
        }
        throw new AssertionError("sample did not reach target plane");
    }

    private static double distance(TanksBallistics.Point a, TanksBallistics.Point b) {
        check(a != null && b != null, "trajectory reaches the reticle plane");
        return Math.sqrt(
                Math.pow(a.x() - b.x(), 2)
                        + Math.pow(a.y() - b.y(), 2)
                        + Math.pow(a.z() - b.z(), 2));
    }

    private static boolean insidePolygon(
            TanksBallistics.Point point, TanksBallistics.Point[] polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            var a = polygon[i];
            var b = polygon[j];
            if ((a.y() > point.y()) != (b.y() > point.y())
                    && point.x() < (b.x() - a.x()) * (point.y() - a.y()) / (b.y() - a.y()) + a.x())
                inside = !inside;
        }
        return inside;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
