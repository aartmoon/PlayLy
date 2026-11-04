package com.playly.core.generation;

import com.playly.core.generation.GenerationModels.Failure;
import com.playly.core.generation.GenerationModels.Target;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class GenerationResultRepository {
    private final JdbcClient db;

    GenerationResultRepository(JdbcClient db) {
        this.db = db;
    }

    Optional<Target> target(UUID generation, UUID eventId) {
        return db.sql("""
                SELECT game_id, game_spec_version, runtime_version, deadline_at, finished_at, deadline_at <= clock_timestamp() AS expired
                FROM generations WHERE id = :id
                    AND NOT EXISTS (SELECT 1 FROM generation_inbox WHERE event_id = :event)
                """).param("id", generation).param("event", eventId).query(Target.class).optional();
    }

    boolean lockGame(UUID game) {
        return db.sql("SELECT id FROM games WHERE id = :game FOR UPDATE").param("game", game)
                .query(UUID.class).optional().isPresent();
    }

    Optional<Target> lockJob(UUID id, UUID game) {
        return db.sql("""
                SELECT game_id, game_spec_version, runtime_version, deadline_at, finished_at, deadline_at <= clock_timestamp() AS expired
                FROM generations WHERE id = :id AND game_id = :game FOR UPDATE
                """).param("id", id).param("game", game).query(Target.class).optional();
    }

    boolean recordEvent(UUID event, UUID id) {
        return db.sql("""
                INSERT INTO generation_inbox(event_id, generation_id) VALUES (:event, :id)
                ON CONFLICT (event_id) DO NOTHING
                """).param("event", event).param("id", id).update() != 0;
    }

    void markRunning(UUID id) {
        db.sql("UPDATE generations SET status = 'RUNNING', updated_at = clock_timestamp() WHERE id = :id AND status = 'QUEUED'")
                .param("id", id).update();
    }

    void markFailed(UUID id, Failure failure) {
        db.sql("""
                UPDATE generations SET status = 'FAILED', error_code = :code, error_message = :message,
                    updated_at = clock_timestamp(), finished_at = clock_timestamp() WHERE id = :id
                """).param("id", id).param("code", failure.code()).param("message", failure.message()).update();
    }

    void saveVersion(UUID game, UUID id, String key, String manifest) {
        db.sql("""
                INSERT INTO game_versions(id, game_id, generation_id, number, manifest_key, manifest)
                SELECT :version, :game, :id, coalesce(max(number), 0) + 1, :key, CAST(:manifest AS jsonb)
                FROM game_versions WHERE game_id = :game
                """).param("version", UUID.randomUUID()).param("game", game).param("id", id)
                .param("key", key).param("manifest", manifest).update();
        db.sql("""
                UPDATE generations SET status = 'SUCCEEDED', updated_at = clock_timestamp(), finished_at = clock_timestamp()
                WHERE id = :id
                """).param("id", id).update();
    }
}
