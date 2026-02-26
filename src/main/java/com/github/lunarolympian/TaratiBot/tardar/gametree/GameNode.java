package com.github.lunarolympian.TaratiBot.tardar.gametree;

import com.github.lunarolympian.TaratiBot.board.FastBoardMap;
import com.github.lunarolympian.TaratiBot.tardar.Tardar;
import com.github.lunarolympian.TaratiBot.training.TardarNN;

import java.util.*;

/**
 * The game tree. Manages all potential game states and prunes itself so less work is necessary.
 */
public class GameNode {
    /*
    An important note is that friendly branches are classified as who is playing white in the turn.
    Essentially, if Tardar just made a move, then it's an unfriendly branch.
    */
    private FastBoardMap map;
    private LinkedHashMap<FastBoardMap, ArrayList<GameNode>> children;

    private Float score;
    private Float dangerScore = 0f;
    private int minimumPieceScore = -1;

    private static int searchDepth = 0;
    private static int gcRate = 6;
    private static Tardar.Difficulty difficulty;

    private static long nodeCounter = 0;

    public GameNode(FastBoardMap map) {
        this.map = map;
        this.score = 0f;
        this.children = new LinkedHashMap<>();
    }

    // ------------------------------------------------------------
    // This is used exclusively for the root.
    // Acts as a combination of buildTree() and scoreBoard(), though with some modifications for simplicity.
    public FastBoardMap getBestMove(Tardar.Difficulty assessmentDifficulty) {

        System.out.println("Beginning move processing");
        switch (assessmentDifficulty) {
            case EASY -> searchDepth = 3;
            case MEDIUM -> searchDepth = 4;
            case HARD -> searchDepth = 6;
            case EXPERT -> searchDepth = 8;
            case AGI -> searchDepth = 10;
        }

        difficulty = assessmentDifficulty;

        gcRate = 6;
        if(assessmentDifficulty == Tardar.Difficulty.AGI ||
                assessmentDifficulty == Tardar.Difficulty.EXPERT) gcRate = 999;

        long startTime = System.currentTimeMillis();

        Map<FastBoardMap, ArrayList<FastBoardMap>> moves = this.map.getFullTurn(false);
        Map<FastBoardMap, ArrayList<GameNode>> nodeMap = new HashMap<>();

        for(FastBoardMap move : moves.keySet())
            if(moves.get(move).isEmpty()) return move;

        Map<FastBoardMap, ArrayList<double[]>> moveInfo = new HashMap<>();

        long timer = switch (assessmentDifficulty) {
            case EXPERT -> 180_000;
            case AGI -> 300_000;
            default -> 60_000;
        };
        // Checks for any quick victories
        for(FastBoardMap opponentState : moves.keySet()) {
            ArrayList<GameNode> gameNodes = new ArrayList<>();
            for (FastBoardMap opponentOption : moves.get(opponentState)) gameNodes.add(new GameNode(opponentOption));

            int winningStates = 0;

            for(GameNode node : gameNodes) {
                node.buildTree(startTime + timer, 3, false);

                if(node.score <= -100f) {
                    break;
                }
                else if(node.score >= 100f) {
                    winningStates++;
                }
                node.freeMemory();
            }
            System.gc(); // Prevents memory problems for more complex board states

            if(winningStates == gameNodes.size()) return opponentState;
        }

        nodeCounter = 0;

        ArrayList<FastBoardMap> prunedList = new ArrayList<>();
        for(FastBoardMap opponentState : moves.keySet()) {
            ArrayList<GameNode> gameNodes = new ArrayList<>();
            boolean pruned = false;
            int winningStates = 0;

            for (FastBoardMap opponentOption : moves.get(opponentState)) gameNodes.add(new GameNode(opponentOption));
            ArrayList<double[]> opponentStateInfo = new ArrayList<>();

            for(GameNode node : gameNodes) {
                double[] nodeInfo = new double[3];
                node.buildTree(startTime + 60_000, searchDepth, false);
                nodeInfo[0] = node.score;
                nodeInfo[1] = node.dangerScore;
                nodeInfo[2] = node.getGuaranteedPieces();
                opponentStateInfo.add(nodeInfo);

                if(node.score <= -100f && !pruned) {
                    pruned = true;
                    prunedList.add(opponentState);
                }
                else if(node.score >= 100f) {
                    winningStates++;
                }
                node.freeMemory();
            }
            System.gc(); // Prevents memory problems for more complex board states

            opponentStateInfo.sort(Comparator.comparingDouble(o -> o[2])); // Useful a bit later

            // Found a winner
            if(winningStates == gameNodes.size()) {
                return opponentState;
            }

            moveInfo.put(opponentState, opponentStateInfo);
            nodeMap.put(opponentState, gameNodes);
        }

        if(System.currentTimeMillis() > startTime + timer) System.out.println("    Time is up!");

        // This prevents Tardar from throwing the game if it spots a loss.
        if(prunedList.size() != moveInfo.size()) {
            for(FastBoardMap fbm : prunedList) moveInfo.remove(fbm);
        }
        else System.out.println("    Pruned them all!");

        System.out.println("    Checked " + nodeCounter + " nodes!");
        ArrayList<FastBoardMap> moveOptions = new ArrayList<>(moveInfo.keySet());
        moveOptions.sort((Comparator.comparingDouble(o -> moveInfo.get(o).getFirst()[2])));

        if(moveOptions.isEmpty()) {
            return new ArrayList<>(moves.keySet()).getFirst();
        }

        double moveOptionScore = moveInfo.get(moveOptions.getLast()).getFirst()[2];
        moveOptions.removeIf(o -> moveInfo.get(o).getFirst()[2] < moveOptionScore);

        if(moveOptions.size() > 1) {
            Map<FastBoardMap, Double> averageMoveOptionScore = new HashMap<>();
            moveInfo.forEach((move, info) -> {
                double averageScore = 0;
                for(double[] opponentOptionInfo : info)
                    averageScore += opponentOptionInfo[1];

                averageScore /= moveInfo.get(move).size();
                averageMoveOptionScore.put(move, averageScore);
            });
            moveOptions.sort((Comparator.comparingDouble(averageMoveOptionScore::get)));
        }




        return moveOptions.getLast();
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // Move tree pruning
        // nodeMap = pruneMoves(nodeMap, this.map);

        /*if(nodeMap.isEmpty())
            return this.map.getFullTurn(false).keySet().iterator().next(); // Returns whatever happens to be the first one.
        if(nodeMap.size() == 1) return nodeMap.keySet().iterator().next();

        // This scores and puts the nodes in descending order, then checks to find the best one that doesn't
        // lose it the game
        Map<FastBoardMap, Integer> scores = new HashMap<>();
        for(FastBoardMap opponentState : nodeMap.keySet()) {
            ArrayList<GameNode> opponentOptions = nodeMap.get(opponentState);
            Map<GameNode, Integer> opponentOptionScores = new HashMap<>();
            for(GameNode node : opponentOptions) {
                opponentOptionScores.put(node, node.getGuaranteedPieces());
                // node.freeMemory(); Using this forces Tardar to rebuild the tree which is a waste of resources
            }

            opponentOptions.sort((Comparator.comparingInt(opponentOptionScores::get)));
            scores.put(opponentState, opponentOptionScores.get(opponentOptions.getLast()));
        }


        ArrayList<FastBoardMap> orderedNodes = new ArrayList<>(nodeMap.keySet());
        orderedNodes.sort((o1, o2) -> Float.compare(scores.get(o1), scores.get(o2)));
        Collections.reverse(orderedNodes);

        ArrayList<FastBoardMap> moveOptions = pickMove(new ArrayList<>(orderedNodes), nodeMap, moveInfo, startTime, true);
        if(!moveOptions.isEmpty()) return moveOptions.getFirst();
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // Shit, same thing but without the danger score
        ArrayList<FastBoardMap> map = pickMove(orderedNodes, nodeMap, moveInfo, startTime, false);
        if(map.isEmpty()) return orderedNodes.getFirst();
        return map.getFirst(); // Tardar's cooked...

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_*/

    }
    // ------------------------------------------------------------

    /**
     * Builds the tree branches to a certain depth.
     *
     * @param timer When it should stop assessing branches.
     * @param maxDepth How deep it should go assessing branches.
     */
    private void buildTree(long timer, int maxDepth, boolean limitedChecks, byte[]... restrictedMoves) {
        if(System.currentTimeMillis() > timer || maxDepth <= 0) {
            this.score = 0f;
            return;
        }

        // Builds the tree
        LinkedHashMap<FastBoardMap, ArrayList<FastBoardMap>> possibleMoves = this.map.getFullTurn(limitedChecks, restrictedMoves);

        nodeCounter++;

        // Checks if it can win from a move
        for(ArrayList<FastBoardMap> possibleMove : possibleMoves.values()) {
            if(possibleMove.isEmpty()) {
                this.score = 200f;
                return;
            }
        }
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // It can't, so it checks to see if its opponent can win.
        ArrayList<FastBoardMap> losingMoves = new ArrayList<>();
        for(FastBoardMap possibleMove : possibleMoves.keySet()) {
            ArrayList<FastBoardMap> opponentOptions = possibleMoves.get(possibleMove);
            for(FastBoardMap opponentOption : opponentOptions) {
                if(opponentOption.assessEndState(true)) {
                    losingMoves.add(possibleMove);
                    break;
                }
            }
        }
        for(FastBoardMap losingMove : losingMoves) possibleMoves.remove(losingMove);
        if(possibleMoves.isEmpty()) {
            // Needs to avoid this state!
            this.score = -200f;
            return;
        }
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // Builds out the children
        for(FastBoardMap pm : possibleMoves.keySet()) {
            ArrayList<GameNode> nextTardarStates = new ArrayList<>();

            possibleMoves.get(pm)
                    .forEach(opponentOption -> nextTardarStates.add(new GameNode(opponentOption)));

            this.children.put(pm, nextTardarStates);
        }
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // Expands the children
        climbTree(timer, maxDepth - 1, possibleMoves, limitedChecks);
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
    }

    private void climbTree(long timer, int maxDepth, LinkedHashMap<FastBoardMap, ArrayList<FastBoardMap>> possibleMoves, boolean limitedChecks) {
        ArrayList<FastBoardMap> losingStates = new ArrayList<>();
        float dangerScore = 0f; // Used to assess if a state should be avoided, even if it isn't a guaranteed loss.
        int dangerScoreCount = 0; // The amount of nodes checked for danger scores.

        for(FastBoardMap opponentState : possibleMoves.keySet()) {
            int opponentLosingStates = 0;
            int minTardarPieceScore = -1;
            for(GameNode opponentOption : children.get(opponentState)) {
                opponentOption.buildTree(timer, maxDepth - 1, limitedChecks, opponentState.getPreviousMove());
                dangerScore += opponentOption.dangerScore;
                dangerScoreCount++;
                if(opponentOption.score <= -100) {
                    dangerScore = -1f;
                    losingStates.add(opponentState);
                    break; // No point in digging further into a losing state
                }

                if(opponentOption.score >= 100f) {
                    dangerScore += 0.1f;
                    opponentLosingStates++;
                }

                int tardarPieceScore = 0;
                for(int i = 1; i <= opponentOption.getMap().getGameState()[0]; i++) {
                    if(opponentOption.map.getGameState()[i] > 22) tardarPieceScore += 2;
                    else tardarPieceScore++;
                }

                if(tardarPieceScore < minTardarPieceScore || minTardarPieceScore == -1) minTardarPieceScore = tardarPieceScore;
            }

            // Checks if all of its opponent's options win the game for Tardar
            if(opponentLosingStates == children.get(opponentState).size()) {
                this.score = 200f;
                if(maxDepth != 0)
                    releaseChildren();
                return; // As it has a winning move it doesn't need to bother checking anything else.
            }
        }

        dangerScore /= dangerScoreCount; // Averages the children's scores

        this.dangerScore = dangerScore;

        for(FastBoardMap losingState : losingStates) children.remove(losingState);
        if(children.isEmpty()) this.score = -200f;
        if(maxDepth != 0)
            releaseChildren();
    }

    /**
     * This frees up memory which helps a LOT with speed
     * Essential to going to depths > 6
     */
    private void releaseChildren() {
        // First it needs to get the best achievable score from the children
        for(ArrayList<GameNode> opponentOptions : children.values()) {
            // This is essentially getting the maximum of the minimums
            int worstOpponentOption = 100;
            for(GameNode opponentOption : opponentOptions) {
                int ooScore = opponentOption.getGuaranteedPieces();
                if(ooScore < worstOpponentOption) worstOpponentOption = ooScore;
            }
            if(worstOpponentOption > minimumPieceScore) this.minimumPieceScore = worstOpponentOption;
        }

        // Now it needs to clear the children and call the GC

        this.children.clear();
        if(nodeCounter % 200_000 == 0 && nodeCounter != 0) System.gc();
    }

    private int getGuaranteedPieces() {
        if(children.isEmpty()) {

            if(minimumPieceScore > -1) // This handles states cleared to free up memory.
                return minimumPieceScore;

            int score = 0;
            for(int i = 1; i < 9; i++) {
                if(i <= map.getGameState()[0]) {
                    if (map.getGameState()[i] <= 22) score++;
                    else score += 2;
                }
                else if (map.getGameState()[i] > 22) score--;
                // TODO maybe add a check to only dock points for opposing roks if they can't be captured or something.

            }

            return score;
        }


        int bestChildPieceScore = 0;
        for(FastBoardMap child : children.keySet()) {
            int minPieceCount = 100;
            for(GameNode opponentOption : children.get(child)) {
                int opponentOptionScore = opponentOption.getGuaranteedPieces();
                if(minPieceCount > opponentOptionScore) minPieceCount = opponentOptionScore;
            }
            if(minPieceCount > bestChildPieceScore)
                bestChildPieceScore = minPieceCount;
        }

        return bestChildPieceScore;
    }

    private void freeMemory() {
        this.children.clear();
    }
    // ------------------------------------------------------------

    public FastBoardMap getMap() {
        return map;
    }


}
