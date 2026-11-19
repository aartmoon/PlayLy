package com.playly.ai.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

final class ChatCompletionsClient {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private final HttpClient http;
    private final URI endpoint;
    private final String model;
    private final String apiKey;

    ChatCompletionsClient(HttpClient http, URI endpoint, String model, String apiKey) {
        var host = endpoint.getHost();
        if (host == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                || !("https".equalsIgnoreCase(endpoint.getScheme())
                || ("http".equalsIgnoreCase(endpoint.getScheme()) && LOOPBACK_HOSTS.contains(host)))) {
            throw new IllegalArgumentException("PLAYLY_LLM_URL must be an HTTPS URL or a local loopback HTTP URL.");
        }
        this.http = http;
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
    }

    static ChatCompletionsClient fromEnvironment(HttpClient http, String command) {
        var url = requiredEnv("PLAYLY_LLM_URL", command);
        URI endpoint;
        try {
            endpoint = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("PLAYLY_LLM_URL is not a valid URL.");
        }
        return new ChatCompletionsClient(http, endpoint, requiredEnv("PLAYLY_LLM_MODEL", command),
                System.getenv().getOrDefault("PLAYLY_LLM_API_KEY", ""));
    }

    private static String requiredEnv(String name, String command) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Set " + name + " before running " + command + ".");
        }
        return value;
    }

    String complete(String instructions, String prompt) throws IOException, InterruptedException {
        var payload = new Request(model, List.of(new Message("system", instructions), new Message("user", prompt)));
        var request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(payload), StandardCharsets.UTF_8));
        if (!apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        try {
            var response = http.send(request.build(), info -> {
                if (info.statusCode() < 200 || info.statusCode() >= 300) {
                    return BodySubscribers.replacing("");
                }
                return BodySubscribers.limiting(BodySubscribers.ofString(StandardCharsets.UTF_8), MAX_RESPONSE_BYTES);
            });
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Model API returned HTTP " + response.statusCode() + ".");
            }
            return readContent(response.body());
        } catch (HttpTimeoutException exception) {
            throw new IOException("Model API request timed out.", exception);
        }
    }

    private static String readContent(String body) throws IOException {
        Completion completion;
        try {
            completion = JSON.fromJson(body, Completion.class);
        } catch (JsonParseException exception) {
            throw new IOException("Model API returned an invalid JSON response.", exception);
        }
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            throw new IOException("Model API returned no choices.");
        }
        var choice = completion.choices().getFirst();
        if (choice == null || !"stop".equals(choice.finishReason()) || choice.message() == null) {
            throw new IOException("Model API did not finish the response.");
        }
        var message = choice.message();
        var content = message.content();
        if ((message.refusal() != null && !message.refusal().isJsonNull())
                || content == null || !content.isJsonPrimitive() || !content.getAsJsonPrimitive().isString()
                || content.getAsString().isBlank()) {
            throw new IOException("Model API returned no response text.");
        }
        return content.getAsString();
    }

    private record Request(String model, List<Message> messages) {}
    private record Message(String role, String content) {}
    private record Completion(List<Choice> choices) {}
    private record Choice(@SerializedName("finish_reason") String finishReason, Reply message) {}
    private record Reply(JsonElement content, JsonElement refusal) {}
}
