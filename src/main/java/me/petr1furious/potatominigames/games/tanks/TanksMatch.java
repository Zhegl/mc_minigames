package me.petr1furious.potatominigames.games.tanks;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One-life battle state. Disconnected actors retain their vulnerable physical tank. */
public final class TanksMatch {
    public static final int ACTIVE = -2, DRAW = -1;
    private final Map<UUID, Actor> actors = new LinkedHashMap<>();
    private final int battleTicks, captureRequired;
    private final boolean practice;
    private boolean started, twoTeams;
    private int elapsed, winner = ACTIVE;

    public TanksMatch(int battleTicks, int captureTicks, boolean practice) {
        if (battleTicks <= 0 || captureTicks <= 0)
            throw new IllegalArgumentException("Nonpositive battle duration");
        this.battleTicks = battleTicks;
        this.captureRequired = captureTicks;
        this.practice = practice;
    }

    public Actor add(UUID id, int team, TanksRules.Chassis chassis) {
        if (started) throw new IllegalStateException("Battle already started");
        if (team < 0 || team > 1 || actors.size() >= 12)
            throw new IllegalArgumentException("Invalid tank roster");
        Objects.requireNonNull(id, "id");
        if (actors.containsKey(id)) throw new IllegalArgumentException("Duplicate tank actor");
        var actor = new Actor(id, team, Objects.requireNonNull(chassis, "chassis"));
        actors.put(id, actor);
        return actor;
    }

    public Actor actor(UUID id) {
        return actors.get(id);
    }

    public Collection<Actor> actors() {
        return Collections.unmodifiableCollection(actors.values());
    }

    public boolean started() {
        return started;
    }

    public boolean finished() {
        return winner != ACTIVE;
    }

    public int winner() {
        return winner;
    }

    public int ticksRemaining() {
        return Math.max(0, battleTicks - elapsed);
    }

    public boolean practice() {
        return practice;
    }

    public void start() {
        if (started) throw new IllegalStateException("Battle already started");
        boolean red = actors.values().stream().anyMatch(a -> a.team == 0 && a.alive);
        boolean blue = actors.values().stream().anyMatch(a -> a.team == 1 && a.alive);
        twoTeams = red && blue;
        if (actors.isEmpty() || (!practice && !twoTeams))
            throw new IllegalStateException("Battle requires both teams");
        started = true;
    }

    public void tick() {
        if (!started || finished()) return;
        for (Actor actor : actors.values()) {
            if (actor.reloadTicks > 0) actor.reloadTicks--;
            if (actor.trackTicks > 0) actor.trackTicks--;
            if (actor.smokeTicks > 0) actor.smokeTicks--;
        }
        boolean red = actors.values().stream().anyMatch(a -> a.team == 0 && a.alive);
        boolean blue = actors.values().stream().anyMatch(a -> a.team == 1 && a.alive);
        if (twoTeams && (!red || !blue)) {
            winner = red == blue ? DRAW : red ? 0 : 1;
            return;
        }
        if (!twoTeams && !red && !blue) {
            winner = DRAW;
            return;
        }
        for (int team = 0; team < 2; team++) {
            int base = 1 - team;
            boolean contested =
                    actors.values().stream()
                            .anyMatch(a -> a.alive && a.team == base && a.base == base);
            if (contested) continue;
            int contributors = 0;
            for (Actor actor : actors.values()) {
                if (actor.team == team
                        && actor.alive
                        && actor.online
                        && actor.base == base
                        && contributors++ < 3)
                    actor.captureTicks = Math.min(captureRequired, actor.captureTicks + 1);
            }
        }
        boolean redCapture = captureProgress(0) >= 1;
        boolean blueCapture = captureProgress(1) >= 1;
        if (redCapture || blueCapture)
            winner = redCapture == blueCapture ? DRAW : redCapture ? 0 : 1;
        elapsed++;
        if (elapsed >= battleTicks && winner == ACTIVE) winner = DRAW;
    }

    /** Progress belongs to the capturing team, so team zero captures the blue (one) base. */
    public double captureProgress(int team) {
        checkTeam(team);
        long total =
                actors.values().stream()
                        .filter(a -> a.team == team && a.alive && a.base == 1 - team)
                        .mapToLong(a -> a.captureTicks)
                        .sum();
        return Math.min(1, (double) total / captureRequired);
    }

    /**
     * Friendly or self hits cannot damage modules or reset capture. Offline targets remain valid.
     */
    public double recordHit(UUID attackerId, UUID victimId, TanksRules.Hit hit, int trackTicks) {
        Objects.requireNonNull(hit, "hit");
        Actor attacker = actor(attackerId), victim = actor(victimId);
        if (!started
                || finished()
                || attacker == null
                || victim == null
                || !victim.alive
                || attacker.team == victim.team) return 0;
        double actual = victim.damage(hit.damage());
        if (actual > 0 && !victim.alive) attacker.kills++;
        if (victim.alive && trackTicks > 0) victim.damageTrack(trackTicks);
        return actual;
    }

    /** Core scoring applies this value once when the session ends. */
    public int reward(UUID id, int winPoints, int killPoints, int participationPoints) {
        Actor actor = actor(id);
        if (practice || actor == null || !finished() || actor.left) return 0;
        long points =
                Math.max(0, participationPoints)
                        + (long) actor.kills * Math.max(0, killPoints)
                        + (winner == actor.team ? Math.max(0, winPoints) : 0);
        return (int) Math.min(Integer.MAX_VALUE, points);
    }

    private static void checkTeam(int team) {
        if (team < 0 || team > 1) throw new IllegalArgumentException("Invalid team");
    }

    public final class Actor {
        private final UUID id;
        private final int team;
        private TanksRules.Chassis chassis;
        private TanksRules.Shell shell = TanksRules.Shell.AP;
        private double hp;
        private boolean alive = true, online = true, left;
        private int reloadTicks, trackTicks, smokeTicks, kills, captureTicks, base = -1;
        private int repairKits = 1, smokeCharges = 1;

        private Actor(UUID id, int team, TanksRules.Chassis chassis) {
            this.id = id;
            this.team = team;
            this.chassis = chassis;
            this.hp = chassis.maxHp();
        }

        public UUID id() {
            return id;
        }

        public int team() {
            return team;
        }

        public TanksRules.Chassis chassis() {
            return chassis;
        }

        public TanksRules.Shell shell() {
            return shell;
        }

        public double hp() {
            return hp;
        }

        public boolean alive() {
            return alive;
        }

        public boolean online() {
            return online;
        }

        public int reloadTicks() {
            return reloadTicks;
        }

        public int trackTicks() {
            return trackTicks;
        }

        public int repairKits() {
            return repairKits;
        }

        public int smokeCharges() {
            return smokeCharges;
        }

        public int smokeTicks() {
            return smokeTicks;
        }

        public int kills() {
            return kills;
        }

        public int captureTicks() {
            return captureTicks;
        }

        public int base() {
            return base;
        }

        public boolean selectChassis(TanksRules.Chassis choice) {
            Objects.requireNonNull(choice, "chassis");
            if (started || left) return false;
            chassis = choice;
            hp = choice.maxHp();
            return true;
        }

        public void selectShell(TanksRules.Shell choice) {
            Objects.requireNonNull(choice, "shell");
            if (alive && online && !finished()) shell = choice;
        }

        private boolean controls() {
            return started && !finished() && alive && online && !left;
        }

        public boolean fire() {
            if (!controls() || reloadTicks > 0) return false;
            reloadTicks = chassis.reloadTicks();
            return true;
        }

        /** Returns effective HP damage. The match records attribution separately. */
        public double damage(double amount) {
            if (!Double.isFinite(amount) || amount < 0)
                throw new IllegalArgumentException("Invalid damage");
            if (!started || finished() || !alive || amount == 0) return 0;
            double actual = Math.min(hp, amount);
            hp -= actual;
            captureTicks = 0;
            if (hp <= 0) destroy();
            return actual;
        }

        public void damageTrack(int ticks) {
            if (ticks < 0) throw new IllegalArgumentException("Negative module duration");
            if (started && !finished() && alive && ticks > 0) {
                trackTicks = Math.max(trackTicks, ticks);
                captureTicks = 0;
            }
        }

        public boolean repair() {
            if (!controls() || repairKits <= 0 || (trackTicks == 0 && hp >= chassis.maxHp()))
                return false;
            repairKits--;
            trackTicks = 0;
            hp = Math.min(chassis.maxHp(), hp + chassis.maxHp() * .15);
            return true;
        }

        public boolean smoke() {
            if (!controls() || smokeCharges <= 0) return false;
            smokeCharges--;
            smokeTicks = 120;
            return true;
        }

        public void disconnect() {
            online = false;
        }

        public void reconnect() {
            if (!left) online = true;
        }

        public void leave() {
            left = true;
            online = false;
            hp = 0;
            destroy();
        }

        private void destroy() {
            alive = false;
            captureTicks = 0;
            trackTicks = 0;
            smokeTicks = 0;
            base = -1;
        }

        public void base(int baseTeam) {
            if (baseTeam < -1 || baseTeam > 1) throw new IllegalArgumentException("Invalid base");
            if (base != baseTeam) captureTicks = 0;
            base = alive ? baseTeam : -1;
        }

        public void inEnemyBase(boolean inside) {
            base(inside ? 1 - team : -1);
        }
    }
}
