CREATE TABLE store (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    timezone VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_store_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    username VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username),
    CONSTRAINT fk_app_user_store FOREIGN KEY (store_id) REFERENCES store (id),
    INDEX idx_app_user_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE seller (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    code VARCHAR(40) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    phone VARCHAR(30) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_seller_store_code UNIQUE (store_id, code),
    CONSTRAINT uk_seller_user UNIQUE (user_id),
    CONSTRAINT fk_seller_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_seller_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    INDEX idx_seller_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    replaced_by_hash VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    INDEX idx_refresh_token_user (user_id),
    INDEX idx_refresh_token_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE agency (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    contact_name VARCHAR(160) NULL,
    phone VARCHAR(30) NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_agency_store_code UNIQUE (store_id, code),
    CONSTRAINT fk_agency_store FOREIGN KEY (store_id) REFERENCES store (id),
    INDEX idx_agency_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lottery_draw (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    issuer_name VARCHAR(160) NOT NULL,
    province_code VARCHAR(30) NOT NULL,
    region VARCHAR(20) NOT NULL,
    draw_date DATE NOT NULL,
    return_cutoff_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_draw_store_province_date UNIQUE (store_id, province_code, draw_date),
    CONSTRAINT fk_draw_store FOREIGN KEY (store_id) REFERENCES store (id),
    INDEX idx_draw_store_date (store_id, draw_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lottery_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    agency_id BIGINT NOT NULL,
    receipt_code VARCHAR(60) NOT NULL,
    business_date DATE NOT NULL,
    received_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    note VARCHAR(500) NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_batch_store_receipt UNIQUE (store_id, receipt_code),
    CONSTRAINT fk_batch_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_batch_agency FOREIGN KEY (agency_id) REFERENCES agency (id),
    INDEX idx_batch_store_date_status (store_id, business_date, status),
    INDEX idx_batch_agency (agency_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lottery_batch_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    batch_id BIGINT NOT NULL,
    lottery_draw_id BIGINT NOT NULL,
    quantity_received INT NOT NULL,
    unit_cost BIGINT NOT NULL,
    unit_sale_price BIGINT NOT NULL,
    serial_from VARCHAR(40) NULL,
    serial_to VARCHAR(40) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_batch_line_batch FOREIGN KEY (batch_id) REFERENCES lottery_batch (id),
    CONSTRAINT fk_batch_line_draw FOREIGN KEY (lottery_draw_id) REFERENCES lottery_draw (id),
    CONSTRAINT chk_batch_line_quantity CHECK (quantity_received > 0),
    CONSTRAINT chk_batch_line_prices CHECK (unit_cost >= 0 AND unit_sale_price > 0),
    INDEX idx_batch_line_batch (batch_id),
    INDEX idx_batch_line_draw (lottery_draw_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_allocation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    issued_at DATETIME(6) NULL,
    issued_by BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_allocation_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_allocation_seller FOREIGN KEY (seller_id) REFERENCES seller (id),
    INDEX idx_allocation_store_date_status (store_id, business_date, status),
    INDEX idx_allocation_seller_date (seller_id, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_allocation_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    allocation_id BIGINT NOT NULL,
    batch_line_id BIGINT NOT NULL,
    quantity_allocated INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_allocation_line_allocation FOREIGN KEY (allocation_id) REFERENCES ticket_allocation (id),
    CONSTRAINT fk_allocation_line_batch_line FOREIGN KEY (batch_line_id) REFERENCES lottery_batch_line (id),
    CONSTRAINT chk_allocation_line_quantity CHECK (quantity_allocated > 0),
    INDEX idx_allocation_line_allocation (allocation_id),
    INDEX idx_allocation_line_batch (batch_line_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_return (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    return_type VARCHAR(30) NOT NULL,
    seller_id BIGINT NULL,
    agency_id BIGINT NULL,
    business_date DATE NOT NULL,
    returned_at DATETIME(6) NULL,
    confirmed_by BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_return_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_return_seller FOREIGN KEY (seller_id) REFERENCES seller (id),
    CONSTRAINT fk_return_agency FOREIGN KEY (agency_id) REFERENCES agency (id),
    CONSTRAINT chk_return_parties CHECK (
        (return_type = 'SELLER_TO_STORE' AND seller_id IS NOT NULL AND agency_id IS NULL)
        OR (return_type = 'STORE_TO_AGENCY' AND seller_id IS NULL AND agency_id IS NOT NULL)
    ),
    INDEX idx_return_store_date_status (store_id, business_date, status),
    INDEX idx_return_seller (seller_id),
    INDEX idx_return_agency (agency_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_return_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    ticket_return_id BIGINT NOT NULL,
    batch_line_id BIGINT NOT NULL,
    allocation_line_id BIGINT NULL,
    quantity INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_return_line_return FOREIGN KEY (ticket_return_id) REFERENCES ticket_return (id),
    CONSTRAINT fk_return_line_batch_line FOREIGN KEY (batch_line_id) REFERENCES lottery_batch_line (id),
    CONSTRAINT fk_return_line_allocation_line FOREIGN KEY (allocation_line_id) REFERENCES ticket_allocation_line (id),
    CONSTRAINT chk_return_line_quantity CHECK (quantity > 0),
    INDEX idx_return_line_return (ticket_return_id),
    INDEX idx_return_line_batch (batch_line_id),
    INDEX idx_return_line_allocation (allocation_line_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory_adjustment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    batch_line_id BIGINT NOT NULL,
    holder_type VARCHAR(20) NOT NULL,
    seller_id BIGINT NULL,
    allocation_line_id BIGINT NULL,
    adjustment_type VARCHAR(20) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL,
    approved_by BIGINT NULL,
    approved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_adjustment_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_adjustment_batch_line FOREIGN KEY (batch_line_id) REFERENCES lottery_batch_line (id),
    CONSTRAINT fk_adjustment_seller FOREIGN KEY (seller_id) REFERENCES seller (id),
    CONSTRAINT fk_adjustment_allocation_line FOREIGN KEY (allocation_line_id) REFERENCES ticket_allocation_line (id),
    CONSTRAINT chk_adjustment_holder CHECK (
        (holder_type = 'STORE' AND seller_id IS NULL AND allocation_line_id IS NULL)
        OR (holder_type = 'SELLER' AND seller_id IS NOT NULL AND allocation_line_id IS NOT NULL)
    ),
    CONSTRAINT chk_adjustment_quantity CHECK (quantity > 0),
    INDEX idx_adjustment_store_status (store_id, status),
    INDEX idx_adjustment_batch_line (batch_line_id),
    INDEX idx_adjustment_allocation_line (allocation_line_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE daily_sales (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    scope VARCHAR(20) NOT NULL,
    scope_key VARCHAR(80) NOT NULL,
    seller_id BIGINT NULL,
    revision INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_base_quantity BIGINT NOT NULL,
    total_returned_quantity BIGINT NOT NULL,
    total_lost_quantity BIGINT NOT NULL,
    total_sold_quantity BIGINT NOT NULL,
    expected_amount BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_sales_revision UNIQUE (store_id, business_date, scope_key, revision),
    CONSTRAINT fk_daily_sales_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_daily_sales_seller FOREIGN KEY (seller_id) REFERENCES seller (id),
    CONSTRAINT chk_daily_sales_scope CHECK (
        (scope = 'STORE' AND seller_id IS NULL AND scope_key = 'STORE')
        OR (scope = 'SELLER' AND seller_id IS NOT NULL)
    ),
    INDEX idx_daily_sales_store_date (store_id, business_date),
    INDEX idx_daily_sales_seller (seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE daily_sales_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    daily_sales_id BIGINT NOT NULL,
    batch_line_id BIGINT NOT NULL,
    base_quantity BIGINT NOT NULL,
    returned_quantity BIGINT NOT NULL,
    lost_quantity BIGINT NOT NULL,
    sold_quantity BIGINT NOT NULL,
    unit_sale_price BIGINT NOT NULL,
    expected_amount BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_daily_sales_line_sales FOREIGN KEY (daily_sales_id) REFERENCES daily_sales (id),
    CONSTRAINT fk_daily_sales_line_batch_line FOREIGN KEY (batch_line_id) REFERENCES lottery_batch_line (id),
    INDEX idx_daily_sales_line_sales (daily_sales_id),
    INDEX idx_daily_sales_line_batch (batch_line_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE daily_reconciliation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    scope VARCHAR(20) NOT NULL,
    scope_key VARCHAR(80) NOT NULL,
    seller_id BIGINT NULL,
    revision INT NOT NULL,
    daily_sales_id BIGINT NOT NULL,
    expected_amount BIGINT NOT NULL,
    actual_received_amount BIGINT NOT NULL,
    difference_amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    note VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    closed_by BIGINT NULL,
    closed_at DATETIME(6) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reconciliation_revision UNIQUE (store_id, business_date, scope_key, revision),
    CONSTRAINT uk_reconciliation_daily_sales UNIQUE (daily_sales_id),
    CONSTRAINT fk_reconciliation_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_reconciliation_seller FOREIGN KEY (seller_id) REFERENCES seller (id),
    CONSTRAINT fk_reconciliation_daily_sales FOREIGN KEY (daily_sales_id) REFERENCES daily_sales (id),
    CONSTRAINT chk_reconciliation_scope CHECK (
        (scope = 'STORE' AND seller_id IS NULL AND scope_key = 'STORE')
        OR (scope = 'SELLER' AND seller_id IS NOT NULL)
    ),
    INDEX idx_reconciliation_store_date_status (store_id, business_date, status),
    INDEX idx_reconciliation_seller (seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cash_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    seller_id BIGINT NULL,
    reconciliation_id BIGINT NULL,
    business_date DATE NOT NULL,
    direction VARCHAR(10) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    amount BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    note VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL,
    posted_by BIGINT NULL,
    posted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_cash_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_cash_seller FOREIGN KEY (seller_id) REFERENCES seller (id),
    CONSTRAINT fk_cash_reconciliation FOREIGN KEY (reconciliation_id) REFERENCES daily_reconciliation (id),
    CONSTRAINT chk_cash_amount CHECK (amount > 0),
    INDEX idx_cash_store_date_status (store_id, business_date, status),
    INDEX idx_cash_seller_date (seller_id, business_date),
    INDEX idx_cash_reconciliation (reconciliation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
