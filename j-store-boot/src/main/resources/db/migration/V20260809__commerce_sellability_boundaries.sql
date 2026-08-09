-- Store / Offer authority.
CREATE TABLE IF NOT EXISTS stores (
    id BIGINT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    persistence_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sales_offers (
    id BIGINT PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES stores(id),
    merchant_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    channel_id VARCHAR(64) NOT NULL,
    market VARCHAR(32) NOT NULL,
    price_fen BIGINT NOT NULL CHECK (price_fen > 0),
    status VARCHAR(32) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    max_quantity_per_order INTEGER NOT NULL CHECK (max_quantity_per_order > 0),
    fulfillment_node_id VARCHAR(128) NOT NULL,
    allow_backorder BOOLEAN NOT NULL DEFAULT FALSE,
    offer_version BIGINT NOT NULL CHECK (offer_version > 0),
    persistence_version BIGINT NOT NULL DEFAULT 0,
    CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX IF NOT EXISTS idx_sales_offers_sku_channel
    ON sales_offers(sku_id, channel_id, market);

-- Preserve the intent of legacy catalog sale states as default-channel Offers before
-- converting Catalog to a content-only lifecycle. IDs are deterministic for retryability.
INSERT INTO stores(id, merchant_id, name, status)
SELECT DISTINCT s.merchant_id, s.merchant_id, 'Default Store', 'ACTIVE'
FROM spu s
WHERE s.status IN ('ON_SALE', 'OFF_SALE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sales_offers(
    id, store_id, merchant_id, sku_id, channel_id, market, price_fen, status,
    starts_at, ends_at, max_quantity_per_order, fulfillment_node_id,
    allow_backorder, offer_version
)
SELECT
    k.id, s.merchant_id, s.merchant_id, k.id, 'ONLINE', 'DEFAULT',
    GREATEST(k.price::BIGINT, 1),
    CASE WHEN s.status = 'ON_SALE' THEN 'ACTIVE' ELSE 'SUSPENDED' END,
    s.create_time AT TIME ZONE 'UTC', NULL, 999999, 'DEFAULT', FALSE, 1
FROM sku k
JOIN spu s ON s.id = k.spu_id
WHERE s.status IN ('ON_SALE', 'OFF_SALE')
ON CONFLICT (id) DO NOTHING;

-- Catalog publication now means that content may be referenced by a Store Offer.
UPDATE spu SET status = 'PUBLISHED' WHERE status IN ('ON_SALE', 'OFF_SALE');
ALTER TABLE sku DROP COLUMN IF EXISTS price;

CREATE TABLE IF NOT EXISTS sale_authorizations (
    id VARCHAR(128) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    offer_id BIGINT NOT NULL REFERENCES sales_offers(id),
    store_id BIGINT NOT NULL REFERENCES stores(id),
    merchant_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    offer_version BIGINT NOT NULL,
    unit_price_fen BIGINT NOT NULL CHECK (unit_price_fen > 0),
    fulfillment_node_id VARCHAR(128) NOT NULL,
    allow_backorder BOOLEAN NOT NULL DEFAULT FALSE,
    authorized_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    persistence_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sale_authorization_order_offer UNIQUE(order_id, offer_id),
    CHECK (expires_at > authorized_at)
);
CREATE INDEX IF NOT EXISTS idx_sale_authorization_expiry
    ON sale_authorizations(status, expires_at);

-- Inventory / ATP authority. Physical quantities are WMS mirrors, reservations are local facts.
CREATE TABLE IF NOT EXISTS inventory_stock_positions (
    id VARCHAR(192) PRIMARY KEY,
    sku_id BIGINT NOT NULL,
    fulfillment_node_id VARCHAR(128) NOT NULL,
    on_hand INTEGER NOT NULL CHECK (on_hand >= 0),
    reserved INTEGER NOT NULL CHECK (reserved >= 0),
    safety_stock INTEGER NOT NULL CHECK (safety_stock >= 0),
    isolated_quantity INTEGER NOT NULL CHECK (isolated_quantity >= 0),
    source_version BIGINT NOT NULL CHECK (source_version >= 0),
    persistence_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_inventory_position_sku_node UNIQUE(sku_id, fulfillment_node_id)
);

CREATE TABLE IF NOT EXISTS inventory_stock_reservations (
    id VARCHAR(256) PRIMARY KEY,
    business_key VARCHAR(256) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    sale_authorization_id VARCHAR(128) NOT NULL,
    sku_id BIGINT NOT NULL,
    fulfillment_node_id VARCHAR(128) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    persistence_version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_inventory_reservation_order
    ON inventory_stock_reservations(order_id);
CREATE INDEX IF NOT EXISTS idx_inventory_reservation_expiry
    ON inventory_stock_reservations(status, expires_at);

-- WMS physical stock authority.
CREATE TABLE IF NOT EXISTS warehouse_physical_stock (
    id VARCHAR(192) PRIMARY KEY,
    sku_id BIGINT NOT NULL,
    fulfillment_node_id VARCHAR(128) NOT NULL,
    on_hand INTEGER NOT NULL CHECK (on_hand >= 0),
    source_version BIGINT NOT NULL CHECK (source_version >= 0),
    persistence_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_warehouse_stock_sku_node UNIQUE(sku_id, fulfillment_node_id)
);

-- Persisted Order Saga stage and frozen Offer references.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS commitment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_OFFER';
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS sale_authorization_ids TEXT NOT NULL DEFAULT '';

UPDATE orders
SET commitment_status = CASE
    WHEN trade_status = 'CREATED' THEN 'PENDING_OFFER'
    WHEN trade_status = 'CLOSED' THEN 'FAILED'
    ELSE 'CONFIRMED'
END;

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS offer_id BIGINT;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS store_id BIGINT;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS offer_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS fulfillment_node_id VARCHAR(128) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS channel_id VARCHAR(64) NOT NULL DEFAULT 'ONLINE';

UPDATE order_items SET offer_id = sku_id WHERE offer_id IS NULL;
UPDATE order_items oi
SET store_id = o.merchant_id
FROM orders o
WHERE oi.order_id = o.id AND oi.store_id IS NULL;

ALTER TABLE order_items ALTER COLUMN offer_id SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN store_id SET NOT NULL;

-- The superseded development-only goods inventory tables must not remain authoritative.
DROP TABLE IF EXISTS goods_inventory_reservation;
DROP TABLE IF EXISTS goods_inventory;
