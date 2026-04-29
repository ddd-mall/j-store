INSERT INTO accounting_ledger_account (
    id, code, name, account_type, balance_direction, subject_type, subject_id, status, created_at, updated_at
) VALUES
    (1002, '1002', '平台银行存款', 'ASSET', 'DEBIT', 'PLATFORM', 'PLATFORM', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1010, '1010', '支付渠道清算', 'ASSET', 'DEBIT', 'CHANNEL', 'DEFAULT', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2101, '2101', '商户待结算款', 'LIABILITY', 'CREDIT', 'MERCHANT', 'DEFAULT', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3001, '3001', '平台佣金收入', 'REVENUE', 'CREDIT', 'PLATFORM', 'PLATFORM', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO accounting_period (
    id, period_code, start_date, end_date, status, closed_at, closed_by
) VALUES (
    202604, '202604', DATE '2026-04-01', DATE '2026-04-30', 'OPEN', NULL, NULL
)
ON CONFLICT (id) DO NOTHING;
