package me.petr1furious.potatominigames.games.party;

import java.util.HashSet;
import java.util.Set;
import me.petr1furious.potatominigames.core.hud.PrivatePlayerGlow;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

/** The round HUD gives each player a private scoreboard before roles are assigned. */
final class FurryQuestHunterMarks {
    private final Player hunter;
    private final PrivatePlayerGlow glow;
    private final Team highlight;
    private final Team lowlight;
    private final Set<Animals> marked = new HashSet<>();

    FurryQuestHunterMarks(Player hunter, PrivatePlayerGlow glow) {
        this.hunter = hunter;
        this.glow = glow;
        var board = hunter.getScoreboard();
        highlight = board.registerNewTeam("fq_highlight");
        highlight.color(NamedTextColor.GOLD);
        lowlight = board.registerNewTeam("fq_lowlight");
        lowlight.color(NamedTextColor.DARK_GRAY);
    }

    boolean toggle(Animals animal, boolean dim) {
        Team team = dim ? lowlight : highlight;
        if (team.hasEntity(animal)) {
            remove(animal);
            return false;
        }
        team.addEntity(animal); // Scoreboard entries can belong to only one team.
        marked.add(animal);
        refresh();
        return true;
    }

    void refresh() {
        glow.updateTargets(hunter, marked);
    }

    void remove(Animals animal) {
        if (marked.remove(animal)) {
            highlight.removeEntity(animal);
            lowlight.removeEntity(animal);
            refresh();
        }
    }

    void clear() {
        marked.clear();
        refresh();
        highlight.unregister();
        lowlight.unregister();
    }
}
