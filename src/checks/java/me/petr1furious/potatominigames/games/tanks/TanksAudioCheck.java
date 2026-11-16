package me.petr1furious.potatominigames.games.tanks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Checks outcome truthfulness and sound scheduling independently of native sound registries. */
public final class TanksAudioCheck {
    public static void main(String[] args) {
        TanksRules.Hit chip = new TanksRules.Hit(35, false, false, 170, TanksRules.ArmorSide.FRONT);
        check(
                TanksAudio.hitKey(chip, 35).equals("he-hit"),
                "HE chip damage must never claim armor penetration");
        TanksRules.Hit pierced =
                new TanksRules.Hit(200, true, false, 30, TanksRules.ArmorSide.REAR);
        TanksRules.Hit ricochet =
                new TanksRules.Hit(0, false, true, 230, TanksRules.ArmorSide.SIDE);
        TanksRules.Hit blocked =
                new TanksRules.Hit(0, false, false, 230, TanksRules.ArmorSide.FRONT);
        check(
                TanksAudio.hitKey(pierced, 200).equals("penetration"),
                "penetration requires an actual penetrated plate");
        check(
                TanksAudio.hitKey(pierced, 0).equals("blocked"),
                "no applied damage does not advertise a penetration");
        check(TanksAudio.hitKey(ricochet, 0).equals("ricochet"), "ricochet remains distinct");
        check(TanksAudio.hitKey(blocked, 0).equals("blocked"), "blocked plate remains distinct");
        check(
                TanksAudio.hit(chip, 35).cue() != TanksAudio.hit(pierced, 200).cue()
                        && TanksAudio.hit(ricochet, 0).cue() != TanksAudio.hit(blocked, 0).cue(),
                "HE splash, penetration, ricochet and block use distinct sound cues");
        check(
                TanksAudio.cannon(TanksRules.Chassis.HEAVY, TanksRules.Shell.AP).getFirst().pitch()
                        < TanksAudio.cannon(TanksRules.Chassis.LIGHT, TanksRules.Shell.AP)
                                .getFirst()
                                .pitch(),
                "heavy cannon has a lower report");
        check(
                TanksAudio.cannon(TanksRules.Chassis.MEDIUM, TanksRules.Shell.AP).get(1).cue()
                        != TanksAudio.cannon(TanksRules.Chassis.MEDIUM, TanksRules.Shell.HE)
                                .get(1)
                                .cue(),
                "AP and HE reports have different shell layers");
        TanksAudio audio = new TanksAudio();
        UUID id = new UUID(0, 1), other = new UUID(0, 2);
        int rejected = 0;
        for (int tick = 0; tick < 200; tick++)
            for (int duplicate = 0; duplicate < 12; duplicate++)
                if (audio.allow(id, TanksAudio.Cue.REJECTED, tick, 10)) rejected++;
        check(rejected == 20, "spam produces at most one rejection cue per ten ticks");
        check(
                audio.allow(other, TanksAudio.Cue.REJECTED, 199, 10)
                        && audio.allow(id, TanksAudio.Cue.TREAD, 199, 10),
                "rate limits are independent by player and cue");
        check(TanksAudio.rejected().volume() < .3, "failed attempt is a soft cue");
        check(
                TanksAudio.treadCadence(TanksRules.Chassis.LIGHT, .24)
                        < TanksAudio.treadCadence(TanksRules.Chassis.LIGHT, .04),
                "track cadence rises with actual speed");
        check(
                TanksAudio.treadCadence(TanksRules.Chassis.HEAVY, .14)
                        != TanksAudio.treadCadence(TanksRules.Chassis.LIGHT, .24),
                "class weight changes track cadence");
        int fast = movementCount(.24), slow = movementCount(.04);
        check(
                fast > slow && fast <= 35,
                "movement layering scales with speed and stays bounded over ten seconds");
        check(movementCount(-.24) == fast, "reverse driving has the same bounded track cadence");
        check(
                audio.movement(id, TanksRules.Chassis.HEAVY, 0, 200).isEmpty(),
                "stationary tank has no movement cue");
        double pivot = TanksAudio.trackSpeed(0, 0, TanksRules.Chassis.HEAVY.hullDegreesPerTick());
        check(
                pivot > .025,
                "an actual heavy hull pivot crosses the movement-audio activity threshold");
        check(
                !new TanksAudio().movement(id, TanksRules.Chassis.HEAVY, pivot, 0).isEmpty(),
                "zero-translation pivot produces a bounded track cue");
        check(
                TanksAudio.trackSpeed(0, 25, 25) == 0
                        && new TanksAudio()
                                .movement(
                                        id,
                                        TanksRules.Chassis.HEAVY,
                                        TanksAudio.trackSpeed(0, 25, 25),
                                        0)
                                .isEmpty(),
                "parked or collision-blocked steering remains silent");
        check(
                Math.abs(TanksAudio.trackSpeed(0, 179, -179) - Math.toRadians(2) * 1.25) < 1e-9,
                "yaw wrap uses actual short-angle track travel");
        check(
                TanksAudio.trackSpeed(.24, 0, 2.8) == .24
                        && TanksAudio.trackSpeed(-.24, 0, -2.8) == .24,
                "normal full-speed forward and reverse curves retain translation cadence");
        audio.clear();
        check(audio.capture(0, 0, false, false, 0).isEmpty(), "idle bases remain silent");
        check(
                cue(audio.capture(0, .01, true, false, 0)) == TanksAudio.Cue.CAPTURE_START,
                "capture start is an edge cue");
        check(
                audio.capture(0, .1, true, false, 50).isEmpty(),
                "ordinary progress does not repeat start tones");
        check(
                cue(audio.capture(0, .1, true, true, 60)) == TanksAudio.Cue.CAPTURE_CONTESTED,
                "defender entry has a contested cue");
        check(
                audio.capture(0, .1, true, true, 119).isEmpty(),
                "contested reminders are rate limited");
        check(
                cue(audio.capture(0, .1, true, true, 120)) == TanksAudio.Cue.CAPTURE_CONTESTED,
                "long contests get bounded reminders");
        check(
                cue(audio.capture(0, .85, true, false, 130)) == TanksAudio.Cue.CAPTURE_NEAR,
                "near victory changes the warning");
        check(
                audio.capture(0, .9, true, false, 159).isEmpty(),
                "near-win cue has a thirty-tick cadence");
        check(
                cue(audio.capture(0, .92, true, false, 160)) == TanksAudio.Cue.CAPTURE_NEAR,
                "near-win warning repeats sparingly");
        audio.capture(0, 0, false, false, 161);
        check(
                audio.capture(0, .01, true, false, 165).isEmpty(),
                "rapid objective reentry does not spam");
        check(
                cue(audio.capture(0, .02, true, false, 170)) == TanksAudio.Cue.CAPTURE_START,
                "muted transition is delivered after its cooldown");
        List<TanksAudio.Tone> tones = new ArrayList<>();
        tones.add(TanksAudio.hit(chip, 35));
        tones.add(TanksAudio.hit(pierced, 200));
        tones.add(TanksAudio.hit(ricochet, 0));
        tones.add(TanksAudio.hit(blocked, 0));
        tones.add(TanksAudio.impact(true));
        tones.add(TanksAudio.impact(false));
        for (var chassis : TanksRules.Chassis.values())
            for (var shell : TanksRules.Shell.values())
                tones.addAll(TanksAudio.cannon(chassis, shell));
        for (var tone : tones)
            check(
                    tone.volume() > 0
                            && tone.volume() <= 1.5
                            && tone.pitch() >= .5
                            && tone.pitch() <= 2,
                    "sound arguments remain valid and restrained");
        audio.clear();
        check(
                audio.allow(id, TanksAudio.Cue.REJECTED, 0, 10)
                        && cue(audio.capture(0, .1, true, false, 0))
                                == TanksAudio.Cue.CAPTURE_START,
                "cleanup resets bounded state for a new battle");
        TanksAudio noisyCapture = new TanksAudio();
        int objectiveCues = 0;
        for (int tick = 0; tick < 1000; tick++)
            for (int duplicate = 0; duplicate < 4; duplicate++)
                objectiveCues +=
                        noisyCapture.capture(0, .5, true, (tick + duplicate) % 2 == 0, tick).size();
        check(
                objectiveCues <= 100,
                "rapidly changing contests cannot bypass the shared objective cooldown");
        System.out.println("Tanks audio checks passed");
    }

    private static int movementCount(double speed) {
        TanksAudio audio = new TanksAudio();
        int count = 0;
        for (int tick = 0; tick < 200; tick++)
            count += audio.movement(new UUID(0, 1), TanksRules.Chassis.LIGHT, speed, tick).size();
        return count;
    }

    private static TanksAudio.Cue cue(List<TanksAudio.Tone> tones) {
        check(tones.size() == 1, "objective transition has exactly one cue");
        return tones.getFirst().cue();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
