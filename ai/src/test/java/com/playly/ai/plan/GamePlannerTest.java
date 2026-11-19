package com.playly.ai.plan;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GamePlannerTest {
    private static final String PLAN = """
            {
              "entryPoint": "src/main.ts",
              "assetRefs": [],
              "requirements": [{"id": "coins", "description": "Cat collects coins"}],
              "groups": [
                {"id": "scene", "dependsOn": [], "files": [
                  {"path": "src/scenes/CatScene.ts", "purpose": "Play scene", "contracts": ["export CatScene"]}
                ]},
                {"id": "boot", "dependsOn": ["scene"], "files": [
                  {"path": "src/main.ts", "purpose": "Start Phaser with CatScene", "contracts": []}
                ]}
              ]
            }
            """;

    @Test
    void requestsAndReadsAPlanThroughChatCompletions() throws Exception {
        var requestBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                var response = Map.of("choices", List.of(
                        Map.of("finish_reason", "stop", "message", Map.of("content", PLAN))));
                var bytes = new Gson().toJson(response).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
            var planner = new GamePlanner(new ChatCompletionsClient(http, endpoint, "test-model", ""));
            var prompt = "Сделай платформер, где кот собирает монеты";
            var plan = planner.create(prompt);

            assertEquals("src/main.ts", plan.entryPoint());
            assertEquals(List.of("scene"), plan.groups().get(1).dependsOn());
            assertEquals("coins", plan.requirements().getFirst().id());

            var request = JsonParser.parseString(requestBody.get()).getAsJsonObject();
            assertEquals("test-model", request.get("model").getAsString());
            var messages = request.getAsJsonArray("messages");
            var instructions = messages.get(0).getAsJsonObject();
            assertEquals("system", instructions.get("role").getAsString());
            assertTrue(instructions.get("content").getAsString().contains("PlayLy game plan"));
            var user = messages.get(1).getAsJsonObject();
            assertEquals("user", user.get("role").getAsString());
            assertEquals(prompt, user.get("content").getAsString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsPlainTextMarkdownAndMissingFields() {
        for (var response : List.of("I will build a platformer", "```json\n" + PLAN + "\n```",
                PLAN.replace("\"assetRefs\": [],", ""), PLAN.replace("\"coins\"", "42"))) {
            assertThrows(IllegalArgumentException.class, () -> GamePlan.parse(response, Set.of()), response);
        }
    }

    @Test
    void rejectsInvalidPathsDependenciesAndAssets() {
        for (var response : List.of(
                PLAN.replace("src/scenes/CatScene.ts", "src/../vite.config.ts"),
                PLAN.replace("src/scenes/CatScene.ts", "src/main.ts"),
                PLAN.replace("\"entryPoint\": \"src/main.ts\"", "\"entryPoint\": \"src/missing.ts\""),
                PLAN.replace("\"dependsOn\": []", "\"dependsOn\": [\"boot\"]"),
                PLAN.replace("\"assetRefs\": []", "\"assetRefs\": [\"unlisted-cat\"]"))) {
            assertThrows(IllegalArgumentException.class, () -> GamePlan.parse(response, Set.of()), response);
        }
    }
}
