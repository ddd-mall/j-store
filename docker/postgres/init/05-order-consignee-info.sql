-- Migration: Merge scattered consignee columns into single consignee_info jsonb
-- Combines country_code, district_code, shipping_address (jsonb), detail_address
-- into a unified consignee_info jsonb column.
-- Historical orders get consigneeName='', consigneePhone=null, consigneeEmail=null.

-- Step 1: Add consignee_info jsonb column
ALTER TABLE orders ADD COLUMN IF NOT EXISTS consignee_info jsonb;

-- Step 2: Migrate existing data into consignee_info
-- shipping_address is already a jsonb column (from 03-order-address-jsonb.sql)
UPDATE orders
SET consignee_info = jsonb_build_object(
    'consigneeName', '',
    'consigneePhone', null,
    'consigneeEmail', null,
    'countryCode', country_code,
    'districtCode', district_code,
    'shippingAddress', shipping_address,
    'detailAddress', detail_address
)
WHERE consignee_info IS NULL;

-- Step 3: Set NOT NULL constraint
ALTER TABLE orders ALTER COLUMN consignee_info SET NOT NULL;

-- Step 4: Drop old columns
ALTER TABLE orders DROP COLUMN IF EXISTS country_code;
ALTER TABLE orders DROP COLUMN IF EXISTS district_code;
ALTER TABLE orders DROP COLUMN IF EXISTS shipping_address;
ALTER TABLE orders DROP COLUMN IF EXISTS detail_address;

-- Step 5: Create GIN index
CREATE INDEX IF NOT EXISTS idx_orders_consignee_info ON orders USING gin (consignee_info);
