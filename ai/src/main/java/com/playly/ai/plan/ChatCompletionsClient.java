package com.playly.ai.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStreamReader;
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
    private static final int DEFAULT_TIMEOUT_SECONDS = 180;
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private final HttpClient http;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final Duration requestTimeout;
    private final ResponseFormat responseFormat;
    private final String reasoningEffort;

    ChatCompletionsClient(HttpClient http, URI endpoint, String model, String apiKey) {
        this(http, endpoint, model, apiKey, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS), null, null);
    }

    private ChatCompletionsClient(HttpClient http, URI endpoint, String model, String apiKey,
                                  Duration requestTimeout, ResponseFormat responseFormat, String reasoningEffort) {
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
        this.requestTimeout = requestTimeout;
        this.responseFormat = responseFormat;
        this.reasoningEffort = reasoningEffort;
    }

    static ChatCompletionsClient fromEnvironment(HttpClient http, String command, String schemaResource)
            throws IOException {
        var url = requiredEnv("PLAYLY_LLM_URL", command);
        URI endpoint;
        try {
            endpoint = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("PLAYLY_LLM_URL is not a valid URL.");
        }
        var reasoningEffort = System.getenv("PLAYLY_LLM_REASONING_EFFORT");
        return new ChatCompletionsClient(http, endpoint, requiredEnv("PLAYLY_LLM_MODEL", command),
                System.getenv().getOrDefault("PLAYLY_LLM_API_KEY", ""), timeoutFromEnvironment(),
                responseFormatFromEnvironment(command, schemaResource),
                reasoningEffort == null || reasoningEffort.isBlank() ? null : reasoningEffort);
    }

    private static ResponseFormat responseFormatFromEnvironment(String command, String schemaResource)
            throws IOException {
        if (!Boolean.parseBoolean(System.getenv("PLAYLY_LLM_STRUCTURED_OUTPUT"))) {
            return null;
        }
        try (var stream = ChatCompletionsClient.class.getResourceAsStream(schemaResource)) {
            if (stream == null) {
                throw new IOException("Missing model response schema: " + schemaResource);
            }
            var schema = JSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonElement.class);
            return new ResponseFormat("json_schema", new ResponseSchema(command, true, schema));
        }
    }

    private static Duration timeoutFromEnvironment() {
        var value = System.getenv("PLAYLY_LLM_TIMEOUT_SECONDS");
        if (value == null || value.isBlank()) {
            return Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);
        }
        try {
            int seconds = Integer.parseInt(value);
            if (seconds > 0) {
                return Duration.ofSeconds(seconds);
            }
        } catch (NumberFormatException ignored) {
            // Report the configuration error without printing the environment value.
        }
        throw new IllegalArgumentException("PLAYLY_LLM_TIMEOUT_SECONDS must be a positive integer.");
    }

    private static String requiredEnv(String name, String command) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Set " + name + " before running " + command + ".");
        }
        return value;
    }

    String complete(String instructions, String prompt) throws IOException, InterruptedException {
        var payload = new Request(model, List.of(new Message("system", instructions), new Message("user", prompt)),
                responseFormat, reasoningEffort);
        var request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
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

    private record Request(String model, List<Message> messages,
                           @SerializedName("response_format") ResponseFormat responseFormat,
                           @SerializedName("reasoning_effort") String reasoningEffort) {}
    private record ResponseFormat(String type, @SerializedName("json_schema") ResponseSchema schema) {}
    private record ResponseSchema(String name, boolean strict, JsonElement schema) {}
    private record Message(String role, String content) {}
    private record Completion(List<Choice> choices) {}
    private record Choice(@SerializedName("finish_reason") String finishReason, Reply message) {}
    private record Reply(JsonElement content, JsonElement refusal) {}
}
