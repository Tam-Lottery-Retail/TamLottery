package com.mtriet.tamlottery.reconciliation.domain;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_reconciliation", uniqueConstraints = @UniqueConstraint(
        name = "uk_reconciliation_revision", columnNames = {"store_id", "business_date", "scope_key", "revision"}))
public class DailyReconciliation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesScope scope;

    @Column(name = "scope_key", nullable = false, length = 80)
    private String scopeKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private Seller seller;

    @Column(nullable = false)
    private int revision;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_sales_id", nullable = false, unique = true)
    private DailySales dailySales;

    @Column(name = "expected_amount", nullable = false)
    private long expectedAmount;

    @Column(name = "actual_received_amount", nullable = false)
    private long actualReceivedAmount;

    @Column(name = "difference_amount", nullable = false)
    private long differenceAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReconciliationStatus status;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    protected DailyReconciliation() {
    }

    public DailyReconciliation(Store store,
                               LocalDate businessDate,
                               SalesScope scope,
                               String scopeKey,
                               Seller seller,
                               int revision,
                               DailySales dailySales,
                               long expectedAmount,
                               long actualReceivedAmount,
                               String note,
                               Long createdBy,
                               Instant now) {
        this.store = store;
        this.businessDate = businessDate;
        this.scope = scope;
        this.scopeKey = scopeKey;
        this.seller = seller;
        this.revision = revision;
        this.dailySales = dailySales;
        this.expectedAmount = expectedAmount;
        this.actualReceivedAmount = actualReceivedAmount;
        this.differenceAmount = Math.subtractExact(actualReceivedAmount, expectedAmount);
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.createdBy = createdBy;
        if (differenceAmount == 0) {
            this.status = ReconciliationStatus.CLOSED;
            this.closedBy = createdBy;
            this.closedAt = now;
        } else {
            this.status = ReconciliationStatus.REVIEW_REQUIRED;
        }
    }

    public void approve(Long ownerId, Instant now) {
        this.status = ReconciliationStatus.CLOSED;
        this.reviewedBy = ownerId;
        this.reviewedAt = now;
        this.closedBy = ownerId;
        this.closedAt = now;
    }

    public void reject(String reason, Long ownerId, Instant now) {
        this.status = ReconciliationStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedBy = ownerId;
        this.reviewedAt = now;
    }

    public Store getStore() {
        return store;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public SalesScope getScope() {
        return scope;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public Seller getSeller() {
        return seller;
    }

    public int getRevision() {
        return revision;
    }

    public DailySales getDailySales() {
        return dailySales;
    }

    public long getExpectedAmount() {
        return expectedAmount;
    }

    public long getActualReceivedAmount() {
        return actualReceivedAmount;
    }

    public long getDifferenceAmount() {
        return differenceAmount;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
