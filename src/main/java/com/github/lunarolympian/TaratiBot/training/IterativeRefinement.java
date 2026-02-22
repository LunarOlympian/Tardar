package com.github.lunarolympian.TaratiBot.training;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.tardar.NN;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class IterativeRefinement {

    /**
     * This improves neural networks by slowly taking their weights and refining them by making small adjustments.
     */
    public static void main(String[] args) throws IOException {
        // Creates 2 random neural networks.
        NN nn1 = new NN();
        NN nn2 = new NN();

        File file = new File("C:\\Users\\sebas\\Documents\\Tarati\\Tarati board states\\TardarTrainingStates.txt");
        ArrayList<LayerOptimiser> layerOptimisers = new ArrayList<>();

        ArrayList<BoardMap> practiceStates = new ArrayList<>();
        for (String state : Files.readString(file.toPath()).split("\n"))
            practiceStates.add(new BoardMap(state.split("%")[0], state.split("%")[1].equalsIgnoreCase("W")));

        BoardMap[] fullPracticeStates = new BoardMap[practiceStates.size()];
        practiceStates.toArray(fullPracticeStates);

        for(int i = 0; i < 3; i++)
            layerOptimisers.add(new LayerOptimiser(i, 3, new NN(nn1), new NN(nn2), practiceStates));

        for(LayerOptimiser optimiser : layerOptimisers) {
            optimiser.run();
            int[] results = simulateGames(fullPracticeStates, 1, nn1, nn2);

            if(results[0] >= results[1])
                nn1.saveToFile(args[0]);
            else
                nn2.saveToFile(args[0]);
        }

    }

    /**
     * Simulates a game from a starting position.
     * @param maps The starting positions to try out.
     * @param lookAheadDepth The depth to allow the neural networks to simulate to.
     * @return [NN1 wins, NN2 wins, Draws]
     */
    public static int[] simulateGames(BoardMap[] maps, int lookAheadDepth, NN nn1, NN nn2) {
        NN[] nns = new NN[]{nn1, nn2};
        int[] scores = new int[]{0, 0, 0};

        for(BoardMap map : maps) {
            BoardMap gameState = map;

            // This switches the sides
            for (int s = 0; s < 2; s++) {
                // Simulates the game
                HashSet<Integer> prevStates = new HashSet<>();
                for (int i = 0; i < 500; i++) {
                    BoardMap move = findBestMove(gameState, nns[i % 2]);
                    if (move == null) {
                        scores[(i + 1) % 2] += 500 - i;
                        break;
                    }
                    gameState = move;

                    if(i == 499 || (prevStates.contains(Arrays.hashCode(gameState.getBoardState())) && nn2 != null)) {
                        scores[2]++;
                        break;
                    }

                    prevStates.add(Arrays.hashCode(gameState.getBoardState()));
                }

                gameState = map;

                // Switches the NNs and scores
                NN firstNN = nns[0];
                nns[0] = nns[1];
                nns[1] = firstNN;

                int firstScore = scores[0];
                scores[0] = scores[1];
                scores[1] = firstScore;
            }


        }

        return scores;
    }

    private static BoardMap findBestMove(BoardMap map, NN nn) {
        if(nn == null) {
            BoardMap[] possibleMoves = map.getPossibleStates();
            if(possibleMoves.length == 0) return null;
            return possibleMoves[(int) (Math.random() * possibleMoves.length)]; // Defaults to random moves if the nn is null
        }

        BoardMap bestMap = null;
        float bestMapScore = -1000;

        for(BoardMap possibleMap : map.getPossibleStates()) {
            float mapScore = nn.score(possibleMap);
            if(mapScore > bestMapScore) {
                bestMapScore = mapScore;
                bestMap = possibleMap;
            }
        }

        return bestMap;
    }

    private static class LayerOptimiser {

        private int layer;
        private int iterations;
        private NN nn1;
        private NN nn2;
        private ArrayList<BoardMap> practiceStates;

        public LayerOptimiser(int layer, int iterations, NN nn1, NN nn2, ArrayList<BoardMap> practiceStates) {
            this.layer = layer;
            this.iterations = iterations;
            this.nn1 = nn1;
            this.nn2 = nn2;

            this.practiceStates = practiceStates;
        }

        public void run() {
            for(int i = 0; i < iterations; i++) {
                Collections.shuffle(practiceStates);
                BoardMap[] testStates = new BoardMap[10];
                testStates[0] = new BoardMap("new", true);
                for(int j = 1; j < 10; j++) {
                    testStates[j] = practiceStates.get(j - 1);
                }
                int[] results = simulateGames(testStates, 1, nn1, nn2);

                System.out.println((layer + 1) + "_" + (i + 1) + " - " + Arrays.toString(results));

                if(results[0] <= results[1]) {
                    nn1.iterateNN(nn2, testStates, layer);

                    results = simulateGames(testStates, 1, nn1, nn2);
                    System.out.println("    " + (layer + 1) + "_" + (i + 1) + " - " + Arrays.toString(results));
                    System.out.println("    Random: " + Arrays.toString(simulateGames(testStates, 1, nn1, null)));
                }
                else {
                    nn2.iterateNN(nn1, testStates, layer);

                    results = simulateGames(testStates, 1, nn1, nn2);
                    System.out.println("    " + (layer + 1) + "_" + (i + 1) + " - " + Arrays.toString(results));
                    System.out.println("    Random: " + Arrays.toString(simulateGames(testStates, 1, nn2, null)));
                }
            }
        }
    }


}
