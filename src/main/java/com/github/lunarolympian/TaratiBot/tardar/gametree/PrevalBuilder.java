package com.github.lunarolympian.TaratiBot.tardar.gametree;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.tardar.NN;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PrevalBuilder {

    private static Preval preval;

    private static TreeMap<Integer, Integer> previousStates = new TreeMap<>(Integer::compare);

    private static int killBranchRewindPos = 0;
    private static int movesSinceLastCheck = 0;
    private static int prevalSize = 0;
    private static boolean killBranch = false;

    public static void buildPreval(String depthString, String nnPath, String savePath) throws IOException, ClassNotFoundException {
        ArrayList<BoardMap> maps = new ArrayList<>();
        maps.add(new BoardMap("new", true));

        int depth = Integer.parseInt(depthString);
        NN nn = new NN(new File(nnPath));
        preval = new Preval();
        // Tries to search layers of the tree
        AtomicInteger pos = new AtomicInteger();
        maps.forEach(map -> {
            List<BoardMap> possibleStates = Arrays.asList(map.getPossibleStates());
            for(BoardMap ps : possibleStates) { // I know what you're gonna say, shut up.
                if(pos.get() < 2) {
                    killBranchRewindPos = depth - 1;
                    buildBranch(ps, depth - 1);
                    pos.getAndIncrement();
                    killBranch = false;
                }
            }
            // newMaps.addAll(possibleStates);
        });

        preval.savePreval(new File(savePath));
    }

    // Made exclusively because Java dies if I do anything else.
    private static void buildBranch(BoardMap map, int depth) {
        if(previousStates.containsKey(Arrays.hashCode(map.getBoardState()))) {
            previousStates.put(Arrays.hashCode(map.getBoardState()), previousStates.get(Arrays.hashCode(map.getBoardState())) + 1);
            return;
        }
        previousStates.put(Arrays.hashCode(map.getBoardState()), 1);
        if(previousStates.size() > 16_000_000) prunePreviousStates();

        movesSinceLastCheck++;
        if(movesSinceLastCheck >= 16_000_000) {
            movesSinceLastCheck = 0;
            if(preval.getPrevalSize() * 0.95 < prevalSize) {
                prevalSize = preval.getPrevalSize();
                killBranchRewindPos = depth + 5;
                killBranch = true; // Stops working on the branch
                return;
            }
            prevalSize = preval.getPrevalSize();
        }

        BoardMap[] possibleStates = map.getPossibleStates();
        if(possibleStates.length == 0) {
            preval.addPreval(map, 1000);
            return;
        }

        if(depth == 1) return;

        for(BoardMap ps : possibleStates) {
            buildBranch(ps, depth - 1);

            if(killBranch && killBranchRewindPos == depth) killBranch = false;
            if(killBranch) return;
        }
    }

    public static void prunePreviousStates() {
        System.out.println("Pruning previous states");
        ArrayList<Integer> prevStates = new ArrayList<>(previousStates.keySet());

        prevStates.forEach(ps -> {
            if(previousStates.get(ps) <= 1) previousStates.remove(ps);
        });

        if(previousStates.size() <= 8_000_000) return;

        prevStates = new ArrayList<>(previousStates.keySet());
        prevStates.sort(Comparator.comparingInt(ps -> previousStates.get(ps)));

        for(int i = 0; i < prevStates.size() - 8_000_000; i++)
            previousStates.remove(prevStates.get(i));
    }

    public static void performPreval(BoardMap map, NN nn) {
        float result = preval.searchDB(map);
        if(result != 0f) return; // Already been searched

        /*
        For a given neural network this checks what path leads to the most likely victory against a perfect opponent.
        I think it works the best to try to find any guaranteed defeats for either Tardar and mark all
        the board states at which the defeat is inescapable as they should be avoided at all costs, then mark board states
        where Tardar will try to die as being bad.
        */

        // Checks if the move is a defeat state.
        if(map.getPossibleStates().length != 0) return; // It isn't

        preval.addPreval(map, 1000);
        // Needs to go back and find when the state became inescapable
        Queue<BoardMap> mapHistory = map.getMapHistory();
        BoardMap escapableState = null;
        for(int i = 0; i < mapHistory.size(); i++) {
            BoardMap prevState = mapHistory.remove();
            if(i % 2 == 0) continue;
            if(scout(prevState)) {
                // Now it prepares to check if Tardar wants to head into the loss state
                escapableState = prevState;
                break;
            }
            else preval.addPreval(escapableState, 1000);

            preval.addPreval(prevState, 1000);
        }

        if(escapableState == null) return; // Tardar's cooked

        // TODO Make sure Tardar takes the right path.
        /*for(BoardMap ps : escapableState.getPossibleStates()) {
            if()
        }*/


    }

    /**
     * Specifically exists to answer the question "Can you escape a loss state from this position?"
     * @param map The map to check.
     * @return A boolean representing whether the state is escapable.
     */
    public static boolean scout(BoardMap map) {
        // Is there a path (with no loops) that Tardar can survive without being defeated for 10 moves?
        ArrayList<BoardMap> maps = new ArrayList<>(Arrays.asList(map.getPossibleStates()));

        for(int i = 0; i < 10; i++) {
            ArrayList<BoardMap> newMaps = new ArrayList<>();

            maps.forEach(currentLayerMap -> {
                List<BoardMap> possibleStates = Arrays.asList(currentLayerMap.getPossibleStates());
                possibleStates.forEach(ps -> ps.addPreviousState(currentLayerMap)); // I know what you're gonna say, shut up.
                newMaps.addAll(possibleStates);
            });

            maps = newMaps;
            if(maps.isEmpty()) return false;
        }

        return true;
    }
}
