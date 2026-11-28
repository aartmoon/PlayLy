package com.playly.core.storage;

import com.playly.core.web.ApiProblem;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

final class GameFileLinks {
    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] PURPOSE = "playly-game-file-v1:".getBytes(StandardCharsets.UTF_8);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final String apiUrl;
    private final SecretKeySpec key;

    GameFileLinks(String publicApiUrl, String signingSecret) {
        var uri = URI.create(publicApiUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("PLAYLY_PUBLIC_API_URL must be an absolute HTTP or HTTPS API URL");
        }
        apiUrl = uri.toASCIIString().replaceFirst("/+$", "");
        key = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    String url(String objectKey, Instant expires) {
        String payload = expires.getEpochSecond() + "." + ENCODER.encodeToString(objectKey.getBytes(StandardCharsets.UTF_8));
        return apiUrl + "/game-files?token=" + payload + "." + ENCODER.encodeToString(signature(payload));
    }

    String objectKey(String token, Instant now) {
        if (token == null || token.length() > 2048) throw ApiProblem.missing();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[1].isEmpty()) throw ApiProblem.missing();
        try {
            byte[] actual = DECODER.decode(parts[2]);
            byte[] expected = signature(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, actual) || Long.parseLong(parts[0]) <= now.getEpochSecond()) {
                throw ApiProblem.missing();
            }
            return new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw ApiProblem.missing();
        }
    }

    private byte[] signature(String payload) {
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            mac.update(PURPOSE);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Cannot sign game file links", error);
        }
    }
}
