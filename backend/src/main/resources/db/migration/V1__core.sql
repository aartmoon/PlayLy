CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE,
    display_name varchar(100) NOT NULL,
    password_hash text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE refresh_sessions (
    token_hash char(64) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL
);
CREATE INDEX refresh_sessions_expiry ON refresh_sessions (expires_at);

CREATE TABLE auth_limits (
    client text PRIMARY KEY,
    window_start timestamptz NOT NULL,
    requests integer NOT NULL
);
CREATE INDEX auth_limits_expiry ON auth_limits (window_start);

CREATE TABLE games (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES users(id),
    title varchar(120) NOT NULL,
    description varchar(2000) NOT NULL,
    published_version_id uuid,
    published_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((published_version_id IS NULL) = (published_at IS NULL))
);
CREATE INDEX games_owner_page ON games (owner_id, updated_at DESC, id DESC);
CREATE INDEX games_catalog_page ON games (published_at DESC, id DESC) WHERE published_version_id IS NOT NULL;

CREATE TABLE generations (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    idempotency_key varchar(128) NOT NULL,
    prompt varchar(10000) NOT NULL,
    base_version_id uuid,
    game_spec_version text NOT NULL,
    runtime_version text NOT NULL,
    status text NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    error_code text,
    error_message text,
    deadline_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    UNIQUE (game_id, idempotency_key),
    UNIQUE (game_id, id),
    CHECK ((status IN ('SUCCEEDED', 'FAILED')) = (finished_at IS NOT NULL)),
    CHECK ((status = 'FAILED') = (error_code IS NOT NULL AND error_message IS NOT NULL))
);
CREATE UNIQUE INDEX generations_one_active ON generations(game_id) WHERE finished_at IS NULL;
CREATE INDEX generations_deadlines ON generations(deadline_at) WHERE finished_at IS NULL;
CREATE INDEX generations_history ON generations(game_id, created_at DESC, id DESC);

CREATE TABLE game_versions (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    generation_id uuid NOT NULL UNIQUE,
    number integer NOT NULL CHECK (number > 0),
    manifest_key text NOT NULL,
    manifest jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (game_id, id),
    UNIQUE (game_id, number),
    FOREIGN KEY (game_id, generation_id) REFERENCES generations(game_id, id) ON DELETE CASCADE
);
ALTER TABLE games ADD FOREIGN KEY (id, published_version_id)
    REFERENCES game_versions(game_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE generations ADD FOREIGN KEY (game_id, base_version_id)
    REFERENCES game_versions(game_id, id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE ratings (
    game_id uuid NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id),
    score integer NOT NULL CHECK (score BETWEEN 1 AND 5),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (game_id, user_id)
);

CREATE TABLE generation_outbox (
    generation_id uuid PRIMARY KEY REFERENCES generations(id) ON DELETE CASCADE,
    payload text NOT NULL,
    available_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX generation_outbox_pending ON generation_outbox(available_at);

CREATE TABLE generation_inbox (
    event_id uuid PRIMARY KEY,
    generation_id uuid NOT NULL REFERENCES generations(id) ON DELETE CASCADE
);

-- Keep previously signed packages readable for a grace period after deleting a game.
CREATE TABLE retired_packages (
    generation_id uuid PRIMARY KEY,
    manifest_key text NOT NULL,
    retain_until timestamptz NOT NULL
);
CREATE INDEX retired_packages_expiry ON retired_packages(retain_until);
