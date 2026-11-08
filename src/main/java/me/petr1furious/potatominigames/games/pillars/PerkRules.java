package me.petr1furious.potatominigames.games.pillars;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.List;
import java.util.random.RandomGenerator;

/** Tick-based rules shared by the runtime and serverless checks. */
final class PerkRules {
    static final int PLATFORM_TICKS = 200, GHOST_TICKS = 54, FREEZE_TICKS = 80,
            PLANK_TICKS = 100, SMOKE_TICKS = 100, BREAK_TICKS = 60, REWIND_TICKS = 60;
    static final int ATOMIC_FUSE_TICKS = 80;
    static final float ATOMIC_POWER = 4f * 4;
    static final int BLESSING_TICKS = 60 * 20, GRAVITY_TICKS = 3 * 20,
            GRAVITY_COOLDOWN_TICKS = 15 * 20, CONNECTED_LIMIT = 4096;
    static final double ANVIL_RANGE = 192, BUILD_RANGE = 6;
    static final int PHANTOM_TICKS = 60 * 20;
    static final int DECOY_TICKS = 60 * 20;
    static final float DECOY_POWER = 1.5f;
    static double decoyDamage(double damage) { return Math.min(4, damage); }
    static final class DecoyTrigger {
        private final java.util.UUID owner;
        private boolean triggered;
        DecoyTrigger(java.util.UUID owner) { this.owner = owner; }
        boolean hit(java.util.UUID attacker, boolean combatHit) {
            if (triggered || !combatHit || attacker == null || owner.equals(attacker)) return false;
            triggered = true;
            return true;
        }
    }
    static <T> T randomOpponent(List<T> players, T user, RandomGenerator random) {
        var opponents = players.stream().filter(p -> !p.equals(user)).toList();
        return opponents.isEmpty() ? null : opponents.get(random.nextInt(opponents.size()));
    }
    static final class PhantomNoises {
        private final long until;
        private long next;
        PhantomNoises(long tick, RandomGenerator random) { until = tick + PHANTOM_TICKS; next = tick + delay(random); }
        boolean expired(long tick) { return tick >= until; }
        boolean due(long tick, RandomGenerator random) {
            if (expired(tick) || tick < next) return false;
            next = tick + delay(random);
            return true;
        }
        private static int delay(RandomGenerator random) { return random.nextInt(80, 121); }
    }
    static final class GravityCycle {
        private long until, ready;
        boolean start(long tick) {
            if (tick < ready) return false;
            until = tick + GRAVITY_TICKS;
            ready = until + GRAVITY_COOLDOWN_TICKS;
            return true;
        }
        boolean active(long tick) { return tick < until; }
        long remaining(long tick) { return Math.max(0, ready - tick); }
    }
    static boolean complete(int[] wanted, int[] present) {
        return wanted.length > 0 && java.util.Arrays.equals(wanted, present);
    }
    static final class History<T> {
        private final NavigableMap<Long, T> positions = new TreeMap<>();
        void add(long tick, T at) { positions.put(tick, at); positions.headMap(tick - REWIND_TICKS - 2, false).clear(); }
        T ago(long tick) { var e = positions.floorEntry(tick - REWIND_TICKS); return e == null ? null : e.getValue(); }
        int size() { return positions.size(); }
    }
    static final class Channel<T> {
        private T target;
        private long started;
        boolean tick(T at, long now, boolean held) {
            if (!held || at == null) { clear(); return false; }
            if (!at.equals(target)) { target = at; started = now; }
            return now - started >= BREAK_TICKS;
        }
        long elapsed(long now) { return target == null ? 0 : now - started; }
        void clear() { target = null; }
    }
}
