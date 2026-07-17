package com.mtriet.tamlottery.inventory.infrastructure;

import com.mtriet.tamlottery.inventory.domain.LotteryBatch;
import com.mtriet.tamlottery.inventory.domain.LotteryBatchStatus;
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

public interface LotteryBatchRepository extends JpaRepository<LotteryBatch, Long> {

    @EntityGraph(attributePaths = {"agency"})
    Page<LotteryBatch> findAllByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"agency", "lines", "lines.draw"})
    Optional<LotteryBatch> findByIdAndStoreId(Long id, Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LotteryBatch b where b.id = :id and b.store.id = :storeId")
    Optional<LotteryBatch> findForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);

    boolean existsByStoreIdAndReceiptCodeIgnoreCase(Long storeId, String receiptCode);
    boolean existsByStoreIdAndReceiptCodeIgnoreCaseAndIdNot(Long storeId, String receiptCode, Long id);

    List<LotteryBatch> findAllByStoreIdAndBusinessDateAndStatusIn(
            Long storeId, LocalDate businessDate, Collection<LotteryBatchStatus> statuses);
}
