-- =============================================
-- J-Store 数据库初始化脚本
-- =============================================

-- 创建订单数据库
DROP DATABASE IF EXISTS j_store_order;
CREATE DATABASE j_store_order
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'zh_CN.UTF-8'
    LC_CTYPE = 'zh_CN.UTF-8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

COMMENT ON DATABASE j_store_order IS '订单模块数据库';

-- 创建商品数据库
DROP DATABASE IF EXISTS j_store_goods;
CREATE DATABASE j_store_goods
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'zh_CN.UTF-8'
    LC_CTYPE = 'zh_CN.UTF-8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

COMMENT ON DATABASE j_store_goods IS '商品模块数据库';

-- =============================================
-- 说明：
-- 1. 表结构会在应用启动时由Hibernate自动创建
-- 2. 如果需要手动创建表，请参考下面的DDL
-- =============================================

-- =============================================
-- 订单数据库表结构（可选，仅供参考）
-- =============================================
-- \c j_store_order;

-- CREATE TABLE IF NOT EXISTS sale_order (
--     order_id BIGINT PRIMARY KEY,
--     uid BIGINT NOT NULL,
--     phone_number VARCHAR(20),
--     user_name VARCHAR(100),
--     address_info TEXT,
--     positive_status VARCHAR(50),
--     amount DECIMAL(19, 2),
--     actual_pay DECIMAL(19, 2),
--     create_time TIMESTAMP,
--     update_time TIMESTAMP,
--     CONSTRAINT uk UNIQUE (order_id)
-- );
--
-- CREATE INDEX idx_uid_create_time_update_time ON sale_order(uid, create_time, update_time);
--
-- CREATE TABLE IF NOT EXISTS order_item (
--     id BIGSERIAL PRIMARY KEY,
--     order_id BIGINT NOT NULL,
--     commodity_code BIGINT,
--     quantity INT,
--     price DECIMAL(19, 2),
--     CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES sale_order(order_id)
-- );

-- =============================================
-- 商品数据库表结构（可选，仅供参考）
-- =============================================
-- \c j_store_goods;

-- CREATE TABLE IF NOT EXISTS inventory (
--     commodity_code BIGINT PRIMARY KEY,
--     available_quantity DECIMAL(19, 2) NOT NULL DEFAULT 0,
--     reserved_quantity DECIMAL(19, 2) NOT NULL DEFAULT 0,
--     version BIGINT NOT NULL DEFAULT 0,
--     create_time TIMESTAMP,
--     update_time TIMESTAMP,
--     CONSTRAINT uk_commodity_code UNIQUE (commodity_code)
-- );
--
-- CREATE INDEX idx_commodity_code ON inventory(commodity_code);
--
-- CREATE TABLE IF NOT EXISTS spu (
--     spu_id BIGINT PRIMARY KEY,
--     spu_name VARCHAR(200) NOT NULL,
--     status VARCHAR(50) NOT NULL,
--     description VARCHAR(1000),
--     category VARCHAR(100),
--     brand VARCHAR(100),
--     create_time TIMESTAMP,
--     update_time TIMESTAMP,
--     CONSTRAINT uk_spu_id UNIQUE (spu_id)
-- );
--
-- CREATE INDEX idx_spu_name_status ON spu(spu_name, status);
-- CREATE INDEX idx_status ON spu(status);
--
-- CREATE TABLE IF NOT EXISTS sku (
--     sku_id BIGINT PRIMARY KEY,
--     spu_id BIGINT NOT NULL,
--     commodity_code BIGINT NOT NULL,
--     attributes TEXT,
--     create_time TIMESTAMP,
--     update_time TIMESTAMP,
--     CONSTRAINT uk_sku_id UNIQUE (sku_id),
--     CONSTRAINT fk_spu FOREIGN KEY (spu_id) REFERENCES spu(spu_id)
-- );
--
-- CREATE INDEX idx_spu_id ON sku(spu_id);
-- CREATE INDEX idx_commodity_code ON sku(commodity_code);

