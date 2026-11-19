package com.playly.ai.plan;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSourceGeneratorTest {
    @Test
    void sendsExamplesFirstAndCarriesForwardAcceptedFiles() throws Exception {
        var contexts = new ArrayList<String>();
        var generator = new GameSourceGenerator((instructions, context) -> {
            contexts.add(context);
            return contexts.size() == 1
                    ? batch("src/scenes/Game.ts", "class Game {}", false)
                    : batch("src/main.ts", "import './scenes/Game'", true);
        });
        var files = generator.generate("Make a tower defense", Map.of("Main.ts", "example"), List::copyOf);

        assertEquals(List.of("src/scenes/Game.ts", "src/main.ts"),
                files.stream().map(GeneratedSources.SourceFile::path).toList());
        var first = JsonParser.parseString(contexts.getFirst()).getAsJsonObject();
        var second = JsonParser.parseString(contexts.getLast()).getAsJsonObject();
        assertEquals("example", first.getAsJsonObject("examples").get("Main.ts").getAsString());
        assertEquals("class Game {}", second.getAsJsonArray("currentFiles").get(0).getAsJsonObject()
                .get("content").getAsString());
        assertEquals("Connect the entry point", second.get("remaining").getAsString());
    }

    @Test
    void limitsIncompleteGenerationToFiveCalls() throws Exception {
        var calls = new AtomicInteger();
        var generator = new GameSourceGenerator((instructions, context) -> {
            calls.incrementAndGet();
            return batch("src/scenes/Game.ts", "class Game {}", false);
        });
        var failure = assertThrows(IOException.class, () -> generator.generate("Make a platformer", Map.of(), files -> {
            throw new AssertionError("An incomplete game reached the sandbox");
        }));
        assertTrue(failure.getMessage().contains("five"));
        assertEquals(5, calls.get());
    }

    @Test
    void repairsOnlyAffectedFilesAfterRejectedBuild() throws Exception {
        var calls = new AtomicInteger();
        var builds = new AtomicInteger();
        var generator = new GameSourceGenerator((instructions, context) -> {
            return switch (calls.incrementAndGet()) {
                case 1 -> batch("src/controls.ts", "export const controls = 1", false);
                case 2 -> batch("src/main.ts", "broken", true);
                default -> {
                    assertTrue(context.contains("Missing import"));
                    yield batch("src/main.ts", "fixed", true);
                }
            };
        });
        var result = generator.generate("Make a platformer", Map.of(), files -> {
            if (builds.incrementAndGet() == 1) {
                throw new GameSourceGenerator.RejectedBuild("Missing import");
            }
            return files;
        });

        assertEquals(3, calls.get());
        assertEquals(2, builds.get());
        assertEquals(List.of("export const controls = 1", "fixed"),
                result.stream().map(GeneratedSources.SourceFile::content).toList());
    }

    @Test
    void limitsRepairsAndDoesNotRetryInfrastructureFailures() throws Exception {
        var calls = new AtomicInteger();
        var generator = new GameSourceGenerator((instructions, context) -> {
            calls.incrementAndGet();
            return batch("src/main.ts", "broken", true);
        });

        assertThrows(IOException.class, () -> generator.generate("Make a platformer", Map.of(), files -> {
            throw new GameSourceGenerator.RejectedBuild("Canvas missing");
        }));
        assertEquals(3, calls.get());
        calls.set(0);
        assertThrows(IOException.class, () -> generator.generate("Make a platformer", Map.of(), files -> {
            throw new IOException("Docker stopped");
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void keepsBuildDiagnosticsWhenRepairRequestFails() throws Exception {
        var calls = new AtomicInteger();
        var generator = new GameSourceGenerator((instructions, context) -> {
            if (calls.incrementAndGet() == 1) {
                return batch("src/main.ts", "broken", true);
            }
            throw new IOException("Model API returned HTTP 402.");
        });

        var failure = assertThrows(IOException.class, () -> generator.generate("Make a platformer", Map.of(), files -> {
            throw new GameSourceGenerator.RejectedBuild("TypeScript compilation failed");
        }));
        assertTrue(failure.getMessage().contains("HTTP 402"));
        assertTrue(failure.getMessage().contains("TypeScript compilation failed"));
        assertEquals(2, calls.get());
    }

    @Test
    void rejectsPathsOutsideGeneratedTypescriptAndRequiresEntryPoint() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> GeneratedSources.parse(batch("src/../config.ts", "code", true)));
        assertThrows(IllegalArgumentException.class, () -> GeneratedSources.parse(batch("index.html", "code", true)));
        assertThrows(IllegalArgumentException.class, () -> GeneratedSources.parse(batch("src/global.d.ts", "code", true)));

        var contexts = new ArrayList<String>();
        var generator = new GameSourceGenerator((instructions, context) -> {
            contexts.add(context);
            return contexts.size() == 1
                    ? batch("src/Game.ts", "class Game {}", true)
                    : batch("src/main.ts", "class Main {}", true);
        });
        var files = generator.generate("Make a puzzle", Map.of(), List::copyOf);
        assertEquals(1, files.size());
        assertTrue(contexts.getLast().contains("must include src/main.ts"));
    }

    private static String batch(String path, String content, boolean done) {
        return """
                {"files":[{"path":"%s","content":"%s"}],"done":%s,"remaining":"%s"}
                """.formatted(path, content, done, done ? "" : "Connect the entry point");
    }
}
