package com.mtriet.tamlottery.cash.infrastructure;

import com.mtriet.tamlottery.cash.domain.CashTransaction;
import com.mtriet.tamlottery.cash.domain.CashTransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {

    @EntityGraph(attributePaths = {"seller", "reconciliation"})
    Page<CashTransaction> findAllByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"seller", "reconciliation"})
    Page<CashTransaction> findAllByStoreIdAndSellerId(Long storeId, Long sellerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CashTransaction c where c.id = :id and c.store.id = :storeId")
    Optional<CashTransaction> findForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CashTransaction c where c.store.id = :storeId and c.businessDate = :businessDate " +
            "and c.status = :status and c.reconciliation is null " +
            "and ((:sellerId is null and c.seller is null) or c.seller.id = :sellerId) order by c.id")
    List<CashTransaction> findUnassignedForUpdate(
            @Param("storeId") Long storeId,
            @Param("businessDate") LocalDate businessDate,
            @Param("sellerId") Long sellerId,
            @Param("status") CashTransactionStatus status);

    @Query("select c from CashTransaction c where c.store.id = :storeId and c.businessDate = :businessDate " +
            "and c.status = :status and c.reconciliation is null " +
            "and ((:sellerId is null and c.seller is null) or c.seller.id = :sellerId) order by c.id")
    List<CashTransaction> findUnassigned(
            @Param("storeId") Long storeId,
            @Param("businessDate") LocalDate businessDate,
            @Param("sellerId") Long sellerId,
            @Param("status") CashTransactionStatus status);

    @Query("select count(c) from CashTransaction c where c.store.id = :storeId and c.businessDate = :businessDate " +
            "and c.status = :status and ((:sellerId is null and c.seller is null) or c.seller.id = :sellerId)")
    long countByScopeAndStatus(
            @Param("storeId") Long storeId,
            @Param("businessDate") LocalDate businessDate,
            @Param("sellerId") Long sellerId,
            @Param("status") CashTransactionStatus status);

    long countByStoreIdAndBusinessDateAndStatus(Long storeId, LocalDate businessDate, CashTransactionStatus status);

    List<CashTransaction> findAllByReconciliationId(Long reconciliationId);
}
