package com.mtriet.tamlottery.identity.application;

import com.mtriet.tamlottery.audit.application.AuditService;
import com.mtriet.tamlottery.audit.domain.AuditAction;
import com.mtriet.tamlottery.audit.domain.AuditEntityType;
import com.mtriet.tamlottery.identity.config.BootstrapProperties;
import com.mtriet.tamlottery.identity.domain.Role;
import com.mtriet.tamlottery.identity.domain.Store;
import com.mtriet.tamlottery.identity.domain.UserAccount;
import com.mtriet.tamlottery.identity.infrastructure.StoreRepository;
import com.mtriet.tamlottery.identity.infrastructure.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.Map;

@Service
public class BootstrapService {

    private final BootstrapProperties properties;
    private final StoreRepository storeRepository;
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public BootstrapService(BootstrapProperties properties,
                            StoreRepository storeRepository,
                            UserAccountRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            AuditService auditService) {
        this.properties = properties;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public void initialize() {
        if (!properties.enabled() || userRepository.count() > 0) {
            return;
        }
        require(properties.storeCode(), "BOOTSTRAP_STORE_CODE");
        require(properties.storeName(), "BOOTSTRAP_STORE_NAME");
        require(properties.ownerUsername(), "BOOTSTRAP_OWNER_USERNAME");
        require(properties.ownerPassword(), "BOOTSTRAP_OWNER_PASSWORD");
        require(properties.ownerFullName(), "BOOTSTRAP_OWNER_FULL_NAME");

        Store store = storeRepository.findByCodeIgnoreCase(properties.storeCode())
                .orElseGet(() -> storeRepository.save(new Store(properties.storeCode(), properties.storeName())));
        UserAccount owner = userRepository.save(new UserAccount(
                store,
                properties.ownerUsername(),
                passwordEncoder.encode(properties.ownerPassword()),
                properties.ownerFullName(),
                Set.of(Role.OWNER)));
        auditService.record(owner, AuditAction.STORE_BOOTSTRAPPED, AuditEntityType.STORE,
                store.getId(), null, "Initial store and owner bootstrap", null,
                Map.of(
                        "storeId", store.getId(),
                        "storeCode", store.getCode(),
                        "storeName", store.getName(),
                        "ownerUserId", owner.getId(),
                        "ownerUsername", owner.getUsername()));
    }

    private void require(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentName + " is required when bootstrap is enabled");
        }
    }
}
