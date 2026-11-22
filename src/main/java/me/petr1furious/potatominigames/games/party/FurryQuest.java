package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Each surviving agent must finish a personal route to start the sniper's final countdown. */
final class FurryQuest {
    static final int SECONDS = 150;
    static final int FINAL_SECONDS = 10;

    enum Station {
        BARN_BARREL(16, 1, -14), LOOM(-15, 1, 3), DOCK_POST(-11, 1, -9),
        BLUE(-5, 1, 14), GOLD(-4, 1, 14), RED(-3, 1, 14), SCARECROW(18, 3, 16);

        final int x, y, z;
        Station(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        boolean chime() { return this == BLUE || this == GOLD || this == RED; }
    }

    enum Site {
        POND(-13, -10), HAY(12, -11), TREE(-12, 10), TROUGH(13, 8), BELL(1, 15),
        PARCEL(Station.BARN_BARREL, Station.LOOM), FISH(Station.DOCK_POST),
        CHIMES(Station.BLUE, Station.GOLD, Station.RED), SWAP(Station.SCARECROW);

        final int x, z;
        final List<Station> stops;
        Site(int x, int z) {
            this.x = x;
            this.z = z;
            stops = List.of();
        }
        Site(Station first, Station... next) {
            x = first.x;
            z = first.z;
            var stations = new ArrayList<>(List.of(first));
            stations.addAll(List.of(next));
            stops = List.copyOf(stations);
        }
    }

    static final int TASKS = 5;
    static final int BITE_WINDOW = 20;
    enum Interaction { IGNORED, NEXT, CAST, EARLY, WRONG_NOTE, COMPLETE }
    enum Outcome { MISSIONS, LIVESTOCK, ALL_CAUGHT, TIME, SNIPER_LEFT }

    private static final class Route {
        final List<Site> sites;
        final Random random;
        boolean active = true;
        int completed, progress;
        int previousSector = -1;
        long biteTick = -1;
        boolean crouching, jumping;

        Route(Random random) {
            this.random = random;
            var pool = new ArrayList<>(List.of(Site.values()));
            Collections.shuffle(pool, random);
            sites = List.copyOf(pool.subList(0, TASKS));
        }
        Site site() { return completed == sites.size() ? null : sites.get(completed); }
        Station station() {
            Site site = site();
            return site == null || site.stops.isEmpty() ? null : site.stops.get(progress);
        }

        Interaction click(Station clicked, boolean sneak, long tick) {
            Station expected = station();
            if (expected == null || clicked == null) return Interaction.IGNORED;
            if (clicked != expected) {
                if (site() == Site.CHIMES && clicked.chime()) {
                    progress = 0;
                    return Interaction.WRONG_NOTE;
                }
                return Interaction.IGNORED;
            }
            if (site() == Site.SWAP && !sneak) return Interaction.IGNORED;
            if (site() == Site.FISH) {
                if (biteTick < 0 || tick >= biteTick + BITE_WINDOW) {
                    biteTick = tick + 24 + random.nextInt(33);
                    return Interaction.CAST;
                }
                if (tick < biteTick) {
                    biteTick = -1;
                    return Interaction.EARLY;
                }
            }
            if (++progress < site().stops.size()) return Interaction.NEXT;
            complete();
            return Interaction.COMPLETE;
        }

        private void complete() {
            completed++;
            progress = 0;
            previousSector = -1;
            biteTick = -1;
        }

        boolean act(double dx, double dz, boolean level, boolean crouch, boolean jump) {
            boolean peck = crouch && !crouching, ring = jump && !jumping;
            crouching = crouch;
            jumping = jump;
            if (!site().stops.isEmpty()) return false;
            double radius2 = dx * dx + dz * dz;
            if (!level || radius2 > (site() == Site.TREE ? 30.25 : 16)) {
                progress = 0;
                previousSector = -1;
                return false;
            }
            boolean done = switch (site()) {
                case POND -> {
                    progress = crouch ? progress + 1 : 0;
                    yield progress >= 40;
                }
                case HAY -> peck && ++progress == 3;
                case TREE -> circle(dx, dz);
                case TROUGH -> {
                    if (Math.abs(dz) <= 2 && Math.abs(dx) >= 1.75)
                        progress |= dx < 0 ? 1 : 2;
                    yield progress == 3;
                }
                case BELL -> ring;
                case PARCEL, FISH, CHIMES, SWAP -> false;
            };
            if (done) complete();
            return done;
        }

        private boolean circle(double dx, double dz) {
            int sector = Math.floorMod((int) Math.floor(Math.atan2(dz, dx) / (Math.PI / 2) + .5), 4);
            if (previousSector != -1) {
                switch (Math.floorMod(sector - previousSector, 4)) {
                    case 1 -> progress++;
                    case 3 -> progress--;
                    case 2 -> progress = 0;
                }
            }
            previousSector = sector;
            return Math.abs(progress) >= 4;
        }
    }

    private final UUID sniper;
    private final Map<UUID, Route> routes = new LinkedHashMap<>();
    private Outcome outcome;
    private boolean finalHunt;

    FurryQuest(Collection<UUID> players, Random random) {
        if (players.size() < 2) throw new IllegalArgumentException("Furry Quest requires two players");
        var ids = new ArrayList<>(players);
        sniper = ids.remove(random.nextInt(ids.size()));
        for (UUID id : ids) routes.put(id, new Route(random));
    }

    UUID sniper() { return sniper; }
    int remaining() { return (int) routes.values().stream().filter(r -> r.active).count(); }
    int completed(UUID id) { return routes.get(id).completed; }
    Site site(UUID id) {
        Route route = routes.get(id);
        return route == null || !route.active ? null : route.site();
    }
    Outcome outcome() { return outcome; }
    Station station(UUID id) { return site(id) == null ? null : routes.get(id).station(); }
    long biteTick(UUID id) { return routes.get(id).biteTick; }
    Interaction click(UUID id, Station station, boolean sneak, long tick) {
        if (outcome != null || site(id) == null) return Interaction.IGNORED;
        return routes.get(id).click(station, sneak, tick);
    }
    boolean finalHunt() { return finalHunt; }

    boolean startFinalHuntIfReady() {
        if (finalHunt || outcome != null || routes.values().stream().anyMatch(r -> r.active && r.completed < TASKS))
            return false;
        finalHunt = true;
        return true;
    }

    boolean act(UUID id, double dx, double dz, boolean level, boolean crouch, boolean jump) {
        if (outcome != null || site(id) == null) return false;
        return routes.get(id).act(dx, dz, level, crouch, jump);
    }

    void out(UUID id) {
        if (outcome != null) return;
        if (sniper.equals(id)) outcome = Outcome.SNIPER_LEFT;
        else if (routes.containsKey(id)) {
            routes.get(id).active = false;
            if (remaining() == 0) outcome = Outcome.ALL_CAUGHT;
        }
    }

    void wrongShot() {
        if (outcome == null) outcome = Outcome.LIVESTOCK;
    }

    void timeout() {
        if (outcome == null) outcome = finalHunt ? Outcome.MISSIONS : Outcome.TIME;
    }

    PartyRoundResult result(Set<UUID> eligible) {
        if (outcome == null) return PartyRoundResult.empty();
        List<UUID> winners = switch (outcome) {
            case ALL_CAUGHT, TIME -> List.of(sniper);
            case MISSIONS, LIVESTOCK, SNIPER_LEFT -> routes.entrySet().stream()
                    .filter(e -> !finalHunt || e.getValue().active).map(Map.Entry::getKey).toList();
        };
        return new PartyRoundResult(List.of(winners)).eligible(eligible);
    }
}
