package com.mtriet.tamlottery.identity.security;

import com.mtriet.tamlottery.identity.domain.Role;

import java.util.Set;

public record CurrentUser(Long userId, Long storeId, Long sellerId, Set<Role> roles) {
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}

