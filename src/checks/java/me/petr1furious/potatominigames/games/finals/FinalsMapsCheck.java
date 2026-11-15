package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsMapData.*;

import me.petr1furious.potatominigames.i18n.I18n;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.structure.Structure;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Proxy;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exercises real validation, import boundaries, snapshot isolation and match geometry without a
 * server.
 */
public final class FinalsMapsCheck {
    public static void main(String[] args) throws Exception {
        outsidePoints();
        geometry();
        fullHeight();
        vaultPersistence();
        cashoutPersistence();
        jumpPadPersistence();
        deathHeightPersistence();
        timeOfDayPersistence();
        arenaCopies();
        atomicReplacementLocks();
        imports();
        System.out.println("FinalsMapsCheck passed");
    }

    private static void timeOfDayPersistence() throws Exception {
        YamlConfiguration input = new YamlConfiguration(); draft().write(input);
        input.set("time-of-day", 18000);
        Draft configured = Draft.read(input);
        YamlConfiguration output = new YamlConfiguration(); configured.write(output);
        require(output.getInt("time-of-day", -1) == 18000, "Each map preserves its selected time of day");
        configured.deathHeight = 64.5;
        Saved savedA = Saved.from(configured, "a.slime");
        Draft other = draft(); other.timeOfDay = 1000;
        Saved savedB = Saved.from(other, "b.slime");
        FakeWorld shared = new FakeWorld("editor");
        FinalsMapArena a = new FinalsImportedArena(shared.world, savedA, (FinalsMapSnapshot) null);
        FinalsMapArena b = new FinalsImportedArena(shared.world, savedB, (FinalsMapSnapshot) null);
        require(a.timeOfDay() == 18000 && b.timeOfDay() == 1000 && a.deathHeight() == 64.5 && b.deathHeight() == null,
                "Actual arenas backed by the same world use only their own time and death settings");
        Draft copy = configured.copy(); copy.timeOfDay = null; copy.write(output);
        require(Draft.read(output).timeOfDay == null && savedA.timeOfDay() == 18000 && configured.timeOfDay == 18000,
                "Resetting a draft time removes its override without changing a saved map or another draft");
        FinalsMapArena legacy = new FinalsImportedArena(shared.world, Saved.from(draft(), "old.slime"), (FinalsMapSnapshot) null);
        require(legacy.timeOfDay() == 6000 && legacy.deathHeight() == null, "Existing maps preserve noon and no custom death floor");
        for (Object invalid : List.of(-1, 24000, 1.5, "night", Double.NaN)) {
            output.set("time-of-day", invalid);
            fails(() -> Draft.read(output), "Invalid persisted time rejected: " + invalid);
        }
    }

    private static void deathHeightPersistence() throws Exception {
        Draft a = draft(), b = draft();
        a.deathHeight = 64.5;
        Saved savedA = Saved.from(a, "a.slime"), savedB = Saved.from(b, "b.slime");
        require(a.world.equals(b.world) && savedA.deathHeight() == 64.5 && savedB.deathHeight() == null,
                "Maps sharing an editor world retain independent death heights");
        YamlConfiguration yaml = new YamlConfiguration();
        a.write(yaml.createSection("a")); b.write(yaml.createSection("b"));
        YamlConfiguration restored = new YamlConfiguration(); restored.loadFromString(yaml.saveToString());
        require(Draft.read(restored.getConfigurationSection("a")).deathHeight == 64.5
                        && Draft.read(restored.getConfigurationSection("b")).deathHeight == null,
                "Per-map death heights remain isolated after YAML round-trip");
        Draft edited = a.copy(); edited.deathHeight = -10d;
        require(a.deathHeight == 64.5 && savedA.deathHeight() == 64.5,
                "Unsaved edits cannot change a published map's death height");
        edited.deathHeight = null; edited.write(yaml.getConfigurationSection("a"));
        require(Draft.read(yaml.getConfigurationSection("a")).deathHeight == null,
                "Turning death height off removes its persisted setting");
        edited.deathHeight = Double.NaN;
        fails(() -> Saved.from(edited, "bad.slime"), "Non-finite saved death height is rejected");
        yaml.getConfigurationSection("a").set("death-height", "bad");
        fails(() -> Draft.read(yaml.getConfigurationSection("a")), "Invalid persisted death height is rejected");
    }

    private static void jumpPadPersistence() throws Exception {
        Draft draft = draft();
        Point origin = new Point(8.5, 65, 8.5, 0, 0), target = new Point(-8.5, 70, 8.5, 90, 0);
        draft.jumpPads.add(new JumpPad(origin, true, null));
        fails(() -> Saved.from(draft, "pads.slime"), "An unfinished targeted pad cannot be saved");
        draft.jumpPads.set(0, new JumpPad(origin, true, target));
        draft.jumpPads.add(new JumpPad(new Point(8.5, 65, -8.5, 0, 0), false, null));
        Saved saved = Saved.from(draft, "pads.slime");
        YamlConfiguration yaml = new YamlConfiguration();
        draft.write(yaml);
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(yaml.saveToString());
        require(Saved.from(Draft.read(loaded), "pads.slime").equals(saved), "Both pad types and targets round-trip with saved maps");
        Draft copy = draft.copy(); copy.jumpPads.clear();
        require(draft.jumpPads.size() == 2 && saved.jumpPads().size() == 2, "Draft pad edits cannot alter saved pads");
        draft.jumpPads.removeLast(); draft.write(yaml);
        require(Draft.read(yaml).jumpPads.size() == 1, "Removed pad entries do not survive YAML rewrite");
        draft.jumpPads.add(draft.jumpPads.getFirst());
        fails(() -> Saved.from(draft, "pads.slime"), "Duplicate pads cannot be saved");
        draft.jumpPads.clear();
        draft.jumpPads.add(new JumpPad(origin, true, new Point(100, 65, 100, 0, 0)));
        fails(() -> Saved.from(draft, "pads.slime"), "Targets outside the playable selection cannot be saved");
        require(Saved.from(draft(), "old.slime").jumpPads().isEmpty(), "Existing maps remain compatible without pads");
    }

    private static void cashoutPersistence() throws Exception {
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("world", "editor");
        legacy.set("cashout-a", List.of(0.5, 65d, -11.5, 0d, 0d));
        legacy.set("cashout-b", List.of(0.5, 65d, 12.5, 0d, 0d));
        Draft migrated = Draft.read(legacy);
        YamlConfiguration written = new YamlConfiguration();
        migrated.write(written);
        require(
                written.getDoubleList("cashouts.0").equals(List.of(0.5, 65d, -11.5, 0d, 0d)),
                "Legacy maps retain station A in the cashout candidate pool");
        require(
                written.getDoubleList("cashouts.1").equals(List.of(0.5, 65d, 12.5, 0d, 0d)),
                "Legacy maps retain station B in the cashout candidate pool");
        require(
                !written.contains("cashout-a") && !written.contains("cashout-b"),
                "New cashout data removes legacy anchors on write");
        Point third = new Point(12.5, 65, 12.5, 90, 0);
        migrated.addCashout(third);
        migrated.write(written);
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(written.saveToString());
        Draft restored = Draft.read(reloaded);
        require(
                restored.cashouts.equals(
                        List.of(
                                new Point(.5, 65, -11.5, 0, 0),
                                new Point(.5, 65, 12.5, 0, 0),
                                third)),
                "Every cashout candidate survives a YAML round trip");
        Draft copy = restored.copy();
        copy.cashouts.clear();
        require(restored.cashouts.size() == 3, "Editing cashouts cannot mutate a previous draft");
        Point replacement = new Point(-12.5, 65, 12.5, 0, 0);
        restored.setCashout(0, replacement);
        require(
                restored.cashouts.getFirst().equals(replacement)
                        && restored.cashouts.get(2).equals(third),
                "Setting cashout A preserves additional candidates");
        fails(
                () -> restored.setCashout(1, third),
                "Cashout replacement cannot duplicate another candidate");
        restored.cashouts.clear();
        written.set("cashout-a", List.of(0.5, 65d, -11.5, 0d, 0d));
        restored.write(written);
        reloaded.loadFromString(written.saveToString());
        require(
                Draft.read(reloaded).cashouts.isEmpty(),
                "Clearing cashouts cannot resurrect legacy anchors");
        legacy.set("cashout-a", null);
        Draft partial = Draft.read(legacy);
        partial.write(written);
        reloaded.loadFromString(written.saveToString());
        Draft partialReloaded = Draft.read(reloaded);
        require(
                partialReloaded.cashouts.size() == 2
                        && partialReloaded.cashouts.get(0) == null
                        && partialReloaded.cashouts.get(1).equals(new Point(.5, 65, 12.5, 0, 0)),
                "A legacy B-only draft preserves the empty A slot through YAML");
        partialReloaded.addCashout(replacement);
        require(
                partialReloaded.cashouts.size() == 2
                        && partialReloaded.cashouts.getFirst().equals(replacement),
                "Adding a cashout fills an unconfigured legacy slot before appending");
        partialReloaded.write(written);
        written.set("cashouts.3", List.of(12.5, 65d, 12.5, 0d, 0d));
        fails(() -> Draft.read(written), "Sparse cashout lists cannot silently discard a location");
        written.set("cashouts", "invalid");
        fails(() -> Draft.read(written), "Malformed cashout lists are rejected");
        Draft full = new Draft();
        for (int i = 0; i < 16; i++) full.addCashout(new Point(i + .5, 65, .5, 0, 0));
        fails(
                () -> full.addCashout(new Point(17.5, 65, .5, 0, 0)),
                "More than 16 cashout candidates are rejected");
        Draft duplicate = new Draft();
        duplicate.addCashout(third);
        fails(
                () -> duplicate.addCashout(new Point(12.1, 65.5, 12.1, 0, 0)),
                "Different fractional coordinates cannot duplicate a cashout block");
    }

    private static void vaultPersistence() throws Exception {
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("world", "editor");
        legacy.set("vault", List.of(0.5, 65d, 0.5, 0d, 0d));
        Draft migrated = Draft.read(legacy);
        YamlConfiguration written = new YamlConfiguration();
        migrated.write(written);
        require(
                written.getDoubleList("vaults.0").equals(List.of(0.5, 65d, 0.5, 0d, 0d)),
                "Legacy maps retain their vault when migrated to a candidate list");
        require(!written.contains("vault"), "Writing the new format removes the legacy position");
        Point second = new Point(5.5, 65, .5, 90, 0);
        migrated.addVault(second);
        migrated.write(written);
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(written.saveToString());
        Draft restored = Draft.read(reloaded);
        require(
                restored.vaults.equals(List.of(new Point(.5, 65, .5, 0, 0), second)),
                "Every candidate survives a YAML round trip in order");
        Draft copy = restored.copy();
        copy.vaults.clear();
        require(
                restored.vaults.size() == 2,
                "Editing a draft does not mutate its previous version");
        restored.setVault(second);
        require(
                restored.vaults.equals(List.of(second)),
                "Legacy vault command replaces all candidates");
        restored.vaults.clear();
        written.set("vault", List.of(0.5, 65d, 0.5, 0d, 0d));
        restored.write(written);
        reloaded.loadFromString(written.saveToString());
        require(
                Draft.read(reloaded).vaults.isEmpty(),
                "Clearing candidates cannot resurrect a legacy vault");
        written.set("vaults.0", List.of(0.5, 65d, 0.5, 0d, 0d));
        written.set("vaults.2", List.of(5.5, 65d, 0.5, 0d, 0d));
        fails(() -> Draft.read(written), "Sparse candidate lists cannot silently drop locations");
        written.set("vaults", "invalid");
        fails(() -> Draft.read(written), "Malformed candidate lists are rejected");
        Draft full = new Draft();
        for (int i = 0; i < 16; i++) full.addVault(new Point(i + .5, 65, .5, 0, 0));
        fails(
                () -> full.addVault(new Point(17.5, 65, .5, 0, 0)),
                "More than 16 candidates are rejected");
        Draft duplicate = new Draft();
        duplicate.addVault(second);
        fails(
                () -> duplicate.addVault(new Point(5.1, 65.5, .1, 45, 12)),
                "Different facing or fractional coordinates cannot duplicate a candidate block");
    }

    private static Draft draft() {
        Draft draft = new Draft();
        draft.world = "editor";
        draft.first = new Point(-16, 64, -16, 0, 0);
        draft.second = new Point(16, 80, 16, 0, 0);
        draft.red.add(new Point(-12.5, 65, .5, -90, 0));
        draft.blue.add(new Point(12.5, 65, .5, 90, 0));
        draft.vaults.add(new Point(.5, 65, .5, 0, 0));
        draft.setCashout(0, new Point(.5, 65, -11.5, 0, 0));
        draft.setCashout(1, new Point(.5, 65, 12.5, 0, 0));
        return draft;
    }

    private static void geometry() {
        Draft draft = draft();
        Saved saved = Saved.from(draft, "test.nbt");
        require(saved.bounds().volume() == 18_513, "Inclusive cuboid size is stable");
        require(
                saved.bounds().contains(new Point(16.99, 80.99, 16.99, 0, 0)),
                "Upper block belongs to bounds");
        require(!saved.bounds().contains(new Point(17, 65, 0, 0, 0)), "Upper edge is exclusive");
        YamlConfiguration yaml = new YamlConfiguration();
        draft.write(yaml);
        Saved restored = Saved.from(Draft.read(yaml), "test.nbt");
        require(restored.equals(saved), "Draft positions, facing and snapshot geometry round-trip");
        draft.red.clear();
        require(saved.red().size() == 1, "Saved spawns are immutable copies");
        fails(() -> Saved.from(draft, "test.nbt"), "Missing team spawn rejected");
        Draft close = draft();
        close.cashouts.set(1, close.vaults.getFirst());
        fails(() -> Saved.from(close, "test.nbt"), "Overlapping objectives rejected");
        Draft outside = draft();
        outside.red.set(0, new Point(-17, 65, 0, 0, 0));
        fails(() -> Saved.from(outside, "test.nbt"), "Out-of-bounds spawn rejected");
        Draft noFloor = draft();
        noFloor.red.set(0, new Point(-12, 64, 0, 0, 0));
        fails(() -> Saved.from(noFloor, "test.nbt"), "Spawn support must be inside snapshot");
        Draft overlap = draft();
        overlap.blue.set(0, overlap.red.getFirst());
        fails(() -> Saved.from(overlap, "test.nbt"), "Opposing spawns cannot overlap");
        require(
                new Bounds(0, 0, 0, 512, 4, 5).volume() == 15_390,
                "Maps can exceed the old 512-block axis limit");
        require(
                new Bounds(0, 0, 0, 100, 100, 100).volume() == 1_030_301,
                "Maps can exceed the old million-block volume limit");
        require(
                new Bounds(-1, -1, -1, -1, -1, -1).volume() == 1,
                "A single selected block has valid geometry");
        Bounds wide = new Bounds(Integer.MIN_VALUE, 0, 0, -2, 0, 0);
        require(
                wide.volume() == 2_147_483_647L
                        && wide.size().getBlockX() == Integer.MAX_VALUE,
                "Representable axes preserve their exact size without integer overflow");
        require(
                new Bounds(0, 0, 0, Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1, 0).volume()
                        == 4_611_686_014_132_420_609L,
                "Large valid volumes retain long precision");
        fails(() -> new Bounds(1, 0, 0, 0, 0, 0), "Inverted X bounds are rejected");
        fails(() -> new Bounds(0, 1, 0, 0, 0, 0), "Inverted Y bounds are rejected");
        fails(() -> new Bounds(0, 0, 1, 0, 0, 0), "Inverted Z bounds are rejected");
        fails(
                () -> new Bounds(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 5, 5),
                "Axes wider than the native integer size are rejected");
        fails(
                () -> new Bounds(0, 0, 0, Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1, 2),
                "Volumes larger than a long are rejected before multiplication overflows");
        require(
                Bounds.between(new Point(16, 80, 16, 0, 0), new Point(-16, 64, -16, 0, 0))
                        .equals(saved.bounds()),
                "Reversing selection points preserves inclusive bounds");
        fails(() -> Bounds.between(null, draft.second), "Missing selection points are rejected");
        fails(() -> new Point(Double.NaN, 65, 0, 0, 0), "Non-finite position rejected");
        Draft multiple = draft();
        multiple.addVault(new Point(5.5, 65, .5, 0, 0));
        Saved multiSaved = Saved.from(multiple, "test.nbt");
        multiple.vaults.clear();
        require(multiSaved.vaults().size() == 2, "Saved candidates are immutable copies");
        fails(() -> Saved.from(multiple, "test.nbt"), "At least one vault candidate is required");
        multiple.vaults.add(new Point(.5, 65, .5, 0, 0));
        multiple.vaults.add(new Point(17.5, 65, .5, 0, 0));
        fails(
                () -> Saved.from(multiple, "test.nbt"),
                "Every vault candidate must lie inside bounds");
        multiple.vaults.set(1, new Point(.5, 65, -10.5, 0, 0));
        fails(
                () -> Saved.from(multiple, "test.nbt"),
                "Every candidate must be separated from cashouts");
        multiple.vaults.set(1, new Point(-12.5, 65, .5, 0, 0));
        fails(
                () -> Saved.from(multiple, "test.nbt"),
                "Every candidate must be separated from team spawns");
        multiple.vaults.set(1, new Point(.7, 65, .7, 20, 0));
        fails(
                () -> Saved.from(multiple, "test.nbt"),
                "Saved geometry rejects duplicate candidate blocks");
        Draft cashoutPool = draft();
        cashoutPool.addCashout(new Point(12.5, 65, 12.5, 0, 0));
        Saved poolSaved = Saved.from(cashoutPool, "test.nbt");
        cashoutPool.cashouts.clear();
        require(poolSaved.cashouts().size() == 3, "Saved cashout candidates are immutable copies");
        fails(() -> Saved.from(cashoutPool, "test.nbt"), "A map needs at least two cashouts");
        cashoutPool.addCashout(new Point(.5, 65, -11.5, 0, 0));
        fails(
                () -> Saved.from(cashoutPool, "test.nbt"),
                "A single cashout cannot start a two-station match");
        cashoutPool.addCashout(new Point(.5, 65, 12.5, 0, 0));
        cashoutPool.addCashout(new Point(17.5, 65, 12.5, 0, 0));
        fails(
                () -> Saved.from(cashoutPool, "test.nbt"),
                "All cashout candidates must be inside the selected map");
        cashoutPool.cashouts.set(2, new Point(4.5, 65, 12.5, 0, 0));
        fails(
                () -> Saved.from(cashoutPool, "test.nbt"),
                "Any two cashout candidates must remain separated");
        cashoutPool.cashouts.set(2, new Point(5.5, 65, .5, 0, 0));
        fails(
                () -> Saved.from(cashoutPool, "test.nbt"),
                "Every cashout candidate stays separated from vaults");
        cashoutPool.cashouts.set(2, new Point(12.5, 65, .5, 0, 0));
        fails(
                () -> Saved.from(cashoutPool, "test.nbt"),
                "Every cashout candidate stays separated from team spawns");
        cashoutPool.cashouts.set(2, null);
        fails(
                () -> Saved.from(cashoutPool, "test.nbt"),
                "Unconfigured cashout slots cannot reach a saved match");
    }

    private static void outsidePoints() {
        Draft draft = draft();
        draft.red.add(new Point(-17.5, 65, .5, 0, 0));
        draft.blue.set(0, new Point(12.5, 80, .5, 0, 0));
        draft.vaults.add(new Point(.5, 64, .5, 0, 0));
        draft.cashouts.add(new Point(.5, 65, 17.5, 0, 0));
        try {
            Saved.from(draft, "invalid.nbt");
            throw new AssertionError("All invalid points must prevent saving");
        } catch (IllegalArgumentException failure) {
            String message = I18n.plain(I18n.message(failure), Locale.ENGLISH);
            for (String coordinate : List.of("-18", "80", "64", "17"))
                require(message.contains(coordinate), "Every invalid point appears in the error: " + coordinate);
            for (String label : List.of("red_spawn:2", "blue_spawn:1", "vault:2", "cashout:3")) {
                String[] parts = label.split(":");
                require(
                        message.contains(I18n.plain(I18n.tr("finals.map.point." + parts[0], parts[1]), Locale.ENGLISH)),
                        "Invalid point labels retain their original 1-based indices: " + label);
            }
            require(message.lines().count() >= 5, "Invalid points are listed on separate lines");
            String russian = I18n.plain(I18n.message(failure), Locale.forLanguageTag("ru"));
            require(
                    russian.contains(I18n.plain(I18n.tr("finals.map.point.red_spawn", 2), Locale.forLanguageTag("ru")))
                            && !russian.contains("potatominigames."),
                    "Nested point labels remain localized for the recipient");
        }
    }

    private static void fullHeight() throws Exception {
        Draft draft = draft();
        draft.first = new Point(16.75, 65, -16.25, 45, -12);
        draft.second = new Point(-16.25, 65, 16.75, -90, 20);
        draft.fullHeight(-64, 320);
        require(
                draft.first.equals(new Point(16.75, -64, -16.25, 45, -12))
                        && draft.second.equals(new Point(-16.25, 319, 16.75, -90, 20)),
                "Full-height selection preserves each corner's X/Z and facing");
        require(
                Bounds.between(draft.first, draft.second).equals(new Bounds(-17, -64, -17, 16, 319, 16)),
                "Same-height horizontal corners include every world layer in either X/Z order");
        YamlConfiguration written = new YamlConfiguration();
        draft.write(written);
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(written.saveToString());
        Draft restored = Draft.read(loaded);
        require(
                restored.first.equals(draft.first) && restored.second.equals(draft.second)
                        && Saved.from(restored, "full-height.nbt").bounds().volume() == 443_904,
                "Full-height corners and inclusive geometry survive YAML round trips");
        Draft partial = new Draft();
        partial.fullHeight(-64, 320);
        require(partial.first == null && partial.second == null, "Height normalization cannot create missing corners");
        partial.second = new Point(2.5, 64, 3.5, 20, 10);
        partial.fullHeight(0, 256);
        require(
                partial.first == null && partial.second.equals(new Point(2.5, 255, 3.5, 20, 10)),
                "A second-only draft retains its missing first corner");
        partial.first = new Point(-2.5, 64, -3.5, 10, 20);
        partial.second = null;
        partial.fullHeight(0, 1);
        require(
                partial.first.equals(new Point(-2.5, 0, -3.5, 10, 20)) && partial.second == null,
                "A first-only draft supports a one-layer world");
        fails(() -> partial.fullHeight(1, 1), "An empty world height range is rejected");
        fails(() -> partial.fullHeight(1, 0), "An inverted world height range is rejected");
    }

    private static void arenaCopies() {
        Saved saved = Saved.from(draft(), "test.nbt");
        FakeWorld editor = new FakeWorld("editor");
        for (int x = -16; x <= 16; x++)
            for (int z = -16; z <= 16; z++) editor.blocks.put(new Cell(x, 64, z), Material.STONE);
        editor.blocks.put(new Cell(6, 66, 6), Material.STONE);
        Map<Cell, Material> snapshot = Map.copyOf(editor.blocks);
        FakeWorld first = new FakeWorld("match-one"), second = new FakeWorld("match-two");
        Map<World, FakeWorld> destinations = Map.of(first.world, first, second.world, second);
        int[] placements = {0};
        Structure structure =
                (Structure)
                        Proxy.newProxyInstance(
                                Structure.class.getClassLoader(),
                                new Class<?>[] {Structure.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("place")) {
                                        Location origin = (Location) args[0];
                                        require(
                                                origin.getBlockX() == -16
                                                        && origin.getBlockY() == 64
                                                        && origin.getBlockZ() == -16,
                                                "Placement preserves source coordinates");
                                        require(
                                                Boolean.FALSE.equals(args[1]),
                                                "Entities are excluded from match copies");
                                        destinations.get(origin.getWorld()).blocks.putAll(snapshot);
                                        placements[0]++;
                                        return null;
                                    }
                                    return primitive(method.getReturnType());
                                });
        FinalsMapArena a = new FinalsImportedArena(first.world, saved, structure);
        a.build();
        a.build();
        require(placements[0] == 1, "Building a copy is idempotent");
        require(editor.blocks.equals(snapshot), "Objective markers never modify the editor map");
        require(a.isVault(first.block(0, 65, 0)), "Vault interaction coordinates are preserved");
        require(
                a.stationAt(first.block(0, 65, -12)) == 0
                        && a.stationAt(first.block(0, 65, 12)) == 1,
                "Both cashouts map to the correct station");
        require(!a.isVault(editor.block(0, 65, 0)), "Objective checks reject another world");
        require(
                a.borderSize() == 33 && a.center().getX() == .5 && a.center().getZ() == .5,
                "Custom map border follows snapshot bounds");
        require(
                a.spawn(0, 4).equals(a.spawn(0, 0)),
                "Configured team spawns cycle deterministically");
        require(!a.isCover(first.block(6, 64, 6)), "Base floor is protected");
        require(!a.isCover(first.block(-13, 64, 0)), "Spawn support is protected");
        require(!a.isCover(first.block(0, 65, 0)), "Objective markers are protected");
        require(a.isCover(first.block(6, 66, 6)), "Interior cover is destructible");
        first.blocks.put(new Cell(0, 66, -12), Material.STONE);
        require(a.isCover(first.block(0, 66, -12)), "Cashout headroom can be destroyed");
        require(first.block(0, 65, -12).getType() == Material.AIR,
                "Cashout selection does not write a physical gold marker");
        a.moveStation(0, new Location(first.world, .5, 67, -11.5));
        require(a.stationAt(first.block(0, 67, -12)) == 0
                        && a.stationAt(first.block(0, 65, -12)) == -1,
                "Cashout interaction coordinates follow the simulated entity height");
        first.blocks.put(new Cell(6, 66, 6), Material.AIR);
        FinalsMapArena b = new FinalsImportedArena(second.world, saved, structure);
        b.build();
        require(
                second.blocks.get(new Cell(6, 66, 6)) == Material.STONE,
                "A new match restores destroyed cover");
        require(editor.blocks.equals(snapshot), "Match destruction leaves the source intact");
        require(
                !a.contains(new Location(first.world, 17, 65, 0)),
                "Players beyond a custom edge are out of bounds");
        require(
                !a.contains(new Location(editor.world, 0, 65, 0)),
                "Containment rejects the editor world");
    }

    private static void atomicReplacementLocks() throws Exception {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return;
        Path temporary = fixtureDirectory("atomic-lock-");
        try {
            Path registry = temporary.resolve("maps.yml"),
                    pending = temporary.resolve("pending.yml");
            Files.writeString(registry, "previous registry");
            Files.writeString(pending, "complete new registry");
            AtomicReference<Throwable> releaseFailure = new AtomicReference<>();
            try (FileChannel lock =
                    FileChannel.open(
                            registry,
                            StandardOpenOption.READ,
                            com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)) {
                Thread releaser =
                        Thread.ofPlatform()
                                .start(() -> {
                                    try {
                                        Thread.sleep(50);
                                        lock.close();
                                    } catch (Throwable error) {
                                        releaseFailure.set(error);
                                    }
                                });
                try {
                    FinalsMapFiles.replace(pending, registry);
                } finally {
                    releaser.join();
                }
            }
            if (releaseFailure.get() != null)
                throw new AssertionError(
                        "The transient registry lock was released", releaseFailure.get());
            require(
                    Files.readString(registry).equals("complete new registry")
                            && !Files.exists(pending),
                    "A briefly locked registry is replaced atomically once the lock clears");
            Files.writeString(pending, "later registry");
            try (FileChannel lock =
                    FileChannel.open(
                            registry,
                            StandardOpenOption.READ,
                            com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)) {
                boolean denied = false;
                try {
                    FinalsMapFiles.replace(pending, registry);
                } catch (AccessDeniedException expected) {
                    denied = true;
                }
                require(denied, "A permanently locked registry fails after bounded retries");
                require(
                        Files.readString(registry).equals("complete new registry"),
                        "Failed locked replacement preserves the previous registry");
                require(
                        Files.readString(pending).equals("later registry"),
                        "Failed locked replacement retains the pending bytes for caller cleanup");
                Thread.currentThread().interrupt();
                boolean interruptedFailure = false;
                boolean interruptPreserved;
                try {
                    FinalsMapFiles.replace(pending, registry);
                } catch (IOException expected) {
                    interruptedFailure = expected.getCause() instanceof InterruptedException;
                } finally {
                    interruptPreserved = Thread.interrupted();
                }
                require(
                        interruptedFailure && interruptPreserved,
                        "Interrupted retry fails promptly and preserves the thread interrupt flag");
                require(
                        Files.readString(registry).equals("complete new registry")
                                && Files.readString(pending).equals("later registry"),
                        "Interrupted replacement leaves both registry and pending bytes intact");
            }
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
    }

    private static void imports() throws Exception {
        Path temporary = fixtureDirectory("imports-");
        try {
            Path imports = Files.createDirectory(temporary.resolve("imports"));
            Path source = Files.createDirectory(imports.resolve("original"));
            Files.createDirectory(source.resolve("region"));
            Files.writeString(source.resolve("level.dat"), "level-data");
            Files.writeString(source.resolve("region/r.0.0.mca"), "region-data");
            Files.writeString(source.resolve("uid.dat"), "original-uuid");
            Files.createDirectory(source.resolve("datapacks"));
            Files.writeString(source.resolve("datapacks/commands.txt"), "must-not-copy");
            Path copy = temporary.resolve("copy");
            FinalsMapFiles.copyWorld(FinalsMapFiles.source(imports, "original"), copy);
            require(
                    Files.readString(copy.resolve("level.dat")).equals("level-data"),
                    "Native world metadata copies");
            require(
                    Files.readString(copy.resolve("region/r.0.0.mca")).equals("region-data"),
                    "Native region data copies");
            require(
                    !Files.exists(copy.resolve("uid.dat"))
                            && !Files.exists(copy.resolve("datapacks")),
                    "Identity and executable data do not copy");
            Files.writeString(copy.resolve("region/r.0.0.mca"), "changed-copy");
            require(
                    Files.readString(source.resolve("region/r.0.0.mca")).equals("region-data"),
                    "Editing a copy preserves the original");
            for (String path :
                    new String[] {
                        "../outside",
                        "/outside",
                        "C:/outside",
                        "original/../original",
                        "original\\region",
                        ""
                    })
                failsChecked(
                        () -> FinalsMapFiles.source(imports, path),
                        "Import path rejected: " + path);
            for (String path :
                    new String[] {
                        "../escape", "/absolute", "C:/file", "root/../level.dat", "root\\file"
                    }) fails(() -> FinalsMapFiles.zipPath(path), "ZIP path rejected: " + path);
            Path valid = imports.resolve("valid.zip");
            zip(valid, "wrapped/level.dat", "wrapped/region/r.0.0.mca");
            Path unzipped = temporary.resolve("unzipped");
            FinalsMapFiles.copyWorld(valid, unzipped);
            require(
                    Files.exists(unzipped.resolve("region/r.0.0.mca")),
                    "Wrapped vanilla ZIP imports");
            Path nativeSource = Files.createDirectory(imports.resolve("native-world"));
            Files.writeString(nativeSource.resolve("level.dat"), "native-level");
            Files.createDirectories(nativeSource.resolve("dimensions/minecraft/overworld/region"));
            Files.writeString(
                    nativeSource.resolve("dimensions/minecraft/overworld/region/r.0.0.mca"),
                    "native-region");
            Path nativeCopy = temporary.resolve("native-copy");
            FinalsMapFiles.copyWorld(nativeSource, nativeCopy);
            require(
                    Files.readString(nativeCopy.resolve("region/r.0.0.mca"))
                            .equals("native-region"),
                    "Native overworld folders normalize region storage");
            Path nativeZip = imports.resolve("native.zip");
            zip(
                    nativeZip,
                    "wrapped/level.dat",
                    "wrapped/dimensions/minecraft/overworld/region/r.0.0.mca");
            Path nativeUnzipped = temporary.resolve("native-unzipped");
            FinalsMapFiles.copyWorld(nativeZip, nativeUnzipped);
            require(
                    Files.exists(nativeUnzipped.resolve("region/r.0.0.mca")),
                    "Native overworld ZIPs normalize region storage");
            Path slip = imports.resolve("slip.zip");
            zip(slip, "../escape", "level.dat", "region/r.0.0.mca");
            failsChecked(
                    () -> FinalsMapFiles.copyWorld(slip, temporary.resolve("slip")),
                    "ZIP traversal rejected before extraction");
            require(
                    !Files.exists(temporary.resolve("escape")),
                    "ZIP traversal writes nothing outside destination");
            Path duplicate = imports.resolve("duplicate.zip");
            zip(duplicate, "level.dat", "LEVEL.DAT", "region/r.0.0.mca");
            failsChecked(
                    () -> FinalsMapFiles.copyWorld(duplicate, temporary.resolve("duplicate")),
                    "Case-colliding ZIP entries rejected");
            Path linkZip = imports.resolve("link.zip");
            zip(linkZip, "level.dat", "region/r.0.0.mca");
            markZipSymlink(linkZip);
            failsChecked(
                    () -> FinalsMapFiles.copyWorld(linkZip, temporary.resolve("zip-link")),
                    "Unix symlinks in ZIP rejected");
            try {
                Path link = Files.createSymbolicLink(imports.resolve("link"), source);
                failsChecked(
                        () -> FinalsMapFiles.source(imports, link.getFileName().toString()),
                        "Source symlinks rejected");
            } catch (UnsupportedOperationException
                    | java.nio.file.FileSystemException unavailable) {
                System.out.println(
                        "Source symlink creation unavailable on this host; ZIP symlink rejection"
                                + " was checked");
            }
            Path registry = temporary.resolve("maps.yml"),
                    pending = temporary.resolve("pending.yml");
            Files.writeString(registry, "old");
            Files.writeString(pending, "new");
            FinalsMapFiles.replace(pending, registry);
            require(
                    Files.readString(registry).equals("new"),
                    "Atomic replacement commits complete registry");
            failsChecked(
                    () -> FinalsMapFiles.replace(temporary.resolve("missing"), registry),
                    "Missing replacement fails");
            require(
                    Files.readString(registry).equals("new"),
                    "Failed replacement preserves last good registry");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
    }

    private static Path fixtureDirectory(String prefix) throws IOException {
        // Exercise replacement on the project's filesystem. Sandboxed Windows Temp can
        // deny atomic replacement even after every file handle has been closed.
        Path root = Files.createDirectories(Path.of("build", "tmp", "finals-map-checks"));
        return Files.createTempDirectory(root, prefix);
    }

    private static void zip(Path path, String... entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String entry : entries) {
                output.putNextEntry(new ZipEntry(entry));
                output.write("data".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static void markZipSymlink(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            for (long p = 0; p + 46 < file.length(); p++) {
                file.seek(p);
                if (Integer.reverseBytes(file.readInt()) == 0x02014b50) {
                    file.seek(p + 38);
                    file.writeInt(Integer.reverseBytes(0120777 << 16));
                    return;
                }
            }
            throw new AssertionError("ZIP central directory missing");
        }
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Exception;
    }

    private static void fails(Runnable action, String description) {
        failsChecked(action::run, description);
    }

    private static void failsChecked(Checked action, String description) {
        try {
            action.run();
        } catch (IllegalArgumentException | IOException expected) {
            return;
        } catch (Exception unexpected) {
            throw new AssertionError(description, unexpected);
        }
        throw new AssertionError(description);
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private record Cell(int x, int y, int z) {}

    private static Object primitive(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        return null;
    }

    private static final class FakeWorld {
        final Map<Cell, Material> blocks = new HashMap<>();
        final World world;

        FakeWorld(String name) {
            world =
                    (World)
                            Proxy.newProxyInstance(
                                    World.class.getClassLoader(),
                                    new Class<?>[] {World.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getName", "toString" -> name;
                                                case "equals" -> proxy == args[0];
                                                case "hashCode" -> System.identityHashCode(proxy);
                                                case "getBlockAt" ->
                                                        block(
                                                                (int) args[0],
                                                                (int) args[1],
                                                                (int) args[2]);
                                                case "setGameRule", "setSpawnLocation" -> true;
                                                default -> primitive(method.getReturnType());
                                            });
        }

        Block block(int x, int y, int z) {
            Cell cell = new Cell(x, y, z);
            return (Block)
                    Proxy.newProxyInstance(
                            Block.class.getClassLoader(),
                            new Class<?>[] {Block.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getWorld" -> world;
                                        case "getX" -> x;
                                        case "getY" -> y;
                                        case "getZ" -> z;
                                        case "getLocation" -> new Location(world, x, y, z);
                                        case "getType" -> blocks.getOrDefault(cell, Material.AIR);
                                        case "setType" -> {
                                            blocks.put(cell, (Material) args[0]);
                                            yield null;
                                        }
                                        default -> primitive(method.getReturnType());
                                    });
        }
    }
}
