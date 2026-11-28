package com.playly.core.storage;

import com.playly.core.web.ApiProblem;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class GameStorage {
    public record Reference(String bucket, String key) {}
    public record StoredFile(String path, String bucket, String key, String mediaType, Long sizeBytes, String sha256) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Manifest(String gameSpecVersion, String runtimeVersion, StoredFile gameSpec, List<StoredFile> assets,
                           StoredFile sourceArchive, StoredFile validationReport, String reportSignature) {
        public Manifest(String gameSpecVersion, String runtimeVersion, StoredFile gameSpec, List<StoredFile> assets) {
            this(gameSpecVersion, runtimeVersion, gameSpec, assets, null, null, null);
        }
    }
    public record PackageFile(String path, String url, String mediaType, long sizeBytes, String sha256) {}
    public record GamePackage(UUID gameId, UUID versionId, String gameSpecVersion, String runtimeVersion,
                       Instant expiresAt, PackageFile gameSpec, List<PackageFile> assets) {}

    public static final class InvalidPackage extends RuntimeException {
        InvalidPackage() {
            super("Invalid generated game package");
        }
    }

    public static final class StorageUnavailable extends RuntimeException {
        StorageUnavailable(Exception cause) {
            super("Game storage is unavailable", cause);
        }
    }

    private final S3Client internal;
    private final GameFileLinks links;
    private final String bucket;
    private final int urlTtl;
    private final long retentionSeconds;

    GameStorage(@Value("${playly.storage.endpoint}") String endpoint,
                @Value("${playly.public-api-url}") String publicApiUrl,
                @Value("${playly.auth.jwt-secret}") String signingSecret,
                @Value("${playly.storage.access-key}") String accessKey,
                @Value("${playly.storage.secret-key}") String secretKey,
                @Value("${playly.storage.bucket}") String bucket,
                @Value("${playly.storage.url-ttl}") Duration urlTtl,
                @Value("${playly.storage.cleanup-grace}") Duration cleanupGrace,
                @Value("${playly.generation.deadline}") Duration generationDeadline) {
        this.bucket = bucket;
        this.links = new GameFileLinks(publicApiUrl, signingSecret);
        this.urlTtl = Math.toIntExact(urlTtl.toSeconds());
        this.retentionSeconds = cleanupGrace.plus(urlTtl).plus(generationDeadline).toSeconds();
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var s3 = S3Configuration.builder().pathStyleAccessEnabled(true)
                .checksumValidationEnabled(false).build();
        this.internal = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .credentialsProvider(credentials).serviceConfiguration(s3)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(2)).socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(15)))
                .build();
    }

    public long retentionSeconds() {
        return retentionSeconds;
    }

    public Reference reference(String key) {
        return new Reference(bucket, key);
    }

    public GamePackage sign(UUID game, UUID version, Manifest manifest) {
        Instant expires = Instant.now().plusSeconds(urlTtl).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return new GamePackage(game, version, manifest.gameSpecVersion(), manifest.runtimeVersion(), expires,
                sign(manifest.gameSpec(), expires), manifest.assets().stream().map(file -> sign(file, expires)).toList());
    }

    ResponseInputStream<GetObjectResponse> download(String token) {
        String key = links.objectKey(token, Instant.now());
        try {
            return openObject(key);
        } catch (InvalidPackage error) {
            throw ApiProblem.missing();
        }
    }

    InputStream open(String key) {
        return openObject(key);
    }

    private ResponseInputStream<GetObjectResponse> openObject(String key) {
        try {
            return internal.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception error) {
            if (error.awsErrorDetails() != null && "NoSuchKey".equals(error.awsErrorDetails().errorCode())) throw new InvalidPackage();
            throw new StorageUnavailable(error);
        } catch (Exception error) {
            throw new StorageUnavailable(error);
        }
    }

    private PackageFile sign(StoredFile file, Instant expires) {
        String url = links.url(file.key(), expires);
        return new PackageFile(file.path(), url, file.mediaType(), file.sizeBytes(), file.sha256());
    }

    String bucket() {
        return bucket;
    }

    Iterable<S3Object> generationObjects() {
        return internal.listObjectsV2Paginator(request -> request.bucket(bucket).prefix("generations/")).contents();
    }

    void remove(String key) {
        internal.deleteObject(request -> request.bucket(bucket).key(key));
    }

    @PreDestroy
    void close() {
        internal.close();
    }
}
