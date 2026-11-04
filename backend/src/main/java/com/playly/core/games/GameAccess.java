package com.playly.core.games;

import com.playly.core.games.GameModels.Access;
import com.playly.core.web.ApiProblem;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GameAccess {
    private final GameRepository games;

    GameAccess(GameRepository games) {
        this.games = games;
    }

    public void requireOwner(UUID game, UUID user, boolean lock) {
        if (!games.access(game, lock).ownerId().equals(user)) throw ApiProblem.missing();
    }

    Access requirePublished(UUID game, boolean lock) {
        var access = games.access(game, lock);
        if (access.publishedVersionId() == null) throw ApiProblem.missing();
        return access;
    }

    void checkVersionAccess(UUID game, UUID version, UUID viewer, boolean lock) {
        var access = games.access(game, lock);
        if (!access.ownerId().equals(viewer) && !version.equals(access.publishedVersionId())) throw ApiProblem.missing();
    }
}
