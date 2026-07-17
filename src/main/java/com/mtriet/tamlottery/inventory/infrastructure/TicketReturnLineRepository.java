package com.mtriet.tamlottery.inventory.infrastructure;

import com.mtriet.tamlottery.inventory.domain.TicketReturnLine;
import com.mtriet.tamlottery.inventory.domain.TicketReturnStatus;
import com.mtriet.tamlottery.inventory.domain.TicketReturnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketReturnLineRepository extends JpaRepository<TicketReturnLine, Long> {

    @Query("select coalesce(sum(l.quantity), 0) from TicketReturnLine l where l.batchLine.id = :batchLineId " +
            "and l.ticketReturn.returnType = :type and l.ticketReturn.status = :status")
    long sumByBatchLineAndType(@Param("batchLineId") Long batchLineId,
                               @Param("type") TicketReturnType type,
                               @Param("status") TicketReturnStatus status);

    @Query("select coalesce(sum(l.quantity), 0) from TicketReturnLine l where l.allocationLine.id = :allocationLineId " +
            "and l.ticketReturn.status = :status")
    long sumByAllocationLine(@Param("allocationLineId") Long allocationLineId,
                             @Param("status") TicketReturnStatus status);

    @Query("select l from TicketReturnLine l join fetch l.ticketReturn r where l.batchLine.id in :batchLineIds " +
            "and r.status = :status")
    List<TicketReturnLine> findConfirmedForBatchLines(@Param("batchLineIds") List<Long> batchLineIds,
                                                      @Param("status") TicketReturnStatus status);

    @Query("select l from TicketReturnLine l join fetch l.ticketReturn r where l.allocationLine.id in :allocationLineIds " +
            "and r.status = :status")
    List<TicketReturnLine> findConfirmedForAllocationLines(@Param("allocationLineIds") List<Long> allocationLineIds,
                                                           @Param("status") TicketReturnStatus status);
}

