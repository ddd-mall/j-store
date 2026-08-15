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
    trade_id BIGINT NOT NULL,
    order_plan_id BIGINT NOT NULL,
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
    CONSTRAINT uk_sale_authorization_plan_offer UNIQUE(order_plan_id, offer_id),
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
    trade_id BIGINT NOT NULL,
    order_plan_id BIGINT NOT NULL,
    sale_authorization_id VARCHAR(128) NOT NULL,
    sku_id BIGINT NOT NULL,
    fulfillment_node_id VARCHAR(128) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    persistence_version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_inventory_reservation_plan
    ON inventory_stock_reservations(order_plan_id);
CREATE INDEX IF NOT EXISTS idx_inventory_reservation_expiry
    ON inventory_stock_reservations(status, expires_at);

-- The legacy goods Inventory stored available quantity after reservations had
-- already been subtracted. Reconstruct physical stock without rounding or
-- silently accepting values that the integer ATP model cannot represent.
DO $$
DECLARE
    missing_columns TEXT;
    invalid_inventory BOOLEAN;
BEGIN
    IF to_regclass('develop.inventory') IS NOT NULL THEN
        SELECT string_agg(expected.column_name, ', ' ORDER BY expected.column_name)
        INTO missing_columns
        FROM (VALUES
            ('commodity_code'),
            ('available_quantity'),
            ('reserved_quantity'),
            ('version')
        ) AS expected(column_name)
        WHERE NOT EXISTS (
            SELECT 1
            FROM information_schema.columns actual
            WHERE actual.table_schema = 'develop'
              AND actual.table_name = 'inventory'
              AND actual.column_name = expected.column_name
        );

        IF missing_columns IS NOT NULL THEN
            RAISE EXCEPTION
                'Cannot migrate develop.inventory: missing required columns: %',
                missing_columns;
        END IF;

        EXECUTE $validation$
            SELECT EXISTS (
                SELECT 1
                FROM develop.inventory
                WHERE commodity_code IS NULL
                   OR commodity_code <= 0
                   OR available_quantity IS NULL
                   OR reserved_quantity IS NULL
                   OR version IS NULL
                   OR available_quantity < 0
                   OR reserved_quantity < 0
                   OR version < 0
                   OR available_quantity <> trunc(available_quantity)
                   OR reserved_quantity <> trunc(reserved_quantity)
                   OR available_quantity + reserved_quantity > 2147483647
            )
        $validation$ INTO invalid_inventory;

        IF invalid_inventory THEN
            RAISE EXCEPTION
                'Cannot migrate develop.inventory: quantities must be nonnegative integral INTEGER values and version must be nonnegative';
        END IF;

        EXECUTE $migration$
            INSERT INTO inventory_stock_positions(
                id, sku_id, fulfillment_node_id, on_hand, reserved,
                safety_stock, isolated_quantity, source_version, persistence_version
            )
            SELECT
                commodity_code::TEXT || '@DEFAULT',
                commodity_code,
                'DEFAULT',
                (available_quantity + reserved_quantity)::INTEGER,
                reserved_quantity::INTEGER,
                0,
                0,
                0,
                version
            FROM develop.inventory
        $migration$;

        DROP TABLE develop.inventory;
    END IF;
END
$$;

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

-- Their historical schema is undocumented. Empty development tables may be
-- removed, but nonempty tables require an explicit, reviewed conversion.
DO $$
DECLARE
    legacy_table TEXT;
    legacy_rows BIGINT;
BEGIN
    FOREACH legacy_table IN ARRAY ARRAY['goods_inventory_reservation', 'goods_inventory']
    LOOP
        IF to_regclass('develop.' || legacy_table) IS NOT NULL THEN
            EXECUTE format('SELECT count(*) FROM develop.%I', legacy_table)
                INTO legacy_rows;
            IF legacy_rows > 0 THEN
                RAISE EXCEPTION
                    'Refusing to drop nonempty legacy table develop.% without a verified conversion',
                    legacy_table;
            END IF;
            EXECUTE format('DROP TABLE develop.%I', legacy_table);
        END IF;
    END LOOP;
END
$$;
