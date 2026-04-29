-- Enhance transactional outbox relay safety and retry diagnostics.
ALTER TABLE outbox_entry
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error TEXT;

CREATE INDEX IF NOT EXISTS idx_outbox_entry_claim
    ON outbox_entry (status, next_attempt_at, created_at ASC)
    WHERE status IN ('PENDING', 'FAILED', 'IN_PROGRESS');

CREATE INDEX IF NOT EXISTS idx_outbox_entry_lock_expired
    ON outbox_entry (locked_until ASC)
    WHERE status = 'IN_PROGRESS';
