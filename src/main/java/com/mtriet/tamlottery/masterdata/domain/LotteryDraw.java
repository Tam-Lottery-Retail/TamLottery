package com.mtriet.tamlottery.masterdata.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import com.mtriet.tamlottery.identity.domain.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "lottery_draw", uniqueConstraints = @UniqueConstraint(
        name = "uk_draw_store_province_date", columnNames = {"store_id", "province_code", "draw_date"}))
public class LotteryDraw extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "issuer_name", nullable = false, length = 160)
    private String issuerName;

    @Column(name = "province_code", nullable = false, length = 30)
    private String provinceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LotteryRegion region;

    @Column(name = "draw_date", nullable = false)
    private LocalDate drawDate;

    @Column(name = "return_cutoff_at", nullable = false)
    private Instant returnCutoffAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LotteryDrawStatus status;

    protected LotteryDraw() {
    }

    public LotteryDraw(Store store, String issuerName, String provinceCode, LotteryRegion region,
                       LocalDate drawDate, Instant returnCutoffAt) {
        this.store = store;
        this.issuerName = issuerName.trim();
        this.provinceCode = provinceCode.trim().toUpperCase();
        this.region = region;
        this.drawDate = drawDate;
        this.returnCutoffAt = returnCutoffAt;
        this.status = LotteryDrawStatus.OPEN;
    }

    public void changeStatus(LotteryDrawStatus status) {
        this.status = status;
    }

    public Store getStore() {
        return store;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public LotteryRegion getRegion() {
        return region;
    }

    public LocalDate getDrawDate() {
        return drawDate;
    }

    public Instant getReturnCutoffAt() {
        return returnCutoffAt;
    }

    public boolean acceptsAgencyReturnsAt(Instant instant) {
        return instant.isBefore(returnCutoffAt);
    }

    public LotteryDrawStatus getStatus() {
        return status;
    }
}
