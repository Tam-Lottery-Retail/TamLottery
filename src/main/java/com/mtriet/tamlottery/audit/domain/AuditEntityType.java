package com.mtriet.tamlottery.audit.domain;

public enum AuditEntityType {
    STORE,
    AUTH_SESSION,
    USER,
    SELLER,
    AGENCY,
    LOTTERY_BATCH,
    TICKET_ALLOCATION,
    TICKET_RETURN,
    INVENTORY_ADJUSTMENT,
    CASH_TRANSACTION,
    DAILY_RECONCILIATION
}
