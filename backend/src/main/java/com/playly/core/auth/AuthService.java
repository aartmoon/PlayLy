package com.playly.core.auth;

import com.playly.core.web.ApiProblem;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
class AuthService {
    private final UserRepository users;
    private final RefreshSessions sessions;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    AuthService(UserRepository users, RefreshSessions sessions, PasswordEncoder passwords) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    RefreshSessions.Tokens register(String email, String password, String displayName) {
        UUID id = UUID.randomUUID();
        if (!users.insert(id, normalize(email), passwords.encode(password), displayName)) {
            throw ApiProblem.conflict("EMAIL_ALREADY_REGISTERED", "Email already registered");
        }
        return sessions.issue(id);
    }

    @Transactional
    RefreshSessions.Tokens login(String email, String password) {
        var account = users.account(normalize(email));
        boolean matches = passwords.matches(password, account.map(UserRepository.Account::hash).orElse(dummyHash));
        if (!matches || account.isEmpty()) {
            throw new ApiProblem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        return sessions.issue(account.get().id());
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
