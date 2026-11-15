package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsMapData.*;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.world.SlimeFileLoader;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Draft setup plus immutable Slime templates. Matches only ever use disposable world copies. */
public final class FinalsMaps {
    private record Entry(Draft draft, Saved saved) {}

    private final JavaPlugin plugin;
    private final Path root, imports, templates, registry;
    private final FinalsMapDownload downloads;
    private final java.util.Set<String> importing = new java.util.HashSet<>();
    private Map<String, Entry> entries = new LinkedHashMap<>();
    private String selected = "generated";
    private boolean closed;

    public FinalsMaps(JavaPlugin plugin) {
        this.plugin = plugin;
        root = plugin.getDataFolder().toPath().resolve("finals");
        imports = root.resolve("imports");
        templates = root.resolve("templates");
        registry = root.resolve("maps.yml");
        try {
            Files.createDirectories(imports);
            Files.createDirectories(templates);
            load();
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load Finals map registry", error);
        }
        downloads = new FinalsMapDownload(plugin, imports);
    }

    public List<String> names() {
        List<String> result = new ArrayList<>();
        result.add("generated");
        entries.forEach((name, entry) -> {
            if (entry.saved != null) result.add(name);
        });
        return List.copyOf(result);
    }

    public List<String> drafts() {
        return List.copyOf(entries.keySet());
    }

    public String selected() {
        return selected;
    }

    public void select(String name) {
        if (!"generated".equals(name)) {
            Entry entry = entry(name);
            if (entry.saved == null) throw failure("not_saved", name);
            validateTemplate(entry.saved);
        }
        commit(entries, name);
    }

    public FinalsMapArena open(SessionServices services, String nullableName) {
        String name = nullableName == null || nullableName.isBlank() ? selected : nullableName;
        if (name.equals("generated"))
            return new FinalsArena(services.worlds().createVoid("finals"));
        Entry entry = entry(name);
        if (entry.saved == null) throw failure("not_saved", name);
        validateTemplate(entry.saved);
        if (entry.saved.template().endsWith(".slime")) {
            String template = entry.saved.template();
            return new FinalsImportedArena(
                    entry.saved,
                    services.worlds(),
                    new SlimeFileLoader(templates.toFile()),
                    template.substring(0, template.length() - ".slime".length()),
                    "finals_match_" + UUID.randomUUID().toString().replace("-", ""));
        }
        FinalsMapSnapshot snapshot = FinalsMapSnapshot.open(templates, entry.saved);
        services.messages().broadcast(I18n.tr("finals.map.upgrading", name));
        World world = services.worlds().createVoid("finals");
        prepareEditor(world);
        world.setGameRule(org.bukkit.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
        world.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);
        if (entry.saved.bounds().minY() < world.getMinHeight()
                || entry.saved.bounds().maxY() >= world.getMaxHeight()) throw failure("height");
        return new FinalsImportedArena(
                world, entry.saved, snapshot, () -> migration(name, entry.saved, world));
    }

    public void create(String name, Location location) {
        requireNewName(name);
        World world = location.getWorld();
        if (world == null) throw failure("persistent_world");
        createInWorld(name, world.getName());
    }

    /** A separate playable area in the same editor, without copying terrain or setup points. */
    public void create(String name, String sourceMap) {
        requireNewName(name);
        createInWorld(name, entry(sourceMap).draft.world);
    }

    private void createInWorld(String name, String worldName) {
        if (worldName.startsWith(
                        me.petr1furious.potatominigames.core.world.ThrowawayWorldService.PREFIX)
                || worldName.startsWith("finals_match_")) throw failure("persistent_world");
        Draft draft = new Draft();
        draft.world = worldName;
        inheritEditor(draft);
        Map<String, Entry> next = new LinkedHashMap<>(entries);
        next.put(name, new Entry(draft, null));
        commit(next, selected);
    }

    /** Recover the shared imported world's reload information while keeping this area's setup. */
    private void inheritEditor(Draft draft) {
        if (draft.imported) return;
        for (Entry entry : entries.values()) {
            Draft source = entry.draft;
            if (source.imported && source.world.equals(draft.world)) {
                draft.imported = true;
                draft.folder = source.folder;
                return;
            }
        }
    }

    public void importWorld(String name, String source) {
        requireNewName(name);
        importWorldSource(name, source);
    }

    private void requireNewName(String name) {
        FinalsMapFiles.name(name);
        if (entries.containsKey(name)) throw failure("exists", name);
        if (importing.contains(name)) throw failure("importing", name);
    }

    /** Downloads off-thread; the existing world importer and registry run on the server thread. */
    public void importUrlAsync(
            String name, String url, Runnable success, Consumer<RuntimeException> failure) {
        if (closed) throw failure("io");
        requireNewName(name);
        importing.add(name);
        try {
            downloads.download(
                    url,
                    source -> {
                        if (closed) return;
                        RuntimeException problem = null;
                        try {
                            importWorldSource(name, source);
                        } catch (RuntimeException error) {
                            problem = error;
                        } finally {
                            importing.remove(name);
                        }
                        if (closed) return;
                        if (problem != null) failure.accept(problem);
                        else success.run();
                    },
                    error -> {
                        importing.remove(name);
                        if (!closed) failure.accept(error);
                    });
        } catch (RuntimeException error) {
            importing.remove(name);
            throw error;
        }
    }

    private void importWorldSource(String name, String source) {
        String worldName =
                "finals_edit_" + name + "_" + UUID.randomUUID().toString().substring(0, 8);
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path destination = container.resolve(worldName);
        World world = null;
        Path loadedFolder = destination;
        boolean created = false;
        try {
            Path input = FinalsMapFiles.source(imports, source);
            if (Files.exists(destination)) throw failure("source");
            Files.createDirectory(destination);
            created = true;
            FinalsMapFiles.copyWorldContents(input, destination);
            world = new WorldCreator(worldName).generateStructures(false).createWorld();
            if (world == null) throw failure("world_load");
            loadedFolder = world.getWorldFolder().toPath().toAbsolutePath().normalize();
            if (!loadedFolder.startsWith(container)
                    || !loadedFolder.getFileName().toString().equals(worldName))
                throw failure("world_load");
            if (closed) throw failure("io");
            prepareEditor(world);
            Draft draft = new Draft();
            draft.world = worldName;
            draft.imported = true;
            // Paper 26.3 migrates a copied legacy world into its dimension directory.
            // Persist the actual API-reported location, so editor reload works on both layouts.
            draft.folder = container.relativize(loadedFolder).toString().replace('\\', '/');
            Map<String, Entry> next = new LinkedHashMap<>(entries);
            next.put(name, new Entry(draft, null));
            commit(next, selected);
        } catch (IOException | RuntimeException error) {
            if (world != null && world.getPlayers().isEmpty()) Bukkit.unloadWorld(world, false);
            if (created && Bukkit.getWorld(worldName) == null) {
                cleanFailedImport(container, destination);
                if (!loadedFolder.equals(destination)) cleanFailedImport(container, loadedFolder);
            }
            if (error instanceof IllegalArgumentException invalid) throw invalid;
            plugin.getLogger().warning("Finals map import failed: " + error.getMessage());
            throw failure("io");
        }
    }

    /**
     * Only the exact new import directory is eligible for rollback; original imports are read-only.
     */
    private void cleanFailedImport(Path container, Path destination) {
        if (!destination.startsWith(container)
                || destination.equals(container)
                || !destination.getFileName().toString().startsWith("finals_edit_")
                || !Files.exists(destination, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(destination)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        } catch (IOException cleanup) {
            plugin.getLogger().warning("Cannot remove incomplete Finals import: " + destination);
        }
    }

    public Location editorLocation(String name) {
        Entry entry = entry(name);
        World world = editor(entry.draft);
        return entry.draft.red.isEmpty()
                ? world.getSpawnLocation()
                : entry.draft.red.getFirst().at(world);
    }

    Draft previewDraft(String name) {
        return entry(name).draft.copy();
    }

    public void corner(String name, int index, Location location) {
        update(
                name,
                location,
                draft -> {
                    if (index == 0) draft.first = Point.of(location);
                    else draft.second = Point.of(location);
                    draft.fullHeight(
                            location.getWorld().getMinHeight(), location.getWorld().getMaxHeight());
                });
    }

    public void spawn(String name, int team, Location location) {
        update(
                name,
                location,
                draft -> {
                    List<Point> spawns = team == 0 ? draft.red : draft.blue;
                    if (spawns.size() >= 16) throw failure("spawn_limit");
                    Point point = Point.of(location).centered();
                    if (spawns.stream().anyMatch(point::sameBlock))
                        throw failure("spawn_duplicate");
                    spawns.add(point);
                });
    }

    public void clearSpawns(String name, int team) {
        update(name, null, draft -> (team == 0 ? draft.red : draft.blue).clear());
    }

    public void spawnRemove(String name, int team, int number) {
        if (team < 0 || team > 1) throw new IllegalArgumentException("Team must be 0 or 1");
        update(name, null, draft -> removePoint(team == 0 ? draft.red : draft.blue, number));
    }

    public void vault(String name, Location location) {
        update(name, location, draft -> draft.setVault(Point.of(location).centered()));
    }

    public void vaultAdd(String name, Location location) {
        update(name, location, draft -> draft.addVault(Point.of(location).centered()));
    }

    public void vaultClear(String name) {
        update(name, null, draft -> draft.vaults.clear());
    }

    public void vaultRemove(String name, int number) {
        update(name, null, draft -> removePoint(draft.vaults, number));
    }

    public void station(String name, int station, Location location) {
        update(name, location, draft -> draft.setCashout(station, Point.of(location).centered()));
    }

    public void cashoutAdd(String name, Location location) {
        update(name, location, draft -> draft.addCashout(Point.of(location).centered()));
    }

    public void cashoutClear(String name) {
        update(name, null, draft -> draft.cashouts.clear());
    }

    public void cashoutRemove(String name, int number) {
        update(name, null, draft -> removePoint(draft.cashouts, number));
    }

    private static void removePoint(List<Point> points, int number) {
        if (number < 1 || number > points.size())
            throw failure("point_number", number, points.size());
        points.remove(number - 1);
    }

    public void jumpPad(String name, Location location, boolean targeted) {
        update(name, location, draft -> {
            Point point = Point.of(location).centered();
            if (draft.jumpPads.stream().anyMatch(p -> p.origin().sameBlock(point))) throw failure("pad_duplicate");
            draft.jumpPads.add(new JumpPad(point, targeted, null));
        });
    }

    public void jumpPadTarget(String name, int number, Location location) {
        update(name, location, draft -> {
            if (number < 1 || number > draft.jumpPads.size()) throw failure("point_number", number, draft.jumpPads.size());
            JumpPad pad = draft.jumpPads.get(number - 1);
            if (!pad.targeted()) throw failure("pad_not_targeted");
            draft.jumpPads.set(number - 1, new JumpPad(pad.origin(), true, Point.of(location).centered()));
        });
    }

    public void jumpPadRemove(String name, int number) {
        update(name, null, draft -> {
            if (number < 1 || number > draft.jumpPads.size()) throw failure("point_number", number, draft.jumpPads.size());
            draft.jumpPads.remove(number - 1);
        });
    }

    public void save(String name) {
        SaveWork work = beginSave(name);
        try {
            work.capture.finish();
            work.commit();
        } catch (RuntimeException error) {
            work.cancel();
            throw error;
        }
    }

    /** Captures bounded pieces on the main thread, yielding between ticks. */
    public void saveAsync(String name, Runnable success, Consumer<RuntimeException> failure) {
        saveAsync(name, ignored -> {}, success, failure);
    }

    public void saveAsync(
            String name,
            Consumer<Integer> progress,
            Runnable success,
            Consumer<RuntimeException> failure) {
        SaveWork work = beginSave(name);
        try {
            work.task =
                    new org.bukkit.scheduler.BukkitRunnable() {
                        private int ticks;

                        @Override
                        public void run() {
                            try {
                                long deadline = System.nanoTime() + 4_000_000L;
                                do {
                                    if (work.capture.step()) {
                                        cancel();
                                        work.commit();
                                        success.run();
                                        return;
                                    }
                                } while (!work.capture.waiting() && System.nanoTime() < deadline);
                                if (++ticks % 100 == 0)
                                    progress.accept(
                                            (int)
                                                    (FinalsSlimeSnapshot.progress(work.capture)
                                                            * 100));
                            } catch (RuntimeException error) {
                                cancel();
                                work.cancel();
                                failure.accept(error);
                            }
                        }
                    }.runTaskTimer(plugin, 1, 1);
        } catch (RuntimeException error) {
            work.cancel();
            throw error;
        }
    }

    private final java.util.Set<String> saving = new java.util.HashSet<>();
    private final java.util.Set<SaveWork> captures = new java.util.HashSet<>();

    /** Releases in-progress snapshot work when the plugin shuts down. */
    public void close() {
        closed = true;
        downloads.close();
        importing.clear();
        for (SaveWork work : List.copyOf(captures)) work.cancel();
    }

    private SaveWork beginSave(String name) {
        Entry previous = entry(name);
        if (!saving.add(name)) throw failure("saving");
        try {
            Draft draft = previous.draft.copy();
            World world = editor(draft);
            if (captures.stream().anyMatch(work -> work.worldId.equals(world.getUID())))
                throw failure("saving");
            draft.fullHeight(world.getMinHeight(), world.getMaxHeight());
            String filename = name + "-" + UUID.randomUUID() + ".slime";
            Saved saved = Saved.from(draft, filename);
            validateStandingPoints(world, saved);
            Path target = templates.resolve(filename);
            Path temporary = templates.resolve(filename + ".tmp");
            SaveWork work =
                    new SaveWork(
                            name,
                            world.getUID(),
                            saved,
                            target,
                            temporary,
                            FinalsSlimeSnapshot.capture(plugin, world, saved.bounds(), temporary));
            captures.add(work);
            return work;
        } catch (RuntimeException error) {
            saving.remove(name);
            throw error;
        }
    }

    /**
     * Rebuild old structure snapshots once, then preserve that exact snapshot as a Slime template.
     */
    private FinalsMapSnapshot.Work migration(String name, Saved previous, World world) {
        // A concurrent editor save will publish its own new template; never replace it with this
        // old map.
        if (saving.contains(name) || !previous.equals(entry(name).saved)) return null;
        saving.add(name);
        String filename = name + "-" + UUID.randomUUID() + ".slime";
        Saved converted =
                new Saved(
                        previous.bounds(),
                        previous.red(),
                        previous.blue(),
                        previous.vaults(),
                        previous.cashouts(),
                        previous.jumpPads(),
                        filename);
        Path target = templates.resolve(filename), temporary = templates.resolve(filename + ".tmp");
        final SaveWork work;
        try {
            work =
                    new SaveWork(
                            name,
                            world.getUID(),
                            converted,
                            target,
                            temporary,
                            FinalsSlimeSnapshot.capture(
                                    plugin, world, converted.bounds(), temporary));
            captures.add(work);
        } catch (RuntimeException error) {
            saving.remove(name);
            throw error;
        }
        return new FinalsMapSnapshot.Work() {
            private boolean done;

            public boolean step() {
                if (done) return true;
                if (!work.capture.step()) return false;
                work.commit();
                done = true;
                return true;
            }

            public void finish() {
                if (done) return;
                work.capture.finish();
                work.commit();
                done = true;
            }

            public void cancel() {
                work.cancel();
            }

            public boolean waiting() {
                return work.capture.waiting();
            }
        };
    }

    private final class SaveWork {
        final String name;
        final UUID worldId;
        final Saved saved;
        final Path target, temporary;
        final FinalsMapSnapshot.Work capture;
        org.bukkit.scheduler.BukkitTask task;
        boolean committed;

        SaveWork(
                String name,
                UUID worldId,
                Saved saved,
                Path target,
                Path temporary,
                FinalsMapSnapshot.Work capture) {
            this.name = name;
            this.worldId = worldId;
            this.saved = saved;
            this.target = target;
            this.temporary = temporary;
            this.capture = capture;
        }

        void commit() {
            try {
                FinalsMapFiles.replace(temporary, target);
                Map<String, Entry> next = new LinkedHashMap<>(entries);
                // Keep any draft edits made while this immutable snapshot was captured.
                Draft latest = entry(name).draft.copy();
                latest.fullHeight(saved.bounds().minY(), saved.bounds().maxY() + 1);
                next.put(name, new Entry(latest, saved));
                FinalsMaps.this.commit(next, selected);
                committed = true;
            } catch (IOException error) {
                plugin.getLogger().warning("Finals snapshot failed: " + error.getMessage());
                throw failure("io");
            } finally {
                saving.remove(name);
                captures.remove(this);
            }
        }

        void cancel() {
            if (task != null) task.cancel();
            capture.cancel();
            saving.remove(name);
            captures.remove(this);
            removeSnapshot(temporary);
            if (!committed) removeSnapshot(target);
        }
    }

    private void removeSnapshot(Path directory) {
        if (Files.isRegularFile(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.deleteIfExists(directory);
            } catch (IOException error) {
                plugin.getLogger()
                        .warning("Cannot remove incomplete Finals snapshot: " + directory);
            }
            return;
        }
        if (!Files.isDirectory(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.walkFileTree(
                    directory,
                    new java.nio.file.SimpleFileVisitor<>() {
                        @Override
                        public java.nio.file.FileVisitResult visitFile(
                                Path path, java.nio.file.attribute.BasicFileAttributes attributes)
                                throws IOException {
                            Files.delete(path);
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }

                        @Override
                        public java.nio.file.FileVisitResult postVisitDirectory(
                                Path path, IOException error) throws IOException {
                            if (error != null) throw error;
                            Files.delete(path);
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException error) {
            plugin.getLogger().warning("Cannot remove incomplete Finals snapshot: " + directory);
        }
    }

    static void validateStanding(World world, Point point) {
        int x = point.bx(), y = point.by(), z = point.bz();
        var feet = world.getBlockAt(x, y, z);
        var head = world.getBlockAt(x, y + 1, z);
        var below = world.getBlockAt(x, y - 1, z);
        if (!feet.isPassable()
                || feet.isLiquid()
                || !head.isPassable()
                || head.isLiquid()
                || !below.getType().isSolid()
                || below.isPassable()
                || hazardous(feet.getType())
                || hazardous(head.getType())
                || hazardous(below.getType())) throw failure("standable");
    }

    private static void validateStandingPoints(World world, Saved saved) {
        List<Component> invalid = new ArrayList<>();
        invalidStandingPoints(world, saved.red(), "red_spawn", invalid);
        invalidStandingPoints(world, saved.blue(), "blue_spawn", invalid);
        invalidStandingPoints(world, saved.vaults(), "vault", invalid);
        invalidStandingPoints(world, saved.cashouts(), "cashout", invalid);
        invalidStandingPoints(world, saved.jumpPads().stream().map(JumpPad::origin).toList(), "pad", invalid);
        invalidStandingPoints(world, saved.jumpPads().stream().map(p -> p.target() == null ? p.origin() : p.target()).toList(), "pad_target", invalid);
        if (!invalid.isEmpty())
            throw failure(
                    "unsafe_points",
                    Component.join(
                            net.kyori.adventure.text.JoinConfiguration.separator(
                                    Component.newline()),
                            invalid));
    }

    private static void invalidStandingPoints(
            World world, List<Point> points, String label, List<Component> invalid) {
        for (int index = 0; index < points.size(); index++) {
            Point point = points.get(index);
            try {
                validateStanding(world, point);
            } catch (IllegalArgumentException error) {
                invalid.add(
                        I18n.tr(
                                "finals.map.point.invalid",
                                I18n.tr("finals.map.point." + label, index + 1),
                                point.bx(),
                                point.by(),
                                point.bz()));
            }
        }
    }

    private static boolean hazardous(org.bukkit.Material material) {
        return switch (material) {
            case FIRE,
                    SOUL_FIRE,
                    POWDER_SNOW,
                    SWEET_BERRY_BUSH,
                    WITHER_ROSE,
                    MAGMA_BLOCK,
                    CACTUS,
                    CAMPFIRE,
                    SOUL_CAMPFIRE,
                    POINTED_DRIPSTONE ->
                    true;
            default -> false;
        };
    }

    public Component info(String name) {
        Entry entry = entry(name);
        Draft draft = entry.draft;
        Component status =
                I18n.tr(
                        entry.saved == null
                                ? "finals.map.status.draft"
                                : "finals.map.status.saved");
        return I18n.tr(
                "finals.map.info",
                name,
                status,
                draft.world,
                draft.red.size(),
                draft.blue.size(),
                mark(draft.first),
                mark(draft.second),
                draft.vaults.size(),
                Component.join(
                        net.kyori.adventure.text.JoinConfiguration.separator(Component.text("; ")),
                        draft.vaults.stream().map(this::mark).toList()),
                draft.cashouts.stream().filter(java.util.Objects::nonNull).count(),
                Component.join(
                        net.kyori.adventure.text.JoinConfiguration.separator(Component.text("; ")),
                        draft.cashouts.stream().map(this::mark).toList()));
    }

    private Component mark(Point point) {
        return point == null
                ? I18n.tr("finals.map.status.unset")
                : Component.text(point.bx() + ", " + point.by() + ", " + point.bz());
    }

    private Entry entry(String name) {
        Entry entry = entries.get(name);
        if (entry == null) throw failure("unknown", name);
        return entry;
    }

    private void update(String name, Location location, Consumer<Draft> edit) {
        Entry previous = entry(name);
        Draft draft = previous.draft.copy();
        if (location != null
                && (location.getWorld() == null
                        || !location.getWorld().getName().equals(draft.world)))
            throw failure("wrong_world", draft.world);
        edit.accept(draft);
        Map<String, Entry> next = new LinkedHashMap<>(entries);
        next.put(name, new Entry(draft, previous.saved));
        commit(next, selected);
    }

    private World editor(Draft draft) {
        World world = Bukkit.getWorld(draft.world);
        if (world == null && draft.imported && draft.world.matches("finals_edit_[a-z0-9_-]+")) {
            Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
            Path folder;
            try {
                folder =
                        FinalsMapFiles.source(
                                container, draft.folder == null ? draft.world : draft.folder);
            } catch (IOException | IllegalArgumentException invalid) {
                throw failure("world_load");
            }
            if (!folder.getFileName().toString().equals(draft.world)
                    || (!Files.isRegularFile(folder.resolve("level.dat"))
                            && !Files.isDirectory(folder.resolve("region"))))
                throw failure("world_load");
            world = new WorldCreator(draft.world).generateStructures(false).createWorld();
            if (world != null) prepareEditor(world);
        }
        if (world == null) throw failure("world_unloaded", draft.world);
        return world;
    }

    private static void prepareEditor(World world) {
        world.setGameRule(org.bukkit.GameRules.COMMAND_BLOCKS_WORK, false);
        world.setGameRule(org.bukkit.GameRules.SPAWNER_BLOCKS_WORK, false);
        world.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
        world.setGameRule(org.bukkit.GameRules.MOB_GRIEFING, false);
        world.setGameRule(org.bukkit.GameRules.RANDOM_TICK_SPEED, 0);
    }

    private void validateTemplate(Saved saved) {
        if (saved.template() != null && saved.template().matches("[a-z0-9_-]+\\.slime")) {
            Path template = templates.resolve(saved.template());
            if (!Files.isRegularFile(template, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                throw failure("template");
        } else FinalsMapSnapshot.open(templates, saved);
    }

    private void commit(Map<String, Entry> next, String selection) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("selected", selection);
        next.forEach((name, entry) -> {
            entry.draft.write(yaml.createSection("maps." + name + ".draft"));
            if (entry.saved != null) {
                ConfigurationSection section = yaml.createSection("maps." + name + ".saved");
                Draft snapshot = new Draft();
                snapshot.world = entry.draft.world;
                Bounds b = entry.saved.bounds();
                snapshot.first = new Point(b.minX(), b.minY(), b.minZ(), 0, 0);
                snapshot.second = new Point(b.maxX(), b.maxY(), b.maxZ(), 0, 0);
                snapshot.red = entry.saved.red();
                snapshot.blue = entry.saved.blue();
                snapshot.vaults = entry.saved.vaults();
                snapshot.cashouts = entry.saved.cashouts();
                snapshot.jumpPads = entry.saved.jumpPads();
                snapshot.write(section);
                section.set("template", entry.saved.template());
            }
        });
        Path temporary = root.resolve("maps-" + UUID.randomUUID() + ".tmp");
        try {
            yaml.save(temporary.toFile());
            FinalsMapFiles.replace(temporary, registry);
            entries = new LinkedHashMap<>(next);
            selected = selection;
        } catch (IOException error) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Finals map registry write failed: " + registry,
                            error);
            throw failure("io");
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    private void load() throws IOException {
        if (!Files.exists(registry)) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(registry.toFile());
        } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IOException(error);
        }
        ConfigurationSection maps = yaml.getConfigurationSection("maps");
        if (maps != null)
            for (String name : maps.getKeys(false)) {
                try {
                    FinalsMapFiles.name(name);
                    Draft draft = Draft.read(maps.getConfigurationSection(name + ".draft"));
                    ConfigurationSection section = maps.getConfigurationSection(name + ".saved");
                    Saved saved =
                            section == null
                                    ? null
                                    : Saved.from(
                                            Draft.read(section), section.getString("template", ""));
                    if (saved != null) validateTemplate(saved);
                    entries.put(name, new Entry(draft, saved));
                } catch (RuntimeException invalid) {
                    plugin.getLogger()
                            .warning(
                                    "Ignoring invalid Finals map "
                                            + name
                                            + ": "
                                            + invalid.getMessage());
                }
            }
        // Earlier /create aliases retained only the world name. Repair them after all potential
        // imported-world owners have been read, independently of their order in the registry.
        entries.values().forEach(entry -> inheritEditor(entry.draft));
        String requested = yaml.getString("selected", "generated");
        selected = names().contains(requested) ? requested : "generated";
    }

    static IllegalArgumentException failure(String key, Object... arguments) {
        return I18n.exception("finals.map.error." + key, arguments);
    }
}
