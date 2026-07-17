package com.mtriet.tamlottery.reconciliation.infrastructure;

import com.mtriet.tamlottery.reconciliation.domain.DailySales;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailySalesRepository extends JpaRepository<DailySales, Long> {

    @EntityGraph(attributePaths = {"seller"})
    Page<DailySales> findAllByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"seller"})
    Page<DailySales> findAllByStoreIdAndSellerId(Long storeId, Long sellerId, Pageable pageable);

    @EntityGraph(attributePaths = {"seller", "lines", "lines.batchLine", "lines.batchLine.draw"})
    Optional<DailySales> findByIdAndStoreId(Long id, Long storeId);

    @Query("select coalesce(max(s.revision), 0) from DailySales s where s.store.id = :storeId " +
            "and s.businessDate = :businessDate and s.scopeKey = :scopeKey")
    int maxRevision(@Param("storeId") Long storeId,
                    @Param("businessDate") LocalDate businessDate,
                    @Param("scopeKey") String scopeKey);
}
