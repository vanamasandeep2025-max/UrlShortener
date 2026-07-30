CREATE TABLE url_clicks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url_id          UUID         NOT NULL REFERENCES urls (id) ON DELETE CASCADE,
    event_id        UUID         NOT NULL,
    clicked_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ip_address      VARCHAR(45),
    ip_hash         VARCHAR(64)  NOT NULL,
    user_agent      TEXT,
    browser         VARCHAR(64),
    browser_version VARCHAR(32),
    os              VARCHAR(64),
    os_version      VARCHAR(32),
    device_type     VARCHAR(32),
    country         VARCHAR(2),
    referrer        TEXT,
    correlation_id  VARCHAR(64),
    CONSTRAINT ck_url_clicks_device_type
        CHECK (device_type IS NULL OR device_type IN ('DESKTOP', 'MOBILE', 'TABLET', 'BOT', 'OTHER'))
);

-- The Kafka consumer is idempotent: a redelivered url-clicked event with the same
-- event_id must not be double-counted. This unique constraint is the enforcement point.
CREATE UNIQUE INDEX uq_url_clicks_event_id ON url_clicks (event_id);

CREATE INDEX idx_url_clicks_url_id ON url_clicks (url_id);
CREATE INDEX idx_url_clicks_url_id_clicked_at ON url_clicks (url_id, clicked_at DESC);
-- Powers "unique visitors" analytics (distinct ip_hash per url).
CREATE INDEX idx_url_clicks_url_id_ip_hash ON url_clicks (url_id, ip_hash);
CREATE INDEX idx_url_clicks_clicked_at ON url_clicks (clicked_at);

COMMENT ON TABLE url_clicks IS 'One row per tracked redirect click; populated asynchronously by the analytics Kafka consumer.';
COMMENT ON COLUMN url_clicks.event_id IS 'Correlates back to the url-clicked Kafka event; enforces consumer idempotency.';
COMMENT ON COLUMN url_clicks.ip_hash IS 'SHA-256 hash of the client IP, used for unique-visitor counting without retaining raw IPs longer than necessary.';
