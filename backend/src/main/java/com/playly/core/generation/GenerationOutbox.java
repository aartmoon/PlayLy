package com.playly.core.generation;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "playly.kafka.enabled", matchIfMissing = true)
class GenerationOutbox {
    private record Pending(UUID generationId, String payload, OffsetDateTime deadlineAt, OffsetDateTime finishedAt) {}

    private final JdbcClient db;
    private final TransactionTemplate transaction;
    private final KafkaTemplate<String, String> kafka;

    GenerationOutbox(JdbcClient db, TransactionTemplate transaction, KafkaTemplate<String, String> kafka) {
        this.db = db;
        this.transaction = transaction;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 500, scheduler = "outboxScheduler")
    void publish() {
        for (int i = 0; i < 20; i++) {
            if (!Boolean.TRUE.equals(transaction.execute(status -> publishOne()))) return;
        }
    }

    private boolean publishOne() {
        var pending = db.sql("""
                SELECT o.generation_id, o.payload, gen.deadline_at, gen.finished_at
                FROM generation_outbox o JOIN generations gen ON gen.id = o.generation_id
                WHERE o.available_at <= clock_timestamp()
                ORDER BY o.available_at, o.generation_id LIMIT 1 FOR UPDATE OF o SKIP LOCKED
                """).query(Pending.class).optional().orElse(null);
        if (pending == null) return false;

        UUID id = pending.generationId();
        if (pending.finishedAt() == null && Instant.now().isBefore(pending.deadlineAt().toInstant())) {
            try {
                kafka.send(GenerationKafka.REQUEST, id.toString(), pending.payload()).get(12, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Outbox publisher interrupted", error);
            } catch (ExecutionException | TimeoutException | org.springframework.kafka.KafkaException error) {
                LoggerFactory.getLogger(GenerationOutbox.class).warn("Retrying Kafka publication for generation {}", id, error);
                db.sql("UPDATE generation_outbox SET available_at = clock_timestamp() + interval '3 seconds' WHERE generation_id = :id")
                        .param("id", id).update();
                return false;
            }
        }
        db.sql("DELETE FROM generation_outbox WHERE generation_id = :id").param("id", id).update();
        return true;
    }
}
