package com.github.lunarolympian.TaratiBot.training;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.board.BoardUtils;

import java.util.HashSet;

public class TestBot {

    public static double score(BoardMap map) {
        // First it checks if this state defeats the opponent
        if(map.getValidMoves().length == 0) return 1000; // Defeats the opponent, so excellent move, no notes.

        // Needs to check if this state directly leads to losing
        for(BoardMap possibleState : map.getPossibleStates()) {
            // The state passed in is inverted already, so no need to invert again.
            if(possibleState.getValidMoves().length == 0) return -1000; // Well you lose, so it's pretty terrible.
        }

        // Now that it rules out the great/horrible moves, here's where it checks other moves.
        double score = 0;

        score += ((double) map.getPossibleStates().length / 4);

        // Needs to check how many of its opponent's pieces are blocked (done first to avoid inverting too much)
        for(int i =  1; i <= map.getBoardState()[0]; i++) {
            if(map.checkBlockedPiece(map.getBoardState()[i])) score += 0.5;
        }
        HashSet<Integer> unsafeTiles = getUnsafeTiles(map); // Helps later on.

        int pawns, kings;
        pawns = kings = 0;
        map.invertBoard();
        // Simple score for pawns and kings
        for(int i = 1; i <= map.getBoardState()[0]; i++) {
            score++;
            if(map.getBoardState()[i] > 22) {
                score++;
                kings++;
            }
            else pawns++;
        }

        // Then it builds a map of "safe tiles" (tiles pieces can move to and reclaim a potentially lost piece with minimal risk)
        HashSet<Integer> safeTiles = getSafeTiles(map);

        // If a piece is on a safe tile then it gets a bit higher score.
        for(int i = 1; i <= map.getBoardState()[0]; i++)
            if(safeTiles.contains(map.getBoardState()[i]) ||
                    safeTiles.contains(map.getBoardState()[i] - 23)) {
                score += 0.5;
            }

        if(unsafeTiles.contains(BoardUtils.invertTile(map.getPreviousMove()[1])) &&
                safeTiles.contains(map.getPreviousMove()[1])) {
            score -= 5; // Stop throwing away pieces!
        }



        // This handles checking future board states, so how risky this move is
        double highestScoreLoss = 0;
        map.invertBoard();

        score -= (double) map.getPossibleStates().length / 4; // Tries to restrict its opponent's movements

        for(BoardMap possibleState : map.getPossibleStates()) {
            int pPawns, pKings;
            pPawns = pKings = 0;
            // Checks if any pieces can be claimed
            for(int i = 1; i <= possibleState.getBoardState()[0]; i++) {
                // If the movement of a piece is restricted then it ignores the piece when scoring.
                if(possibleState.getBoardState()[i] > 22) pKings++;
                else pPawns++;
            }

            double scoreLoss = (pawns - pPawns) + ((kings * 2) - (pKings * 2));

            // Inverts to check how restricted this move makes its own options
            possibleState.invertBoard();
            // Gains 0.15 points for each possible move
            scoreLoss += ((double) possibleState.getValidMoves().length * 0.15);

            // Really quickly checks if this move implies a piece is just being thrown away
            /*for(int i = 1; i <= possibleState.getBoardState()[0]; i++) {
                if((possibleState.getBoardState()[i] == BoardUtils.invertTile(map.getPreviousMove()[1]) ||
                        possibleState.getBoardState()[i] - 23 == BoardUtils.invertTile(map.getPreviousMove()[1])) &&
                        !safeTiles.contains(BoardUtils.invertTile(map.getPreviousMove()[1])))
                {
                    scoreLoss += 5; // Don't just throw pieces away!
                }
            }*/


            if(highestScoreLoss < scoreLoss) highestScoreLoss = scoreLoss;
        }

        score -= highestScoreLoss;

        return score;

    }

    private static HashSet<Integer> getSafeTiles(BoardMap map) {
        HashSet<Integer> safeTiles = new HashSet<>();
        for(int i = map.getBoardState()[0] + 1; i < 8; i++) {
            int[] possibleMoves = BoardUtils.invertTile(map.getBoardState()[i]) > 22 ?
                    BoardUtils.getNeighbouringSpaces((byte) (BoardUtils.invertTile(map.getBoardState()[i]) - 23)) :
                    BoardUtils.getForwardSpaces((byte) BoardUtils.invertTile(map.getBoardState()[i]));

            for(int pm : possibleMoves) {
                for(int ns : BoardUtils.getNeighbouringSpaces((byte) pm)) safeTiles.add(ns);
            }
        }

        // Removes safe tiles that have pieces on them.
        for(int i = 1; i <= map.getBoardState()[0]; i++) {
            safeTiles.remove(map.getBoardState()[0]);
            safeTiles.remove(map.getBoardState()[0] - 23);
        }
        return safeTiles;
    }

    private static HashSet<Integer> getUnsafeTiles(BoardMap map) {
        HashSet<Integer> safeTiles = new HashSet<>();
        for(BoardMap possibleMove : map.getPossibleStates()) { // Yes, this is called a lot, shhhhhhhh
            // Adds tiles adjacent to a possible move to the safeTiles list
            for(int neighbouringSpace : BoardUtils.getNeighbouringSpaces((byte) possibleMove.getPreviousMove()[1]))
                safeTiles.add(neighbouringSpace);
        }

        // Removes safe tiles that have pieces on them.
        for(int i = 1; i <= map.getBoardState()[0]; i++) {
            safeTiles.remove(map.getBoardState()[0]);
            safeTiles.remove(map.getBoardState()[0] - 23);
        }
        return safeTiles;
    }
}
