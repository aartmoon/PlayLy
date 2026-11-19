package com.playly.ai.plan;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

public final class GameGenerationCli {
    private static final int MAX_PLAN_BYTES = 512 * 1024;

    private GameGenerationCli() {}

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Pass a JSON plan file and one quoted game prompt to generateGame.");
            System.exit(2);
            return;
        }

        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var plan = readPlan(Path.of(args[0]));
            var model = ChatCompletionsClient.fromEnvironment(http, "generateGame");
            var sources = new GameSourceGenerator(model::complete).generate(plan, args[1]);
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(sources));
        } catch (IllegalArgumentException | IOException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Game source generation was interrupted.");
            System.exit(1);
        }
    }

    private static GamePlan readPlan(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            var bytes = input.readNBytes(MAX_PLAN_BYTES + 1);
            if (bytes.length > MAX_PLAN_BYTES) {
                throw new IllegalArgumentException("The game plan file exceeds the size limit.");
            }
            var json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return GamePlan.parse(json, Set.of());
        }
    }
}
