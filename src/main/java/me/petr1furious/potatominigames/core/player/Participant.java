package me.petr1furious.potatominigames.core.player;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.petr1furious.potatominigames.core.team.TeamColor;

public final class Participant {
    private final UUID id;
    private final String name;
    private TeamColor team;
    private boolean online = true;

    public Participant(Player player) {
        this.id = player.getUniqueId();
        this.name = player.getName();
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public TeamColor team() {
        return team;
    }

    public void setTeam(TeamColor team) {
        this.team = team;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    /** Null if offline. */
    public Player player() {
        return Bukkit.getPlayer(id);
    }
}
