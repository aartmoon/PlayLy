package com.playly.core.generation;

import com.playly.core.generation.GenerationModels.Failure;
import com.playly.core.storage.GameStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
class GenerationResults {
    private final GenerationResultRepository results;
    private final ObjectMapper json;
    private final GenerationDeadlines deadlines;

    GenerationResults(GenerationResultRepository results, ObjectMapper json, GenerationDeadlines deadlines) {
        this.results = results;
        this.json = json;
        this.deadlines = deadlines;
    }

    @Transactional
    void apply(GenerationKafka.Result event, GameStorage.Manifest manifest, Failure failure) {
        if (!results.lockGame(event.gameId())) return;

        var id = event.generationId();
        var current = results.lockJob(id, event.gameId());
        if (current.isEmpty() || current.get().finishedAt() != null) return;
        if (!results.recordEvent(event.eventId(), id) || deadlines.expireJob(id)) return;

        if (event.type() == GenerationKafka.Type.STARTED) {
            results.markRunning(id);
        } else if (failure != null) {
            results.markFailed(id, failure);
        } else {
            if (manifest == null) throw new IllegalStateException("Successful result has no verified manifest");
            results.saveVersion(event.gameId(), id, event.manifest().key(), json.writeValueAsString(manifest));
        }
    }
}
