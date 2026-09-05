package me.petr1furious.potatominigames.games.bingo.model;

import java.util.Collections;
import me.petr1furious.potatominigames.core.team.TeamColor;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TeamState {
    private final TeamColor color;
    private final Set<String> members = new LinkedHashSet<>();
    private final boolean[] collected = new boolean[25];
    private int score = 0;
    private boolean finished = false;
    private int finishedAtSeconds = -1;

    public TeamState(TeamColor color) {
        this.color = color;
    }

    public TeamColor color() {
        return color;
    }

    public Set<String> members() {
        return Collections.unmodifiableSet(members);
    }

    public void addMember(String nickname) {
        members.add(nickname);
    }

    public void removeMember(String nickname) {
        members.remove(nickname);
    }

    public int memberCount() {
        return members.size();
    }

    public boolean[] collected() {
        return collected;
    }

    public int score() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void addScore(int delta) {
        this.score += delta;
    }

    public boolean finished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public int finishedAtSeconds() {
        return finishedAtSeconds;
    }

    public void setFinishedAtSeconds(int finishedAtSeconds) {
        this.finishedAtSeconds = finishedAtSeconds;
    }

    public void resetRound() {
        for (int i = 0; i < collected.length; i++) {
            collected[i] = false;
        }
        score = 0;
        finished = false;
        finishedAtSeconds = -1;
    }
}
