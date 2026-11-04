package com.playly.core.generation;

import com.playly.core.generation.GenerationKafka.Result;
import com.playly.core.generation.GenerationKafka.Type;
import com.playly.core.generation.GenerationModels.Failure;
import com.playly.core.generation.GenerationModels.Target;
import com.playly.core.storage.GameStorage.Manifest;
import com.playly.core.storage.GameStorage.Reference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GenerationResultsTest {
    private final GenerationResultRepository repository = mock(GenerationResultRepository.class);
    private final GenerationDeadlines deadlines = mock(GenerationDeadlines.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final GenerationResults results = new GenerationResults(repository, json, deadlines);
    private final UUID game = UUID.randomUUID();
    private final UUID job = UUID.randomUUID();
    private final UUID event = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-04T10:00:00Z");

    @Test
    void successCanCreateVersionBeforeStartedArrives() {
        acceptEvent();
        var manifest = new Manifest("1.0", "1.0.0", null, List.of());
        var reference = new Reference("playly-games", "attempt/manifest.json");

        results.apply(result(Type.SUCCEEDED, reference, null), manifest, null);

        var order = inOrder(repository, deadlines);
        order.verify(repository).lockGame(game);
        order.verify(repository).lockJob(job, game);
        order.verify(repository).recordEvent(event, job);
        order.verify(deadlines).expireJob(job);
        order.verify(repository).saveVersion(game, job, reference.key(), json.writeValueAsString(manifest));
    }

    @Test
    void duplicateEventDoesNotUpdateTheJob() {
        lockJob(null);

        results.apply(result(Type.STARTED, null, null), null, null);

        verify(repository).recordEvent(event, job);
        verify(repository, never()).markRunning(job);
        verifyNoInteractions(deadlines);
    }

    @Test
    void lateSuccessCannotCreateVersion() {
        acceptEvent();
        when(deadlines.expireJob(job)).thenReturn(true);

        results.apply(result(Type.SUCCEEDED, new Reference("playly-games", "manifest.json"), null),
                new Manifest("1.0", "1.0.0", null, List.of()), null);

        verify(repository, never()).saveVersion(any(), any(), anyString(), anyString());
    }

    @Test
    void terminalJobIgnoresLateStartedEvent() {
        lockJob(now);

        results.apply(result(Type.STARTED, null, null), null, null);

        verify(repository, never()).recordEvent(any(), any());
        verify(repository, never()).markRunning(job);
        verifyNoInteractions(deadlines);
    }

    @Test
    void failureIsStoredWithoutCreatingVersion() {
        acceptEvent();
        var failure = new Failure("INVALID_GAME_SPEC", "Invalid generated game package");

        results.apply(result(Type.FAILED, null, failure), null, failure);

        verify(repository).markFailed(job, failure);
        verify(repository, never()).saveVersion(any(), any(), anyString(), anyString());
    }

    private void acceptEvent() {
        lockJob(null);
        when(repository.recordEvent(event, job)).thenReturn(true);
    }

    private void lockJob(OffsetDateTime finishedAt) {
        when(repository.lockGame(game)).thenReturn(true);
        when(repository.lockJob(job, game)).thenReturn(Optional.of(
                new Target(game, "1.0", "1.0.0", now.plusMinutes(10), finishedAt, false)));
    }

    private Result result(Type type, Reference reference, Failure failure) {
        return new Result(event, 1, job, game, now.toInstant(), type, reference, failure);
    }
}
