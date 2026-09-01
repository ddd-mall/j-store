-- Preserve legacy authorization and reservation rows while allowing the Trade
-- coordinator to write plan-scoped commitments.
ALTER TABLE sale_authorizations
    ADD COLUMN trade_id BIGINT,
    ADD COLUMN order_plan_id BIGINT,
    ALTER COLUMN order_id DROP NOT NULL,
    DROP CONSTRAINT uk_sale_authorization_order_offer,
    ADD CONSTRAINT uk_sale_authorization_plan_offer UNIQUE (order_plan_id, offer_id);

ALTER TABLE inventory_stock_reservations
    ADD COLUMN trade_id BIGINT,
    ADD COLUMN order_plan_id BIGINT,
    ALTER COLUMN order_id DROP NOT NULL;
CREATE INDEX idx_inventory_reservation_plan
    ON inventory_stock_reservations(order_plan_id);

CREATE TABLE trades (
    id BIGINT PRIMARY KEY,
    checkout_request_id VARCHAR(128) NOT NULL,
    request_digest VARCHAR(80) NOT NULL,
    buyer_party_type VARCHAR(32) NOT NULL,
    buyer_party_id BIGINT NOT NULL,
    buyer_display_name VARCHAR(128) NOT NULL,
    buyer_phone VARCHAR(32),
    acting_principal_id BIGINT NOT NULL,
    recipient_name VARCHAR(256) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    recipient_phone VARCHAR(64),
    recipient_email VARCHAR(320),
    district_code VARCHAR(64) NOT NULL,
    detail_address VARCHAR(1024) NOT NULL,
    shipping_address JSONB NOT NULL,
    postal_code VARCHAR(32),
    payable_amount_fen BIGINT NOT NULL CHECK (payable_amount_fen > 0),
    currency VARCHAR(3) NOT NULL,
    trade_mode VARCHAR(32) NOT NULL,
    settlement_mode VARCHAR(32) NOT NULL,
    fulfillment_release_rule VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    settlement_plan_id BIGINT UNIQUE,
    failure_reason VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    persistence_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_trade_buyer_checkout_request
        UNIQUE (buyer_party_type, buyer_party_id, checkout_request_id)
);

CREATE INDEX idx_trades_buyer_status
    ON trades (buyer_party_type, buyer_party_id, status);

CREATE TABLE trade_customs_fields (
    trade_id BIGINT NOT NULL REFERENCES trades(id) ON DELETE CASCADE,
    field_name VARCHAR(128) NOT NULL,
    field_value VARCHAR(1024) NOT NULL,
    PRIMARY KEY (trade_id, field_name)
);

CREATE TABLE trade_payment_installments (
    trade_id BIGINT NOT NULL REFERENCES trades(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    installment_id VARCHAR(128) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    amount_fen BIGINT NOT NULL CHECK (amount_fen > 0),
    PRIMARY KEY (trade_id, sequence_no),
    UNIQUE (trade_id, installment_id)
);

CREATE TABLE trade_installment_payment_refs (
    trade_id BIGINT NOT NULL REFERENCES trades(id) ON DELETE CASCADE,
    installment_id VARCHAR(128) NOT NULL,
    payment_id BIGINT NOT NULL UNIQUE,
    PRIMARY KEY (trade_id, installment_id),
    FOREIGN KEY (trade_id, installment_id)
        REFERENCES trade_payment_installments(trade_id, installment_id)
        ON DELETE CASCADE
);

CREATE TABLE trade_order_plans (
    order_plan_id BIGINT PRIMARY KEY,
    trade_id BIGINT NOT NULL REFERENCES trades(id) ON DELETE CASCADE,
    plan_no INTEGER NOT NULL,
    merchant_id BIGINT NOT NULL,
    fulfillment_group VARCHAR(128) NOT NULL,
    payable_amount_fen BIGINT NOT NULL CHECK (payable_amount_fen > 0),
    status VARCHAR(32) NOT NULL,
    order_id BIGINT UNIQUE REFERENCES orders(id),
    reservation_expires_at TIMESTAMPTZ,
    UNIQUE (trade_id, plan_no)
);

CREATE INDEX idx_trade_order_plans_trade_status
    ON trade_order_plans (trade_id, status);

CREATE TABLE trade_order_plan_items (
    order_plan_id BIGINT NOT NULL REFERENCES trade_order_plans(order_plan_id) ON DELETE CASCADE,
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
    unit_price_fen BIGINT NOT NULL CHECK (unit_price_fen > 0),
    goods_name VARCHAR(256) NOT NULL,
    sku_description VARCHAR(512) NOT NULL,
    PRIMARY KEY (order_plan_id, line_no),
    UNIQUE (order_plan_id, offer_id)
);

CREATE TABLE trade_order_plan_authorizations (
    order_plan_id BIGINT NOT NULL REFERENCES trade_order_plans(order_plan_id) ON DELETE CASCADE,
    line_no INTEGER NOT NULL,
    authorization_id VARCHAR(128) NOT NULL,
    offer_id BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (order_plan_id, line_no),
    UNIQUE (order_plan_id, authorization_id),
    UNIQUE (order_plan_id, offer_id)
);

CREATE TABLE trade_order_plan_reservations (
    order_plan_id BIGINT NOT NULL REFERENCES trade_order_plans(order_plan_id) ON DELETE CASCADE,
    line_no INTEGER NOT NULL,
    reservation_id VARCHAR(192) NOT NULL,
    PRIMARY KEY (order_plan_id, line_no),
    UNIQUE (order_plan_id, reservation_id)
);

CREATE TABLE trade_payments (
    id BIGINT PRIMARY KEY,
    trade_id BIGINT NOT NULL REFERENCES trades(id),
    settlement_plan_id BIGINT NOT NULL,
    installment_id VARCHAR(128) NOT NULL,
    payable_amount_fen BIGINT NOT NULL CHECK (payable_amount_fen > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_reference TEXT,
    pay_action VARCHAR(2048),
    provider_accepted_at TIMESTAMPTZ,
    accept_before TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    failure_reason VARCHAR(1024),
    cancellation_reason VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL,
    persistence_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_trade_payment_installment UNIQUE (settlement_plan_id, installment_id)
);

ALTER TABLE trade_installment_payment_refs
    ADD CONSTRAINT fk_trade_installment_payment
        FOREIGN KEY (payment_id) REFERENCES trade_payments(id);

CREATE TABLE trade_payment_allocations (
    payment_id BIGINT NOT NULL REFERENCES trade_payments(id) ON DELETE CASCADE,
    allocation_no INTEGER NOT NULL,
    order_plan_id BIGINT NOT NULL REFERENCES trade_order_plans(order_plan_id),
    order_id BIGINT NOT NULL REFERENCES orders(id),
    merchant_id BIGINT NOT NULL,
    amount_fen BIGINT NOT NULL CHECK (amount_fen > 0),
    PRIMARY KEY (payment_id, allocation_no),
    UNIQUE (payment_id, order_plan_id)
);

ALTER TABLE orders
    ADD COLUMN source_trade_id BIGINT,
    ADD COLUMN source_order_plan_id BIGINT,
    ADD COLUMN source_plan_digest VARCHAR(80),
    ADD CONSTRAINT uk_orders_source_order_plan UNIQUE (source_order_plan_id),
    ADD CONSTRAINT fk_orders_source_trade
        FOREIGN KEY (source_trade_id) REFERENCES trades(id),
    ADD CONSTRAINT fk_orders_source_order_plan
        FOREIGN KEY (source_order_plan_id) REFERENCES trade_order_plans(order_plan_id),
    ADD CONSTRAINT chk_orders_trade_source_complete CHECK (
        (source_trade_id IS NULL AND source_order_plan_id IS NULL AND source_plan_digest IS NULL)
        OR
        (source_trade_id IS NOT NULL AND source_order_plan_id IS NOT NULL AND source_plan_digest IS NOT NULL)
    );
