package com.playly.core.games;

import com.playly.core.games.GameModels.Rating;
import com.playly.core.web.ApiProblem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
class RatingService {
    private final JdbcClient db;
    private final GameAccess access;

    RatingService(JdbcClient db, GameAccess access) {
        this.db = db;
        this.access = access;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Optional<Rating> rating(UUID game, UUID user) {
        access.requirePublished(game, false);
        return db.sql("SELECT * FROM ratings WHERE game_id = :game AND user_id = :user")
                .param("game", game).param("user", user).query(Rating.class).optional();
    }

    @Transactional
    Rating rate(UUID game, UUID user, int score) {
        if (access.requirePublished(game, true).ownerId().equals(user)) {
            throw ApiProblem.conflict("SELF_RATING_FORBIDDEN", "Authors cannot rate their own game");
        }
        return db.sql("""
                INSERT INTO ratings(game_id, user_id, score) VALUES (:game, :user, :score)
                ON CONFLICT (game_id, user_id) DO UPDATE SET score = excluded.score, updated_at = clock_timestamp()
                RETURNING *
                """).param("game", game).param("user", user).param("score", score).query(Rating.class).single();
    }

    @Transactional
    void deleteRating(UUID game, UUID user) {
        access.requirePublished(game, true);
        db.sql("DELETE FROM ratings WHERE game_id = :game AND user_id = :user")
                .param("game", game).param("user", user).update();
    }
}
