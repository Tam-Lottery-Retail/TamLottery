package com.mtriet.tamlottery.identity.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "seller", uniqueConstraints = @UniqueConstraint(name = "uk_seller_store_code", columnNames = {"store_id", "code"}))
public class Seller extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private UserAccount user;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SellerStatus status;

    protected Seller() {
    }

    public Seller(Store store, UserAccount user, String code, String fullName, String phone) {
        this.store = store;
        this.user = user;
        this.code = code.trim().toUpperCase();
        this.fullName = fullName.trim();
        this.phone = normalizeNullable(phone);
        this.status = SellerStatus.ACTIVE;
    }

    public void changeStatus(SellerStatus status) {
        this.status = status;
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Store getStore() {
        return store;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getCode() {
        return code;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public SellerStatus getStatus() {
        return status;
    }
}

