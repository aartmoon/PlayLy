package com.playly.core.generation;

import com.playly.core.generation.GenerationModels.Failure;
import com.playly.core.storage.GameStorage;
import com.playly.core.storage.PackageVerifier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "playly.kafka.enabled", matchIfMissing = true)
class GenerationKafka {
    static final String REQUEST = "game.generation.request";
    static final String RESULT = "game.generation.result";
    static final String DLT = "game.generation.result.DLT";

    enum Type { STARTED, SUCCEEDED, FAILED }

    record Result(UUID eventId, Integer schemaVersion, UUID generationId, UUID gameId, Instant occurredAt,
                  Type type, GameStorage.Reference manifest, Failure error) {}

    private static final Map<String, String> FAILURES = Map.of(
            "LLM_UNAVAILABLE", "Generation provider is unavailable",
            "GENERATION_TIMEOUT", "Generation deadline exceeded",
            "UNSUPPORTED_GAME_REQUEST", "Requested game mechanics are unsupported",
            "INVALID_GAME_SPEC", "Invalid generated game package",
            "STORAGE_ERROR", "Game package could not be stored",
            "INTERNAL_ERROR", "Generation failed");

    static final class InvalidEvent extends RuntimeException {
        InvalidEvent() {
            super("Invalid generation result event");
        }
    }

    private final ObjectMapper json;
    private final GenerationResults generations;
    private final GenerationResultRepository results;
    private final PackageVerifier verifier;

    GenerationKafka(ObjectMapper json, GenerationResults generations, GenerationResultRepository results,
                    PackageVerifier verifier) {
        this.json = json;
        this.generations = generations;
        this.results = results;
        this.verifier = verifier;
    }

    @KafkaListener(topics = RESULT)
    void receive(ConsumerRecord<String, String> record) {
        var event = parse(record);
        var target = results.target(event.generationId(), event.eventId()).orElse(null);
        if (target == null) return;
        if (!target.gameId().equals(event.gameId())) throw new InvalidEvent();
        if (target.finishedAt() != null) return;

        GameStorage.Manifest manifest = null;
        Failure failure = null;
        if (event.type() == Type.FAILED) {
            String code = event.error().code();
            failure = new Failure(code, FAILURES.get(code));
        } else if (event.type() == Type.SUCCEEDED) {
            if (target.expired()) {
                generations.apply(event, null, null);
                return;
            }
            try {
                manifest = verifier.verify(event.generationId(), event.manifest(), target.gameSpecVersion(),
                        target.runtimeVersion(), target.deadlineAt().toInstant());
            } catch (GameStorage.InvalidPackage error) {
                failure = new Failure("INVALID_GAME_SPEC", FAILURES.get("INVALID_GAME_SPEC"));
            }
        }
        generations.apply(event, manifest, failure);
    }

    private Result parse(ConsumerRecord<String, String> record) {
        if (record.value() == null) throw new InvalidEvent();
        Result event;
        try {
            event = json.readValue(record.value(), Result.class);
        } catch (JacksonException error) {
            throw new InvalidEvent();
        }
        if (event == null || event.eventId() == null || !Integer.valueOf(1).equals(event.schemaVersion())
                || event.generationId() == null || !event.generationId().toString().equals(record.key())
                || event.gameId() == null || event.occurredAt() == null || event.type() == null
                || (event.manifest() != null) != (event.type() == Type.SUCCEEDED)
                || (event.error() != null) != (event.type() == Type.FAILED)
                || (event.manifest() != null && (event.manifest().bucket() == null || event.manifest().key() == null))
                || (event.error() != null && (event.error().code() == null || !FAILURES.containsKey(event.error().code())
                    || event.error().message() == null))) {
            throw new InvalidEvent();
        }
        return event;
    }
}
