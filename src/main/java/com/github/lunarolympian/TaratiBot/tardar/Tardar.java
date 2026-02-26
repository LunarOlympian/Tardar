package com.github.lunarolympian.TaratiBot.tardar;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.board.BoardUtils;
import com.github.lunarolympian.TaratiBot.board.FastBoardMap;
import com.github.lunarolympian.TaratiBot.tardar.gametree.GameNode;
import com.github.lunarolympian.TaratiBot.training.TardarNN;

import java.io.File;
import java.io.IOException;

public class Tardar {

    public static final String startingBoard =
            ("!D3_NORMAL_BLACK!D4_NORMAL_BLACK!C8_NORMAL_BLACK!D1_NORMAL_WHITE!" +
                    "D2_NORMAL_WHITE!C1_NORMAL_WHITE!C2_NORMAL_WHITE!B4_NORMAL_BLACK").trim(); // .trim() because I copied from console.

    private BoardMap map;
    private TardarNN tardarNN;

    public Tardar(BoardMap map) {
        this.map = map;
        //this.tree = new SinkingTree(map, nn, false);
    }

    public Tardar(BoardMap map, File trdrFile) throws IOException {
        this.map = map;
        //this.tardarNN = new TardarNN(trdrFile);
    }

    public void startGame(String board, boolean white) {
        this.map = new BoardMap(board, white);
    }

    public void tardarSetNN(TardarNN nn) {
        this.tardarNN = nn;
    }

    public FastBoardMap runNN(FastBoardMap map, Tardar.Difficulty difficulty) {
        // Checks if it matches any branches on the sinking tree, then attempts to build another layer.
        GameNode tree = new GameNode(map);
        return tree.getBestMove(difficulty);
    }

    /**
     * Loads a .trdr file. These files mostly contain the NN info and a lot of info on moves that have been taken note of before.
     * @param path The path to the file with the data.
     */
    public void tardarLoad(File path) throws IOException {
        this.tardarNN = new TardarNN(path);
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

    private double playBot(int rounds) {
        return 0d;
    }

    public static enum Difficulty {
        EASY,
        MEDIUM,
        HARD,
        EXPERT,
        AGI
    }
}
