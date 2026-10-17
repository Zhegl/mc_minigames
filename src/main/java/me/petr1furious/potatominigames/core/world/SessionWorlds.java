package me.petr1furious.potatominigames.core.world;

import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.World;

/**
 * The worlds one game session plays in, so that events can be routed to the
 * session that owns their world and so that a session's teardown only touches
 * its own worlds while other games keep running.
 *
 * <p>Games reach the shared world services through this facade: everything it
 * creates or resolves is recorded as owned by the session.
 */
public final class SessionWorlds {
    private final ThrowawayWorldService throwaway;
    private final SlimeWorldService slime;
    private final Set<String> throwawayIds = new LinkedHashSet<>();
    private final Set<String> ownedWorlds = new LinkedHashSet<>();
    private final Set<String> slimeClones = new LinkedHashSet<>();

    public SessionWorlds(ThrowawayWorldService throwaway, SlimeWorldService slime) {
        this.throwaway = throwaway;
        this.slime = slime;
    }

    // --- throwaway worlds (bingo, chase) ---

    /** See {@link ThrowawayWorldService#create}; the whole set, including dimensions made later, is ours. */
    public World create(String id, World.Environment... eagerDimensions) {
        throwawayIds.add(id);
        return throwaway.create(id, eagerDimensions);
    }

    // --- the shared ASP arena (warfare) ---

    public void setupWorld(boolean force) {
        slime.setupWorld(force);
    }

    public World resolveWorld(String configuredWorldName) {
        World world = slime.resolveWorld(configuredWorldName);
        ownedWorlds.add(world.getName());
        return world;
    }

    // --- per-game slime clones (pillars) ---

    /**
     * Loads a fresh copy of a slime template under a new name. The copy lives in
     * memory only and is unloaded, never saved, when the session is cleaned up.
     */
    public World cloneSlime(String templateName, String worldName) {
        World world = slime.cloneTemplate(templateName, worldName);
        slimeClones.add(world.getName());
        ownedWorlds.add(world.getName());
        return world;
    }

    /** Claims an already loaded world for this session, e.g. a fixed arena world. */
    public void claim(World world) {
        ownedWorlds.add(world.getName());
    }

    public boolean owns(World world) {
        if (world == null) {
            return false;
        }
        if (ownedWorlds.contains(world.getName())) {
            return true;
        }
        String base = throwaway.baseOf(world);
        return base != null && throwawayIds.contains(base.substring(ThrowawayWorldService.PREFIX.length()));
    }

    /** Destroys what this session created; shared worlds it only played in are left loaded. */
    public void destroyOwn() {
        for (String id : throwawayIds) {
            throwaway.destroy(id);
        }
        throwawayIds.clear();
        for (String name : slimeClones) {
            slime.discardClone(name);
        }
        slimeClones.clear();
        ownedWorlds.clear();
    }
}
