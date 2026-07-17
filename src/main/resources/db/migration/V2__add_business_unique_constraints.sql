ALTER TABLE ticket_allocation_line
    ADD CONSTRAINT uk_allocation_line_batch UNIQUE (allocation_id, batch_line_id);

ALTER TABLE daily_sales_line
    ADD CONSTRAINT uk_daily_sales_line_batch UNIQUE (daily_sales_id, batch_line_id);

ALTER TABLE ticket_return_line
    ADD COLUMN allocation_line_scope_id BIGINT
        GENERATED ALWAYS AS (IFNULL(allocation_line_id, 0)) STORED,
    ADD CONSTRAINT uk_return_line_scope
        UNIQUE (ticket_return_id, batch_line_id, allocation_line_scope_id);
