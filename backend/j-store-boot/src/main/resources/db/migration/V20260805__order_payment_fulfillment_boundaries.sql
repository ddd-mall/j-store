-- Destructive replacement for the unreleased development environment.
-- Order/after-sale data is intentionally discarded because amount, merchant,
-- payment and fulfillment semantics changed and no production compatibility is required.
SET search_path TO develop, public;

DELETE FROM after_sale_command_receipts;
DELETE FROM after_sale_items;
DELETE FROM after_sale_capacities;
DELETE FROM order_refund_facts;
DELETE FROM after_sales;
DELETE FROM order_items;
DELETE FROM orders;

ALTER TABLE spu ADD COLUMN IF NOT EXISTS merchant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE spu_snapshot ADD COLUMN IF NOT EXISTS merchant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE spu ALTER COLUMN merchant_id DROP DEFAULT;
ALTER TABLE spu_snapshot ALTER COLUMN merchant_id DROP DEFAULT;
CREATE INDEX IF NOT EXISTS idx_spu_merchant_status ON spu(merchant_id, status);

ALTER TABLE orders
    ADD COLUMN merchant_id BIGINT NOT NULL,
    ADD COLUMN currency VARCHAR(3) NOT NULL,
    ADD COLUMN items_subtotal NUMERIC(19,0) NOT NULL,
    ADD COLUMN discount_amount NUMERIC(19,0) NOT NULL,
    ADD COLUMN shipping_amount NUMERIC(19,0) NOT NULL,
    ADD COLUMN tax_amount NUMERIC(19,0) NOT NULL,
    ADD COLUMN payable_amount NUMERIC(19,0) NOT NULL,
    ADD COLUMN paid_amount NUMERIC(19,0) NOT NULL,
    ADD COLUMN refunded_amount NUMERIC(19,0) NOT NULL,
    ADD COLUMN payment_reference VARCHAR(64),
    ADD COLUMN fulfillment_reference VARCHAR(64);

ALTER TABLE orders
    DROP COLUMN IF EXISTS total_amount,
    DROP COLUMN IF EXISTS actual_pay,
    DROP COLUMN IF EXISTS total_refunded_amount;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_amounts_non_negative CHECK (
        items_subtotal >= 0 AND discount_amount >= 0 AND shipping_amount >= 0 AND
        tax_amount >= 0 AND payable_amount >= 0 AND paid_amount >= 0 AND refunded_amount >= 0
    ),
    ADD CONSTRAINT chk_orders_amount_composition CHECK (
        payable_amount = items_subtotal - discount_amount + shipping_amount + tax_amount
    ),
    ADD CONSTRAINT chk_orders_payment_amounts CHECK (
        refunded_amount <= paid_amount AND paid_amount <= payable_amount
    ),
    ADD CONSTRAINT uk_orders_payment_reference UNIQUE (payment_reference),
    ADD CONSTRAINT uk_orders_fulfillment_reference UNIQUE (fulfillment_reference);

CREATE INDEX idx_orders_merchant_created ON orders(merchant_id, create_time DESC);

ALTER TABLE after_sales DROP CONSTRAINT IF EXISTS after_sales_status_check;
ALTER TABLE after_sales DROP CONSTRAINT IF EXISTS after_sales_check;

ALTER TABLE after_sales
    ADD COLUMN return_received_at TIMESTAMP,
    ADD COLUMN refund_id VARCHAR(64),
    ADD COLUMN refund_failure_reason VARCHAR(500),
    ADD CONSTRAINT chk_after_sales_status CHECK (
        status IN ('REQUESTED','RETURN_REQUIRED','REFUND_PENDING','REFUND_FAILED','COMPLETED','REJECTED','CANCELLED')
    );

DROP TABLE order_refund_facts;
CREATE TABLE order_refund_facts (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    refund_id VARCHAR(64) NOT NULL,
    after_sale_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    amount NUMERIC(19,0) NOT NULL CHECK (amount > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    UNIQUE(order_id, refund_id, order_item_id)
);
CREATE INDEX idx_order_refund_facts_order_after_sale ON order_refund_facts(order_id, after_sale_id);

CREATE TABLE payment_orders (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    payable_amount NUMERIC(19,0) NOT NULL CHECK (payable_amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','CAPTURED','PARTIALLY_REFUNDED','REFUNDED')),
    provider_transaction_id VARCHAR(128) UNIQUE,
    captured_amount NUMERIC(19,0),
    captured_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (
        (status = 'PENDING' AND provider_transaction_id IS NULL AND captured_amount IS NULL AND captured_at IS NULL)
        OR
        (status <> 'PENDING' AND provider_transaction_id IS NOT NULL AND captured_amount = payable_amount AND captured_at IS NOT NULL)
    )
);
CREATE INDEX idx_payment_orders_merchant_status ON payment_orders(merchant_id, status);

CREATE TABLE payment_refunds (
    id BIGINT PRIMARY KEY,
    payment_order_id BIGINT NOT NULL REFERENCES payment_orders(id) ON DELETE CASCADE,
    after_sale_id BIGINT NOT NULL UNIQUE,
    amount NUMERIC(19,0) NOT NULL CHECK (amount > 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','SUCCEEDED','FAILED')),
    provider_refund_id VARCHAR(128) UNIQUE,
    failure_reason VARCHAR(500),
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_payment_refunds_payment_order ON payment_refunds(payment_order_id);

CREATE TABLE payment_refund_items (
    id VARCHAR(128) PRIMARY KEY,
    payment_refund_id BIGINT NOT NULL REFERENCES payment_refunds(id) ON DELETE CASCADE,
    order_item_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    amount NUMERIC(19,0) NOT NULL CHECK (amount > 0),
    UNIQUE(payment_refund_id, order_item_id)
);
CREATE INDEX idx_payment_refund_items_refund ON payment_refund_items(payment_refund_id);

CREATE TABLE fulfillment_orders (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','READY','SHIPPED','DELIVERED')),
    recipient_name VARCHAR(128) NOT NULL,
    recipient_phone VARCHAR(32),
    recipient_email VARCHAR(256),
    country_code VARCHAR(2) NOT NULL,
    district_code VARCHAR(32) NOT NULL,
    detail_address VARCHAR(512),
    carrier_code VARCHAR(64),
    tracking_number VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (
        (status IN ('PENDING','READY') AND carrier_code IS NULL AND tracking_number IS NULL)
        OR
        (status IN ('SHIPPED','DELIVERED') AND carrier_code IS NOT NULL AND tracking_number IS NOT NULL)
    )
);
CREATE INDEX idx_fulfillment_orders_merchant_status ON fulfillment_orders(merchant_id, status);

CREATE TABLE fulfillment_items (
    id BIGINT PRIMARY KEY,
    fulfillment_order_id BIGINT NOT NULL REFERENCES fulfillment_orders(id) ON DELETE CASCADE,
    order_item_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    UNIQUE(fulfillment_order_id, order_item_id)
);
CREATE INDEX idx_fulfillment_items_order ON fulfillment_items(fulfillment_order_id);
