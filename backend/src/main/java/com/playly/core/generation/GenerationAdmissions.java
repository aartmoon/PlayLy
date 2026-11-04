package com.playly.core.generation;

import com.playly.core.generation.GenerationModels.Generation;
import com.playly.core.web.ApiProblem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Duration;
import java.util.UUID;

@Repository
class GenerationAdmissions {
    private final JdbcClient db;

    GenerationAdmissions(JdbcClient db) {
        this.db = db;
    }

    void lockUser(UUID owner) {
        db.sql("SELECT id FROM users WHERE id = :owner FOR UPDATE").param("owner", owner)
                .query(UUID.class).optional().orElseThrow(ApiProblem::unauthorized);
    }

    boolean hasActiveJob(UUID game) {
        return db.sql("SELECT EXISTS (SELECT 1 FROM generations WHERE game_id = :game AND finished_at IS NULL)")
                .param("game", game).query(Boolean.class).single();
    }

    long activeJobs(UUID owner) {
        return db.sql("""
                SELECT count(*) FROM generations gen JOIN games g ON g.id = gen.game_id
                WHERE g.owner_id = :owner AND gen.finished_at IS NULL AND gen.deadline_at > clock_timestamp()
                """).param("owner", owner).query(Long.class).single();
    }

    Generation insert(UUID id, UUID game, String key, String prompt, UUID baseVersion,
                      String gameSpecVersion, String runtimeVersion, Duration deadline) {
        return db.sql("""
                INSERT INTO generations(id, game_id, idempotency_key, prompt, base_version_id,
                                        game_spec_version, runtime_version, status, deadline_at)
                VALUES (:id, :game, :key, :prompt, :base, :schema, :runtime, 'QUEUED',
                        clock_timestamp() + :deadline * interval '1 second')
                RETURNING *, NULL::uuid AS result_version_id
                """).param("id", id).param("game", game).param("key", key).param("prompt", prompt)
                .param("base", baseVersion, Types.OTHER).param("schema", gameSpecVersion).param("runtime", runtimeVersion)
                .param("deadline", deadline.toSeconds()).query(GenerationQueries::generation).single();
    }

    void enqueue(UUID id, String payload) {
        db.sql("INSERT INTO generation_outbox(generation_id, payload) VALUES (:id, :payload)")
                .param("id", id).param("payload", payload).update();
    }
}
