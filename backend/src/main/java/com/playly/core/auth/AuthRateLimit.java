package com.playly.core.auth;

import com.playly.core.web.ApiProblem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class AuthRateLimit {
    private final JdbcClient db;
    private final int requestsPerMinute;

    AuthRateLimit(JdbcClient db, @Value("${playly.auth.requests-per-minute}") int requestsPerMinute) {
        this.db = db;
        this.requestsPerMinute = requestsPerMinute;
    }

    void check(String client) {
        int requests = db.sql("""
                INSERT INTO auth_limits(client, window_start, requests) VALUES (:client, date_trunc('minute', now()), 1)
                ON CONFLICT (client) DO UPDATE SET window_start = excluded.window_start,
                    requests = CASE WHEN auth_limits.window_start < excluded.window_start THEN 1
                                    ELSE least(auth_limits.requests, :limit) + 1 END
                RETURNING requests
                """).param("client", client).param("limit", requestsPerMinute).query(Integer.class).single();
        if (requests > requestsPerMinute) {
            throw new ApiProblem(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMIT_EXCEEDED", "Too many auth requests", 60);
        }
    }

    @Scheduled(fixedDelay = 3_600_000)
    void purgeExpired() {
        db.sql("DELETE FROM auth_limits WHERE window_start < now() - interval '1 hour'").update();
    }
}
