package com.playly.core.games;

import com.playly.core.games.GameModels.Game;
import com.playly.core.storage.GameStorage;
import com.playly.core.web.ApiProblem;
import com.playly.core.web.Pages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class GameService {
    private final GameRepository games;
    private final GameAccess access;
    private final VersionRepository versions;
    private final GameStorage storage;

    GameService(GameRepository games, GameAccess access, VersionRepository versions, GameStorage storage) {
        this.games = games;
        this.access = access;
        this.versions = versions;
        this.storage = storage;
    }

    Game get(UUID id, UUID viewer) {
        return games.get(id, viewer);
    }

    Pages.Page<Game> ownGames(UUID owner, String cursor, int limit) {
        return games.ownGames(owner, cursor, limit);
    }

    Pages.Page<Game> catalog(String search, String cursor, int limit) {
        return games.catalog(search, cursor, limit);
    }

    @Transactional
    Game create(UUID owner, String title, String description) {
        UUID id = UUID.randomUUID();
        games.insert(id, owner, title, description);
        return games.get(id, owner);
    }

    @Transactional
    Game update(UUID id, UUID owner, String title, String description) {
        access.requireOwner(id, owner, true);
        games.update(id, title, description);
        return games.get(id, owner);
    }

    @Transactional
    void delete(UUID id, UUID owner) {
        access.requireOwner(id, owner, true);
        if (games.hasActiveGeneration(id)) {
            throw ApiProblem.conflict("GENERATION_IN_PROGRESS", "A generation is already in progress");
        }
        versions.retirePackages(id, storage.retentionSeconds());
        games.delete(id);
    }
}
