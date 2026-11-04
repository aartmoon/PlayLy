package com.playly.core.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.playly.core.security.CurrentUser.userId;

@RestController
@RequestMapping("/api/v1/me")
class ProfileApi {
    record ProfileUpdate(@NotBlank @Size(max = 100) String displayName) {}

    private final UserRepository users;

    ProfileApi(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    UserRepository.Profile profile(@AuthenticationPrincipal Jwt jwt) {
        return users.profile(userId(jwt));
    }

    @PatchMapping
    UserRepository.Profile updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdate body) {
        return users.updateProfile(userId(jwt), body.displayName());
    }
}
