package com.playly.core.generation;

import com.playly.core.games.GameAccess;
import com.playly.core.games.VersionService;
import com.playly.core.generation.GenerationModels.Generation;
import com.playly.core.web.ApiProblem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Service
class GenerationService {
    private final GenerationAdmissions admissions;
    private final GenerationQueries queries;
    private final GenerationDeadlines deadlines;
    private final GenerationRequests requests;
    private final GameAccess games;
    private final VersionService versions;
    private final Duration deadline;
    private final int userLimit;
    private final String gameSpecVersion;
    private final String runtimeVersion;

    GenerationService(GenerationAdmissions admissions, GenerationQueries queries, GenerationDeadlines deadlines,
                      GenerationRequests requests, GameAccess games, VersionService versions,
                      @Value("${playly.generation.deadline}") Duration deadline,
                      @Value("${playly.generation.active-per-user}") int userLimit,
                      @Value("${playly.generation.game-spec-version}") String gameSpecVersion,
                      @Value("${playly.generation.runtime-version}") String runtimeVersion) {
        this.admissions = admissions;
        this.queries = queries;
        this.deadlines = deadlines;
        this.requests = requests;
        this.games = games;
        this.versions = versions;
        this.deadline = deadline;
        this.userLimit = userLimit;
        this.gameSpecVersion = gameSpecVersion;
        this.runtimeVersion = runtimeVersion;
    }

    @Transactional
    Generation create(UUID game, UUID owner, String key, String prompt, UUID baseVersion) {
        // Serialize admissions per owner, then take the game lock.
        admissions.lockUser(owner);
        games.requireOwner(game, owner, true);
        deadlines.expireGame(game);

        var existing = queries.byKey(game, key);
        if (existing.isPresent()) {
            var accepted = existing.get();
            if (!accepted.prompt().equals(prompt) || !Objects.equals(accepted.baseVersionId(), baseVersion)) {
                throw ApiProblem.conflict("IDEMPOTENCY_KEY_REUSED", "This key was used with a different request");
            }
            return accepted;
        }

        if (admissions.hasActiveJob(game)) {
            throw ApiProblem.conflict("GENERATION_IN_PROGRESS", "A generation is already in progress");
        }
        if (admissions.activeJobs(owner) >= userLimit) {
            throw new ApiProblem(HttpStatus.TOO_MANY_REQUESTS, "GENERATION_LIMIT_EXCEEDED", "Too many active generations", 3);
        }
        String baseKey = baseVersion == null ? null : versions.baseManifestKey(game, baseVersion);
        UUID id = UUID.randomUUID();
        var created = admissions.insert(id, game, key, prompt, baseVersion, gameSpecVersion, runtimeVersion, deadline);
        admissions.enqueue(id, requests.encode(created, gameSpecVersion, runtimeVersion, baseKey));
        return created;
    }
}
