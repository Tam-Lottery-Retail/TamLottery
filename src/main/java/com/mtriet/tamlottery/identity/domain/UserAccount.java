package com.mtriet.tamlottery.identity.domain;

import com.mtriet.tamlottery.common.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
public class UserAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() {
    }

    public UserAccount(Store store, String username, String passwordHash, String fullName, Set<Role> roles) {
        this.store = store;
        this.username = username.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.status = UserStatus.ACTIVE;
        this.roles.addAll(roles);
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Store getStore() {
        return store;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}

