package com.github.lunarolympian.TaratiBot.tardar.gametree;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.board.FastBoardMap;
import com.github.lunarolympian.TaratiBot.tardar.NN;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The game tree. Manages all potential game states and prunes itself so less work is necessary.
 */
public class GameNode {
    /*
    An important note is that friendly branches are classified as who is playing white in the turn.
    Essentially, if Tardar just made a move, then it's an unfriendly branch.
    */
    private FastBoardMap map;
    private NN nn;
    private Map<FastBoardMap, ArrayList<GameNode>> children;

    private Float score;
    private int lossDepth = -1;
    private Float branchScore;


    public GameNode(BoardMap map, NN nn) {
        this.map = new FastBoardMap(map);
        this.nn = nn;
        this.score = 0f;
        this.branchScore = 0f;
        this.children = new HashMap<>();
    }

    public GameNode(FastBoardMap map, NN nn) {
        this.map = map;
        this.nn = nn;
        this.score = 0f;
        this.branchScore = 0f;
        this.children = new HashMap<>();
    }

    // ------------------------------------------------------------
    // This is used exclusively for the root.
    // Acts as a combination of buildTree() and scoreBoard(), though with some modifications for simplicity.
    public FastBoardMap getBestMove() throws InterruptedException {
        long startTime = System.currentTimeMillis();

        Map<FastBoardMap, ArrayList<FastBoardMap>> moves = this.map.getFullTurn();
        Map<FastBoardMap, ArrayList<GameNode>> nodeMap = new HashMap<>();

        ArrayList<FastBoardMap> losingStates = new ArrayList<>();
        for(FastBoardMap opponentState : moves.keySet()) {
            ArrayList<GameNode> gameNodes = new ArrayList<>();
            for (FastBoardMap opponentOption : moves.get(opponentState)) gameNodes.add(new GameNode(opponentOption, nn));

            for(GameNode node : gameNodes) {
                node.buildTree(startTime + 300_000, 2);
            }
            nodeMap.put(opponentState, gameNodes);
        }

        for(FastBoardMap opponentState : moves.keySet()) {

            int opponentLosingStates = 0;
            for(GameNode node : nodeMap.get(opponentState)) {

                if(node.score <= -200) {
                    losingStates.add(opponentState);
                    break; // No point in digging further into a losing state
                }

                if(node.score >= 200) opponentLosingStates++;
            }

            // Checks if all of its opponent's options win the game for Tardar
            if(opponentLosingStates == moves.get(opponentState).size()) {
                return opponentState;
            }
        }
        for(FastBoardMap losingState : losingStates) moves.remove(losingState);

        if(moves.isEmpty())
            return this.map.getFullTurn().keySet().iterator().next(); // Returns whatever happens to be the first one.
        if(moves.size() == 1) return moves.keySet().iterator().next();

        // If nothing is guaranteed to win then it runs the NN on the children and gets the best one.
        Map<FastBoardMap, Float> childrenScores = new HashMap<>();
        ArrayList<FastBoardMap> childrenCollection = new ArrayList<>();

        for(FastBoardMap child : moves.keySet()) {
            childrenCollection.add(child);

            if(moves.get(child).isEmpty()) return child;
            childrenScores.put(child, nn.score(child));
        }

        childrenCollection.sort((c1, c2) -> Float.compare(childrenScores.get(c1), childrenScores.get(c2)));

        return childrenCollection.getLast();

    }
    // ------------------------------------------------------------

    // ------------------------------------------------------------
    /**
     * Builds the tree branches to a certain depth.
     *
     * @param timer When it should stop assessing branches.
     * @param maxDepth How deep it should go assessing branches.
     */
    private void buildTree(long timer, float maxDepth) {
        if(System.currentTimeMillis() > timer || maxDepth <= 0) {
            this.score = 0f;
            return;
        }

        // Builds the tree
        Map<FastBoardMap, ArrayList<FastBoardMap>> possibleMoves = this.map.getFullTurn();

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
        ArrayList<FastBoardMap> losingStates = new ArrayList<>();
        for(FastBoardMap opponentState : children.keySet()) {

            int opponentLosingStates = 0;
            for(GameNode opponentOption : children.get(opponentState)) {
                opponentOption.buildTree(timer, maxDepth - 1);
                if(opponentOption.score <= -200) {
                    losingStates.add(opponentState);
                    break; // No point in digging further into a losing state
                }

                if(opponentOption.score >= 200) opponentLosingStates++;
            }

            // Checks if all of its opponent's options win the game for Tardar
            if(opponentLosingStates == children.get(opponentState).size()) {
                this.score = 200f;
                return; // As it has a winning move it doesn't need to bother checking anything else.
            }
        }

        for(FastBoardMap losingState : losingStates) children.remove(losingState);
        if(children.isEmpty()) this.score = -200f;
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
    }

    /**
     * This is used to find the branch with the best score in the end.
     */
    private void getBranchScore() {
        // This shouldn't really be necessary as it's the evaluation function's job to get the branch to a good position.
    }

    /**
     * Checks if its opponent can make any moves this turn.
     */
    private void quickAssess() {
        // Needs to check if it will lose when making a move
        this.map.assessEndState(false);
    }

    private void scoreBoard() {

    }
    // ------------------------------------------------------------

    private static class EvalThread extends Thread {
        private ArrayList<GameNode> evalNodes;
        private long timer;
        private AtomicInteger doneCounter;

        public EvalThread(ArrayList<GameNode> nodes, long timer, AtomicInteger doneCounter) {
            this.evalNodes = nodes;
            this.timer = timer;
            this.doneCounter = doneCounter;
        }

        @Override
        public void run() {
            for(GameNode node : evalNodes) node.buildTree(timer, 4);
            doneCounter.incrementAndGet();
        }
        
        public ArrayList<GameNode> getNodes() {
            return this.evalNodes;
        }
    }


}
