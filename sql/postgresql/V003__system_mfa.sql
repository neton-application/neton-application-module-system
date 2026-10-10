CREATE TABLE IF NOT EXISTS system_mfa (
    user_id BIGINT PRIMARY KEY,
    required INTEGER NOT NULL DEFAULT 0 CHECK (required IN (0, 1)),
    secret TEXT,
    revision BIGINT NOT NULL DEFAULT 0,
    last_step BIGINT NOT NULL DEFAULT -1
);
CREATE TABLE IF NOT EXISTS system_mfa_challenges (
    user_id BIGINT PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    revision BIGINT NOT NULL,
    enrollment INTEGER NOT NULL,
    secret TEXT,
    password_hash TEXT NOT NULL,
    expires_at BIGINT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0
);
