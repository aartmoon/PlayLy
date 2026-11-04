package com.playly.core.generation;

import com.playly.core.games.GameAccess;
import com.playly.core.games.VersionService;
import com.playly.core.generation.GenerationModels.Generation;
import com.playly.core.web.ApiProblem;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GenerationServiceTest {
    private final GenerationAdmissions admissions = mock(GenerationAdmissions.class);
    private final GenerationQueries queries = mock(GenerationQueries.class);
    private final GenerationDeadlines deadlines = mock(GenerationDeadlines.class);
    private final GenerationRequests requests = mock(GenerationRequests.class);
    private final GameAccess games = mock(GameAccess.class);
    private final VersionService versions = mock(VersionService.class);
    private final Duration timeout = Duration.ofMinutes(10);
    private final GenerationService service = new GenerationService(
            admissions, queries, deadlines, requests, games, versions, timeout, 3, "1.0", "1.0.0");
    private final UUID game = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-04T10:00:00Z");

    @Test
    void retryReturnsExistingJobBeforeCheckingLimits() {
        var existing = job(UUID.randomUUID(), null);
        when(queries.byKey(game, "key")).thenReturn(Optional.of(existing));

        assertSame(existing, service.create(game, owner, "key", "Make a game", null));

        var order = inOrder(admissions, games, deadlines, queries);
        order.verify(admissions).lockUser(owner);
        order.verify(games).requireOwner(game, owner, true);
        order.verify(deadlines).expireGame(game);
        order.verify(queries).byKey(game, "key");
        verifyNoMoreInteractions(admissions);
        verifyNoInteractions(requests, versions);
    }

    @Test
    void rejectsReusedKeyWithDifferentBaseVersion() {
        when(queries.byKey(game, "key")).thenReturn(Optional.of(job(UUID.randomUUID(), null)));

        var error = assertThrows(ApiProblem.class,
                () -> service.create(game, owner, "key", "Make a game", UUID.randomUUID()));

        assertEquals("IDEMPOTENCY_KEY_REUSED", error.body().get("code"));
        verifyNoInteractions(requests, versions);
    }

    @Test
    void rejectsSecondActiveJobForTheSameGame() {
        when(admissions.hasActiveJob(game)).thenReturn(true);

        var error = assertThrows(ApiProblem.class,
                () -> service.create(game, owner, "key", "Make a game", null));

        assertEquals("GENERATION_IN_PROGRESS", error.body().get("code"));
        verify(admissions, never()).activeJobs(owner);
        verifyNoInteractions(requests);
    }

    @Test
    void rejectsNewJobWhenOwnerIsAtLimit() {
        when(admissions.activeJobs(owner)).thenReturn(3L);

        var error = assertThrows(ApiProblem.class,
                () -> service.create(game, owner, "key", "Make a game", null));

        assertEquals(429, error.body().get("status"));
        assertEquals("GENERATION_LIMIT_EXCEEDED", error.body().get("code"));
        verifyNoInteractions(requests);
    }

    @Test
    void insertsJobAndEnqueuesRequestForSameGeneration() {
        UUID base = UUID.randomUUID();
        when(versions.baseManifestKey(game, base)).thenReturn("base/manifest.json");
        when(admissions.insert(any(UUID.class), eq(game), eq("key"), eq("Make a game"), eq(base),
                eq("1.0"), eq("1.0.0"), eq(timeout)))
                .thenAnswer(call -> job(call.getArgument(0), base));
        when(requests.encode(any(Generation.class), eq("1.0"), eq("1.0.0"), eq("base/manifest.json")))
                .thenReturn("event-payload");

        var created = service.create(game, owner, "key", "Make a game", base);

        var order = inOrder(admissions, requests);
        order.verify(admissions).insert(created.id(), game, "key", "Make a game", base, "1.0", "1.0.0", timeout);
        order.verify(requests).encode(created, "1.0", "1.0.0", "base/manifest.json");
        order.verify(admissions).enqueue(created.id(), "event-payload");
    }

    private Generation job(UUID id, UUID base) {
        return new Generation(id, game, "Make a game", base, "QUEUED", now.plus(timeout), null, null, now, now, null);
    }
}
