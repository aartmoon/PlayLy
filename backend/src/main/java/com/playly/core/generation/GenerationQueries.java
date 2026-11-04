package com.playly.core.generation;

import com.playly.core.games.GameAccess;
import com.playly.core.generation.GenerationModels.Failure;
import com.playly.core.generation.GenerationModels.Generation;
import com.playly.core.web.ApiProblem;
import com.playly.core.web.Pages;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
class GenerationQueries {
    private static final String SELECT = """
            SELECT gen.*, v.id AS result_version_id FROM generations gen
            LEFT JOIN game_versions v ON v.generation_id = gen.id
            """;
    private final JdbcClient db;
    private final GameAccess games;

    GenerationQueries(JdbcClient db, GameAccess games) {
        this.db = db;
        this.games = games;
    }

    Generation get(UUID id, UUID owner) {
        return db.sql(SELECT + " JOIN games g ON g.id = gen.game_id WHERE gen.id = :id AND g.owner_id = :owner")
                .param("id", id).param("owner", owner).query(GenerationQueries::generation).optional()
                .orElseThrow(ApiProblem::missing);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Pages.Page<Generation> history(UUID game, UUID owner, String cursor, int limit) {
        games.requireOwner(game, owner, false);
        String scope = "generations:" + game;
        var position = Pages.position(cursor, scope);
        var statement = db.sql(SELECT + " WHERE gen.game_id = :game"
                + (position == null ? "" : " AND (gen.created_at, gen.id) < (:time, :id)")
                + " ORDER BY gen.created_at DESC, gen.id DESC LIMIT :limit")
                .param("game", game).param("limit", limit + 1);
        if (position != null) statement.param("time", position.time()).param("id", position.id());
        return Pages.page(statement.query(GenerationQueries::generation).list(), limit, scope,
                value -> value.createdAt() + "/" + value.id());
    }

    static Generation generation(ResultSet rs, int row) throws SQLException {
        String code = rs.getString("error_code");
        return new Generation(rs.getObject("id", UUID.class), rs.getObject("game_id", UUID.class), rs.getString("prompt"),
                rs.getObject("base_version_id", UUID.class), rs.getString("status"),
                rs.getObject("deadline_at", OffsetDateTime.class), rs.getObject("result_version_id", UUID.class),
                code == null ? null : new Failure(code, rs.getString("error_message")),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class));
    }

    Optional<Generation> byKey(UUID game, String key) {
        return db.sql(SELECT + " WHERE gen.game_id = :game AND gen.idempotency_key = :key")
                .param("game", game).param("key", key).query(GenerationQueries::generation).optional();
    }
}
