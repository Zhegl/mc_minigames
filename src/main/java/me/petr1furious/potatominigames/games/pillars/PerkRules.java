package me.petr1furious.potatominigames.games.pillars;

import java.util.NavigableMap;
import java.util.TreeMap;

/** Tick-based rules shared by the runtime and serverless checks. */
final class PerkRules {
    static final int PLATFORM_TICKS = 200, GHOST_TICKS = 80, FREEZE_TICKS = 80,
            PLANK_TICKS = 100, SMOKE_TICKS = 100, BREAK_TICKS = 120, REWIND_TICKS = 60;
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
