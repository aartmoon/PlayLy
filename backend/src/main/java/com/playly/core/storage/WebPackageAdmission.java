package com.playly.core.storage;

import com.playly.core.storage.GameStorage.InvalidPackage;
import com.playly.core.storage.GameStorage.Manifest;
import com.playly.core.storage.GameStorage.StorageUnavailable;
import com.playly.core.storage.GameStorage.StoredFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
final class WebPackageAdmission {
    private static final Pattern PATH = Pattern.compile("(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern TRACE = Pattern.compile("[0-9a-f]{32}");
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html"), Map.entry("js", "application/javascript"), Map.entry("css", "text/css"),
            Map.entry("json", "application/json"), Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"), Map.entry("webp", "image/webp"), Map.entry("ogg", "audio/ogg"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"), Map.entry("woff2", "font/woff2"));
    private static final Map<String, Double> REQUIRED = Map.of(
            "request", 1d, "moderation", 1d, "source_security", 1d, "source_requirements", 0.8d,
            "build", 1d, "browser_smoke", 1d, "final_requirements", 0.8d);

    private final GameStorage storage;
    private final ObjectMapper json;
    private final byte[] signingKey;

    WebPackageAdmission(GameStorage storage, ObjectMapper json,
                        @Value("${playly.generation.validation-key}") String signingKey) {
        this.storage = storage;
        this.json = json;
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
    }

    void check(Manifest manifest, String prefix, List<StoredFile> publicFiles, Instant deadline) {
        if (signingKey.length < 32 || publicFiles.size() > 513 || manifest.gameSpec().sizeBytes() > 16384
                || manifest.assets().stream().mapToLong(StoredFile::sizeBytes).sum() > 16 * 1024 * 1024) throw new InvalidPackage();
        var paths = publicFiles.stream().map(StoredFile::path).collect(java.util.stream.Collectors.toSet());
        if (!paths.contains("index.html") || paths.stream().noneMatch(path -> path.endsWith(".js"))) throw new InvalidPackage();
        for (var file : publicFiles) {
            String path = file.path();
            int dot = path.lastIndexOf('.');
            if (!PATH.matcher(path).matches() || dot < 0
                    || !Objects.equals(TYPES.get(path.substring(dot + 1)), file.mediaType())
                    || !file.key().equals(prefix + "public/" + path) || file.sizeBytes() == 0) throw new InvalidPackage();
            for (int i = path.indexOf('/'); i >= 0; i = path.indexOf('/', i + 1)) {
                if (paths.contains(path.substring(0, i))) throw new InvalidPackage();
            }
        }
        privateFile(manifest.validationReport(), "validation.json", prefix, 32768);
        privateFile(manifest.sourceArchive(), "source.json", prefix, 2 * 1024 * 1024);
        String signature = manifest.reportSignature();
        if (signature == null || !HASH.matcher(signature).matches()) throw new InvalidPackage();
        byte[] reportBytes = read(manifest.validationReport());
        if (!MessageDigest.isEqual(sign(reportBytes), HexFormat.of().parseHex(signature))) throw new InvalidPackage();

        Report report;
        try {
            report = json.readValue(reportBytes, Report.class);
        } catch (JacksonException invalid) {
            throw new InvalidPackage();
        }
        if (report == null) throw new InvalidPackage();
        String expectedAttempt = prefix.split("/")[2];
        String canonical = canonical(publicFiles);
        Instant now = Instant.now();
        if (!sha256(canonical.getBytes(StandardCharsets.UTF_8)).equals(report.artifactSha256())
                || !manifest.sourceArchive().sha256().equals(report.sourceSha256()) || !expectedAttempt.equals(report.attemptId())
                || report.traceId() == null || !TRACE.matcher(report.traceId()).matches()
                || report.validatedAt() == null || report.validatedAt().isAfter(now) || !report.validatedAt().isBefore(deadline)
                || !"family-v1".equals(report.policyVersion()) || !"phaser-mvp-v1".equals(report.rubricVersion())
                || !"phaser-3.90.0-vite-7.3.8".equals(report.toolchainVersion())
                || report.checks() == null || !report.checks().keySet().equals(REQUIRED.keySet())) throw new InvalidPackage();
        for (var required : REQUIRED.entrySet()) {
            var score = report.checks().get(required.getKey());
            if (score == null || !Double.isFinite(score) || score < required.getValue() || score > 1) throw new InvalidPackage();
        }
        read(manifest.sourceArchive());
        try {
            var spec = json.readTree(read(manifest.gameSpec()));
            if (spec == null || !spec.isObject() || !"phaser-web".equals(spec.path("format").asString())
                    || !"index.html".equals(spec.path("entryPoint").asString())
                    || !"2.0".equals(spec.path("packageSchemaVersion").asString())
                    || !manifest.runtimeVersion().equals(spec.path("runtimeVersion").asString())
                    || !"3.90.0".equals(spec.path("phaserVersion").asString())
                    || !"1.0.0".equals(spec.path("gameKitVersion").asString())
                    || !spec.path("viewport").isObject() || !spec.path("orientation").isString()) throw new InvalidPackage();
        } catch (JacksonException invalid) {
            throw new InvalidPackage();
        }
    }

    private void privateFile(StoredFile file, String name, String prefix, int limit) {
        if (file == null || !name.equals(file.path()) || !storage.bucket().equals(file.bucket())
                || !(prefix + "private/" + name).equals(file.key()) || !"application/json".equals(file.mediaType())
                || file.sizeBytes() == null || file.sizeBytes() < 1 || file.sizeBytes() > limit
                || file.sha256() == null || !HASH.matcher(file.sha256()).matches()) throw new InvalidPackage();
    }

    private byte[] read(StoredFile file) {
        try (var input = storage.open(file.key())) {
            byte[] data = input.readNBytes(Math.toIntExact(file.sizeBytes()) + 1);
            if (data.length != file.sizeBytes() || !sha256(data).equals(file.sha256())) throw new InvalidPackage();
            return data;
        } catch (IOException error) {
            throw new StorageUnavailable(error);
        }
    }

    private static String canonical(List<StoredFile> files) {
        return files.stream().sorted(Comparator.comparing(StoredFile::path))
                .map(file -> "{\"path\":\"" + file.path() + "\",\"sizeBytes\":" + file.sizeBytes()
                        + ",\"sha256\":\"" + file.sha256() + "\"}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private byte[] sign(byte[] data) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    private record Report(String artifactSha256, String sourceSha256, String attemptId, String traceId, Instant validatedAt,
                          String policyVersion, String rubricVersion, String toolchainVersion, Map<String, Double> checks) {}
}
