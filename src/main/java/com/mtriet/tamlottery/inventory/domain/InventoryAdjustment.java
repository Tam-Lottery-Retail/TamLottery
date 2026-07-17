package com.mtriet.tamlottery.inventory.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import com.mtriet.tamlottery.identity.domain.Seller;
import com.mtriet.tamlottery.identity.domain.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "inventory_adjustment")
public class InventoryAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_line_id", nullable = false)
    private LotteryBatchLine batchLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "holder_type", nullable = false, length = 20)
    private InventoryHolderType holderType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_line_id")
    private TicketAllocationLine allocationLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 20)
    private InventoryAdjustmentType adjustmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdjustmentDirection direction;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryAdjustmentStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected InventoryAdjustment() {
    }

    public InventoryAdjustment(Store store,
                               LotteryBatchLine batchLine,
                               InventoryHolderType holderType,
                               Seller seller,
                               TicketAllocationLine allocationLine,
                               InventoryAdjustmentType adjustmentType,
                               AdjustmentDirection direction,
                               int quantity,
                               String reason,
                               Long createdBy) {
        this.store = store;
        this.batchLine = batchLine;
        this.holderType = holderType;
        this.seller = seller;
        this.allocationLine = allocationLine;
        this.adjustmentType = adjustmentType;
        this.direction = direction;
        this.quantity = quantity;
        this.reason = reason.trim();
        this.createdBy = createdBy;
        this.status = InventoryAdjustmentStatus.PENDING;
    }

    public void approve(Long userId, Instant now) {
        this.status = InventoryAdjustmentStatus.APPROVED;
        this.approvedBy = userId;
        this.approvedAt = now;
    }

    public void reject(Long userId, Instant now) {
        this.status = InventoryAdjustmentStatus.REJECTED;
        this.approvedBy = userId;
        this.approvedAt = now;
    }

    public Store getStore() {
        return store;
    }

    public LotteryBatchLine getBatchLine() {
        return batchLine;
    }

    public InventoryHolderType getHolderType() {
        return holderType;
    }

    public Seller getSeller() {
        return seller;
    }

    public TicketAllocationLine getAllocationLine() {
        return allocationLine;
    }

    public InventoryAdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public AdjustmentDirection getDirection() {
        return direction;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getReason() {
        return reason;
    }

    public InventoryAdjustmentStatus getStatus() {
        return status;
    }
}
