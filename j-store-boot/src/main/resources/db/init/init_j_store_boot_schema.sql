-- Full schema initialization for j-store-boot.
-- Target database: PostgreSQL.
-- Target schema: develop, matching application-local.properties currentSchema=develop.
-- This script is intended for initializing an empty database. It is idempotent
-- for table/index creation, but it does not migrate old incompatible schemas.

CREATE SCHEMA IF NOT EXISTS develop;
SET search_path TO develop, public;

-- ============================================================
-- User account
-- ============================================================
CREATE TABLE IF NOT EXISTS user_accounts (
    id              BIGSERIAL       PRIMARY KEY,
    phone_number    VARCHAR(11)     NOT NULL,
    nickname        VARCHAR(20)     NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    create_time     TIMESTAMP       NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_accounts_phone_number UNIQUE (phone_number)
);

-- ============================================================
-- Goods
-- ============================================================
CREATE TABLE IF NOT EXISTS brand (
    id                  BIGINT PRIMARY KEY,
    merchant_id         BIGINT NOT NULL,
    name                JSONB NOT NULL,
    normalized_name     VARCHAR(256) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    persistence_version BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_brand_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT uk_brand_merchant_name UNIQUE (merchant_id, normalized_name)
);

CREATE TABLE IF NOT EXISTS product_type (
    id                  BIGINT PRIMARY KEY,
    merchant_id         BIGINT NOT NULL,
    name                JSONB NOT NULL,
    definitions         JSONB NOT NULL DEFAULT '[]',
    persistence_version BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS spu (
    id              BIGINT        PRIMARY KEY,
    name            VARCHAR(256)  NOT NULL,
    description     VARCHAR(2000) DEFAULT '',
    product_type_id BIGINT REFERENCES product_type(id),
    product_attributes JSONB NOT NULL DEFAULT '[]',
    brand_id        BIGINT REFERENCES brand(id),
    category_ids    JSONB NOT NULL DEFAULT '[]',
    localized_names JSONB,
    localized_descriptions JSONB,
    status          VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    version         BIGINT        NOT NULL DEFAULT 1,
    source_spu_id   BIGINT,
    persistence_version BIGINT NOT NULL DEFAULT 0,
    create_time     TIMESTAMP     NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_spu_source_spu_id
    ON spu (source_spu_id)
    WHERE source_spu_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_spu_one_draft_per_source
    ON spu (source_spu_id)
    WHERE source_spu_id IS NOT NULL AND status = 'DRAFT';

CREATE TABLE IF NOT EXISTS sku (
    id              BIGINT        PRIMARY KEY,
    spu_id          BIGINT        NOT NULL REFERENCES spu(id),
    sku_name        VARCHAR(256)  NOT NULL,
    attributes      JSONB         NOT NULL DEFAULT '[]',
    price           NUMERIC(19,0) NOT NULL DEFAULT 0,
    merchant_code   VARCHAR(128),
    barcode         VARCHAR(64),
    source_sku_id   BIGINT
);

CREATE INDEX IF NOT EXISTS idx_sku_spu_id ON sku(spu_id);

CREATE TABLE IF NOT EXISTS spu_snapshot (
    id                BIGINT        PRIMARY KEY,
    spu_id            BIGINT        NOT NULL,
    snapshot_version  BIGINT        NOT NULL,
    spu_name          VARCHAR(256)  NOT NULL,
    description       VARCHAR(2000) DEFAULT '',
    sku_snapshots     JSONB         NOT NULL DEFAULT '[]',
    main_images       JSONB         NOT NULL DEFAULT '[]',
    detail_html       TEXT          NOT NULL DEFAULT '',
    product_type_id   BIGINT,
    product_attributes JSONB        NOT NULL DEFAULT '[]',
    brand_id          BIGINT,
    brand_name        JSONB,
    category_ids      JSONB         NOT NULL DEFAULT '[]',
    localized_names   JSONB,
    localized_descriptions JSONB,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_spu_snapshot_spu_version UNIQUE (spu_id, snapshot_version)
);

CREATE INDEX IF NOT EXISTS idx_spu_snapshot_spu_id ON spu_snapshot(spu_id);

CREATE TABLE IF NOT EXISTS goods_style (
    id              BIGINT      PRIMARY KEY,
    spu_id          BIGINT      NOT NULL,
    main_images     JSONB       NOT NULL DEFAULT '[]',
    detail_html     TEXT        NOT NULL DEFAULT '',
    sku_images      JSONB       NOT NULL DEFAULT '{}',
    create_time     TIMESTAMP   NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_goods_style_spu_id ON goods_style(spu_id);

-- ============================================================
-- Orders
-- ============================================================
CREATE TABLE IF NOT EXISTS orders (
    id                BIGSERIAL      PRIMARY KEY,
    buyer_uid         BIGINT         NOT NULL,
    buyer_phone       VARCHAR(20),
    buyer_name        VARCHAR(64),
    recipient_info    JSONB,
    status            VARCHAR(32)    NOT NULL DEFAULT 'PENDING_STOCK',
    previous_status   VARCHAR(32),
    total_amount      NUMERIC(19,0)  NOT NULL DEFAULT 0,
    actual_pay        NUMERIC(19,0)  NOT NULL DEFAULT 0,
    create_time       TIMESTAMP      NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_buyer_uid ON orders(buyer_uid);
CREATE INDEX IF NOT EXISTS idx_orders_status_create_time ON orders(status, create_time);
CREATE INDEX IF NOT EXISTS idx_orders_recipient_info ON orders USING gin (recipient_info);

CREATE TABLE IF NOT EXISTS order_items (
    id                    BIGSERIAL      PRIMARY KEY,
    order_id              BIGINT         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sku_id                BIGINT         NOT NULL,
    spu_id                BIGINT         NOT NULL,
    goods_name            VARCHAR(256)   NOT NULL,
    sku_description       VARCHAR(512)   NOT NULL,
    quantity              INTEGER        NOT NULL,
    unit_price            NUMERIC(19,0)  NOT NULL DEFAULT 0,
    snapshot_version      BIGINT         NOT NULL DEFAULT 0,
    status                VARCHAR(32)    NOT NULL DEFAULT 'NONE',
    previous_item_status  VARCHAR(32)
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_sku_id ON order_items(sku_id);
CREATE INDEX IF NOT EXISTS idx_order_items_spu_id ON order_items(spu_id);

-- ============================================================
-- Transactional outbox and domain event consumption
-- ============================================================
CREATE TABLE IF NOT EXISTS outbox_entry (
    id                VARCHAR(36)   PRIMARY KEY,
    event_type        VARCHAR(512)  NOT NULL,
    event_id          VARCHAR(64)   NOT NULL,
    event_class_name  VARCHAR(512)  NOT NULL,
    event_version     INTEGER       NOT NULL DEFAULT 1,
    message_kind      VARCHAR(32)   NOT NULL DEFAULT 'DOMAIN_EVENT',
    delivery_target   VARCHAR(32)   NOT NULL DEFAULT 'LOCAL_DOMAIN',
    destination       VARCHAR(512)  NOT NULL,
    partition_key     VARCHAR(256)  NOT NULL,
    correlation_id    VARCHAR(128)  NOT NULL,
    causation_id      VARCHAR(128),
    tenant_id         VARCHAR(128),
    payload           TEXT          NOT NULL,
    aggregate_type    VARCHAR(256)  NOT NULL,
    aggregate_id      VARCHAR(128)  NOT NULL,
    occurred_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    retry_count       INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    locked_by         VARCHAR(128),
    locked_at         TIMESTAMPTZ,
    locked_until      TIMESTAMPTZ,
    last_error        TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbox_entry_status_created
    ON outbox_entry (status, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_outbox_entry_claim
    ON outbox_entry (status, next_attempt_at, created_at ASC)
    WHERE status IN ('PENDING', 'FAILED', 'IN_PROGRESS');

CREATE INDEX IF NOT EXISTS idx_outbox_entry_lock_expired
    ON outbox_entry (locked_until ASC)
    WHERE status = 'IN_PROGRESS';

CREATE INDEX IF NOT EXISTS idx_outbox_entry_cleanup
    ON outbox_entry (status, created_at)
    WHERE status = 'PUBLISHED';

CREATE INDEX IF NOT EXISTS idx_outbox_entry_aggregate
    ON outbox_entry (aggregate_type, aggregate_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_outbox_entry_event_id
    ON outbox_entry (event_id);

CREATE INDEX IF NOT EXISTS idx_outbox_entry_target_ready
    ON outbox_entry (delivery_target, status, next_attempt_at, created_at);

CREATE TABLE IF NOT EXISTS domain_event_consumption (
    listener_id    VARCHAR(512) NOT NULL,
    event_id       VARCHAR(64)  NOT NULL,
    event_name     VARCHAR(256) NOT NULL,
    event_version  INTEGER      NOT NULL DEFAULT 1,
    consumed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (listener_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_domain_event_consumption_event
    ON domain_event_consumption (event_name, event_version, consumed_at);

CREATE INDEX IF NOT EXISTS idx_domain_event_consumption_retention
    ON domain_event_consumption (consumed_at, listener_id, event_id);

-- ============================================================
-- Accounting
-- ============================================================
CREATE TABLE IF NOT EXISTS accounting_ledger_account (
    id                 BIGINT       PRIMARY KEY,
    code               VARCHAR(64)  NOT NULL,
    name               VARCHAR(128) NOT NULL,
    account_type       VARCHAR(32)  NOT NULL,
    balance_direction  VARCHAR(16)  NOT NULL,
    subject_type       VARCHAR(32)  NOT NULL,
    subject_id         VARCHAR(128) NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_accounting_ledger_account_code_subject UNIQUE (code, subject_type, subject_id)
);

CREATE TABLE IF NOT EXISTS accounting_journal_entry (
    id                 BIGINT       PRIMARY KEY,
    entry_no           VARCHAR(64)  NOT NULL UNIQUE,
    entry_type         VARCHAR(64)  NOT NULL,
    source_type        VARCHAR(64)  NOT NULL,
    source_id          VARCHAR(128) NOT NULL,
    source_event_type  VARCHAR(128) NOT NULL,
    accounting_date    DATE         NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    reversed_by        BIGINT,
    reversal_of        BIGINT,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    posted_at          TIMESTAMP,
    CONSTRAINT uk_accounting_journal_source UNIQUE (source_type, source_id, source_event_type)
);

CREATE TABLE IF NOT EXISTS accounting_journal_line (
    id          BIGINT       PRIMARY KEY,
    entry_id    BIGINT       NOT NULL,
    account_id  BIGINT       NOT NULL,
    side        VARCHAR(16)  NOT NULL,
    amount_fen  BIGINT       NOT NULL,
    memo        VARCHAR(256) NOT NULL,
    CONSTRAINT fk_accounting_line_entry FOREIGN KEY (entry_id) REFERENCES accounting_journal_entry(id),
    CONSTRAINT fk_accounting_line_account FOREIGN KEY (account_id) REFERENCES accounting_ledger_account(id),
    CONSTRAINT ck_accounting_line_amount_positive CHECK (amount_fen > 0)
);

CREATE TABLE IF NOT EXISTS accounting_period (
    id           BIGINT      PRIMARY KEY,
    period_code  VARCHAR(16) NOT NULL UNIQUE,
    start_date   DATE        NOT NULL,
    end_date     DATE        NOT NULL,
    status       VARCHAR(32) NOT NULL,
    closed_at    TIMESTAMP,
    closed_by    VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS accounting_settlement_statement (
    id                  BIGINT       PRIMARY KEY,
    statement_no        VARCHAR(64)  NOT NULL UNIQUE,
    merchant_id         VARCHAR(128) NOT NULL,
    period_start        DATE         NOT NULL,
    period_end          DATE         NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    payable_amount_fen  BIGINT       NOT NULL,
    confirmed_at        TIMESTAMP,
    paid_at             TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_accounting_settlement_merchant_period UNIQUE (merchant_id, period_start, period_end)
);

CREATE TABLE IF NOT EXISTS accounting_settlement_line (
    id                     BIGINT       PRIMARY KEY,
    statement_id           BIGINT       NOT NULL,
    order_id               VARCHAR(128) NOT NULL,
    gross_amount_fen       BIGINT       NOT NULL,
    refund_amount_fen      BIGINT       NOT NULL,
    commission_amount_fen  BIGINT       NOT NULL,
    net_amount_fen         BIGINT       NOT NULL,
    CONSTRAINT fk_accounting_settlement_line_statement
        FOREIGN KEY (statement_id) REFERENCES accounting_settlement_statement(id)
);
