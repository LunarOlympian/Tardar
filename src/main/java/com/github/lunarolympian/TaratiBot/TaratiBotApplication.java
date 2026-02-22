package com.github.lunarolympian.TaratiBot;

import com.github.lunarolympian.TaratiBot.tardar.NN;
import com.github.lunarolympian.TaratiBot.tardar.Tardar;
import com.github.lunarolympian.TaratiBot.tardar.gametree.PrevalBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

@SpringBootApplication
public class TaratiBotApplication {

    public static Tardar tardar;
    public static ArrayList<Tardar> tardarInstances = new ArrayList<>();
    public static NN nn;

	public static void main(String[] args) throws IOException, ClassNotFoundException {
        switch (args[0].toLowerCase()) {
            case "run":
                tardar = new Tardar(null, new File(args[1]));
                SpringApplication.run(TaratiBotApplication.class, args);
                break;
            case "preval":
                PrevalBuilder.buildPreval(args[1], args[2], args[3]);
                break;
        }

        /*NN test1 = new NN();
        System.out.println(test1.score(new BoardMap("new", true)));
        test1.saveToFile("C:\\Users\\sebas\\Documents\\Tarati\\Tardar\\test.trdr");

        NN test2 = new NN(new File("C:\\Users\\sebas\\Documents\\Tarati\\Tardar\\test.trdr"));
        System.out.println(test2.score(new BoardMap("new", true)));*/

        //BoardMap map = new BoardMap();
        //map.invertBoard();
        //System.out.println("123");


	}

}
