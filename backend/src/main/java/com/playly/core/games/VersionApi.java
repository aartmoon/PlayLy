package com.playly.core.games;

import com.playly.core.storage.GameStorage;
import com.playly.core.web.Pages;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.playly.core.security.CurrentUser.userId;

@RestController
@RequestMapping("/api/v1")
class VersionApi {
    private final VersionService versions;

    VersionApi(VersionService versions) {
        this.versions = versions;
    }

    @GetMapping("/games/{gameId}/versions")
    Pages.Page<GameModels.Version> versions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId,
                                             @RequestParam(required = false) @Size(min = 1, max = 2048) String cursor,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return versions.versions(gameId, userId(jwt), cursor, limit);
    }

    @GetMapping("/games/{gameId}/versions/{versionId}")
    GameModels.Version version(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId, @PathVariable UUID versionId) {
        return versions.version(gameId, versionId, userId(jwt));
    }

    @GetMapping("/games/{gameId}/versions/{versionId}/package")
    ResponseEntity<GameStorage.GamePackage> gamePackage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gameId,
                                                        @PathVariable UUID versionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(versions.gamePackage(gameId, versionId, userId(jwt)));
    }
}
