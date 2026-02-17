package com.github.lunarolympian.TaratiBot.board;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bunch of stuff for processing the board state, like getting legal moves and
 * flattening a branch (making all mirrors of it have the same data) to prevent duplicates.
 */
public abstract class BoardUtils {

    // Board structure can be increased in length as the tree is storing changes made to the board, not boards themselves.
    // That being said, for pre-computed boards a smaller version is necessary.
    public static final byte[] startingBoard = new byte[]{0x00, 0x00, 0x10, 0x20, 0x00, 0x04, 0x03};

    /**
     * Checks legal moves. Used getting branches from root layers.
     *
     * @param root The root it should get the branches of.
     * @return An array of the branches from the root.
     */
    public static byte[][] branch(int[] root) {
        return null;
    }

    /**
     * Modifies the board to perform a move. This is more efficient than saving each board in memory.
     *
     * @return The next board.
     */
    public static byte[] applyMove(byte[] board, byte move) {
        return null;
    }

    /**
     * Reverts a move applied to the board. Used to backtrack when navigating the tree.
     *
     * @return The previous board.
     */
    public static byte[] revertMove(byte[] board, byte move) {
        return null;
    }

    /**
     * Makes moves as the other player (just flips the board and piece colours) and makes all their possible moves.
     *
     * @param branch The branch it wants to... well branch from.
     * @return The possible roots. This may be used later on as the starting point for a tree.
     */
    public static byte[][] getRoots(byte[] branch) {
        return null;
    }

    public static String convertMoveByte(byte move) {
        return null;
    }

    /**
     * Gets the legal moves for a specific piece.
     *
     * @param map   The board.
     * @param piece A friendly piece to get the moves for. ESSENTIAL THAT IT'S FRIENDLY.
     * @return An array of valid moves.
     */
    public static Integer[] getLegalMoves(BoardMap map, int piece) {
        // This just checks if there's a piece on each tile it can move to
        int pieceTile = piece > 22 ? piece - 22 : piece;
        ArrayList<Integer> possibleMoves = new ArrayList<>();
        for (int possibleMove : (piece > 22 ? BoardUtils.getNeighbouringSpaces((byte) pieceTile) :
                BoardUtils.getForwardSpaces((byte) pieceTile)))
            possibleMoves.add(possibleMove);

        for (int i = 1; i < 9; i++) {
            int occupiedTile = map.getBoardState()[i];
            if (occupiedTile > 22) occupiedTile -= 22;

            int finalOccupiedTile = occupiedTile; // IntelliJ made me do it!!!
            possibleMoves.removeIf((val) -> (val == finalOccupiedTile || val + 22 == finalOccupiedTile));
        }

        Integer[] legalMoves = new Integer[possibleMoves.size()]; // Stupid primitive rules...
        possibleMoves.toArray(legalMoves);
        return legalMoves;
    }

    public static int tileToInt(String tile) {
        String trimmedTile = tile.trim();
        if (trimmedTile.startsWith("D")) return Integer.parseInt(trimmedTile.substring(1)) - 1;
        else if (trimmedTile.startsWith("C")) return Integer.parseInt(trimmedTile.substring(1)) + 3;
        else if (trimmedTile.startsWith("B")) {
            return switch (Integer.parseInt(trimmedTile.substring(1))) {
                case 1 -> 16;
                case 2 -> 21;
                case 3 -> 20;
                case 4 -> 19;
                case 5 -> 18;
                default -> 17;
            };
        }
        return 22;
    }

    public static String intToTile(int tile) {
        if (tile < 4) return "D" + (tile + 1);
        else if (tile < 16) return "C" + (tile - 3);
            // Annoyingly the B row is reversed (why even) so I need to do this as I didn't notice until it was too late
        else if (tile < 22) {
            return "B" + switch (tile) {
                case 16 -> 1;
                case 17 -> 6;
                case 18 -> 5;
                case 19 -> 4;
                case 20 -> 3;
                default -> 2;
            };
        }
        return "A1";
    }


    /**
     * Finds the spaces that border a specified space. d1 = 0, d2 = 1... c1 = 4, c2 = 5, etc.
     *
     * @param space The space to return the bordering spaces for.
     * @return An array of the spaces bordering the specified space.
     */
    public static int[] getNeighbouringSpaces(byte space) {
        /*
        This basically follows a few rules. C spaces (4-15) and B spaces (16-21) border +1 and -1 in their respective loops.
        C spaces border B spaces of ceil(n/2). B spaces border n * 2 and (n * 2) - 1.
        */
        return switch (space) {
            case 0, 1, 2, 3: // D tiles
                yield switch (space) {
                    case 0 -> new int[]{1, 4};
                    case 1 -> new int[]{0, 5};
                    case 2 -> new int[]{3, 10};
                    case 3 -> new int[]{2, 11};
                    default -> // Oh my fucking god IntelliJ, SHUT UP
                            null;
                };

            case 4, 5, 10, 11: // C tiles bordering D tiles.
                yield switch (space) {
                    case 4 -> new int[]{0, 5, 15, 16};
                    case 5 -> new int[]{1, 4, 6, 16};
                    case 10 -> new int[]{2, 9, 11, 19};
                    case 11 -> new int[]{3, 10, 12, 19};
                    default -> null;// Oh my fucking god IntelliJ, SHUT UP

                };
            case 16, 17, 18, 19, 20, 21: // B tiles
            {
                int[] borderingTiles = new int[5];
                borderingTiles[0] = ((space - 16) * 2) + 4; // Calculates 1st bordering C tile.
                borderingTiles[1] = ((space - 16) * 2) + 5; // Calculates 2nd bordering C tile.
                borderingTiles[2] = space == 21 ? 16 : space + 1;
                borderingTiles[3] = space == 16 ? 21 : space - 1;
                borderingTiles[4] = 22;
                yield borderingTiles;
            }

            case 22: // A1
                yield new int[]{16, 17, 18, 19, 20, 21};

            default: // Remaining C tiles.
                int[] borderingTiles = new int[3];
                borderingTiles[0] = space == 15 ? 4 : space + 1; // +1 C tile.
                borderingTiles[1] = space - 1;

                // TODO double check this works.
                // Calculates the bordering B tile.
                borderingTiles[2] = (((space - 4) - (space % 2)) / 2) + 16;
                yield borderingTiles;
        };
    }

    /**
     * This is the same as getNeighbouringSpaces, but I just removed the invalid movement options.
     */
    public static int[] getForwardSpaces(byte space) {
        return switch (space) {
            case 0, 1, 2, 3: // D tiles
                yield switch (space) {
                    case 0 -> new int[]{4};
                    case 1 -> new int[]{5};
                    default -> // Oh my fucking god IntelliJ, SHUT UP
                            null;
                };

            case 4, 5: // C tiles bordering D tiles.
                yield switch (space) {
                    case 4 -> new int[]{15, 16};
                    case 5 -> new int[]{6, 16};
                    default -> null;// Oh my fucking god IntelliJ, SHUT UP

                };

                // B tiles? More like these are going to B my undoing!
            case 16: // 16 is the only B tile with 3 connections
                yield new int[]{17, 21, 22};
            case 17, 18: // + 1 squad
                yield new int[]{(byte) (space + 1), (byte) (space == 17 ? 22 : 9)};
            case 20, 21: // - 1 squad
                yield new int[]{(byte) (space - 1), (byte) (space == 21 ? 22 : 12)};
            case 19: // 19 is special
                yield new int[]{10, 11};

            case 22: // A1
                yield new int[]{18, 19, 20};

            case 6, 15: // C tiles with 2 forward connections
                yield space == 6 ? new int[]{7, 17} : new int[]{14, 21};

                // C tiles where you can only advance forward
            case 7, 8, 9:
                yield new int[]{(byte) (space + 1)};
            case 12, 13, 14:
                yield new int[]{(byte) (space - 1)};


            default:
                System.out.println("Apparently I forgot to consider " + space);
                yield null;
        };
    }

    public static byte[] getForwardSpacesByte(byte space) {
        return switch (space) {
            case 0, 1, 2, 3: // D tiles
                yield switch (space) {
                    case 0 -> new byte[]{4};
                    case 1 -> new byte[]{5};
                    default -> // Oh my fucking god IntelliJ, SHUT UP
                            null;
                };

            case 4, 5: // C tiles bordering D tiles.
                yield switch (space) {
                    case 4 -> new byte[]{15, 16};
                    case 5 -> new byte[]{6, 16};
                    default -> null;// Oh my fucking god IntelliJ, SHUT UP

                };

                // B tiles? More like these are going to B my undoing!
            case 16: // 16 is the only B tile with 3 connections
                yield new byte[]{17, 21, 22};
            case 17, 18: // + 1 squad
                yield new byte[]{(byte) (space + 1), (byte) (space == 17 ? 22 : 9)};
            case 20, 21: // - 1 squad
                yield new byte[]{(byte) (space - 1), (byte) (space == 21 ? 22 : 12)};
            case 19: // 19 is special
                yield new byte[]{10, 11};

            case 22: // A1
                yield new byte[]{18, 19, 20};

            case 6, 15: // C tiles with 2 forward connections
                yield space == 6 ? new byte[]{7, 17} : new byte[]{14, 21};

                // C tiles where you can only advance forward
            case 7, 8, 9:
                yield new byte[]{(byte) (space + 1)};
            case 12, 13, 14:
                yield new byte[]{(byte) (space - 1)};


            default:
                System.out.println("Apparently I forgot to consider " + space);
                yield null;
        };
    }

    /**
     * This is intended for FastBoardMap and just gets the forward spaces for the opponent.
     * It's more efficient than flipping the entire board around which is like the entire point of FastBoardMap.
     */
    public static byte[] getBackwardSpaces(byte space) {
        return switch (space) {
            case 0, 1, 2, 3: // D tiles
                yield switch (space) {
                    case 2 -> new byte[]{10};
                    case 3 -> new byte[]{11};
                    default -> // Oh my fucking god IntelliJ, SHUT UP
                            null;
                };

            case 10, 11: // C tiles bordering D tiles.
                yield switch (space) {
                    case 10 -> new byte[]{9, 19};
                    case 11 -> new byte[]{12, 19};
                    default -> null; // Oh my fucking god IntelliJ, SHUT UP

                };

            // B tiles? More like these are going to B my undoing!
            case 16:
                yield new byte[]{4, 5};
            case 17, 18: // - 1 squad
                yield new byte[]{(byte) (space - 1), (byte) (space == 18 ? 22 : 6)};
            case 20:
                yield new byte[]{21, 22};
            case 21:
                yield new byte[]{15, 16};
            case 19:
                yield new byte[]{18, 20, 22};

            case 22: // A1
                yield new byte[]{21, 16, 17};

            case 9, 12: // C tiles with 2 forward connections
                yield space == 9 ? new byte[]{8, 18} : new byte[]{13, 20};


            case 6, 7, 8: // C tiles where you can only advance forward
                yield new byte[]{(byte) (space - 1)};
            case 13, 14:
                yield new byte[]{(byte) (space + 1)};
            case 15:
                yield new byte[]{4};


            default:
                System.out.println("Apparently I forgot to consider " + space);
                yield null;
        };
    }

    public static byte[] getNeighbouringSpacesByte(byte space) {
        /*
        This basically follows a few rules. C spaces (4-15) and B spaces (16-21) border +1 and -1 in their respective loops.
        C spaces border B spaces of ceil(n/2). B spaces border n * 2 and (n * 2) - 1.
        */
        return switch (space) {
            case 0, 1, 2, 3: // D tiles
                yield switch (space) {
                    case 0 -> new byte[]{1, 4};
                    case 1 -> new byte[]{0, 5};
                    case 2 -> new byte[]{3, 10};
                    case 3 -> new byte[]{2, 11};
                    default -> // Oh my fucking god IntelliJ, SHUT UP
                            null;
                };

            case 4, 5, 10, 11: // C tiles bordering D tiles.
                yield switch (space) {
                    case 4 -> new byte[]{0, 5, 15, 16};
                    case 5 -> new byte[]{1, 4, 6, 16};
                    case 10 -> new byte[]{2, 9, 11, 19};
                    case 11 -> new byte[]{3, 10, 12, 19};
                    default -> null;// Oh my fucking god IntelliJ, SHUT UP

                };
            case 16, 17, 18, 19, 20, 21: // B tiles
            {
                byte[] borderingTiles = new byte[5];
                borderingTiles[0] = (byte) (((space - 16) * 2) + 4); // Calculates 1st bordering C tile.
                borderingTiles[1] = (byte) (((space - 16) * 2) + 5); // Calculates 2nd bordering C tile.
                borderingTiles[2] = (byte) (space == 21 ? 16 : space + 1);
                borderingTiles[3] = (byte) (space == 16 ? 21 : space - 1);
                borderingTiles[4] = 22;
                yield borderingTiles;
            }

            case 22: // A1
                yield new byte[]{16, 17, 18, 19, 20, 21};

            default: // Remaining C tiles.
                byte[] borderingTiles = new byte[3];
                borderingTiles[0] = (byte) (space == 15 ? 4 : space + 1); // +1 C tile.
                borderingTiles[1] = (byte) (space - 1);

                // Calculates the bordering B tile.
                borderingTiles[2] = (byte) ((((space - 4) - (space % 2)) / 2) + 16);
                yield borderingTiles;
        };
    }

    /**
     * Gets the pieces for the player currently moving.
     *
     * @param board The game board
     */
    public static byte[] getCurrentPlayerPieces(byte[] board) {
        // First byte contains which player is the current player (bit 1),
        // followed by their piece count (3 following bits)
        // TODO please for the love of god check that this works.
        int pieceCount = (board[0] & 0b01110000) >> 4;
        byte[] piecePositions = new byte[pieceCount];
        int startPos = ((int) board[0] < 0) ? 1 : 8 - pieceCount;
        for (int i = startPos; i < pieceCount + startPos; i++) {
            piecePositions[i - startPos] = board[i];
        }

        return piecePositions;
    }


    // Exclusively for debugging!

    /**
     * Converts board to a String, so I can actually tell what's going on.
     */
    public static void printBoard(byte[] board) {

    }

    /**
     * Inverts a move. Used for black making a move.
     *
     * @param move A 2 element long array with the first index being the starting tile and the second being the ending tile.
     * @return The input but inverted.
     */
    public static int[] invertMove(int[] move) {
        int[] invertedMove = new int[2];
        for (int i = 0; i < 2; i++) {
            int piece = move[i];
            // This is surprisingly annoying to do without hard coding :/
            invertedMove[i] = switch (piece) {
                case 0, 1, 2, 3:
                    yield (piece + 2) % 4;
                default:
                    if (piece < 16)
                        yield ((piece + 2) % 12) + 4; // The + 2 is because - 4 ignores the D tiles, then +5 inverts the C tiles.
                    else if (piece < 22) // Corrections to the counter-clockwise thing are done in the conversion to tile
                        yield ((piece - 13) % 6) + 16; // Same as above, but +3 inverts the B tiles
                    else yield 22;
            };
        }
        return invertedMove;
    }

    public static int[] invertMove(byte[] move) {
        int[] invertedMove = new int[2];
        invertedMove[0] = move[0];
        invertedMove[1] = move[1];
        return invertMove(invertedMove);
    }

    public static int invertTile(int tile) {
        // This is surprisingly annoying to do without hard coding :/
        return switch (tile) {
            case 0, 1, 2, 3:
                yield (tile + 2) % 4;
            default:
                if (tile < 16)
                    yield ((tile + 2) % 12) + 4; // The + 2 is because - 4 ignores the D tiles, then +5 inverts the C tiles.
                else if (tile < 22) // Corrections to the counter-clockwise thing are done in the conversion to tile
                    yield ((tile - 13) % 6) + 16; // Same as above, but +3 inverts the B tiles
                else yield 22;
        };
    }



    public static byte[] getInvertedBoard(byte[] boardState) {
        byte[] invertedBoardState = new byte[boardState.length];
        invertedBoardState[0] = (byte) (8 - boardState[0]);
        for(int i = 1; i < boardState.length; i++) {

            int piece = boardState[i];
            int kingOffset = 0;
            if(piece > 22) {
                kingOffset = 23;
                piece -= 23;
            }
            // This is surprisingly annoying to do without hard coding :/
            invertedBoardState[9 - i] = (byte) switch (piece) {
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
        return invertedBoardState;
    }

    public static byte[] getMirroredBoard(byte[] boardState) {
        byte[] mirroredBoardState = new byte[boardState.length];
        mirroredBoardState[0] = boardState[0];
        for(int i = 1; i < boardState.length; i++) {

            int piece = boardState[i];
            int rockOffset = 0;
            if(piece > 22) {
                rockOffset = 23;
                piece -= 23;
            }
            // I'm sick of coming up with nice formulas, I'm just gonna hardcode it. Sue me.
            mirroredBoardState[i] = (byte) switch (piece) {
                case 0, 1, 2, 3 -> piece % 2 == 0 ? piece + 1 + rockOffset : (piece - 1) + rockOffset;

                case 4 -> 5 + rockOffset;
                case 5 -> 4 + rockOffset;
                case 6 -> 15 + rockOffset;
                case 7 -> 14 + rockOffset;
                case 8 -> 13 + rockOffset;
                case 9 -> 12 + rockOffset;
                case 10 -> 11 + rockOffset;
                case 11 -> 10 + rockOffset;
                case 12 -> 9 + rockOffset;
                case 13 -> 8 + rockOffset;
                case 14 -> 7 + rockOffset;
                case 15 -> 6 + rockOffset;

                case 17 -> 21 + rockOffset;
                case 18 -> 20 + rockOffset;
                case 20 -> 18 + rockOffset;
                case 21 -> 17 + rockOffset;

                default -> piece + rockOffset;
            };
        }
        return mirroredBoardState;
    }
}
