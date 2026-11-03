package com.playly.ai;

import com.google.gson.Gson;
import com.playly.ai.generated.kafka.GenerationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationContractTest {
    @Test
    void readsSuccessfulGenerationEvent() {
        var event = new Gson().fromJson("""
                {
                  "eventId": "12e9576c-f80c-4fdc-85b4-000f08b99b86",
                  "schemaVersion": 1,
                  "generationId": "8220d353-86d5-4843-9019-3adbc54cbb12",
                  "gameId": "031aa8ba-28c8-4c33-a8b2-b2187f3475c3",
                  "occurredAt": "2026-11-03T12:00:00Z",
                  "type": "SUCCEEDED",
                  "manifest": {"bucket": "playly", "key": "game/manifest.json"}
                }
                """, GenerationResult.class);

        assertEquals("8220d353-86d5-4843-9019-3adbc54cbb12", event.getGenerationId().toString());
        assertEquals("SUCCEEDED", event.getType().name());
        assertEquals("game/manifest.json", event.getManifest().getKey());
    }
}
