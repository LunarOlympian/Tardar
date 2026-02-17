package com.github.lunarolympian.TaratiBot;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.board.BoardUtils;
import com.github.lunarolympian.TaratiBot.board.FastBoardMap;
import com.github.lunarolympian.TaratiBot.training.TestBot;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import static java.nio.file.Files.readString;

@RestController
@ComponentScan(basePackages={"com.web"})
@CrossOrigin(origins="*")
public class BotInput {

    public static String taunt = "";

    @RequestMapping("/process")
    //@RequestMapping("")
    @CrossOrigin(origins="*")
    public String process(String data, String colour) throws InterruptedException {

        BoardMap map = new BoardMap(data, colour.equalsIgnoreCase("W"));

        FastBoardMap bestMap = TaratiBotApplication.tardar.runNN(new FastBoardMap(map));
        /*double bestScore = 0;
        for(BoardMap possibleMove : map.getPossibleStates()) {
            double mapScore = TaratiBotApplication.nn.score(possibleMove);

            if(bestMap == null || mapScore > bestScore) {
                bestMap = possibleMove;
                bestScore = mapScore;
            }
        }*/

        int[] convertedMove = new int[]{bestMap.getPreviousMove()[0], bestMap.getPreviousMove()[1]};
        if(convertedMove[0] > 22) convertedMove[0] -= 23;
        if(convertedMove[1] > 22) convertedMove[1] -= 23;

        int[] bestMove = colour.equalsIgnoreCase("B") ? BoardUtils.invertMove(convertedMove) : convertedMove;

        /*File file = new File("C:\\Users\\sebas\\Documents\\Tarati\\Tarati board states\\TardarTrainingStates.txt");


        try {
            file.createNewFile();
            String prevStates = Files.readString(file.toPath());
            if(Arrays.asList(prevStates.split("\n")).contains(data + "%" + colour))
                return BoardUtils.intToTile(bestMove[0]) + " " + BoardUtils.intToTile(bestMove[1]);

            prevStates += "\n" + data + "%" + colour;
            Files.writeString(file.toPath(), prevStates.trim());
        } catch (Exception e) {

        }*/

        return BoardUtils.intToTile(bestMove[0]) + " " + BoardUtils.intToTile(bestMove[1]);


        //if(data.equalsIgnoreCase("New")) return "C2 C3";
        //return "C7 B4";
    }

    @RequestMapping("/taunt")
    //@RequestMapping("")
    @CrossOrigin(origins="*")
    public String taunt(String character) throws InterruptedException {
        String returnTaunt = taunt;
        taunt = "";
        return returnTaunt;
    }

    // For training this is a victory, it handles shifting the models currently playing
    /*@RequestMapping("/shift")
    //@RequestMapping("")
    @CrossOrigin(origins="*")
    public String shift(String data, String colour) throws InterruptedException {

    }*/

}
