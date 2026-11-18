package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.games.party.DropTowerCourse.Pad;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

final class DropTowerRoutesCheck {
    private static final int DEFAULT_SEEDS = 128;
    private static final double HALF_WIDTH = .3;
    private static final double HEIGHT = 1.8;
    private static final double EPSILON = 1e-7;
    private static final double[] DETOURS = {0, 1.5, -1.5, 3, -3, .75, -.75, 2.25, -2.25};
    private static final double[] STAGING_OFFSETS = {0, -.4, .4};
    private static final boolean[] SPRINTING = {true, false};
    private static final int[] BRAKE_TICKS = {5, 0, 9, -1, 2};

    public static void main(String[] args) {
        int seeds = args.length == 0 ? DEFAULT_SEEDS : Integer.parseInt(args[0]);
        generatedCourses(seeds);
        System.out.println("Reachable routes for " + seeds + " generated Drop Tower courses");
    }

    static void generatedCourses() {
        generatedCourses(DEFAULT_SEEDS);
    }

    private static void generatedCourses(int seeds) {
        if (seeds <= 0) throw new IllegalArgumentException("Expected a positive seed count");
        Set<String> layouts = new HashSet<>(), allFootprints = new HashSet<>();
        for (int seed = 0; seed < seeds; seed++) {
            var course = DropTowerCourse.generate(new Random(seed));
            require(course.pads().size() == 21 && course.jumps().size() == 20,
                    "Expected twenty jumps and a starting platform", seed);
            Set<String> footprints = new HashSet<>(), kinds = new HashSet<>();
            StringBuilder layout = new StringBuilder();
            int[] landingArea = new int[4];
            for (int i = 0; i < course.pads().size(); i++) {
                Pad pad = course.pads().get(i);
                String footprint = footprint(pad, seed);
                footprints.add(footprint);
                int area = (int) footprint.chars().filter(c -> c == '#').count();
                if (i > 0) landingArea[(i - 1) / 5] += area;
                layout.append(pad.x()).append(',').append(pad.z()).append(',')
                        .append(pad.floor()).append('/').append(footprint).append(';');
            }
            for (int band = 1; band < landingArea.length; band++)
                require(landingArea[band] < landingArea[band - 1],
                        "Landing area should shrink with each five-stage difficulty band", seed);
            require(footprints.size() >= 3, "One race should offer several different footprints", seed);
            allFootprints.addAll(footprints);
            layouts.add(layout.toString());
            Terrain terrain = new Terrain(course);
            boolean hasGap = false, hasDrop = false, hasObstacle = false;
            for (int i = 0; i < course.jumps().size(); i++) {
                var jump = course.jumps().get(i);
                String kind = jump.kind().name();
                kinds.add(kind);
                Pad start = course.pads().get(i), goal = course.pads().get(i + 1);
                boolean gap = kind.equals("GAP") || kind.equals("DIAGONAL_GAP");
                if (gap) {
                    hasGap = true;
                    require(start.floor() == goal.floor() && separatedByAir(start, goal),
                            "A marked gap must physically require jumping at stage " + (i + 1), seed);
                } else {
                    hasDrop = true;
                    require(start.floor() > goal.floor(), "A drop must descend at stage " + (i + 1), seed);
                }
                if (!jump.baffles().isEmpty()) hasObstacle = true;
                terrain.openings = openings(jump);
                if (kind.equals("OFFSET_GATE")) {
                    require(terrain.openings.size() == 1, "An offset gate must have an opening", seed);
                    Opening gate = terrain.openings.getFirst();
                    double x = gate.travelsAlongX() ? gate.x() : start.x() + .5;
                    double z = gate.travelsAlongX() ? start.z() + .5 : gate.z();
                    require(terrain.touching(x, gate.floor(), z, 0),
                            "A straight run through the middle must hit the offset gate", seed);
                }
                if (kind.equals("SLALOM")) {
                    require(terrain.openings.size() == 2, "A slalom needs two openings", seed);
                    Opening upper = terrain.openings.getFirst(), lower = terrain.openings.getLast();
                    require(upper.floor() > lower.floor() && upper.travelsAlongX() == lower.travelsAlongX()
                                    && (upper.highSide() <= lower.lowSide() || lower.highSide() <= upper.lowSide()),
                            "Slalom openings must require a change of line between heights", seed);
                }
                int gateFloor = jump.baffles().stream().mapToInt(b -> b.floor()).min().orElse(start.floor());
                if (!hasRoute(terrain, start, goal, i, gap, gateFloor)) {
                    throw new AssertionError("No playable trajectory for seed " + seed + ", stage " + (i + 1)
                            + " (" + kind + ") from " + start + " to " + goal + ", baffles " + jump.baffles());
                }
            }
            require(kinds.size() >= 4 && hasGap && hasDrop && hasObstacle,
                    "Each race should vary the actual jumping and steering required", seed);
        }
        require(layouts.size() >= seeds / 2, "Tower geometry must vary between sessions", -1);
        require(allFootprints.size() >= 5, "Generated platforms need a variety of physical footprints", -1);
    }

    private record Cell(int x, int z) {}

    private static String footprint(Pad pad, int seed) {
        int radius = pad.radius();
        Set<Cell> tiles = new HashSet<>();
        StringBuilder mask = new StringBuilder();
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                boolean tile = pad.tile(x, z);
                mask.append(tile ? '#' : '.');
                if (tile) {
                    tiles.add(new Cell(x, z));
                    var lane = DropTowerCourse.Lane.at(0);
                    require(lane.contains(pad.x() + x, pad.z() + z)
                                    && lane.contains(pad.x() + x + 1, pad.z() + z + 1),
                            "Checkpoint blocks must stay in the player's lane", seed);
                }
            }
            mask.append('/');
        }
        // Every staging tile used by the trajectory search is walkable from the saved checkpoint.
        Cell center = new Cell(0, 0);
        require(tiles.remove(center), "Checkpoint respawn must have solid ground", seed);
        ArrayDeque<Cell> todo = new ArrayDeque<>();
        todo.add(center);
        while (!todo.isEmpty()) {
            Cell cell = todo.removeFirst();
            for (Cell step : new Cell[] {new Cell(1, 0), new Cell(-1, 0), new Cell(0, 1), new Cell(0, -1)}) {
                Cell neighbor = new Cell(cell.x + step.x, cell.z + step.z);
                if (tiles.remove(neighbor)) todo.add(neighbor);
            }
        }
        require(tiles.isEmpty(), "Every part of the platform must connect to its checkpoint", seed);
        double top = pad.floor() + 1;
        for (double x = -radius - .75; x < radius + 1.9; x += .5) {
            for (double z = -radius - .75; z < radius + 1.9; z += .5) {
                boolean standing = false;
                for (int dx = floor(x - HALF_WIDTH); dx <= floor(x + HALF_WIDTH); dx++)
                    for (int dz = floor(z - HALF_WIDTH); dz <= floor(z + HALF_WIDTH); dz++)
                        standing |= pad.tile(dx, dz);
                if (pad.landed(pad.x() + x, top, pad.z() + z) != standing)
                    throw new AssertionError("Landing does not match player footprint on " + pad + " (seed " + seed + ")");
            }
        }
        require(!pad.landed(pad.x() + .5, top + .2, pad.z() + .5)
                        && !pad.missed(top + .2) && pad.missed(top - .2),
                "Airborne approaches and missed landings must be distinct", seed);
        return mask.toString();
    }

    private static boolean separatedByAir(Pad a, Pad b) {
        for (int x = -a.radius(); x <= a.radius(); x++)
            for (int z = -a.radius(); z <= a.radius(); z++)
                if (a.tile(x, z)) {
                    int rx = a.x() + x - b.x(), rz = a.z() + z - b.z();
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++)
                            if (b.tile(rx + dx, rz + dz)) return false;
                }
        return true;
    }

    /** A player passes a pair of bars only while their body actually occupies its opening. */
    private record Opening(int floor, double x, double z, boolean travelsAlongX,
            int lowSide, int highSide) {
        boolean contains(double px, double pz) {
            double forward = travelsAlongX ? px - x : pz - z;
            double side = travelsAlongX ? pz : px;
            return Math.abs(forward) < .5 + HALF_WIDTH
                    && side - HALF_WIDTH >= lowSide && side + HALF_WIDTH <= highSide;
        }
    }

    private static List<Opening> openings(DropTowerCourse.Jump jump) {
        var result = new ArrayList<Opening>();
        for (var left : jump.baffles()) {
            for (var right : jump.baffles()) {
                if (left == right || left.floor() != right.floor()) continue;
                boolean alongX = left.minX() == right.minX() && left.maxX() == right.maxX();
                if (!alongX && (left.minZ() != right.minZ() || left.maxZ() != right.maxZ())) continue;
                int low = (alongX ? left.maxZ() : left.maxX()) + 1;
                int high = alongX ? right.minZ() : right.minX();
                if (low >= high) continue;
                double x = alongX ? left.minX() + .5 : (low + high) * .5;
                double z = alongX ? (low + high) * .5 : left.minZ() + .5;
                result.add(new Opening(left.floor(), x, z, alongX, low, high));
            }
        }
        result.sort((a, b) -> Integer.compare(b.floor(), a.floor()));
        return result;
    }

    /** All solid blocks in the lane, including previous/future pads and other stages' baffles. */
    private static final class Terrain {
        private final int offset = DropTowerCourse.LANE_RADIUS + 1;
        private final int side = offset * 2 + 1;
        private final int bottom;
        private final int[][] levels;
        private List<Opening> openings = List.of();

        Terrain(DropTowerCourse course) {
            bottom = course.pads().getLast().floor();
            levels = new int[course.pads().getFirst().floor() - bottom + 5][];
            for (int i = 0; i < course.pads().size(); i++) {
                Pad pad = course.pads().get(i);
                for (int x = -pad.radius(); x <= pad.radius(); x++)
                    for (int z = -pad.radius(); z <= pad.radius(); z++)
                        if (pad.tile(x, z)) put(pad.x() + x, pad.floor(), pad.z() + z, i + 1);
            }
            for (var jump : course.jumps())
                for (var baffle : jump.baffles())
                    for (int x = -offset; x <= offset; x++)
                        for (int z = -offset; z <= offset; z++)
                            if (baffle.overlaps(x + .5, z + .5)) put(x, baffle.floor(), z, -1);
        }

        private void put(int x, int y, int z, int value) {
            int level = y - bottom;
            if (levels[level] == null) levels[level] = new int[side * side];
            int cell = (x + offset) * side + z + offset;
            int old = levels[level][cell];
            if (old != 0 && old != value) throw new AssertionError("Overlapping course elements at " + x + "," + y + "," + z);
            levels[level][cell] = value;
        }

        boolean touching(double x, int y, double z, int padId) {
            int level = y - bottom;
            if (level < 0 || level >= levels.length || levels[level] == null) return false;
            int[] blocks = levels[level];
            for (int bx = floor(x - HALF_WIDTH + EPSILON); bx <= floor(x + HALF_WIDTH - EPSILON); bx++) {
                if (Math.abs(bx) > offset) continue;
                for (int bz = floor(z - HALF_WIDTH + EPSILON); bz <= floor(z + HALF_WIDTH - EPSILON); bz++) {
                    if (Math.abs(bz) > offset) continue;
                    int block = blocks[(bx + offset) * side + bz + offset];
                    if (padId == 0 ? block != 0 : block == padId) return true;
                }
            }
            return false;
        }

        boolean clear(double x, double y, double z) {
            if (Math.abs(x - .5) >= DropTowerCourse.LANE_RADIUS || Math.abs(z - .5) >= DropTowerCourse.LANE_RADIUS)
                return false;
            for (int floor = floor(y + EPSILON); floor <= floor(y + HEIGHT - EPSILON); floor++)
                if (touching(x, floor, z, 0)) return false;
            return true;
        }
    }

    private static boolean hasRoute(Terrain terrain, Pad start, Pad goal, int stage, boolean gap, int gateFloor) {
        // Begin at rest on a reachable tile. Try the center before walking to other staging tiles.
        for (int ring = 0; ring <= start.radius(); ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || !start.tile(dx, dz)) continue;
                    double x = start.x() + dx + .5, z = start.z() + dz + .5;
                    for (double detour : DETOURS) {
                        double gx = goal.x() + .5, gz = goal.z() + .5;
                        double distance = Math.hypot(gx - x, gz - z);
                        if (distance < EPSILON) continue;
                        double aimX = gx - detour * (gz - z) / distance;
                        double aimZ = gz + detour * (gx - x) / distance;
                        double heading = Math.hypot(aimX - x, aimZ - z);
                        double forwardX = (aimX - x) / heading, forwardZ = (aimZ - z) / heading;
                        for (double offset : STAGING_OFFSETS) {
                            for (double side : STAGING_OFFSETS) {
                                for (boolean sprint : SPRINTING) {
                                    if (runUp(terrain, start, goal, stage, gap, gateFloor, sprint,
                                            x + offset * forwardX - side * forwardZ, z + offset * forwardZ + side * forwardX,
                                            forwardX, forwardZ, aimX, aimZ)) return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean runUp(Terrain terrain, Pad start, Pad goal, int stage, boolean gap, int gateFloor,
            boolean sprint, double x, double z, double dx, double dz, double aimX, double aimZ) {
        double speed = 0, feet = start.floor() + 1;
        double acceleration = sprint ? .1274 : .098;
        for (int groundTick = 0; groundTick < 90; groundTick++) {
            if (!terrain.clear(x, feet, z)) return false;
            speed += acceleration;
            // Each valid takeoff tick is an alternative player input, with the vanilla jump impulse.
            if (groundTick > 0 || !sprint) {
                double jumpSpeed = speed + (sprint ? .2 : 0);
                if (flight(terrain, goal, stage, gateFloor, sprint, aimX, aimZ, x, feet, z,
                        dx * jumpSpeed, .42, dz * jumpSpeed, true)) return true;
            }
            double nx = x + speed * dx, nz = z + speed * dz;
            if (!terrain.clear(nx, feet, nz)) return false;
            speed *= .546;
            if (!terrain.touching(nx, start.floor(), nz, stage + 1)) {
                // Walking off a ledge starts the next tick airborne, with gravity already applied.
                return !gap && flight(terrain, goal, stage, gateFloor, sprint, aimX, aimZ,
                        nx, feet, nz, dx * speed, -.0784, dz * speed, false);
            }
            x = nx;
            z = nz;
        }
        return false;
    }

    private static boolean flight(Terrain terrain, Pad goal, int stage, int gateFloor, boolean sprint,
            double aimX, double aimZ, double x, double y, double z,
            double vx, double vy, double vz, boolean jumping) {
        // Aim at the opening until the player's head has cleared it. A longer lookahead starts
        // braking earlier; inputs always have ordinary vanilla walk/sprint acceleration.
        for (int brakeAhead : BRAKE_TICKS) {
            if (tryFlight(terrain, goal, stage, gateFloor, sprint, aimX, aimZ,
                    x, y, z, vx, vy, vz, jumping, brakeAhead)) return true;
        }
        return false;
    }

    private static boolean tryFlight(Terrain terrain, Pad goal, int stage, int gateFloor, boolean sprint,
            double aimX, double aimZ, double x, double y, double z,
            double vx, double vy, double vz, boolean jumping, int brakeAhead) {
        int passed = 0;
        for (int tick = 0; tick < 70 && y >= goal.floor() + 1 - EPSILON; tick++) {
            boolean takeoff = jumping && tick == 0;
            Opening opening = passed < terrain.openings.size() ? terrain.openings.get(passed) : null;
            if (!takeoff) {
                boolean pastGate = y + HEIGHT < gateFloor || (passed > 0 && opening == null);
                double lead = brakeAhead < 0 ? 5 : brakeAhead;
                double steerX = (pastGate ? goal.x() + .5 : aimX) - x - lead * vx;
                double steerZ = (pastGate ? goal.z() + .5 : aimZ) - z - lead * vz;
                if (opening != null && brakeAhead != 0) {
                    double gateX = opening.x(), gateZ = opening.z();
                    double lookahead = brakeAhead;
                    if (brakeAhead == -1) {
                        // Aim toward the exit side of the opening, leaving room for the player's feet.
                        if (terrain.openings.size() > 1) {
                            double side = opening.travelsAlongX() ? goal.z() + .5 : goal.x() + .5;
                            if (passed + 1 < terrain.openings.size()) {
                                var next = terrain.openings.get(passed + 1);
                                side = opening.travelsAlongX() ? next.z() : next.x();
                            }
                            side = Math.clamp(side, opening.lowSide() + .36, opening.highSide() - .36);
                            if (opening.travelsAlongX()) gateZ = side;
                            else gateX = side;
                        }
                        // Project inertial drift until our feet cross the top of this opening.
                        double feet = y, vertical = vy, friction = 1;
                        lookahead = 0;
                        while (feet + vertical > opening.floor() + 1) {
                            feet += vertical;
                            vertical = (vertical - .08) * .98;
                            lookahead += friction;
                            friction *= .91;
                        }
                    }
                    steerX = gateX - x - lookahead * vx;
                    steerZ = gateZ - z - lookahead * vz;
                }
                double length = Math.hypot(steerX, steerZ);
                if (length > .06) {
                    double accel = sprint ? .02548 : .0196;
                    vx += accel * steerX / length;
                    vz += accel * steerZ / length;
                }
            }
            double nx = x + vx, ny = y + vy, nz = z + vz;
            // Vanilla resolves vertical movement first. Test the old horizontal footprint at
            // every crossed level so a fast fall cannot tunnel through a bar or another pad.
            if (vy < 0) {
                if (opening != null && ny < opening.floor() + 1) {
                    if (ny + HEIGHT < opening.floor()) return false;
                    if (opening.contains(x, z) || opening.contains(nx, nz)) passed++;
                }
                for (int level = floor(y + EPSILON) - 1; level + 1 >= ny - EPSILON; level--) {
                    if (terrain.touching(x, level, z, 0)) {
                        return passed == terrain.openings.size()
                                && level == goal.floor() && terrain.touching(x, level, z, stage + 2)
                                && terrain.touching(nx, level, nz, stage + 2)
                                && terrain.clear(nx, level + 1, nz)
                                && canStopOnPad(terrain, goal, stage, nx, nz, vx * .91, vz * .91);
                    }
                }
            }
            if (!terrain.clear(x, ny, z) || !terrain.clear(nx, ny, z)
                    || !terrain.clear(x, ny, nz) || !terrain.clear(nx, ny, nz)) return false;
            x = nx;
            y = ny;
            z = nz;
            double drag = takeoff ? .546 : .91;
            vx *= drag;
            vz *= drag;
            vy = (vy - .08) * .98;
        }
        return false;
    }

    private static boolean canStopOnPad(Terrain terrain, Pad goal, int stage,
            double x, double z, double vx, double vz) {
        // Holding the opposite direction on ordinary blocks, then releasing movement, has
        // enough friction to settle. A fleeting clip of the far edge is not a playable route.
        while (Math.hypot(vx, vz) > .003) {
            double speed = Math.hypot(vx, vz);
            if (speed > .098) {
                vx *= (speed - .098) / speed;
                vz *= (speed - .098) / speed;
            }
            x += vx;
            z += vz;
            if (!terrain.touching(x, goal.floor(), z, stage + 2) || !terrain.clear(x, goal.floor() + 1, z))
                return false;
            vx *= .546;
            vz *= .546;
        }
        return true;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static void require(boolean condition, String message, int seed) {
        if (!condition) throw new AssertionError(message + " (seed " + seed + ")");
    }
}
