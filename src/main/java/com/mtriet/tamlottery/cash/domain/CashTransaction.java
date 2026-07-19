package com.mtriet.tamlottery.cash.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import com.mtriet.tamlottery.identity.domain.Seller;
import com.mtriet.tamlottery.identity.domain.Store;
import com.mtriet.tamlottery.reconciliation.domain.DailyReconciliation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

@Entity
@Table(name = "cash_transaction")
public class CashTransaction extends BaseEntity {

    private static final EnumSet<CashTransactionType> SALES_AFFECTING_TYPES =
            EnumSet.of(CashTransactionType.SALES_COLLECTION, CashTransactionType.REFUND, CashTransactionType.ADJUSTMENT);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_id")
    private DailyReconciliation reconciliation;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private CashTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private long amount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashTransactionStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @OneToMany(mappedBy = "cashTransaction", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<CashTransactionSource> sources = new ArrayList<>();

    protected CashTransaction() {
    }

    public CashTransaction(Store store,
                           Seller seller,
                           LocalDate businessDate,
                           CashDirection direction,
                           CashTransactionType transactionType,
                           PaymentMethod paymentMethod,
                           long amount,
                           Instant occurredAt,
                           String note,
                           Long createdBy) {
        this.store = store;
        this.seller = seller;
        this.businessDate = businessDate;
        this.direction = direction;
        this.transactionType = transactionType;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.occurredAt = occurredAt;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.createdBy = createdBy;
        this.status = CashTransactionStatus.PENDING;
    }

    public void post(Long userId, Instant now) {
        this.status = CashTransactionStatus.POSTED;
        this.postedBy = userId;
        this.postedAt = now;
    }

    public void voidTransaction(String reason, Long userId, Instant now) {
        this.status = CashTransactionStatus.VOID;
        this.voidReason = reason;
        this.voidedBy = userId;
        this.voidedAt = now;
    }

    public void assignTo(DailyReconciliation reconciliation) {
        this.reconciliation = reconciliation;
    }

    public void addSource(CashTransactionSource source) {
        source.attachTo(this);
        sources.add(source);
    }

    public void unassign() {
        this.reconciliation = null;
    }

    public long signedSalesAmount() {
        if (!SALES_AFFECTING_TYPES.contains(transactionType)) {
            return 0;
        }
        return direction == CashDirection.IN ? amount : -amount;
    }

    public Store getStore() {
        return store;
    }

    public Seller getSeller() {
        return seller;
    }

    public DailyReconciliation getReconciliation() {
        return reconciliation;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public CashDirection getDirection() {
        return direction;
    }

    public CashTransactionType getTransactionType() {
        return transactionType;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public long getAmount() {
        return amount;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getNote() {
        return note;
    }

    public CashTransactionStatus getStatus() {
        return status;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public Long getVoidedBy() {
        return voidedBy;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public List<CashTransactionSource> getSources() {
        return Collections.unmodifiableList(sources);
    }
}
