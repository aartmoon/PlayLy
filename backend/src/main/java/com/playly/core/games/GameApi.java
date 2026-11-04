package com.playly.core.games;

import com.playly.core.web.Pages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

import static com.playly.core.security.CurrentUser.userId;

@RestController
@RequestMapping("/api/v1")
class GameApi {
    record Metadata(@NotBlank @Size(max = 120) String title, @NotNull @Size(max = 2000) String description) {}

    private final GameService games;

    GameApi(GameService games) {
        this.games = games;
    }

    @GetMapping("/me/games")
    Pages.Page<GameModels.Game> mine(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(required = false) @Size(min = 1, max = 2048) String cursor,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return games.ownGames(userId(jwt), cursor, limit);
    }

    @GetMapping("/catalog/games")
    Pages.Page<GameModels.Game> catalog(@RequestParam(required = false) @Size(min = 1, max = 200) String q,
                                        @RequestParam(required = false) @Size(min = 1, max = 2048) String cursor,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return games.catalog(q, cursor, limit);
    }

    @PostMapping("/games")
    ResponseEntity<GameModels.Game> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Metadata body) {
        var game = games.create(userId(jwt), body.title(), body.description());
        return ResponseEntity.created(URI.create("/api/v1/games/" + game.id())).body(game);
    }

    @GetMapping("/games/{gameId}")
    GameModels.Game get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId) {
        return games.get(gameId, userId(jwt));
    }

    @PutMapping("/games/{gameId}")
    GameModels.Game update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId, @Valid @RequestBody Metadata body) {
        return games.update(gameId, userId(jwt), body.title(), body.description());
    }

    @DeleteMapping("/games/{gameId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId) {
        games.delete(gameId, userId(jwt));
        return ResponseEntity.noContent().build();
    }
}
