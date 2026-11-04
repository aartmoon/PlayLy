package com.playly.core.games;

import com.playly.core.games.GameModels.StoredVersion;
import com.playly.core.games.GameModels.Version;
import com.playly.core.web.ApiProblem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
class VersionRepository {
    private static final String VERSION_SELECT = """
            SELECT v.*, gen.game_spec_version, gen.runtime_version FROM game_versions v
            JOIN generations gen ON gen.id = v.generation_id
            """;
    private final JdbcClient db;

    VersionRepository(JdbcClient db) {
        this.db = db;
    }

    List<Version> list(UUID game, int number, int limit) {
        return db.sql(VERSION_SELECT + " WHERE v.game_id = :game AND v.number < :number ORDER BY v.number DESC LIMIT :limit")
                .param("game", game).param("number", number).param("limit", limit)
                .query((rs, row) -> version(rs)).list();
    }

    StoredVersion storedVersion(UUID game, UUID version) {
        return db.sql(VERSION_SELECT + " WHERE v.game_id = :game AND v.id = :version")
                .param("game", game).param("version", version)
                .query((rs, row) -> new StoredVersion(version(rs), rs.getString("manifest_key"), rs.getString("manifest")))
                .optional().orElseThrow(ApiProblem::missing);
    }

    private static Version version(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Version(rs.getObject("id", UUID.class), rs.getObject("game_id", UUID.class),
                rs.getInt("number"), rs.getObject("generation_id", UUID.class), rs.getString("game_spec_version"),
                rs.getString("runtime_version"), rs.getObject("created_at", OffsetDateTime.class));
    }

    void retirePackages(UUID game, long retentionSeconds) {
        db.sql("""
                INSERT INTO retired_packages(generation_id, manifest_key, retain_until)
                SELECT generation_id, manifest_key, clock_timestamp() + :seconds * interval '1 second'
                FROM game_versions WHERE game_id = :id
                """).param("id", game).param("seconds", retentionSeconds).update();
    }
}
