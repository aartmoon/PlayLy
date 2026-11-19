package com.playly.ai.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

record GeneratedSources(List<SourceFile> files, boolean done, String remaining) {
    static final String ENTRY_POINT = "src/main.ts";
    static final int MAX_SOURCE_BYTES = 128 * 1024;
    private static final int MAX_FILES = 100;
    private static final Pattern PATH = Pattern.compile("src/(?:[a-zA-Z0-9_-]+/)*[a-zA-Z0-9_-][a-zA-Z0-9_.-]*\\.ts");
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    record SourceFile(String path, String content) {}

    static GeneratedSources parse(String response) {
        if (bytes(response) > 384 * 1024) {
            throw invalid("response exceeds the size limit");
        }
        JsonElement value;
        try {
            value = JSON.fromJson(response, JsonElement.class);
        } catch (JsonParseException exception) {
            throw invalid("expected a JSON object");
        }
        var root = object(value, "files", "done", "remaining");
        var done = root.get("done");
        var remaining = root.get("remaining");
        var files = root.get("files");
        if (!done.isJsonPrimitive() || !done.getAsJsonPrimitive().isBoolean()
                || !remaining.isJsonPrimitive() || !remaining.getAsJsonPrimitive().isString()
                || !files.isJsonArray() || files.getAsJsonArray().isEmpty()
                || files.getAsJsonArray().size() > MAX_FILES) {
            throw invalid("expected a non-empty file list, a boolean done and a string remaining");
        }
        var instructions = remaining.getAsString();
        if (bytes(instructions) > 4096 || (done.getAsBoolean() != instructions.isBlank())) {
            throw invalid("remaining must describe the next work, or be empty when done");
        }
        Map<String, SourceFile> batch = new LinkedHashMap<>();
        for (var item : files.getAsJsonArray()) {
            var file = object(item, "path", "content");
            var path = string(file.get("path"));
            var content = string(file.get("content"));
            if (path.length() > 240 || !PATH.matcher(path).matches() || path.endsWith(".d.ts")) {
                throw invalid("only relative TypeScript file paths under src/ are supported");
            }
            if (bytes(content) > MAX_SOURCE_BYTES || batch.putIfAbsent(path, new SourceFile(path, content)) != null) {
                throw invalid("each file must be unique and within the per-file size limit");
            }
        }
        return new GeneratedSources(List.copyOf(batch.values()), done.getAsBoolean(), instructions);
    }

    static void validateProject(Map<String, SourceFile> files) {
        if (files.size() > MAX_FILES || files.values().stream().mapToInt(file -> bytes(file.content())).sum() > 256 * 1024) {
            throw invalid("project exceeds the file count or source size limit");
        }
    }

    private static JsonObject object(JsonElement value, String... fields) {
        if (value == null || !value.isJsonObject() || !value.getAsJsonObject().keySet().equals(Set.of(fields))) {
            throw invalid("expected an object with fields " + String.join(", ", fields));
        }
        return value.getAsJsonObject();
    }

    private static String string(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw invalid("file paths and contents must be non-blank strings");
        }
        return value.getAsString();
    }

    private static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid generated files: " + message + ".");
    }
}
