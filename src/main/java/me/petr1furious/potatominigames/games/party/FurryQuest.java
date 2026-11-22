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

/** Each agent owns a route. Catching one cannot erase the team's unfinished work. */
final class FurryQuest {
    static final int SECONDS = 150;

    enum Site {
        POND(-13, -10), HAY(12, -11), TREE(-12, 10), TROUGH(13, 8), BELL(1, 15);

        final int x, z;
        Site(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    static final int TASKS = Site.values().length;
    enum Outcome { MISSIONS, LIVESTOCK, ALL_CAUGHT, TIME, SNIPER_LEFT }

    private static final class Route {
        final List<Site> sites = new ArrayList<>(List.of(Site.values()));
        boolean active = true;
        int completed, progress;
        int previousSector = -1;
        boolean crouching, jumping;

        Route(Random random) { Collections.shuffle(sites, random); }
        Site site() { return completed == sites.size() ? null : sites.get(completed); }

        boolean act(double dx, double dz, boolean level, boolean crouch, boolean jump) {
            boolean peck = crouch && !crouching, ring = jump && !jumping;
            crouching = crouch;
            jumping = jump;
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
            };
            if (done) {
                completed++;
                progress = 0;
                previousSector = -1;
            }
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

    boolean act(UUID id, double dx, double dz, boolean level, boolean crouch, boolean jump) {
        if (outcome != null || site(id) == null) return false;
        if (!routes.get(id).act(dx, dz, level, crouch, jump)) return false;
        if (routes.values().stream().allMatch(r -> r.completed == TASKS)) outcome = Outcome.MISSIONS;
        return true;
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
        if (outcome == null) outcome = Outcome.TIME;
    }

    PartyRoundResult result(Set<UUID> eligible) {
        if (outcome == null) return PartyRoundResult.empty();
        List<UUID> winners = switch (outcome) {
            case ALL_CAUGHT, TIME -> List.of(sniper);
            default -> List.copyOf(routes.keySet());
        };
        return new PartyRoundResult(List.of(winners)).eligible(eligible);
    }
}
