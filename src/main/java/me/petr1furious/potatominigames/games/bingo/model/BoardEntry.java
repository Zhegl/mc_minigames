package me.petr1furious.potatominigames.games.bingo.model;

import org.bukkit.Material;

public final class BoardEntry {
    private final String id;
    private final BoardMatch match;
    private final Material displayMaterial;
    private final String costNodeId;

    public BoardEntry(String id, BoardMatch match, Material displayMaterial, String costNodeId) {
        this.id = id;
        this.match = match;
        this.displayMaterial = displayMaterial;
        this.costNodeId = costNodeId;
    }

    public String id() {
        return id;
    }

    public BoardMatch match() {
        return match;
    }

    public Material displayMaterial() {
        return displayMaterial;
    }

    public String costNodeId() {
        return costNodeId;
    }

    public boolean matches(Material material) {
        return match.matches(material);
    }
}
