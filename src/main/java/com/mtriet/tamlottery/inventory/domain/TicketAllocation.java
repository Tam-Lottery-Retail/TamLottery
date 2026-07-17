package com.mtriet.tamlottery.inventory.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import com.mtriet.tamlottery.identity.domain.Seller;
import com.mtriet.tamlottery.identity.domain.Store;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "ticket_allocation")
public class TicketAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "issued_by")
    private Long issuedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketAllocationStatus status;

    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "allocation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<TicketAllocationLine> lines = new ArrayList<>();

    protected TicketAllocation() {
    }

    public TicketAllocation(Store store, Seller seller, LocalDate businessDate, String note) {
        this.store = store;
        this.seller = seller;
        this.businessDate = businessDate;
        this.note = normalize(note);
        this.status = TicketAllocationStatus.DRAFT;
    }

    public void addLine(TicketAllocationLine line) {
        line.attachTo(this);
        lines.add(line);
    }

    public void issue(Long userId, Instant now) {
        this.status = TicketAllocationStatus.ISSUED;
        this.issuedBy = userId;
        this.issuedAt = now;
    }

    public void reconcile() {
        this.status = TicketAllocationStatus.RECONCILED;
    }

    public void reopenAfterRejectedReconciliation() {
        if (this.status == TicketAllocationStatus.RECONCILED) {
            this.status = TicketAllocationStatus.ISSUED;
        }
    }

    public void cancel() {
        this.status = TicketAllocationStatus.CANCELLED;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Store getStore() {
        return store;
    }

    public Seller getSeller() {
        return seller;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public TicketAllocationStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public List<TicketAllocationLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
