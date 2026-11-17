package me.petr1furious.potatominigames.core.hud;

import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Per-viewer sidebar; each line is a team prefix on an invisible entry and only changed
 * lines are updated. Spectators mirror their target's sidebar, or the first one shown.
 */
public final class SidebarService {
    private static final int MAX_LINES = 15;

    private final Map<UUID, View> views = new LinkedHashMap<>();
    private final Map<UUID, TeamColor> nameColors = new HashMap<>();
    private java.util.Set<String> hiddenNonCollidingPlayers = java.util.Set.of();
    private final Supplier<Collection<Player>> spectators;
    private boolean nameTagsVisible = true;

    public SidebarService(Supplier<Collection<Player>> spectators) {
        this.spectators = spectators;
    }

    private static final class View {
        final Scoreboard scoreboard;
        final Objective objective;
        final List<Component> lines = new ArrayList<>();
        Component title;
        String language;

        View(Component title, Locale locale) {
            this.title = title;
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            objective = scoreboard.registerNewObjective("sidebar", Criteria.DUMMY, I18n.render(title, locale));
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

        void setLines(List<Component> newLines, Locale locale) {
            boolean relocalize = !I18n.language(locale).equals(language);
            int count = Math.min(newLines.size(), MAX_LINES);
            // A scoped player keeps their private scoreboard and teams while the panel is hidden.
            DisplaySlot slot = count == 0 ? null : DisplaySlot.SIDEBAR;
            if (objective.getDisplaySlot() != slot) objective.setDisplaySlot(slot);
            for (int i = 0; i < count; i++) {
                Component line = newLines.get(i);
                if (relocalize || i >= lines.size() || !line.equals(lines.get(i))) {
                    lineTeam(i).prefix(I18n.render(line, locale));
                }
                objective.getScore(entryFor(i)).setScore(count - i);
            }
            for (int i = count; i < lines.size(); i++) {
                scoreboard.resetScores(entryFor(i));
            }
            lines.clear();
            lines.addAll(newLines.subList(0, count));
            language = I18n.language(locale);
        }

        void setTitle(Component title, Locale locale) {
            if (!this.title.equals(title) || !I18n.language(locale).equals(language)) {
                objective.displayName(I18n.render(title, locale));
            }
            this.title = title;
        }
    }

    public void update(Player viewer, Component title, List<Component> lines) {
        render(viewer, title, lines);
        for (Player spectator : spectators.get()) {
            if (mirrorTarget(spectator) == viewer) {
                render(spectator, title, lines);
            }
        }
    }

    private Player mirrorTarget(Player spectator) {
        if (spectator.getSpectatorTarget() instanceof Player target && views.containsKey(target.getUniqueId())) {
            return target;
        }
        Collection<Player> watching = spectators.get();
        for (UUID id : views.keySet()) {
            Player candidate = Bukkit.getPlayer(id);
            if (candidate != null && !watching.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void render(Player viewer, Component title, List<Component> lines) {
        Locale locale = viewer.locale();
        View view = views.get(viewer.getUniqueId());
        if (view == null) {
            view = new View(title, locale);
            views.put(viewer.getUniqueId(), view);
            applyNameColors(view.scoreboard);
            viewer.setScoreboard(view.scoreboard);
        } else {
            view.setTitle(title, locale);
        }
        // Reconnecting players return on the main scoreboard; re-attach the view.
        if (viewer.getScoreboard() != view.scoreboard) {
            viewer.setScoreboard(view.scoreboard);
        }
        view.setLines(lines, locale);
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
        nameTagsVisible = true;
        hiddenNonCollidingPlayers = java.util.Set.of();
    }

    /** Controls vanilla overhead names on this session's private scoreboards only. */
    public void setNameTagsVisible(boolean visible) {
        nameTagsVisible = visible;
        for (View view : views.values()) {
            for (TeamColor color : TeamColor.values()) {
                Team team = view.scoreboard.getTeam("color_" + color.id());
                if (team != null) applyNameTagVisibility(team);
            }
        }
    }

    private void applyNameTagVisibility(Team team) {
        team.setOption(Team.Option.NAME_TAG_VISIBILITY,
                nameTagsVisible ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER);
    }

    /** Round-scoped rules, also applied to boards created later for new spectators. */
    public void setHiddenNonCollidingPlayers(Collection<String> names) {
        hiddenNonCollidingPlayers = java.util.Set.copyOf(names);
        for (View view : views.values()) applyNameColors(view.scoreboard);
    }

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
        Team hidden = scoreboard.getTeam("hidden_no_push");
        if (hidden != null) hidden.unregister();
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
            applyNameTagVisibility(team);
            team.addEntry(player.getName());
        }
        if (!hiddenNonCollidingPlayers.isEmpty()) {
            hidden = scoreboard.registerNewTeam("hidden_no_push");
            hidden.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            hidden.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            hidden.setCanSeeFriendlyInvisibles(false);
            for (String name : hiddenNonCollidingPlayers) hidden.addEntry(name);
        }
    }
}
