package com.mtriet.tamlottery.identity.infrastructure;

import com.mtriet.tamlottery.identity.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByCodeIgnoreCase(String code);
}

