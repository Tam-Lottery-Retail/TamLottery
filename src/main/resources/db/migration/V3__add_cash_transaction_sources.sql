ALTER TABLE ticket_allocation_line
    ADD COLUMN active_collected_amount BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_allocation_line_collected_amount CHECK (active_collected_amount >= 0);

CREATE TABLE cash_transaction_source (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    cash_transaction_id BIGINT NOT NULL,
    allocation_line_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cash_source_transaction_allocation UNIQUE (cash_transaction_id, allocation_line_id),
    CONSTRAINT fk_cash_source_transaction FOREIGN KEY (cash_transaction_id) REFERENCES cash_transaction (id),
    CONSTRAINT fk_cash_source_allocation_line FOREIGN KEY (allocation_line_id) REFERENCES ticket_allocation_line (id),
    CONSTRAINT chk_cash_source_amount CHECK (amount > 0),
    INDEX idx_cash_source_allocation_line (allocation_line_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
