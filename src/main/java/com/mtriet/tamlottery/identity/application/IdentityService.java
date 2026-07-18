package com.mtriet.tamlottery.identity.application;

import com.mtriet.tamlottery.common.exception.BusinessException;
import com.mtriet.tamlottery.common.exception.ErrorCode;
import com.mtriet.tamlottery.identity.api.IdentityDtos;
import com.mtriet.tamlottery.identity.domain.Role;
import com.mtriet.tamlottery.identity.domain.Seller;
import com.mtriet.tamlottery.identity.domain.Store;
import com.mtriet.tamlottery.identity.domain.UserAccount;
import com.mtriet.tamlottery.identity.domain.UserStatus;
import com.mtriet.tamlottery.identity.infrastructure.SellerRepository;
import com.mtriet.tamlottery.identity.infrastructure.StoreRepository;
import com.mtriet.tamlottery.identity.infrastructure.UserAccountRepository;
import com.mtriet.tamlottery.identity.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class IdentityService {

    private final StoreRepository storeRepository;
    private final UserAccountRepository userRepository;
    private final SellerRepository sellerRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;

    public IdentityService(StoreRepository storeRepository,
                           UserAccountRepository userRepository,
                           SellerRepository sellerRepository,
                           CurrentUserProvider currentUserProvider,
                           PasswordEncoder passwordEncoder) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.sellerRepository = sellerRepository;
        this.currentUserProvider = currentUserProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public IdentityDtos.StoreResponse currentStore() {
        Store store = storeRepository.findById(currentUserProvider.get().storeId())
                .orElseThrow(() -> BusinessException.notFound("Store not found"));
        return new IdentityDtos.StoreResponse(store.getId(), store.getCode(), store.getName(), store.getTimezone(), store.getStatus());
    }

    @Transactional
    public IdentityDtos.UserResponse createUser(IdentityDtos.CreateUserRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Username already exists");
        }
        Store store = storeRepository.getReferenceById(currentUserProvider.get().storeId());
        UserAccount user = userRepository.save(new UserAccount(
                store, username, passwordEncoder.encode(request.password()), request.fullName(), request.roles()));
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<IdentityDtos.UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAllByStoreId(currentUserProvider.get().storeId(), pageable).map(this::toUserResponse);
    }

    @Transactional
    public IdentityDtos.UserResponse changeUserStatus(Long id, IdentityDtos.ChangeUserStatusRequest request) {
        var current = currentUserProvider.get();
        UserAccount user = userRepository.findByIdAndStoreId(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        if (id.equals(current.userId()) && request.status() != UserStatus.ACTIVE) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "You cannot disable your own account");
        }
        user.changeStatus(request.status());
        return toUserResponse(user);
    }

    @Transactional
    public IdentityDtos.SellerResponse createSeller(IdentityDtos.CreateSellerRequest request) {
        var current = currentUserProvider.get();
        Long storeId = current.storeId();
        String code = request.code().trim();
        if (sellerRepository.existsByStoreIdAndCodeIgnoreCase(storeId, code)) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Seller code already exists");
        }
        if (request.userId() != null && request.loginAccount() != null) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST,
                    "Choose either an existing user or create a login account, not both");
        }
        Store store = storeRepository.getReferenceById(storeId);
        UserAccount user = null;
        if (request.loginAccount() != null) {
            if (!current.hasRole(Role.OWNER)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                        "Only an owner can create a login account for a seller");
            }
            String username = request.loginAccount().username().trim();
            if (userRepository.existsByUsernameIgnoreCase(username)) {
                throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Username already exists");
            }
            user = userRepository.save(new UserAccount(
                    store,
                    username,
                    passwordEncoder.encode(request.loginAccount().password()),
                    request.fullName(),
                    Set.of(Role.SELLER)));
        } else if (request.userId() != null) {
            user = userRepository.findByIdAndStoreId(request.userId(), storeId)
                    .orElseThrow(() -> BusinessException.notFound("User not found"));
            if (sellerRepository.findByUserId(user.getId()).isPresent()) {
                throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "User is already linked to a seller");
            }
            requireSellerRole(user);
        }
        return toSellerResponse(sellerRepository.save(new Seller(
                store, user, code, request.fullName(), request.phone())));
    }

    @Transactional(readOnly = true)
    public Page<IdentityDtos.SellerResponse> listSellers(Pageable pageable) {
        return sellerRepository.findAllByStoreId(currentUserProvider.get().storeId(), pageable).map(this::toSellerResponse);
    }

    @Transactional
    public IdentityDtos.SellerResponse changeSellerStatus(Long id, IdentityDtos.ChangeSellerStatusRequest request) {
        Seller seller = sellerRepository.findByIdAndStoreId(id, currentUserProvider.get().storeId())
                .orElseThrow(() -> BusinessException.notFound("Seller not found"));
        seller.changeStatus(request.status());
        return toSellerResponse(seller);
    }

    @Transactional
    public IdentityDtos.SellerResponse updateSeller(Long id, IdentityDtos.UpdateSellerRequest request) {
        Long storeId = currentUserProvider.get().storeId();
        Seller seller = sellerRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> BusinessException.notFound("Seller not found"));
        String code = request.code().trim();
        if (sellerRepository.existsByStoreIdAndCodeIgnoreCaseAndIdNot(storeId, code, id)) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Seller code already exists");
        }
        UserAccount user = null;
        if (request.userId() != null) {
            user = userRepository.findByIdAndStoreId(request.userId(), storeId)
                    .orElseThrow(() -> BusinessException.notFound("User not found"));
            Seller linkedSeller = sellerRepository.findByUserId(user.getId()).orElse(null);
            if (linkedSeller != null && !linkedSeller.getId().equals(id)) {
                throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "User is already linked to a seller");
            }
            requireSellerRole(user);
        }
        seller.updateDetails(user, code, request.fullName(), request.phone());
        return toSellerResponse(seller);
    }

    private void requireSellerRole(UserAccount user) {
        if (!user.getRoles().contains(Role.SELLER)) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "Linked user must have SELLER role");
        }
    }

    private IdentityDtos.UserResponse toUserResponse(UserAccount user) {
        return new IdentityDtos.UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getStatus(), user.getRoles());
    }

    private IdentityDtos.SellerResponse toSellerResponse(Seller seller) {
        Long userId = seller.getUser() == null ? null : seller.getUser().getId();
        return new IdentityDtos.SellerResponse(
                seller.getId(), userId, seller.getCode(), seller.getFullName(), seller.getPhone(), seller.getStatus());
    }
}
