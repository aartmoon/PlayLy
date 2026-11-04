package com.playly.core.games;

import com.playly.core.games.GameModels.Publication;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class PublicationService {
    private final JdbcClient db;
    private final GameAccess access;
    private final VersionRepository versions;

    PublicationService(JdbcClient db, GameAccess access, VersionRepository versions) {
        this.db = db;
        this.access = access;
        this.versions = versions;
    }

    @Transactional
    Publication publish(UUID game, UUID version, UUID owner) {
        access.requireOwner(game, owner, true);
        versions.storedVersion(game, version);
        return db.sql("""
                UPDATE games SET published_version_id = :version,
                    published_at = CASE WHEN published_version_id = :version THEN published_at ELSE clock_timestamp() END,
                    updated_at = CASE WHEN published_version_id = :version THEN updated_at ELSE clock_timestamp() END
                WHERE id = :id RETURNING id AS game_id, published_version_id AS version_id, published_at
                """).param("id", game).param("version", version).query(Publication.class).single();
    }

    @Transactional
    void unpublish(UUID game, UUID owner) {
        access.requireOwner(game, owner, true);
        db.sql("""
                UPDATE games SET published_version_id = NULL, published_at = NULL, updated_at = clock_timestamp()
                WHERE id = :id AND published_version_id IS NOT NULL
                """).param("id", game).update();
    }
}
