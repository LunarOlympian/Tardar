package com.github.lunarolympian.TaratiBot.board;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The same as BoardMap, but lighter weight and just designed to be used for building game state trees.
 */
public class FastBoardMap {

    private byte[] boardState = new byte[9];
    private byte[] previousMove;

    public FastBoardMap(BoardMap boardMap) {
        for(int i = 0; i < 9; i++) {
            boardState[i] = (byte) boardMap.getBoardState()[i];
        }
    }

    public FastBoardMap(byte[] boardState) {
        this.boardState = boardState;
    }

    public FastBoardMap(byte[] boardState, byte[] move) {
        this.boardState = boardState;
        this.previousMove = move;
    }

    /**
     * Just for testing
     */
    public static void main(String[] args) {
        FastBoardMap fbm = new FastBoardMap(new byte[]{
                4,
                0, 1, 4, 5,
                2, 3, 10, 11
        });
        var fullTurn = fbm.getFullTurn();
    }

    /**
     * Full turn is just each opponent making a move. It checks the possible moves that could be made this turn,
     * the possible moves its opponent could make, and returns the resulting board states.
     */
    public Map<FastBoardMap, ArrayList<FastBoardMap>> getFullTurn() {
        Map<FastBoardMap, ArrayList<FastBoardMap>> fullTurns = new HashMap<>();

        ArrayList<FastBoardMap> halfTurn = getHalfTurn();
        halfTurn.forEach(ht -> fullTurns.put(ht, FastBoardMap.getOpponentMoves(ht.boardState)));

        return fullTurns;
    }

    /**
     * Checks if a player has won.
     * @return I'm not 100% confident, but I don't think there are any edge cases where a player
     * can make a move and lose the game that turn.
     */
    public boolean assessEndState(boolean checkSelf) {
        // Checks the state of the pieces the specified player can make.
        if(checkSelf) {
            // First checks the number of pieces it has
            if(boardState[0] == 0) return true;

            // Now it needs to check if it can make any moves.
            byte[] occupiedMap = new byte[23];
            for(int i = 1; i < 9; i++) {
                int pieceTile = boardState[i] > 22 ? boardState[i] - 23 : boardState[i];
                occupiedMap[pieceTile] = 1;
            }

            // Goes through the pieces in a board state and for each piece checks which tiles are valid moves.
            boolean validMove = false;
            for(int i = 1; i <= boardState[0]; i++) {
                byte space = boardState[i] > 22 ? (byte) (boardState[i] - 23) : boardState[i];

                // I know this is a mess...
                byte[] validTilesArr = boardState[i] > 22 ? BoardUtils.getNeighbouringSpacesByte(space) :
                        (checkSelf ? BoardUtils.getForwardSpacesByte(space) : BoardUtils.getBackwardSpaces(space));
                for (int validTile : validTilesArr) {
                    if (occupiedMap[validTile] == 0) {
                        validMove = true; // A move can be made
                        break;
                    }
                }
                if(validMove) break;
            }

            return !validMove;
        }

        // First checks the number of pieces it has
        if(boardState[0] == 8) return true;

        // Now it needs to check if it can make any moves.
        byte[] occupiedMap = new byte[23];
        for(int i = 1; i < 9; i++) {
            int pieceTile = boardState[i] > 22 ? boardState[i] - 23 : boardState[i];
            occupiedMap[pieceTile] = 1;
        }

        // Goes through the pieces in a board state and for each piece checks which tiles are valid moves.
        boolean validMove = false;
        for(int i = boardState[0] + 1; i < 9; i++) {
            byte space = boardState[i] > 22 ? (byte) (boardState[i] - 23) : boardState[i];

            byte[] validTilesArr = boardState[i] > 22 ? BoardUtils.getNeighbouringSpacesByte(space) : BoardUtils.getBackwardSpaces(space);
            for (int validTile : validTilesArr) {
                if (occupiedMap[validTile] == 0) {
                    validMove = true; // A move can be made
                    break;
                }
            }
            if(validMove) break;
        }

        return !validMove;
    }

    /**
     * Gets the possible board states for the opponent to receive.
     * @return An AL of the board states.
     */
    private ArrayList<FastBoardMap> getHalfTurn() {
        ArrayList<FastBoardMap> validMoves = new ArrayList<>();

        // If I'm not mistaken this is a bit more efficient than looping through the array and checking that a tile isn't occupied.
        byte[] occupiedMap = new byte[23];
        for(int i = 1; i < 9; i++) {
            int pieceTile = boardState[i] > 22 ? boardState[i] - 23 : boardState[i];
            occupiedMap[pieceTile] = 1;
        }

        // Goes through the pieces in a board state and for each piece checks which tiles are valid moves.
        for(int i = 1; i <= boardState[0]; i++) {
            byte space = boardState[i] > 22 ? (byte) (boardState[i] - 23) : boardState[i];

            int[] validTilesArr = boardState[i] > 22 ? BoardUtils.getNeighbouringSpaces(space) : BoardUtils.getForwardSpaces(space);
            for(int validTile : validTilesArr) {
                if(occupiedMap[validTile] != 0) continue;

                // Cool, it's valid, now it needs to make the new board
                byte[] newBoard = new byte[9];
                newBoard[0] = boardState[0];

                // Moves the piece
                for(int j = 1; j <= boardState[0]; j++) {
                    if(j == i) {
                        newBoard[j] = (byte) (validTile + (boardState[i] > 22 ? 23 : 0));
                        if((validTile == 10 || validTile == 11) && newBoard[j] < 23) {
                            newBoard[j] += 23; // Promotions from movement
                        }
                    }
                    else
                        newBoard[j] = boardState[j];
                }


                // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
                // Capturing
                // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_


                int[] capturingSpaces = BoardUtils.getNeighbouringSpaces((byte) validTile);

                int piecesCaptured = 0;
                int backwardsFillInPos = 8;
                // Captures enemy pieces
                for(int j = boardState[0] + 1; j < 9; j++) {
                    boolean captured = false;
                    for (int capturingSpace : capturingSpaces) {
                        if (boardState[j] == capturingSpace || boardState[j] - 23 == capturingSpace) {
                            newBoard[newBoard[0] + piecesCaptured + 1] = boardState[j];
                            if((boardState[j] == 2 || boardState[j] == 3 || boardState[j] == 10 || boardState[j] == 11)
                                    && boardState[j] < 23)
                            {
                                newBoard[newBoard[0] + 1 + piecesCaptured] += 23;
                            }
                            piecesCaptured++;
                            captured = true;
                            break;
                        }
                    }

                    // This fills in the pieces that haven't been captured.
                    if(!captured) {
                        newBoard[backwardsFillInPos] = boardState[j];
                        backwardsFillInPos--;
                    }
                }
                newBoard[0] += (byte) piecesCaptured;

                // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

                validMoves.add(new FastBoardMap(newBoard, new byte[]{boardState[i], (byte) validTile}));

            }
        }

        return validMoves;
    }

    private static ArrayList<FastBoardMap> getOpponentMoves(byte[] boardState) {
        ArrayList<FastBoardMap> validMoves = new ArrayList<>();

        // If I'm not mistaken this is a bit more efficient than looping through the array and checking that a tile isn't occupied.
        byte[] occupiedMap = new byte[23];
        for(int i = 1; i < 9; i++) {
            int pieceTile = boardState[i] > 22 ? boardState[i] - 23 : boardState[i];
            occupiedMap[pieceTile] = 1;
        }

        // Goes through the pieces in a board state and for each piece checks which tiles are valid moves.
        for(int i = boardState[0] + 1; i < 9; i++) {
            byte space = boardState[i] > 22 ? (byte) (boardState[i] - 23) : boardState[i];

            byte[] validTilesArr = boardState[i] > 22 ? BoardUtils.getNeighbouringSpacesByte(space) : BoardUtils.getBackwardSpaces(space);
            for(int validTile : validTilesArr) {
                if(occupiedMap[validTile] != 0) continue;

                // Cool, it's valid, now it needs to make the new board
                byte[] newBoard = new byte[9];
                newBoard[0] = boardState[0];

                // Moves the piece
                for(int j = boardState[0] + 1; j < 9; j++) {
                    if(j == i) {
                        newBoard[j] = (byte) (validTile + (boardState[i] > 22 ? 23 : 0));
                        if((validTile == 4 || validTile == 5) && newBoard[j] < 23)
                            newBoard[j] += 23; // Promotions from movement
                    }
                    else
                        newBoard[j] = boardState[j];
                }


                // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
                // Capturing
                // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_


                int[] capturingSpaces = BoardUtils.getNeighbouringSpaces((byte) validTile);

                int piecesCaptured = 0;
                int forwardsFillInPos = 1;
                for(int j = 1; j <= boardState[0]; j++) {
                    boolean captured = false;
                    // Checks if any of white's pieces are on a tile black is capturing
                    for (int capturingSpace : capturingSpaces) {
                        // This is very similar to the friendly one, but it's more or less reversed
                        if (boardState[j] == capturingSpace || boardState[j] - 23 == capturingSpace) {
                            newBoard[newBoard[0] - piecesCaptured] = boardState[j];
                            if(boardState[j] == 0 || boardState[j] == 1 ||
                                    boardState[j] == 4 || boardState[j] == 5)
                            {
                                newBoard[newBoard[0] - piecesCaptured] += 23;
                            }
                            piecesCaptured++;
                            captured = true;
                            break;
                        }
                    }

                    // This fills in the pieces that haven't been captured.
                    if(!captured) {
                        newBoard[forwardsFillInPos] = boardState[j];
                        forwardsFillInPos++;
                    }
                }
                newBoard[0] -= (byte) piecesCaptured;

                // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

                validMoves.add(new FastBoardMap(newBoard));

            }
        }

        return validMoves;
    }

    public byte[] getGameState() {
        return this.boardState;
    }

    public byte[] getPreviousMove() {
        return previousMove;
    }
}
