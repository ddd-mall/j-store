-- Migration: Add country_code column to orders table for i18n address support
-- Existing data defaults to 'CN' (China) for backward compatibility

ALTER TABLE orders ADD COLUMN IF NOT EXISTS country_code VARCHAR(2) NOT NULL DEFAULT 'CN';
