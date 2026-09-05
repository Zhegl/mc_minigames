package me.petr1furious.potatominigames.games.bingo.model;

import java.util.Arrays;
import java.util.List;

public enum GameMode {
    DEFAULT("default"),
    BUTFAST("butfast"),
    COLLECTALL("collectall");

    private final String key;

    GameMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static GameMode fromKey(String key) {
        for (GameMode mode : values()) {
            if (mode.key.equalsIgnoreCase(key)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown game mode: " + key);
    }

    public static List<String> keys() {
        return Arrays.stream(values()).map(GameMode::key).toList();
    }
}
