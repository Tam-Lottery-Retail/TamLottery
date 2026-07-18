package com.mtriet.tamlottery.cash.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "cash_transaction_source", uniqueConstraints = @UniqueConstraint(
        name = "uk_cash_source_transaction_allocation",
        columnNames = {"cash_transaction_id", "allocation_line_id"}))
public class CashTransactionSource extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_transaction_id", nullable = false)
    private CashTransaction cashTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_line_id", nullable = false)
    private TicketAllocationLine allocationLine;

    @Column(nullable = false)
    private long amount;

    protected CashTransactionSource() {
    }

    public CashTransactionSource(TicketAllocationLine allocationLine, long amount) {
        this.allocationLine = allocationLine;
        this.amount = amount;
    }

    void attachTo(CashTransaction cashTransaction) {
        this.cashTransaction = cashTransaction;
    }

    public TicketAllocationLine getAllocationLine() {
        return allocationLine;
    }

    public long getAmount() {
        return amount;
    }
}
