package com.playly.core.games;

import com.playly.core.games.GameModels.Version;
import com.playly.core.storage.GameStorage;
import com.playly.core.web.Pages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
public class VersionService {
    private final VersionRepository versions;
    private final GameAccess access;
    private final GameStorage storage;
    private final ObjectMapper json;

    VersionService(VersionRepository versions, GameAccess access, GameStorage storage, ObjectMapper json) {
        this.versions = versions;
        this.access = access;
        this.storage = storage;
        this.json = json;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Pages.Page<Version> versions(UUID game, UUID owner, String cursor, int limit) {
        access.requireOwner(game, owner, false);
        String scope = "versions:" + game;
        int number = Pages.number(cursor, scope);
        var rows = versions.list(game, number, limit + 1);
        return Pages.page(rows, limit, scope, value -> Integer.toString(value.number()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Version version(UUID game, UUID version, UUID viewer) {
        access.checkVersionAccess(game, version, viewer, false);
        return versions.storedVersion(game, version).version();
    }

    @Transactional
    GameStorage.GamePackage gamePackage(UUID game, UUID version, UUID viewer) {
        access.checkVersionAccess(game, version, viewer, true);
        var manifest = json.readValue(versions.storedVersion(game, version).manifest(), GameStorage.Manifest.class);
        return storage.sign(game, version, manifest);
    }

    public String baseManifestKey(UUID game, UUID version) {
        return versions.storedVersion(game, version).manifestKey();
    }

    public String editableManifestKey(UUID game, UUID version) {
        var value = versions.storedVersion(game, version);
        if (!"2.0".equals(value.version().gameSpecVersion())) {
            throw com.playly.core.web.ApiProblem.conflict("CONFLICT", "Base game format does not support editing");
        }
        return value.manifestKey();
    }
}
