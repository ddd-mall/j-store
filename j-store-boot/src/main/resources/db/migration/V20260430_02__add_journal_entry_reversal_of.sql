ALTER TABLE accounting_journal_entry
    ADD COLUMN IF NOT EXISTS reversal_of BIGINT NULL;
