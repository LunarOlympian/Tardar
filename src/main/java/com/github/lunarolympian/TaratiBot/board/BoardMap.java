package com.github.lunarolympian.TaratiBot.board;

import java.util.*;

/**
 * Keeps track of the board state and allows it to be progressed and reverted
 */
public class BoardMap {

    // If a piece has a value >= 23 then it's a king at tile value - 23
    private int[] boardState = new int[]{4, // How many pieces white controls
            0, 1, 4, 5, // White piece positions
            2, 3, 10, 11 // Black piece positions
    };

    private Queue<BoardMap> mapHistory = new LinkedList<>();

    private boolean flipped = false;
    private Stack<int[]> moveHistory = new Stack<>();


    public BoardMap(String board, boolean white) {
        if(board.equalsIgnoreCase("new")) return;

        String[] boardArr = board.substring(1).split("!");
        // This just loops through for black and white and converts the pieces.
        int pos = 0;
        for(String piece : boardArr) {
            if(piece.contains("WHITE")) {
                boardState[pos + 1] = BoardUtils.tileToInt(piece.split("_")[0]) +
                        (piece.contains("NORMAL") ? 0 : 23);
                pos++;
            }
        }
        boardState[0] = pos;
        for(String piece : boardArr) {
            if(piece.contains("BLACK")) {
                boardState[pos + 1] = BoardUtils.tileToInt(piece.split("_")[0]) +
                        (piece.contains("NORMAL") ? 0 : 23);
                pos++;
            }
        }

        if(!white) invertBoard();
    }

    public BoardMap(int[] map) {
        this.boardState = map;
    }

    public BoardMap(FastBoardMap fbm, boolean flipped) {
        for(int i = 0; i < 9; i++) {
            this.boardState[i] = fbm.getGameState()[i];
            if(flipped) this.invertBoard();
        }
    }

    /**
     * Used to progress a board state.
     * @param currentMap The map to progress.
     * @param moveApplied The move to perform.
     */
    private BoardMap(BoardMap currentMap, int[] moveApplied) {
        // White pieces CANNOT go down this turn. Therefore, this performs the move and counts existing white pieces,
        // as well as black pieces bordering the tile moved to.
        int whitePieceCount = currentMap.boardState[0];
        ArrayList<Integer> flippedPieces = new ArrayList<>();
        ArrayList<Integer> remainingPieces = new ArrayList<>();
        int[] borderingTiles = BoardUtils.getNeighbouringSpaces((byte) moveApplied[1]);
        for(int i = currentMap.boardState[0] + 1; i < 9; i++) {

            // Searches the array for bordering tiles and increments the count if a black piece borders the white piece.
            boolean added = false;
            for(int tile : borderingTiles) {
                if(tile == currentMap.boardState[i] || tile == currentMap.boardState[i] + 23 ||
                        tile == currentMap.boardState[i] - 23) {
                    whitePieceCount++;
                    flippedPieces.add(currentMap.boardState[i]);
                    added = true;
                    break;
                }
            }
            if(!added) remainingPieces.add(currentMap.boardState[i]);

        }

        int[] newBoardState = new int[9];
        newBoardState[0] = whitePieceCount;
        // Adds in the previous white pieces (and updates the moved piece)
        for(int i = 1; i <= currentMap.boardState[0]; i++)
            // This considers a move a shift in value rather than an update. This allows kings to keep their nobility after a move.
            newBoardState[i] =
                    (currentMap.boardState[i] == moveApplied[0] || currentMap.boardState[i] == moveApplied[0] + 23 ||
                    currentMap.boardState[i] == moveApplied[0] - 23) ?
                            currentMap.boardState[i] + (moveApplied[1] - moveApplied[0]) : currentMap.boardState[i];

        // Now adds in the flipped pieces
        for(int i = currentMap.boardState[0] + 1;
            i < currentMap.boardState[0] + 1 + flippedPieces.size(); i++)
            newBoardState[i] = flippedPieces.get(i - (currentMap.boardState[0] + 1));

        // Adds in all remaining pieces
        for(int i = currentMap.boardState[0] + 1 + flippedPieces.size(); i < 9; i++)
            newBoardState[i] = remainingPieces.get(i - (currentMap.boardState[0] + 1 + flippedPieces.size()));

        this.boardState = newBoardState;

        // Promotes pieces that should be promoted
        for(int i = 1; i <= newBoardState[0]; i++) {
            if(newBoardState[i] == 10 || newBoardState[i] == 11 || newBoardState[i] == 2 || newBoardState[i] == 3) {
                newBoardState[i] += 23;
            }
        }

        flipped = !currentMap.flipped;

        invertBoard();
        moveHistory.add(moveApplied);

    }

    public void addPreviousState(BoardMap map) {
        mapHistory.add(map);
        if(mapHistory.size() > 10) mapHistory.remove();
    }

    public Queue<BoardMap> getMapHistory() {
        return new LinkedList<>(mapHistory); // Cloned to not cause problems with pointers
    }


    /**
     * Gets the valid moves for the current board state.
     * @return An array of valid moves for white.
     */
    public int[][] getValidMoves() {
        ArrayList<int[]> validMoves = new ArrayList<>();
        for(int i = 0; i < boardState[0]; i++) {

            int piece = boardState[i + 1];
            // Uses BoardUtils to check the valid tiles, then checks if any pieces are on them.
            // If none are then it's a valid move.
            // Note that it needs to convert kings to their proper tile location. This is done by subtracting 23.
            int[] possibleMoves = piece >= 23 ? BoardUtils.getNeighbouringSpaces((byte) (piece - 23)) : BoardUtils.getForwardSpaces((byte) piece);
            for(int move : possibleMoves) {
                boolean valid = true;
                for(int j = 1; j < boardState.length; j++) {
                    if(boardState[j] == move || boardState[j] - 23 == move || boardState[j] + 23 == move) {
                        valid = false;
                        break;
                    }
                }
                int pieceTile = piece >= 23 ? piece - 23 : piece;

                if(valid) validMoves.add(new int[]{pieceTile, move});
            }
        }

        int[][] validMovesReturn = new int[validMoves.size()][2];
        validMoves.toArray(validMovesReturn);

        return validMovesReturn;
    }

    /**
     * Gets the possible next states for the current board map. Please note, it only checks white, so inverting is still necessary.
     * @return An array of all possible next states.
     */
    public BoardMap[] getPossibleStates() {
        int[][] validMoves = getValidMoves();

        BoardMap[] possibleStates = new BoardMap[validMoves.length];
        int pos = 0;
        for(int[] move : validMoves) {
            possibleStates[pos] = new BoardMap(this, move);
            pos++;
        }

        return possibleStates;
    }

    /**
     * Checks if there's a winning move that can be made from this position. A lot more efficient than getting
     * all possible states.
     * @return Whether there's a winning move.
     */
    public boolean getWinningMove() {
        // First it checks if there's a move that can be made which will flip all remaining pieces for the opponent.

        for(int i = 1; i <= boardState[0]; i++) {
            // Checks if the move is valid
            //BoardUtils.getNeighbouringSpaces()
            for(int j = 1; j < 9; j++)
                if(boardState[i] == boardState[j]) continue; // Collision
        }
        return false; // Can't be bothered to do this rn. TODO do it!
    }

    /**
     * Checks if a piece is blocked from moving
     * @return Boolean representing if it's blocked.
     */
    public boolean checkBlockedPiece(int piece) {
        boolean containsPiece = false;
        int[] validMoves = piece > 22 ? BoardUtils.getNeighbouringSpaces((byte) (piece - 23)) :
                BoardUtils.getForwardSpaces((byte) piece);

        for(int i = 1; i < 9; i++) {
            if(boardState[i] == piece) containsPiece = true;
            // Now it checks if any pieces are on the tiles.
            for(int j = 0; j < validMoves.length; j++) {
                if(validMoves[j] == boardState[i]) validMoves[j] = -1;
            }
        }
        if(!containsPiece) return false;

        for(int validMove : validMoves) if(validMove != -1) return false;
        return true;
    }

    /**
     * Run after progressing the game state. This is mostly just used so that I don't have to write in logic for black.
     */
    public void invertBoard() {
        int[] updatedBoardState = new int[boardState.length];
        updatedBoardState[0] = 8 - boardState[0];
        for(int i = 1; i < boardState.length; i++) {

            int piece = boardState[i];
            int kingOffset = 0;
            if(piece > 22) {
                kingOffset = 23;
                piece -= 23;
            }
            // This is surprisingly annoying to do without hard coding :/
            updatedBoardState[9 - i] = switch (piece) {
                case 0, 1, 2, 3:
                    yield ((piece + 2) % 4) + kingOffset;
                default:
                    if(piece < 16)
                        yield ((piece + 2) % 12) + 4 + kingOffset; // The + 2 is because - 4 ignores the D tiles, then +6 inverts the C tiles.
                    else if(piece < 22)
                        yield ((piece - 13) % 6) + 16 + kingOffset; // Same as above, but +3 inverts the B tiles
                    else yield 22 + kingOffset;
            };
        }
        boardState = updatedBoardState;
        flipped = !flipped;
    }

    public boolean getInverted() {
        return flipped;
    }

    public int[] getBoardState() {
        return boardState;
    }

    public int[] getPreviousMove() {
        return moveHistory.peek();
    }
}
