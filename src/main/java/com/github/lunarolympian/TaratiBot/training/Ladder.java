package com.github.lunarolympian.TaratiBot.training;

import com.github.lunarolympian.TaratiBot.tardar.NN;

import java.io.IOException;
import java.util.*;

/**
 * This is intended to encourage constant, meaningful, progress
 */
public class Ladder {

    private static int bestRandomBotScore = 0;
    private static int randomBotScore = 0;

    public static NN approveGeneration(ArrayList<NN> generation, ArrayList<NN> previousChampions) {
        Collections.reverse(generation); // So it gets the one that did the best.

        for(NN nn : generation) {

            for(NN pc : previousChampions) {
                boolean failed = false;
                for(int i = 0; i < 10; i++) {
                    Gauntlet.GameScore score = Gauntlet.simulateGame(nn, pc, i);
                    if(score.score1() < score.score2()) {
                        failed = true;
                        break;
                    }
                }

                if(!failed) {
                    randomBotScore = Gauntlet.approveTardar(nn, 1000);
                    if(randomBotScore < bestRandomBotScore - 25) break;
                    if(randomBotScore > bestRandomBotScore) bestRandomBotScore = randomBotScore;
                    return nn;
                }
            }

        }

        return null;
    }

    public static void runLadder(String path) throws IOException {
        Gauntlet gauntlet = new Gauntlet();

        ArrayList<NN> previousChampions = new ArrayList<>();


        NetworkGeneration generation = new NetworkGeneration(32);
        for(int i = 0; i < 64; i++) {
            Map<NN, Double> scoresheet = gauntlet.runGauntlet(generation.getNetworks());

            ArrayList<NN> sortedByWorthiness = new ArrayList<>(scoresheet.keySet());
            sortedByWorthiness.sort(Comparator.comparingDouble(scoresheet::get));

            NN survivor = approveGeneration(sortedByWorthiness, previousChampions);
            if(i == 0) survivor = sortedByWorthiness.getLast();

            if(survivor == null) {
                // Oh, I guess it's time to regenerate then.
                System.out.println((i + 1) + " - Generation failed.");
                generation.evolveNetworks(32, previousChampions.getLast());
                continue;
            }

            if(randomBotScore == 0) randomBotScore = Gauntlet.approveTardar(survivor, 1000);
            System.out.println((i + 1) + " - The survivor has a score of " + randomBotScore + ".");

            previousChampions.addFirst(survivor);
            if(previousChampions.size() > 32) previousChampions.removeFirst();

            generation.evolveNetworks(32, survivor);
            generation.clearDuplicates(previousChampions); // Better safe than sorry

            survivor.saveToFile(path);
        }
    }
}