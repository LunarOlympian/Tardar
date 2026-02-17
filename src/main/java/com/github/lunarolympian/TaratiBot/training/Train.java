package com.github.lunarolympian.TaratiBot.training;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.tardar.NN;
import com.github.lunarolympian.TaratiBot.tardar.Tardar;

import java.io.IOException;
import java.util.Map;

public class Train {

    public static int maxRandomWinRate = 30;

    public static void main(String[] args) throws IOException {
        NetworkGeneration generation = new NetworkGeneration(128);
        Gauntlet gauntlet = new Gauntlet();

        for(int i = 0; i < 32; i++) {
            // Map<NN, Double> scoreSheet = new HashMap<>();
            NN[] genArray = generation.getNetworks();
            /*for(NN nn : genArray)
                scoreSheet.put(nn, Gauntlet.simulateTardarGame(nn, 50, false, false));
            generation.executeUnworthy(2, scoreSheet);

            ArrayList<Double> networkScores = new ArrayList<>(scoreSheet.values().stream().toList());
            Collections.sort(networkScores);
            System.out.println(i + " - " + networkScores.getLast()); // To keep track of how much it's improving

            NN[] survivors = generation.getNetworks();
            competentNetworks.put(survivors[0], scoreSheet.get(survivors[0]));
            if(survivors.length > 1) competentNetworks.put(survivors[1], scoreSheet.get(survivors[1]));

            if(networkScores.getLast() > 35) break;

            if(competentNetworks.size() > 12) {
                generation.regenerate(competentNetworks); // This keeps the networks from getting stuck with a bad batch by routinely restoring some old networks
                competentNetworks.clear();
            }

            if(i % 4 == 0 && i != 0) {
                double score = Gauntlet.simulateTardarGame(generation.getNetworks()[0], 50, false, false);
                System.out.println(score);
            }

            if(i != 31) generation.evolveNetworks(128);
            else generation.executeUnworthy(1, scoreSheet);*/

            Map<NN, Double> scoreSheet = gauntlet.runGauntlet(genArray);

            generation.executeUnworthy(8, scoreSheet);

            int highestWinRate = 0;

            /*ArrayList<NN> sortedNetworks = new ArrayList<>(scoreSheet.keySet());

            sortedNetworks.sort(Comparator.comparingDouble(scoreSheet::get));

            for(int j = sortedNetworks.size() - 1; j >= 0; j--) {
                int winRate = Gauntlet.approveTardar(sortedNetworks.get(j), 100);
                if(winRate < maxRandomWinRate - 5) scoreSheet.remove(sortedNetworks.get(j));
                else {
                    if (winRate > highestWinRate) highestWinRate = winRate;
                    break;
                }
            }
            if(highestWinRate > maxRandomWinRate) maxRandomWinRate = highestWinRate;*/

            scoreSheet = gauntlet.runGauntlet(generation.getNetworks()); // Reruns just to make it extra accurate
            generation.executeUnworthy(1, scoreSheet);

            System.out.println(i + " - " + Gauntlet.approveTardar(generation.getNetworks()[0], 1000) + " ");

            if(i != 0) {
                generation.executeUnworthy(1, scoreSheet);
                generation.getNetworks()[0].saveToFile(args[0]);
            }

            generation.evolveNetworks(128);
        }

        Tardar tardar = new Tardar(new BoardMap("new", true));
        tardar.tardarSetNN(generation.getNetworks()[0]);

        tardar.trainingArc();
    }
}
