package com.playly.core.storage;

import com.playly.core.storage.GameStorage.StorageUnavailable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
class PackageCleanup {
    private final JdbcClient db;
    private final GameStorage storage;

    PackageCleanup(JdbcClient db, GameStorage storage) {
        this.db = db;
        this.storage = storage;
    }

    @Scheduled(initialDelay = 3_600_000, fixedDelay = 3_600_000)
    void run() {
        cleanAttempts();
        db.sql("DELETE FROM retired_packages WHERE retain_until <= clock_timestamp()").update();
    }

    private boolean retain(UUID generation, String manifestKey) {
        return db.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM generations WHERE id = :generation AND finished_at IS NULL
                    UNION ALL
                    SELECT 1 FROM game_versions WHERE generation_id = :generation AND manifest_key = :manifest
                    UNION ALL
                    SELECT 1 FROM retired_packages WHERE generation_id = :generation AND manifest_key = :manifest
                        AND retain_until > clock_timestamp()
                )
                """).param("generation", generation).param("manifest", manifestKey).query(Boolean.class).single();
    }

    private void cleanAttempts() {
        var cutoff = Instant.now().minusSeconds(storage.retentionSeconds());
        String previousPrefix = null;
        boolean protectedAttempt = true;
        for (var item : storage.generationObjects()) {
            try {
                if (!item.lastModified().isBefore(cutoff)) continue;
                String key = item.key();
                String[] parts = key.split("/", 4);
                if (parts.length != 4 || !PackageVerifier.ATTEMPT.matcher(parts[2]).matches()) continue;
                UUID id;
                try {
                    id = UUID.fromString(parts[1]);
                } catch (IllegalArgumentException error) {
                    continue;
                }
                String prefix = "generations/" + id + "/" + parts[2] + "/";
                if (!key.startsWith(prefix)) continue;
                if (!prefix.equals(previousPrefix)) {
                    protectedAttempt = retain(id, prefix + "manifest.json");
                    previousPrefix = prefix;
                }
                if (!protectedAttempt) storage.remove(key);
            } catch (Exception error) {
                throw new StorageUnavailable(error);
            }
        }
    }
}
