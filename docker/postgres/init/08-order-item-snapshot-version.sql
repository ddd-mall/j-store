-- Migration: 订单行项新增商品快照版本号字段

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS snapshot_version BIGINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN order_items.snapshot_version IS '商品快照版本号，对应 spu_snapshot.snapshot_version';
