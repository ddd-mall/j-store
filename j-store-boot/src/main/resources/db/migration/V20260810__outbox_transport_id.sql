ALTER TABLE outbox_entry
    ADD COLUMN IF NOT EXISTS transport_id VARCHAR(64);

UPDATE outbox_entry
SET transport_id = CASE delivery_target
    WHEN 'LOCAL_DOMAIN' THEN 'local-domain'
    WHEN 'LOCAL_INTEGRATION' THEN 'local'
    WHEN 'BROKER' THEN 'broker'
    ELSE 'broker'
END
WHERE transport_id IS NULL OR transport_id = '';

ALTER TABLE outbox_entry
    ALTER COLUMN transport_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_entry_transport_ready
    ON outbox_entry (transport_id, status, next_attempt_at, created_at);
