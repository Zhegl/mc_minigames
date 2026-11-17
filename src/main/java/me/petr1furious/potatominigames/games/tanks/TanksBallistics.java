package me.petr1furious.potatominigames.games.tanks;

/** Pure continuous collision against the same conservative footprint as the vehicle controller. */
final class TanksBallistics {
    static final double SHELL_SPEED = 2.6, GRAVITY = .012;
    static final int RETICLE_POINTS = 16;
    static final double GAUSSIAN_SIGMA = .45, CONFIDENCE_95 = Math.sqrt(-2 * Math.log(.05));

    record Direction(double x, double y, double z) {}

    record Point(double x, double y, double z) {}

    static Point markerPlane(Point muzzle, Point impact, Direction normal) {
        double forward =
                (impact.x - muzzle.x) * normal.x
                        + (impact.y - muzzle.y) * normal.y
                        + (impact.z - muzzle.z) * normal.z;
        double offset = Math.min(.06, Math.max(0, forward) * .5);
        return new Point(
                impact.x - normal.x * offset,
                impact.y - normal.y * offset,
                impact.z - normal.z * offset);
    }

    /** Radial95% contour for the existing pair of independent Gaussian angular errors. */
    static double confidence95Angle(double spread) {
        return spread * GAUSSIAN_SIGMA * CONFIDENCE_95;
    }

    /** Keep the original two Gaussian draws, .45 multiplier and float-angle rounding exactly. */
    static Direction spreadDirection(
            float yaw, float pitch, double spread, double gaussianYaw, double gaussianPitch) {
        float shotYaw = yaw + (float) (gaussianYaw * spread * GAUSSIAN_SIGMA);
        float shotPitch = pitch + (float) (gaussianPitch * spread * GAUSSIAN_SIGMA);
        double yr = Math.toRadians(shotYaw),
                pr = Math.toRadians(shotPitch),
                horizontal = Math.cos(pr);
        return new Direction(-horizontal * Math.sin(yr), -Math.sin(pr), horizontal * Math.cos(yr));
    }

    /** Projects one actual tick-integrated trajectory onto the marker's impact plane. */
    static Point projectShot(Point start, Direction initial, Point plane, Direction normal) {
        double x = start.x, y = start.y, z = start.z;
        double vx = initial.x * SHELL_SPEED,
                vy = initial.y * SHELL_SPEED,
                vz = initial.z * SHELL_SPEED;
        double side =
                (x - plane.x) * normal.x + (y - plane.y) * normal.y + (z - plane.z) * normal.z;
        if (Math.abs(side) < 1e-10) return start;
        for (int age = 0; age < 65; age++) {
            double next = side + vx * normal.x + vy * normal.y + vz * normal.z;
            if ((side < 0 && next >= 0) || (side > 0 && next <= 0)) {
                double t = side / (side - next);
                return new Point(x + vx * t, y + vy * t, z + vz * t);
            }
            x += vx;
            y += vy;
            z += vz;
            vy -= GRAVITY;
            side = next;
        }
        return null;
    }

    /**
     * Pure projection: fixed-size private reticle, no radius cap, terrain reads or hit sampling.
     */
    static Point[] dispersionContour(
            Point start, float yaw, float pitch, double spread, Point plane, Direction normal) {
        Point[] contour = new Point[RETICLE_POINTS];
        for (int i = 0; i < contour.length; i++) {
            double angle = i * Math.PI * 2 / contour.length;
            Direction direction =
                    spreadDirection(
                            yaw,
                            pitch,
                            spread,
                            CONFIDENCE_95 * Math.cos(angle),
                            CONFIDENCE_95 * Math.sin(angle));
            contour[i] = projectShot(start, direction, plane, normal);
        }
        return contour;
    }

    private TanksBallistics() {}

    static double intersection(
            double ax,
            double ay,
            double az,
            double bx,
            double by,
            double bz,
            double x,
            double y,
            double z,
            double yaw) {
        return box(ax, ay, az, bx, by, bz, x, y, z, yaw, 1.65, 2.2, 0, 3);
    }

    static double intersection(
            double ax,
            double ay,
            double az,
            double bx,
            double by,
            double bz,
            double x,
            double y,
            double z,
            double hullYaw,
            TanksRules.Chassis chassis,
            double turretYaw) {
        var g = TanksRules.geometry(chassis);
        return Math.min(
                box(
                        ax,
                        ay,
                        az,
                        bx,
                        by,
                        bz,
                        x,
                        y,
                        z,
                        hullYaw,
                        g.hullHalfWidth(),
                        g.hullHalfLength(),
                        0,
                        g.hullHeight()),
                box(
                        ax,
                        ay,
                        az,
                        bx,
                        by,
                        bz,
                        x,
                        y,
                        z,
                        turretYaw,
                        g.turretHalfWidth(),
                        g.turretHalfLength(),
                        g.turretBottom(),
                        g.turretTop()));
    }

    private static double box(
            double ax,
            double ay,
            double az,
            double bx,
            double by,
            double bz,
            double x,
            double y,
            double z,
            double yaw,
            double halfWidth,
            double halfLength,
            double minY,
            double maxY) {
        double r = Math.toRadians(yaw), c = Math.cos(r), s = Math.sin(r);
        double dx = ax - x, dz = az - z, ex = bx - x, ez = bz - z;
        double[] a = {dx * c + dz * s, ay - y, -dx * s + dz * c};
        double[] b = {ex * c + ez * s, by - y, -ex * s + ez * c};
        double[] min = {-halfWidth, minY, -halfLength}, max = {halfWidth, maxY, halfLength};
        double enter = 0, exit = 1;
        for (int i = 0; i < 3; i++) {
            double d = b[i] - a[i];
            if (Math.abs(d) < 1e-9) {
                if (a[i] < min[i] || a[i] > max[i]) return Double.POSITIVE_INFINITY;
            } else {
                double t = (min[i] - a[i]) / d, u = (max[i] - a[i]) / d;
                enter = Math.max(enter, Math.min(t, u));
                exit = Math.min(exit, Math.max(t, u));
                if (enter > exit) return Double.POSITIVE_INFINITY;
            }
        }
        return enter;
    }

    record Aim(double yaw, double pitch) {}

    static Aim aim(double sx, double sy, double sz, double tx, double ty, double tz) {
        double dx = tx - sx, dy = ty - sy, dz = tz - sz;
        return new Aim(
                Math.toDegrees(Math.atan2(-dx, dz)),
                -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }

    /** Solves the low arc against the exact piecewise-linear, tick-based gravity integrator. */
    static Aim ballisticAim(double sx, double sy, double sz, double tx, double ty, double tz) {
        Aim straight = aim(sx, sy, sz, tx, ty, tz);
        double range = Math.hypot(tx - sx, tz - sz), dy = ty - sy;
        if (range < 1e-7) return straight;
        double low = 0;
        for (double high = .25; high <= 65; high += .25) {
            if (flightEquation(high, range, dy) <= 0) {
                for (int i = 0; i < 45; i++) {
                    double mid = (low + high) / 2;
                    if (flightEquation(mid, range, dy) > 0) low = mid;
                    else high = mid;
                }
                double time = (low + high) / 2;
                return new Aim(straight.yaw(), -Math.toDegrees(Math.atan2(dy + drop(time), range)));
            }
            low = high;
        }
        return straight;
    }

    private static double drop(double time) {
        double whole = Math.floor(time), part = time - whole;
        return GRAVITY * (whole * (whole - 1) / 2 + whole * part);
    }

    private static double flightEquation(double time, double range, double dy) {
        double vertical = dy + drop(time);
        return range * range + vertical * vertical - SHELL_SPEED * SHELL_SPEED * time * time;
    }

    static boolean visibleToTeam(
            int viewerTeam,
            boolean external,
            int targetTeam,
            boolean targetAlive,
            long spottedUntil,
            long now) {
        return external || viewerTeam == targetTeam || !targetAlive || spottedUntil >= now;
    }

    static double hullIntersection(
            double ax,
            double ay,
            double az,
            double bx,
            double by,
            double bz,
            double x,
            double y,
            double z,
            double yaw,
            TanksRules.Chassis chassis) {
        var g = TanksRules.geometry(chassis);
        return box(
                ax,
                ay,
                az,
                bx,
                by,
                bz,
                x,
                y,
                z,
                yaw,
                g.hullHalfWidth(),
                g.hullHalfLength(),
                0,
                g.hullHeight());
    }

    /** Four-axis separating test; visible track footprints determine vehicle spacing. */
    static boolean overlap(
            double ax,
            double az,
            double ayaw,
            TanksRules.Chassis ac,
            double bx,
            double bz,
            double byaw,
            TanksRules.Chassis bc) {
        var a = TanksRules.geometry(ac);
        var b = TanksRules.geometry(bc);
        double ar = Math.toRadians(ayaw), br = Math.toRadians(byaw);
        double[][] aa = {{Math.cos(ar), Math.sin(ar)}, {-Math.sin(ar), Math.cos(ar)}};
        double[][] ba = {{Math.cos(br), Math.sin(br)}, {-Math.sin(br), Math.cos(br)}};
        for (double[] axis : new double[][] {aa[0], aa[1], ba[0], ba[1]}) {
            double separation = Math.abs((bx - ax) * axis[0] + (bz - az) * axis[1]);
            double ra =
                    a.hullHalfWidth() * dotAbs(axis, aa[0])
                            + a.hullHalfLength() * dotAbs(axis, aa[1]);
            double rb =
                    b.hullHalfWidth() * dotAbs(axis, ba[0])
                            + b.hullHalfLength() * dotAbs(axis, ba[1]);
            if (separation >= ra + rb + .025) return false;
        }
        return true;
    }

    private static double dotAbs(double[] a, double[] b) {
        return Math.abs(a[0] * b[0] + a[1] * b[1]);
    }

    /** Projects the two belts and their wheel faces onto the chassis collision silhouette. */
    static boolean treadHit(TanksRules.Chassis chassis, double yaw, double x, double y, double z) {
        double scale = TanksRules.geometry(chassis).scale();
        double angle = Math.toRadians(yaw), c = Math.cos(angle), s = Math.sin(angle);
        double side = Math.abs(x * c + z * s) / scale;
        double length = Math.abs(-x * s + z * c) / scale;
        double height = y / scale;
        // TankVisual's .62-wide belts are centered at ±1.25. The beveled ends are narrower
        // than the wheel faces, which reach the side of the conservative hull hitbox.
        if (side < .94 || side > 1.65 + 1e-6 || length > 2.2 + 1e-6) return false;
        if (length > 1.825) return side <= 1.56 && height >= .17 && height <= .67;
        return height >= .05 && height <= .81;
    }

    static TanksRules.Hit armorHit(
            TanksRules.Chassis shooter,
            TanksRules.Shell shell,
            TanksRules.Chassis target,
            double hullYaw,
            double turretYaw,
            double x,
            double y,
            double z,
            double shotX,
            double shotZ) {
        var g = TanksRules.geometry(target);
        boolean turret = y > g.hullHeight() + 1e-5;
        return TanksRules.hit(
                shooter,
                shell,
                target,
                turret ? turretYaw : hullYaw,
                x,
                z,
                shotX,
                shotZ,
                turret ? g.turretHalfWidth() : g.hullHalfWidth(),
                turret ? g.turretHalfLength() : g.hullHalfLength());
    }

    static boolean smokeCrosses(
            double ax,
            double ay,
            double az,
            double bx,
            double by,
            double bz,
            double x,
            double y,
            double z,
            double radius) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = dx * dx + dy * dy + dz * dz;
        double t =
                len < 1e-9
                        ? 0
                        : Math.clamp(((x - ax) * dx + (y - ay) * dy + (z - az) * dz) / len, 0, 1);
        double ex = ax + t * dx - x, ey = ay + t * dy - y, ez = az + t * dz - z;
        return ex * ex + ey * ey + ez * ez <= radius * radius;
    }
}
