package me.petr1furious.potatominigames.core.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

public final class ParticipantRegistry {
    private final Map<UUID, Participant> participants = new LinkedHashMap<>();

    public Participant add(Player player) {
        return participants.computeIfAbsent(player.getUniqueId(), id -> new Participant(player));
    }

    public void remove(UUID id) {
        participants.remove(id);
    }

    public Optional<Participant> find(UUID id) {
        return Optional.ofNullable(participants.get(id));
    }

    public boolean isParticipant(Player player) {
        return participants.containsKey(player.getUniqueId());
    }

    public Collection<Participant> all() {
        return participants.values();
    }

    public List<Player> onlinePlayers() {
        List<Player> result = new ArrayList<>();
        for (Participant participant : participants.values()) {
            Player player = participant.player();
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    public int size() {
        return participants.size();
    }

    public boolean isEmpty() {
        return participants.isEmpty();
    }

    public void clear() {
        participants.clear();
    }
}
