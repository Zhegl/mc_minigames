package me.petr1furious.potatominigames.core.world;

import java.io.File;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;

import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;

/** Advanced Slime Paper integration: per-game clones of template worlds. */
public final class SlimeWorldService {
    private final JavaPlugin plugin;
    private final PropertyBag config = new PropertyBag("arena.asp");
    private final Property<Boolean> enable = config.define("enable", PropertyCodec.BOOL, false);
    private final Property<String> templateWorld = config.define("templateWorld", PropertyCodec.STRING, "template")
            .suggest(() -> Bukkit.getWorlds().stream().map(World::getName).toList());
    private final Property<String> gameWorld = config.define("gameWorld", PropertyCodec.STRING, "game")
            .suggest(() -> Bukkit.getWorlds().stream().map(World::getName).toList());
    private final Property<Boolean> resetOnStart = config.define("resetOnStart", PropertyCodec.BOOL, true);

    private AdvancedSlimePaperAPI asp;
    private SlimeFileLoader fileLoader;

    public SlimeWorldService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** ASP's file world directory. */
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
     * In-memory copy, never saved; see {@link #discardClone}. A copy left loaded by an earlier game
     * is discarded first: clones are only ever made by the one session of their game.
     */
    public World cloneTemplate(String templateName, String worldName) {
        init();
        World leftover = Bukkit.getWorld(worldName);
        if (leftover != null) {
            plugin.getLogger().warning("ASP: " + worldName + " was still loaded; discarding it");
            evacuate(leftover);
            Bukkit.unloadWorld(leftover, false);
        }
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
        // Slime worlds default to peaceful.
        world.setDifficulty(Bukkit.getWorlds().getFirst().getDifficulty());
        return world;
    }

    /** Unloads without saving, evacuating players first; retried for a few seconds if refused. */
    public void discardClone(String worldName) {
        discardClone(worldName, 3);
    }

    private void discardClone(String worldName, int retries) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        evacuate(world);
        if (Bukkit.unloadWorld(world, false)) {
            return;
        }
        if (retries > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> discardClone(worldName, retries - 1), 20L);
        } else {
            plugin.getLogger().warning("ASP: failed to unload clone " + worldName + "; still in it: "
                    + world.getPlayers().stream().map(Player::getName).toList());
        }
    }

    /** To the main world's spawn; dead players cannot be teleported, so they are respawned first. */
    private static void evacuate(World world) {
        final var spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
        for (final Player player : world.getPlayers()) {
            if (player.isDead()) {
                player.spigot().respawn();
            }
            player.teleport(spawn);
        }
    }

    /** The vanilla world folder must not be loaded; replaces any older template. */
    public void importVanilla(File worldFolder, String templateName, int spawnX, int spawnY, int spawnZ)
            throws Exception {
        init();
        // ASP refuses to import over an existing world; the old one is restored on failure.
        byte[] previous = fileLoader().worldExists(templateName) ? fileLoader().readWorld(templateName) : null;
        if (previous != null) {
            fileLoader().deleteWorld(templateName);
        }
        try {
            SlimeWorld imported = asp.readVanillaWorld(worldFolder, templateName, fileLoader());
            setSpawn(imported.getPropertyMap(), spawnX, spawnY, spawnZ);
            asp.saveWorld(imported);
        } catch (Exception failure) {
            if (previous != null) {
                fileLoader().saveWorld(templateName, previous);
            }
            throw failure;
        }
    }

    /** Void world; replaces any older template. */
    public void createEmptyTemplate(String templateName, int spawnX, int spawnY, int spawnZ) throws Exception {
        init();
        if (fileLoader().worldExists(templateName)) {
            fileLoader().deleteWorld(templateName);
        }
        SlimePropertyMap properties = new SlimePropertyMap();
        setSpawn(properties, spawnX, spawnY, spawnZ);
        asp.saveWorld(asp.createEmptyWorld(templateName, false, properties, fileLoader()));
    }

    private static void setSpawn(SlimePropertyMap properties, int x, int y, int z) {
        properties.setValue(SlimeProperties.SPAWN_X, x);
        properties.setValue(SlimeProperties.SPAWN_Y, y);
        properties.setValue(SlimeProperties.SPAWN_Z, z);
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

    /** With slime arenas enabled the cloned world wins over the game's own world setting. */
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
        evacuate(world);
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("ASP: failed to unload world " + worldName);
        }
    }

    /** No-op unless enabled (and resetOnStart, unless forced). */
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
