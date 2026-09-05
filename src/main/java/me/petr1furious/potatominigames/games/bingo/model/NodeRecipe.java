package me.petr1furious.potatominigames.games.bingo.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record NodeRecipe(int outputAmount, Map<String, Integer> ingredients) {
    public NodeRecipe {
        if (outputAmount <= 0) {
            throw new IllegalArgumentException("Recipe outputAmount must be positive.");
        }
        ingredients = Map.copyOf(new LinkedHashMap<>(ingredients));
    }
}
