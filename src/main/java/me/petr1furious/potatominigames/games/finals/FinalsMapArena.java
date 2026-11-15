package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Consumer;

/** Geometry shared by the generated city and a disposable copy of a custom map. */
public interface FinalsMapArena {
    World world();

    void build();

    /** Large imported arenas prepare incrementally before contestants enter the world. */
    default void buildAsync(JavaPlugin plugin, Runnable ready, Consumer<RuntimeException> failed) {
        build();
        ready.run();
    }

    default void cancelBuild() {}

    Location center();

    Location spawn(int team, int memberIndex);

    /** All starting-position candidates for one team. */
    default List<Location> spawnLocations(int team) {
        return List.of(spawn(team, 0));
    }

    Location vault();

    default List<FinalsMapJumpPads.Pad> jumpPads() { return List.of(); }

    /** All configured candidates, including the currently active vault. */
    List<Location> vaultLocations();

    /** Start the next objective at a random candidate, avoiding an immediate repeat. */
    void nextVault();

    Location station(int station);

    /** Publish the live bottom-center of a simulated cashout body. */
    default void moveStation(int station, Location position) {}

    /** All configured cashout candidates, including both active stations. */
    List<Location> cashoutLocations();

    /** Replace only the completed station, using an inactive candidate when one exists. */
    void nextCashout(int station);

    boolean contains(Location location);

    boolean isCover(Block block);

    int stationAt(Block block);

    boolean isVault(Block block);

    double borderSize();
}
