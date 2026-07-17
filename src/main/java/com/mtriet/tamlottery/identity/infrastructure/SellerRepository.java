package com.mtriet.tamlottery.identity.infrastructure;

import com.mtriet.tamlottery.identity.domain.Seller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SellerRepository extends JpaRepository<Seller, Long> {
    Optional<Seller> findByIdAndStoreId(Long id, Long storeId);
    Optional<Seller> findByUserId(Long userId);
    Page<Seller> findAllByStoreId(Long storeId, Pageable pageable);
    boolean existsByStoreIdAndCodeIgnoreCase(Long storeId, String code);
}

