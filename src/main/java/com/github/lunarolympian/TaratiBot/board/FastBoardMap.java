package com.github.lunarolympian.TaratiBot.board;

import java.util.*;

/**
 * The same as BoardMap, but lighter weight and just designed to be used for building game state trees.
 */
public class FastBoardMap {

    private byte[] boardState = new byte[9];
    private byte[] previousMove = new byte[0];
    private byte[] previousOpponentMove = new byte[0];
    private byte[] flags = new byte[3]; // 0 - End state

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

    public void setPreviousOpponentMove(byte[] previousOpponentMove) {
        this.previousOpponentMove = previousOpponentMove;
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
        var fullTurn = fbm.getFullTurn(true);
    }

    /**
     * Full turn is just each opponent making a move. It checks the possible moves that could be made this turn,
     * the possible moves its opponent could make, and returns the resulting board states.
     * @param limitedChecks Toggles the setting where some moves are ignored as being obviously bad.
     */
    public LinkedHashMap<FastBoardMap, ArrayList<FastBoardMap>> getFullTurn(boolean limitedChecks, byte[]... restrictedMoves) {
        LinkedHashMap<FastBoardMap, ArrayList<FastBoardMap>> fullTurns = new LinkedHashMap<>();

        ArrayList<FastBoardMap> halfTurns = getHalfTurn(limitedChecks, restrictedMoves);

        halfTurns.forEach(ht -> fullTurns.put(ht, this.getOpponentMoves(ht, limitedChecks)));

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
     * @param limitedChecks Toggles the mode where some board states are ignored as being obviously bad. Aims for half of all branches being pruned.
     * @return An AL of the board states.
     */
    private ArrayList<FastBoardMap> getHalfTurn(boolean limitedChecks, byte[][] restrictedMoves) {
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

                // This handles getting the rocks to more relevant tiles (stalling is the bane of my existence)
                // Only if limited checks are enabled though
                if(limitedChecks && boardState[i] > 22) {
                    if(validTile <= 3 && (space <= 3)) continue; // Handles spaces 0-3
                    // Consider adding more in
                }

                // Confirms it's not a restricted move (stop fucking moving back and forth!)
                boolean restricted = false;
                for(byte[] restrictedMove : restrictedMoves) {
                    if(restrictedMove[0] == validTile && restrictedMove[1] == space) {
                        restricted = true;
                        break;
                    }
                }

                if(restricted) continue;

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

                // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
                // Capturing
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

                if(limitedChecks && !limitedCheck(newBoard)) continue;

                validMoves.add(new FastBoardMap(newBoard, new byte[]{boardState[i] <= 22 ? boardState[i] : (byte) (boardState[i] - 23), (byte) validTile}));

            }
        }

        return validMoves;
    }

    private ArrayList<FastBoardMap> getOpponentMoves(FastBoardMap boardState, boolean limitedChecks) {
        ArrayList<FastBoardMap> validMoves = new ArrayList<>();

        // If I'm not mistaken this is a bit more efficient than looping through the array and checking that a tile isn't occupied.
        byte[] occupiedMap = new byte[23];
        for(int i = 1; i < 9; i++) {
            int pieceTile = boardState.boardState[i] > 22 ? boardState.boardState[i] - 23 : boardState.boardState[i];
            occupiedMap[pieceTile] = 1;
        }

        // Goes through the pieces in a board state and for each piece checks which tiles are valid moves.
        for(int i = boardState.boardState[0] + 1; i < 9; i++) {
            byte space = boardState.boardState[i] > 22 ? (byte) (boardState.boardState[i] - 23) : boardState.boardState[i];

            byte[] validTilesArr = boardState.boardState[i] > 22 ? BoardUtils.getNeighbouringSpacesByte(space) : BoardUtils.getBackwardSpaces(space);
            for(byte validTile : validTilesArr) {
                if(occupiedMap[validTile] != 0) continue;

                /*
                This has a few things to check (optimisation). Namely, it needs to weed out:
                - Stall tactics
                - Moving pieces into the 4 disconnected tiles
                */
                if(limitedChecks && ((validTile <= 3) ||
                        (previousOpponentMove.length > 0 &&
                        space == previousOpponentMove[1] && validTile == previousOpponentMove[0]))) continue;

                // Cool, it's valid, now it needs to make the new board
                byte[] newBoard = new byte[9];
                newBoard[0] = boardState.boardState[0];

                // Moves the piece
                for(int j = boardState.boardState[0] + 1; j < 9; j++) {
                    if(j == i) {
                        newBoard[j] = (byte) (validTile + (boardState.boardState[i] > 22 ? 23 : 0));
                        if((validTile == 4 || validTile == 5) && newBoard[j] < 23)
                            newBoard[j] += 23; // Promotions from movement
                    }
                    else
                        newBoard[j] = boardState.boardState[j];
                }


                // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
                // Capturing
                // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
                int[] capturingSpaces = BoardUtils.getNeighbouringSpaces((byte) validTile);

                int piecesCaptured = 0;
                int forwardsFillInPos = 1;
                for(int j = 1; j <= boardState.boardState[0]; j++) {
                    boolean captured = false;
                    // Checks if any of white's pieces are on a tile black is capturing
                    for (int capturingSpace : capturingSpaces) {
                        // This is very similar to the friendly one, but it's more or less reversed
                        if (boardState.boardState[j] == capturingSpace || boardState.boardState[j] - 23 == capturingSpace) {
                            newBoard[newBoard[0] - piecesCaptured] = boardState.boardState[j];
                            if(boardState.boardState[j] == 0 || boardState.boardState[j] == 1 ||
                                    boardState.boardState[j] == 4 || boardState.boardState[j] == 5)
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
                        newBoard[forwardsFillInPos] = boardState.boardState[j];
                        forwardsFillInPos++;
                    }
                }
                newBoard[0] -= (byte) piecesCaptured;

                // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
                FastBoardMap opponentMove = new FastBoardMap(newBoard, boardState.getPreviousMove());
                opponentMove.setPreviousOpponentMove(new byte[]{space, validTile});
                validMoves.add(opponentMove);



            }
        }

        return validMoves;
    }

    /**
     * This checks for:
     * <ul>
     *     <li>Moves which lose pieces with a score >= 4 (normal pieces are 1, rocks are 2)</li>
     * </ul>
     * @param pieces The move which is being compared to the current move.
     * @return Whether the move should be expanded.
     */
    private boolean limitedCheck(byte[] pieces) {
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // Needs to check if there's a move the opponent can make which claims at least 3 material

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // First it gets the spots the pieces border

        // First is tile, second is pieces
        TreeMap<Byte, ArrayList<Byte>> pieceTileBorders = new TreeMap<>();
        for(int i = 1; i <= pieces[0]; i++) {
            if(pieces[i] <= 22) {
                for(byte tile : BoardUtils.getForwardSpacesByte(pieces[i])) {
                    ArrayList<Byte> existingPieces = pieceTileBorders.getOrDefault(tile, new ArrayList<>());
                    existingPieces.add(pieces[i]);
                    pieceTileBorders.put(tile, existingPieces);
                }
            }
            else {
                for(byte tile : BoardUtils.getNeighbouringSpacesByte((byte) (pieces[i] - 23))) {
                    ArrayList<Byte> existingPieces = pieceTileBorders.getOrDefault(tile, new ArrayList<>());
                    existingPieces.add(pieces[i]);
                    pieceTileBorders.put(tile, existingPieces);
                }
            }
        }

        TreeSet<Byte> problemChildren = new TreeSet<>();

        // Checks if pieces with a value of at least 4 border the same tile
        ArrayList<Byte> tiles = new ArrayList<>(pieceTileBorders.keySet());
        for(Byte tile : tiles) {
            ArrayList<Byte> borderingPieces = pieceTileBorders.get(tile);
            if(borderingPieces.size() == 1) continue;
            int scoreTotal = 0;
            for(Byte piece : borderingPieces) {
                if(piece <= 22) scoreTotal++;
                else scoreTotal += 2;
            }

            if(scoreTotal >= 3) problemChildren.add(tile);
        }

        // Now it needs to check if there are any pieces on the tiles that can cause problems
        for(int i = 1; i < 9; i++) problemChildren.remove(pieces[i]);

        // Finally it checks if any pieces can move to those tiles
        for(int i = pieces[0] + 1; i < 9; i++) {
            if(pieces[i] <= 22) {
                for(Byte opponentMoveTile : BoardUtils.getBackwardSpaces(pieces[i]))
                    if(problemChildren.contains(opponentMoveTile)) return false;
            }
            else {
                for(Byte opponentMoveTile : BoardUtils.getNeighbouringSpacesByte((byte) (pieces[i] - 23)))
                    if(problemChildren.contains(opponentMoveTile)) return false;
            }
        }

        return true;
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // Now it checks if any pieces are moving away from tiles guarded by another piece

        // First it checks all tiles
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
    }

    public byte[] getGameState() {
        return this.boardState;
    }

    public byte[] getPreviousMove() {
        return previousMove;
    }

    public void setFlag(int flag, byte value) {
        flags[flag] = value;
    }

    public byte[] getFlags() {
        return flags;
    }
}
