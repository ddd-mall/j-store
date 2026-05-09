-- Migration: Create goods module tables (SPU, SKU, SPU Snapshot)

-- ============================================================
-- SPU (Standard Product Unit) — 商品主表
-- ============================================================
CREATE TABLE IF NOT EXISTS spu (
    id              BIGINT       PRIMARY KEY,
    name            VARCHAR(256) NOT NULL,
    description     VARCHAR(2000) DEFAULT '',
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    version         BIGINT       NOT NULL DEFAULT 1,
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE spu IS '商品SPU主表';
COMMENT ON COLUMN spu.status IS '商品状态: DRAFT / OFF_SALE / ON_SALE';
COMMENT ON COLUMN spu.version IS '版本号，每次上架时递增，与快照版本对应';

-- ============================================================
-- SKU (Stock Keeping Unit) — 商品规格表
-- ============================================================
CREATE TABLE IF NOT EXISTS sku (
    id              BIGINT        PRIMARY KEY,
    spu_id          BIGINT        NOT NULL REFERENCES spu(id),
    sku_name        VARCHAR(256)  NOT NULL,
    attributes      JSONB         NOT NULL DEFAULT '[]',
    price           NUMERIC(19,0) NOT NULL DEFAULT 0
);

COMMENT ON TABLE sku IS '商品SKU规格表';
COMMENT ON COLUMN sku.attributes IS '销售属性JSON，如 [{"key":"颜色","value":"红色"}]';
COMMENT ON COLUMN sku.price IS '单价（分）';

CREATE INDEX IF NOT EXISTS idx_sku_spu_id ON sku(spu_id);

-- ============================================================
-- SPU Snapshot — 商品快照表（不可变，供订单历史查询）
-- ============================================================
CREATE TABLE IF NOT EXISTS spu_snapshot (
    id                BIGINT        PRIMARY KEY,
    spu_id            BIGINT        NOT NULL,
    snapshot_version  BIGINT        NOT NULL,
    spu_name          VARCHAR(256)  NOT NULL,
    description       VARCHAR(2000) DEFAULT '',
    sku_snapshots     JSONB         NOT NULL DEFAULT '[]',
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE (spu_id, snapshot_version)
);

COMMENT ON TABLE spu_snapshot IS '商品快照表，记录上架时刻的完整商品信息';
COMMENT ON COLUMN spu_snapshot.sku_snapshots IS 'SKU快照JSON数组，包含skuId/skuName/attributes/price';

CREATE INDEX IF NOT EXISTS idx_spu_snapshot_spu_id ON spu_snapshot(spu_id);
