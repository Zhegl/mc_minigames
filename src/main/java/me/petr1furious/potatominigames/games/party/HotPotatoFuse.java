package me.petr1furious.potatominigames.games.party;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

final class HotPotatoFuse {
    private static final long TRADEBACK_TICKS = 28;
    private static final long RECOVERY_TICKS = 100;

    private final Random random;
    private final Set<UUID> seeded = new HashSet<>();
    private UUID lastSeed;
    private UUID carrier;
    private UUID previousCarrier;
    private long deadline;
    private long protectedUntil;

    HotPotatoFuse(Random random) {
        this.random = random;
    }

    void start(List<UUID> candidates, long now, int duration) {
        List<UUID> fresh = candidates.stream().filter(id -> !seeded.contains(id)).toList();
        if (fresh.isEmpty()) {
            seeded.clear();
            fresh =
                    candidates.size() <= 1
                            ? candidates
                            : candidates.stream().filter(id -> !id.equals(lastSeed)).toList();
        }
        carrier = randomCandidate(fresh);
        if (carrier != null) {
            seeded.add(carrier);
            lastSeed = carrier;
        }
        previousCarrier = null;
        deadline = now + duration;
    }

    private UUID randomCandidate(List<UUID> candidates) {
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    UUID carrier() {
        return carrier;
    }

    long remaining(long now) {
        return Math.max(0, deadline - now);
    }

    boolean pass(UUID from, UUID to, long now) {
        if (!from.equals(carrier) || from.equals(to) || remaining(now) == 0) return false;
        if (to.equals(previousCarrier) && now < protectedUntil) return false;
        carrier = to;
        previousCarrier = from;
        protectedUntil = now + TRADEBACK_TICKS;
        return true;
    }

    boolean replaceMissing(List<UUID> candidates, long now) {
        if (carrier == null ? candidates.isEmpty() : candidates.contains(carrier)) return false;
        carrier = randomCandidate(candidates);
        previousCarrier = null;
        deadline = Math.max(deadline, now + RECOVERY_TICKS);
        return true;
    }
}
