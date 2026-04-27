-- Migration: Replace province/city/county columns with jsonb shipping_address
-- Supports arbitrary-depth i18n address hierarchies for any country

-- Step 1: Add the new jsonb column
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_address jsonb;

-- Step 2: Migrate existing data — reconstruct JSON from province/city/county columns
-- Uses Chinese address structure (depth 1/2/3) with zh-CN locale for backward compatibility
UPDATE orders
SET shipping_address = jsonb_build_object(
    'countryCode', country_code,
    'components', (
        SELECT jsonb_agg(comp ORDER BY (comp->>'depth')::int)
        FROM (
            SELECT jsonb_build_object(
                'code', CASE
                    WHEN lvl = 1 THEN left(district_code, 2) || repeat('0', length(district_code) - 2)
                    WHEN lvl = 2 THEN left(district_code, 4) || repeat('0', length(district_code) - 4)
                    WHEN lvl = 3 THEN district_code
                END,
                'level', jsonb_build_object('depth', lvl, 'name', CASE lvl WHEN 1 THEN '省' WHEN 2 THEN '市' WHEN 3 THEN '区/县' END),
                'names', jsonb_build_object('zh-CN', CASE lvl WHEN 1 THEN province WHEN 2 THEN city WHEN 3 THEN county END),
                'defaultLocale', 'zh-CN'
            ) AS comp
            FROM unnest(ARRAY[1, 2, 3]) AS lvl
            WHERE CASE lvl WHEN 1 THEN province WHEN 2 THEN city WHEN 3 THEN county END IS NOT NULL
              AND CASE lvl WHEN 1 THEN province WHEN 2 THEN city WHEN 3 THEN county END <> ''
        ) sub
    )
)
WHERE shipping_address IS NULL;

-- Step 3: Set NOT NULL constraint after migration
ALTER TABLE orders ALTER COLUMN shipping_address SET NOT NULL;

-- Step 4: Drop the old denormalized columns
ALTER TABLE orders DROP COLUMN IF EXISTS province;
ALTER TABLE orders DROP COLUMN IF EXISTS city;
ALTER TABLE orders DROP COLUMN IF EXISTS county;

-- Step 5: Create GIN index on jsonb for efficient querying
CREATE INDEX IF NOT EXISTS idx_orders_shipping_address ON orders USING gin (shipping_address);
