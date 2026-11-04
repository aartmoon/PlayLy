package com.playly.core.generation;

import com.playly.core.generation.GenerationModels.Generation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class GenerationDeadlines {
    private static final String TIMEOUT = """
            UPDATE generations SET status = 'FAILED', error_code = 'GENERATION_TIMEOUT',
                error_message = 'Generation deadline exceeded', updated_at = clock_timestamp(), finished_at = clock_timestamp()
            WHERE finished_at IS NULL AND deadline_at <= clock_timestamp()
            """;

    private final JdbcClient db;

    GenerationDeadlines(JdbcClient db) {
        this.db = db;
    }

    @Scheduled(fixedDelay = 1000)
    void expire() {
        db.sql(TIMEOUT).update();
    }

    void expireGame(UUID game) {
        db.sql(TIMEOUT + " AND game_id = :game").param("game", game).update();
    }

    boolean expireJob(UUID id) {
        return db.sql(TIMEOUT + " AND id = :id").param("id", id).update() != 0;
    }
}
