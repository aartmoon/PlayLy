package com.playly.core.auth;

import com.playly.core.web.ApiProblem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
class UserRepository {
    record Profile(UUID id, String email, String displayName, OffsetDateTime createdAt) {}
    record Account(UUID id, String hash) {}

    private final JdbcClient db;

    UserRepository(JdbcClient db) {
        this.db = db;
    }

    boolean insert(UUID id, String email, String hash, String displayName) {
        return db.sql("""
                INSERT INTO users(id, email, password_hash, display_name)
                VALUES (:id, :email, :hash, :name) ON CONFLICT (email) DO NOTHING
                """).param("id", id).param("email", email).param("hash", hash)
                .param("name", displayName).update() != 0;
    }

    Optional<Account> account(String email) {
        return db.sql("SELECT id, password_hash FROM users WHERE email = :email")
                .param("email", email).query((rs, row) ->
                        new Account(rs.getObject("id", UUID.class), rs.getString("password_hash"))).optional();
    }

    Profile profile(UUID id) {
        return db.sql("SELECT id, email, display_name, created_at FROM users WHERE id = :id")
                .param("id", id).query(Profile.class).optional().orElseThrow(ApiProblem::unauthorized);
    }

    @Transactional
    Profile updateProfile(UUID id, String name) {
        db.sql("UPDATE users SET display_name = :name WHERE id = :id").param("id", id).param("name", name).update();
        return profile(id);
    }
}
