package com.playly.ai;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

final class LangfuseClient {
    private final HttpClient http;
    private final String baseUrl;
    private final String credentials;

    LangfuseClient(HttpClient http) {
        this.http = http;
        String publicKey = GenerationStorage.env("LANGFUSE_PUBLIC_KEY", "");
        String secretKey = GenerationStorage.env("LANGFUSE_SECRET_KEY", "");
        if (publicKey.isBlank() || secretKey.isBlank()) throw new IllegalArgumentException("Configure Langfuse API keys");
        credentials = "Basic " + Base64.getEncoder().encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        baseUrl = GenerationStorage.env("LANGFUSE_BASE_URL", "http://localhost:3000").replaceAll("/+$", "");
    }

    String pinnedPrompt(String name, String expected) throws IOException, InterruptedException {
        String path = "/api/public/v2/prompts/" + name + "?version=1";
        var current = getPrompt(path);
        if (current.statusCode() == 404) {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/public/v2/prompts"))
                    .timeout(Duration.ofSeconds(8)).header("Authorization", credentials)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GenerationStorage.JSON.toJson(
                            Map.of("type", "text", "name", name, "prompt", expected)))).build();
            var created = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (created.statusCode() / 100 != 2 && created.statusCode() != 409) {
                throw new IOException("Could not initialize Langfuse prompt " + name + " (HTTP " + created.statusCode() + ")");
            }
            current = getPrompt(path);
        }
        if (current.statusCode() != 200) throw new IOException("Could not read Langfuse prompt " + name);
        try {
            var prompt = GenerationStorage.JSON.fromJson(current.body(), JsonObject.class);
            if (!"text".equals(prompt.get("type").getAsString()) || prompt.get("version").getAsInt() != 1
                    || !expected.equals(prompt.get("prompt").getAsString())) {
                throw new IOException("Langfuse prompt does not match the pinned policy: " + name);
            }
            return prompt.get("prompt").getAsString();
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid Langfuse prompt response", invalid);
        }
    }

    private HttpResponse<String> getPrompt(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(8))
                .header("Authorization", credentials).GET().build(),
                ignored -> HttpResponse.BodySubscribers.limiting(
                        HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), 32 * 1024));
    }

    void write(String traceId, UUID attemptId, String artifactSha256, Map<String, Double> checks)
            throws IOException, InterruptedException {
        for (var check : new java.util.TreeMap<>(checks).entrySet()) {
            var id = UUID.nameUUIDFromBytes((attemptId + ":" + artifactSha256 + ":" + check.getKey())
                    .getBytes(StandardCharsets.UTF_8));
            String payload = GenerationStorage.JSON.toJson(Map.of(
                    "id", id, "traceId", traceId, "name", "playly." + check.getKey(),
                    "value", check.getValue(), "dataType", "NUMERIC",
                    "comment", "attemptId=" + attemptId + ", artifactSha256=" + artifactSha256));
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/public/scores")).timeout(Duration.ofSeconds(5))
                    .header("Authorization", credentials).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Langfuse score write failed with HTTP " + response.statusCode());
            }
        }
    }
}
