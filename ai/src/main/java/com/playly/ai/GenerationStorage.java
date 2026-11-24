package com.playly.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.playly.ai.plan.GenerationPipeline;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class GenerationStorage implements AutoCloseable {
    static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_ARCHIVE = 2 * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html"), Map.entry("js", "application/javascript"), Map.entry("css", "text/css"),
            Map.entry("json", "application/json"), Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"), Map.entry("webp", "image/webp"), Map.entry("ogg", "audio/ogg"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"), Map.entry("woff2", "font/woff2"));
    record Reference(String bucket, String key) {}
    record StoredFile(String path, String bucket, String key, String mediaType, long sizeBytes, String sha256) {}
    record Manifest(String gameSpecVersion, String runtimeVersion, StoredFile gameSpec, List<StoredFile> assets,
                    StoredFile sourceArchive, StoredFile validationReport, String reportSignature) {}
    record Report(String artifactSha256, String sourceSha256, String attemptId, String traceId, String validatedAt,
                  String policyVersion, String rubricVersion, String toolchainVersion, Map<String, Double> checks) {}

    private final S3Client s3;
    private final String bucket = env("S3_GAME_BUCKET", "playly-games");
    private final byte[] validationKey;

    GenerationStorage() {
        validationKey = env("PLAYLY_VALIDATION_KEY", "").getBytes(StandardCharsets.UTF_8);
        if (validationKey.length < 32) throw new IllegalArgumentException("Configure PLAYLY_VALIDATION_KEY (at least 32 bytes).");
        s3 = S3Client.builder().endpointOverride(URI.create(env("S3_ENDPOINT", "http://localhost:9000")))
                .region(Region.US_EAST_1).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true)
                        .checksumValidationEnabled(false).build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        env("PLAYLY_S3_ACCESS_KEY", "playly"), env("PLAYLY_S3_SECRET_KEY", "playly-local"))))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3))
                        .socketTimeout(Duration.ofSeconds(8)))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(15))).build();
    }

    byte[] readBase(Reference base) throws IOException {
        if (!bucket.equals(base.bucket()) || base.key() == null
                || !base.key().matches("generations/[0-9a-f-]{36}/[A-Za-z0-9_-]{1,128}/manifest\\.json")) {
            throw new IOException("Invalid base manifest");
        }
        var data = read(base.key(), 1024 * 1024);
        if (data == null) throw new IOException("Base manifest is missing");
        var manifest = JSON.fromJson(new String(data, StandardCharsets.UTF_8), Manifest.class);
        if (manifest == null || !"2.0".equals(manifest.gameSpecVersion()) || manifest.sourceArchive() == null) {
            throw new IOException("Base version does not contain editable sources");
        }
        var source = manifest.sourceArchive();
        String expected = base.key().replaceFirst("manifest\\.json$", "private/source.json");
        if (!bucket.equals(source.bucket()) || !expected.equals(source.key())
                || source.sizeBytes() < 1 || source.sizeBytes() > MAX_ARCHIVE) throw new IOException("Invalid base source");
        var archive = read(source.key(), MAX_ARCHIVE);
        if (archive == null || source.sizeBytes() != archive.length
                || !GenerationPipeline.sha256(archive).equals(source.sha256())) throw new IOException("Invalid base source");
        return archive;
    }

    Reference store(UUID id, GenerationPipeline.Build build, String traceId, Instant deadline) throws IOException {
        String attempt = build.attemptId().toString();
        String prefix = "generations/" + id + "/" + attempt + "/";
        var assets = new ArrayList<StoredFile>();
        var spec = file(prefix, "game.json", "public/game.json", build.files().get("game.json"), deadline);
        for (var entry : new java.util.TreeMap<>(build.files()).entrySet()) {
            if (!entry.getKey().equals("game.json")) {
                assets.add(file(prefix, entry.getKey(), "public/" + entry.getKey(), entry.getValue(), deadline));
            }
        }
        var archive = file(prefix, "source.json", "private/source.json", build.sources(), deadline);
        var report = new Report(build.artifactSha256(), build.sourceSha256(), attempt, traceId,
                Instant.now().toString(), GenerationPipeline.POLICY_VERSION, GenerationPipeline.RUBRIC_VERSION,
                GenerationPipeline.TOOLCHAIN_VERSION, build.checks());
        byte[] reportBytes = jsonBytes(report);
        var validation = file(prefix, "validation.json", "private/validation.json", reportBytes, deadline);
        var manifest = new Manifest("2.0", "2.0.0", spec, List.copyOf(assets), archive, validation, sign(reportBytes));
        checkDeadline(deadline);
        return put(prefix + "manifest.json", "application/json", jsonBytes(manifest), false);
    }

    String outcome(UUID id) throws IOException {
        byte[] bytes = read(outcomeKey(id), 8192);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    String remember(UUID id, String result) throws IOException {
        try {
            put(outcomeKey(id), "application/json", result.getBytes(StandardCharsets.UTF_8), true);
            return result;
        } catch (S3Exception error) {
            if (error.statusCode() != 412) throw error;
            String previous = outcome(id);
            if (previous == null) throw new IOException("Stored result is unavailable");
            return previous;
        }
    }

    private static String outcomeKey(UUID id) { return "generations/" + id + "/outcome/result.json"; }

    private StoredFile file(String prefix, String path, String key, byte[] bytes, Instant deadline) throws IOException {
        checkDeadline(deadline);
        var type = TYPES.get(path.substring(path.lastIndexOf('.') + 1));
        var reference = put(prefix + key, type, bytes, false);
        return new StoredFile(path, bucket, reference.key(), type, bytes.length, GenerationPipeline.sha256(bytes));
    }

    private Reference put(String key, String type, byte[] bytes, boolean once) {
        s3.putObject(request -> {
            request.bucket(bucket).key(key).contentType(type);
            if (once) request.ifNoneMatch("*");
        }, RequestBody.fromBytes(bytes));
        return new Reference(bucket, key);
    }

    private byte[] read(String key, int max) throws IOException {
        try (var input = s3.getObject(request -> request.bucket(bucket).key(key))) {
            byte[] data = input.readNBytes(max + 1);
            if (data.length > max) throw new IOException("Stored object exceeds its limit");
            return data;
        } catch (S3Exception error) {
            if (error.statusCode() == 404) return null;
            throw error;
        }
    }

    private String sign(byte[] data) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(validationKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    private static byte[] jsonBytes(Object value) {
        return JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    static String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }

    static void checkDeadline(Instant deadline) throws java.net.http.HttpTimeoutException {
        if (!Instant.now().isBefore(deadline)) throw new java.net.http.HttpTimeoutException("Generation deadline expired");
    }

    @Override
    public void close() { s3.close(); }
}
