package com.github.lunarolympian.TaratiBot.training;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.board.BoardUtils;
import com.github.lunarolympian.TaratiBot.tardar.NN;

import java.util.*;

public class Gauntlet {

    public Map<NN, Double> runGauntlet(NN[] networks) {
        // Simulates a bunch of games for the bots, though with the notable edition of draws to the rules

        Map<NN, Double> scoreSheet = new HashMap<>();

        // Each bot plays bots 4 ahead of i
        for(int i = 0; i < networks.length; i++) {
            for(int j = 0; j < 4; j++) {
                GameScore gameResult = simulateGame(networks[i], networks[(i + (j + 1)) % networks.length], 0);
                scoreSheet.put(networks[i], scoreSheet.getOrDefault(networks[i], 0d) + gameResult.score1);
                scoreSheet.put(networks[(i + j) % networks.length], scoreSheet.getOrDefault(networks[(i + j) % networks.length], 0d) + gameResult.score2);
            }

            for(int j = 0; j < 4; j++) {
                GameScore gameResult = simulateGame(networks[i], networks[(i + (j + 1)) % networks.length], (int) (Math.random() * 5));
                scoreSheet.put(networks[i], scoreSheet.getOrDefault(networks[i], 0d) + gameResult.score1);
                scoreSheet.put(networks[(i + j) % networks.length], scoreSheet.getOrDefault(networks[(i + j) % networks.length], 0d) + gameResult.score2);
            }
        }

        return scoreSheet;
    }

    public static GameScore simulateGame(NN nn1, NN nn2, int randomBeginning) {


        // Scoring gets the winner 1 - turns / 500.

        NN[] nnOrder = new NN[]{nn1, nn2};
        double[] nnScores = new double[]{0d, 0d};

        BoardMap randomBoardStart = new BoardMap("new", true);

        // This elaborate thing just exists to allow it to start at random states further into the future.
        BoardMap prevState = null;
        for(int i = 0; i < randomBeginning; i++) {
            ArrayList<BoardMap> states = new ArrayList<>(List.of(randomBoardStart.getPossibleStates()));
            states.removeIf(state -> state.getPossibleStates().length == 0);

            if(states.isEmpty()) {
                randomBoardStart = prevState;
                break;
            }

            Collections.shuffle(states);
            prevState = randomBoardStart;
            randomBoardStart = states.getFirst();
        }

        for(int s = 0; s < 2; s++) {
            BoardMap map = new BoardMap(randomBoardStart.getBoardState());
            // Max 500 turns
            for (int i = 0; i < 500; i++) {
                BoardMap bestState = null;
                float bestScore = -1000;
                for (BoardMap state : map.getPossibleStates()) {
                    float stateScore = nnOrder[i % 2].score(state);
                    if (bestState == null || stateScore > bestScore) {
                        bestState = state;
                        bestScore = stateScore;
                    }
                }

                // Checks if it can move this turn (loss if fails)
                if(bestState == null) {
                    nnScores[(i + 1) % 2] += (1d - ((double) i / 500d));
                    break;
                }
                // Checks if it took all the pieces with that move (win if passes)
                else if(bestState.getBoardState()[0] == 0) {
                    nnScores[i % 2] += (1d - ((double) i / 500d));
                    break;
                }

                // Makes the move
                map = bestState;
            }

            NN network = nnOrder[0];
            nnOrder[0] = nnOrder[1];
            nnOrder[1] = network;

            double networkScore = nnScores[0];
            nnScores[0] = nnScores[1];
            nnScores[1] = networkScore;
        }

        return new GameScore(nnScores[0], nnScores[1]);
    }

    public static double simulateTardarGame(NN nn1, int rounds, boolean random, boolean announceMoves) {
        Random rng = new Random(1);
        BoardMap map = new BoardMap("new", true);

        // Scoring gets the winner 1 - turns / 100.

        boolean tardarFirst = false;
        int wins = 0;
        double nnScore = 0d;
        double tardarScore = 0d;

        for(int s = 0; s < rounds; s++) {

            // Max 100 turns
            for (int i = 0; i < 100; i++) {
                BoardMap bestState = null;

                boolean nnTurn = (tardarFirst && i % 2 == 1) || (!tardarFirst && i % 2 == 0);
                if(nnTurn) {
                    bestState = null;
                    float bestScore = -1000;
                    for (BoardMap state : map.getPossibleStates()) {
                        float stateScore = nn1.score(state);
                        if (bestState == null || stateScore > bestScore) {
                            bestState = state;
                            bestScore = stateScore;
                        }
                    }
                }
                else if(!random) {
                    /*BoardMap[] possibleStates = map.getPossibleStates();
                    bestState = map;
                    int bestBoardStateScore = map.getBoardState()[0];
                    for(BoardMap state : possibleStates) {
                        // Subtracted from 8 because the returned states are inverted
                        if(8 - state.getBoardState()[0] > bestBoardStateScore) {
                            bestState = state;
                            bestBoardStateScore = 8 - state.getBoardState()[0];
                        }
                    }
                    if(possibleStates.length > 0 && bestState == map) bestState = possibleStates[(int) (rng.nextDouble() * possibleStates.length)];*/
                    BoardMap bestMap = null;
                    double bestScore = 0;
                    for(BoardMap possibleMove : map.getPossibleStates()) {
                        double mapScore = TestBot.score(possibleMove);

                        if (bestMap == null || mapScore > bestScore) {
                            bestMap = possibleMove;
                            bestScore = mapScore;
                        }
                    }
                    if(bestMap != null) bestState = bestMap;
                }
                else {
                    BoardMap[] possibleStates = map.getPossibleStates();
                    if(possibleStates.length > 0) bestState = possibleStates[(int) (rng.nextDouble() * possibleStates.length)];
                }


                if(bestState == null || bestState.getBoardState()[0] == 0) {
                    if(nnTurn) {
                        nnScore += (1d - ((double) i / 100d));
                        wins++;
                    }
                    else tardarScore += (1d - ((double) i / 100d));
                    break;
                }
                if(announceMoves) {
                    int[] previousMove = i % 2 == 0 ? bestState.getPreviousMove() : BoardUtils.invertMove(bestState.getPreviousMove());
                    System.out.println(BoardUtils.intToTile(previousMove[0]) + " " + BoardUtils.intToTile(previousMove[1]));
                }

                // Makes the move
                map = bestState;

                if(map.getPossibleStates().length == 0) {
                    if(nnTurn) {
                        nnScore += (1d - ((double) i / 100d));
                        wins++;
                    }
                    else {
                        tardarScore += (1d - ((double) i / 100d));
                        nnScore += ((double) i / 10_000); // Needs a reward for surviving longer, though it's tiny.
                    }
                    break;
                }

                if(i == 100) {
                    if(announceMoves) {
                        System.out.println("Draw!");
                    }
                }


            }

            tardarFirst = !tardarFirst;
            double nextBoard = Math.random();
            map = new BoardMap("new", true);
        }

        if(tardarScore == 0) tardarScore = 0.00001; // So no divide by 0 errors
        if(!announceMoves) return wins;

        for(int s = 0; s < 20; s++) {

            // Max 100 turns
            for (int i = 0; i < 100; i++) {
                BoardMap bestState = null;

                boolean nnTurn = (tardarFirst && i % 2 == 1) || (!tardarFirst && i % 2 == 0);
                if(nnTurn) {
                    bestState = null;
                    float bestScore = -1000;
                    for (BoardMap state : map.getPossibleStates()) {
                        float stateScore = nn1.score(state);
                        if (bestState == null || stateScore > bestScore) {
                            bestState = state;
                            bestScore = stateScore;
                        }
                    }
                }
                else {
                    BoardMap[] possibleStates = map.getPossibleStates();
                    if(possibleStates.length > 0) bestState = possibleStates[(int) (Math.random() * possibleStates.length)];
                }


                if(bestState == null || bestState.getBoardState()[0] == 0) {
                    if(nnTurn) nnScore += (1d - ((double) i / 100d));
                    else tardarScore += (1d - ((double) i / 100d));
                    break;
                }
                if(announceMoves) {
                    int[] previousMove = i % 2 == 0 ? bestState.getPreviousMove() : BoardUtils.invertMove(bestState.getPreviousMove());
                    System.out.println(BoardUtils.intToTile(previousMove[0]) + " " + BoardUtils.intToTile(previousMove[1]));
                }

                // Makes the move
                map = bestState;

                if(map.getPossibleStates().length == 0) {
                    if(nnTurn) nnScore += (1d - ((double) i / 100d));
                    else tardarScore += (1d - ((double) i / 100d));
                    break;
                }

                if(i == 100) {
                    if(announceMoves) {
                        System.out.println("Draw!");
                    }
                }


            }

            tardarFirst = !tardarFirst;
            map = new BoardMap("new", true);
        }

        return nnScore / tardarScore;
    }

    public static int approveTardar(NN nn, int rounds) {
        Random rng = new Random(1);
        BoardMap map = new BoardMap("new", true);

        // Scoring gets the winner 1 - turns / 100.

        boolean randomFirst = false;
        int wins = 0;

        for(int s = 0; s < rounds; s++) {
            // Max 100 turns
            for (int i = 0; i < 100; i++) {
                BoardMap bestState = null;

                boolean tardarTurn = (randomFirst && i % 2 == 1) || (!randomFirst && i % 2 == 0);
                if(tardarTurn) {
                    bestState = null;
                    float bestScore = -1000;
                    for (BoardMap state : map.getPossibleStates()) {
                        float stateScore = nn.score(state);
                        if (bestState == null || stateScore > bestScore) {
                            bestState = state;
                            bestScore = stateScore;
                        }
                    }
                }
                else {
                    BoardMap[] possibleStates = map.getPossibleStates();
                    if(possibleStates.length > 0) bestState = possibleStates[(int) (rng.nextDouble() * possibleStates.length)];
                }


                if(bestState == null || bestState.getBoardState()[0] == 0) {
                    if(tardarTurn) wins++;
                    break;
                }

                // Makes the move
                map = bestState;

                if(map.getPossibleStates().length == 0) {
                    if (tardarTurn) wins++;
                    break;
                }
            }

            randomFirst = !randomFirst;
            map = new BoardMap("new", true);
        }

        return wins;
    }


    // Games over 10 moves long start to receive minor penalties
    public record GameScore(double score1, double score2) {

    }
}
