ALTER TABLE outbox_entry
    ADD COLUMN IF NOT EXISTS message_kind VARCHAR(32),
    ADD COLUMN IF NOT EXISTS delivery_target VARCHAR(32),
    ADD COLUMN IF NOT EXISTS destination VARCHAR(512),
    ADD COLUMN IF NOT EXISTS partition_key VARCHAR(256),
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS causation_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);

UPDATE outbox_entry
SET message_kind = COALESCE(message_kind, 'DOMAIN_EVENT'),
    delivery_target = COALESCE(delivery_target, 'LOCAL_DOMAIN'),
    destination = COALESCE(NULLIF(destination, ''), event_type),
    partition_key = COALESCE(NULLIF(partition_key, ''), aggregate_id),
    correlation_id = COALESCE(NULLIF(correlation_id, ''), event_id, id);

ALTER TABLE outbox_entry
    ALTER COLUMN message_kind SET NOT NULL,
    ALTER COLUMN message_kind SET DEFAULT 'DOMAIN_EVENT',
    ALTER COLUMN delivery_target SET NOT NULL,
    ALTER COLUMN delivery_target SET DEFAULT 'LOCAL_DOMAIN',
    ALTER COLUMN destination SET NOT NULL,
    ALTER COLUMN partition_key SET NOT NULL,
    ALTER COLUMN correlation_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_entry_target_ready
    ON outbox_entry (delivery_target, status, next_attempt_at, created_at);
