-- Transactional Outbox: outbox_entry 表
CREATE TABLE IF NOT EXISTS outbox_entry (
    id              VARCHAR(36)     PRIMARY KEY,
    event_type      VARCHAR(512)    NOT NULL,
    event_id        VARCHAR(64)     NOT NULL,
    event_class_name VARCHAR(512)   NOT NULL,
    event_version   INTEGER         NOT NULL DEFAULT 1,
    payload         TEXT            NOT NULL,
    aggregate_type  VARCHAR(256)    NOT NULL,
    aggregate_id    VARCHAR(128)    NOT NULL,
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    locked_by       VARCHAR(128),
    locked_at       TIMESTAMPTZ,
    locked_until    TIMESTAMPTZ,
    last_error      TEXT
);

-- 轮询查询索引：按状态 + 创建时间排序
CREATE INDEX IF NOT EXISTS idx_outbox_entry_status_created
    ON outbox_entry (status, created_at ASC);

-- 领取查询索引：按状态 + 下一次重试时间 + 创建时间排序
CREATE INDEX IF NOT EXISTS idx_outbox_entry_claim
    ON outbox_entry (status, next_attempt_at, created_at ASC)
    WHERE status IN ('PENDING', 'FAILED', 'IN_PROGRESS');

-- 锁超时恢复索引：快速找回 relay 崩溃后过期的处理中消息
CREATE INDEX IF NOT EXISTS idx_outbox_entry_lock_expired
    ON outbox_entry (locked_until ASC)
    WHERE status = 'IN_PROGRESS';

-- 清理查询索引：按状态 + 创建时间（部分索引，仅 PUBLISHED）
CREATE INDEX IF NOT EXISTS idx_outbox_entry_cleanup
    ON outbox_entry (status, created_at)
    WHERE status = 'PUBLISHED';

-- 聚合根维度查询索引（用于排查）
CREATE INDEX IF NOT EXISTS idx_outbox_entry_aggregate
    ON outbox_entry (aggregate_type, aggregate_id, created_at ASC);

-- 事件幂等键索引（用于排查和消费者幂等协作）
CREATE INDEX IF NOT EXISTS idx_outbox_entry_event_id
    ON outbox_entry (event_id);

-- 领域事件监听器幂等消费记录
CREATE TABLE IF NOT EXISTS domain_event_consumption (
    listener_id   VARCHAR(512) NOT NULL,
    event_id      VARCHAR(64)  NOT NULL,
    event_name    VARCHAR(256) NOT NULL,
    event_version INTEGER      NOT NULL DEFAULT 1,
    consumed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (listener_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_domain_event_consumption_event
    ON domain_event_consumption (event_name, event_version, consumed_at);
