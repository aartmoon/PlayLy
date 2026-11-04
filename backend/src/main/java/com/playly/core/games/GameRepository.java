package com.playly.core.games;

import com.playly.core.games.GameModels.Access;
import com.playly.core.games.GameModels.Author;
import com.playly.core.games.GameModels.Game;
import com.playly.core.games.GameModels.Publication;
import com.playly.core.games.GameModels.RatingSummary;
import com.playly.core.web.ApiProblem;
import com.playly.core.web.Pages;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
class GameRepository {
    private static final String GAME_SELECT = """
            SELECT g.*, u.display_name,
                (SELECT avg(r.score)::double precision FROM ratings r WHERE r.game_id = g.id) AS rating_average,
                (SELECT count(*) FROM ratings r WHERE r.game_id = g.id) AS rating_count
            FROM games g JOIN users u ON u.id = g.owner_id
            """;
    private final JdbcClient db;

    GameRepository(JdbcClient db) {
        this.db = db;
    }

    Game get(UUID id, UUID viewer) {
        return db.sql(GAME_SELECT + " WHERE g.id = :id AND (g.owner_id = :viewer OR g.published_version_id IS NOT NULL)")
                .param("id", id).param("viewer", viewer, java.sql.Types.OTHER)
                .query(GameRepository::game).optional().orElseThrow(ApiProblem::missing);
    }

    Pages.Page<Game> ownGames(UUID owner, String cursor, int limit) {
        String scope = "own:" + owner;
        var position = Pages.position(cursor, scope);
        var statement = db.sql(GAME_SELECT + " WHERE g.owner_id = :owner"
                + (position == null ? "" : " AND (g.updated_at, g.id) < (:time, :id)")
                + " ORDER BY g.updated_at DESC, g.id DESC LIMIT :limit")
                .param("owner", owner).param("limit", limit + 1);
        if (position != null) statement.param("time", position.time()).param("id", position.id());
        return Pages.page(statement.query(GameRepository::game).list(), limit, scope,
                game -> game.updatedAt() + "/" + game.id());
    }

    Pages.Page<Game> catalog(String search, String cursor, int limit) {
        String scope = "catalog:" + (search == null ? "" : search);
        var position = Pages.position(cursor, scope);
        var statement = db.sql(GAME_SELECT + " WHERE g.published_version_id IS NOT NULL"
                + (search == null ? "" : " AND (strpos(lower(g.title), lower(:q)) > 0 OR strpos(lower(g.description), lower(:q)) > 0)")
                + (position == null ? "" : " AND (g.published_at, g.id) < (:time, :id)")
                + " ORDER BY g.published_at DESC, g.id DESC LIMIT :limit").param("limit", limit + 1);
        if (search != null) statement.param("q", search);
        if (position != null) statement.param("time", position.time()).param("id", position.id());
        return Pages.page(statement.query(GameRepository::game).list(), limit, scope,
                game -> game.publication().publishedAt() + "/" + game.id());
    }

    Access access(UUID game, boolean lock) {
        return db.sql("SELECT owner_id, published_version_id FROM games WHERE id = :id" + (lock ? " FOR UPDATE" : ""))
                .param("id", game).query(Access.class).optional().orElseThrow(ApiProblem::missing);
    }

    private static Game game(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID owner = rs.getObject("owner_id", UUID.class);
        UUID published = rs.getObject("published_version_id", UUID.class);
        var publication = published == null ? null : new Publication(id, published, rs.getObject("published_at", OffsetDateTime.class));
        return new Game(id, owner, new Author(owner, rs.getString("display_name")),
                rs.getString("title"), rs.getString("description"), published == null ? "DRAFT" : "PUBLISHED",
                publication, new RatingSummary(rs.getObject("rating_average", Double.class), rs.getLong("rating_count")),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    void insert(UUID id, UUID owner, String title, String description) {
        db.sql("INSERT INTO games(id, owner_id, title, description) VALUES (:id, :owner, :title, :description)")
                .param("id", id).param("owner", owner).param("title", title).param("description", description).update();
    }

    void update(UUID id, String title, String description) {
        db.sql("UPDATE games SET title = :title, description = :description, updated_at = clock_timestamp() WHERE id = :id")
                .param("title", title).param("description", description).param("id", id).update();
    }

    boolean hasActiveGeneration(UUID id) {
        return db.sql("SELECT EXISTS (SELECT 1 FROM generations WHERE game_id = :id AND finished_at IS NULL)")
                .param("id", id).query(Boolean.class).single();
    }

    void delete(UUID id) {
        db.sql("DELETE FROM games WHERE id = :id").param("id", id).update();
    }
}
