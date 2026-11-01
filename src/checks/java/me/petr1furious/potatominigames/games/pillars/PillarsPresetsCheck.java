package me.petr1furious.potatominigames.games.pillars;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Run by Gradle check; exercises the real YAML store without a server or test dependency. */
public final class PillarsPresetsCheck {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final List<String> RESERVED = List.of("original", "fusion", "party", "rave", "adhd");

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("pillars-presets-check-");
        try {
            Path file = directory.resolve("data/pillars-presets.yml");
            PillarsPresets store = new PillarsPresets(file, RESERVED);
            check(!Files.exists(file), "Opening an empty store must not write data");
            var first = store.save(ALICE, "Alice", "MiX", List.of("adhd", "rave", "tactical", "chaos", "shuffle"));
            check(first.name().equals("mix"), "Names must normalize case");
            check(first.modifiers().equals(List.of("chaos", "shuffle", "tactical", "rave", "adhd")),
                    "Modifier selection must persist in canonical order");
            for (int i = 1; i < 5; i++) {
                store.save(ALICE, "Alice", "alice" + i, List.of());
            }
            expect(IllegalArgumentException.class, () -> store.save(ALICE, "Alice", "sixth", List.of("chaos")));
            store.save(BOB, "Bob", "bob", List.of("big_house", "party"));
            store.save(ALICE, "Alice", "mix", List.of("tactical"));
            check(store.find("MIX").modifiers().equals(List.of("tactical")), "Overwrite must work at the quota");
            expect(IllegalArgumentException.class, () -> store.save(BOB, "Bob", "mix", List.of("rave")));
            expect(IllegalArgumentException.class, () -> store.delete(BOB, "mix"));
            for (String invalid : List.of("fusion", "adhd", "../escape", "bad.name", "bad:name", "two words", "_bad",
                    "a".repeat(25))) {
                expect(IllegalArgumentException.class, () -> store.save(BOB, "Bob", invalid, List.of()));
            }
            expect(IllegalArgumentException.class,
                    () -> store.save(BOB, "Bob", "invalid", List.of("party", "rave")));
            expect(IllegalArgumentException.class,
                    () -> store.save(BOB, "Bob", "invalid", List.of("chaos", "chaos")));
            expect(IllegalArgumentException.class,
                    () -> store.save(BOB, "Bob", "invalid", List.of("lucky")));
            store.delete(ALICE, "alice4");
            store.save(ALICE, "Alice", "replacement", List.of("big_house", "adhd"));
            PillarsPresets loaded = new PillarsPresets(file, RESERVED);
            check(loaded.all().equals(store.all()), "Owners, names and modifiers must survive reload");
            expect(IllegalArgumentException.class,
                    () -> loaded.save(ALICE, "Alice", "sixth", List.of("chaos")));
            expect(UnsupportedOperationException.class, () -> loaded.find("mix").modifiers().add("chaos"));

            String persisted = Files.readString(file);
            Path backup = directory.resolve("backup.yml");
            Files.move(file, backup);
            Files.createDirectory(file);
            expect(IOException.class, () -> store.save(ALICE, "Alice", "mix", List.of("chaos")));
            check(store.find("mix").modifiers().equals(List.of("tactical")), "Failed save must not change memory");
            expect(IOException.class, () -> store.delete(ALICE, "mix"));
            check(store.find("mix") != null, "Failed delete must not change memory");
            check(Files.readString(backup).equals(persisted), "Failed writes must preserve the old file");
            Files.delete(file);
            Files.move(backup, file);
            check(new PillarsPresets(file, RESERVED).all().equals(store.all()), "Failed writes must preserve reload");

            Path corrupt = directory.resolve("corrupt.yml");
            for (String invalid : List.of("presets: [", "presets: 42", "unexpected: data",
                    "presets:\n  bad:\n    owner: not-a-uuid\n    modifiers: []\n")) {
                Files.writeString(corrupt, invalid);
                expect(IOException.class, () -> new PillarsPresets(corrupt, RESERVED));
                check(Files.readString(corrupt).equals(invalid), "Invalid files must not be rewritten");
            }
            System.out.println("Pillars presets: quota, ownership, validation, reload and atomic failure checks passed");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expect(Class<? extends Exception> type, Action action) throws Exception {
        try {
            action.run();
        } catch (Exception exception) {
            if (type.isInstance(exception)) {
                return;
            }
            throw exception;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
