package me.petr1furious.potatominigames.games.pillars;

import java.lang.reflect.Proxy;
import java.util.Random;
import java.util.Set;

import org.bukkit.scheduler.BukkitTask;

/** Real burst timing, fractional shuffle cadence and ownership of delayed event tasks. */
public final class AdhdModifierCheck {
    public static void main(String[] args) {
        AdhdModifier modifier = new AdhdModifier(120, 30, 10, fixed(0.5));
        idle(modifier, 2399);
        check(modifier.tick() == AdhdModifier.Change.START && modifier.active(), "First burst starts at two minutes");
        check(modifier.secondsLeft() == 30 && modifier.shuffleSecondsLeft() == 4, "Initial countdowns are rounded up");
        int shuffles = 1; // The session replaces items immediately at START.
        int nextShuffle = 67;
        for (int tick = 1; tick <= 600; tick++) {
            AdhdModifier.Change change = modifier.tick();
            if (tick == 600) {
                check(change == AdhdModifier.Change.END && !modifier.active(), "Exactly 30 seconds, without a final extra shuffle");
            } else if (tick == nextShuffle) {
                check(change == AdhdModifier.Change.SHUFFLE, "A shuffle must run at ceil(n * 200 / 3) ticks");
                shuffles++;
                nextShuffle = (shuffles * 200 + 2) / 3;
            } else {
                check(change == AdhdModifier.Change.NONE, "No unexpected item replacement");
            }
            check(modifier.shuffleProgress() >= 0 && modifier.shuffleProgress() <= 1, "HUD progress stays valid");
        }
        check(shuffles == 9, "Ten-second shuffle runs nine sets in 30 seconds at triple speed");
        idle(modifier, 1799);
        check(modifier.tick() == AdhdModifier.Change.START, "Two-minute intervals are start-to-start, not two minutes plus the burst");

        AdhdModifier fastest = new AdhdModifier(120, 30, 1, fixed(0.5));
        idle(fastest, 2399);
        check(fastest.tick() == AdhdModifier.Change.START, "Fast configuration starts normally");
        int sets = 1;
        for (int tick = 1; tick <= 600; tick++) {
            if (fastest.tick() == AdhdModifier.Change.SHUFFLE) sets++;
        }
        check(sets == 90 && !fastest.active(), "Subsecond shuffle periods retain exactly triple speed");

        for (double random : new double[] {0, Math.nextDown(1.0)}) {
            int delay = random == 0 ? 1800 : 3000;
            AdhdModifier jitter = new AdhdModifier(120, 30, 10, fixed(random));
            idle(jitter, delay - 1);
            check(jitter.tick() == AdhdModifier.Change.START, "Jitter is bounded to 90–150 seconds");
        }
        AdhdModifier repeated = new AdhdModifier(120, 30, 10, new Random(42));
        int previousStart = 0, lastStart = 0, starts = 0;
        for (int tick = 1; tick <= 120000; tick++) {
            AdhdModifier.Change change = repeated.tick();
            if (change == AdhdModifier.Change.START) {
                check(tick - previousStart >= 1800 && tick - previousStart <= 3000, "Every random start uses the configured interval bounds");
                previousStart = lastStart = tick;
                starts++;
            } else if (change == AdhdModifier.Change.END) {
                check(tick - lastStart == 600, "Repeated bursts always last exactly 600 ticks");
            }
        }
        check(starts > 30, "Several consecutive bursts were checked");
        check(AdhdModifier.eventMean(0, 30) == 10, "Classic gains rave at three times its event frequency");
        check(AdhdModifier.eventMean(60, 30) == 10, "Party gains the accelerated rave rate");
        check(AdhdModifier.eventMean(30, 30) == 10, "Rave itself is accelerated threefold");
        check(Math.abs(AdhdModifier.eventMean(0, 20) - 20.0 / 3) < 1e-9, "Use the configured rave frequency");
        check(AdhdModifier.eventMean(2, 30) == 2, "Never slow a faster existing event setting down");
        check(AdhdModifier.eventMean(0, 0) == 0, "An explicitly disabled rave stays disabled");
        eventTasks();
        System.out.println("ADHD: burst intervals/duration, exact triple shuffle pace, rave frequency and event-task cleanup verified");
    }

    private static void eventTasks() {
        var tasks = new PillarsEvents.EventTasks<PillarsEvents.Kind>();
        Cancel original = new Cancel(), warning = new Cancel(), blockBan = new Cancel(), math = new Cancel();
        tasks.selected(PillarsEvents.Kind.WIND);
        tasks.add(PillarsEvents.Kind.WIND, original.task);
        tasks.startBurst();
        tasks.selected(PillarsEvents.Kind.BLOCK_BAN);
        tasks.add(PillarsEvents.Kind.BLOCK_BAN, warning.task);
        tasks.add(PillarsEvents.Kind.BLOCK_BAN, blockBan.task);
        tasks.selected(PillarsEvents.Kind.MATH);
        tasks.add(PillarsEvents.Kind.MATH, math.task);
        check(!tasks.isBurst(PillarsEvents.Kind.WIND) && tasks.isBurst(PillarsEvents.Kind.MATH),
                "Only newly selected events belong to the burst");
        tasks.finish(PillarsEvents.Kind.MATH);
        check(math.cancelled, "Natural completion removes its delayed callbacks");
        check(tasks.stopBurst().equals(Set.of(PillarsEvents.Kind.BLOCK_BAN)), "Do not stop pre-existing or already finished events");
        for (var kind : tasks.stopBurst()) tasks.finish(kind);
        check(warning.cancelled && blockBan.cancelled && !original.cancelled,
                "Stop both the warning and delayed block destruction, preserving the pre-existing wind");
        Cancel next = new Cancel();
        tasks.selected(PillarsEvents.Kind.BLOCK_BAN);
        tasks.add(PillarsEvents.Kind.BLOCK_BAN, next.task);
        check(!tasks.isBurst(PillarsEvents.Kind.BLOCK_BAN) && tasks.stopBurst().isEmpty(),
                "An event selected after the burst has a fresh ordinary lifetime");
        tasks.close();
        check(original.cancelled && next.cancelled, "Session teardown cancels every remaining task");
    }

    private static final class Cancel {
        boolean cancelled;
        final BukkitTask task = (BukkitTask) Proxy.newProxyInstance(BukkitTask.class.getClassLoader(),
                new Class<?>[] {BukkitTask.class}, (p, method, args) -> {
                    check(method.getName().equals("cancel"), "Only task cancellation is expected");
                    cancelled = true;
                    return null;
                });
    }

    private static Random fixed(double value) {
        return new Random(0) {
            @Override public double nextDouble() { return value; }
        };
    }

    private static void idle(AdhdModifier modifier, int ticks) {
        for (int i = 0; i < ticks; i++) {
            check(modifier.tick() == AdhdModifier.Change.NONE && !modifier.active(), "Normal gameplay between bursts");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
