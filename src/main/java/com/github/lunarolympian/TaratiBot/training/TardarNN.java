package com.github.lunarolympian.TaratiBot.training;

import com.github.lunarolympian.TaratiBot.board.FastBoardMap;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.cpu.nativecpu.NDArray;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TardarNN {

    private MultiLayerNetwork nn;

    private double bestLoss = 100d;
    private Map<String, INDArray> bestNetwork;

    public TardarNN() {
        MultiLayerConfiguration nnConf = new NeuralNetConfiguration.Builder()
                .activation(Activation.RELU)
                .weightInit(WeightInit.XAVIER).biasInit(0.5)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(23).nOut(23).activation(Activation.RELU).build())
                .layer(1, new DenseLayer.Builder().nIn(23).nOut(23).activation(Activation.RELU).build())
                .layer(2, new DenseLayer.Builder().nIn(23).nOut(23).activation(Activation.RELU).build())
                .layer(3, new DenseLayer.Builder().nIn(23).nOut(1).activation(Activation.SIGMOID).build())
                .build();


        this.nn = new MultiLayerNetwork(nnConf);
    }

    public TardarNN(File source) throws IOException {
        this.nn = MultiLayerNetwork.load(source, false);
    }

    public Map<FastBoardMap, Float> scoreOptions(ArrayList<FastBoardMap> options) {
        Map<FastBoardMap, Float> scores = new HashMap<>();
        for(FastBoardMap option : options)
            scores.put(option, score(option));

        return scores;
    }

    public float score(FastBoardMap map) {
        float[] boardArray = new float[23];

        for(int i = 1; i < 9; i++) {
            int tile = map.getGameState()[i];
            if(tile > 22) tile -= 23;
            int white = (i > map.getGameState()[0] ? -1 : 1);
            boolean king = map.getGameState()[i] > 22;


            boardArray[tile] = 0.5f; // Occupied tile
            boardArray[tile] += (king ? 0.5f : 0f); // King
            boardArray[tile] *= white; // Colour
        }

        return (float) this.nn.feedForward(new NDArray(boardArray)).getLast().getRow(0).toDoubleVector()[0];
    }

    public void train(float winsPart, float bestMovePart, float otherMovesPart, float losingMovesPart, double acceptanceProbability) {

        double averageLoss = (winsPart * 0.5) + (bestMovePart * 1.2) + otherMovesPart + (losingMovesPart * 1.4f);

        System.out.print(averageLoss);

        if(averageLoss >= bestLoss) {
            Map<String, INDArray> workingNetwork = new HashMap<>();

            for(String params : this.bestNetwork.keySet())
                workingNetwork.put(params, this.bestNetwork.get(params).dup());
            this.nn.setParamTable(workingNetwork);

            regenerate(acceptanceProbability);
            return;
        }

        this.bestLoss = averageLoss;

        this.bestNetwork = new HashMap<>();
        Map<String, INDArray> workingNetwork = new HashMap<>();

        for(String params : this.nn.paramTable().keySet()) {
            this.bestNetwork.put(params, this.nn.paramTable().get(params).dup());
            workingNetwork.put(params, this.nn.paramTable().get(params).dup());
        }

        this.nn.setParamTable(workingNetwork);
        regenerate(acceptanceProbability);

    }

    private void regenerate(double acceptanceProbability) {
        Map<String, INDArray> paramTable = new HashMap<>();
        for(String params : this.nn.paramTable().keySet()) {
            INDArray paramValues = this.nn.paramTable().get(params).dup();
            for(int i = 0; i < paramValues.length(); i++) {
                if(Math.random() > acceptanceProbability) continue;
                paramValues.putScalar(i, (float) Math.clamp(paramValues.getFloat(i) + (Math.random() * (0.5f * acceptanceProbability)) - (0.25f * acceptanceProbability), -1, 1));
            }
            paramTable.put(params, paramValues);
        }
        this.nn.setParamTable(paramTable);
    }

    public void save(String path, boolean best) throws IOException {
        if(!best) {
            this.nn.save(new File(path));
            return;
        }

        MultiLayerNetwork saveNetwork = this.nn.clone();
        saveNetwork.setParamTable(bestNetwork);
        saveNetwork.save(new File(path));
    }
}
