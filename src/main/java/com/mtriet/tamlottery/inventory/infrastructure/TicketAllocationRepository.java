package com.mtriet.tamlottery.inventory.infrastructure;

import com.mtriet.tamlottery.inventory.domain.TicketAllocation;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationStatus;
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

public interface TicketAllocationRepository extends JpaRepository<TicketAllocation, Long> {

    @EntityGraph(attributePaths = {"seller"})
    Page<TicketAllocation> findAllByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"seller", "lines", "lines.batchLine", "lines.batchLine.draw"})
    Optional<TicketAllocation> findByIdAndStoreId(Long id, Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TicketAllocation a where a.id = :id and a.store.id = :storeId")
    Optional<TicketAllocation> findForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);

    List<TicketAllocation> findAllByStoreIdAndSellerIdAndBusinessDateAndStatusIn(
            Long storeId, Long sellerId, LocalDate businessDate, Collection<TicketAllocationStatus> statuses);

    List<TicketAllocation> findAllByStoreIdAndBusinessDateAndStatusIn(
            Long storeId, LocalDate businessDate, Collection<TicketAllocationStatus> statuses);
}

