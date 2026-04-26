package com.github.lunarolympian.TaratiBot.tardar;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.board.FastBoardMap;
import com.github.lunarolympian.TaratiBot.tardar.gametree.GameNode;

public class Tardar {

    private BoardMap map;

    public Tardar(BoardMap map) {
        this.map = map;
    }

    public FastBoardMap runTardar(FastBoardMap map, Tardar.Difficulty difficulty) {
        // Checks if it matches any branches on the sinking tree, then attempts to build another layer.
        GameNode tree = new GameNode(map);
        return tree.getBestMove(difficulty);
    }

    public static enum Difficulty {
        EASY,
        MEDIUM,
        HARD,
        SHORT_SEARCH,
        EXPERT,
        AGI
    }
}
