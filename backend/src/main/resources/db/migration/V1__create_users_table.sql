-- Shared trigger function: keep updated_at current on every row update.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(50)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- Case-insensitive uniqueness on username/email, only enforced while the account is active
-- (soft-deleted users free up their username/email for reuse).
CREATE UNIQUE INDEX uq_users_username_active ON users (LOWER(username)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_email_active ON users (LOWER(email)) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_deleted_at ON users (deleted_at);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE users IS 'Platform users (dashboard/API principals).';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash; plaintext passwords are never persisted.';
