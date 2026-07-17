package com.mtriet.tamlottery.reconciliation.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import com.mtriet.tamlottery.inventory.domain.LotteryBatchLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "daily_sales_line", uniqueConstraints = @UniqueConstraint(
        name = "uk_daily_sales_line_batch", columnNames = {"daily_sales_id", "batch_line_id"}))
public class DailySalesLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_sales_id", nullable = false)
    private DailySales dailySales;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_line_id", nullable = false)
    private LotteryBatchLine batchLine;

    @Column(name = "base_quantity", nullable = false)
    private long baseQuantity;

    @Column(name = "returned_quantity", nullable = false)
    private long returnedQuantity;

    @Column(name = "lost_quantity", nullable = false)
    private long lostQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private long soldQuantity;

    @Column(name = "unit_sale_price", nullable = false)
    private long unitSalePrice;

    @Column(name = "expected_amount", nullable = false)
    private long expectedAmount;

    protected DailySalesLine() {
    }

    public DailySalesLine(LotteryBatchLine batchLine,
                          long baseQuantity,
                          long returnedQuantity,
                          long lostQuantity,
                          long soldQuantity,
                          long unitSalePrice) {
        this.batchLine = batchLine;
        this.baseQuantity = baseQuantity;
        this.returnedQuantity = returnedQuantity;
        this.lostQuantity = lostQuantity;
        this.soldQuantity = soldQuantity;
        this.unitSalePrice = unitSalePrice;
        this.expectedAmount = Math.multiplyExact(soldQuantity, unitSalePrice);
    }

    void attachTo(DailySales dailySales) {
        this.dailySales = dailySales;
    }

    public LotteryBatchLine getBatchLine() {
        return batchLine;
    }

    public long getBaseQuantity() {
        return baseQuantity;
    }

    public long getReturnedQuantity() {
        return returnedQuantity;
    }

    public long getLostQuantity() {
        return lostQuantity;
    }

    public long getSoldQuantity() {
        return soldQuantity;
    }

    public long getUnitSalePrice() {
        return unitSalePrice;
    }

    public long getExpectedAmount() {
        return expectedAmount;
    }
}
