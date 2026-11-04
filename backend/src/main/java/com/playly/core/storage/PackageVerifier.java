package com.playly.core.storage;

import com.playly.core.storage.GameStorage.InvalidPackage;
import com.playly.core.storage.GameStorage.Manifest;
import com.playly.core.storage.GameStorage.Reference;
import com.playly.core.storage.GameStorage.StorageUnavailable;
import com.playly.core.storage.GameStorage.StoredFile;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class PackageVerifier {
    private static final int MAX_MANIFEST_SIZE = 1024 * 1024;
    private static final int MAX_SPEC_SIZE = 4 * 1024 * 1024;
    private static final long MAX_PACKAGE_SIZE = 64L * 1024 * 1024;
    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");
    static final Pattern ATTEMPT = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final GameStorage storage;
    private final ObjectMapper json;

    PackageVerifier(GameStorage storage, ObjectMapper json) {
        this.storage = storage;
        this.json = json;
    }

    public Manifest verify(UUID generation, Reference reference, String schema, String runtime, Instant deadline) {
        String prefix = attemptPrefix(generation, reference);
        Manifest manifest;
        try (var input = storage.open(reference.key())) {
            byte[] data = input.readNBytes(MAX_MANIFEST_SIZE + 1);
            if (data.length > MAX_MANIFEST_SIZE) throw new InvalidPackage();
            manifest = json.readValue(data, Manifest.class);
        } catch (JacksonException error) {
            throw new InvalidPackage();
        } catch (java.io.IOException error) {
            throw new StorageUnavailable(error);
        }
        if (manifest == null || !schema.equals(manifest.gameSpecVersion()) || !runtime.equals(manifest.runtimeVersion())
                || manifest.gameSpec() == null || manifest.assets() == null
                || !"game.json".equals(manifest.gameSpec().path())
                || !"application/json".equals(manifest.gameSpec().mediaType())) {
            throw new InvalidPackage();
        }

        var files = Stream.concat(Stream.of(manifest.gameSpec()), manifest.assets().stream()).toList();
        var paths = new HashSet<String>();
        var keys = new HashSet<String>();
        long remaining = MAX_PACKAGE_SIZE;
        for (var file : files) {
            if (file == null || !relativePath(file.path()) || !paths.add(file.path()) || !storage.bucket().equals(file.bucket())
                    || file.key() == null || !file.key().startsWith(prefix)
                    || !relativePath(file.key().substring(prefix.length())) || file.key().equals(reference.key())
                    || !keys.add(file.key()) || file.mediaType() == null || file.mediaType().isBlank()
                    || file.sizeBytes() == null || file.sizeBytes() < 0 || file.sizeBytes() > remaining
                    || file.sha256() == null || !SHA256.matcher(file.sha256()).matches()) {
                throw new InvalidPackage();
            }
            remaining -= file.sizeBytes();
        }
        if (manifest.gameSpec().sizeBytes() > MAX_SPEC_SIZE) throw new InvalidPackage();
        for (var file : files) {
            verifyFile(file, file == manifest.gameSpec(), deadline);
        }
        return manifest;
    }

    private String attemptPrefix(UUID generation, Reference reference) {
        String root = "generations/" + generation + "/";
        if (reference == null || !storage.bucket().equals(reference.bucket()) || reference.key() == null
                || !reference.key().startsWith(root)) throw new InvalidPackage();
        String[] tail = reference.key().substring(root.length()).split("/", -1);
        if (tail.length != 2 || !ATTEMPT.matcher(tail[0]).matches() || !tail[1].equals("manifest.json")) {
            throw new InvalidPackage();
        }
        return root + tail[0] + "/";
    }

    private void verifyFile(StoredFile file, boolean gameSpec, Instant deadline) {
        try (var input = storage.open(file.key())) {
            var digest = MessageDigest.getInstance("SHA-256");
            if (gameSpec) {
                byte[] bytes = input.readNBytes(Math.toIntExact(file.sizeBytes()) + 1);
                if (bytes.length != file.sizeBytes()) throw new InvalidPackage();
                var spec = json.readTree(bytes);
                if (spec == null || !spec.isObject()) throw new InvalidPackage();
                digest.update(bytes);
            } else {
                byte[] buffer = new byte[8192];
                long size = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    if (size > file.sizeBytes() || !Instant.now().isBefore(deadline)) throw new InvalidPackage();
                    digest.update(buffer, 0, read);
                }
                if (size != file.sizeBytes()) throw new InvalidPackage();
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(file.sha256())) throw new InvalidPackage();
        } catch (JacksonException error) {
            throw new InvalidPackage();
        } catch (java.io.IOException error) {
            throw new StorageUnavailable(error);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static boolean relativePath(String path) {
        if (path == null || path.length() > 1024 || path.indexOf('\\') >= 0
                || path.chars().anyMatch(Character::isISOControl)) return false;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        }
        return true;
    }
}
