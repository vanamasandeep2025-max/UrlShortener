CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    actor_type      VARCHAR(16)  NOT NULL,
    action          VARCHAR(64)  NOT NULL,
    entity_type     VARCHAR(32)  NOT NULL,
    entity_id       VARCHAR(64),
    details         JSONB,
    ip_address      VARCHAR(45),
    correlation_id  VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_logs_actor_type CHECK (actor_type IN ('USER', 'API_KEY', 'SYSTEM', 'ANONYMOUS'))
);

CREATE INDEX idx_audit_logs_actor_user_id ON audit_logs (actor_user_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_details_gin ON audit_logs USING gin (details);

COMMENT ON TABLE audit_logs IS 'Immutable audit trail of security- and data-relevant actions (append-only; no updates/deletes).';
