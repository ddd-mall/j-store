-- 用户账号表
CREATE TABLE IF NOT EXISTS user_accounts (
    id              BIGSERIAL       PRIMARY KEY,
    phone_number    VARCHAR(11)     NOT NULL,
    nickname        VARCHAR(20)     NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    create_time     TIMESTAMP       NOT NULL,
    update_time     TIMESTAMP       NOT NULL,

    CONSTRAINT uk_user_accounts_phone_number UNIQUE (phone_number)
);

-- 手机号查询索引（UNIQUE 约束已隐式创建唯一索引，此处显式命名便于维护）
-- PostgreSQL UNIQUE 约束自动创建索引，无需额外创建
