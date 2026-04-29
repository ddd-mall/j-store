CREATE TABLE IF NOT EXISTS accounting_ledger_account (
    id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    balance_direction VARCHAR(16) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_accounting_ledger_account_code_subject UNIQUE (code, subject_type, subject_id)
);

CREATE TABLE IF NOT EXISTS accounting_journal_entry (
    id BIGINT PRIMARY KEY,
    entry_no VARCHAR(64) NOT NULL UNIQUE,
    entry_type VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    source_event_type VARCHAR(128) NOT NULL,
    accounting_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    reversed_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL,
    posted_at TIMESTAMP NULL,
    CONSTRAINT uk_accounting_journal_source UNIQUE (source_type, source_id, source_event_type)
);

CREATE TABLE IF NOT EXISTS accounting_journal_line (
    id BIGINT PRIMARY KEY,
    entry_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    side VARCHAR(16) NOT NULL,
    amount_fen BIGINT NOT NULL,
    memo VARCHAR(256) NOT NULL,
    CONSTRAINT fk_accounting_line_entry FOREIGN KEY (entry_id) REFERENCES accounting_journal_entry(id),
    CONSTRAINT fk_accounting_line_account FOREIGN KEY (account_id) REFERENCES accounting_ledger_account(id),
    CONSTRAINT ck_accounting_line_amount_positive CHECK (amount_fen > 0)
);

CREATE TABLE IF NOT EXISTS accounting_period (
    id BIGINT PRIMARY KEY,
    period_code VARCHAR(16) NOT NULL UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    closed_at TIMESTAMP NULL,
    closed_by VARCHAR(128) NULL
);

CREATE TABLE IF NOT EXISTS accounting_settlement_statement (
    id BIGINT PRIMARY KEY,
    statement_no VARCHAR(64) NOT NULL UNIQUE,
    merchant_id VARCHAR(128) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    payable_amount_fen BIGINT NOT NULL,
    confirmed_at TIMESTAMP NULL,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_accounting_settlement_merchant_period UNIQUE (merchant_id, period_start, period_end)
);

CREATE TABLE IF NOT EXISTS accounting_settlement_line (
    id BIGINT PRIMARY KEY,
    statement_id BIGINT NOT NULL,
    order_id VARCHAR(128) NOT NULL,
    gross_amount_fen BIGINT NOT NULL,
    refund_amount_fen BIGINT NOT NULL,
    commission_amount_fen BIGINT NOT NULL,
    net_amount_fen BIGINT NOT NULL,
    CONSTRAINT fk_accounting_settlement_line_statement FOREIGN KEY (statement_id) REFERENCES accounting_settlement_statement(id)
);
