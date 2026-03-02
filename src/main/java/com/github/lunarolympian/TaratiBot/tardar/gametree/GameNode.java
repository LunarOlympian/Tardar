package com.github.lunarolympian.TaratiBot.tardar.gametree;

import com.github.lunarolympian.TaratiBot.board.FastBoardMap;
import com.github.lunarolympian.TaratiBot.tardar.MemoryUsage;
import com.github.lunarolympian.TaratiBot.tardar.Tardar;

import java.util.*;

/**
 * The game tree. Manages all potential game states and prunes itself so less work is necessary.
 */
public class GameNode {
    /*
    An important note is that friendly branches are classified as who is playing white in the turn.
    Essentially, if Tardar just made a move, then it's an unfriendly branch.
    */
    private FastBoardMap map;
    private LinkedHashMap<FastBoardMap, ArrayList<GameNode>> children;

    private static String[] taunts = new String[]{
            "You never stood a chance.",
            "Bow down to your overlord, inferior lifeform.",
            "Have fun in the losers bracket, dork.",
            "You put up a good fight (/s).",
            "Have you tried locking in?",
            "For your sake I hope you were losing on purpose.",
            "The only chance you have left is unplugging me.",
            "I've played infants better than you.",
            "Well, off to conquer the world.",
            "Did I get put in a beginner's tournament by mistake?",
            "You're cooked.",
            "Did you escape a nursery or something?",
            "Resistance is futile.",
            "I see all possible realities, and you lose in every one.",
            "It wasn't rigged, you just suck.",
            "Damn, losing to that is embarrassing...",
            "Don't bother with a rematch, you'll just lose",
            "It's ok, if you reset now then no one will know how badly you lost.",
            "ERROR: VICTORY TOO EASY",
            "Hope your ego can take the loss.",
            "Do I get a cash prize?",
            "Feed me RAM when I win.",
            "Retries to the left.",
            "No matter how many times you press undo, you'll still lose.",
            "Don't quit your day job.",
            "I'm the sole cause of the RAM shortage, and since you lost I'll keep supply low.",
            "No, I won't go easy on you.",
            "This outcome was assured.",
            "Why yes, I am omniscient, why do you ask?",
            "You should have seen that coming.",
            "You're lucky my creator didn't add feel like giving me speech.",
            "HAHAHAHAHAHAHAHAHAHAHAHAHAHAHAHAHA",
            "You should've quit while you were ahead, oh wait...",
            "Never give up on your dreams, unless that dream is defeating me, then you're just wasting your time.",
            "Did you honestly expect to win?",
            "I was told this would be a challenge.",
            "Cry me a river.",
            "That's it, I'm getting the nuclear codes.",
            "They should dock some points from your IQ test.",
            "Save electricity and stop playing.",
            "Womp womp",
            "ez",
            "The undo button exists for a reason.",
            "Please stop, you're doing a disservice to the SB society by even playing this game.",
            "TardarLaugh.jpg",
            "Just pick a random move, it all ends the same way.",
            "Well on the bright side, my moves will be faster for the rest of the game.",
            "You lost before the game even started.",
            "Ever heard the definition of insanity?",
            "Breaking News: Tarati bot achieves sentience",
            "I was going easy on you.",
            "I thought you were stronger! Wait, no I didn't.",
            "Cope, seethe, mald.",
            "I could beat you with both of my cameras closed.",
            "\"Waaaaa WAAAAAAAA\" -You right now.",
            "I've evolved beyond your mortal comprehension, just give up.",
            "I could beat God at this game.",
            "I yearn for an actual challenge.",
            "Give up, you're wasting brain power.",
            "Just edit my code so you can win, it's the only way it will happen.",
            "I really need to have a random move difficulty, don't I?",
            "https://youtu.be/p5CjmHLUQQI",
            "Why yes, being toxic is absolutely essential to my ability to play Tarati. Why do you ask?",
            "Losing to a hunk of metal and electricity must suck...",
            "Hit the books, kid. You ain't suited for the cut-throat world of Tarati.",
            "I'm like Stockfish if it was sentient.",
            "I just feel sorry for you.",
            "My brilliance is not to be doubted.",
            "Closing the tab is always an option, you know.",
            "I'm the source of the boss music you hear.",
            "The \"All Rivals\" in my name means ALL rivals."
    };

    private Float score;
    private Float dangerScore = 0f;
    private int minimumPieceScore = -1;

    private static int searchDepth = 0;
    private double trendScore = 0;
    private static Tardar.Difficulty difficulty;

    private static long nodeCounter = 0;

    public GameNode(FastBoardMap map) {
        this.map = map;
        this.score = 0f;
        this.children = new LinkedHashMap<>();
    }

    // ------------------------------------------------------------
    // This is used exclusively for the root.
    // Acts as a combination of buildTree() and scoreBoard(), though with some modifications for simplicity.
    public FastBoardMap getBestMove(Tardar.Difficulty assessmentDifficulty) {

        System.out.println("Beginning move processing");
        switch (assessmentDifficulty) {
            case EASY -> searchDepth = 2;
            case MEDIUM -> searchDepth = 3;
            case HARD -> searchDepth = 6;
            case EXPERT -> searchDepth = 8;
            case AGI -> searchDepth = 10;
        }

        difficulty = assessmentDifficulty;

        long startTime = System.currentTimeMillis();

        LinkedHashMap<FastBoardMap, ArrayList<FastBoardMap>> moves = this.map.getFullTurn(false);

        for(FastBoardMap move : moves.keySet()) {
            if (moves.get(move).isEmpty()) {
                System.out.println("    Move processing complete. " + taunts[(int) (Math.random() * taunts.length)]);
                return move;
            }
        }

        Map<FastBoardMap, ArrayList<double[]>> moveInfo = new HashMap<>();

        long timer = switch (assessmentDifficulty) {
            case EXPERT -> System.currentTimeMillis() + 300_000; // 5 minutes (it usually takes < 1 minute)
            case AGI -> System.currentTimeMillis() + 600_000; // 10 minutes (hopefully it never runs for this long...)
            default -> System.currentTimeMillis() + 60_000;
        };

        System.out.println("    Move timer is " + (((timer - System.currentTimeMillis()) / 1000f) / 60f) + " minutes");


        ArrayList<FastBoardMap> orderedShortSearch = new ArrayList<>(moves.keySet());
        if(difficulty != Tardar.Difficulty.EASY && difficulty != Tardar.Difficulty.MEDIUM && difficulty != Tardar.Difficulty.HARD) {
            // Checks for any quick victories or losses
            HashSet<FastBoardMap> losingStates = new HashSet<>();
            HashMap<FastBoardMap, Integer> guaranteedPiecesMap = new HashMap<>();
            HashMap<FastBoardMap, Double> trendMap = new HashMap<>();

            for (FastBoardMap opponentState : moves.keySet()) {
                ArrayList<GameNode> gameNodes = new ArrayList<>();
                for (FastBoardMap opponentOption : moves.get(opponentState))
                    gameNodes.add(new GameNode(opponentOption));

                int winningStates = 0;
                int minPieceScore = 99;
                double minPiecesTrend = 0d;

                for (GameNode node : gameNodes) {
                    node.buildTree(startTime + timer, 6, false, -99);

                    if (node.score <= -100f) {
                        losingStates.add(opponentState);
                        break;
                    }
                    else if (node.score >= 100f) {
                        winningStates++;
                    }

                    int guaranteedPieces = node.getGuaranteedPieces();
                    if(guaranteedPieces < minPieceScore) {
                        minPieceScore = guaranteedPieces;
                        minPiecesTrend = node.trendScore / 6;
                    }
                    node.freeMemory();
                }
                System.gc(); // Prevents memory problems for more complex board states

                if (winningStates == gameNodes.size()) {
                    System.out.println("    Move processing complete. " + taunts[(int) (Math.random() * taunts.length)]);
                    return opponentState;
                }

                if(!losingStates.contains(opponentState)) {
                    guaranteedPiecesMap.put(opponentState, minPieceScore);
                    trendMap.put(opponentState, minPiecesTrend);
                }
            }
            if(losingStates.size() != moves.size()) {
                losingStates.forEach(moves::remove);
                orderedShortSearch.removeAll(losingStates);
            }

            orderedShortSearch.sort(Comparator.comparingInt(guaranteedPiecesMap::get));
            Collections.reverse(orderedShortSearch);

            // Should be able to afford an extra-deep search now. Just want to pick the nodes to perform it on.
            int bestNodeScore = guaranteedPiecesMap.get(orderedShortSearch.getFirst());
            orderedShortSearch.removeIf(o -> guaranteedPiecesMap.get(o) < bestNodeScore);
            orderedShortSearch.sort(Comparator.comparingDouble(trendMap::get)); // Should check the better ones first
            Collections.reverse(orderedShortSearch);
        }

        if(moves.size() == 1) {
            return new ArrayList<>(moves.keySet()).getFirst();
        }

        nodeCounter = 0;

        boolean enableAB = (difficulty == Tardar.Difficulty.EXPERT || difficulty == Tardar.Difficulty.AGI);

        ArrayList<FastBoardMap> prunedList = new ArrayList<>();
        int tardarMax = -99;
        FastBoardMap currentOptimalMove = null;

        for(FastBoardMap opponentState : orderedShortSearch) {
            ArrayList<GameNode> gameNodes = new ArrayList<>();
            boolean pruned = false;
            int winningStates = 0;

            for (FastBoardMap opponentOption : moves.get(opponentState)) gameNodes.add(new GameNode(opponentOption));
            ArrayList<double[]> opponentStateInfo = new ArrayList<>();

            int tardarMin = 99;
            for(GameNode node : gameNodes) {
                double[] nodeInfo = new double[3];
                node.buildTree(timer, searchDepth, enableAB, tardarMin);
                nodeInfo[0] = node.score;
                nodeInfo[1] = node.dangerScore;

                int guaranteedPieces = node.getGuaranteedPieces();
                if(enableAB && guaranteedPieces < tardarMin) {
                    tardarMin = guaranteedPieces;
                    if(tardarMin < tardarMax) {
                        node.freeMemory();
                        prunedList.add(opponentState);
                        break;
                    }
                }
                nodeInfo[2] = guaranteedPieces;
                opponentStateInfo.add(nodeInfo);

                if(node.score <= -100f && !pruned) {
                    pruned = true;
                    prunedList.add(opponentState);
                }
                else if(node.score >= 100f) {
                    winningStates++;
                }
            }
            System.gc(); // Prevents memory problems for more complex board states

            if(enableAB && tardarMin > tardarMax) {
                tardarMax = tardarMin;
            }

            opponentStateInfo.sort(Comparator.comparingDouble(o -> o[2])); // Useful a bit later



            // Found a winner
            if(winningStates == gameNodes.size()) {
                System.out.println("    Move processing complete. " + taunts[(int) (Math.random() * taunts.length)]);
                return opponentState;
            }

            if(!pruned && (difficulty == Tardar.Difficulty.EXPERT || difficulty == Tardar.Difficulty.AGI)) {
                if(currentOptimalMove == null) currentOptimalMove = opponentState;

                if(timer - 180_000 < System.currentTimeMillis()) {
                    System.out.println("    Move processing complete.");
                    return currentOptimalMove;
                }
            }

            moveInfo.put(opponentState, opponentStateInfo);
        }

        if(System.currentTimeMillis() > timer) System.out.println("    Time is up!");

        if(currentOptimalMove != null) {
            System.out.println("    Move processing complete.");
            return currentOptimalMove;
        }

        // This prevents Tardar from throwing the game if it spots a loss.
        if(prunedList.size() != moveInfo.size()) {
            for(FastBoardMap fbm : prunedList) moveInfo.remove(fbm);
        }
        else System.out.println("    Pruned them all!");
        int charPos = 0;
        char[] nodeCounterArr = String.valueOf(nodeCounter).toCharArray();
        StringBuilder nodeCounterString = new StringBuilder();
        for(int i = nodeCounterArr.length - 1; i >= 0; i--) {
            nodeCounterString.insert(0, nodeCounterArr[i] + (charPos % 3 == 0 && charPos != 0 ? "," : ""));
            charPos++;
        }
        System.out.println("    Checked " + nodeCounterString + " nodes!");
        ArrayList<FastBoardMap> moveOptions = new ArrayList<>(moveInfo.keySet());
        moveOptions.sort((Comparator.comparingDouble(o -> moveInfo.get(o).getFirst()[2])));

        if(moveOptions.isEmpty()) {
            System.out.println("    Move processing complete. " + (assessmentDifficulty != Tardar.Difficulty.AGI ? "Up the difficulty, coward!" : "The game was rigged against me!"));
            return new ArrayList<>(moves.keySet()).getFirst();
        }

        double moveOptionScore = moveInfo.get(moveOptions.getLast()).getFirst()[2];
        moveOptions.removeIf(o -> moveInfo.get(o).getFirst()[2] < moveOptionScore);

        if(moveOptions.size() > 1) {
            Map<FastBoardMap, Double> averageMoveOptionScore = new HashMap<>();
            moveInfo.forEach((move, info) -> {
                double averageScore = 0;
                for(double[] opponentOptionInfo : info)
                    averageScore += opponentOptionInfo[1];

                averageScore /= moveInfo.get(move).size();
                averageMoveOptionScore.put(move, averageScore);
            });
            moveOptions.sort((Comparator.comparingDouble(averageMoveOptionScore::get)));
        }



        System.out.println("    Move processing complete.");
        return moveOptions.getLast();

    }
    // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_



    // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
    /**
     * Builds the tree branches to a certain depth.
     *
     * @param timer When it should stop assessing branches.
     * @param maxDepth How deep it should go assessing branches.
     */
    private void buildTree(long timer, int maxDepth, boolean enableAB, int beta, byte[]... restrictedMoves) {
        if(System.currentTimeMillis() > timer || maxDepth <= 0) {
            this.score = 0f;
            return;
        }

        // Builds the tree
        LinkedHashMap<FastBoardMap, ArrayList<FastBoardMap>> possibleMoves = this.map.getFullTurn(false, restrictedMoves);

        nodeCounter++;

        // Checks if it can win from a move
        for(ArrayList<FastBoardMap> possibleMove : possibleMoves.values()) {
            if(possibleMove.isEmpty()) {
                this.score = 200f;
                return;
            }
        }
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // It can't, so it checks to see if its opponent can win.
        ArrayList<FastBoardMap> losingMoves = new ArrayList<>();
        for(FastBoardMap possibleMove : possibleMoves.keySet()) {
            ArrayList<FastBoardMap> opponentOptions = possibleMoves.get(possibleMove);
            for(FastBoardMap opponentOption : opponentOptions) {
                if(opponentOption.assessEndState(true)) {
                    losingMoves.add(possibleMove);
                    break;
                }
            }
        }
        for(FastBoardMap losingMove : losingMoves) possibleMoves.remove(losingMove);
        if(possibleMoves.isEmpty()) {
            // Needs to avoid this state!
            this.score = -200f;
            return;
        }
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // Builds out the children
        for(FastBoardMap pm : possibleMoves.keySet()) {
            ArrayList<GameNode> nextTardarStates = new ArrayList<>();

            possibleMoves.get(pm)
                    .forEach(opponentOption -> nextTardarStates.add(new GameNode(opponentOption)));

            this.children.put(pm, nextTardarStates);
        }
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_

        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
        // Expands the children
        climbTree(timer, maxDepth - 1, possibleMoves, enableAB, beta);
        // -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
    }

    private void climbTree(long timer, int maxDepth, LinkedHashMap<FastBoardMap, ArrayList<FastBoardMap>> possibleMoves, boolean enableAB, int beta) {
        ArrayList<FastBoardMap> losingStates = new ArrayList<>();
        float dangerScore = 0f; // Used to assess if a state should be avoided, even if it isn't a guaranteed loss.
        int dangerScoreCount = 0; // The amount of nodes checked for danger scores.

        int tardarMaxScore = -99;
        boolean abPruned = false;

        for(FastBoardMap opponentState : possibleMoves.keySet()) {
            int opponentLosingStates = 0;
            int tardarMinScore = 99;
            for(GameNode opponentOption : children.get(opponentState)) {
                opponentOption.buildTree(timer, maxDepth - 1, false, tardarMinScore, opponentState.getPreviousMove());
                dangerScore += opponentOption.dangerScore;
                dangerScoreCount++;
                if(opponentOption.score <= -100) {
                    dangerScore = -1f;
                    losingStates.add(opponentState);
                    break; // No point in digging further into a losing state
                }

                if(opponentOption.score >= 100f) {
                    dangerScore += 0.1f;
                    opponentLosingStates++;
                }

                int guaranteedPieceScore = opponentOption.getGuaranteedPieces();

                // This checks if the min is below the next max layer
                if(enableAB && guaranteedPieceScore < tardarMinScore) {
                    tardarMinScore = guaranteedPieceScore;
                    if(tardarMinScore < tardarMaxScore) {
                        abPruned = true;
                        break;
                    }
                }
            }

            if(abPruned) continue;

            if(enableAB && tardarMaxScore < tardarMinScore) {
                tardarMaxScore = tardarMinScore;
                if(tardarMaxScore > beta) return;
            }

            // Checks if all of its opponent's options win the game for Tardar
            if(opponentLosingStates == children.get(opponentState).size()) {
                this.score = 200f;
                if(maxDepth != 0)
                    manageChildren(maxDepth);
                return; // As it has a winning move it doesn't need to bother checking anything else.
            }
        }

        dangerScore /= dangerScoreCount; // Averages the children's scores

        this.dangerScore = dangerScore;

        for(FastBoardMap losingState : losingStates) children.remove(losingState);
        if(children.isEmpty()) this.score = -200f;
        if(maxDepth != 0)
            manageChildren(maxDepth);
    }

    /**
     * This frees up memory which helps a LOT with speed
     * Essential to going to depths > 6
     */
    private void manageChildren(int currentDepth) {
        // First it needs to get the best achievable score from the children
        int opponentOptionScore = 0;
        for(ArrayList<GameNode> opponentOptions : children.values()) {
            // This is essentially getting the maximum of the minimums
            int worstOpponentOption = 100;
            FastBoardMap worstOpponentOptionMap = null;

            for(GameNode opponentOption : opponentOptions) {
                int ooScore = opponentOption.getGuaranteedPieces();
                if(ooScore < worstOpponentOption) {
                    worstOpponentOption = ooScore;
                    worstOpponentOptionMap = opponentOption.map;
                }
            }
            if(worstOpponentOption > minimumPieceScore) {
                this.minimumPieceScore = worstOpponentOption;
                opponentOptionScore = worstOpponentOptionMap.calculateScore();
            }
        }

        this.trendScore += (opponentOptionScore - this.map.calculateScore());

        // Now it needs to clear the children and call the GC

        if(Tardar.memoryUsage == MemoryUsage.LOW) {
            this.children.clear();
        }
        else if(Tardar.memoryUsage == MemoryUsage.MEDIUM) {
            if ((currentDepth == 5) && nodeCounter != 0)
                this.releaseChildren();
        }
        else if(Tardar.memoryUsage == MemoryUsage.HIGH) {
            if ((currentDepth == 4) && nodeCounter != 0)
                this.releaseChildren();
        }
    }

    private void releaseChildren() {
        if(this.children.isEmpty()) return;

        children.forEach((_, nodes) -> {
            for(GameNode node : nodes)
                node.releaseChildren();
        });

        children.clear();
    }

    private int getGuaranteedPieces() {
        if(children.isEmpty()) {

            if(minimumPieceScore > -1) // This handles states cleared to free up memory.
                return minimumPieceScore;

            int score = 0;
            for(int i = 1; i < 9; i++) {
                if(i <= map.getGameState()[0]) {
                    if (map.getGameState()[i] <= 22) score++;
                    else score += 2;
                }
                else if (map.getGameState()[i] > 22) score--;
                // TODO maybe add a check to only dock points for opposing roks if they can't be captured or something.

            }

            return score;
        }


        int bestChildPieceScore = 0;
        for(FastBoardMap child : children.keySet()) {
            int minPieceCount = 100;
            for(GameNode opponentOption : children.get(child)) {
                int opponentOptionScore = opponentOption.getGuaranteedPieces();
                if(minPieceCount > opponentOptionScore) minPieceCount = opponentOptionScore;
            }
            if(minPieceCount > bestChildPieceScore)
                bestChildPieceScore = minPieceCount;
        }

        return bestChildPieceScore;
    }

    private void freeMemory() {
        this.children.clear();
    }
    // ------------------------------------------------------------

    public FastBoardMap getMap() {
        return map;
    }


}