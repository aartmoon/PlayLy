package com.playly.core.storage;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
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

    private final MinioClient internal;
    private final MinioClient publicSigner;
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
        this.internal = MinioClient.builder().endpoint(endpoint).region("us-east-1").credentials(accessKey, secretKey).build();
        this.internal.setTimeout(2_000, 5_000, 5_000);
        this.publicSigner = MinioClient.builder().endpoint(publicEndpoint).region("us-east-1").credentials(accessKey, secretKey).build();
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
            return internal.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (ErrorResponseException error) {
            if ("NoSuchKey".equals(error.errorResponse().code())) throw new InvalidPackage();
            throw new StorageUnavailable(error);
        } catch (Exception error) {
            throw new StorageUnavailable(error);
        }
    }

    private PackageFile sign(StoredFile file) {
        try {
            String url = publicSigner.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(file.key()).expiry(urlTtl).build());
            return new PackageFile(file.path(), url, file.mediaType(), file.sizeBytes(), file.sha256());
        } catch (Exception error) {
            throw new StorageUnavailable(error);
        }
    }

    String bucket() {
        return bucket;
    }

    Iterable<io.minio.Result<io.minio.messages.Item>> generationObjects() {
        return internal.listObjects(ListObjectsArgs.builder().bucket(bucket)
                .prefix("generations/").recursive(true).build());
    }

    void remove(String key) throws Exception {
        internal.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    }
}
