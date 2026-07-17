package com.mtriet.tamlottery.inventory.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import com.mtriet.tamlottery.identity.domain.Seller;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "ticket_return")
public class TicketReturn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_type", nullable = false, length = 30)
    private TicketReturnType returnType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id")
    private Agency agency;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketReturnStatus status;

    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "ticketReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<TicketReturnLine> lines = new ArrayList<>();

    protected TicketReturn() {
    }

    public TicketReturn(Store store, TicketReturnType returnType, Seller seller, Agency agency,
                        LocalDate businessDate, String note) {
        this.store = store;
        this.returnType = returnType;
        this.seller = seller;
        this.agency = agency;
        this.businessDate = businessDate;
        this.note = normalize(note);
        this.status = TicketReturnStatus.DRAFT;
    }

    public void addLine(TicketReturnLine line) {
        line.attachTo(this);
        lines.add(line);
    }

    public void confirm(Long userId, Instant now) {
        this.status = TicketReturnStatus.CONFIRMED;
        this.confirmedBy = userId;
        this.returnedAt = now;
    }

    public void cancel() {
        this.status = TicketReturnStatus.CANCELLED;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Store getStore() {
        return store;
    }

    public TicketReturnType getReturnType() {
        return returnType;
    }

    public Seller getSeller() {
        return seller;
    }

    public Agency getAgency() {
        return agency;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public TicketReturnStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public List<TicketReturnLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}

