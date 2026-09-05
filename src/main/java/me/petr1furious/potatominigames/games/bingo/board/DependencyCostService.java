package me.petr1furious.potatominigames.games.bingo.board;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;



import me.petr1furious.potatominigames.games.bingo.model.CostNode;
import me.petr1furious.potatominigames.games.bingo.model.CostNodeType;
import me.petr1furious.potatominigames.games.bingo.model.NodeRecipe;

public final class DependencyCostService {
    private final Map<String, CostNode> nodes = new LinkedHashMap<>();
    private final Map<String, Integer> completionDepthMemo = new HashMap<>();

    public DependencyCostService() {
    }

    public void setNodes(Map<String, CostNode> loadedNodes) {
        nodes.clear();
        nodes.putAll(loadedNodes);
        completionDepthMemo.clear();
    }

    public void refresh() {
        completionDepthMemo.clear();
    }

    public double scoreForNode(String nodeId) {
        return evaluateNode(nodeId).totalScore();
    }

    public Evaluation evaluateNode(String nodeId) {
        return evaluateDemand(Map.of(nodeId, 1));
    }

    public Evaluation evaluateNodes(Iterable<String> nodeIds) {
        Map<String, Integer> requestedNodes = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            requestedNodes.merge(nodeId, 1, Integer::sum);
        }
        return evaluateDemand(requestedNodes);
    }

    private Evaluation evaluateDemand(Map<String, Integer> requestedNodes) {
        Map<String, Integer> totalDemand = computeTotalDemand(requestedNodes);
        Map<String, Integer> mergedResources = new TreeMap<>();
        Map<OperationDepthKey, Integer> mergedOperations = new TreeMap<>();

        for (Map.Entry<String, Integer> entry : totalDemand.entrySet()) {
            CostNode node = requireNode(entry.getKey());
            int quantity = entry.getValue();
            if (node.type() == CostNodeType.OPERATION) {
                continue;
            }
            if (node.isAtomic()) {
                mergedResources.merge(node.id(), node.once() ? Math.min(1, quantity) : quantity, Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> entry : totalDemand.entrySet()) {
            CostNode node = requireNode(entry.getKey());
            if (node.isAtomic()) {
                continue;
            }
            NodeRecipe recipe = node.recipe();
            int batches = requiredBatches(node, entry.getValue(), recipe.outputAmount());
            int opDepth = recipeOperationDepth(node.id());
            for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
                CostNode ingredientNode = requireNode(ingredient.getKey());
                if (ingredientNode.type() != CostNodeType.OPERATION) {
                    continue;
                }
                mergedOperations.merge(new OperationDepthKey(ingredientNode.id(), opDepth),
                        ingredient.getValue() * batches, Integer::sum);
            }
        }

        double total = 0d;
        for (Map.Entry<String, Integer> entry : mergedResources.entrySet()) {
            total += atomicCost(requireNode(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<OperationDepthKey, Integer> entry : mergedOperations.entrySet()) {
            total += atomicCost(requireNode(entry.getKey().nodeId()), entry.getValue());
        }
        int maxDepth = mergedOperations.keySet().stream().mapToInt(OperationDepthKey::depth).max().orElse(0);
        return new Evaluation(total, Map.copyOf(mergedResources), Map.copyOf(mergedOperations), maxDepth);
    }

    private Map<String, Integer> computeTotalDemand(Map<String, Integer> requestedNodes) {
        Map<String, Integer> totalDemand = new TreeMap<>();
        Map<String, Integer> expandedBatches = new HashMap<>();
        for (Map.Entry<String, Integer> entry : requestedNodes.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                totalDemand.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }

        boolean changed;
        do {
            changed = false;
            Map<String, Integer> snapshot = new TreeMap<>(totalDemand);
            for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
                String nodeId = entry.getKey();
                CostNode node = requireNode(nodeId);
                if (node.isAtomic()) {
                    continue;
                }

                NodeRecipe recipe = node.recipe();
                int totalBatches = requiredBatches(node, entry.getValue(), recipe.outputAmount());
                int previousBatches = expandedBatches.getOrDefault(nodeId, 0);
                if (totalBatches <= previousBatches) {
                    continue;
                }

                int deltaBatches = totalBatches - previousBatches;
                expandedBatches.put(nodeId, totalBatches);
                changed = true;

                for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
                    int ingredientQuantity = ingredient.getValue() * deltaBatches;
                    totalDemand.merge(ingredient.getKey(), ingredientQuantity, Integer::sum);
                }
            }
        } while (changed);

        for (Map.Entry<String, Integer> entry : totalDemand.entrySet()) {
            requireNode(entry.getKey());
        }
        return totalDemand;
    }

    private int recipeOperationDepth(String nodeId) {
        Integer memoized = completionDepthMemo.get(nodeId);
        if (memoized != null) {
            return memoized;
        }
        int depth = recipeOperationDepth(nodeId, new HashSet<>());
        completionDepthMemo.put(nodeId, depth);
        return depth;
    }

    private int recipeOperationDepth(String nodeId, Set<String> visiting) {
        if (!visiting.add(nodeId)) {
            throw new IllegalStateException("Cycle detected while computing depth for node '" + nodeId + "'.");
        }
        CostNode node = requireNode(nodeId);
        if (node.isAtomic()) {
            visiting.remove(nodeId);
            return 0;
        }

        int childMaxDepth = 0;
        boolean hasOperation = false;
        for (Map.Entry<String, Integer> ingredient : node.recipe().ingredients().entrySet()) {
            CostNode ingredientNode = requireNode(ingredient.getKey());
            if (ingredientNode.type() == CostNodeType.OPERATION) {
                hasOperation = true;
                continue;
            }
            childMaxDepth = Math.max(childMaxDepth, recipeOperationDepth(ingredientNode.id(), visiting));
        }
        visiting.remove(nodeId);
        return hasOperation ? childMaxDepth + 1 : childMaxDepth;
    }

    private CostNode requireNode(String nodeId) {
        CostNode node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalStateException("Unknown node id: " + nodeId);
        }
        return node;
    }

    private static double atomicCost(CostNode node, int quantity) {
        if (!node.isAtomic()) {
            throw new IllegalStateException("Composite node '" + node.id() + "' cannot be scored directly.");
        }
        int normalized = Math.max(1, quantity);
        return node.base() + node.perBatch() * Math.pow(normalized, node.beta());
    }

    private static int ceilDiv(int x, int y) {
        return (x + y - 1) / y;
    }

    private static int requiredBatches(CostNode node, int quantity, int outputAmount) {
        int batches = ceilDiv(quantity, outputAmount);
        if (node.once()) {
            return Math.min(1, batches);
        }
        return batches;
    }

    public record Evaluation(double totalScore, Map<String, Integer> resourceCounts,
            Map<OperationDepthKey, Integer> operationCounts, int maxDepth) {
    }

    public record OperationDepthKey(String nodeId, int depth) implements Comparable<OperationDepthKey> {
        @Override
        public int compareTo(OperationDepthKey other) {
            int byDepth = Integer.compare(depth, other.depth);
            if (byDepth != 0) {
                return byDepth;
            }
            return nodeId.compareToIgnoreCase(other.nodeId);
        }
    }
}
