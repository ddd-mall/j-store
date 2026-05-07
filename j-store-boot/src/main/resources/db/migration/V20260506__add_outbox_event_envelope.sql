-- Add stable event envelope metadata while retaining JVM class name for deserialization compatibility.
ALTER TABLE outbox_entry
    ADD COLUMN IF NOT EXISTS event_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS event_class_name VARCHAR(512),
    ADD COLUMN IF NOT EXISTS event_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE outbox_entry
SET event_id = id
WHERE event_id IS NULL OR event_id = '';

UPDATE outbox_entry
SET event_class_name = event_type
WHERE event_class_name IS NULL OR event_class_name = '';

ALTER TABLE outbox_entry
    ALTER COLUMN event_id SET NOT NULL,
    ALTER COLUMN event_class_name SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_entry_event_id
    ON outbox_entry (event_id);
