package com.mtriet.tamlottery.reconciliation.infrastructure;

import com.mtriet.tamlottery.reconciliation.domain.DailyReconciliation;
import com.mtriet.tamlottery.reconciliation.domain.ReconciliationStatus;
import com.mtriet.tamlottery.reconciliation.domain.SalesScope;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyReconciliationRepository extends JpaRepository<DailyReconciliation, Long> {

    @EntityGraph(attributePaths = {"seller", "dailySales"})
    Page<DailyReconciliation> findAllByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"seller", "dailySales"})
    Page<DailyReconciliation> findAllByStoreIdAndSellerId(Long storeId, Long sellerId, Pageable pageable);

    @EntityGraph(attributePaths = {"seller", "dailySales"})
    Optional<DailyReconciliation> findByIdAndStoreId(Long id, Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DailyReconciliation r where r.id = :id and r.store.id = :storeId")
    Optional<DailyReconciliation> findForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);

    boolean existsByStoreIdAndBusinessDateAndScopeKeyAndStatusIn(
            Long storeId, LocalDate businessDate, String scopeKey, Collection<ReconciliationStatus> statuses);

    boolean existsByStoreIdAndBusinessDateAndScopeKeyAndStatus(
            Long storeId, LocalDate businessDate, String scopeKey, ReconciliationStatus status);

    List<DailyReconciliation> findAllByStoreIdAndBusinessDateAndScopeAndStatus(
            Long storeId, LocalDate businessDate, SalesScope scope, ReconciliationStatus status);

    @Query("select coalesce(max(r.revision), 0) from DailyReconciliation r where r.store.id = :storeId " +
            "and r.businessDate = :businessDate and r.scopeKey = :scopeKey")
    int maxRevision(@Param("storeId") Long storeId,
                    @Param("businessDate") LocalDate businessDate,
                    @Param("scopeKey") String scopeKey);
}
