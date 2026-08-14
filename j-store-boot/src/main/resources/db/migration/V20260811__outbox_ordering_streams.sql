ALTER TABLE outbox_entry
    ADD COLUMN IF NOT EXISTS ordering_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS sequence_no BIGINT;

UPDATE outbox_entry
SET ordering_key = CASE
    WHEN message_kind = 'DOMAIN_EVENT'
        THEN encode(sha256(convert_to(
            octet_length(aggregate_type) || ':' || aggregate_type || ':' ||
            octet_length(aggregate_id) || ':' || aggregate_id,
            'UTF8'
        )), 'hex')
    ELSE encode(sha256(convert_to(
        octet_length(destination) || ':' || destination || ':' ||
        octet_length(partition_key) || ':' || partition_key,
        'UTF8'
    )), 'hex')
END
WHERE ordering_key IS NULL OR ordering_key = '';

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY transport_id, ordering_key
               ORDER BY created_at, id
           ) AS stream_sequence
    FROM outbox_entry
)
UPDATE outbox_entry entry
SET sequence_no = ranked.stream_sequence
FROM ranked
WHERE entry.id = ranked.id
  AND entry.sequence_no IS NULL;

ALTER TABLE outbox_entry
    ALTER COLUMN ordering_key SET NOT NULL,
    ALTER COLUMN sequence_no SET NOT NULL;

ALTER TABLE outbox_entry
    ADD CONSTRAINT uk_outbox_transport_stream_sequence
        UNIQUE (transport_id, ordering_key, sequence_no);

CREATE TABLE outbox_stream_position (
    transport_id VARCHAR(64) NOT NULL,
    ordering_key VARCHAR(64) NOT NULL,
    last_sequence_no BIGINT NOT NULL CHECK (last_sequence_no > 0),
    PRIMARY KEY (transport_id, ordering_key)
);

CREATE TABLE message_stream_consumption (
    consumer_id VARCHAR(512) NOT NULL,
    transport_id VARCHAR(64) NOT NULL,
    ordering_key VARCHAR(64) NOT NULL,
    last_sequence_no BIGINT NOT NULL CHECK (last_sequence_no >= 0),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_id, transport_id, ordering_key)
);

CREATE INDEX idx_message_stream_consumption_retention
    ON message_stream_consumption (updated_at, consumer_id, transport_id, ordering_key);

INSERT INTO outbox_stream_position (transport_id, ordering_key, last_sequence_no)
SELECT transport_id, ordering_key, MAX(sequence_no)
FROM outbox_entry
GROUP BY transport_id, ordering_key;

INSERT INTO message_stream_consumption
    (consumer_id, transport_id, ordering_key, last_sequence_no, updated_at)
SELECT 'jstore.local-integration-bus',
       transport_id,
       ordering_key,
       COALESCE(
           MIN(sequence_no) FILTER (WHERE status <> 'PUBLISHED') - 1,
           MAX(sequence_no)
       ),
       NOW()
FROM outbox_entry
WHERE transport_id = 'local'
GROUP BY transport_id, ordering_key;

CREATE INDEX idx_outbox_entry_stream_claim
    ON outbox_entry (transport_id, ordering_key, sequence_no, status);
