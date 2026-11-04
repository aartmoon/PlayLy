package com.playly.core.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthApi {
    record Registration(@NotBlank @Email @Size(max = 254) String email,
                        @NotNull @Size(min = 8, max = 128) String password,
                        @NotBlank @Size(max = 100) String displayName) {
        Registration {
            if (email != null) email = email.strip();
        }
    }

    record Login(@NotBlank @Email @Size(max = 254) String email,
                 @NotNull @Size(min = 1, max = 128) String password) {
        Login {
            if (email != null) email = email.strip();
        }
    }

    record Refresh(@NotNull @Size(min = 1, max = 2048) String refreshToken) {}
    private final AuthService auth;
    private final RefreshSessions sessions;
    private final AuthRateLimit rateLimit;

    AuthApi(AuthService auth, RefreshSessions sessions, AuthRateLimit rateLimit) {
        this.auth = auth;
        this.sessions = sessions;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/register")
    ResponseEntity<RefreshSessions.Tokens> register(@Valid @RequestBody Registration body, HttpServletRequest request) {
        rateLimit.check(request.getRemoteAddr());
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(auth.register(body.email(), body.password(), body.displayName()));
    }

    @PostMapping("/login")
    ResponseEntity<RefreshSessions.Tokens> login(@Valid @RequestBody Login body, HttpServletRequest request) {
        rateLimit.check(request.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(body.email(), body.password()));
    }

    @PostMapping("/refresh")
    ResponseEntity<RefreshSessions.Tokens> refresh(@Valid @RequestBody Refresh body, HttpServletRequest request) {
        rateLimit.check(request.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.refresh(body.refreshToken()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody Refresh body, HttpServletRequest request) {
        rateLimit.check(request.getRemoteAddr());
        sessions.logout(body.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
