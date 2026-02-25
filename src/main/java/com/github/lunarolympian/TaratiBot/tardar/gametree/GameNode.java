package com.github.lunarolympian.TaratiBot.tardar.gametree;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.board.BoardUtils;
import com.github.lunarolympian.TaratiBot.board.FastBoardMap;
import com.github.lunarolympian.TaratiBot.tardar.NN;
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
    private TardarNN nn;
    private Map<FastBoardMap, ArrayList<GameNode>> children;

    private Float score;
    private Float dangerScore = 0f;
    private boolean assessed = false;
    private int lossDepth = -1;
    private Float branchScore;

    private static long nodeCounter = 0;


    public GameNode(BoardMap map, TardarNN nn) {
        this.map = new FastBoardMap(map);
        this.nn = nn;
        this.score = 0f;
        this.branchScore = 0f;
        this.children = new HashMap<>();
    }

    public GameNode(FastBoardMap map, TardarNN nn) {
        this.map = map;
        this.nn = nn;
        this.score = 0f;
        this.branchScore = 0f;
        this.children = new HashMap<>();
    }

    // ------------------------------------------------------------
    // This is used exclusively for the root.
    // Acts as a combination of buildTree() and scoreBoard(), though with some modifications for simplicity.
    public FastBoardMap getBestMove() {
        long startTime = System.currentTimeMillis();

        Map<FastBoardMap, ArrayList<FastBoardMap>> moves = this.map.getFullTurn(false);
        Map<FastBoardMap, ArrayList<GameNode>> nodeMap = new HashMap<>();

        for(FastBoardMap move : moves.keySet())
            if(moves.get(move).isEmpty()) return move;

        for(FastBoardMap opponentState : moves.keySet()) {
            ArrayList<GameNode> gameNodes = new ArrayList<>();
            for (FastBoardMap opponentOption : moves.get(opponentState)) gameNodes.add(new GameNode(opponentOption, nn));

            for(GameNode node : gameNodes) {
                node.buildTree(startTime + 30_000, 3, false);
            }
            nodeMap.put(opponentState, gameNodes);
        }
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // Move tree pruning
        nodeMap = pruneMoves(nodeMap, this.map);

        if(nodeMap.isEmpty())
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
                node.freeMemory();
            }

            opponentOptions.sort((Comparator.comparingInt(opponentOptionScores::get)));
            scores.put(opponentState, opponentOptionScores.get(opponentOptions.getLast()));
        }

        System.gc(); // Prevents some crashing


        ArrayList<FastBoardMap> orderedNodes = new ArrayList<>(nodeMap.keySet());
        orderedNodes.sort((o1, o2) -> Float.compare(scores.get(o1), scores.get(o2)));
        Collections.reverse(orderedNodes);

        ArrayList<FastBoardMap> moveOptions = pickMove(new ArrayList<>(orderedNodes), nodeMap, startTime, true);
        if(!moveOptions.isEmpty()) return moveOptions.getFirst();
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // Shit, same thing but without the danger score
        ArrayList<FastBoardMap> map = pickMove(orderedNodes, nodeMap, startTime, false);
        if(map.isEmpty()) return orderedNodes.getFirst();
        return map.getFirst(); // Tardar's cooked...

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

    }
    // ------------------------------------------------------------

    private static Map<FastBoardMap, ArrayList<GameNode>> pruneMoves(Map<FastBoardMap, ArrayList<GameNode>> moves, FastBoardMap sourceMap) {
        ArrayList<FastBoardMap> losingStates = new ArrayList<>();
        for(FastBoardMap opponentState : moves.keySet()) {
            int opponentLosingStates = 0;
            for(GameNode node : moves.get(opponentState)) {

                if(node.score <= -200) {
                    losingStates.add(opponentState);
                    break; // No point in digging further into a losing state
                }

                if(node.score >= 200) opponentLosingStates++;
            }

            if(losingStates.contains(opponentState)) continue;

            // Checks if all of its opponent's options win the game for Tardar
            if(opponentLosingStates == moves.get(opponentState).size()) {
                Map<FastBoardMap, ArrayList<GameNode>> winningStateMap = new HashMap<>();
                winningStateMap.put(opponentState, new ArrayList<>());
                return winningStateMap;
            }
            // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

            // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
            // This handles checking if the move loses pieces that can't be reclaimed
            // Essentially it checks if Tardar can hold any gains made with the move
            int piecesGained = opponentState.getGameState()[0] - sourceMap.getGameState()[0];

            if(piecesGained == 0) continue; // Checks for gains

            // Gains are made, now to check if they can be held
            // First it gets the relevant pieces
            TreeSet<Byte> relevantPieces = new TreeSet<>();
            for(int i = 1; i <= opponentState.getGameState()[0]; i++) {
                relevantPieces.add(opponentState.getGameState()[i] <= 22 ? opponentState.getGameState()[i] : (byte) (opponentState.getGameState()[i] - 23));
            }

            for(int i = 1; i <= sourceMap.getGameState()[0]; i++) {
                if(relevantPieces.contains(sourceMap.getGameState()[i]) &&
                        opponentState.getPreviousMove()[1] != sourceMap.getGameState()[i]) relevantPieces.remove(sourceMap.getGameState()[i]);
            }


            // Now it needs to check if the opponent can reclaim those pieces
            boolean passing = true;
            for(GameNode node : moves.get(opponentState)) {
                int reclaimedPieces = 0;
                for(int i = node.getMap().getGameState()[0] + 1; i < 9; i++) {
                    byte convertedPiece = node.getMap().getGameState()[i] <= 22 ? node.getMap().getGameState()[i] : (byte) (node.getMap().getGameState()[i] - 23);
                    if(relevantPieces.contains(convertedPiece)) reclaimedPieces++;
                }

                if(reclaimedPieces < relevantPieces.size()) continue; // Checks if it's lost enough pieces to care

                // Bit weird, but the last section of code works by assuming Tardar can't get back its pieces, then proving it can
                passing = false;
                // Since it has it checks this node's children to see if Tardar can reclaim gains
                Map<FastBoardMap, ArrayList<GameNode>> stateChildren = node.children;

                for(FastBoardMap child : stateChildren.keySet()) {
                    int heldGains = 0;
                    for(int i = 1; i <= child.getGameState()[0]; i++) {
                        byte convertedPiece = child.getGameState()[i] <= 22 ? child.getGameState()[i] : (byte) (child.getGameState()[i] - 23);
                        if(relevantPieces.contains(convertedPiece)) heldGains++;
                    }

                    if(heldGains > 1) {
                        passing = true;
                        break;
                    }
                }

                if(!passing) break;
            }

            if(!passing) losingStates.add(opponentState);

        }
        for(FastBoardMap losingState : losingStates) moves.remove(losingState);

        return moves;
    }

    // ------------------------------------------------------------

    private ArrayList<FastBoardMap> pickMove(ArrayList<FastBoardMap> orderedNodes, Map<FastBoardMap,
            ArrayList<GameNode>> nodeMap, long startTime, boolean accountForRisk) {
        ArrayList<FastBoardMap> possiblyValidOptions = new ArrayList<>(orderedNodes);
        nodeCounter = 0;
        for(FastBoardMap opponentState : orderedNodes) {
            ArrayList<GameNode> opponentOptions = nodeMap.get(opponentState);

            opponentOptions.sort(Comparator.comparingInt(oo -> oo.map.getGameState()[0]));
            Collections.reverse(opponentOptions);

            if(accountForRisk) opponentOptions.forEach(option -> option.buildTree(startTime + 60_000, 4, true));

            // Needs to make sure none of them are -200
            boolean failed = false;
            float averageDangerScore = 0f;
            for(GameNode option : opponentOptions) {
                averageDangerScore += option.dangerScore;
                if(option.score <= -100) {
                    failed = true;
                    break;
                }
            }

            if(accountForRisk && averageDangerScore < -0.16) failed = true;
            if(failed) {
                possiblyValidOptions.removeFirst();
                continue;
            }
            System.out.println("Checked " + nodeCounter);
            System.out.println("Average danger score " + averageDangerScore);
            this.children = null;
            possiblyValidOptions.removeIf(option -> option != opponentState);
            return possiblyValidOptions;
        }

        return possiblyValidOptions;
    }

    /**
     * Builds the tree branches to a certain depth.
     *
     * @param timer When it should stop assessing branches.
     * @param maxDepth How deep it should go assessing branches.
     */
    private void buildTree(long timer, float maxDepth, boolean limitedChecks, byte[]... restrictedMoves) {
        if(System.currentTimeMillis() > timer || maxDepth <= 0) {
            this.score = 0f;
            return;
        }

        /*if(this.assessed) {
            for(ArrayList<GameNode> opponentOptions : children.values()) {
                for(GameNode opponentOption : opponentOptions) {
                    opponentOption.buildTree(timer, maxDepth - 1, limitedChecks);
                }
            }
        }*/

        this.assessed = true;

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
                    .forEach(opponentOption -> nextTardarStates.add(new GameNode(opponentOption, nn)));

            this.children.put(pm, nextTardarStates);
        }
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // Expands the children

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

            dangerScore += minTardarPieceScore * 0.01f;

            // Checks if all of its opponent's options win the game for Tardar
            if(opponentLosingStates == children.get(opponentState).size()) {
                this.score = 200f;
                return; // As it has a winning move it doesn't need to bother checking anything else.
            }
        }

        dangerScore /= dangerScoreCount; // Averages the children's scores

        this.dangerScore = dangerScore;

        for(FastBoardMap losingState : losingStates) children.remove(losingState);
        if(children.isEmpty()) this.score = -200f;
    }

    private int getGuaranteedPieces() {
        if(children.isEmpty()) {
            int score = 0;
            for(int i = 1; i <= map.getGameState()[0]; i++) {
                if(map.getGameState()[i] <= 22) score++;
                else score += 2;
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
            if(minPieceCount > bestChildPieceScore) {
                bestChildPieceScore = minPieceCount;
            }
        }

        return bestChildPieceScore;
    }

    private void freeMemory() {
        this.children.clear();
    }

    /**
     * Used to encourage promotions and discourage stalling.
     * @param move The move performed. Compared with the starting state.
     * @return The offset to apply to get Tardar to sort this out.
     */
    private float checkOffset(FastBoardMap move) {
        float offset = 0f;
        // Checks rocks and sees if the move promoted any
        int startingRockCount = 0;
        for(int i = 1; i <= map.getGameState()[0]; i++)
            if(map.getGameState()[i] > 22) startingRockCount++;

        int moveRockCount = 0;
        for(int i = 1; i <= move.getGameState()[0]; i++)
            if(move.getGameState()[i] > 22) moveRockCount++;

        if(moveRockCount > startingRockCount) offset += 0.1f;

        // Now it checks to make sure Tardar isn't stalling
        byte pm0 = move.getPreviousMove()[0];
        byte pm1 = move.getPreviousMove()[1];
        if((pm0 <= 5 || pm0 == 10 || pm0 == 11) && pm1 <= 3) offset -= 0.2f;

        // Docks points for pieces not developed
        int undevelopedCobs = 0;
        for(int i = 1; i <= move.getGameState()[0]; i++) {
            if(move.getGameState()[i] <= 1) {
                undevelopedCobs++;
            }
            else if(move.getGameState()[i] > 22 && move.getGameState()[i] - 23 <= 3)
                offset -= 0.03f; // No limit for rocks

            if(move.getGameState()[i] > 23) offset += 0.01f; // Bonus points for extra rocks
        }

        if(undevelopedCobs == 2) offset -= 0.5f; // Docks a lot for not developing any cobs in the back.


        return offset;
    }
    // ------------------------------------------------------------

    public FastBoardMap getMap() {
        return map;
    }


}
