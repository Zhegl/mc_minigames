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

/** Saved per-game colours ({@link TeamRegistry}) and the current session's teams. */
public final class TeamService {
    private final TeamRegistry registry;
    private ParticipantRegistry participants;
    /** Queue picks; they override everything. */
    private Map<UUID, TeamColor> queuePicks = Map.of();
    /** Saved colours apply only to admin-started games. */
    private boolean useStanding = true;

    public TeamService(TeamRegistry registry) {
        this.registry = registry;
    }

    public void reload() {
        registry.reload();
    }

    public void attach(ParticipantRegistry participants) {
        this.participants = participants;
    }

    public void detach() {
        this.participants = null;
    }

    public void configureStart(Map<UUID, TeamColor> picks, boolean standingColours) {
        this.queuePicks = Map.copyOf(picks);
        this.useStanding = standingColours;
    }

    public TeamColor standingTeam(String gameId, UUID playerId) {
        return registry.get(gameId, playerId);
    }

    public Map<UUID, TeamColor> standingTeams(String gameId) {
        return registry.all(gameId);
    }

    /** Persists, and applies to a live participant if any. */
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

    /** Persisted like an explicit pick. */
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

    /**
     * Queue pick, else saved colour (admin-started games only), else the least-populated
     * colour. Auto-picked colours are not persisted.
     */
    public TeamColor resolveForStart(String gameId, Participant participant) {
        TeamColor picked = queuePicks.get(participant.id());
        if (picked != null) {
            participant.setTeam(picked);
            return picked;
        }
        TeamColor standing = useStanding ? standingTeam(gameId, participant.id()) : null;
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
        // Empty colours count as 0 so players spread onto fresh teams.
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

    /** Non-empty teams, in enum order. */
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

    public Map<TeamColor, List<UUID>> standingByTeam(String gameId) {
        Map<TeamColor, List<UUID>> result = new EnumMap<>(TeamColor.class);
        for (Map.Entry<UUID, TeamColor> entry : standingTeams(gameId).entrySet()) {
            result.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }
        return result;
    }

    /** Colour name, or the player's name for a solo team. */
    public Component teamDisplayName(TeamColor color) {
        List<Participant> members = members(color);
        if (members.size() == 1) {
            return Component.text(members.getFirst().name(), color.textColor());
        }
        return color.displayNameComponent();
    }

    /** Cached names for offline players. */
    public Map<UUID, String> nameLookup(Iterable<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        for (UUID id : ids) {
            String name = org.bukkit.Bukkit.getOfflinePlayer(id).getName();
            names.put(id, name != null ? name : id.toString().substring(0, 8));
        }
        return names;
    }
}
