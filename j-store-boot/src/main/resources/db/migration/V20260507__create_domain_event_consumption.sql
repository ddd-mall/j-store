-- Domain event listener idempotency records.
CREATE TABLE IF NOT EXISTS domain_event_consumption (
    listener_id   VARCHAR(512) NOT NULL,
    event_id      VARCHAR(64)  NOT NULL,
    event_name    VARCHAR(256) NOT NULL,
    event_version INTEGER      NOT NULL DEFAULT 1,
    consumed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (listener_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_domain_event_consumption_event
    ON domain_event_consumption (event_name, event_version, consumed_at);
