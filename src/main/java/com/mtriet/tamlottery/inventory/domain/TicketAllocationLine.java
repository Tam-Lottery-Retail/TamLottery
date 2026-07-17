package com.mtriet.tamlottery.inventory.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "ticket_allocation_line", uniqueConstraints = @UniqueConstraint(
        name = "uk_allocation_line_batch", columnNames = {"allocation_id", "batch_line_id"}))
public class TicketAllocationLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id", nullable = false)
    private TicketAllocation allocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_line_id", nullable = false)
    private LotteryBatchLine batchLine;

    @Column(name = "quantity_allocated", nullable = false)
    private int quantityAllocated;

    protected TicketAllocationLine() {
    }

    public TicketAllocationLine(LotteryBatchLine batchLine, int quantityAllocated) {
        this.batchLine = batchLine;
        this.quantityAllocated = quantityAllocated;
    }

    void attachTo(TicketAllocation allocation) {
        this.allocation = allocation;
    }

    public TicketAllocation getAllocation() {
        return allocation;
    }

    public LotteryBatchLine getBatchLine() {
        return batchLine;
    }

    public int getQuantityAllocated() {
        return quantityAllocated;
    }
}
