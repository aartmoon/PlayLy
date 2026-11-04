package com.playly.core.games;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.playly.core.security.CurrentUser.userId;

@RestController
@RequestMapping("/api/v1")
class PublicationApi {
    record PublicationRequest(@NotNull UUID versionId) {}

    private final PublicationService publications;

    PublicationApi(PublicationService publications) {
        this.publications = publications;
    }

    @PutMapping("/games/{gameId}/publication")
    GameModels.Publication publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId,
                                    @Valid @RequestBody PublicationRequest body) {
        return publications.publish(gameId, body.versionId(), userId(jwt));
    }

    @DeleteMapping("/games/{gameId}/publication")
    ResponseEntity<Void> unpublish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId) {
        publications.unpublish(gameId, userId(jwt));
        return ResponseEntity.noContent().build();
    }
}
