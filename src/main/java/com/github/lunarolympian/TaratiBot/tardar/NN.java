package com.github.lunarolympian.TaratiBot.tardar;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.board.FastBoardMap;
import com.github.lunarolympian.TaratiBot.tardar.gametree.Preval;
import com.github.lunarolympian.TaratiBot.training.iterative.IterativeRefinement;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.PerformanceListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.cpu.nativecpu.NDArray;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;

public class NN {

    private MultiLayerNetwork network;
    private double[][] weightVar;
    private double[][] biasVar;
    private double evolveChance;
    private Preval preval;

    public NN() {
        MultiLayerConfiguration configuration
                = new NeuralNetConfiguration.Builder()
                .activation(Activation.SIGMOID)
                .weightInit(WeightInit.XAVIER)
                .biasInit(0.5)
                .list()
                //.layer(0, new DenseLayer().B)
                .layer(0, new DenseLayer.Builder().nIn(23).nOut(23).build())
                .layer(1, new DenseLayer.Builder().nIn(23).nOut(23).build())
                .layer(2, new DenseLayer.Builder().nIn(23).nOut(23).build())
                .layer(3, new DenseLayer.Builder().nIn(23).nOut(1).build())
                .build();

        this.network = new MultiLayerNetwork(configuration);
        network.setListeners(new PerformanceListener(1, true));

        this.network.init();



        int weightVarRows = 0;
        for(int i = 0; i < network.getnLayers(); i++) {
            weightVarRows += network.getLayer(i).paramTable().get("W").columns(); // Idfk why it's columns
        }

        this.weightVar = new double[weightVarRows][network.getLayer(0).paramTable().get("W").columns()];
        for(int i = 0; i < weightVar.length; i++) {
            for (int j = 0; j < weightVar[1].length; j++) {
                weightVar[i][j] = Math.clamp(Math.random(), 0.2, 0.3);
            }
        }

        this.biasVar = new double[network.getnLayers()][];
        for(int i = 0; i < network.getnLayers(); i++) {
            biasVar[i] = new double[network.getLayer(i).paramTable().get("b").columns()]; // Idfk why it's columns
        }

        for(int i = 0; i < biasVar.length; i++) {
            for (int j = 0; j < biasVar[i].length; j++) {
                biasVar[i][j] = Math.clamp(Math.random(), 0.2, 0.3);
            }
        }

        for(int i = 0; i < network.getnLayers(); i++) {
            Map<String, INDArray> paramTable = network.getLayer(i).paramTable();
            INDArray biases = paramTable.get("b");
            long pos = 0;
            for (float val : biases.toFloatVector()) {
                biases.putScalar(pos, val + Math.random());
                pos++;
            }

            network.getLayer(i).setParamTable(paramTable);
        }
    }

    public NN(NN template) {
        this.network = template.network.clone();

        this.weightVar = new double[template.weightVar.length][template.weightVar[0].length];
        for(int i = 0; i < weightVar.length; i++) {
            for(int j = 0; j < weightVar[i].length; j++) {
                weightVar[i][j] = template.weightVar[i][j] * Math.clamp(Math.random() * 2, 0.5, 2);
            }
        }

        this.biasVar = new double[template.biasVar.length][];
        for(int i = 0; i < network.getnLayers(); i++) {
            biasVar[i] = new double[network.getLayer(i).paramTable().get("b").columns()]; // Idfk why it's columns
        }
        for(int i = 0; i < biasVar.length; i++) {
            for(int j = 0; j < biasVar[i].length; j++) {
                biasVar[i][j] = template.biasVar[i][j] * Math.clamp(Math.random() * 2, 0.5, 2);
            }
        }

        for(int i = 0; i < network.getnLayers(); i++) {
            Map<String, INDArray> paramTable = network.getLayer(i).paramTable();
            for(int j = 0; j < network.getLayer(i).paramTable().get("W").columns(); j++) {
                INDArray parameters = paramTable.get("W").getRow(j);
                long pos = 0;
                for (float val : parameters.toFloatVector()) {
                    parameters.putScalar(pos, val/* + ((Math.random() * this.weightVar[i][(int) pos]) - (this.weightVar[i][(int) pos] / 2))*/);
                    pos++;
                }
            }

            INDArray biases = paramTable.get("b");
            int pos = 0;
            for (float val : biases.toFloatVector()) {
                biases.putScalar(pos, val/*  + ((Math.random() * this.biasVar[i][pos]) - (this.biasVar[i][pos] / 2))*/);
                pos++;
            }
            network.getLayer(i).setParamTable(paramTable);
        }
    }

    public NN(File trdrFile) throws IOException, ClassNotFoundException {
        FileInputStream input = new FileInputStream(trdrFile);

        MultiLayerConfiguration configuration
                = new NeuralNetConfiguration.Builder()
                .activation(Activation.SIGMOID)
                .weightInit(WeightInit.SIGMOID_UNIFORM)
                .biasInit(0.5)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(23).nOut(23).build())
                .layer(1, new DenseLayer.Builder().nIn(23).nOut(23).build())
                .layer(2, new DenseLayer.Builder().nIn(23).nOut(23).build())
                .layer(3, new DenseLayer.Builder().nIn(23).nOut(1).build())
                .build();
        this.network = new MultiLayerNetwork(configuration);
        this.network.init();

        // First byte is the layer count (if it's over 255 then something has gone horribly wrong
        int layerCount = input.readNBytes(1)[0];
        long prevalStart = ByteBuffer.wrap(input.readNBytes(8)).getLong();

        for(int i = 0; i < layerCount; i++) {
            // Gets the weights then biases for each layer
            Map<String, INDArray> paramTable = network.getLayer(i).paramTable();
            //int layerSize = input.readNBytes(1)[0];

            for(int y = 0; y < paramTable.get("W").rows(); y++) {
                for (int x = 0; x < paramTable.get("W").columns(); x++) {
                    float inputWeight = ByteBuffer.wrap(input.readNBytes(4)).getFloat();
                    paramTable.get("W").getRow(y).putScalar(x, inputWeight);
                }
            }

            network.getLayer(i).setParamTable(paramTable);
        }

        for(int i = 0; i < layerCount; i++) {
            Map<String, INDArray> paramTable = network.getLayer(i).paramTable();

            for(int j = 0; j < paramTable.get("b").length(); j++) {
                float inputBias = ByteBuffer.wrap(input.readNBytes(4)).getFloat();
                paramTable.get("b").putScalar(j, inputBias);
            }
            network.getLayer(i).setParamTable(paramTable);
        }


        // ------------------------------------------------------------
        // Sets training variation parameters
        // ------------------------------------------------------------
        int weightVarRows = 0;
        for(int i = 0; i < network.getnLayers(); i++) {
            weightVarRows += network.getLayer(i).paramTable().get("W").columns(); // Idfk why it's columns
        }

        this.weightVar = new double[weightVarRows][network.getLayer(0).paramTable().get("W").columns()];
        for(int i = 0; i < weightVar.length; i++) {
            for (int j = 0; j < weightVar[1].length; j++) {
                weightVar[i][j] = Math.clamp(Math.random(), 0.2, 0.3);
            }
        }
        for(int i = 0; i < weightVar.length; i++) {
            for (int j = 0; j < weightVar[i].length; j++) {
                double trainingWeightVar = ByteBuffer.wrap(input.readNBytes(8)).getDouble();
                this.weightVar[i][j] = trainingWeightVar;
            }
        }

        this.biasVar = new double[network.getnLayers()][];
        for(int i = 0; i < network.getnLayers(); i++) {
            biasVar[i] = new double[network.getLayer(i).paramTable().get("b").columns()]; // Idfk why it's columns
        }
        for(int i = 0; i < biasVar.length; i++) {
            for (int j = 0; j < biasVar[i].length; j++) {
                this.biasVar[i][j] = ByteBuffer.wrap(input.readNBytes(8)).getDouble();
            }
        }

        // ------------------------------------------------------------
        // Sets the preval info (if it exists)
        // ------------------------------------------------------------

        // THERE ARE 6 BLANK BYTES, KEEP THEM AS IT WORKS FAIRLY GOOD AS A BARRIER!!!!!!!!
        input.readNBytes(6);
        byte[] preval = input.readAllBytes();
        if(preval.length <= 20) return;
        this.preval = new Preval(preval);

    }

    public void saveToFile(String path) throws IOException {
        File file = new File(path);


        int size = 1 + network.getnLayers(); // 2 is for the layer count, the other thing is for the node count in each layer
        for(int i = 0; i < network.getnLayers(); i++) {
            size += network.getLayer(i).paramTable().get("W").rows() * network.getLayer(i).paramTable().get("W").columns() * 4;
            size += (int) network.getLayer(i).paramTable().get("b").length() * 4;
        }

        byte[] nnConverted = new byte[size + (size * 2)]; // There was initially a - 8 for some reason. However, I added 8 to handle the preval start.
        nnConverted[0] = (byte) network.getnLayers();
        for(int i = 1; i < 9; i++) nnConverted[i] = 0;
        // Note that preval start is set to all 0s here. It can be overwritten later.
        int pos = 9;

        for(int i = 0; i < network.getnLayers(); i++) {
            for(int j = 0; j < network.getLayer(i).paramTable().get("W").rows(); j++) {
                float[] weights = network.getLayer(i).paramTable().get("W").getRow(j).toFloatVector();
                for (float weight : weights) {
                    for (byte weightByte : ByteBuffer.allocate(4).putFloat(weight).array()) {
                        nnConverted[pos] = weightByte;
                        pos++;
                    }
                }
            }
        }

        for(int i = 0; i < network.getnLayers(); i++) {
            Map<String, INDArray> params = network.getLayer(i).paramTable();
            for(float bias : params.get("b").toFloatVector()) {
                for(byte biasByte : ByteBuffer.allocate(4).putFloat(bias).array()) {
                    nnConverted[pos] = biasByte;
                    pos++;
                }
            }
        }

        // ----------------------------------------
        // Training parameters
        // ----------------------------------------

        /*for(int i = 0; i < weightVar.length; i++) {
            for(int j = 0; j < weightVar[0].length; j++) {
                double trainingWeight = weightVar[i][j];
                for (byte weightByte : ByteBuffer.allocate(8).putDouble(trainingWeight).array()) {
                    nnConverted[pos] = weightByte;
                    pos++;
                }
            }
        }

        for(int i = 0; i < biasVar.length; i++) {
            for(int j = 0; j < biasVar[i].length; j++) {
                double trainingBias = biasVar[i][j];
                for (byte biasByte : ByteBuffer.allocate(8).putDouble(trainingBias).array()) {
                    nnConverted[pos] = biasByte;
                    pos++;
                }
            }
        }*/

        Files.write(new File(path).toPath(), nnConverted);
    }



    public float score(BoardMap map) {
        float[] boardArray = new float[23];

        // TODO Add in a check for if the board is marked as bad or the opponent can win the game the next move.

        for(int i = 1; i < 9; i++) {
            int tile = map.getBoardState()[i];
            if(tile > 22) tile -= 23;
            int white = (i > map.getBoardState()[0] ? -1 : 1);
            boolean king = map.getBoardState()[i] > 22;


            boardArray[tile] = 0.5f; // Occupied tile
            boardArray[tile] += (king ? 0.5f : 0f); // King
            boardArray[tile] *= white; // Colour
        }

        INDArray scoreArray = this.network.feedForward(new NDArray(boardArray)).getLast();
        double score = scoreArray.toDoubleVector()[0];
        return (float) score;
    }

    public float score(FastBoardMap map) {
        float[] boardArray = new float[23];

        // TODO Add in a check for if the board is marked as bad or the opponent can win the game the next move.

        for(int i = 1; i < 9; i++) {
            int tile = map.getGameState()[i];
            if(tile > 22) tile -= 23;
            int white = (i > map.getGameState()[0] ? -1 : 1);
            boolean king = map.getGameState()[i] > 22;


            boardArray[tile] = 0.5f; // Occupied tile
            boardArray[tile] += (king ? 0.5f : 0f); // King
            boardArray[tile] *= white; // Colour
        }

        INDArray scoreArray = this.network.feedForward(new NDArray(boardArray)).getLast();
        double score = scoreArray.toDoubleVector()[0];
        return (float) score;
    }

    public ArrayList<NN> evolveNetwork(int quota) {
        ArrayList<NN> networks = new ArrayList<>();
        for(int i = 0; i < quota; i++)
            networks.add(new NN(this));

        return networks;
    }

    public float[] getWeights(int layer) {
        return network.getLayer(layer).paramTable().get("W").getColumn(0).toFloatVector();
    }

    public void updateWeight(int layer, long pos, float weight) {
        Map<String, INDArray> params = network.getLayer(layer).paramTable();
        params.get("W").putScalar(pos, weight);

        network.getLayer(layer).setParamTable(params);
    }

    private TreeSet<int[]> optimisedWeights = new TreeSet<>(Arrays::compare);

    public void iterateNN(NN opponent, BoardMap[] states, int layerNum) {
        // Chooses 8 random weights and adds small offsets to their values to see if it can find an optimal combination that beats the opponent.
        // If none do it tries a different set of random weights and repeats.

        int[][] weights = new int[8][3];
        HashSet<Integer> existingWeights = new HashSet<>();
        HashSet<Integer> existingBiases = new HashSet<>();
        for(int i = 0; i < 8; i++) {
            int weightSize = network.getLayer(layerNum).paramTable().get("W").rows() * network.getLayer(layerNum).paramTable().get("W").rows();
            int biasSize = network.getLayer(layerNum).paramTable().get("b").columns();
            int randomWeight;
            int randomBias;
            do { // Finally! A use for do-while loops!
                randomWeight = (int) (Math.random() * weightSize);
                randomBias = (int) (Math.random() * biasSize);
            } while ((existingWeights.contains(randomWeight) || existingBiases.contains(randomBias)));
            existingWeights.add(randomWeight);
            existingBiases.add(randomBias);

            weights[i] = new int[]{layerNum, randomWeight, randomBias};
        }

        // Needs to prioritise speed in defeating its opponent, so plays a bunch of games and finds the combination it did the best with.

        String bestCombination = "";
        float[] bestOffsets_W = new float[8];
        float[] bestOffsets_B = new float[8];

        int[] scores = IterativeRefinement.simulateGames(states, 1, this, opponent);

        int bestScore = scores[0] - scores[1];

        for(int i = 0; i < 200; i++) {
            /*StringBuilder combination = new StringBuilder(Integer.toString(i, 3));
            while(combination.length() < 4) {
                combination.insert(0, "0");
            }*/

            float[] offsets_w = new float[8];
            float[] offsets_b = new float[8];
            for(int j = 0; j < 8; j++) {
                Map<String, INDArray> paramTable = network.getLayer(weights[j][0]).paramTable();
                //float offset = 0f;
                //if(combination.charAt(j) == '1') offset = -0.1f;
                //else if(combination.charAt(j) == '2') offset = 0.1f;

                offsets_w[j] = (float) (Math.random() * 0.5) - 0.25f;
                offsets_b[j] = (float) (Math.random() * 0.5) - 0.25f;

                if(Math.random() <= 0.333)
                    offsets_w[j] = 0.0f;
                if(Math.random() <= 0.333)
                    offsets_b[j] = 0.0f;

                paramTable.get("W").putScalar(weights[j][1], paramTable.get("W").getFloat(weights[j][1]) +
                        offsets_w[j]);
                paramTable.get("b").putScalar(weights[j][2], paramTable.get("b").getFloat(weights[j][2]) +
                        offsets_b[j]);
                if(j == 7) network.getLayer(weights[j][0]).setParamTable(paramTable);
            }

            scores = IterativeRefinement.simulateGames(states, 1, this, opponent);
            if(scores[0] - scores[1] > bestScore) {
                bestScore = scores[0] - scores[1];
                bestOffsets_W = offsets_w;
                bestOffsets_B = offsets_b;
            }


            // Reverts it
            for(int j = 0; j < 8; j++) {
                Map<String, INDArray> paramTable = network.getLayer(weights[j][0]).paramTable();
                //float offset = 0f;
                //if(combination.charAt(j) == '1') offset = 0.1f;
                //else if(combination.charAt(j) == '2') offset = -0.1f;
                //                                             ^ Change is here (flipped signs in this and the one above)
                paramTable.get("W").putScalar(weights[j][1], paramTable.get("W").getFloat(weights[j][1]) - offsets_w[j]);
                paramTable.get("b").putScalar(weights[j][2], paramTable.get("b").getFloat(weights[j][2]) - offsets_b[j]);
                if(j == 7) network.getLayer(weights[j][0]).setParamTable(paramTable);
            }
        }

        // Once it finds the best it tries to optimise the change.
        for(int j = 0; j < 8; j++) {
            Map<String, INDArray> layer = network.getLayer(weights[j][0]).paramTable();
            /*float offset = 0f;
            if(bestCombination.charAt(j) == '1') offset = -0.1f;
            else if(bestCombination.charAt(j) == '2') offset = 0.1f;*/

            layer.get("W").putScalar(weights[j][1], layer.get("W").getFloat(weights[j][1]) +
                    bestOffsets_W[j]);
            layer.get("b").putScalar(weights[j][2], layer.get("b").getFloat(weights[j][2]) +
                    bestOffsets_B[j]);
            if(j == 7) network.getLayer(layerNum).setParamTable(layer);
        }

    }

    public float searchPreval(BoardMap map) {
        return 0f; //preval.searchDB(map);
    }
}