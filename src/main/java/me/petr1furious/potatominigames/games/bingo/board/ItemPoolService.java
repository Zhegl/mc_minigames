package me.petr1furious.potatominigames.games.bingo.board;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import me.petr1furious.potatominigames.games.bingo.model.BoardEntry;
import me.petr1furious.potatominigames.games.bingo.model.BoardMatch;
import me.petr1furious.potatominigames.games.bingo.model.CostNode;
import me.petr1furious.potatominigames.games.bingo.model.CostNodeType;
import me.petr1furious.potatominigames.games.bingo.model.NodeRecipe;

public final class ItemPoolService {
    private final JavaPlugin plugin;
    private final Map<String, CostNode> nodes = new LinkedHashMap<>();
    private final List<BoardEntry> entries = new ArrayList<>();
    private final List<CanonicalGroup> canonicalGroups = new ArrayList<>();
    private final List<Material> boardBlacklistMaterials = new ArrayList<>();
    private final List<String> boardBlacklistPrefixes = new ArrayList<>();

    public ItemPoolService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File boardEntriesFile = new File(plugin.getDataFolder(), "games/bingo-board-entries.yml");
        if (!boardEntriesFile.exists()) {
            plugin.saveResource("games/bingo-board-entries.yml", false);
        }

        nodes.clear();
        entries.clear();
        canonicalGroups.clear();
        boardBlacklistMaterials.clear();
        boardBlacklistPrefixes.clear();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(boardEntriesFile);
        boardBlacklistMaterials.addAll(parseMaterialList(yaml.getList("board-blacklist-materials")));
        boardBlacklistPrefixes.addAll(normalizePrefixes(asStringList(yaml.getList("board-blacklist-prefixes"))));
        List<?> rawCanonicalGroups = yaml.getMapList("canonical-groups");
        for (int i = 0; i < rawCanonicalGroups.size(); i++) {
            if (!(rawCanonicalGroups.get(i) instanceof Map<?, ?> map)) {
                throw new IllegalStateException("Invalid canonical group at index " + i + ": expected map.");
            }
            canonicalGroups.add(parseCanonicalGroup(map));
        }
        List<?> rawNodes = yaml.getMapList("nodes");
        if (rawNodes.isEmpty()) {
            throw new IllegalStateException("games/bingo-board-entries.yml must define a non-empty nodes list.");
        }

        for (int i = 0; i < rawNodes.size(); i++) {
            if (!(rawNodes.get(i) instanceof Map<?, ?> map)) {
                throw new IllegalStateException("Invalid node at index " + i + ": expected map.");
            }
            CostNode node = parseNode(map);
            if (nodes.putIfAbsent(node.id(), node) != null) {
                throw new IllegalStateException("Duplicate node id: " + node.id());
            }
        }

        resolveVanillaRecipes();
        inferImplicitNodes();
        rebuildBoardEntries();
    }

    public List<BoardEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public Map<String, CostNode> nodes() {
        return Collections.unmodifiableMap(nodes);
    }

    private CostNode parseNode(Map<?, ?> map) {
        String id = requireString(map, "id");
        CostNodeType type = parseType(asString(map.get("type")));
        boolean once = asBoolean(map.get("once"), false);
        BoardMatch boardMatch = parseBoardMatch(map);
        Material displayMaterial = parseDisplayMaterial(map, boardMatch);
        NodeRecipe recipe = parseExplicitRecipe(map);
        boolean inferVanilla = asBoolean(map.get("recipe-source"), false)
                || "vanilla".equalsIgnoreCase(asString(map.get("recipe-source")));
        boolean boardTargetEnabled = asBoolean(map.get("board"), true);

        Double base = asDouble(map.get("base"));
        Double perBatch = asDouble(map.get("per-batch"));
        Double beta = asDouble(map.get("beta"));

        if (type == CostNodeType.OPERATION) {
            if (recipe != null || inferVanilla) {
                throw new IllegalStateException("Operation node '" + id + "' cannot have a recipe.");
            }
            if (boardMatch != null) {
                throw new IllegalStateException("Operation node '" + id + "' cannot map to board items.");
            }
            requireAtomicCurve(id, base, perBatch, beta);
        } else {
            boolean composite = recipe != null || inferVanilla;
            if (composite) {
                if (base != null || perBatch != null || beta != null) {
                    throw new IllegalStateException("Composite node '" + id + "' cannot define base/per-batch/beta.");
                }
            } else {
                requireAtomicCurve(id, base, perBatch, beta);
            }
        }

        return new CostNode(id, type, once, base, perBatch, beta, recipe, boardMatch, displayMaterial, inferVanilla,
                boardTargetEnabled);
    }

    private CanonicalGroup parseCanonicalGroup(Map<?, ?> map) {
        String id = requireString(map, "id");
        BoardMatch boardMatch = parseBoardMatch(map);
        if (boardMatch == null) {
            throw new IllegalStateException("Canonical group '" + id + "' must define tag or materials.");
        }
        if (boardMatch.acceptedMaterials().size() < 2) {
            throw new IllegalStateException("Canonical group '" + id + "' must match at least two materials.");
        }
        Material displayMaterial = parseDisplayMaterial(map, boardMatch);
        if (displayMaterial == null) {
            throw new IllegalStateException("Canonical group '" + id + "' needs a display material.");
        }
        Material recipeSourceMaterial = parseMaterial(asString(map.get("recipe-source-material")));
        if (recipeSourceMaterial == null) {
            recipeSourceMaterial = displayMaterial;
        }
        return new CanonicalGroup(id, boardMatch, displayMaterial, recipeSourceMaterial);
    }

    private void rebuildBoardEntries() {
        for (CostNode node : nodes.values()) {
            if (!node.isBoardTarget()) {
                continue;
            }
            if (isBlacklistedBoardTarget(node)) {
                continue;
            }
            if (isShadowedByTagNode(node)) {
                continue;
            }
            Material displayMaterial = node.displayMaterial();
            if (displayMaterial == null) {
                List<Material> accepted = new ArrayList<>(node.boardMatch().acceptedMaterials());
                accepted.sort(Comparator.comparing(Enum::name));
                if (accepted.isEmpty()) {
                    throw new IllegalStateException("Board target node '" + node.id() + "' matches no materials.");
                }
                displayMaterial = accepted.getFirst();
            }
            entries.add(new BoardEntry(node.id(), node.boardMatch(), displayMaterial, node.id()));
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("No board items were exposed from games/bingo-board-entries.yml nodes.");
        }
    }

    private boolean isShadowedByTagNode(CostNode node) {
        if (node.boardMatch() == null) {
            return false;
        }
        List<Material> accepted = node.boardMatch().acceptedMaterials();
        if (accepted.size() != 1) {
            return false;
        }
        Material only = accepted.getFirst();
        for (CostNode other : nodes.values()) {
            if (other == node || !other.isBoardTarget() || other.boardMatch() == null) {
                continue;
            }
            List<Material> otherAccepted = other.boardMatch().acceptedMaterials();
            if (otherAccepted.size() <= 1) {
                continue;
            }
            if (otherAccepted.contains(only)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlacklistedBoardTarget(CostNode node) {
        if (node.boardMatch() == null) {
            return false;
        }
        List<Material> accepted = node.boardMatch().acceptedMaterials();
        if (accepted.isEmpty()) {
            return false;
        }
        for (Material material : accepted) {
            if (!isBlacklistedBoardMaterial(material)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlacklistedBoardMaterial(Material material) {
        if (boardBlacklistMaterials.contains(material)) {
            return true;
        }
        String name = material.name().toLowerCase(Locale.ROOT);
        for (String prefix : boardBlacklistPrefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void resolveVanillaRecipes() {
        List<Map.Entry<String, CostNode>> toRewrite = new ArrayList<>();
        for (Map.Entry<String, CostNode> entry : nodes.entrySet()) {
            if (entry.getValue().inferRecipeFromVanilla()) {
                toRewrite.add(entry);
            }
        }
        for (Map.Entry<String, CostNode> entry : toRewrite) {
            CostNode current = entry.getValue();
            Material output = inferOutputMaterial(current);
            NodeRecipe recipe = inferVanillaRecipe(current.id(), output);
            nodes.put(entry.getKey(), new CostNode(current.id(), current.type(), current.once(), current.base(),
                    current.perBatch(), current.beta(), recipe, current.boardMatch(), current.displayMaterial(),
                    false, current.boardTargetEnabled()));
        }
    }

    private void inferImplicitNodes() {
        boolean changed;
        do {
            changed = false;
            changed = inferCanonicalTagGroups() || changed;
            changed = inferReachableVanillaNodes() || changed;
        } while (changed);
    }

    private boolean inferReachableVanillaNodes() {
        boolean changed = false;
        for (Material material : Material.values()) {
            if (!material.isItem() || material == Material.AIR) {
                continue;
            }
            if (resolveMaterialToNode(material) != null) {
                continue;
            }
            String nodeId = material.name().toLowerCase(Locale.ROOT);
            NodeRecipe recipe = tryInferVanillaRecipe(nodeId, material);
            if (recipe == null) {
                continue;
            }
            nodes.put(nodeId, new CostNode(nodeId, CostNodeType.RESOURCE, false, null, null, null, recipe,
                    BoardMatch.forMaterial(material), material, false, true));
            changed = true;
        }
        return changed;
    }

    private boolean inferCanonicalTagGroups() {
        boolean changed = false;
        for (CanonicalGroup group : canonicalGroups) {
            if (nodes.containsKey(group.id())) {
                continue;
            }
            List<Material> taggedMaterials = new ArrayList<>(group.boardMatch().acceptedMaterials());
            if (taggedMaterials.size() < 2) {
                continue;
            }
            NodeRecipe canonicalRecipe = tryInferVanillaRecipe(group.id(), group.recipeSourceMaterial());
            if (canonicalRecipe == null) {
                continue;
            }
            nodes.put(group.id(), new CostNode(group.id(), CostNodeType.RESOURCE, false, null, null, null,
                    canonicalRecipe, group.boardMatch(), group.displayMaterial(), false, true));
            changed = true;
        }
        return changed;
    }

    private NodeRecipe tryInferVanillaRecipe(String nodeId, Material output) {
        try {
            return inferVanillaRecipe(nodeId, output);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private Material inferOutputMaterial(CostNode node) {
        if (node.boardMatch() == null) {
            throw new IllegalStateException(
                    "Node '" + node.id() + "' needs a direct material mapping for vanilla recipe inference.");
        }
        List<Material> accepted = node.boardMatch().acceptedMaterials();
        if (accepted.size() != 1) {
            throw new IllegalStateException(
                    "Node '" + node.id() + "' must map to exactly one material to infer a vanilla recipe.");
        }
        return accepted.getFirst();
    }

    private NodeRecipe inferVanillaRecipe(String nodeId, Material output) {
        IllegalStateException lastFailure = null;
        for (Recipe recipe : preferredRecipes(output)) {
            try {
                return inferVanillaRecipe(nodeId, output, recipe);
            } catch (IllegalStateException ex) {
                lastFailure = ex;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException(
                "Could not infer vanilla recipe for node '" + nodeId + "' (" + output + ").");
    }

    private NodeRecipe inferVanillaRecipe(String nodeId, Material output, Recipe recipe) {
        LinkedHashMap<String, Integer> ingredients = new LinkedHashMap<>();
        int outputAmount = Math.max(1, recipe.getResult().getAmount());
        String operationNodeId = switch (recipe) {
            case ShapedRecipe shaped -> {
                for (RecipeChoice choice : shaped.getChoiceMap().values()) {
                    addChoiceIngredients(nodeId, ingredients, choice, countChar(shaped, choice));
                }
                yield "craft";
            }
            case ShapelessRecipe shapeless -> {
                for (RecipeChoice choice : shapeless.getChoiceList()) {
                    addChoiceIngredients(nodeId, ingredients, choice, 1);
                }
                yield "craft";
            }
            case FurnaceRecipe furnace -> {
                addChoiceIngredients(nodeId, ingredients, furnace.getInputChoice(), 1);
                yield "smelt";
            }
            case CookingRecipe<?> cooking -> {
                addChoiceIngredients(nodeId, ingredients, cooking.getInputChoice(), 1);
                yield "smelt";
            }
            case StonecuttingRecipe stonecutting -> {
                addChoiceIngredients(nodeId, ingredients, stonecutting.getInputChoice(), 1);
                yield "cut";
            }
            case SmithingTransformRecipe smithing -> {
                addChoiceIngredients(nodeId, ingredients, smithing.getTemplate(), 1);
                addChoiceIngredients(nodeId, ingredients, smithing.getBase(), 1);
                addChoiceIngredients(nodeId, ingredients, smithing.getAddition(), 1);
                yield "smith";
            }
            default -> throw new IllegalStateException("Unsupported vanilla recipe type for node '" + nodeId + "': "
                    + recipe.getClass().getName());
        };
        if (!ingredients.isEmpty()) {
            requireOperationNode(nodeId, operationNodeId, output);
            ingredients.merge(operationNodeId, 1, Integer::sum);
        }
        return new NodeRecipe(outputAmount, ingredients);
    }

    private void requireOperationNode(String nodeId, String operationNodeId, Material output) {
        CostNode operationNode = nodes.get(operationNodeId);
        if (operationNode == null) {
            throw new IllegalStateException("Vanilla recipe for node '" + nodeId + "' (" + output.name().toLowerCase(
                    Locale.ROOT) + ") requires missing operation node '" + operationNodeId + "'.");
        }
        if (operationNode.type() != CostNodeType.OPERATION) {
            throw new IllegalStateException(
                    "Node '" + operationNodeId + "' must be an operation to support vanilla recipe inference.");
        }
    }

    private static int countChar(ShapedRecipe shaped, RecipeChoice choice) {
        for (Map.Entry<Character, RecipeChoice> entry : shaped.getChoiceMap().entrySet()) {
            if (!Objects.equals(entry.getValue(), choice)) {
                continue;
            }
            int count = 0;
            for (String row : shaped.getShape()) {
                for (char c : row.toCharArray()) {
                    if (c == entry.getKey()) {
                        count++;
                    }
                }
            }
            return count;
        }
        return 0;
    }

    private void addChoiceIngredients(String nodeId, Map<String, Integer> ingredients, RecipeChoice choice,
            int amount) {
        if (choice == null || amount <= 0) {
            return;
        }
        List<Material> options = choiceOptions(nodeId, choice);

        String resolvedNodeId = null;
        List<String> missingOptions = new ArrayList<>();
        for (Material option : options) {
            String candidate = resolveMaterialToNode(option);
            if (candidate == null) {
                missingOptions.add(option.name().toLowerCase());
                continue;
            }
            if (resolvedNodeId == null) {
                resolvedNodeId = candidate;
            } else if (!resolvedNodeId.equals(candidate)) {
                throw new IllegalStateException("Vanilla recipe for node '" + nodeId
                        + "' has alternatives that map to different nodes. Add an explicit recipe instead.");
            }
        }
        if (resolvedNodeId == null) {
            throw new IllegalStateException("Vanilla recipe for node '" + nodeId
                    + "' has no mapped ingredient option. Missing: " + String.join(", ", missingOptions));
        }
        ingredients.merge(resolvedNodeId, amount, Integer::sum);
    }

    private static List<Material> choiceOptions(String nodeId, RecipeChoice choice) {
        return switch (choice) {
            case RecipeChoice.MaterialChoice materialChoice -> materialChoice.getChoices();
            case RecipeChoice.ExactChoice exactChoice ->
                exactChoice.getChoices().stream().map(stack -> stack.getType()).toList();
            default -> throw new IllegalStateException(
                    "Unsupported recipe choice for node '" + nodeId + "': " + choice.getClass().getName());
        };
    }

    private String resolveMaterialToNode(Material material) {
        List<CostNode> candidates = new ArrayList<>();
        for (CostNode node : nodes.values()) {
            if (node.type() != CostNodeType.RESOURCE || node.boardMatch() == null) {
                continue;
            }
            if (node.boardMatch().matches(material)) {
                candidates.add(node);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator
                .comparingInt((CostNode node) -> node.boardMatch().acceptedMaterials().size())
                .thenComparing(CostNode::id));
        return candidates.getFirst().id();
    }

    private static List<Recipe> preferredRecipes(Material output) {
        List<RankedRecipe> matches = new ArrayList<>();
        java.util.Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe.getResult().getType() != output) {
                continue;
            }
            matches.add(new RankedRecipe(recipePriority(recipe), recipe));
        }
        matches.sort(Comparator.comparingInt(RankedRecipe::priority));
        return matches.stream().map(RankedRecipe::recipe).toList();
    }

    private static int recipePriority(Recipe recipe) {
        return switch (recipe) {
            case ShapedRecipe ignored -> 0;
            case ShapelessRecipe ignored -> 1;
            case FurnaceRecipe ignored -> 2;
            case CookingRecipe<?> ignored -> 3;
            case StonecuttingRecipe ignored -> 4;
            case SmithingTransformRecipe ignored -> 5;
            default -> 100;
        };
    }

    private static void requireAtomicCurve(String nodeId, Double base, Double perBatch, Double beta) {
        if (base == null || perBatch == null || beta == null) {
            throw new IllegalStateException(
                    "Atomic node '" + nodeId + "' must define base, per-batch, and beta.");
        }
        if (beta < 0d) {
            throw new IllegalStateException("Node '" + nodeId + "' has invalid beta: " + beta);
        }
    }

    private static CostNodeType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return CostNodeType.RESOURCE;
        }
        return CostNodeType.valueOf(raw.trim().toUpperCase());
    }

    private static BoardMatch parseBoardMatch(Map<?, ?> map) {
        String materialRaw = asString(map.get("material"));
        if (materialRaw != null && !materialRaw.isBlank()) {
            Material material = parseMaterial(materialRaw);
            if (material == null) {
                throw new IllegalStateException("Unknown material mapping: " + materialRaw);
            }
            return BoardMatch.forMaterial(material);
        }

        String tagRaw = asString(map.get("tag"));
        if (tagRaw != null && !tagRaw.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(tagRaw);
            if (key == null) {
                throw new IllegalStateException("Invalid tag mapping: " + tagRaw);
            }
            return BoardMatch.forTag(key);
        }

        List<String> rawMaterials = asStringList(map.get("materials"));
        if (rawMaterials.isEmpty()) {
            return null;
        }
        List<Material> materials = new ArrayList<>();
        for (String raw : rawMaterials) {
            Material material = parseMaterial(raw);
            if (material == null) {
                throw new IllegalStateException("Unknown material in materials list: " + raw);
            }
            materials.add(material);
        }
        return BoardMatch.forMaterials(materials);
    }

    private static Material parseDisplayMaterial(Map<?, ?> map, BoardMatch boardMatch) {
        String raw = asString(map.get("display-material"));
        if (raw == null || raw.isBlank()) {
            if (boardMatch == null) {
                return null;
            }
            List<Material> accepted = new ArrayList<>(boardMatch.acceptedMaterials());
            accepted.sort(Comparator.comparing(Enum::name));
            return accepted.isEmpty() ? null : accepted.getFirst();
        }
        Material material = parseMaterial(raw);
        if (material == null) {
            throw new IllegalStateException("Unknown display-material: " + raw);
        }
        return material;
    }

    private static NodeRecipe parseExplicitRecipe(Map<?, ?> map) {
        if (!(map.get("recipe") instanceof Map<?, ?> recipeMap)) {
            return null;
        }
        int outputAmount = asInt(recipeMap.get("output-amount"), 1);
        Object ingredientsRaw = recipeMap.get("ingredients");
        if (!(ingredientsRaw instanceof Map<?, ?> ingredientsMap) || ingredientsMap.isEmpty()) {
            throw new IllegalStateException("Recipe must define a non-empty ingredients map.");
        }
        LinkedHashMap<String, Integer> ingredients = new LinkedHashMap<>();
        for (Map.Entry<?, ?> ingredient : ingredientsMap.entrySet()) {
            String nodeId = asString(ingredient.getKey());
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalStateException("Recipe ingredient key cannot be blank.");
            }
            ingredients.put(nodeId, asInt(ingredient.getValue(), 1));
        }
        return new NodeRecipe(outputAmount, ingredients);
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Material.matchMaterial(raw.toLowerCase().replace("minecraft:", ""));
    }

    private static List<Material> parseMaterialList(Object value) {
        List<String> rawMaterials = asStringList(value);
        if (rawMaterials.isEmpty()) {
            return List.of();
        }
        List<Material> materials = new ArrayList<>();
        for (String raw : rawMaterials) {
            Material material = parseMaterial(raw);
            if (material == null) {
                throw new IllegalStateException("Unknown material in blocked-propagation-materials: " + raw);
            }
            materials.add(material);
        }
        return materials;
    }

    private static List<String> normalizePrefixes(List<String> rawPrefixes) {
        if (rawPrefixes.isEmpty()) {
            return List.of();
        }
        List<String> prefixes = new ArrayList<>();
        for (String raw : rawPrefixes) {
            String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (!normalized.isEmpty()) {
                prefixes.add(normalized);
            }
        }
        return prefixes;
    }

    private static String requireString(Map<?, ?> map, String key) {
        String value = asString(map.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required string: " + key);
        }
        return value;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String raw = String.valueOf(value);
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        return defaultValue;
    }

    private static Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid numeric value: " + value, ex);
        }
    }

    private static int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid integer value: " + value, ex);
        }
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry != null) {
                result.add(String.valueOf(entry));
            }
        }
        return result;
    }

    private record CanonicalGroup(String id, BoardMatch boardMatch, Material displayMaterial,
            Material recipeSourceMaterial) {
    }

    private record RankedRecipe(int priority, Recipe recipe) {
    }
}
