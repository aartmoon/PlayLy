package com.playly.core.generation;

import com.playly.core.web.Pages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

import static com.playly.core.security.CurrentUser.userId;

@RestController
@RequestMapping("/api/v1")
class GenerationApi {
    record Request(@NotBlank @Size(max = 10000) String prompt, UUID baseVersionId) {}

    private final GenerationService generations;
    private final GenerationQueries queries;

    GenerationApi(GenerationService generations, GenerationQueries queries) {
        this.generations = generations;
        this.queries = queries;
    }

    @PostMapping("/games/{gameId}/generations")
    ResponseEntity<GenerationModels.Generation> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId,
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 128) String key, @Valid @RequestBody Request body) {
        var generation = generations.create(gameId, userId(jwt), key, body.prompt(), body.baseVersionId());
        return ResponseEntity.accepted().location(URI.create("/api/v1/generations/" + generation.id()))
                .header("Retry-After", "3").body(generation);
    }

    @GetMapping("/generations/{generationId}")
    GenerationModels.Generation get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID generationId) {
        return queries.get(generationId, userId(jwt));
    }

    @GetMapping("/games/{gameId}/generations")
    Pages.Page<GenerationModels.Generation> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId,
            @RequestParam(required = false) @Size(min = 1, max = 2048) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return queries.history(gameId, userId(jwt), cursor, limit);
    }
}
