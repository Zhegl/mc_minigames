package me.petr1furious.potatominigames.core.team;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;

import me.petr1furious.potatominigames.core.player.Participant;
import me.petr1furious.potatominigames.core.player.ParticipantRegistry;

/**
 * Two related jobs: the standing per-game colour assignments (which work with no
 * game running, backed by {@link TeamRegistry}), and grouping the current
 * session's participants by the colour they ended up with.
 */
public final class TeamService {
    private final TeamRegistry registry;
    private ParticipantRegistry participants;

    public TeamService(TeamRegistry registry) {
        this.registry = registry;
    }

    /** Re-reads data/teams.yml from disk, so `reload` picks up hand-edited team data. */
    public void reload() {
        registry.reload();
    }

    public void attach(ParticipantRegistry participants) {
        this.participants = participants;
    }

    public void detach() {
        this.participants = null;
    }

    // --- standing assignments: usable at any time ---

    public TeamColor standingTeam(String gameId, UUID playerId) {
        return registry.get(gameId, playerId);
    }

    public Map<UUID, TeamColor> standingTeams(String gameId) {
        return registry.all(gameId);
    }

    /** Sets (and persists) a colour, and applies it to a live participant if there is one. */
    public void assign(String gameId, UUID playerId, TeamColor color) {
        registry.set(gameId, playerId, color);
        if (participants != null) {
            participants.find(playerId).ifPresent(participant -> participant.setTeam(color));
        }
    }

    public void unassign(String gameId, UUID playerId) {
        registry.clear(gameId, playerId);
        if (participants != null) {
            participants.find(playerId).ifPresent(participant -> participant.setTeam(null));
        }
    }

    /** The colour after this one in enum order, persisted like an explicit pick. */
    public TeamColor cycle(String gameId, UUID playerId) {
        TeamColor current = standingTeam(gameId, playerId);
        TeamColor next = current == null
                ? TeamColor.values()[0]
                : TeamColor.values()[(current.ordinal() + 1) % TeamColor.values().length];
        assign(gameId, playerId, next);
        return next;
    }

    public void swap(String gameId, UUID first, UUID second) {
        TeamColor firstColor = standingTeam(gameId, first);
        TeamColor secondColor = standingTeam(gameId, second);
        if (secondColor == null) {
            unassign(gameId, first);
        } else {
            assign(gameId, first, secondColor);
        }
        if (firstColor == null) {
            unassign(gameId, second);
        } else {
            assign(gameId, second, firstColor);
        }
    }

    // --- session-time resolution ---

    /**
     * The colour a participant plays with: their standing assignment if they have
     * one, otherwise the least-populated colour among this game's participants.
     * Auto-picked colours are deliberately not persisted — only explicit picks are.
     */
    public TeamColor resolveForStart(String gameId, Participant participant) {
        TeamColor standing = standingTeam(gameId, participant.id());
        if (standing != null) {
            participant.setTeam(standing);
            return standing;
        }

        Map<TeamColor, Integer> counts = new EnumMap<>(TeamColor.class);
        for (TeamColor color : TeamColor.values()) {
            counts.put(color, 0);
        }
        if (participants != null) {
            for (Participant other : participants.all()) {
                if (other.team() != null) {
                    counts.merge(other.team(), 1, Integer::sum);
                }
            }
        }
        // Empty colours count as size 0, so players spread onto fresh teams instead of
        // piling onto whichever team happened to be created first.
        TeamColor chosen = TeamColor.values()[0];
        int fewest = Integer.MAX_VALUE;
        for (TeamColor color : TeamColor.values()) {
            int size = counts.get(color);
            if (size < fewest) {
                fewest = size;
                chosen = color;
            }
        }
        participant.setTeam(chosen);
        return chosen;
    }

    public List<Participant> members(TeamColor color) {
        if (participants == null) {
            return List.of();
        }
        List<Participant> result = new ArrayList<>();
        for (Participant participant : participants.all()) {
            if (participant.team() == color) {
                result.add(participant);
            }
        }
        return result;
    }

    /** Teams that currently have at least one member, in enum order. */
    public List<TeamColor> activeTeams() {
        List<TeamColor> result = new ArrayList<>();
        for (TeamColor color : TeamColor.values()) {
            if (!members(color).isEmpty()) {
                result.add(color);
            }
        }
        return result;
    }

    public Map<TeamColor, List<Participant>> byTeam() {
        Map<TeamColor, List<Participant>> result = new EnumMap<>(TeamColor.class);
        for (TeamColor color : activeTeams()) {
            result.put(color, members(color));
        }
        return result;
    }

    /** Standing assignments grouped by colour, for listing with no game running. */
    public Map<TeamColor, List<UUID>> standingByTeam(String gameId) {
        Map<TeamColor, List<UUID>> result = new EnumMap<>(TeamColor.class);
        for (Map.Entry<UUID, TeamColor> entry : standingTeams(gameId).entrySet()) {
            result.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }
        return result;
    }

    /** "Red" for multi-member teams, the player's name for solo teams (bingo convention). */
    public Component teamDisplayName(TeamColor color) {
        List<Participant> members = members(color);
        if (members.size() == 1) {
            return Component.text(members.getFirst().name(), color.textColor());
        }
        return color.displayNameComponent();
    }

    /** Cached display names so listings can show offline players sensibly. */
    public Map<UUID, String> nameLookup(Iterable<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        for (UUID id : ids) {
            String name = org.bukkit.Bukkit.getOfflinePlayer(id).getName();
            names.put(id, name != null ? name : id.toString().substring(0, 8));
        }
        return names;
    }
}
