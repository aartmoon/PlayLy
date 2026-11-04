package com.playly.core.auth;

import com.playly.core.security.SecurityConfig;
import com.playly.core.web.ApiProblem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
class RefreshSessions {
    record Tokens(String accessToken, String refreshToken, String tokenType, long expiresIn, long refreshExpiresIn) {}

    private final JdbcClient db;
    private final JwtEncoder jwt;
    private final SecureRandom random = new SecureRandom();
    private final Duration accessTtl;
    private final Duration refreshTtl;

    RefreshSessions(JdbcClient db, JwtEncoder jwt,
                    @Value("${playly.auth.access-ttl}") Duration accessTtl,
                    @Value("${playly.auth.refresh-ttl}") Duration refreshTtl) {
        this.db = db;
        this.jwt = jwt;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    Tokens refresh(String token) {
        UUID user = db.sql("""
                DELETE FROM refresh_sessions WHERE token_hash = :hash AND expires_at > clock_timestamp()
                RETURNING user_id
                """).param("hash", hash(token)).query(UUID.class).optional().orElseThrow(() ->
                new ApiProblem(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Invalid refresh token"));
        return issue(user);
    }

    void logout(String token) {
        db.sql("DELETE FROM refresh_sessions WHERE token_hash = :hash").param("hash", hash(token)).update();
    }

    @Scheduled(fixedDelay = 3_600_000)
    void purgeExpired() {
        db.sql("DELETE FROM refresh_sessions WHERE expires_at <= now()").update();
    }

    Tokens issue(UUID user) {
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        db.sql("""
                INSERT INTO refresh_sessions(token_hash, user_id, expires_at)
                VALUES (:hash, :user, clock_timestamp() + :ttl * interval '1 second')
                """).param("hash", hash(refresh)).param("user", user).param("ttl", refreshTtl.toSeconds()).update();

        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(SecurityConfig.ISSUER).subject(user.toString())
                .issuedAt(now).expiresAt(now.plus(accessTtl)).id(UUID.randomUUID().toString()).build();
        var access = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return new Tokens(access.getTokenValue(), refresh, "Bearer", accessTtl.toSeconds(), refreshTtl.toSeconds());
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
