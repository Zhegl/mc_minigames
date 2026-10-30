package me.petr1furious.potatominigames.games.bingo.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;



import me.petr1furious.potatominigames.games.bingo.model.BoardEntry;
import me.petr1furious.potatominigames.games.bingo.model.GameDifficulty;
import me.petr1furious.potatominigames.games.bingo.model.GameMode;

public final class BoardService {
    public record Board(BoardEntry[] entries, int[] effectiveDifficulties) {
    }

    public record GeneratedBoard(Board board, int selectionDistance, int attemptsTried, int candidateCount,
            int poolSize, int requiredCount, double lowestLineAverage, double highestLineAverage,
            double averageLineAverage) {
    }

    private record BoardAssessment(int distance, double lowestLineAverage, double highestLineAverage,
            double averageLineAverage) {
    }

    private static final int[] COLLECT_ALL_SLOTS = { 6, 7, 8, 11, 12, 13, 16, 17, 18 };
    private final DependencyCostService dependencyCostService;

    public BoardService(DependencyCostService dependencyCostService) {
        this.dependencyCostService = dependencyCostService;
    }

    public GeneratedBoard generateBoard(GameMode mode, GameDifficulty difficulty, List<BoardEntry> pool) {
        if (pool.isEmpty()) {
            throw new IllegalStateException("Board entry pool is empty.");
        }

        List<Candidate> candidates = new ArrayList<>();
        for (BoardEntry entry : pool) {
            int score = (int) Math.round(dependencyCostService.scoreForNode(entry.costNodeId()));
            if (Math.abs(score - difficulty.targetScore()) <= difficulty.maxDeviation()) {
                candidates.add(new Candidate(entry, score));
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No board items fit the selected difficulty score band.");
        }

        int required = mode == GameMode.COLLECTALL ? 9 : 25;
        if (candidates.size() < required) {
            throw new IllegalStateException("Not enough candidate entries for board generation.");
        }

        Random random = new Random();
        Board bestBoard = null;
        int bestDistance = Integer.MAX_VALUE;
        int attemptsTried = 0;
        double bestLowestLineAverage = 0d;
        double bestHighestLineAverage = 0d;
        double bestAverageLineAverage = 0d;

        for (int attempt = 0; attempt < 1200; attempt++) {
            attemptsTried = attempt + 1;
            Collections.shuffle(candidates, random);
            List<Candidate> chosen = candidates.subList(0, required);
            Board board = placeOnBoard(mode, chosen);
            BoardAssessment assessment = scoreDistance(mode, difficulty, board);
            if (assessment.distance() >= 0 && assessment.distance() < bestDistance) {
                bestDistance = assessment.distance();
                bestBoard = board;
                bestLowestLineAverage = assessment.lowestLineAverage();
                bestHighestLineAverage = assessment.highestLineAverage();
                bestAverageLineAverage = assessment.averageLineAverage();
            }
            if (assessment.distance() == 0) {
                break;
            }
        }

        if (bestBoard == null) {
            throw new IllegalStateException("Could not generate a valid board.");
        }
        return new GeneratedBoard(bestBoard, bestDistance, attemptsTried, candidates.size(),
                pool.size(), required, bestLowestLineAverage, bestHighestLineAverage, bestAverageLineAverage);
    }

    private static Board placeOnBoard(GameMode mode, List<Candidate> chosen) {
        BoardEntry[] entries = new BoardEntry[25];
        int[] effectiveDifficulties = new int[25];
        if (mode == GameMode.COLLECTALL) {
            for (int i = 0; i < COLLECT_ALL_SLOTS.length; i++) {
                int slot = COLLECT_ALL_SLOTS[i];
                entries[slot] = chosen.get(i).entry();
                effectiveDifficulties[slot] = chosen.get(i).effectiveDifficulty();
            }
        } else {
            for (int i = 0; i < 25; i++) {
                entries[i] = chosen.get(i).entry();
                effectiveDifficulties[i] = chosen.get(i).effectiveDifficulty();
            }
        }
        return new Board(entries, effectiveDifficulties);
    }

    private BoardAssessment scoreDistance(GameMode mode, GameDifficulty difficulty, Board board) {
        if (mode.lineBoard()) {
            double minAverage = Double.POSITIVE_INFINITY;
            double maxAverage = Double.NEGATIVE_INFINITY;
            double averageSum = 0d;
            int lineCount = 0;
            for (int[] line : lines()) {
                DependencyCostService.Evaluation lineEvaluation = evaluateLine(board, line);
                int count = countActiveCells(board, line);
                if (count <= 0) {
                    continue;
                }
                double totalScore = lineEvaluation.totalScore();
                double avg = totalScore / count;
                minAverage = Math.min(minAverage, avg);
                maxAverage = Math.max(maxAverage, avg);
                averageSum += avg;
                lineCount++;
            }
            if (lineCount == 0) {
                return new BoardAssessment(-1, 0d, 0d, 0d);
            }
            double spread = maxAverage - minAverage;
            double meanAverage = averageSum / lineCount;
            return new BoardAssessment((int) Math.round(spread * 1000d), minAverage, maxAverage, meanAverage);
        }

        int sum = 0;
        int count = 0;
        for (int i = 0; i < board.entries.length; i++) {
            if (board.entries[i] == null) {
                continue;
            }
            sum += board.effectiveDifficulties[i];
            count++;
        }
        int avg = sum / Math.max(1, count);
        if (Math.abs(avg - difficulty.targetScore()) > difficulty.maxDeviation()) {
            return new BoardAssessment(-1, avg, avg, avg);
        }
        return new BoardAssessment(Math.abs(sum - difficulty.targetScore() * count), avg, avg, avg);
    }

    private static List<int[]> lines() {
        List<int[]> lines = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            lines.add(new int[] { row * 5, row * 5 + 1, row * 5 + 2, row * 5 + 3, row * 5 + 4 });
        }
        for (int col = 0; col < 5; col++) {
            lines.add(new int[] { col, col + 5, col + 10, col + 15, col + 20 });
        }
        lines.add(new int[] { 0, 6, 12, 18, 24 });
        lines.add(new int[] { 4, 8, 12, 16, 20 });
        return lines;
    }

    private DependencyCostService.Evaluation evaluateLine(Board board, int[] line) {
        List<String> nodeIds = new ArrayList<>();
        for (int idx : line) {
            BoardEntry entry = board.entries()[idx];
            if (entry != null) {
                nodeIds.add(entry.costNodeId());
            }
        }
        return dependencyCostService.evaluateNodes(nodeIds);
    }

    private static int countActiveCells(Board board, int[] line) {
        int count = 0;
        for (int idx : line) {
            if (board.entries()[idx] != null) {
                count++;
            }
        }
        return count;
    }

    private record Candidate(BoardEntry entry, int effectiveDifficulty) {
    }
}
