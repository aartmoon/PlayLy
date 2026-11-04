package com.playly.core.auth;

import com.playly.core.security.SecurityConfig;
import com.playly.core.web.ApiProblem;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshSessionsTest {
    private final JdbcClient db = mock(JdbcClient.class);
    private final JdbcClient.StatementSpec consume = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
    private final JdbcClient.StatementSpec insert = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
    private final JwtEncoder jwt = mock(JwtEncoder.class);
    private final RefreshSessions sessions = new RefreshSessions(db, jwt, Duration.ofMinutes(15), Duration.ofDays(30));

    @Test
    void rotatesConsumedTokenAndRejectsItsReplay() {
        UUID user = UUID.randomUUID();
        var token = "previous-refresh-token";
        @SuppressWarnings("unchecked")
        JdbcClient.MappedQuerySpec<UUID> query = mock(JdbcClient.MappedQuerySpec.class);
        when(db.sql(contains("DELETE FROM refresh_sessions"))).thenReturn(consume);
        when(consume.query(UUID.class)).thenReturn(query);
        when(query.optional()).thenReturn(Optional.of(user)).thenReturn(Optional.empty());
        when(db.sql(contains("INSERT INTO refresh_sessions"))).thenReturn(insert);
        when(jwt.encode(any())).thenAnswer(call -> {
            JwtEncoderParameters params = call.getArgument(0);
            assertEquals(user.toString(), params.getClaims().getSubject());
            assertEquals(SecurityConfig.ISSUER, params.getClaims().getClaimAsString("iss"));
            return org.springframework.security.oauth2.jwt.Jwt.withTokenValue("signed-access")
                    .header("alg", "HS256").claims(claims -> claims.putAll(params.getClaims().getClaims())).build();
        });

        var rotated = sessions.refresh(token);

        assertEquals("signed-access", rotated.accessToken());
        assertNotEquals(token, rotated.refreshToken());
        assertEquals(900, rotated.expiresIn());
        assertEquals(2_592_000, rotated.refreshExpiresIn());
        verify(consume).param(eq("hash"), argThat(value -> value instanceof String hash && hash.matches("[0-9a-f]{64}")));
        verify(insert).param(eq("hash"), argThat(value -> value instanceof String hash && hash.matches("[0-9a-f]{64}")));
        verify(insert).param("user", user);

        var replay = assertThrows(ApiProblem.class, () -> sessions.refresh(token));
        assertEquals("INVALID_REFRESH_TOKEN", replay.body().get("code"));
        verify(insert, times(1)).update();
        verify(jwt, times(1)).encode(any());
    }
}
