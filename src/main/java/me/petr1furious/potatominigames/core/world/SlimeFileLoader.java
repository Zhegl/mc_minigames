package me.petr1furious.potatominigames.core.world;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.loaders.SlimeLoader;

/** {@code <name>.slime} files in ASP's layout, so templates are read on demand instead of kept loaded. */
public final class SlimeFileLoader implements SlimeLoader {
    private static final String EXTENSION = ".slime";

    private final File folder;

    public SlimeFileLoader(File folder) {
        this.folder = folder;
    }

    private File fileFor(String worldName) {
        return new File(folder, worldName + EXTENSION);
    }

    @Override
    public byte[] readWorld(String worldName) throws UnknownWorldException, IOException {
        File file = fileFor(worldName);
        if (!file.isFile()) {
            throw new UnknownWorldException(worldName);
        }
        return Files.readAllBytes(file.toPath());
    }

    @Override
    public boolean worldExists(String worldName) {
        return fileFor(worldName).isFile();
    }

    @Override
    public List<String> listWorlds() {
        List<String> names = new ArrayList<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(EXTENSION));
        if (files != null) {
            for (File file : files) {
                names.add(file.getName().substring(0, file.getName().length() - EXTENSION.length()));
            }
        }
        return names;
    }

    @Override
    public void saveWorld(String worldName, byte[] bytes) throws IOException {
        folder.mkdirs();
        // Atomic replace, so a crash never leaves a half-written template.
        File temporary = new File(folder, "." + worldName + EXTENSION + ".tmp");
        Files.write(temporary.toPath(), bytes);
        Files.move(temporary.toPath(), fileFor(worldName).toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void deleteWorld(String worldName) throws UnknownWorldException, IOException {
        File file = fileFor(worldName);
        if (!file.isFile()) {
            throw new UnknownWorldException(worldName);
        }
        Files.delete(file.toPath());
    }
}
