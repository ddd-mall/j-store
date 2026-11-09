-- Destructive schema replacement for the unreleased development environment only.
-- Existing order data is intentionally discarded; this migration is not suitable for production upgrades.
DELETE FROM order_items;
DELETE FROM orders;

DROP INDEX IF EXISTS idx_orders_status_create_time;

ALTER TABLE orders
    DROP COLUMN IF EXISTS previous_status,
    DROP COLUMN IF EXISTS status,
    ADD COLUMN trade_status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    ADD COLUMN payment_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
    ADD COLUMN fulfillment_status VARCHAR(32) NOT NULL DEFAULT 'UNFULFILLED',
    ADD COLUMN after_sale_status VARCHAR(32) NOT NULL DEFAULT 'NONE';

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_trade_status
        CHECK (trade_status IN ('CREATED', 'ACTIVE', 'CLOSED', 'COMPLETED')),
    ADD CONSTRAINT chk_orders_payment_status
        CHECK (payment_status IN ('UNPAID', 'PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    ADD CONSTRAINT chk_orders_fulfillment_status
        CHECK (fulfillment_status IN ('UNFULFILLED', 'PENDING_SHIPMENT', 'SHIPPED', 'DELIVERED')),
    ADD CONSTRAINT chk_orders_after_sale_status
        CHECK (after_sale_status IN ('NONE', 'PROCESSING', 'PARTIALLY_COMPLETED', 'COMPLETED'));

CREATE INDEX idx_orders_trade_status_create_time
    ON orders(trade_status, create_time DESC);
CREATE INDEX idx_orders_payment_status_create_time
    ON orders(payment_status, create_time DESC);
CREATE INDEX idx_orders_fulfillment_status_create_time
    ON orders(fulfillment_status, create_time DESC);
CREATE INDEX idx_orders_after_sale_status_create_time
    ON orders(after_sale_status, create_time DESC);
