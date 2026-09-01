CREATE TABLE trade_processes (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    payable_amount_fen BIGINT NOT NULL CHECK (payable_amount_fen >= 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reservation_expires_at TIMESTAMPTZ,
    failure_reason VARCHAR(1024),
    close_reason VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    persistence_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_trade_processes_merchant_status
    ON trade_processes (merchant_id, status);

CREATE TABLE trade_process_items (
    trade_id BIGINT NOT NULL REFERENCES trade_processes(id) ON DELETE CASCADE,
    line_no INTEGER NOT NULL,
    offer_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    spu_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    catalog_snapshot_version BIGINT NOT NULL,
    offer_version BIGINT NOT NULL,
    fulfillment_node_id VARCHAR(128) NOT NULL,
    channel_id VARCHAR(64) NOT NULL,
    unit_price_fen BIGINT NOT NULL CHECK (unit_price_fen >= 0),
    PRIMARY KEY (trade_id, line_no),
    UNIQUE (trade_id, offer_id)
);

CREATE TABLE trade_process_authorizations (
    trade_id BIGINT NOT NULL REFERENCES trade_processes(id) ON DELETE CASCADE,
    line_no INTEGER NOT NULL,
    authorization_id VARCHAR(128) NOT NULL,
    offer_id BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (trade_id, line_no),
    UNIQUE (trade_id, authorization_id),
    UNIQUE (trade_id, offer_id)
);

CREATE TABLE trade_process_reservations (
    trade_id BIGINT NOT NULL REFERENCES trade_processes(id) ON DELETE CASCADE,
    line_no INTEGER NOT NULL,
    reservation_id VARCHAR(192) NOT NULL,
    PRIMARY KEY (trade_id, line_no),
    UNIQUE (trade_id, reservation_id)
);

ALTER TABLE orders DROP COLUMN IF EXISTS sale_authorization_ids;
