-- Enables trigram indexing for fast substring search on original_url.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE urls (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    short_code      VARCHAR(16)  NOT NULL,
    original_url    TEXT         NOT NULL,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    password_hash   VARCHAR(100),
    click_count     BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT ck_urls_original_url_length CHECK (char_length(original_url) <= 8192),
    CONSTRAINT ck_urls_click_count_nonneg CHECK (click_count >= 0)
);

-- short_code must be unique among *live* rows only; a soft-deleted code can be
-- garbage-collected/reused later without violating history.
CREATE UNIQUE INDEX uq_urls_short_code_active ON urls (short_code) WHERE deleted_at IS NULL;

CREATE INDEX idx_urls_user_id ON urls (user_id);
CREATE INDEX idx_urls_user_id_created_at ON urls (user_id, created_at DESC);
CREATE INDEX idx_urls_expires_at ON urls (expires_at) WHERE deleted_at IS NULL AND expires_at IS NOT NULL;
CREATE INDEX idx_urls_deleted_at ON urls (deleted_at);
-- Supports substring search on original_url ("Search" list requirement) without a full table scan.
CREATE INDEX idx_urls_original_url_trgm ON urls USING gin (original_url gin_trgm_ops);

CREATE TRIGGER trg_urls_updated_at
    BEFORE UPDATE ON urls
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE urls IS 'Shortened URL records. Soft-deleted via deleted_at; expiry via expires_at.';
COMMENT ON COLUMN urls.password_hash IS 'Optional BCrypt hash for password-protected ("secure sharing") links; NULL = public link.';
COMMENT ON COLUMN urls.click_count IS 'Denormalized counter maintained by the analytics consumer; url_clicks remains the source of truth.';
