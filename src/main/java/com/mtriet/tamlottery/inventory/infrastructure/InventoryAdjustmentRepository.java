package com.mtriet.tamlottery.inventory.infrastructure;

import com.mtriet.tamlottery.inventory.domain.AdjustmentDirection;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustment;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustmentStatus;
import com.mtriet.tamlottery.inventory.domain.InventoryHolderType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {

    @EntityGraph(attributePaths = {"seller", "batchLine"})
    Page<InventoryAdjustment> findAllByStoreId(Long storeId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from InventoryAdjustment a where a.id = :id and a.store.id = :storeId")
    Optional<InventoryAdjustment> findForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);

    @Query("select coalesce(sum(a.quantity), 0) from InventoryAdjustment a where a.batchLine.id = :batchLineId " +
            "and a.status = :status and a.direction = :direction")
    long sumByBatchLine(@Param("batchLineId") Long batchLineId,
                        @Param("status") InventoryAdjustmentStatus status,
                        @Param("direction") AdjustmentDirection direction);

    @Query("select coalesce(sum(a.quantity), 0) from InventoryAdjustment a where a.batchLine.id = :batchLineId " +
            "and a.holderType = :holderType and a.status = :status and a.direction = :direction")
    long sumByBatchLineAndHolderType(@Param("batchLineId") Long batchLineId,
                                     @Param("holderType") InventoryHolderType holderType,
                                     @Param("status") InventoryAdjustmentStatus status,
                                     @Param("direction") AdjustmentDirection direction);

    @Query("select coalesce(sum(a.quantity), 0) from InventoryAdjustment a where a.allocationLine.id = :allocationLineId " +
            "and a.status = :status and a.direction = :direction")
    long sumByAllocationLine(@Param("allocationLineId") Long allocationLineId,
                             @Param("status") InventoryAdjustmentStatus status,
                             @Param("direction") AdjustmentDirection direction);

    @Query("select a from InventoryAdjustment a where a.batchLine.id in :batchLineIds and a.status = :status")
    List<InventoryAdjustment> findApprovedForBatchLines(@Param("batchLineIds") List<Long> batchLineIds,
                                                        @Param("status") InventoryAdjustmentStatus status);

    @Query("select a from InventoryAdjustment a where a.allocationLine.id in :allocationLineIds and a.status = :status")
    List<InventoryAdjustment> findApprovedForAllocationLines(@Param("allocationLineIds") List<Long> allocationLineIds,
                                                             @Param("status") InventoryAdjustmentStatus status);

    @Query("select count(a) from InventoryAdjustment a where a.allocationLine.id in :allocationLineIds and a.status = :status")
    long countForAllocationLinesAndStatus(@Param("allocationLineIds") List<Long> allocationLineIds,
                                          @Param("status") InventoryAdjustmentStatus status);

    @Query("select count(a) from InventoryAdjustment a where a.batchLine.id in :batchLineIds and a.status = :status")
    long countForBatchLinesAndStatus(@Param("batchLineIds") List<Long> batchLineIds,
                                     @Param("status") InventoryAdjustmentStatus status);
}
