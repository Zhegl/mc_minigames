package me.petr1furious.potatominigames.games.tanks;

import java.util.UUID;

/** Server-free checks for armor geometry, controls and one-life battle state. */
public final class TanksRulesCheck {
    private static final UUID RED = new UUID(0, 1), BLUE = new UUID(0, 2), ALLY = new UUID(0, 3);
    private static int checks;

    public static void main(String[] args) {
        armorAndShells();
        steeringAndClasses();
        reloadAndConsumables();
        capture();
        lifecycleAndRewards();
        invalidInputs();
        System.out.println("TanksRulesCheck: " + checks + " checks passed");
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

    private static void capture() {
        var match = battle(1000, 8, false);
        var red = match.actor(RED);
        var blue = match.actor(BLUE);
        red.base(1);
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
                        && solo.actor(RED).hp() == 1500,
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
