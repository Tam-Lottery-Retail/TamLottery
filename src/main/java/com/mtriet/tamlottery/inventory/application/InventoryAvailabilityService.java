package com.mtriet.tamlottery.inventory.application;

import com.mtriet.tamlottery.inventory.domain.AdjustmentDirection;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustmentStatus;
import com.mtriet.tamlottery.inventory.domain.InventoryHolderType;
import com.mtriet.tamlottery.inventory.domain.LotteryBatchLine;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationLine;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationStatus;
import com.mtriet.tamlottery.inventory.domain.TicketReturnStatus;
import com.mtriet.tamlottery.inventory.domain.TicketReturnType;
import com.mtriet.tamlottery.inventory.infrastructure.InventoryAdjustmentRepository;
import com.mtriet.tamlottery.inventory.infrastructure.TicketAllocationLineRepository;
import com.mtriet.tamlottery.inventory.infrastructure.TicketReturnLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

@Service
public class InventoryAvailabilityService {

    private static final EnumSet<TicketAllocationStatus> CONSUMING_ALLOCATION_STATUSES =
            EnumSet.of(TicketAllocationStatus.ISSUED, TicketAllocationStatus.RECONCILED);

    private final TicketAllocationLineRepository allocationLineRepository;
    private final TicketReturnLineRepository returnLineRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;

    public InventoryAvailabilityService(TicketAllocationLineRepository allocationLineRepository,
                                        TicketReturnLineRepository returnLineRepository,
                                        InventoryAdjustmentRepository adjustmentRepository) {
        this.allocationLineRepository = allocationLineRepository;
        this.returnLineRepository = returnLineRepository;
        this.adjustmentRepository = adjustmentRepository;
    }

    @Transactional(readOnly = true)
    public long storeAvailable(LotteryBatchLine batchLine) {
        long allocated = allocationLineRepository.sumAllocated(batchLine.getId(), CONSUMING_ALLOCATION_STATUSES);
        long sellerReturned = returnLineRepository.sumByBatchLineAndType(
                batchLine.getId(), TicketReturnType.SELLER_TO_STORE, TicketReturnStatus.CONFIRMED);
        long agencyReturned = returnLineRepository.sumByBatchLineAndType(
                batchLine.getId(), TicketReturnType.STORE_TO_AGENCY, TicketReturnStatus.CONFIRMED);
        return (long) batchLine.getQuantityReceived()
                - allocated
                + sellerReturned
                - agencyReturned
                - netAdjustment(batchLine.getId(), InventoryHolderType.STORE);
    }

    @Transactional(readOnly = true)
    public long sellerAvailable(TicketAllocationLine allocationLine) {
        long returned = returnLineRepository.sumByAllocationLine(
                allocationLine.getId(), TicketReturnStatus.CONFIRMED);
        return (long) allocationLine.getQuantityAllocated()
                - returned
                - netAdjustment(allocationLine.getId());
    }

    @Transactional(readOnly = true)
    public long netAdjustment(Long batchLineId, InventoryHolderType holderType) {
        long decrease = adjustmentRepository.sumByBatchLineAndHolderType(
                batchLineId, holderType, InventoryAdjustmentStatus.APPROVED, AdjustmentDirection.DECREASE);
        long increase = adjustmentRepository.sumByBatchLineAndHolderType(
                batchLineId, holderType, InventoryAdjustmentStatus.APPROVED, AdjustmentDirection.INCREASE);
        return decrease - increase;
    }

    @Transactional(readOnly = true)
    public long netAdjustment(Long allocationLineId) {
        long decrease = adjustmentRepository.sumByAllocationLine(
                allocationLineId, InventoryAdjustmentStatus.APPROVED, AdjustmentDirection.DECREASE);
        long increase = adjustmentRepository.sumByAllocationLine(
                allocationLineId, InventoryAdjustmentStatus.APPROVED, AdjustmentDirection.INCREASE);
        return decrease - increase;
    }

    @Transactional(readOnly = true)
    public long totalNetAdjustment(Long batchLineId) {
        long decrease = adjustmentRepository.sumByBatchLine(
                batchLineId, InventoryAdjustmentStatus.APPROVED, AdjustmentDirection.DECREASE);
        long increase = adjustmentRepository.sumByBatchLine(
                batchLineId, InventoryAdjustmentStatus.APPROVED, AdjustmentDirection.INCREASE);
        return decrease - increase;
    }
}
