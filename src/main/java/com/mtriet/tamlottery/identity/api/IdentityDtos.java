package com.mtriet.tamlottery.identity.api;

import com.mtriet.tamlottery.identity.domain.Role;
import com.mtriet.tamlottery.identity.domain.SellerStatus;
import com.mtriet.tamlottery.identity.domain.StoreStatus;
import com.mtriet.tamlottery.identity.domain.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public final class IdentityDtos {
    private IdentityDtos() {
    }

    public record StoreResponse(Long id, String code, String name, String timezone, StoreStatus status) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 80) String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 160) String fullName,
            @NotEmpty Set<Role> roles) {
    }

    public record UserResponse(Long id, String username, String fullName, UserStatus status, Set<Role> roles) {
    }

    public record ChangeUserStatusRequest(@NotNull UserStatus status) {
    }

    public record CreateSellerRequest(
            Long userId,
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 30) String phone,
            @Valid CreateSellerLoginRequest loginAccount) {
    }

    public record CreateSellerLoginRequest(
            @NotBlank @Size(max = 80) String username,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record SellerResponse(Long id, Long userId, String code, String fullName, String phone, SellerStatus status) {
    }

    public record UpdateSellerRequest(
            Long userId,
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 30) String phone) {
    }

    public record ChangeSellerStatusRequest(@NotNull SellerStatus status) {
    }
}
