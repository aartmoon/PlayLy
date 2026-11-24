package com.playly.core.storage;

import com.playly.core.storage.GameStorage.InvalidPackage;
import com.playly.core.storage.GameStorage.Manifest;
import com.playly.core.storage.GameStorage.StoredFile;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebPackageAdmissionTest {
    @Test
    void requiresSignatureBoundToTheActualPackage() throws Exception {
        var json = new JsonMapper();
        var storage = mock(GameStorage.class);
        when(storage.bucket()).thenReturn("games");
        String attempt = UUID.randomUUID().toString();
        String prefix = "generations/" + UUID.randomUUID() + "/" + attempt + "/";
        String key = "fixture-key-for-game-package-verification";
        var admission = new WebPackageAdmission(storage, json, key);
        var descriptor = file(storage, prefix, "public/game.json", "game.json", "application/json", """
                {"format":"phaser-web","entryPoint":"index.html","packageSchemaVersion":"2.0","runtimeVersion":"2.0.0",
                "phaserVersion":"3.90.0","gameKitVersion":"1.0.0","orientation":"any","viewport":{"width":800,"height":600}}
                """.getBytes(StandardCharsets.UTF_8));
        var html = file(storage, prefix, "public/index.html", "index.html", "text/html", new byte[]{1});
        var js = file(storage, prefix, "public/game.js", "game.js", "application/javascript", new byte[]{2});
        var source = file(storage, prefix, "private/source.json", "source.json", "application/json", new byte[]{3});
        String canonical = "[" + canonical(js) + "," + canonical(descriptor) + "," + canonical(html) + "]";
        var scores = Map.of("request", 1d, "moderation", 1d, "source_security", 1d, "source_requirements", 0.9d,
                "build", 1d, "browser_smoke", 1d, "final_requirements", 0.9d);
        var report = json.writeValueAsBytes(Map.of(
                "artifactSha256", sha(canonical.getBytes(StandardCharsets.UTF_8)), "sourceSha256", source.sha256(),
                "attemptId", attempt, "traceId", "12345678901234567890123456789012", "validatedAt", Instant.now().toString(),
                "policyVersion", "family-v1", "rubricVersion", "phaser-mvp-v1",
                "toolchainVersion", "phaser-3.90.0-vite-7.3.8", "checks", scores));
        var validation = file(storage, prefix, "private/validation.json", "validation.json", "application/json", report);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(report));
        var deadline = Instant.now().plusSeconds(60);
        var valid = new Manifest("2.0", "2.0.0", descriptor, List.of(html, js), source, validation, signature);
        assertDoesNotThrow(() -> admission.check(valid, prefix, List.of(descriptor, js, html), deadline));

        var wrongSignature = new Manifest("2.0", "2.0.0", descriptor, List.of(html, js), source, validation, "0".repeat(64));
        assertThrows(InvalidPackage.class, () -> admission.check(wrongSignature, prefix, List.of(descriptor, html, js), deadline));

        var modified = file(storage, prefix, "public/game.js", "game.js", "application/javascript", new byte[]{4});
        var wrongArtifact = new Manifest("2.0", "2.0.0", descriptor, List.of(html, modified), source, validation, signature);
        assertThrows(InvalidPackage.class, () -> admission.check(wrongArtifact, prefix, List.of(descriptor, modified, html), deadline));
    }

    private static StoredFile file(GameStorage storage, String prefix, String key, String path, String type, byte[] data)
            throws Exception {
        when(storage.open(prefix + key)).thenAnswer(invocation -> new ByteArrayInputStream(data));
        return new StoredFile(path, "games", prefix + key, type, (long) data.length, sha(data));
    }

    private static String canonical(StoredFile file) {
        return "{\"path\":\"" + file.path() + "\",\"sizeBytes\":" + file.sizeBytes()
                + ",\"sha256\":\"" + file.sha256() + "\"}";
    }

    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
