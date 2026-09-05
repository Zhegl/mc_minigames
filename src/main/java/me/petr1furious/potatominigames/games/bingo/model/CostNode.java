package me.petr1furious.potatominigames.games.bingo.model;

import org.bukkit.Material;

public final class CostNode {
    private final String id;
    private final CostNodeType type;
    private final boolean once;
    private final Double base;
    private final Double perBatch;
    private final Double beta;
    private final NodeRecipe recipe;
    private final BoardMatch boardMatch;
    private final Material displayMaterial;
    private final boolean inferRecipeFromVanilla;
    private final boolean boardTargetEnabled;

    public CostNode(String id, CostNodeType type, boolean once, Double base, Double perBatch, Double beta,
            NodeRecipe recipe, BoardMatch boardMatch, Material displayMaterial, boolean inferRecipeFromVanilla,
            boolean boardTargetEnabled) {
        this.id = id;
        this.type = type;
        this.once = once;
        this.base = base;
        this.perBatch = perBatch;
        this.beta = beta;
        this.recipe = recipe;
        this.boardMatch = boardMatch;
        this.displayMaterial = displayMaterial;
        this.inferRecipeFromVanilla = inferRecipeFromVanilla;
        this.boardTargetEnabled = boardTargetEnabled;
    }

    public String id() {
        return id;
    }

    public CostNodeType type() {
        return type;
    }

    public boolean once() {
        return once;
    }

    public Double base() {
        return base;
    }

    public Double perBatch() {
        return perBatch;
    }

    public Double beta() {
        return beta;
    }

    public NodeRecipe recipe() {
        return recipe;
    }

    public BoardMatch boardMatch() {
        return boardMatch;
    }

    public Material displayMaterial() {
        return displayMaterial;
    }

    public boolean inferRecipeFromVanilla() {
        return inferRecipeFromVanilla;
    }

    public boolean boardTargetEnabled() {
        return boardTargetEnabled;
    }

    public boolean isAtomic() {
        return recipe == null;
    }

    public boolean isBoardTarget() {
        return type == CostNodeType.RESOURCE && boardMatch != null && boardTargetEnabled;
    }

    public boolean isScoredAtomic() {
        return isAtomic();
    }
}
