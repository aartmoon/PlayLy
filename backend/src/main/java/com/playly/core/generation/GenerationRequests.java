package com.playly.core.generation;

import com.playly.core.generation.GenerationModels.Generation;
import com.playly.core.storage.GameStorage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.UUID;

@Component
class GenerationRequests {
    private final ObjectMapper json;
    private final GameStorage storage;

    GenerationRequests(ObjectMapper json, GameStorage storage) {
        this.json = json;
        this.storage = storage;
    }

    String encode(Generation created, String gameSpecVersion, String runtimeVersion, String baseManifestKey) {
        var event = new HashMap<String, Object>();
        event.put("eventId", UUID.randomUUID());
        event.put("schemaVersion", 1);
        event.put("generationId", created.id());
        event.put("gameId", created.gameId());
        event.put("occurredAt", created.createdAt().toInstant().toString());
        event.put("deadlineAt", created.deadlineAt().toInstant().toString());
        event.put("prompt", created.prompt());
        event.put("gameSpecVersion", gameSpecVersion);
        event.put("runtimeVersion", runtimeVersion);
        if (baseManifestKey != null) {
            event.put("baseVersionId", created.baseVersionId());
            event.put("baseManifest", storage.reference(baseManifestKey));
        }
        return json.writeValueAsString(event);
    }
}
