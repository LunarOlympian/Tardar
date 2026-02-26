package com.github.lunarolympian.TaratiBot;

import com.github.lunarolympian.TaratiBot.board.BoardMap;
import com.github.lunarolympian.TaratiBot.board.BoardUtils;
import com.github.lunarolympian.TaratiBot.board.FastBoardMap;
import com.github.lunarolympian.TaratiBot.tardar.Tardar;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static java.nio.file.Files.readString;

@RestController
@ComponentScan(basePackages={"com.web"})
@CrossOrigin(origins="*")
public class BotInput {

    public static String taunt = "";

    @RequestMapping("/process")
    //@RequestMapping("")
    @CrossOrigin(origins="*")
    public String process(String data, String colour, String difficulty) throws InterruptedException {
        Tardar.Difficulty diff = switch (difficulty.toLowerCase()) {
            case "easy" -> Tardar.Difficulty.EASY;
            case "medium" -> Tardar.Difficulty.MEDIUM;
            case "hard" -> Tardar.Difficulty.HARD;
            case "expert" -> Tardar.Difficulty.EXPERT;
            case "agi" -> Tardar.Difficulty.AGI;
            default -> throw new IllegalStateException("Invalid difficulty: " + difficulty);
        };


        BoardMap map = new BoardMap(data, colour.equalsIgnoreCase("W"));

        FastBoardMap bestMap = TaratiBotApplication.tardar.runNN(new FastBoardMap(map), diff);

        int[] convertedMove = new int[]{bestMap.getPreviousMove()[0], bestMap.getPreviousMove()[1]};
        if(convertedMove[0] > 22) convertedMove[0] -= 23;
        if(convertedMove[1] > 22) convertedMove[1] -= 23;

        int[] bestMove = colour.equalsIgnoreCase("B") ? BoardUtils.invertMove(convertedMove) : convertedMove;


        return BoardUtils.intToTile(bestMove[0]) + " " + BoardUtils.intToTile(bestMove[1]);
    }

    @RequestMapping("/taunt")
    //@RequestMapping("")
    @CrossOrigin(origins="*")
    public String taunt(String character) {
        String returnTaunt = taunt;
        taunt = "";
        return returnTaunt;
    }

    @GetMapping("/saveboard")
    //@RequestMapping("")
    @CrossOrigin(origins="*")
    public String saveBoard(String data) throws IOException {
        File file = new File("C:\\Users\\sebas\\Documents\\Tarati\\Tarati board states\\TardarSavedMoves.txt");
        String existingStates = readString(file.toPath()).trim();

        TreeSet<String> existingSaves = new TreeSet<>(Comparator.comparingInt(i -> i.split(" ")[0].hashCode()));
        existingSaves.addAll(Arrays.asList(existingStates.split("\n")));
        existingSaves.add(data);

        Files.writeString(file.toPath(), String.join("\n", existingSaves));
        return "";
    }

    // For training this is a victory, it handles shifting the models currently playing
    /*@RequestMapping("/shift")
    //@RequestMapping("")
    @CrossOrigin(origins="*")
    public String shift(String data, String colour) throws InterruptedException {

    }*/

}
