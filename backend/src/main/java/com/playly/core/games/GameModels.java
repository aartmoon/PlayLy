package com.playly.core.games;

import java.time.OffsetDateTime;
import java.util.UUID;

final class GameModels {
    record Author(UUID id, String displayName) {}
    record Publication(UUID gameId, UUID versionId, OffsetDateTime publishedAt) {}
    record RatingSummary(Double average, long count) {}
    record Game(UUID id, UUID ownerId, Author author, String title, String description, String status,
                Publication publication, RatingSummary rating, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    record Rating(UUID gameId, UUID userId, int score, OffsetDateTime updatedAt) {}
    record Version(UUID id, UUID gameId, int number, UUID generationId, String gameSpecVersion,
                   String runtimeVersion, OffsetDateTime createdAt) {}
    record StoredVersion(Version version, String manifestKey, String manifest) {}
    record Access(UUID ownerId, UUID publishedVersionId) {}

    private GameModels() {}
}
