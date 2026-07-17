package com.mtriet.tamlottery.identity.api;

import com.mtriet.tamlottery.identity.application.IdentityService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/stores/current")
    IdentityDtos.StoreResponse currentStore() {
        return identityService.currentStore();
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('OWNER')")
    Page<IdentityDtos.UserResponse> listUsers(Pageable pageable) {
        return identityService.listUsers(pageable);
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('OWNER')")
    IdentityDtos.UserResponse createUser(@Valid @RequestBody IdentityDtos.CreateUserRequest request) {
        return identityService.createUser(request);
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasRole('OWNER')")
    IdentityDtos.UserResponse changeUserStatus(@PathVariable Long id,
                                               @Valid @RequestBody IdentityDtos.ChangeUserStatusRequest request) {
        return identityService.changeUserStatus(id, request);
    }

    @GetMapping("/sellers")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Page<IdentityDtos.SellerResponse> listSellers(Pageable pageable) {
        return identityService.listSellers(pageable);
    }

    @PostMapping("/sellers")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    IdentityDtos.SellerResponse createSeller(@Valid @RequestBody IdentityDtos.CreateSellerRequest request) {
        return identityService.createSeller(request);
    }

    @PatchMapping("/sellers/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    IdentityDtos.SellerResponse changeSellerStatus(@PathVariable Long id,
                                                   @Valid @RequestBody IdentityDtos.ChangeSellerStatusRequest request) {
        return identityService.changeSellerStatus(id, request);
    }
}
