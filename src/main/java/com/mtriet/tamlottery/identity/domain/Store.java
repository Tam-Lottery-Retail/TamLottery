package com.mtriet.tamlottery.identity.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "store")
public class Store extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreStatus status;

    protected Store() {
    }

    public Store(String code, String name) {
        this.code = normalizeCode(code);
        this.name = name.trim();
        this.timezone = "Asia/Ho_Chi_Minh";
        this.status = StoreStatus.ACTIVE;
    }

    private static String normalizeCode(String value) {
        return value.trim().toUpperCase();
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public StoreStatus getStatus() {
        return status;
    }
}

