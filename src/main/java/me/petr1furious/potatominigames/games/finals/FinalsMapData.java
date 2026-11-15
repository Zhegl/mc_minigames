package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.List;

/** Serializable positions and bounded geometry; snapshots never depend on the editor world. */
final class FinalsMapData {
    static final int MAX_VAULTS = 16;
    static final int MAX_CASHOUTS = 16;

    record Point(double x, double y, double z, float yaw, float pitch) {
        Point {
            if (!Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(z)
                    || !Float.isFinite(yaw)
                    || !Float.isFinite(pitch)
                    || Math.abs(x) > 29_999_000
                    || Math.abs(z) > 29_999_000
                    || Math.abs(y) > 4096) throw FinalsMaps.failure("invalid_position");
        }

        static Point of(Location location) {
            return new Point(
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch());
        }

        int bx() {
            return (int) Math.floor(x);
        }

        int by() {
            return (int) Math.floor(y);
        }

        int bz() {
            return (int) Math.floor(z);
        }

        Location at(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }

        Point centered() {
            return new Point(bx() + .5, by(), bz() + .5, yaw, pitch);
        }

        boolean sameBlock(Point other) {
            return bx() == other.bx() && by() == other.by() && bz() == other.bz();
        }

        double distanceSquared(Point other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }

        void write(ConfigurationSection parent, String key) {
            parent.set(key, List.of(x, y, z, (double) yaw, (double) pitch));
        }

        static Point read(ConfigurationSection parent, String key) {
            List<Double> values = parent.getDoubleList(key);
            return values.size() == 5
                    ? new Point(
                            values.get(0),
                            values.get(1),
                            values.get(2),
                            values.get(3).floatValue(),
                            values.get(4).floatValue())
                    : null;
        }
    }

    record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Bounds {
            long sx = (long) maxX - minX + 1,
                    sy = (long) maxY - minY + 1,
                    sz = (long) maxZ - minZ + 1;
            if (sx <= 0
                    || sy <= 0
                    || sz <= 0
                    || sx > Integer.MAX_VALUE
                    || sy > Integer.MAX_VALUE
                    || sz > Integer.MAX_VALUE
                    || sx > Long.MAX_VALUE / sy / sz)
                throw FinalsMaps.failure("bounds");
        }

        static Bounds between(Point first, Point second) {
            if (first == null || second == null) throw FinalsMaps.failure("corners");
            return new Bounds(
                    Math.min(first.bx(), second.bx()),
                    Math.min(first.by(), second.by()),
                    Math.min(first.bz(), second.bz()),
                    Math.max(first.bx(), second.bx()),
                    Math.max(first.by(), second.by()),
                    Math.max(first.bz(), second.bz()));
        }

        boolean contains(Point p) {
            return p != null
                    && p.x >= minX
                    && p.x < (double) maxX + 1
                    && p.y >= minY
                    && p.y < (double) maxY + 1
                    && p.z >= minZ
                    && p.z < (double) maxZ + 1;
        }

        boolean standingRoom(Point p) {
            return contains(p) && p.by() > minY && p.by() + 1 <= maxY;
        }

        long volume() {
            return ((long) maxX - minX + 1)
                    * ((long) maxY - minY + 1)
                    * ((long) maxZ - minZ + 1);
        }

        BlockVector size() {
            return new BlockVector(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        }

        Location origin(World world) {
            return new Location(world, minX, minY, minZ);
        }
    }

    record JumpPad(Point origin, boolean targeted, Point target) {
        JumpPad {
            if (origin == null || (!targeted && target != null))
                throw FinalsMaps.failure("invalid_position");
        }
    }

    static final class Draft {
        String world;
        String folder;
        boolean imported;
        Point first, second;
        List<Point> vaults = new ArrayList<>();
        List<Point> cashouts = new ArrayList<>();
        List<Point> red = new ArrayList<>(), blue = new ArrayList<>();
        List<JumpPad> jumpPads = new ArrayList<>();

        void fullHeight(int minY, int maxYExclusive) {
            if (maxYExclusive <= minY) throw FinalsMaps.failure("bounds");
            if (first != null)
                first = new Point(first.x(), minY, first.z(), first.yaw(), first.pitch());
            if (second != null)
                second =
                        new Point(
                                second.x(),
                                maxYExclusive - 1,
                                second.z(),
                                second.yaw(),
                                second.pitch());
        }

        Draft copy() {
            Draft result = new Draft();
            result.world = world;
            result.folder = folder;
            result.imported = imported;
            result.first = first;
            result.second = second;
            result.vaults = new ArrayList<>(vaults);
            result.cashouts = new ArrayList<>(cashouts);
            result.red = new ArrayList<>(red);
            result.blue = new ArrayList<>(blue);
            result.jumpPads = new ArrayList<>(jumpPads);
            return result;
        }

        void setVault(Point point) {
            vaults.clear();
            addVault(point);
        }

        void addVault(Point point) {
            if (point == null) throw FinalsMaps.failure("invalid_position");
            if (vaults.size() >= MAX_VAULTS) throw FinalsMaps.failure("vault_limit", MAX_VAULTS);
            if (vaults.stream().anyMatch(point::sameBlock))
                throw FinalsMaps.failure("vault_duplicate");
            vaults.add(point);
        }

        void setCashout(int index, Point point) {
            if (index < 0 || index > 1)
                throw new IllegalArgumentException("Cashout must be A or B");
            validateCashout(point, index);
            while (cashouts.size() <= index) cashouts.add(null);
            cashouts.set(index, point);
        }

        void addCashout(Point point) {
            validateCashout(point, -1);
            int empty = cashouts.indexOf(null);
            if (empty >= 0) cashouts.set(empty, point);
            else {
                if (cashouts.size() >= MAX_CASHOUTS)
                    throw FinalsMaps.failure("cashout_limit", MAX_CASHOUTS);
                cashouts.add(point);
            }
        }

        private void validateCashout(Point point, int replacing) {
            if (point == null) throw FinalsMaps.failure("invalid_position");
            for (int i = 0; i < cashouts.size(); i++)
                if (i != replacing && cashouts.get(i) != null && point.sameBlock(cashouts.get(i)))
                    throw FinalsMaps.failure("cashout_duplicate");
        }

        void write(ConfigurationSection section) {
            section.set("jump-pads", null);
            for (int i = 0; i < jumpPads.size(); i++) {
                JumpPad pad = jumpPads.get(i);
                ConfigurationSection entry = section.createSection("jump-pads." + i);
                pad.origin().write(entry, "origin");
                entry.set("targeted", pad.targeted());
                if (pad.target() != null) pad.target().write(entry, "target");
            }
            section.set("world", world);
            section.set("folder", folder);
            section.set("imported", imported);
            if (first != null) first.write(section, "pos1");
            if (second != null) second.write(section, "pos2");
            section.set("vault", null);
            section.createSection("vaults");
            for (int i = 0; i < vaults.size(); i++) vaults.get(i).write(section, "vaults." + i);
            section.set("cashout-a", null);
            section.set("cashout-b", null);
            section.createSection("cashouts");
            for (int i = 0; i < cashouts.size(); i++) {
                Point point = cashouts.get(i);
                if (point == null) section.set("cashouts." + i, List.of());
                else point.write(section, "cashouts." + i);
            }
            for (int i = 0; i < red.size(); i++) red.get(i).write(section, "red." + i);
            for (int i = 0; i < blue.size(); i++) blue.get(i).write(section, "blue." + i);
        }

        static Draft read(ConfigurationSection section) {
            Draft draft = new Draft();
            draft.world = section.getString("world");
            draft.folder = section.getString("folder");
            draft.imported = section.getBoolean("imported");
            draft.first = Point.read(section, "pos1");
            draft.second = Point.read(section, "pos2");
            ConfigurationSection pads = section.getConfigurationSection("jump-pads");
            if (pads != null) for (int i = 0; i < pads.getKeys(false).size(); i++) {
                ConfigurationSection entry = pads.getConfigurationSection("" + i);
                if (entry == null) throw FinalsMaps.failure("invalid_position");
                draft.jumpPads.add(new JumpPad(Point.read(entry, "origin"),
                        entry.getBoolean("targeted"), Point.read(entry, "target")));
            }
            if (section.contains("vaults")) {
                ConfigurationSection candidates = section.getConfigurationSection("vaults");
                if (candidates == null) throw FinalsMaps.failure("invalid_position");
                int count = candidates.getKeys(false).size();
                if (count > MAX_VAULTS) throw FinalsMaps.failure("vault_limit", MAX_VAULTS);
                for (int i = 0; i < count; i++) draft.addVault(Point.read(candidates, "" + i));
            } else if (section.contains("vault")) {
                draft.addVault(Point.read(section, "vault"));
            }
            if (section.contains("cashouts")) {
                ConfigurationSection candidates = section.getConfigurationSection("cashouts");
                if (candidates == null) throw FinalsMaps.failure("invalid_position");
                int count = candidates.getKeys(false).size();
                if (count > MAX_CASHOUTS) throw FinalsMaps.failure("cashout_limit", MAX_CASHOUTS);
                for (int i = 0; i < count; i++) {
                    Object raw = candidates.get("" + i);
                    if (i < 2 && raw instanceof List<?> values && values.isEmpty()) {
                        draft.cashouts.add(null);
                    } else {
                        Point point = Point.read(candidates, "" + i);
                        draft.validateCashout(point, -1);
                        draft.cashouts.add(point);
                    }
                }
            } else {
                if (section.contains("cashout-a"))
                    draft.setCashout(0, Point.read(section, "cashout-a"));
                if (section.contains("cashout-b"))
                    draft.setCashout(1, Point.read(section, "cashout-b"));
            }
            for (int i = 0; i < 16 && section.contains("red." + i); i++)
                draft.red.add(Point.read(section, "red." + i));
            for (int i = 0; i < 16 && section.contains("blue." + i); i++)
                draft.blue.add(Point.read(section, "blue." + i));
            if (draft.world == null || draft.red.contains(null) || draft.blue.contains(null))
                throw FinalsMaps.failure("invalid_position");
            return draft;
        }
    }

    record Saved(
            Bounds bounds,
            List<Point> red,
            List<Point> blue,
            List<Point> vaults,
            List<Point> cashouts,
            List<JumpPad> jumpPads,
            String template) {
        Saved(Bounds bounds, List<Point> red, List<Point> blue, List<Point> vaults,
                List<Point> cashouts, String template) {
            this(bounds, red, blue, vaults, cashouts, List.of(), template);
        }
        Saved {
            red = List.copyOf(red);
            blue = List.copyOf(blue);
            vaults = List.copyOf(vaults);
            cashouts = List.copyOf(cashouts);
            jumpPads = List.copyOf(jumpPads);
        }

        static Saved from(Draft draft, String template) {
            Bounds bounds = Bounds.between(draft.first, draft.second);
            if (draft.red.isEmpty()
                    || draft.blue.isEmpty()
                    || draft.vaults.isEmpty()
                    || draft.cashouts.size() < 2
                    || draft.cashouts.contains(null)) throw FinalsMaps.failure("incomplete");
            if (draft.vaults.size() > MAX_VAULTS)
                throw FinalsMaps.failure("vault_limit", MAX_VAULTS);
            if (draft.cashouts.size() > MAX_CASHOUTS)
                throw FinalsMaps.failure("cashout_limit", MAX_CASHOUTS);
            List<Point> stations = draft.cashouts;
            List<Point> objectives = concat(draft.vaults, stations);
            List<Component> invalid = new ArrayList<>();
            invalidPoints(bounds, draft.red, "red_spawn", invalid);
            invalidPoints(bounds, draft.blue, "blue_spawn", invalid);
            invalidPoints(bounds, draft.vaults, "vault", invalid);
            invalidPoints(bounds, stations, "cashout", invalid);
            for (int i = 0; i < draft.jumpPads.size(); i++) {
                JumpPad pad = draft.jumpPads.get(i);
                if (pad.targeted() && pad.target() == null) throw FinalsMaps.failure("pad_target_missing", i + 1);
                for (int j = 0; j < i; j++)
                    if (pad.origin().sameBlock(draft.jumpPads.get(j).origin())) throw FinalsMaps.failure("pad_duplicate");
            }
            invalidPoints(bounds, draft.jumpPads.stream().map(JumpPad::origin).toList(), "pad", invalid);
            invalidPoints(bounds, draft.jumpPads.stream().map(p -> p.target() == null ? p.origin() : p.target()).toList(), "pad_target", invalid);
            if (!invalid.isEmpty())
                throw FinalsMaps.failure(
                        "outside",
                        Component.join(JoinConfiguration.separator(Component.newline()), invalid));
            for (int i = 0; i < stations.size(); i++)
                for (int j = 0; j < i; j++) {
                    if (stations.get(i).sameBlock(stations.get(j)))
                        throw FinalsMaps.failure("cashout_duplicate");
                    if (stations.get(i).distanceSquared(stations.get(j)) < 64)
                        throw FinalsMaps.failure("objectives_close");
                }
            for (int i = 0; i < draft.vaults.size(); i++) {
                Point point = draft.vaults.get(i);
                for (Point station : stations)
                    if (point.distanceSquared(station) < 64)
                        throw FinalsMaps.failure("objectives_close");
                for (int j = 0; j < i; j++)
                    if (point.sameBlock(draft.vaults.get(j)))
                        throw FinalsMaps.failure("vault_duplicate");
            }
            for (Point spawn : concat(draft.red, draft.blue)) {
                for (Point point : objectives)
                    if (spawn.distanceSquared(point) < 9) throw FinalsMaps.failure("spawn_close");
            }
            for (Point red : draft.red)
                for (Point blue : draft.blue) {
                    if (red.distanceSquared(blue) < 16) throw FinalsMaps.failure("teams_close");
                }
            return new Saved(bounds, draft.red, draft.blue, draft.vaults, draft.cashouts, draft.jumpPads, template);
        }

        private static void invalidPoints(
                Bounds bounds, List<Point> points, String label, List<Component> invalid) {
            for (int i = 0; i < points.size(); i++) {
                Point point = points.get(i);
                if (!bounds.standingRoom(point))
                    invalid.add(
                            I18n.tr(
                                    "finals.map.point.invalid",
                                    I18n.tr("finals.map.point." + label, i + 1),
                                    point == null ? "?" : point.bx(),
                                    point == null ? "?" : point.by(),
                                    point == null ? "?" : point.bz()));
            }
        }
    }

    static List<Point> concat(List<Point> first, List<Point> second) {
        List<Point> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private FinalsMapData() {}
}
