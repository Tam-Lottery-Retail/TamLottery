package com.mtriet.tamlottery.inventory.infrastructure;

import com.mtriet.tamlottery.inventory.domain.TicketReturn;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketReturnRepository extends JpaRepository<TicketReturn, Long> {

    @EntityGraph(attributePaths = {"seller", "agency"})
    Page<TicketReturn> findAllByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"seller", "agency", "lines", "lines.batchLine", "lines.allocationLine"})
    Optional<TicketReturn> findByIdAndStoreId(Long id, Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TicketReturn r where r.id = :id and r.store.id = :storeId")
    Optional<TicketReturn> findForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);
}

