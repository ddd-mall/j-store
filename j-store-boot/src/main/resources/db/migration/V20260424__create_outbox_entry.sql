-- Transactional Outbox: outbox_entry 表
CREATE TABLE IF NOT EXISTS outbox_entry (
    id              VARCHAR(36)     PRIMARY KEY,
    event_type      VARCHAR(512)    NOT NULL,
    payload         TEXT            NOT NULL,
    aggregate_type  VARCHAR(256)    NOT NULL,
    aggregate_id    VARCHAR(128)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    retry_count     INTEGER         NOT NULL DEFAULT 0
);

-- 轮询查询索引：按状态 + 创建时间排序
CREATE INDEX IF NOT EXISTS idx_outbox_entry_status_created
    ON outbox_entry (status, created_at ASC);

-- 清理查询索引：按状态 + 创建时间（部分索引，仅 PUBLISHED）
CREATE INDEX IF NOT EXISTS idx_outbox_entry_cleanup
    ON outbox_entry (status, created_at)
    WHERE status = 'PUBLISHED';

-- 聚合根维度查询索引（用于排查）
CREATE INDEX IF NOT EXISTS idx_outbox_entry_aggregate
    ON outbox_entry (aggregate_type, aggregate_id, created_at ASC);
