package com.github.lunarolympian.TaratiBot.tardar.gametree;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.board.BoardUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;

public class Preval {

    private TreeMap<byte[], Float> prevalBoards;

    protected Preval() {
        this.prevalBoards = new TreeMap<>(new PrevalComparator());
    }

    public Preval(byte[] prevalData) throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(prevalData));

        this.prevalBoards = (TreeMap<byte[], Float>) in.readObject();
    }

    /**
     * Tardar has thousands of states that are saved as being particularly good or bad.
     * This database should be used in addition to the standard evaluation function to give more accurate info on the board state.
     *
     * @param boardMap The board to search for (variations included)
     * @return The override for the evaluation function (if there is any). 0f means there isn't an override.
     */
    public float searchDB(BoardMap boardMap) {
        /*
        Format of the file is a tiny bit weird, but here's the rough layout:
        - Tardar NN layout
        - Tardar training weights layout
        - Preval boards
            - For efficiency I'm just turning a Map into a byte array. I doubt it will be big enough to cause problems.
        */
        byte[] board = new byte[9];
        for(int i = 0; i < 9; i++) board[i] = (byte) boardMap.getBoardState()[i];

        // Now it needs to search for the board and its variations
        if(prevalBoards.containsKey(board))
            return prevalBoards.get(board);
        ArrayList<byte[]> keys = new ArrayList<>(prevalBoards.keySet());
        keys.removeIf(k -> k[0] != 4);

        if(prevalBoards.containsKey(BoardUtils.getInvertedBoard(board)))
            return prevalBoards.get(BoardUtils.getInvertedBoard(board));

        if(prevalBoards.containsKey(BoardUtils.getMirroredBoard(board)))
            return prevalBoards.get(BoardUtils.getMirroredBoard(board));

        if(prevalBoards.containsKey(BoardUtils.getInvertedBoard(BoardUtils.getMirroredBoard(board))))
            return prevalBoards.get(BoardUtils.getInvertedBoard(BoardUtils.getMirroredBoard(board)));

        return 0f;
    }

    /**
     * Adds/updates a preval to the database.
     * @param boardMap The map to add it for.
     * @param score The score to assign it.
     */
    protected boolean addPreval(BoardMap boardMap, float score) {
        byte[] board = new byte[9];
        for(int i = 0; i < 9; i++) board[i] = (byte) boardMap.getBoardState()[i];
        byte[] boardCopy = Arrays.copyOf(board, 9);

        // Now it needs to search for the board and its variations
        if(prevalBoards.containsKey(board)) {
            prevalBoards.put(board, score);
            return true;
        }
        board = BoardUtils.getInvertedBoard(board);
        if(prevalBoards.containsKey(board)) {
            prevalBoards.put(board, score);
            return true;
        }
        board = BoardUtils.getMirroredBoard(boardCopy);
        if(prevalBoards.containsKey(board)) {
            prevalBoards.put(board, score);
            return true;
        }
        board = BoardUtils.getInvertedBoard(board);
        if(prevalBoards.containsKey(board)) {
            prevalBoards.put(board, score);
            return true;
        }

        prevalBoards.put(boardCopy, score); // Just adds the original in
        return false;
    }



    protected void savePreval(File trdrFile) throws IOException {
        // Needs to check if the file already has data.
        ByteArrayOutputStream toBytes = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(toBytes);
        outputStream.writeObject(prevalBoards);

        byte[] mapBytes = toBytes.toByteArray();
        if(!trdrFile.exists()) {
            Files.write(trdrFile.toPath(), mapBytes);
            return;
        }

        byte[] trdrBytes = Files.readAllBytes(trdrFile.toPath());
        if(trdrBytes.length > 10) {
            // The bytes at the very end of the file
            byte[] prevalStartArr = new byte[8];
            System.arraycopy(trdrBytes, 1, prevalStartArr, 0, 8);
            long prevalStart = ByteBuffer.wrap(prevalStartArr).getLong();

            byte[] nnBytes = prevalStart == 0 ? trdrBytes : new byte[Math.toIntExact(prevalStart)];
            if (prevalStart != 0) System.arraycopy(nnBytes, 0, nnBytes, 0, Math.toIntExact(prevalStart));

            // Updates the NN start
            System.arraycopy(ByteBuffer.allocate(8).putLong(nnBytes.length).array(), 0, nnBytes, 1, 8);
        }

        byte[] combinedArrays = new byte[mapBytes.length + trdrBytes.length];
        for(int i = 0; i < trdrBytes.length; i++) combinedArrays[i] = trdrBytes[i];
        for(int i = trdrBytes.length; i < trdrBytes.length + mapBytes.length; i++) combinedArrays[i] = mapBytes[i - trdrBytes.length];

        Files.write(trdrFile.toPath(), combinedArrays);
    }

    protected int getPrevalSize() {
        return prevalBoards.size();
    }

    private static class PrevalComparator implements Comparator<byte[]>, Serializable {

        /**
         * Compares its two arguments for order.  Returns a negative integer,
         * zero, or a positive integer as the first argument is less than, equal
         * to, or greater than the second.<p>
         * <p>
         * The implementor must ensure that {@link Integer#signum
         * signum}{@code (compare(x, y)) == -signum(compare(y, x))} for
         * all {@code x} and {@code y}.  (This implies that {@code
         * compare(x, y)} must throw an exception if and only if {@code
         * compare(y, x)} throws an exception.)<p>
         * <p>
         * The implementor must also ensure that the relation is transitive:
         * {@code ((compare(x, y)>0) && (compare(y, z)>0))} implies
         * {@code compare(x, z)>0}.<p>
         * <p>
         * Finally, the implementor must ensure that {@code compare(x,
         * y)==0} implies that {@code signum(compare(x,
         * z))==signum(compare(y, z))} for all {@code z}.
         *
         * @param o1 the first object to be compared.
         * @param o2 the second object to be compared.
         * @return a negative integer, zero, or a positive integer as the
         * first argument is less than, equal to, or greater than the
         * second.
         * @throws NullPointerException if an argument is null and this
         *                              comparator does not permit null arguments
         * @throws ClassCastException   if the arguments' types prevent them from
         *                              being compared by this comparator.
         * @apiNote It is generally the case, but <i>not</i> strictly required that
         * {@code (compare(x, y)==0) == (x.equals(y))}.  Generally speaking,
         * any comparator that violates this condition should clearly indicate
         * this fact.  The recommended language is "Note: this comparator
         * imposes orderings that are inconsistent with equals."
         */
        @Override
        public int compare(byte[] o1, byte[] o2) {
            for(int i = 0; i < o1.length; i++) {
                if(o1[i] > o2[i]) return 1;
                else if(o2[i] > o1[i]) return -1;
            }
            return 0;
        }
    }
}
