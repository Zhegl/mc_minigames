package me.petr1furious.potatominigames.games.tanks;

import java.util.UUID;
import java.util.Locale;

/** Server-free checks for armor geometry, controls and one-life battle state. */
public final class TanksRulesCheck {
    private static final UUID RED = new UUID(0, 1), BLUE = new UUID(0, 2), ALLY = new UUID(0, 3);
    private static int checks;

    public static void main(String[] args) {
        classRoles();
        movingGunHandling();
        classMobility();
        armorAndShells();
        steeringAndClasses();
        reloadAndConsumables();
        trackRecovery();
        capture();
        lifecycleAndRewards();
        spottingAssists();
        invalidInputs();
        System.out.println("TanksRulesCheck: " + checks + " checks passed");
        if (java.util.Arrays.asList(args).contains("--balance")) balanceReport();
    }

    /** Ideal loaded-first-shot encounters are balance bounds, not predicted human win rates. */
    private static void classRoles() {
        var light = TanksRules.Chassis.LIGHT;
        var medium = TanksRules.Chassis.MEDIUM;
        var heavy = TanksRules.Chassis.HEAVY;
        var front = TanksRules.ArmorSide.FRONT;
        var side = TanksRules.ArmorSide.SIDE;
        var rear = TanksRules.ArmorSide.REAR;
        double lightFlank = timeToKill(light, TanksRules.Shell.AP, heavy, side);
        check(lightFlank >= 15 && lightFlank <= 20,
                "a clean light flank has a usable 15–20 second damage window");
        check(timeToKill(light, TanksRules.Shell.HE, heavy, rear) < lightFlank,
                "light rewards a square rear ambush with a useful HE ammunition choice");
        check(plateHit(light, TanksRules.Shell.HE, heavy, rear, 0).penetrated(),
                "light HE can punish the heavy rear rather than always chipping armor");
        double heavyCounter = bestTimeToKill(heavy, light, front);
        check(lightFlank < heavyCounter,
                "a light that earns uninterrupted side shots can beat a heavy damage trade");
        check(plateHit(light, TanksRules.Shell.AP, heavy, front, 0).damage() == 0
                        && bestTimeToKill(light, heavy, front) > heavyCounter * 3,
                "heavy frontal armor still wins decisively against a careless light head-on");
        check(plateHit(light, TanksRules.Shell.AP, heavy, side, 45).penetrated(),
                "a modestly angled heavy flank remains a usable light AP target");
        var mediumHe = plateHit(medium, TanksRules.Shell.HE, light, front, 0);
        check(mediumHe.penetrated()
                        && mediumHe.damage() > plateHit(medium, TanksRules.Shell.AP, light, front, 0).damage()
                        && timeToKill(medium, TanksRules.Shell.HE, light, front)
                                < bestTimeToKill(light, medium, side),
                "medium HE remains a useful answer to the fast lightly armored class");
        check(plateHit(medium, TanksRules.Shell.HE, light, front, 30).penetrated()
                        && !plateHit(medium, TanksRules.Shell.HE, light, front, 40).penetrated()
                        && plateHit(medium, TanksRules.Shell.AP, light, front, 40).damage()
                                > plateHit(medium, TanksRules.Shell.HE, light, front, 40).damage(),
                "medium chooses HE against square light armor and AP against a glancing front");
        check(bestTimeToKill(heavy, medium, front) < bestTimeToKill(medium, heavy, front),
                "heavy retains its ideal flat-front trade advantage over medium");
        check(plateHit(medium, TanksRules.Shell.AP, heavy, front, 25).penetrated(),
                "medium AP has counterplay against heavy fronts angled by 25 degrees");
        check(!plateHit(medium, TanksRules.Shell.AP, heavy, front, 35).penetrated(),
                "a deliberately angled heavy can still deny medium frontal AP");
        check(timeToKill(light, TanksRules.Shell.AP, heavy, side, heavy.maxHp() * 1.15) <= 20,
                "one fully used heavy repair kit cannot erase the light's twenty-second flank window");
        check(timeToKill(light, TanksRules.Shell.HE, heavy, rear, heavy.maxHp() * 1.15)
                        < Math.min(timeToKill(heavy, TanksRules.Shell.AP, light, front, light.maxHp() * 1.15),
                                timeToKill(heavy, TanksRules.Shell.HE, light, front, light.maxHp() * 1.15)),
                "light rear-HE agency survives both opponents optimally spending one repair kit");
        var match = battle(1000, 30, false);
        var victim = match.actor(RED);
        var heavyHe = plateHit(heavy, TanksRules.Shell.HE, light, front, 0);
        for (int i = 0; i < 3; i++) match.recordHit(BLUE, RED, heavyHe, 0);
        check(victim.alive(), "three heavy HE hits cannot erase a fresh light before it can disengage");
        for (var shooter : TanksRules.Chassis.values()) for (var target : TanksRules.Chassis.values()) {
            check(bestTimeToKill(shooter, target, rear) <= bestTimeToKill(shooter, target, front),
                    "earning the rear never makes a matchup worse: " + shooter + " to " + target);
        }
    }

    private static void spottingAssists() {
        var match = new TanksMatch(1000, 30, false);
        var shooter = match.add(RED, 0, TanksRules.Chassis.HEAVY);
        var first = match.add(ALLY, 0, TanksRules.Chassis.LIGHT);
        var second = match.add(new UUID(0, 4), 0, TanksRules.Chassis.MEDIUM);
        var enemy = match.add(BLUE, 1, TanksRules.Chassis.HEAVY);
        var unseen = match.add(new UUID(0, 5), 1, TanksRules.Chassis.MEDIUM);
        match.start();

        var blind = match.spottersFor(shooter, 5, t -> false);
        enemy.spottedBy(first, 10);
        blind.credit(BLUE, 20);
        check(first.spottingDamage() == 0, "spotting during shell flight cannot credit blind fire");
        var direct = match.spottersFor(shooter, 12, t -> t == enemy);
        direct.credit(BLUE, 20);
        enemy.spottedBy(shooter, 15);
        enemy.spottedBy(first, 25);
        match.spottersFor(shooter, 75, t -> false).credit(BLUE, 20);
        check(first.spottingDamage() == 0, "direct detection and final tick of own memory exclude scouts");

        enemy.spottedBy(second, 25);
        var shot = match.spottersFor(shooter, 85, t -> false);
        match.spottersFor(shooter, 86, t -> false).credit(BLUE, 20);
        shot.credit(unseen.id(), 20);
        shot.credit(BLUE, 0);
        check(first.spottingDamage() == 0 && second.spottingDamage() == 0,
                "expired, different-target and zero-damage hits earn no assist");
        enemy.spottedBy(second, 85);
        match.spottersFor(shooter, 85, t -> false).credit(BLUE, 10);
        check(first.spottingDamage() == 0 && second.spottingDamage() == 10,
                "only observers providing the freshest contact share credit");
        first.disconnect();
        enemy.spottedBy(first, 100);
        check(enemy.spottedUntil() == 145, "an offline observer cannot refresh shared contact");
        enemy.damage(enemy.hp() - 9);
        var lethal = new TanksRules.Hit(100, true, false, 10, TanksRules.ArmorSide.SIDE);
        shot.credit(BLUE, match.recordHit(RED, BLUE, lethal, 0));
        check(first.spottingDamage() == 4.5 && second.spottingDamage() == 14.5,
                "launch contacts split remaining HP even when a scout disconnects during flight");
        check(shooter.spottingDamage() == 0 && shooter.kills() == 1,
                "spotter credit leaves kill attribution with the gunner");
    }

    private static void movingGunHandling() {
        var light = TanksRules.Chassis.LIGHT;
        var medium = TanksRules.Chassis.MEDIUM;
        var heavy = TanksRules.Chassis.HEAVY;
        double movingLight = TanksRules.dispersion(light, light.speed(), 0, 0);
        double sigmaAtForty = Math.tan(Math.toRadians(movingLight * .45)) * 40;
        check(sigmaAtForty < TanksRules.geometry(heavy).hullHalfWidth() / 2,
                "moving light gun retains useful accuracy against a heavy profile at 40 blocks");
        check(movingLight < TanksRules.dispersion(medium, medium.speed(), 0, 0)
                        && TanksRules.dispersion(medium, medium.speed(), 0, 0)
                                < TanksRules.dispersion(heavy, heavy.speed(), 0, 0),
                "gun stabilization rewards light movement while heavy needs to settle");
        close(TanksRules.dispersion(light, 0, 0, 20), TanksRules.dispersion(light, 0, 0, 80),
                "light fully settles within one second after stopping");
        check(TanksRules.dispersion(medium, 0, 0, 20) > TanksRules.dispersion(medium, 0, 0, 80),
                "medium has a distinct settling delay after a short peek");
        close(TanksRules.dispersion(medium, 0, 0, 30), TanksRules.dispersion(medium, 0, 0, 80),
                "medium settles sooner than the armored heavy");
        check(TanksRules.dispersion(heavy, 0, 0, 30) > TanksRules.dispersion(heavy, 0, 0, 40),
                "heavy rewards holding a firing position rather than snap shooting");
        double circleTurn = Math.toDegrees(light.speed() / 8);
        check(circleTurn > heavy.turretDegreesPerTick()
                        && circleTurn < medium.turretDegreesPerTick()
                        && circleTurn < light.hullDegreesPerTick(),
                "an eight-block light orbit outturns heavy traverse but medium can countertrack it");
    }

    private static TanksRules.Hit plateHit(TanksRules.Chassis shooter, TanksRules.Shell shell,
            TanksRules.Chassis target, TanksRules.ArmorSide side, double angle) {
        double radians = Math.toRadians(angle);
        var g = TanksRules.geometry(target);
        return switch (side) {
            case FRONT -> TanksRules.hit(shooter, shell, target, 0, 0, g.hullHalfLength(),
                    Math.sin(radians), -Math.cos(radians));
            case SIDE -> TanksRules.hit(shooter, shell, target, 0, g.hullHalfWidth(), 0,
                    -Math.cos(radians), Math.sin(radians));
            case REAR -> TanksRules.hit(shooter, shell, target, 0, 0, -g.hullHalfLength(),
                    Math.sin(radians), Math.cos(radians));
        };
    }

    private static void classMobility() {
        int lightExit = travelTicks(TanksRules.Chassis.LIGHT, 3);
        int mediumExit = travelTicks(TanksRules.Chassis.MEDIUM, 3);
        int heavyExit = travelTicks(TanksRules.Chassis.HEAVY, 3);
        check(lightExit < mediumExit && mediumExit < heavyExit && lightExit <= 18,
                "light escapes a three-block firing lane sooner than heavier hulls from rest");
        int lightTraverse = travelTicks(TanksRules.Chassis.LIGHT, 50);
        int mediumTraverse = travelTicks(TanksRules.Chassis.MEDIUM, 50);
        int heavyTraverse = travelTicks(TanksRules.Chassis.HEAVY, 50);
        check(lightTraverse <= 180 && mediumTraverse > lightTraverse
                        && mediumTraverse < heavyTraverse && heavyTraverse >= 340,
                "light can relocate fifty blocks within nine seconds while heavy commits much longer");
        for (var chassis : TanksRules.Chassis.values()) {
            double speed = 0;
            for (int i = 0; i < 400; i++) speed = TanksRules.accelerate(speed, -1, chassis);
            check(speed < 0 && Math.abs(speed) < chassis.speed(), "class reverse remains slower than forward: " + chassis);
            for (int i = 0; i < 400; i++) speed = TanksRules.accelerate(speed, 0, chassis);
            close(speed, 0, "class braking reaches a stable stop: " + chassis);
        }
    }

    private static int travelTicks(TanksRules.Chassis chassis, double distance) {
        double speed = 0, travelled = 0;
        for (int tick = 1; tick <= 2000; tick++) {
            speed = TanksRules.accelerate(speed, 1, chassis);
            travelled += speed;
            if (travelled >= distance) return tick;
        }
        throw new AssertionError("class cannot reach its reposition target: " + chassis);
    }

    private static double timeToKill(TanksRules.Chassis shooter, TanksRules.Shell shell,
            TanksRules.Chassis target, TanksRules.ArmorSide side) {
        return timeToKill(shooter, shell, target, side, target.maxHp());
    }

    private static double timeToKill(TanksRules.Chassis shooter, TanksRules.Shell shell,
            TanksRules.Chassis target, TanksRules.ArmorSide side, double healthBudget) {
        double damage = plateHit(shooter, shell, target, side, 0).damage();
        return damage == 0 ? Double.POSITIVE_INFINITY
                : (Math.ceil(healthBudget / damage) - 1) * shooter.reloadTicks() / 20;
    }

    private static double bestTimeToKill(TanksRules.Chassis shooter,
            TanksRules.Chassis target, TanksRules.ArmorSide side) {
        return Math.min(timeToKill(shooter, TanksRules.Shell.AP, target, side),
                timeToKill(shooter, TanksRules.Shell.HE, target, side));
    }

    private static void balanceReport() {
        System.out.println("BALANCE: ideal plate-center hits, first loaded shot at t=0, 20 ticks/s; no misses, cover, flight delay or human win-rate inference.");
        System.out.println("Repair budget is maxHP*1.15, assuming the full one-use heal is spent before death. F/S/R = front/side/rear.");
        for (var c : TanksRules.Chassis.values()) {
            System.out.printf(Locale.ROOT,
                    "PROFILE %s hp=%.0f alpha=%.0f penetration=%.0f reload=%.2fs DPS=%.2f speed=%.2fb/s hull180=%.2fs turret180=%.2fs movingCone=%.2fdeg escape3=%.2fs travel50=%.2fs%n",
                    c, c.maxHp(), c.damage(), c.penetration(), c.reloadTicks()/20.0,
                    c.damage()*20/c.reloadTicks(), c.speed()*20,
                    180/c.hullDegreesPerTick()/20, 180/c.turretDegreesPerTick()/20,
                    TanksRules.dispersion(c,c.speed(),0,0), travelTicks(c,3)/20.0, travelTicks(c,50)/20.0);
        }
        for (var shooter : TanksRules.Chassis.values()) for (var target : TanksRules.Chassis.values()) {
            StringBuilder damage = new StringBuilder(), ttk = new StringBuilder(), repair = new StringBuilder();
            for (var side : TanksRules.ArmorSide.values()) {
                var ap = plateHit(shooter,TanksRules.Shell.AP,target,side,0);
                var he = plateHit(shooter,TanksRules.Shell.HE,target,side,0);
                damage.append(String.format(Locale.ROOT," %s=%.1f/%.1f",side,ap.damage(),he.damage()));
                ttk.append(String.format(Locale.ROOT," %s=%.1f/%.1f",side,
                        timeToKill(shooter,TanksRules.Shell.AP,target,side),timeToKill(shooter,TanksRules.Shell.HE,target,side)));
                repair.append(String.format(Locale.ROOT," %s=%.1f/%.1f",side,
                        timeToKill(shooter,TanksRules.Shell.AP,target,side,target.maxHp()*1.15),
                        timeToKill(shooter,TanksRules.Shell.HE,target,side,target.maxHp()*1.15)));
            }
            System.out.println("MATCH "+shooter+">"+target+" damage(AP/HE)"+damage+" TTKseconds(AP/HE)"+ttk);
            System.out.printf(Locale.ROOT,"REPAIRED %s>%s targetBudget=%.1f TTKseconds(AP/HE)%s%n",
                    shooter,target,target.maxHp()*1.15,repair);
            StringBuilder angles = new StringBuilder();
            for(int angle:new int[]{0,20,25,30,35,40}) angles.append(String.format(Locale.ROOT," %ddeg=%.1f/%.1f",angle,
                    plateHit(shooter,TanksRules.Shell.AP,target,TanksRules.ArmorSide.FRONT,angle).damage(),
                    plateHit(shooter,TanksRules.Shell.HE,target,TanksRules.ArmorSide.FRONT,angle).damage()));
            System.out.println("FRONTANGLE "+shooter+">"+target+" damage(AP/HE)"+angles);
        }
    }

    private static void armorAndShells() {
        var front = hit(TanksRules.Shell.AP, 0, 0, 1.35, 0, -1);
        var rear = hit(TanksRules.Shell.AP, 0, 0, -1.35, 0, 1);
        check(
                !front.penetrated() && front.damage() == 0,
                "light AP cannot penetrate heavy frontal armor");
        check(rear.penetrated() && rear.damage() > 0, "same shot penetrates weaker rear armor");
        check(
                front.side() == TanksRules.ArmorSide.FRONT
                        && rear.side() == TanksRules.ArmorSide.REAR,
                "front and rear follow the hull's direction");
        var rotated = hit(TanksRules.Shell.AP, 90, -1.35, 0, 1, 0);
        check(
                rotated.side() == TanksRules.ArmorSide.FRONT && !rotated.penetrated(),
                "rotating the hull rotates its strong frontal plate");
        var side = hit(TanksRules.Shell.AP, 0, .95, 0, -1, 0);
        check(
                side.side() == TanksRules.ArmorSide.SIDE && side.penetrated(),
                "square side hit penetrates");
        check(
                TanksRules.armorSide(0, 1.6, 2.2) == TanksRules.ArmorSide.FRONT,
                "hit near the actual front corner uses the visual hull's 1.65 by 2.2 footprint");
        var glance = hit(TanksRules.Shell.AP, 0, .95, .3, -.1, 1);
        check(glance.ricochet() && glance.damage() == 0, "AP ricochets at a grazing incidence");
        var he = hit(TanksRules.Shell.HE, 0, 0, 1.35, 0, -1);
        var rearHe = hit(TanksRules.Shell.HE, 0, 0, -1.35, 0, 1);
        check(
                he.damage() > 0 && he.damage() < rearHe.damage() && !he.ricochet(),
                "HE chips frontal armor while weaker armor takes more damage");
        check(
                TanksRules.effectiveArmor(100, 0) == 100
                        && TanksRules.effectiveArmor(100, 60) > 199,
                "obliquity increases the material a shell must penetrate");
    }

    private static TanksRules.Hit hit(
            TanksRules.Shell shell, double yaw, double x, double z, double dx, double dz) {
        return TanksRules.hit(
                TanksRules.Chassis.LIGHT, shell, TanksRules.Chassis.HEAVY, yaw, x, z, dx, dz);
    }

    private static void steeringAndClasses() {
        close(TanksRules.normalizeYaw(541), -179, "yaw wraps large rotations");
        close(TanksRules.angleDelta(179, -179), 2, "turning crosses the wrap by the shortest path");
        close(TanksRules.stepYaw(179, -179, 1), -180, "turret obeys its angular rate at the wrap");
        close(TanksRules.stepYaw(0, 1, 5), 1, "turret does not overshoot its target");
        double speed = 0;
        for (int i = 0; i < 300; i++) speed = TanksRules.accelerate(speed, 1, .24);
        close(speed, .24, "forward acceleration is bounded by chassis speed");
        for (int i = 0; i < 300; i++) speed = TanksRules.accelerate(speed, -1, .24);
        close(speed, -.132, "reverse is slower and remains bounded");
        for (int i = 0; i < 300; i++) speed = TanksRules.accelerate(speed, 0, .24);
        close(speed, 0, "releasing throttle brings a tank to rest");
        var light = TanksRules.Chassis.LIGHT;
        var heavy = TanksRules.Chassis.HEAVY;
        check(
                light.speed() > heavy.speed()
                        && light.maxHp() < heavy.maxHp()
                        && light.damage() < heavy.damage()
                        && light.reloadTicks() < heavy.reloadTicks()
                        && light.frontArmor() < heavy.frontArmor(),
                "light mobility trades away heavy survivability and firepower");
        check(
                TanksRules.dispersion(light, 0, 0, 80) < TanksRules.dispersion(light, .24, 30, 0),
                "standing still and settling the turret tightens the aiming circle");
    }

    private static void reloadAndConsumables() {
        var match = battle(1000, 30, false);
        var actor = match.actor(RED);
        check(actor.fire() && !actor.fire(), "firing consumes the loaded shell");
        int reload = actor.reloadTicks();
        for (int i = 0; i < reload - 1; i++) match.tick();
        check(!actor.fire(), "reload cannot complete a tick early");
        match.tick();
        check(actor.fire(), "reload completes at the promised tick");
        actor.damage(100);
        actor.damageTrack(80);
        check(
                actor.repair() && actor.trackTicks() == 0 && actor.hp() > 700,
                "repair kit restores tracks and repairs some hull damage");
        check(!actor.repair() && actor.repairKits() == 0, "repair kit is consumed only once");
        check(
                actor.smoke() && !actor.smoke() && actor.smokeTicks() > 0,
                "smoke has a bounded duration and one charge");
        int smoke = actor.smokeTicks();
        for (int i = 0; i < smoke; i++) match.tick();
        check(actor.smokeTicks() == 0, "smoke expires by simulation ticks");
        check(
                !actor.selectChassis(TanksRules.Chassis.HEAVY),
                "class cannot change after battle starts");
    }

    private static void trackRecovery() {
        var heavy = TanksRules.Chassis.HEAVY;
        var light = TanksRules.Chassis.LIGHT;
        check(!TanksBallistics.treadHit(heavy, 0, 0, .4, 2.2)
                        && !TanksBallistics.treadHit(heavy, 90, -2.2, .4, 0),
                "a hit to the low central plate never breaks a track, even after a hull turn");
        check(TanksBallistics.treadHit(heavy, 0, -1.65, .4, 0)
                        && TanksBallistics.treadHit(heavy, 90, 2.2, .4, 1.25),
                "both broadside wheels and an exposed rear belt end can be targeted");
        check(!TanksBallistics.treadHit(light, 90, 0, .74, 1.35)
                        && TanksBallistics.treadHit(light, 90, 0, .4, 1.35),
                "light track hit height scales with its visible model");
        var match = battle(1000, 900, false);
        var target = match.actor(BLUE);
        var ricochet = new TanksRules.Hit(0, false, true, 230, TanksRules.ArmorSide.SIDE);
        target.base(0);
        match.tick();
        double hp = target.hp();
        match.recordHit(RED, BLUE, ricochet, 70);
        check(target.trackTicks() == 70 && target.captureTicks() == 0 && target.hp() == hp,
                "a fresh tread break immobilizes and resets capture without claiming HP");
        for (int i = 0; i < 69; i++) {
            match.tick();
            match.recordHit(RED, BLUE, ricochet, 70);
        }
        check(target.trackTicks() == 1 && target.captureTicks() == 69,
                "repeated tread impacts do not extend repair or erase capture again");
        match.tick();
        for (int i = 0; i < 40; i++) {
            match.recordHit(RED, BLUE, ricochet, 70);
            check(target.trackTicks() == 0, "natural repair gives two seconds of movement");
            match.tick();
        }
        match.recordHit(RED, BLUE, ricochet, 70);
        check(target.trackTicks() == 70 && target.repair(), "a later break can be repaired by kit");
        var pierced = new TanksRules.Hit(15, true, false, 30, TanksRules.ArmorSide.REAR);
        match.recordHit(RED, BLUE, pierced, 70);
        check(target.trackTicks() == 0 && target.hp() == hp - 15,
                "kit recovery protects the track while penetrating rounds still damage the hull");
    }

    private static void capture() {
        var match = battle(1000, 8, false);
        var red = match.actor(RED);
        var blue = match.actor(BLUE);
        red.base(1);
        blue.base(1);
        match.tick();
        check(match.captureState(0) == TanksMatch.CaptureState.CONTESTED
                        && match.captureProgress(0) == 0,
                "defender already in the circle contests at zero percent");
        red.disconnect();
        check(match.captureState(0) == TanksMatch.CaptureState.IDLE,
                "an offline attacker cannot create a contested warning");
        red.reconnect();
        blue.base(-1);
        check(match.captureState(0) == TanksMatch.CaptureState.CAPTURING,
                "removing the defender resumes capture");
        match.tick();
        close(match.captureProgress(0), .125, "attacker advances capture in enemy base");
        blue.base(1);
        match.tick();
        close(match.captureProgress(0), .125, "living defender contests capture");
        blue.base(-1);
        match.recordHit(
                BLUE, RED, new TanksRules.Hit(1, true, false, 10, TanksRules.ArmorSide.SIDE), 0);
        close(match.captureProgress(0), 0, "actual damage resets the damaged attacker's capture");
        red.disconnect();
        match.tick();
        close(match.captureProgress(0), 0, "offline tanks cannot advance capture");
        red.reconnect();
        for (int i = 0; i < 8; i++) match.tick();
        check(match.winner() == 0 && match.finished(), "uncontested capture ends the match");
        var partial = new TanksMatch(1000, 20, false);
        partial.add(RED, 0, TanksRules.Chassis.LIGHT);
        partial.add(ALLY, 0, TanksRules.Chassis.MEDIUM);
        partial.add(BLUE, 1, TanksRules.Chassis.HEAVY);
        partial.start();
        partial.actor(RED).base(1);
        partial.actor(ALLY).base(1);
        partial.tick();
        partial.tick();
        close(partial.captureProgress(0), .2, "two capturing tanks contribute independently");
        partial.actor(RED).damage(10);
        close(
                partial.captureProgress(0),
                .1,
                "damage removes only the hit tank's banked contribution");
        partial.actor(ALLY).base(-1);
        close(partial.captureProgress(0), 0, "leaving the base clears that tank's contribution");
        partial.tick();
        partial.actor(RED).damageTrack(20);
        close(
                partial.captureProgress(0),
                0,
                "damage to a track resets capture even without hull damage");

        var simultaneous = battle(100, 2, false);
        simultaneous.actor(RED).base(1);
        simultaneous.actor(BLUE).base(0);
        simultaneous.tick();
        simultaneous.tick();
        check(
                simultaneous.winner() == TanksMatch.DRAW,
                "simultaneous completed base captures draw");

        var capped = new TanksMatch(100, 12, false);
        for (int i = 0; i < 4; i++) capped.add(new UUID(1, i), 0, TanksRules.Chassis.LIGHT).base(1);
        capped.add(BLUE, 1, TanksRules.Chassis.HEAVY);
        capped.start();
        capped.tick();
        close(capped.captureProgress(0), .25, "capture rate is capped at three tanks");
    }

    private static void lifecycleAndRewards() {
        var match = battle(1000, 30, false);
        var red = match.actor(RED);
        red.damage(100);
        red.fire();
        red.damageTrack(20);
        red.disconnect();
        double hp = red.hp();
        int reload = red.reloadTicks();
        check(!red.fire() && !red.smoke(), "disconnected players cannot issue controls");
        red.reconnect();
        check(
                red.hp() == hp && red.reloadTicks() == reload && red.trackTicks() == 20,
                "reconnect preserves hull damage, reload and modules");
        match.actor(BLUE).disconnect();
        match.recordHit(
                RED,
                BLUE,
                new TanksRules.Hit(10000, true, false, 1, TanksRules.ArmorSide.REAR),
                40);
        check(
                !match.actor(BLUE).alive() && red.kills() == 1,
                "offline hull remains vulnerable and awards one kill");
        match.recordHit(
                RED, BLUE, new TanksRules.Hit(10000, true, false, 1, TanksRules.ArmorSide.REAR), 0);
        check(red.kills() == 1, "repeated impacts on a wreck cannot award duplicate kills");
        match.actor(BLUE).reconnect();
        check(
                !match.actor(BLUE).alive()
                        && !match.actor(BLUE).repair()
                        && !match.actor(BLUE).fire(),
                "reconnect and consumables cannot resurrect a wreck");
        match.tick();
        check(
                match.winner() == 0 && match.reward(RED, 20, 5, 1) == 26,
                "elimination pays winner, kill and participation once through core scoring");
        check(match.reward(BLUE, 20, 5, 1) == 1, "loser receives only participation");
        red.disconnect();
        check(match.reward(RED, 20, 5, 1) == 26, "disconnect does not erase earned battle rewards");
        red.leave();
        red.reconnect();
        check(
                match.reward(RED, 20, 5, 1) == 0 && !red.online() && !red.alive(),
                "permanent leave forfeits rewards and reconnect cannot reverse it");
        var practice = battle(1000, 30, true);
        practice.actor(BLUE).leave();
        practice.tick();
        check(
                practice.winner() == 0 && practice.reward(RED, 20, 5, 1) == 0,
                "practice ends normally but awards no points");
        var timed = battle(2, 30, false);
        timed.tick();
        check(!timed.finished(), "match remains live until its time budget expires");
        timed.tick();
        check(timed.winner() == -1 && timed.ticksRemaining() == 0, "battle timeout is a draw");
        var draw = battle(100, 30, false);
        draw.actor(RED).leave();
        draw.actor(BLUE).leave();
        draw.tick();
        check(draw.winner() == -1, "simultaneous elimination is a draw");
        var mutual = battle(100, 30, false);
        var lethal = new TanksRules.Hit(10000, true, false, 1, TanksRules.ArmorSide.REAR);
        mutual.recordHit(RED, BLUE, lethal, 0);
        mutual.recordHit(BLUE, RED, lethal, 0);
        check(
                !mutual.actor(RED).alive()
                        && !mutual.actor(BLUE).alive()
                        && mutual.actor(RED).kills() == 1
                        && mutual.actor(BLUE).kills() == 1,
                "a destroyed shooter's already flying shell can still trade the final kill");
        mutual.tick();
        check(mutual.winner() == TanksMatch.DRAW, "in-flight mutual final kills resolve as a draw");
        var friendly = new TanksMatch(100, 30, false);
        friendly.add(RED, 0, TanksRules.Chassis.LIGHT);
        friendly.add(ALLY, 0, TanksRules.Chassis.MEDIUM);
        friendly.add(BLUE, 1, TanksRules.Chassis.HEAVY);
        friendly.start();
        double allyHp = friendly.actor(ALLY).hp();
        check(
                friendly.recordHit(
                                        RED,
                                        ALLY,
                                        new TanksRules.Hit(
                                                300, true, false, 1, TanksRules.ArmorSide.SIDE),
                                        80)
                                == 0
                        && friendly.actor(ALLY).hp() == allyHp
                        && friendly.actor(ALLY).trackTicks() == 0,
                "friendly shells cannot damage hull or tracks");
        var solo = new TanksMatch(100, 30, true);
        solo.add(RED, 0, TanksRules.Chassis.LIGHT);
        solo.tick();
        check(
                solo.ticksRemaining() == 100 && !solo.actor(RED).fire(),
                "preparation does not consume battle time or fire shells");
        check(
                solo.actor(RED).selectChassis(TanksRules.Chassis.HEAVY)
                        && solo.actor(RED).hp() == 1450,
                "preparation class selection initializes the selected hull's health");
        solo.start();
        solo.tick();
        check(!solo.finished(), "solo practice does not immediately win against an absent team");
    }

    private static void invalidInputs() {
        boolean rejected = false;
        try {
            new TanksMatch(0, 10, false);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "zero-duration matches are rejected");
        rejected = false;
        try {
            TanksRules.accelerate(Double.NaN, 1, .2);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "non-finite controls cannot corrupt simulation state");
    }

    private static TanksMatch battle(int duration, int capture, boolean practice) {
        var match = new TanksMatch(duration, capture, practice);
        match.add(RED, 0, TanksRules.Chassis.LIGHT);
        match.add(BLUE, 1, TanksRules.Chassis.HEAVY);
        match.start();
        return match;
    }

    private static void close(double actual, double expected, String label) {
        check(Math.abs(actual - expected) < .000001, label + " (actual " + actual + ")");
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
