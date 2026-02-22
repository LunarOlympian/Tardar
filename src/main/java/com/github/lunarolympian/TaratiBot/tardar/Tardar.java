package com.github.lunarolympian.TaratiBot.tardar;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.board.BoardUtils;
import com.github.lunarolympian.TaratiBot.board.FastBoardMap;
import com.github.lunarolympian.TaratiBot.tardar.gametree.Preval;
import com.github.lunarolympian.TaratiBot.tardar.gametree.GameNode;

import java.io.File;
import java.io.IOException;

public class Tardar {

    public static final String startingBoard =
            ("!D3_NORMAL_BLACK!D4_NORMAL_BLACK!C8_NORMAL_BLACK!D1_NORMAL_WHITE!" +
                    "D2_NORMAL_WHITE!C1_NORMAL_WHITE!C2_NORMAL_WHITE!B4_NORMAL_BLACK").trim(); // .trim() because I copied from console.

    private GameNode tree;
    private BoardMap map;
    private File tardarFile;
    private NN nn;
    private Preval preval;

    public Tardar(BoardMap map) {
        this.map = map;
        this.nn = new NN();
        //this.tree = new SinkingTree(map, nn, false);
    }

    public Tardar(BoardMap map, File trdrFile) throws IOException, ClassNotFoundException {
        this.map = map;
        this.nn = new NN(trdrFile);
    }

    public void startGame(String board, boolean white) {
        this.map = new BoardMap(board, white);
    }

    public void tardarSetNN(NN nn) {
        this.nn = nn;
    }

    public FastBoardMap runNN(FastBoardMap map) throws InterruptedException {
        // Checks if it matches any branches on the sinking tree, then attempts to build another layer.
        GameNode tree = new GameNode(map, nn);
        FastBoardMap fbm = tree.getBestMove();

        tree = null;
        System.gc(); // It devours memory otherwise.
        return fbm;
    }

    /**
     * Loads a .trdr file. These files mostly contain the NN info and a lot of info on moves that have been taken note of before.
     * @param path The path to the file with the data.
     */
    public void tardarLoad(File path) throws IOException, ClassNotFoundException {
        this.nn = new NN(path);
    }

    /**
     * Saves/updates a .trdr file's NN.
     */
    public void tardarUpdateNN(File file) {

    }

    /**
     * Saves/updates a .trdr file's board DB.
     */
    public void tardarUpdateBoardDB(File file) {

    }

    /**
     * This is preferable to just assessing each move individually as it allows the bot to do some extra processing
     * in previous moves and just reuse the work.
     * @return The move to return to the JS code.
     */
    public String makeMove() {
        BoardMap[] possibleStates = map.getPossibleStates();
        BoardMap bestBoardState = map;
        int bestBoardStateScore = map.getBoardState()[0];
        for(BoardMap state : possibleStates) {
            // Subtracted from 8 because the returned states are inverted
            if(8 - state.getBoardState()[0] > bestBoardStateScore) {
                bestBoardState = state;
                bestBoardStateScore = 8 - state.getBoardState()[0];
            }
        }

        int[][] legalMoves = map.getValidMoves();
        int[] chosenMove = legalMoves[(int) Math.floor(Math.random() * legalMoves.length)];

        if(bestBoardState != map) chosenMove = bestBoardState.getPreviousMove();


        if(map.getInverted()) chosenMove = BoardUtils.invertMove(chosenMove);

        return BoardUtils.intToTile(chosenMove[0]) + " " + BoardUtils.intToTile(chosenMove[1]);
    }

    /**
     * Tardar can and will shoot itself in the foot if these don't exist.
     * @return How much the map should be marked down compared to the current version.
     */
    private double guardrails(BoardMap map) {
        return 0;
    }

    public void trainingArc() {
        // This just loops through all neurons and tries to find the optimal value for each.
        for(int s = 0; s < 3; s++) {
            float[] neurons = nn.getWeights(s);

            double latestAccuracy = playBot(1_000);
            System.out.println(latestAccuracy);

            for (int i = 0; i < neurons.length; i++) {
                float neuron = neurons[i];
                float starting = neuron;

                int direction = 1;
                float stepSize = 0.2f;

                // Takes a max of 10 steps, first a small one to see if it can make an impact
                for (int j = 0; j < 10; j++) {
                    if (j == 0) {
                        // First goal is to pinpoint the ideal direction to head in
                        neuron += 0.4f;
                        nn.updateWeight(s, i, neuron);
                        double positive = playBot(1000);

                        neuron = starting;
                        neuron -= 0.4f;

                        nn.updateWeight(s, i, neuron);
                        double negative = playBot(1000);

                        neuron = starting;

                        if(positive < negative) {
                            direction = -1;
                            if(negative > latestAccuracy) latestAccuracy = negative;
                            continue;
                        }
                        else if(positive == negative) break; // No point in messing with it
                        if(positive > latestAccuracy) latestAccuracy = positive;
                        continue;
                    }

                    // Updates the weight
                    neuron += (stepSize * direction);
                    //if (neuron > 1) neuron = 1f;
                    //else if (neuron < 0) neuron = 0;
                    nn.updateWeight(s, i, neuron);
                    double newAccuracy = playBot(1000);

                    // This can repeat for the remaining loops
                    if (latestAccuracy > newAccuracy) {
                        neuron -= stepSize;
                        stepSize /= 2; // We know this is an improvement, so if there's an extra attempt it will shrink the step and see if that helps
                        nn.updateWeight(s, i, neuron);
                        continue;
                    }
                    // It getting more accurate isn't relevant as it's already improving.

                    latestAccuracy = newAccuracy;
                }

                System.out.println(latestAccuracy);

            }
        }

    }

    private double playBot(int rounds) {
        return Gauntlet.simulateTardarGame(nn, rounds, false, false);
    }
}
