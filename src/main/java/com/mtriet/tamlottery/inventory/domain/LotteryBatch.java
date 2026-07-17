package com.mtriet.tamlottery.inventory.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import com.mtriet.tamlottery.identity.domain.Store;
import com.mtriet.tamlottery.masterdata.domain.Agency;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "lottery_batch", uniqueConstraints = @UniqueConstraint(
        name = "uk_batch_store_receipt", columnNames = {"store_id", "receipt_code"}))
public class LotteryBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Column(name = "receipt_code", nullable = false, length = 60)
    private String receiptCode;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LotteryBatchStatus status;

    @Column(length = 500)
    private String note;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<LotteryBatchLine> lines = new ArrayList<>();

    protected LotteryBatch() {
    }

    public LotteryBatch(Store store, Agency agency, String receiptCode, LocalDate businessDate,
                        Instant receivedAt, String note) {
        this.store = store;
        this.agency = agency;
        this.receiptCode = receiptCode.trim().toUpperCase();
        this.businessDate = businessDate;
        this.receivedAt = receivedAt;
        this.note = normalize(note);
        this.status = LotteryBatchStatus.DRAFT;
    }

    public void replaceDetails(Agency agency, String receiptCode, LocalDate businessDate, Instant receivedAt,
                               String note, List<LotteryBatchLine> newLines) {
        this.agency = agency;
        this.receiptCode = receiptCode.trim().toUpperCase();
        this.businessDate = businessDate;
        this.receivedAt = receivedAt;
        this.note = normalize(note);
        this.lines.clear();
        newLines.forEach(this::addLine);
    }

    public void addLine(LotteryBatchLine line) {
        line.attachTo(this);
        this.lines.add(line);
    }

    public void confirm(Long userId, Instant now) {
        this.status = LotteryBatchStatus.CONFIRMED;
        this.confirmedBy = userId;
        this.confirmedAt = now;
    }

    public void cancel() {
        this.status = LotteryBatchStatus.CANCELLED;
    }

    public void close() {
        this.status = LotteryBatchStatus.CLOSED;
    }

    public void reopenAfterRejectedReconciliation() {
        if (this.status == LotteryBatchStatus.CLOSED) {
            this.status = LotteryBatchStatus.CONFIRMED;
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Store getStore() {
        return store;
    }

    public Agency getAgency() {
        return agency;
    }

    public String getReceiptCode() {
        return receiptCode;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public LotteryBatchStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public List<LotteryBatchLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
