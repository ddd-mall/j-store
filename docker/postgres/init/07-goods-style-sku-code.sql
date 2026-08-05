-- Migration: GoodsStyle 表与 SKU 编码字段扩展

-- ============================================================
-- GoodsStyle — 商品展示样式表（独立于 SPU 聚合）
-- ============================================================
CREATE TABLE IF NOT EXISTS goods_style (
    id              BIGINT       PRIMARY KEY,
    spu_id          BIGINT       NOT NULL,
    main_images     JSONB        NOT NULL DEFAULT '[]',
    detail_html     TEXT         NOT NULL DEFAULT '',
    sku_images      JSONB        NOT NULL DEFAULT '{}',
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE goods_style IS '商品展示样式表';
COMMENT ON COLUMN goods_style.main_images IS 'SPU主图列表，有序的ImageKey JSON数组';
COMMENT ON COLUMN goods_style.detail_html IS 'SPU详情页富文本HTML内容';
COMMENT ON COLUMN goods_style.sku_images IS 'SKU图片映射，JSON对象 {skuId: [imageKey]}';

-- 唯一索引：一个 SPU 只对应一个 GoodsStyle
CREATE UNIQUE INDEX IF NOT EXISTS idx_goods_style_spu_id ON goods_style(spu_id);

-- ============================================================
-- SKU 编码字段扩展 — 新增商家内部货号与标准条形码
-- ============================================================
ALTER TABLE sku ADD COLUMN IF NOT EXISTS merchant_code VARCHAR(128);
ALTER TABLE sku ADD COLUMN IF NOT EXISTS barcode VARCHAR(64);

COMMENT ON COLUMN sku.merchant_code IS '商家内部货号';
COMMENT ON COLUMN sku.barcode IS '标准条形码（EAN/UPC）';
