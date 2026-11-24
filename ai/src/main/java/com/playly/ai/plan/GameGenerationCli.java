package com.playly.ai.plan;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameGenerationCli {
    private GameGenerationCli() {}

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Pass an output directory and one quoted game prompt to generateGame.");
            System.exit(2);
            return;
        }
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            GamePlanner.validatePrompt(args[1]);
            var model = ChatCompletionsClient.fromEnvironment(http, "generateGame", "/game/files.schema.json");
            var runner = new DockerGameRunner(Path.of(args[0]));
            var result = new GameSourceGenerator(model::complete).generate(args[1], examples(), runner::build);
            System.out.println(result);
        } catch (IllegalArgumentException | IOException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Game generation was interrupted.");
            System.exit(1);
        }
    }

    static Map<String, String> examples() throws IOException {
        var result = new LinkedHashMap<String, String>();
        for (String file : List.of("CoinScene.ts", "art.ts")) {
            try (var stream = GameGenerationCli.class.getResourceAsStream("/game/examples/" + file)) {
                if (stream == null) {
                    throw new IOException("Missing Phaser example: " + file);
                }
                result.put(file, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return result;
    }
}
