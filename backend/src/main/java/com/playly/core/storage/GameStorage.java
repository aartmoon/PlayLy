package com.playly.core.storage;

import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
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
    public record Manifest(String gameSpecVersion, String runtimeVersion, StoredFile gameSpec, List<StoredFile> assets) {}
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
    private final S3Presigner publicSigner;
    private final String bucket;
    private final int urlTtl;
    private final long retentionSeconds;

    GameStorage(@Value("${playly.storage.endpoint}") String endpoint,
                @Value("${playly.storage.public-endpoint}") String publicEndpoint,
                @Value("${playly.storage.access-key}") String accessKey,
                @Value("${playly.storage.secret-key}") String secretKey,
                @Value("${playly.storage.bucket}") String bucket,
                @Value("${playly.storage.url-ttl}") Duration urlTtl,
                @Value("${playly.storage.cleanup-grace}") Duration cleanupGrace,
                @Value("${playly.generation.deadline}") Duration generationDeadline) {
        this.bucket = bucket;
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
        // Sign for the client-facing host without making a request to that endpoint.
        this.publicSigner = S3Presigner.builder().endpointOverride(URI.create(publicEndpoint))
                .region(Region.US_EAST_1).credentialsProvider(credentials).serviceConfiguration(s3).build();
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
                sign(manifest.gameSpec()), manifest.assets().stream().map(this::sign).toList());
    }

    InputStream open(String key) {
        try {
            return internal.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception error) {
            if (error.awsErrorDetails() != null && "NoSuchKey".equals(error.awsErrorDetails().errorCode())) throw new InvalidPackage();
            throw new StorageUnavailable(error);
        } catch (Exception error) {
            throw new StorageUnavailable(error);
        }
    }

    private PackageFile sign(StoredFile file) {
        try {
            String url = publicSigner.presignGetObject(request -> request
                    .signatureDuration(Duration.ofSeconds(urlTtl))
                    .getObjectRequest(object -> object.bucket(bucket).key(file.key()))).url().toExternalForm();
            return new PackageFile(file.path(), url, file.mediaType(), file.sizeBytes(), file.sha256());
        } catch (Exception error) {
            throw new StorageUnavailable(error);
        }
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
        try {
            publicSigner.close();
        } finally {
            internal.close();
        }
    }
}
