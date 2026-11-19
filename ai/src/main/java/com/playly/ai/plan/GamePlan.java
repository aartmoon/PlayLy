package com.playly.ai.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

record GamePlan(String entryPoint, List<String> assetRefs, List<Requirement> requirements,
                List<FileGroup> groups) {
    private static final int MAX_GROUPS = 16;
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final Pattern SOURCE_PATH =
            Pattern.compile("src/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.ts");

    record Requirement(String id, String description) {}
    record FileGroup(String id, List<String> dependsOn, List<SourceFile> files) {}
    record SourceFile(String path, String purpose, List<String> contracts) {}

    static GamePlan parse(String response, Set<String> allowedAssets) {
        JsonElement value;
        try {
            value = JSON.fromJson(response, JsonElement.class);
        } catch (JsonParseException exception) {
            throw invalid("expected a JSON object");
        }
        var root = object(value, "entryPoint", "assetRefs", "requirements", "groups");
        var plan = new GamePlan(
                path(root.get("entryPoint")),
                array(root, "assetRefs", GamePlan::text),
                array(root, "requirements", GamePlan::requirement),
                array(root, "groups", GamePlan::group));
        plan.validate(allowedAssets);
        return plan;
    }

    private void validate(Set<String> allowedAssets) {
        if (!allowedAssets.containsAll(assetRefs) || Set.copyOf(assetRefs).size() != assetRefs.size()) {
            throw invalid("assetRefs must contain distinct IDs from the supplied catalog");
        }
        if (requirements.isEmpty() || groups.isEmpty()) {
            throw invalid("requirements and groups must be non-empty");
        }
        if (groups.size() > MAX_GROUPS) {
            throw invalid("at most " + MAX_GROUPS + " groups are supported");
        }
        var requirementIds = new HashSet<String>();
        for (var requirement : requirements) {
            if (!requirementIds.add(requirement.id())) {
                throw invalid("requirement IDs must be unique");
            }
        }
        var earlierGroups = new HashSet<String>();
        var filePaths = new HashSet<String>();
        for (var group : groups) {
            if (!earlierGroups.containsAll(group.dependsOn())
                    || Set.copyOf(group.dependsOn()).size() != group.dependsOn().size()) {
                throw invalid("group dependencies must be distinct and refer to earlier groups");
            }
            if (!earlierGroups.add(group.id()) || group.files().isEmpty()) {
                throw invalid("group IDs must be unique and each group must contain files");
            }
            for (var file : group.files()) {
                if (!filePaths.add(file.path())) {
                    throw invalid("each file path must appear exactly once");
                }
            }
        }
        if (!filePaths.contains(entryPoint)) {
            throw invalid("entryPoint must be one of the planned files");
        }
    }

    private static Requirement requirement(JsonElement value) {
        var object = object(value, "id", "description");
        return new Requirement(text(object.get("id")), text(object.get("description")));
    }

    private static FileGroup group(JsonElement value) {
        var object = object(value, "id", "dependsOn", "files");
        return new FileGroup(text(object.get("id")), array(object, "dependsOn", GamePlan::text),
                array(object, "files", GamePlan::sourceFile));
    }

    private static SourceFile sourceFile(JsonElement value) {
        var object = object(value, "path", "purpose", "contracts");
        return new SourceFile(path(object.get("path")), text(object.get("purpose")),
                array(object, "contracts", GamePlan::text));
    }

    private static JsonObject object(JsonElement value, String... fields) {
        if (value == null || !value.isJsonObject() || !value.getAsJsonObject().keySet().equals(Set.of(fields))) {
            throw invalid("expected an object with fields " + String.join(", ", fields));
        }
        return value.getAsJsonObject();
    }

    private static <T> List<T> array(JsonObject object, String field, Function<JsonElement, T> decode) {
        var value = object.get(field);
        if (!value.isJsonArray()) {
            throw invalid(field + " must be an array");
        }
        var items = new ArrayList<T>();
        for (var item : value.getAsJsonArray()) {
            items.add(decode.apply(item));
        }
        return List.copyOf(items);
    }

    private static String text(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw invalid("expected a non-blank string");
        }
        return value.getAsString();
    }

    private static String path(JsonElement value) {
        var path = text(value);
        if (!SOURCE_PATH.matcher(path).matches()) {
            throw invalid("expected a relative TypeScript path under src/");
        }
        return path;
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid game plan: " + reason + ".");
    }
}
