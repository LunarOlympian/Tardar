package com.github.lunarolympian.TaratiBot;

import com.github.lunarolympian.TaratiBot.tardar.MemoryUsage;
import com.github.lunarolympian.TaratiBot.tardar.Tardar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TardarMain {

    public static Tardar tardar;

	public static void main(String[] args) {
        tardar = new Tardar(null);

        Tardar.memoryUsage = switch (args[0].trim().toLowerCase()) {
            case "low" -> MemoryUsage.LOW;
            case "high" -> MemoryUsage.HIGH;
            default -> MemoryUsage.MEDIUM;
        };
        SpringApplication.run(TardarMain.class, args);


	}

}
