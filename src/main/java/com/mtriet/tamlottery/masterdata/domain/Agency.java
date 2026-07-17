package com.mtriet.tamlottery.masterdata.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import com.mtriet.tamlottery.identity.domain.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "agency", uniqueConstraints = @UniqueConstraint(name = "uk_agency_store_code", columnNames = {"store_id", "code"}))
public class Agency extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "contact_name", length = 160)
    private String contactName;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false)
    private boolean active;

    protected Agency() {
    }

    public Agency(Store store, String code, String name, String contactName, String phone) {
        this.store = store;
        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.contactName = normalize(contactName);
        this.phone = normalize(phone);
        this.active = true;
    }

    public void changeActive(boolean active) {
        this.active = active;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Store getStore() {
        return store;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getContactName() {
        return contactName;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isActive() {
        return active;
    }
}

