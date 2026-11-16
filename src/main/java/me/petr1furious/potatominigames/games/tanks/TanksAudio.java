package me.petr1furious.potatominigames.games.tanks;

import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small vanilla sound palette with deterministic, bounded vehicle and objective cadence. */
final class TanksAudio {
    enum Cue {
        CANNON,
        AP_CRACK,
        HE_BOOM,
        PENETRATION,
        RICOCHET,
        BLOCKED,
        HE_DAMAGE,
        METAL_IMPACT,
        GROUND_IMPACT,
        ENGINE,
        TREAD,
        CAPTURE_START,
        CAPTURE_CONTESTED,
        CAPTURE_NEAR,
        REJECTED;

        Sound sound() {
            return switch (this) {
                case CANNON -> Sound.ENTITY_GENERIC_EXPLODE;
                case AP_CRACK -> Sound.ENTITY_FIREWORK_ROCKET_BLAST;
                case HE_BOOM, HE_DAMAGE -> Sound.ENTITY_DRAGON_FIREBALL_EXPLODE;
                case PENETRATION -> Sound.ENTITY_ARROW_HIT_PLAYER;
                case RICOCHET -> Sound.ITEM_TRIDENT_HIT;
                case BLOCKED -> Sound.ITEM_SHIELD_BLOCK;
                case METAL_IMPACT -> Sound.BLOCK_ANVIL_HIT;
                case GROUND_IMPACT -> Sound.BLOCK_STONE_BREAK;
                case ENGINE -> Sound.ENTITY_MINECART_INSIDE;
                case TREAD -> Sound.ENTITY_IRON_GOLEM_STEP;
                case CAPTURE_START -> Sound.BLOCK_BEACON_POWER_SELECT;
                case CAPTURE_CONTESTED -> Sound.BLOCK_NOTE_BLOCK_BASS;
                case CAPTURE_NEAR -> Sound.BLOCK_NOTE_BLOCK_PLING;
                case REJECTED -> Sound.BLOCK_DISPENSER_FAIL;
            };
        }
    }

    record Tone(Cue cue, float volume, float pitch) {
        Sound sound() {
            return cue.sound();
        }
    }

    private record Gate(UUID player, Cue cue) {}

    private enum Capture {
        IDLE,
        TAKING,
        CONTESTED,
        NEAR
    }

    private static final class CaptureMemory {
        Capture state = Capture.IDLE;
        long played = -1_000_000;
    }

    private final Map<Gate, Long> played = new HashMap<>();
    private final Map<Integer, CaptureMemory> captures = new HashMap<>();

    static String hitKey(TanksRules.Hit hit, double damage) {
        return hit.ricochet()
                ? "ricochet"
                : damage <= 0 ? "blocked" : hit.penetrated() ? "penetration" : "he-hit";
    }

    static Tone hit(TanksRules.Hit hit, double damage) {
        return switch (hitKey(hit, damage)) {
            case "ricochet" -> new Tone(Cue.RICOCHET, .5f, 1.75f);
            case "blocked" -> new Tone(Cue.BLOCKED, .45f, .6f);
            case "penetration" -> new Tone(Cue.PENETRATION, .5f, 1.15f);
            default -> new Tone(Cue.HE_DAMAGE, .35f, 1.1f);
        };
    }

    static List<Tone> cannon(TanksRules.Chassis chassis, TanksRules.Shell shell) {
        float pitch =
                switch (chassis) {
                    case LIGHT -> 1.05f;
                    case MEDIUM -> .85f;
                    case HEAVY -> .62f;
                };
        return List.of(
                new Tone(Cue.CANNON, 1.25f, pitch),
                shell == TanksRules.Shell.AP
                        ? new Tone(Cue.AP_CRACK, .42f, 1.65f)
                        : new Tone(Cue.HE_BOOM, .55f, .85f));
    }

    static Tone impact(boolean metal) {
        return metal
                ? new Tone(Cue.METAL_IMPACT, .38f, 1.5f)
                : new Tone(Cue.GROUND_IMPACT, .65f, .65f);
    }

    static Tone rejected() {
        return new Tone(Cue.REJECTED, .22f, .9f);
    }

    /** The timestamp is per player and cue: duplicate rejection paths cannot produce a buzz. */
    boolean allow(UUID player, Cue cue, long tick, int interval) {
        Gate key = new Gate(player, cue);
        Long previous = played.get(key);
        if (previous != null && tick - previous < interval) return false;
        played.put(key, tick);
        return true;
    }

    static int treadCadence(TanksRules.Chassis chassis, double speed) {
        double throttle = Math.clamp(Math.abs(speed) / chassis.speed(), 0, 1);
        int weight =
                switch (chassis) {
                    case LIGHT -> -2;
                    case MEDIUM -> 0;
                    case HEAVY -> 2;
                };
        return Math.max(8, (int) Math.round(22 - 12 * throttle) + weight);
    }

    static double trackSpeed(double actualSpeed, double previousYaw, double yaw) {
        return Math.max(
                Math.abs(actualSpeed),
                Math.abs(Math.toRadians(TanksRules.angleDelta(previousYaw, yaw))) * 1.25);
    }

    List<Tone> movement(UUID id, TanksRules.Chassis chassis, double speed, long tick) {
        if (!Double.isFinite(speed) || Math.abs(speed) <= .025) return List.of();
        double throttle = Math.clamp(Math.abs(speed) / chassis.speed(), 0, 1);
        float weight =
                switch (chassis) {
                    case LIGHT -> 1.05f;
                    case MEDIUM -> .83f;
                    case HEAVY -> .63f;
                };
        List<Tone> tones = new ArrayList<>(2);
        if (allow(id, Cue.ENGINE, tick, 40))
            tones.add(
                    new Tone(
                            Cue.ENGINE,
                            .1f + (float) throttle * .04f,
                            weight + (float) throttle * .2f));
        if (allow(id, Cue.TREAD, tick, treadCadence(chassis, speed)))
            tones.add(
                    new Tone(
                            Cue.TREAD,
                            chassis == TanksRules.Chassis.HEAVY ? .22f : .15f,
                            weight + (float) throttle * .15f));
        return List.copyOf(tones);
    }

    List<Tone> capture(int side, double progress, boolean attackers, boolean contested, long tick) {
        CaptureMemory memory = captures.computeIfAbsent(side, ignored -> new CaptureMemory());
        Capture state =
                !attackers
                        ? Capture.IDLE
                        : contested
                                ? Capture.CONTESTED
                                : progress >= .8 ? Capture.NEAR : Capture.TAKING;
        if (state == Capture.IDLE) {
            memory.state = Capture.IDLE;
            return List.of();
        }
        boolean changed = state != memory.state;
        if (tick - memory.played < 10) return List.of();
        int repeat = state == Capture.NEAR ? 30 : 60;
        if (!changed && (state == Capture.TAKING || tick - memory.played < repeat))
            return List.of();
        memory.state = state;
        memory.played = tick;
        return List.of(
                switch (state) {
                    case TAKING -> new Tone(Cue.CAPTURE_START, .35f, 1.15f);
                    case CONTESTED -> new Tone(Cue.CAPTURE_CONTESTED, .32f, .7f);
                    case NEAR -> new Tone(Cue.CAPTURE_NEAR, .38f, 1.55f);
                    case IDLE -> throw new IllegalStateException("idle capture has no sound");
                });
    }

    void clear() {
        played.clear();
        captures.clear();
    }
}
