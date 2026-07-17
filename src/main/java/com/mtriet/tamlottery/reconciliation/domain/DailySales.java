package com.mtriet.tamlottery.reconciliation.domain;

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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "daily_sales", uniqueConstraints = @UniqueConstraint(
        name = "uk_daily_sales_revision", columnNames = {"store_id", "business_date", "scope_key", "revision"}))
public class DailySales extends BaseEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DailySalesStatus status;

    @Column(name = "total_base_quantity", nullable = false)
    private long totalBaseQuantity;

    @Column(name = "total_returned_quantity", nullable = false)
    private long totalReturnedQuantity;

    @Column(name = "total_lost_quantity", nullable = false)
    private long totalLostQuantity;

    @Column(name = "total_sold_quantity", nullable = false)
    private long totalSoldQuantity;

    @Column(name = "expected_amount", nullable = false)
    private long expectedAmount;

    @OneToMany(mappedBy = "dailySales", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<DailySalesLine> lines = new ArrayList<>();

    protected DailySales() {
    }

    public DailySales(Store store, LocalDate businessDate, SalesScope scope, String scopeKey,
                      Seller seller, int revision) {
        this.store = store;
        this.businessDate = businessDate;
        this.scope = scope;
        this.scopeKey = scopeKey;
        this.seller = seller;
        this.revision = revision;
        this.status = DailySalesStatus.FINALIZED;
    }

    public void addLine(DailySalesLine line) {
        line.attachTo(this);
        lines.add(line);
        totalBaseQuantity += line.getBaseQuantity();
        totalReturnedQuantity += line.getReturnedQuantity();
        totalLostQuantity += line.getLostQuantity();
        totalSoldQuantity += line.getSoldQuantity();
        expectedAmount = Math.addExact(expectedAmount, line.getExpectedAmount());
    }

    public void voidSnapshot() {
        this.status = DailySalesStatus.VOID;
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

    public DailySalesStatus getStatus() {
        return status;
    }

    public long getTotalBaseQuantity() {
        return totalBaseQuantity;
    }

    public long getTotalReturnedQuantity() {
        return totalReturnedQuantity;
    }

    public long getTotalLostQuantity() {
        return totalLostQuantity;
    }

    public long getTotalSoldQuantity() {
        return totalSoldQuantity;
    }

    public long getExpectedAmount() {
        return expectedAmount;
    }

    public List<DailySalesLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}

