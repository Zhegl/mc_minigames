package me.petr1furious.potatominigames.core.world;

import java.io.File;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;

import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;

/**
 * Advanced Slime Paper integration: clones a template slime world over the game
 * world so every game starts on pristine terrain.
 */
public final class SlimeWorldService {
    private final JavaPlugin plugin;
    private final PropertyBag config = new PropertyBag("arena.asp");
    private final Property<Boolean> enable = config.define("enable", PropertyCodec.BOOL, false);
    private final Property<String> templateWorld = config.define("templateWorld", PropertyCodec.STRING, "template");
    private final Property<String> gameWorld = config.define("gameWorld", PropertyCodec.STRING, "game");
    private final Property<Boolean> resetOnStart = config.define("resetOnStart", PropertyCodec.BOOL, true);

    private AdvancedSlimePaperAPI asp;
    private SlimeFileLoader fileLoader;

    public SlimeWorldService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Templates and imports live where the ASP plugin keeps its file worlds. */
    public SlimeFileLoader fileLoader() {
        if (fileLoader == null) {
            fileLoader = new SlimeFileLoader(new File(Bukkit.getWorldContainer(), "slime_worlds"));
        }
        return fileLoader;
    }

    public boolean templateExists(String templateName) {
        return fileLoader().worldExists(templateName);
    }

    /**
     * Loads a fresh in-memory copy of a slime file under a new name. The template file
     * is only read; the copy is never saved anywhere and goes away with
     * {@link #discardClone}.
     */
    public World cloneTemplate(String templateName, String worldName) {
        init();
        if (Bukkit.getWorld(worldName) != null) {
            throw new IllegalStateException("A world called " + worldName + " is already loaded");
        }
        SlimeWorld template;
        try {
            template = asp.readWorld(fileLoader(), templateName, true, new SlimePropertyMap());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read slime template " + templateName, exception);
        }
        asp.loadWorld(template.clone(worldName), true);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Slime clone " + worldName + " did not load");
        }
        return world;
    }

    /** Unloads a clone made by {@link #cloneTemplate} without saving it, moving anyone left inside out first. */
    public void discardClone(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        final var spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
        for (final var player : world.getPlayers()) {
            player.teleport(spawn);
        }
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("ASP: failed to unload clone " + worldName);
        }
    }

    /**
     * Converts a vanilla world folder (which must not be loaded) into a slime file
     * called {@code templateName}, replacing any older one.
     */
    public void importVanilla(File worldFolder, String templateName) throws Exception {
        init();
        if (fileLoader().worldExists(templateName)) {
            fileLoader().deleteWorld(templateName);
        }
        SlimeWorld imported = asp.readVanillaWorld(worldFolder, templateName, fileLoader());
        asp.saveWorld(imported);
    }

    public PropertyBag config() {
        return config;
    }

    public boolean isEnabled() {
        return enable.get();
    }

    public String gameWorldName() {
        return gameWorld.get();
    }

    /**
     * The world a game should actually play in: when slime-world arenas are
     * enabled the cloned game world wins, so it cannot drift out of sync with a
     * game's own world setting.
     */
    public World resolveWorld(String configuredWorldName) {
        String name = enable.get() ? gameWorld.get() : configuredWorldName;
        World world = Bukkit.getWorld(name);
        if (world == null) {
            throw new IllegalStateException("World '" + name + "' is not loaded");
        }
        return world;
    }

    private void init() {
        if (asp == null) {
            asp = AdvancedSlimePaperAPI.instance();
        }
    }

    private void unloadGameWorld() {
        final var worldName = Objects.requireNonNull(gameWorld.get());
        if (asp.getLoadedWorld(worldName) == null) {
            return;
        }
        final var world = Objects.requireNonNull(Bukkit.getWorld(worldName));
        final var spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
        for (final var player : world.getPlayers()) {
            player.teleport(spawn);
        }
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("ASP: failed to unload world " + worldName);
        }
    }

    /** Clones the template over the game world. No-op unless enabled (and resetOnStart, unless forced). */
    public void setupWorld(boolean force) {
        if (!enable.get()) {
            return;
        }
        if (!force && !resetOnStart.get()) {
            return;
        }
        init();
        unloadGameWorld();
        final var sourceWorldName = Objects.requireNonNull(templateWorld.get());
        final var destWorldName = Objects.requireNonNull(gameWorld.get());

        final var sourceSlimeWorld = asp.getLoadedWorld(sourceWorldName);
        if (sourceSlimeWorld == null) {
            throw new IllegalStateException("ASP: template world " + sourceWorldName + " is not loaded");
        }
        final var newSlimeWorld = sourceSlimeWorld.clone(destWorldName);
        asp.loadWorld(newSlimeWorld, true);

        World sourceWorld = Bukkit.getWorld(sourceWorldName);
        World destWorld = Bukkit.getWorld(destWorldName);
        if (sourceWorld == null || destWorld == null) {
            plugin.getLogger().warning("ASP: could not copy world settings after cloning");
            return;
        }
        copyWorldSettings(sourceWorld, destWorld);
    }

    private void copyWorldSettings(World sourceWorld, World destWorld) {
        destWorld.setDifficulty(sourceWorld.getDifficulty());
        for (GameRule<?> gameRule : Registry.GAME_RULE) {
            copyGameRule(sourceWorld, destWorld, gameRule);
        }
    }

    private <T> void copyGameRule(World sourceWorld, World destWorld, GameRule<T> gameRule) {
        try {
            T value = sourceWorld.getGameRuleValue(gameRule);
            if (value != null) {
                destWorld.setGameRule(gameRule, value);
            }
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().fine("ASP: skipping unsupported game rule " + gameRule.getKey());
        }
    }
}
