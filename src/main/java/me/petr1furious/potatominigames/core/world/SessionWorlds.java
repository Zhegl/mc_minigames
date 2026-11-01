package me.petr1furious.potatominigames.core.world;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.World;

/** A session's worlds, for event routing and teardown. Everything resolved here is owned by the session. */
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

    /** Owns the whole set, including dimensions made later. */
    public World create(String id, World.Environment... eagerDimensions) {
        throwawayIds.add(id);
        return throwaway.create(id, eagerDimensions);
    }

    public World createVoid(String id) {
        throwawayIds.add(id);
        return throwaway.createVoid(id);
    }

    /** Seeds of the generated worlds; empty when the session only plays on templates or shared maps. */
    public List<Long> seeds() {
        List<Long> seeds = new ArrayList<>();
        for (String id : throwawayIds) {
            Long seed = throwaway.seed(id);
            if (seed != null && !seeds.contains(seed)) {
                seeds.add(seed);
            }
        }
        return seeds;
    }

    public void setupWorld(boolean force) {
        slime.setupWorld(force);
    }

    public World resolveWorld(String configuredWorldName) {
        World world = slime.resolveWorld(configuredWorldName);
        ownedWorlds.add(world.getName());
        return world;
    }

    /** In-memory copy, unloaded without saving on cleanup. */
    public World cloneSlime(String templateName, String worldName) {
        World world = slime.cloneTemplate(templateName, worldName);
        slimeClones.add(world.getName());
        ownedWorlds.add(world.getName());
        return world;
    }

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

    /** Shared worlds are left loaded. */
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
