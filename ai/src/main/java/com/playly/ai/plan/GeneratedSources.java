package com.playly.ai.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

record GeneratedSources(int responseVersion, List<SourceFile> files) {
    static final int VERSION = 1;
    private static final int MAX_GROUP_RESPONSE_BYTES = 128 * 1024;
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    record SourceFile(String name, String path, String content) {}

    static List<SourceFile> parseGroup(String response, GamePlan.FileGroup group) {
        if (response.getBytes(StandardCharsets.UTF_8).length > MAX_GROUP_RESPONSE_BYTES) {
            throw invalid("response exceeds the size limit");
        }
        JsonElement value;
        try {
            value = JSON.fromJson(response, JsonElement.class);
        } catch (JsonParseException exception) {
            throw invalid("expected a JSON object");
        }
        var root = object(value, "responseVersion", "files");
        var version = root.get("responseVersion");
        if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                || !isSupportedVersion(version)) {
            throw invalid("responseVersion must be 1");
        }
        var files = root.get("files");
        var expectedPaths = group.files().stream().map(GamePlan.SourceFile::path).toList();
        if (!files.isJsonArray() || files.getAsJsonArray().size() != expectedPaths.size()) {
            throw invalid("return exactly the requested files");
        }

        var expected = Set.copyOf(expectedPaths);
        var byPath = new HashMap<String, SourceFile>();
        for (var item : files.getAsJsonArray()) {
            var file = object(item, "name", "path", "content");
            var name = text(file.get("name"));
            var directory = text(file.get("path"));
            var content = text(file.get("content"));
            var fullPath = directory + "/" + name;
            if (name.contains("/") || !expected.contains(fullPath)
                    || byPath.putIfAbsent(fullPath, new SourceFile(name, directory, content)) != null) {
                throw invalid("file paths must match the plan without duplicates");
            }
        }
        return expectedPaths.stream().map(byPath::get).toList();
    }

    private static boolean isSupportedVersion(JsonElement version) {
        try {
            return version.getAsBigDecimal().compareTo(BigDecimal.valueOf(VERSION)) == 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static JsonObject object(JsonElement value, String... fields) {
        if (value == null || !value.isJsonObject() || !value.getAsJsonObject().keySet().equals(Set.of(fields))) {
            throw invalid("expected an object with fields " + String.join(", ", fields));
        }
        return value.getAsJsonObject();
    }

    private static String text(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw invalid("name, path and content must be non-blank strings");
        }
        return value.getAsString();
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid generated files: " + reason + ".");
    }
}
