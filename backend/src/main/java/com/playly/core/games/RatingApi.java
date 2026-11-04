package com.playly.core.games;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.playly.core.security.CurrentUser.userId;

@RestController
@RequestMapping("/api/v1")
class RatingApi {
    record RatingRequest(@NotNull @Min(1) @Max(5) Integer score) {}

    private final RatingService ratings;

    RatingApi(RatingService ratings) {
        this.ratings = ratings;
    }

    @GetMapping("/games/{gameId}/rating")
    ResponseEntity<GameModels.Rating> rating(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId) {
        return ratings.rating(gameId, userId(jwt)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/games/{gameId}/rating")
    GameModels.Rating rate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId,
                            @Valid @RequestBody RatingRequest body) {
        return ratings.rate(gameId, userId(jwt), body.score());
    }

    @DeleteMapping("/games/{gameId}/rating")
    ResponseEntity<Void> deleteRating(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId) {
        ratings.deleteRating(gameId, userId(jwt));
        return ResponseEntity.noContent().build();
    }
}
