package com.mtriet.tamlottery.inventory.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_return_line")
public class TicketReturnLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_return_id", nullable = false)
    private TicketReturn ticketReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_line_id", nullable = false)
    private LotteryBatchLine batchLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_line_id")
    private TicketAllocationLine allocationLine;

    @Column(nullable = false)
    private int quantity;

    protected TicketReturnLine() {
    }

    public TicketReturnLine(LotteryBatchLine batchLine, TicketAllocationLine allocationLine, int quantity) {
        this.batchLine = batchLine;
        this.allocationLine = allocationLine;
        this.quantity = quantity;
    }

    void attachTo(TicketReturn ticketReturn) {
        this.ticketReturn = ticketReturn;
    }

    public TicketReturn getTicketReturn() {
        return ticketReturn;
    }

    public LotteryBatchLine getBatchLine() {
        return batchLine;
    }

    public TicketAllocationLine getAllocationLine() {
        return allocationLine;
    }

    public int getQuantity() {
        return quantity;
    }
}

