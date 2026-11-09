ALTER TABLE outbox_entry
    ADD COLUMN logical_destination VARCHAR(512),
    ADD COLUMN delivery_profile VARCHAR(64),
    ADD COLUMN accept_before TIMESTAMPTZ,
    ADD COLUMN published_at TIMESTAMPTZ;

ALTER TABLE outbox_entry
    ALTER COLUMN logical_destination SET NOT NULL,
    ALTER COLUMN delivery_profile SET NOT NULL,
    ADD CONSTRAINT chk_outbox_publication_time
        CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL)),
    ADD CONSTRAINT chk_outbox_acceptance_deadline_kind
        CHECK (accept_before IS NULL OR message_kind = 'INTEGRATION_COMMAND'),
    ADD CONSTRAINT chk_outbox_domain_delivery_metadata
        CHECK (
            message_kind <> 'DOMAIN_EVENT'
            OR (delivery_profile = 'LOCAL_DOMAIN' AND accept_before IS NULL)
        );

CREATE INDEX idx_outbox_entry_logical_destination
    ON outbox_entry (logical_destination, status, next_attempt_at);

CREATE INDEX idx_outbox_entry_accept_before
    ON outbox_entry (accept_before)
    WHERE accept_before IS NOT NULL AND status IN ('PENDING', 'FAILED', 'IN_PROGRESS');
