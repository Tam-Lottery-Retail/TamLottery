ALTER TABLE cash_transaction
    ADD COLUMN void_reason VARCHAR(500) NULL AFTER posted_at,
    ADD COLUMN voided_by BIGINT NULL AFTER void_reason,
    ADD COLUMN voided_at DATETIME(6) NULL AFTER voided_by;

ALTER TABLE daily_reconciliation
    ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER reviewed_at;
