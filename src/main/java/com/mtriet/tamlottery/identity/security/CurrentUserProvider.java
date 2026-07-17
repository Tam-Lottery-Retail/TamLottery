package com.mtriet.tamlottery.identity.security;

import com.mtriet.tamlottery.common.exception.BusinessException;
import com.mtriet.tamlottery.common.exception.ErrorCode;
import com.mtriet.tamlottery.identity.domain.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CurrentUserProvider {

    public CurrentUser get() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        Jwt jwt = authentication.getToken();
        Long userId = Long.valueOf(jwt.getSubject());
        Long storeId = numberClaim(jwt, "storeId", true);
        Long sellerId = numberClaim(jwt, "sellerId", false);
        List<String> roleClaims = jwt.getClaimAsStringList("roles");
        Set<Role> roles = new HashSet<>();
        if (roleClaims != null) {
            roleClaims.forEach(value -> roles.add(Role.valueOf(value)));
        }
        return new CurrentUser(userId, storeId, sellerId, Set.copyOf(roles));
    }

    private Long numberClaim(Jwt jwt, String name, boolean required) {
        Object value = jwt.getClaim(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            return Long.valueOf(value.toString());
        }
        if (required) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Token is missing claim " + name);
        }
        return null;
    }
}

