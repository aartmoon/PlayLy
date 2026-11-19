package com.playly.ai.plan;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSourceGeneratorTest {
    private static final String PROMPT = "Кот собирает монеты";
    private static final GamePlan PLAN = GamePlan.parse("""
            {
              "entryPoint": "src/main.ts",
              "assetRefs": [],
              "requirements": [{"id": "coins", "description": "Собирать монеты"}],
              "groups": [
                {"id": "rules", "dependsOn": [], "files": [
                  {"path": "src/rules.ts", "purpose": "Coin rules", "contracts": ["export function points(): number"]}
                ]},
                {"id": "scene", "dependsOn": ["rules"], "files": [
                  {"path": "src/scenes/CatScene.ts", "purpose": "Play scene", "contracts": ["export CatScene"]}
                ]},
                {"id": "boot", "dependsOn": ["scene"], "files": [
                  {"path": "src/main.ts", "purpose": "Start Phaser", "contracts": []},
                  {"path": "src/settings.ts", "purpose": "Game settings", "contracts": []}
                ]}
              ]
            }
            """, Set.of());
    private static final String RULES = "export const points = () => 10;";
    private static final String SCENE = "import { points } from '../rules';\nexport class CatScene {}";

    @Test
    void generatesInPlanOrderWithOnlyDeclaredDependencySources() throws Exception {
        var requests = new ArrayList<JsonObject>();
        var generator = new GameSourceGenerator((instructions, context) -> {
            assertTrue(instructions.contains("PlayLy generated file group"));
            var request = JsonParser.parseString(context).getAsJsonObject();
            requests.add(request);
            assertEquals(PROMPT, request.get("brief").getAsString());
            assertEquals("coins", request.getAsJsonArray("requirements").get(0).getAsJsonObject().get("id").getAsString());
            return switch (request.getAsJsonObject("group").get("id").getAsString()) {
                case "rules" -> response(file("rules.ts", "src", RULES));
                case "scene" -> response(file("CatScene.ts", "src/scenes", SCENE));
                case "boot" -> response(file("settings.ts", "src", "export const width = 320;"),
                        file("main.ts", "src", "import { CatScene } from './scenes/CatScene';"));
                default -> throw new AssertionError("Unexpected group");
            };
        });

        var sources = generator.generate(PLAN, PROMPT);

        assertEquals(1, sources.responseVersion());
        assertEquals(List.of("rules.ts", "CatScene.ts", "main.ts", "settings.ts"),
                sources.files().stream().map(GeneratedSources.SourceFile::name).toList());
        assertEquals(3, requests.size());
        assertTrue(requests.get(0).getAsJsonArray("dependencies").isEmpty());
        assertEquals(RULES, dependencySource(requests.get(1)));
        assertEquals(SCENE, dependencySource(requests.get(2)));
    }

    @Test
    void retriesOnlyTheRejectedGroupAndStopsAfterTheSecondInvalidResponse() {
        var instructions = new ArrayList<String>();
        var contexts = new ArrayList<String>();
        var generator = new GameSourceGenerator((system, context) -> {
            instructions.add(system);
            contexts.add(context);
            if (contexts.size() == 1) {
                return response(file("rules.ts", "src", RULES));
            }
            return response(file("OtherScene.ts", "src/scenes", SCENE));
        });

        assertThrows(IllegalArgumentException.class, () -> generator.generate(PLAN, PROMPT));
        assertEquals(3, contexts.size());
        assertEquals(contexts.get(1), contexts.get(2));
        assertTrue(instructions.get(2).contains("previous response was rejected"));
    }

    @Test
    void acceptsAValidCorrectionAndContinues() throws Exception {
        var calls = new ArrayList<String>();
        var replies = List.of("plain text", response(file("rules.ts", "src", RULES)),
                response(file("CatScene.ts", "src/scenes", SCENE)),
                response(file("main.ts", "src", "new Phaser.Game(config);"),
                        file("settings.ts", "src", "export const width = 320;")));
        var generator = new GameSourceGenerator((system, context) -> {
            calls.add(context);
            return replies.get(calls.size() - 1);
        });

        assertEquals(4, generator.generate(PLAN, PROMPT).files().size());
        assertEquals(4, calls.size());
        assertEquals(calls.get(0), calls.get(1));
    }

    @Test
    void rejectsWrongVersionMissingFilesDuplicateFilesAndInvalidPaths() {
        var group = PLAN.groups().getLast();
        var main = file("main.ts", "src", "export {};");
        var settings = file("settings.ts", "src", "export {};");
        var valid = response(main, settings);
        for (var invalid : List.of(valid.replace("\"responseVersion\":1", "\"responseVersion\":2"),
                response(main), response(main, main),
                response(main, file("settings.ts", "src/../src", "export {};")),
                response(main, file("settings.ts", "src", " ")),
                response(main, file("settings.ts", "src", "x".repeat(128 * 1024))))) {
            assertThrows(IllegalArgumentException.class, () -> GeneratedSources.parseGroup(invalid, group));
        }
    }

    private static String dependencySource(JsonObject request) {
        var dependencies = request.getAsJsonArray("dependencies");
        assertEquals(1, dependencies.size());
        return dependencies.get(0).getAsJsonObject().get("content").getAsString();
    }

    private static String response(GeneratedSources.SourceFile... files) {
        return new Gson().toJson(new GeneratedSources(1, List.of(files)));
    }

    private static GeneratedSources.SourceFile file(String name, String path, String source) {
        return new GeneratedSources.SourceFile(name, path, source);
    }
}
