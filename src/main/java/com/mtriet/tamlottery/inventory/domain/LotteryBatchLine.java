package com.mtriet.tamlottery.inventory.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import com.mtriet.tamlottery.masterdata.domain.LotteryDraw;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "lottery_batch_line")
public class LotteryBatchLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private LotteryBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lottery_draw_id", nullable = false)
    private LotteryDraw draw;

    @Column(name = "quantity_received", nullable = false)
    private int quantityReceived;

    @Column(name = "unit_cost", nullable = false)
    private long unitCost;

    @Column(name = "unit_sale_price", nullable = false)
    private long unitSalePrice;

    @Column(name = "serial_from", length = 40)
    private String serialFrom;

    @Column(name = "serial_to", length = 40)
    private String serialTo;

    protected LotteryBatchLine() {
    }

    public LotteryBatchLine(LotteryDraw draw, int quantityReceived, long unitCost, long unitSalePrice,
                            String serialFrom, String serialTo) {
        this.draw = draw;
        this.quantityReceived = quantityReceived;
        this.unitCost = unitCost;
        this.unitSalePrice = unitSalePrice;
        this.serialFrom = normalize(serialFrom);
        this.serialTo = normalize(serialTo);
    }

    void attachTo(LotteryBatch batch) {
        this.batch = batch;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public LotteryBatch getBatch() {
        return batch;
    }

    public LotteryDraw getDraw() {
        return draw;
    }

    public int getQuantityReceived() {
        return quantityReceived;
    }

    public long getUnitCost() {
        return unitCost;
    }

    public long getUnitSalePrice() {
        return unitSalePrice;
    }

    public String getSerialFrom() {
        return serialFrom;
    }

    public String getSerialTo() {
        return serialTo;
    }
}

