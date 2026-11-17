package me.petr1furious.potatominigames.games.party;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LessIsBetterVisibility {
    private record View(Player viewer, Player target) {}

    private final Plugin plugin;
    private final Map<Player, Boolean> originalCollision = new LinkedHashMap<>();
    private final List<View> hidden = new ArrayList<>();

    LessIsBetterVisibility(Plugin plugin) {
        this.plugin = plugin;
    }

    void hide(Player player) {
        if (originalCollision.containsKey(player)) return;
        for (Player other : originalCollision.keySet()) {
            hideFrom(player, other);
            hideFrom(other, player);
        }
        originalCollision.put(player, player.isCollidable());
        player.setCollidable(false);
    }

    private void hideFrom(Player viewer, Player target) {
        // Hold our own visibility reason even if another plugin has already hidden the target.
        hidden.add(new View(viewer, target));
        viewer.hidePlayer(plugin, target);
    }

    void remove(Player player) {
        Boolean collidable = originalCollision.remove(player);
        if (collidable == null) return;
        hidden.removeIf(view -> {
            if (!view.viewer().equals(player) && !view.target().equals(player)) return false;
            view.viewer().showPlayer(plugin, view.target());
            return true;
        });
        player.setCollidable(collidable);
    }

    void clear() {
        for (View view : hidden) view.viewer().showPlayer(plugin, view.target());
        hidden.clear();
        originalCollision.forEach(Player::setCollidable);
        originalCollision.clear();
    }
}
