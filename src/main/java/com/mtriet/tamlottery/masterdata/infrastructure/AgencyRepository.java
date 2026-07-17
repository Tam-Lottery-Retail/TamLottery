package com.mtriet.tamlottery.masterdata.infrastructure;

import com.mtriet.tamlottery.masterdata.domain.Agency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgencyRepository extends JpaRepository<Agency, Long> {
    Optional<Agency> findByIdAndStoreId(Long id, Long storeId);
    Page<Agency> findAllByStoreId(Long storeId, Pageable pageable);
    boolean existsByStoreIdAndCodeIgnoreCase(Long storeId, String code);
}

