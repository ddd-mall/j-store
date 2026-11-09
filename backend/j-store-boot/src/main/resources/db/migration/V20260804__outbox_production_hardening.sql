ALTER TABLE outbox_entry
    ADD COLUMN IF NOT EXISTS lock_token BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_outbox_entry_aggregate_order
    ON outbox_entry (aggregate_type, aggregate_id, created_at, id, status);

CREATE INDEX IF NOT EXISTS idx_outbox_entry_expired_in_progress
    ON outbox_entry (locked_until)
    WHERE status = 'IN_PROGRESS';

CREATE INDEX IF NOT EXISTS idx_outbox_entry_dead_letter_updated
    ON outbox_entry (updated_at, id)
    WHERE status = 'DEAD_LETTER';

CREATE TABLE IF NOT EXISTS outbox_dead_letter_audit (
    id BIGSERIAL PRIMARY KEY,
    outbox_entry_id VARCHAR(36) NOT NULL,
    event_id VARCHAR(64),
    operator_id VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    result VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_dead_letter_audit_entry_created
    ON outbox_dead_letter_audit (outbox_entry_id, created_at DESC);
