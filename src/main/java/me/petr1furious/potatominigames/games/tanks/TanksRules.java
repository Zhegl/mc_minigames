package me.petr1furious.potatominigames.games.tanks;

import java.util.Objects;

/** Deterministic combat and steering rules; distances are blocks and time is server ticks. */
public final class TanksRules {
    public static final double HALF_WIDTH = 1.65, HALF_LENGTH = 2.2;

    private TanksRules() {}

    /** The rendered armor silhouette, shared with projectile collision; barrel is decorative. */
    public record ModelGeometry(
            double scale,
            double hullHalfWidth,
            double hullHeight,
            double hullHalfLength,
            double turretHalfWidth,
            double turretBottom,
            double turretTop,
            double turretHalfLength,
            double turretPivotY) {}

    public static ModelGeometry geometry(Chassis chassis) {
        double scale =
                switch (Objects.requireNonNull(chassis, "chassis")) {
                    case LIGHT -> .82;
                    case MEDIUM -> .92;
                    case HEAVY -> 1;
                };
        double pivot =
                switch (chassis) {
                    case LIGHT -> 1.22;
                    case MEDIUM -> 1.42;
                    case HEAVY -> 1.56;
                };
        return new ModelGeometry(
                scale,
                1.65 * scale,
                1.32 * scale,
                2.2 * scale,
                scale,
                pivot - .21 * scale,
                pivot + .68 * scale,
                1.05 * scale,
                pivot);
    }

    public enum Chassis {
        LIGHT(800, .24, 62, 150, 160, 85, 55, 35, 4.5, 2.8),
        MEDIUM(1100, .19, 84, 190, 220, 125, 85, 55, 3.5, 2.1),
        HEAVY(1500, .14, 112, 235, 300, 180, 115, 75, 2.5, 1.55);
        private final double maxHp, speed, penetration, damage, frontArmor, sideArmor, rearArmor;
        private final double turretDegreesPerTick, hullDegreesPerTick;
        private final int reloadTicks;

        Chassis(
                double hp,
                double speed,
                int reload,
                double penetration,
                double damage,
                double front,
                double side,
                double rear,
                double turretTurn,
                double hullTurn) {
            this.maxHp = hp;
            this.speed = speed;
            this.reloadTicks = reload;
            this.penetration = penetration;
            this.damage = damage;
            this.frontArmor = front;
            this.sideArmor = side;
            this.rearArmor = rear;
            this.turretDegreesPerTick = turretTurn;
            this.hullDegreesPerTick = hullTurn;
        }

        public double maxHp() {
            return maxHp;
        }

        public double speed() {
            return speed;
        }

        public int reloadTicks() {
            return reloadTicks;
        }

        public double penetration() {
            return penetration;
        }

        public double damage() {
            return damage;
        }

        public double frontArmor() {
            return frontArmor;
        }

        public double sideArmor() {
            return sideArmor;
        }

        public double rearArmor() {
            return rearArmor;
        }

        public double turretDegreesPerTick() {
            return turretDegreesPerTick;
        }

        public double hullDegreesPerTick() {
            return hullDegreesPerTick;
        }

        public double armor(ArmorSide side) {
            return switch (side) {
                case FRONT -> frontArmor;
                case SIDE -> sideArmor;
                case REAR -> rearArmor;
            };
        }
    }

    public enum Shell {
        AP,
        HE
    }

    public enum ArmorSide {
        FRONT,
        SIDE,
        REAR
    }

    /** Armor is the effective plate thickness after accounting for obliquity. */
    public record Hit(
            double damage, boolean penetrated, boolean ricochet, double armor, ArmorSide side) {
        public Hit {
            finite(damage, armor);
            if (damage < 0 || armor < 0) throw new IllegalArgumentException("Negative hit value");
            Objects.requireNonNull(side, "side");
        }
    }

    /** Minecraft yaw: zero faces +Z, positive yaw rotates toward -X. */
    public static ArmorSide armorSide(double hullYaw, double hitOffsetX, double hitOffsetZ) {
        return armorSide(hullYaw, hitOffsetX, hitOffsetZ, HALF_WIDTH, HALF_LENGTH);
    }

    private static ArmorSide armorSide(
            double hullYaw,
            double hitOffsetX,
            double hitOffsetZ,
            double halfWidth,
            double halfLength) {
        finite(hullYaw, hitOffsetX, hitOffsetZ);
        double angle = Math.toRadians(normalizeYaw(hullYaw));
        double right = hitOffsetX * Math.cos(angle) + hitOffsetZ * Math.sin(angle);
        double forward = -hitOffsetX * Math.sin(angle) + hitOffsetZ * Math.cos(angle);
        return Math.abs(forward) / halfLength >= Math.abs(right) / halfWidth
                ? (forward >= 0 ? ArmorSide.FRONT : ArmorSide.REAR)
                : ArmorSide.SIDE;
    }

    /** Hit offset is relative to the target center; shot direction points along shell travel. */
    public static Hit hit(
            Chassis shooter,
            Shell shell,
            Chassis target,
            double targetHullYaw,
            double hitOffsetX,
            double hitOffsetZ,
            double shotDirectionX,
            double shotDirectionZ) {
        return hit(
                shooter,
                shell,
                target,
                targetHullYaw,
                hitOffsetX,
                hitOffsetZ,
                shotDirectionX,
                shotDirectionZ,
                HALF_WIDTH,
                HALF_LENGTH);
    }

    static Hit hit(
            Chassis shooter,
            Shell shell,
            Chassis target,
            double targetHullYaw,
            double hitOffsetX,
            double hitOffsetZ,
            double shotDirectionX,
            double shotDirectionZ,
            double halfWidth,
            double halfLength) {
        Objects.requireNonNull(shooter, "shooter");
        Objects.requireNonNull(shell, "shell");
        Objects.requireNonNull(target, "target");
        finite(targetHullYaw, hitOffsetX, hitOffsetZ, shotDirectionX, shotDirectionZ);
        double length = Math.hypot(shotDirectionX, shotDirectionZ);
        if (length < 1e-9) throw new IllegalArgumentException("Shell direction is zero");
        double angle = Math.toRadians(normalizeYaw(targetHullYaw));
        double right = hitOffsetX * Math.cos(angle) + hitOffsetZ * Math.sin(angle);
        ArmorSide side = armorSide(targetHullYaw, hitOffsetX, hitOffsetZ, halfWidth, halfLength);
        double nx, nz;
        if (side == ArmorSide.SIDE) {
            nx = Math.cos(angle) * Math.copySign(1, right);
            nz = Math.sin(angle) * Math.copySign(1, right);
        } else {
            double sign = side == ArmorSide.FRONT ? 1 : -1;
            nx = -Math.sin(angle) * sign;
            nz = Math.cos(angle) * sign;
        }
        double cosine = Math.min(1, Math.abs((shotDirectionX * nx + shotDirectionZ * nz) / length));
        double incidence = Math.toDegrees(Math.acos(cosine));
        double armor = effectiveArmor(target.armor(side), incidence);
        if (shell == Shell.AP) {
            boolean ricochet = incidence >= 70;
            boolean penetrated = !ricochet && shooter.penetration() >= armor;
            return new Hit(penetrated ? shooter.damage() : 0, penetrated, ricochet, armor, side);
        }
        boolean penetrated = shooter.penetration() * .45 >= armor;
        double damage =
                penetrated
                        ? shooter.damage() * 1.1
                        : Math.max(shooter.damage() * .04, shooter.damage() * .5 - armor * .28);
        return new Hit(damage, penetrated, false, armor, side);
    }

    public static double effectiveArmor(double nominal, double incidenceDegrees) {
        finite(nominal, incidenceDegrees);
        if (nominal < 0) throw new IllegalArgumentException("Negative armor");
        return nominal
                / Math.max(.1, Math.cos(Math.toRadians(Math.min(90, Math.abs(incidenceDegrees)))));
    }

    public static double normalizeYaw(double yaw) {
        finite(yaw);
        double result = yaw % 360;
        if (result >= 180) result -= 360;
        if (result < -180) result += 360;
        return result;
    }

    public static double angleDelta(double current, double target) {
        return normalizeYaw(normalizeYaw(target) - normalizeYaw(current));
    }

    public static double stepYaw(double current, double target, double maxDegrees) {
        finite(maxDegrees);
        if (maxDegrees < 0) throw new IllegalArgumentException("Negative angular rate");
        return normalizeYaw(current + clamp(angleDelta(current, target), -maxDegrees, maxDegrees));
    }

    public static double steerYaw(double current, double steering, Chassis chassis) {
        finite(steering);
        return normalizeYaw(current + clamp(steering, -1, 1) * chassis.hullDegreesPerTick());
    }

    /** Bounded throttle and friction prevent instantaneous reversing and runaway speeds. */
    public static double accelerate(double current, double throttle, double maxSpeed) {
        finite(current, throttle, maxSpeed);
        if (maxSpeed <= 0) throw new IllegalArgumentException("Nonpositive top speed");
        throttle = clamp(throttle, -1, 1);
        double target = throttle * maxSpeed * (throttle < 0 ? .55 : 1);
        double acceleration = throttle == 0 ? .018 : .012;
        return clamp(
                current + clamp(target - current, -acceleration, acceleration),
                -maxSpeed * .55,
                maxSpeed);
    }

    /** Aiming cone half-angle in degrees; motion and a lagging turret widen the cone. */
    public static double dispersion(
            Chassis chassis, double speed, double turretErrorDegrees, int stationaryTicks) {
        Objects.requireNonNull(chassis, "chassis");
        finite(speed, turretErrorDegrees);
        double settling = 1 - clamp(stationaryTicks / 40.0, 0, 1);
        return .45
                + settling * 1.4
                + clamp(Math.abs(speed) / chassis.speed(), 0, 1) * 2.5
                + Math.min(3, Math.abs(turretErrorDegrees) / 20);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void finite(double... values) {
        for (double value : values)
            if (!Double.isFinite(value))
                throw new IllegalArgumentException("Non-finite simulation value");
    }
}
