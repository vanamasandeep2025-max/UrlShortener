CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    key_prefix      VARCHAR(12)  NOT NULL,
    key_hash        VARCHAR(128) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    scopes          VARCHAR(255) NOT NULL DEFAULT 'URL_READ,URL_WRITE',
    last_used_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- key_hash is a SHA-256 hash of the actual secret; the plaintext key is shown to the
-- user exactly once at creation time and never persisted.
CREATE UNIQUE INDEX uq_api_keys_key_hash ON api_keys (key_hash);
CREATE INDEX idx_api_keys_user_id ON api_keys (user_id);
CREATE INDEX idx_api_keys_key_prefix ON api_keys (key_prefix);
CREATE INDEX idx_api_keys_active
    ON api_keys (user_id)
    WHERE revoked_at IS NULL;

COMMENT ON TABLE api_keys IS 'Long-lived API credentials for programmatic access via the X-API-Key header.';
COMMENT ON COLUMN api_keys.key_prefix IS 'Non-secret prefix shown in the dashboard so users can identify a key without re-exposing it.';
