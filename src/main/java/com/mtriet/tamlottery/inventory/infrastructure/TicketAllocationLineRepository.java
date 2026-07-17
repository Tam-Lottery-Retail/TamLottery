package com.mtriet.tamlottery.inventory.infrastructure;

import com.mtriet.tamlottery.inventory.domain.TicketAllocationLine;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketAllocationLineRepository extends JpaRepository<TicketAllocationLine, Long> {

    Optional<TicketAllocationLine> findByIdAndAllocationStoreId(Long id, Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from TicketAllocationLine l join fetch l.allocation a where l.id in :ids and a.store.id = :storeId order by l.id")
    List<TicketAllocationLine> findAllForUpdate(@Param("ids") Collection<Long> ids, @Param("storeId") Long storeId);

    @Query("select coalesce(sum(l.quantityAllocated), 0) from TicketAllocationLine l " +
            "where l.batchLine.id = :batchLineId and l.allocation.status in :statuses")
    long sumAllocated(@Param("batchLineId") Long batchLineId,
                      @Param("statuses") Collection<TicketAllocationStatus> statuses);

    @Query("select l from TicketAllocationLine l join fetch l.batchLine bl join fetch l.allocation a " +
            "where a.store.id = :storeId and a.seller.id = :sellerId and a.businessDate = :businessDate " +
            "and a.status in :statuses")
    List<TicketAllocationLine> findSellerLines(
            @Param("storeId") Long storeId,
            @Param("sellerId") Long sellerId,
            @Param("businessDate") LocalDate businessDate,
            @Param("statuses") Collection<TicketAllocationStatus> statuses);
}

