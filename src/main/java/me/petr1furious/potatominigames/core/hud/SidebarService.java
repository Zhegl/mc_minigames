package me.petr1furious.potatominigames.core.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.team.TeamColor;

/**
 * Per-viewer sidebar with stable objectives and line diffing: each line is a
 * scoreboard team prefix on an invisible entry, and updates only touch lines
 * whose content changed — no flicker, no per-tick scoreboard rebuilds.
 */
public final class SidebarService {
    private static final int MAX_LINES = 15;

    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, TeamColor> nameColors = new HashMap<>();

    private static final class View {
        final Scoreboard scoreboard;
        final Objective objective;
        final List<Component> lines = new ArrayList<>();

        View(Component title) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            objective = scoreboard.registerNewObjective("sidebar", Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        static String entryFor(int index) {
            return "§" + Character.forDigit(index, 16) + "§r";
        }

        Team lineTeam(int index) {
            String name = "line_" + index;
            Team team = scoreboard.getTeam(name);
            if (team == null) {
                team = scoreboard.registerNewTeam(name);
                team.addEntry(entryFor(index));
            }
            return team;
        }

        void setLines(List<Component> newLines) {
            int count = Math.min(newLines.size(), MAX_LINES);
            for (int i = 0; i < count; i++) {
                Component line = newLines.get(i);
                if (i >= lines.size() || !line.equals(lines.get(i))) {
                    lineTeam(i).prefix(line);
                }
                objective.getScore(entryFor(i)).setScore(count - i);
            }
            for (int i = count; i < lines.size(); i++) {
                scoreboard.resetScores(entryFor(i));
            }
            lines.clear();
            lines.addAll(newLines.subList(0, count));
        }

        void setTitle(Component title) {
            objective.displayName(title);
        }
    }

    /** Shows (creating if needed) the sidebar for the viewer and sets its lines. */
    public void update(Player viewer, Component title, List<Component> lines) {
        View view = views.get(viewer.getUniqueId());
        if (view == null) {
            view = new View(title);
            views.put(viewer.getUniqueId(), view);
            applyNameColors(view.scoreboard);
            viewer.setScoreboard(view.scoreboard);
        } else {
            view.setTitle(title);
        }
        // A reconnecting player comes back on the main scoreboard while their view
        // lives on, so re-attach it or the sidebar stays hidden for the whole game.
        if (viewer.getScoreboard() != view.scoreboard) {
            viewer.setScoreboard(view.scoreboard);
        }
        view.setLines(lines);
    }

    public void clear(Player viewer) {
        if (views.remove(viewer.getUniqueId()) != null) {
            viewer.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void clearAll() {
        for (UUID id : List.copyOf(views.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        views.clear();
        nameColors.clear();
    }

    /** Colors player names (tab list and overhead) on every active sidebar's scoreboard. */
    public void setNameColor(Player player, TeamColor color) {
        if (color == null) {
            nameColors.remove(player.getUniqueId());
        } else {
            nameColors.put(player.getUniqueId(), color);
        }
        for (View view : views.values()) {
            applyNameColors(view.scoreboard);
        }
    }

    private void applyNameColors(Scoreboard scoreboard) {
        for (TeamColor color : TeamColor.values()) {
            Team team = scoreboard.getTeam("color_" + color.id());
            if (team != null) {
                for (String entry : List.copyOf(team.getEntries())) {
                    team.removeEntry(entry);
                }
            }
        }
        for (Map.Entry<UUID, TeamColor> entry : nameColors.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            TeamColor color = entry.getValue();
            Team team = scoreboard.getTeam("color_" + color.id());
            if (team == null) {
                team = scoreboard.registerNewTeam("color_" + color.id());
                NamedTextColor textColor = color.textColor();
                team.color(textColor);
            }
            team.addEntry(player.getName());
        }
    }
}
