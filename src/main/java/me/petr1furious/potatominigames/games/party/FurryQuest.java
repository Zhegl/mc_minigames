package me.petr1furious.potatominigames.games.party;

import java.util.*;
import me.petr1furious.potatominigames.games.party.FurryQuestArena.Site;
import org.bukkit.Material;

final class FurryQuest {
    static final int SECONDS = 105;
    static final int TASK_POINTS = 4;
    enum Action { BUILD, CAST, CHOP, CLIMB, DIG, HARVEST, LIGHT, NOTE, REEL, SIGHT, SIGN, SNEAK, SOW, TEND, USE }
    record Step(Action action, Site site, Material item) {}

    enum Task {
        TIMBER(new Step(Action.CHOP, Site.STUMP_W, Material.IRON_AXE),
                new Step(Action.CHOP, Site.STUMP_E, Material.IRON_AXE)),
        SAW(new Step(Action.USE, Site.BENCH, Material.OAK_LOG), new Step(Action.TEND, Site.SAW, Material.OAK_LOG)),
        FENCE(new Step(Action.BUILD, Site.POST_A, Material.OAK_FENCE),
                new Step(Action.BUILD, Site.POST_B, Material.OAK_FENCE)),
        SIGN(new Step(Action.USE, Site.SHELF, Material.OAK_SIGN),
                new Step(Action.SIGN, Site.SIGNPOST, Material.OAK_SIGN)),
        HARVEST(new Step(Action.HARVEST, Site.WHEAT, Material.IRON_HOE),
                new Step(Action.SOW, Site.SOIL, Material.WHEAT_SEEDS)),
        FLOUR(new Step(Action.CLIMB, Site.AXLE, Material.WHEAT),
                new Step(Action.TEND, Site.GRIND, Material.WHEAT)),
        DOUGH(new Step(Action.USE, Site.WELL, Material.BUCKET), new Step(Action.TEND, Site.KNEAD, Material.WHEAT)),
        BREAD(new Step(Action.TEND, Site.OVEN, Material.WHEAT), new Step(Action.USE, Site.MARKET, Material.BREAD)),
        WINDMILL(new Step(Action.USE, Site.GEAR, Material.AIR),
                new Step(Action.CLIMB, Site.AXLE, Material.COPPER_INGOT)),
        WIRE(new Step(Action.USE, Site.WIREBOX, Material.SHEARS),
                new Step(Action.USE, Site.SWITCH, Material.COPPER_INGOT)),
        FUSE(new Step(Action.USE, Site.SHELF, Material.AIR),
                new Step(Action.TEND, Site.ENGINE, Material.REDSTONE)),
        POWER(new Step(Action.USE, Site.SWITCH, Material.LEVER),
                new Step(Action.SIGHT, Site.BEACON, Material.SPYGLASS)),
        PRINT(new Step(Action.SNEAK, Site.CRATE, Material.BRUSH),
                new Step(Action.USE, Site.ARCHIVE, Material.PAPER)),
        TRACKS(new Step(Action.SNEAK, Site.TRACK, Material.BRUSH),
                new Step(Action.SIGHT, Site.FLAG, Material.SPYGLASS)),
        EVIDENCE(new Step(Action.DIG, Site.DIRT_A, Material.IRON_SHOVEL),
                new Step(Action.USE, Site.DESK, Material.BUNDLE)),
        REPORT(new Step(Action.USE, Site.ARCHIVE, Material.WRITABLE_BOOK),
                new Step(Action.USE, Site.MAIL, Material.WRITTEN_BOOK)),
        PARCEL(new Step(Action.USE, Site.PARCEL, Material.AIR),
                new Step(Action.USE, Site.LOOM, Material.WHITE_WOOL)),
        WRAP(new Step(Action.USE, Site.DYE, Material.WHITE_WOOL),
                new Step(Action.SNEAK, Site.CRATE, Material.STRING)),
        PASSWORD(new Step(Action.NOTE, Site.LOW, Material.AIR), new Step(Action.NOTE, Site.MID, Material.AIR)),
        STASH(new Step(Action.DIG, Site.DIRT_B, Material.IRON_SHOVEL),
                new Step(Action.SNEAK, Site.DROP, Material.BUNDLE)),
        STRINGS(new Step(Action.USE, Site.LOOM, Material.SHEARS),
                new Step(Action.TEND, Site.BENCH, Material.STRING)),
        TUNE(new Step(Action.NOTE, Site.MID, Material.AIR), new Step(Action.NOTE, Site.LOW, Material.AIR)),
        POSTERS(new Step(Action.USE, Site.DESK, Material.PAPER), new Step(Action.USE, Site.BOARD, Material.MAP)),
        CHIMES(new Step(Action.NOTE, Site.LOW, Material.AIR), new Step(Action.NOTE, Site.HIGH, Material.AIR),
                new Step(Action.NOTE, Site.MID, Material.AIR)),
        BAIT(new Step(Action.DIG, Site.DIRT_C, Material.IRON_SHOVEL),
                new Step(Action.USE, Site.TACKLE, Material.STRING)),
        FISH(new Step(Action.CAST, Site.POND, Material.FISHING_ROD),
                new Step(Action.REEL, Site.POND, Material.FISHING_ROD)),
        SMOKE(new Step(Action.USE, Site.COAL, Material.AIR), new Step(Action.TEND, Site.SMOKER, Material.COD)),
        SUPPER(new Step(Action.USE, Site.KNEAD, Material.COOKED_COD),
                new Step(Action.USE, Site.TABLE, Material.COOKED_COD)),
        BEARING(new Step(Action.CLIMB, Site.AXLE, Material.COMPASS),
                new Step(Action.SIGHT, Site.SURVEY_FLAG, Material.SPYGLASS)),
        FORD(new Step(Action.SNEAK, Site.FORD, Material.COMPASS), new Step(Action.USE, Site.DESK, Material.MAP)),
        LANTERN(new Step(Action.USE, Site.COAL, Material.STICK), new Step(Action.USE, Site.BENCH, Material.COAL),
                new Step(Action.LIGHT, Site.LAMP, Material.TORCH)),
        ATLAS(new Step(Action.SIGHT, Site.BEACON, Material.SPYGLASS),
                new Step(Action.USE, Site.BOARD, Material.FILLED_MAP));
        final List<Step> steps;
        Task(Step... steps) {
            this.steps = List.of(steps);
        }
    }

    enum Role {
        CARPENTER(Task.TIMBER, Task.SAW, Task.FENCE, Task.SIGN),
        MILLER(Task.HARVEST, Task.FLOUR, Task.DOUGH, Task.BREAD),
        MECHANIC(Task.WINDMILL, Task.WIRE, Task.FUSE, Task.POWER),
        SLEUTH(Task.PRINT, Task.TRACKS, Task.EVIDENCE, Task.REPORT),
        RUNNER(Task.PARCEL, Task.WRAP, Task.PASSWORD, Task.STASH),
        BARD(Task.STRINGS, Task.TUNE, Task.POSTERS, Task.CHIMES),
        ANGLER(Task.BAIT, Task.FISH, Task.SMOKE, Task.SUPPER),
        SCOUT(Task.BEARING, Task.FORD, Task.LANTERN, Task.ATLAS);
        final List<Task> jobs;
        Role(Task... jobs) {
            this.jobs = List.of(jobs);
        }
    }
    enum Hit { WAIT, MISS, KILL }

    private static final class Progress {
        int evidence;
        final List<Site> melody = new ArrayList<>(List.of(Site.LOW, Site.MID, Site.HIGH));
        final List<Role> roles = new ArrayList<>(List.of(Role.values()));
        int roleIndex;
        int index, step, score;
        long ready = 80, safe = 80;
    }

    private final Map<UUID, Progress> players = new LinkedHashMap<>();
    private final Random random;

    FurryQuest(Collection<UUID> roster, Random random) {
        this.random = random;
        var opening = new ArrayList<>(List.of(Role.values()));
        int dealt = 0;
        for (UUID id : roster) {
            Progress p = new Progress();
            if (dealt % opening.size() == 0)
                Collections.shuffle(opening, random);
            Collections.shuffle(p.roles, random);
            Collections.swap(p.roles, 0, p.roles.indexOf(opening.get(dealt++ % opening.size())));
            players.put(id, p);
            drawClues(p);
        }
    }

    private void drawClues(Progress p) {
        Collections.shuffle(p.melody, random);
        p.evidence = random.nextInt(3);
    }

    List<Site> melody(UUID id) {
        return List.copyOf(players.get(id).melody);
    }
    int evidence(UUID id) {
        return players.get(id).evidence;
    }
    boolean matchesNote(UUID id, Site pressed) {
        Progress p = players.get(id);
        if (task(id) != Task.CHIMES)
            return false;
        if (p.melody.get(p.step) == pressed)
            return true;
        p.step = 0;
        return false;
    }

    Task task(UUID id) {
        Progress p = players.get(id);
        return p == null ? null : role(id).jobs.get(p.index);
    }

    Role role(UUID id) {
        Progress p = players.get(id);
        return p.roles.get(p.roleIndex);
    }

    int chapter(UUID id) {
        return players.get(id).index + 1;
    }
    Step objective(UUID id) {
        return task(id).steps.get(step(id));
    }

    int score(UUID id) {
        return players.get(id).score;
    }
    int step(UUID id) {
        return players.get(id).step;
    }
    long waitTicks(UUID id, long tick) {
        return Math.max(0, players.get(id).ready - tick);
    }

    boolean advance(UUID id) {
        Progress p = players.get(id);
        Task finished = task(id);
        if (++p.step < finished.steps.size())
            return false;
        p.step = 0;
        p.score += TASK_POINTS;
        if (++p.index == role(id).jobs.size()) {
            p.index = 0;
            Role previous = role(id);
            drawClues(p);
            if (++p.roleIndex == p.roles.size()) {
                p.roleIndex = 0;
                Collections.shuffle(p.roles, random);
                if (p.roles.getFirst() == previous)
                    Collections.swap(p.roles, 0, 1);
            }
        }
        return true;
    }

    Hit hit(UUID hunter, UUID target, long tick) {
        Progress p = players.get(hunter);
        Progress victim = players.get(target);
        if (p == null || tick < p.ready || hunter.equals(target) || (victim != null && tick < victim.safe))
            return Hit.WAIT;
        if (victim == null) {
            p.score -= 3;
            p.ready = tick + 100;
            return Hit.MISS;
        }
        p.score += 3;
        p.ready = tick + 40;
        victim.score -= 2;
        victim.ready = victim.safe = tick + 80;
        return Hit.KILL;
    }

    void withdraw(UUID id) {
        players.remove(id);
    }

    PartyRoundResult result(Collection<UUID> eligible) {
        return PartyRoundResult.ordered(eligible.stream().filter(players::containsKey).toList(),
                Comparator.comparingInt(this::score).reversed());
    }
}
