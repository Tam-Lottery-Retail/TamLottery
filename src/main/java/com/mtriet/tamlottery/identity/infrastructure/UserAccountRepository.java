package com.mtriet.tamlottery.identity.infrastructure;

import com.mtriet.tamlottery.identity.domain.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    Optional<UserAccount> findByIdAndStoreId(Long id, Long storeId);
    Page<UserAccount> findAllByStoreId(Long storeId, Pageable pageable);
    boolean existsByUsernameIgnoreCase(String username);
}

