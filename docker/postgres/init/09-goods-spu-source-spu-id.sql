-- Migration: SPU 表新增 source_spu_id 列，支持 Copy-on-Write 草稿模型

ALTER TABLE spu ADD COLUMN IF NOT EXISTS source_spu_id BIGINT;

COMMENT ON COLUMN spu.source_spu_id IS '源商品SPU ID，null表示原始商品，非null表示草稿副本';

-- 部分索引：加速按 source_spu_id 查询草稿副本（仅索引非 null 行）
CREATE INDEX IF NOT EXISTS idx_spu_source_spu_id ON spu(source_spu_id) WHERE source_spu_id IS NOT NULL;
