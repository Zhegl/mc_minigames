package me.petr1furious.potatominigames.games.bingo.model;

public enum BoardClaimMode {
    AUTO("auto"),
    MANUAL("manual");

    private final String key;

    BoardClaimMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static BoardClaimMode fromKey(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Unknown board claim mode: null");
        }
        for (BoardClaimMode mode : values()) {
            if (mode.key.equalsIgnoreCase(raw)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown board claim mode: " + raw);
    }
}
