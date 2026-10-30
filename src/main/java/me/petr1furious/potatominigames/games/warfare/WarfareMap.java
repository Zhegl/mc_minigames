package me.petr1furious.potatominigames.games.warfare;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import me.petr1furious.potatominigames.core.team.TeamColor;

/**
 * One arena from {@code maps} in games/warfare.yml. Each game plays in a throwaway copy of the
 * slime template ({@code slime_worlds/<template>.slime}), which keeps the coordinates of the
 * world it was imported from, so the center and beacons are in those coordinates.
 */
public record WarfareMap(String name, String template, Vector center, int radius, Map<TeamColor, Vector> beacons) {

    /** The game world's name: one game runs at a time, so one clone per map is enough. */
    public String worldName() {
        return "war_" + name.toLowerCase(Locale.ROOT);
    }

    /** Team beacon mode needs two of them. */
    public List<TeamColor> beaconTeams() {
        return List.of(TeamColor.values()).stream().filter(beacons::containsKey).toList();
    }

    public Location beacon(TeamColor team, World world) {
        Vector beacon = beacons.get(team);
        return beacon == null ? null : beacon.toLocation(world);
    }

    /** The template itself (e.g. opened with {@code /swp goto}) or this map's game copy. */
    public boolean isWorldOf(World world) {
        return world.getName().equalsIgnoreCase(template) || world.getName().equalsIgnoreCase(worldName());
    }
}
