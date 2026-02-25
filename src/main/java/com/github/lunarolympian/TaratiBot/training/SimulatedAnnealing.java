package com.github.lunarolympian.TaratiBot.training;

import com.github.lunarolympian.TaratiBot.board.FastBoardMap;

import java.io.IOException;
import java.util.*;

public class SimulatedAnnealing {

    public static void main(String[] args) throws IOException {
        Map<FastBoardMap, ArrayList<FastBoardMap>> notableMoves = generate();

        TardarNN tardarNN = new TardarNN();

        double bestLoss = 100;

        for(int i = 0; i < 100; i++) {
            ArrayList<double[]> lossValues = new ArrayList<>();

            double winningMovesLoss = 0;
            int correctWinsTotal = 0;

            double bestMovesLoss = 0;
            int bestMovesTotal = 0;

            double losingMovesLoss = 0;
            int losingMovesTotal = 0;

            double otherMovesLoss = 0;
            int otherMovesTotal = 0;

            for(FastBoardMap move : notableMoves.keySet()) {
                var possibleMoves = move.getFullTurn(false);
                ArrayList<FastBoardMap> options = new ArrayList<>(possibleMoves.keySet());
                var scoresMap = tardarNN.scoreOptions(options);

                double[] lossArray = new double[scoresMap.size()];
                int pos = 0;
                ArrayList<FastBoardMap> sortedScores = new ArrayList<>(scoresMap.keySet());
                sortedScores.sort((s1, s2) -> Float.compare(scoresMap.get(s1), scoresMap.get(s2)));

                for(FastBoardMap fbm : scoresMap.keySet()) {
                    float actualScore = fbm.getFlags()[1] * 0.05f;
                    int moveType = 0;
                    for(FastBoardMap optimalMove : notableMoves.get(move)) {
                        if(optimalMove.equals(fbm) && optimalMove.getFlags()[0] == (byte) 255) {
                            actualScore = 1f;
                            correctWinsTotal++;
                            moveType = 3;
                            break;
                        }
                        else if(optimalMove.equals(fbm) && optimalMove.getFlags()[0] != (byte) 0) {
                            // Score is adjusted depending on how many nearby win states there are.
                            actualScore = Math.clamp((0.05f * optimalMove.getFlags()[0]), 0.05f, 0.25f) + 0.5f;
                            bestMovesTotal++;
                            moveType = 2;
                            break;
                        }
                        else if(optimalMove.equals(fbm)) {
                            losingMovesTotal++;
                            moveType = 1;
                            actualScore = 0;
                            break;
                        }
                        else {
                            actualScore = 0.25f;
                            otherMovesTotal++;
                            moveType = 0;
                            break;
                        }
                    }
                    float loss = Math.abs(actualScore - scoresMap.get(fbm));
                    lossArray[pos] = loss;
                    switch (moveType) {
                        case 0:
                            otherMovesLoss += loss;
                            break;
                        case 1:
                            losingMovesLoss += loss;
                            break;
                        case 2:
                            bestMovesLoss += loss;
                            break;
                        case 3:
                            winningMovesLoss += loss;
                            break;
                    }
                    pos++;
                }

                lossValues.add(lossArray);
            }

            float winsPart = (float) (winningMovesLoss / (float) correctWinsTotal);
            float bestMovesPart = (float) (bestMovesLoss / (float) bestMovesTotal);
            float losingMovesPart = (float) (losingMovesLoss / (float) losingMovesTotal);
            float otherMovesPart = (float) (otherMovesLoss / (float) otherMovesTotal);

            System.out.print(i +
                    ":\n   Winning: " + winsPart +
                    "\n   Best: " + bestMovesPart +
                    "\n   Other: " + otherMovesPart +
                    "\n   Losing: " + losingMovesPart +
                    "\n   ");
            tardarNN.train(winsPart, bestMovesPart, otherMovesPart, losingMovesPart, (1 / 100d) * (100 - i));
            System.out.println();

            double averageLoss = (winsPart * 0.5) + (bestMovesPart * 1.2) + otherMovesPart + (losingMovesPart * 1.4f);

            if(averageLoss >= bestLoss) continue;
            bestLoss = averageLoss;
            tardarNN.save("C:\\Users\\sebas\\Documents\\Tarati\\Tardar\\Simulated Annealing\\tardar2.trdr", true);
        }
    }

    /**
     * Generates training data by using FastBoardMap to simulate games and find states which are a win/loss.
     */
    private static Map<FastBoardMap, ArrayList<FastBoardMap>> generate() {
        Map<FastBoardMap, ArrayList<FastBoardMap>> notableMoves = new HashMap<>();

        for(int i = 0; i < 500; i++) {
            FastBoardMap fbm = new FastBoardMap(new byte[]{
                    4,
                    0, 1, 4, 5,
                    2, 3, 10, 11
            });

            for(int j = 0; j < 1_000; j++) {
                LinkedHashMap<FastBoardMap, ArrayList<FastBoardMap>> moveResults = fbm.getFullTurn(false);

                ArrayList<FastBoardMap> bestMoveOptions = new ArrayList<>();
                int bestMoveOptionCount = 0;
                boolean optimalMoveFound = false;

                // This goes through each possible move to find the best one (or at least close to best)
                for(FastBoardMap moveOption : moveResults.keySet()) {
                    // Checks if the move wins Tardar the game
                    if(moveResults.get(moveOption).isEmpty()) {
                        moveOption.setFlag(0, (byte) 255);
                        ArrayList<FastBoardMap> opponentOptions = notableMoves.getOrDefault(fbm, new ArrayList<>());
                        opponentOptions.add(moveOption);
                        notableMoves.put(fbm, opponentOptions);
                        optimalMoveFound = true;
                        continue;
                    }

                    if(optimalMoveFound) continue;

                    // Next checks if the move is losing
                    boolean endState = false;
                    for(FastBoardMap map : moveResults.get(moveOption)) {
                        if(map.assessEndState(true)) {
                            endState = true;
                            break;
                        }
                    }

                    // Losing move, so VERY bad.
                    if(endState) {
                        ArrayList<FastBoardMap> opponentOptions = notableMoves.getOrDefault(fbm, new ArrayList<>());
                        opponentOptions.add(moveOption);
                        notableMoves.put(fbm, opponentOptions);
                        continue; // Losing move
                    }

                    // This offsets based on the highest number of pieces Tardar can guarantee in one turn
                    int minScore = 100;
                    for(FastBoardMap map : moveResults.get(moveOption)) {
                        int currentScore = map.getGameState()[0];
                        for(int k = 1; k <= map.getGameState()[0]; k++) {
                            if(map.getGameState()[k] > 22) currentScore += 1;
                        }

                        if(currentScore < minScore) minScore = currentScore;
                    }

                    moveOption.setFlag(1, (byte) minScore);


                    // Now it checks if the move is good (opponent can lose if they choose a bad move)
                    int goodMoveCount = getGoodMoves(moveResults.get(moveOption));

                    moveOption.setFlag(0, (byte) goodMoveCount);

                    if(goodMoveCount > bestMoveOptionCount) {
                        bestMoveOptions.clear();
                        bestMoveOptions.add(moveOption);
                        bestMoveOptionCount = goodMoveCount;
                    }
                    else if(bestMoveOptionCount == goodMoveCount && goodMoveCount != 0) {
                        bestMoveOptions.add(moveOption);
                    }
                }

                if(!optimalMoveFound && !bestMoveOptions.isEmpty()) notableMoves.put(fbm, bestMoveOptions);

                // Picks a random move and flips the board
                ArrayList<FastBoardMap> shuffledMoveOptions = new ArrayList<>(moveResults.keySet());
                Collections.shuffle(shuffledMoveOptions);

                if(shuffledMoveOptions.isEmpty()) break;
                fbm = shuffledMoveOptions.getFirst().flipBoard();

            }

        }

        return notableMoves;
    }

    /**
     * Looks for places where the opponent CAN lose, but not all of their moves are losing.
     * @return How many good states there are.
     */
    private static int getGoodMoves(ArrayList<FastBoardMap> opponentOptions) {
        int goodMoveCount = 0;
        for(FastBoardMap opponentMove : opponentOptions ) {
            LinkedHashMap<FastBoardMap, ArrayList<FastBoardMap>> tardarOptions = opponentMove.getFullTurn(false);
            for(FastBoardMap tardarOption : tardarOptions.keySet()) {
                if(tardarOptions.get(tardarOption).isEmpty()) goodMoveCount++;
            }
        }
        return goodMoveCount;
    }

    private static void train() {

    }
}
