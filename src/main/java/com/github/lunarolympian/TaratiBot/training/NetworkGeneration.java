package com.github.lunarolympian.TaratiBot.training;

import com.github.lunarolympian.TaratiBot.tardar.NN;

import java.util.*;

public class NetworkGeneration {

    private HashSet<NN> networks = new HashSet<>();

    public NetworkGeneration(int amount) {
        generateNetworks(amount);
    }

    public void generateNetworks(int amount) {
        for(int i = 0; i < amount; i++) {
            networks.add(new NN());
        }
    }
    public void generateNetworks(int amount, double score) {
        for(int i = 0; i < amount; i++) {
            scoreNetworks(new NN(), score);
        }
    }

    public void scoreNetworks(NN nn, double score) {
        networks.add(nn);
    }

    public void replaceGeneration(Collection<NN> newGeneration) {
        networks.clear();
        networks.addAll(newGeneration);
    }

    /**
     * Sends unworthy networks to a butterfly farm up North.
     */
    public void executeUnworthy(int cutoff, Map<NN, Double> scoresheet) {
        ArrayList<Double> networkScores = new ArrayList<>(scoresheet.values().stream().toList());
        Collections.sort(networkScores);
        networks.clear();
        for(NN network : scoresheet.keySet()) {
            if(networkScores.size() < cutoff) return;
            if(scoresheet.getOrDefault(network, 0d) >= networkScores.get(networkScores.size() - cutoff))
                networks.add(network);
        }

        // Couldn't be bothered to fix it another way
        if(cutoff == 1) {
            NN onlySurvivor = networks.stream().toList().getFirst();
            networks.clear();
            networks.add(onlySurvivor);
        }
    }

    public void regenerate(Map<NN, Double> competentNetworks) {

        ArrayList<Double> scores = new ArrayList<>(competentNetworks.values());
        Collections.sort(scores);

        for(NN nn : new ArrayList<>(competentNetworks.keySet()))
            if(competentNetworks.get(nn) < scores.getLast() * 0.92) competentNetworks.remove(nn);
        this.networks.clear();
        this.networks.addAll(competentNetworks.keySet());
    }

    /**
     * Takes the remaining networks and evolves them to meet the quota.
     */
    public void evolveNetworks(int quota) {
        int increasePerNetwork = (quota / networks.size());

        ArrayList<NN> evolutions = new ArrayList<>();

        for(NN network : networks) {
            evolutions.addAll(network.evolveNetwork(increasePerNetwork));
        }

        networks.clear();
        networks.addAll(evolutions);
    }

    public void evolveNetworks(int quota, NN seed) {
        networks.clear();
        networks.addAll(seed.evolveNetwork(quota));
    }

    public void clearDuplicates(Collection<NN> duplicates) {
        networks.removeAll(duplicates);
    }

    public NN[] getNetworks() {
        Set<NN> networkBracket = networks; // As it's being retrieved from a map that means the bracket is already shuffled.

        NN[] networkArray = new NN[networkBracket.size()];
        networkBracket.toArray(networkArray);

        return networkArray;

    }



}
