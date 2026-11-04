package com.playly.core.generation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

final class GenerationModels {
    record Failure(String code, String message) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Generation(UUID id, UUID gameId, String prompt, UUID baseVersionId, String status, OffsetDateTime deadlineAt,
                      UUID resultVersionId, Failure error, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                      OffsetDateTime finishedAt) {}

    record Target(UUID gameId, String gameSpecVersion, String runtimeVersion, OffsetDateTime deadlineAt,
                  OffsetDateTime finishedAt, boolean expired) {}

    private GenerationModels() {}
}
