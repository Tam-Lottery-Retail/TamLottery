package com.mtriet.tamlottery.reconciliation.application;

import com.mtriet.tamlottery.cash.domain.CashTransaction;
import com.mtriet.tamlottery.cash.domain.CashTransactionStatus;
import com.mtriet.tamlottery.cash.infrastructure.CashTransactionRepository;
import com.mtriet.tamlottery.common.exception.BusinessException;
import com.mtriet.tamlottery.common.exception.ErrorCode;
import com.mtriet.tamlottery.inventory.domain.AdjustmentDirection;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustment;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustmentStatus;
import com.mtriet.tamlottery.inventory.domain.LotteryBatch;
import com.mtriet.tamlottery.inventory.domain.LotteryBatchLine;
import com.mtriet.tamlottery.inventory.domain.LotteryBatchStatus;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationLine;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationStatus;
import com.mtriet.tamlottery.inventory.domain.TicketReturnLine;
import com.mtriet.tamlottery.inventory.domain.TicketReturnStatus;
import com.mtriet.tamlottery.inventory.domain.TicketReturnType;
import com.mtriet.tamlottery.inventory.infrastructure.InventoryAdjustmentRepository;
import com.mtriet.tamlottery.inventory.infrastructure.LotteryBatchRepository;
import com.mtriet.tamlottery.inventory.infrastructure.TicketAllocationLineRepository;
import com.mtriet.tamlottery.inventory.infrastructure.TicketReturnLineRepository;
import com.mtriet.tamlottery.reconciliation.domain.DailyReconciliation;
import com.mtriet.tamlottery.reconciliation.domain.ReconciliationStatus;
import com.mtriet.tamlottery.reconciliation.domain.SalesScope;
import com.mtriet.tamlottery.reconciliation.infrastructure.DailyReconciliationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesCalculationService {

    private static final EnumSet<TicketAllocationStatus> SALES_ALLOCATION_STATUSES =
            EnumSet.of(TicketAllocationStatus.ISSUED, TicketAllocationStatus.RECONCILED);
    private static final EnumSet<LotteryBatchStatus> SALES_BATCH_STATUSES =
            EnumSet.of(LotteryBatchStatus.CONFIRMED, LotteryBatchStatus.CLOSED);

    private final LotteryBatchRepository batchRepository;
    private final TicketAllocationLineRepository allocationLineRepository;
    private final TicketReturnLineRepository returnLineRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final CashTransactionRepository cashRepository;
    private final DailyReconciliationRepository reconciliationRepository;

    public SalesCalculationService(LotteryBatchRepository batchRepository,
                                   TicketAllocationLineRepository allocationLineRepository,
                                   TicketReturnLineRepository returnLineRepository,
                                   InventoryAdjustmentRepository adjustmentRepository,
                                   CashTransactionRepository cashRepository,
                                   DailyReconciliationRepository reconciliationRepository) {
        this.batchRepository = batchRepository;
        this.allocationLineRepository = allocationLineRepository;
        this.returnLineRepository = returnLineRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.cashRepository = cashRepository;
        this.reconciliationRepository = reconciliationRepository;
    }

    @Transactional(readOnly = true)
    public Calculation calculate(Long storeId, LocalDate businessDate, SalesScope scope, Long sellerId, boolean lockCash) {
        return scope == SalesScope.SELLER
                ? calculateSeller(storeId, businessDate, sellerId, lockCash)
                : calculateStore(storeId, businessDate, lockCash);
    }

    private Calculation calculateSeller(Long storeId, LocalDate businessDate, Long sellerId, boolean lockCash) {
        List<TicketAllocationLine> allocationLines = allocationLineRepository.findSellerLines(
                storeId, sellerId, businessDate, SALES_ALLOCATION_STATUSES);
        Map<Long, MutableLine> grouped = new LinkedHashMap<>();
        Map<Long, Long> allocationToBatch = new LinkedHashMap<>();
        for (TicketAllocationLine allocationLine : allocationLines) {
            LotteryBatchLine batchLine = allocationLine.getBatchLine();
            grouped.computeIfAbsent(batchLine.getId(), ignored -> new MutableLine(batchLine))
                    .base += allocationLine.getQuantityAllocated();
            allocationToBatch.put(allocationLine.getId(), batchLine.getId());
        }
        List<Long> allocationIds = new ArrayList<>(allocationToBatch.keySet());
        if (!allocationIds.isEmpty()) {
            for (TicketReturnLine returnLine : returnLineRepository.findConfirmedForAllocationLines(
                    allocationIds, TicketReturnStatus.CONFIRMED)) {
                grouped.get(returnLine.getBatchLine().getId()).returned += returnLine.getQuantity();
            }
            for (InventoryAdjustment adjustment : adjustmentRepository.findApprovedForAllocationLines(
                    allocationIds, InventoryAdjustmentStatus.APPROVED)) {
                MutableLine line = grouped.get(adjustment.getBatchLine().getId());
                line.lost += signedLoss(adjustment);
            }
        }
        List<CashTransaction> cash = findCash(storeId, businessDate, sellerId, lockCash);
        return finish(grouped, cash, 0);
    }

    private Calculation calculateStore(Long storeId, LocalDate businessDate, boolean lockCash) {
        List<LotteryBatch> batches = batchRepository.findAllByStoreIdAndBusinessDateAndStatusIn(
                storeId, businessDate, SALES_BATCH_STATUSES);
        Map<Long, MutableLine> grouped = new LinkedHashMap<>();
        for (LotteryBatch batch : batches) {
            for (LotteryBatchLine batchLine : batch.getLines()) {
                MutableLine line = grouped.computeIfAbsent(batchLine.getId(), ignored -> new MutableLine(batchLine));
                line.base += batchLine.getQuantityReceived();
            }
        }
        List<Long> batchLineIds = new ArrayList<>(grouped.keySet());
        if (!batchLineIds.isEmpty()) {
            for (TicketReturnLine returnLine : returnLineRepository.findConfirmedForBatchLines(
                    batchLineIds, TicketReturnStatus.CONFIRMED)) {
                if (returnLine.getTicketReturn().getReturnType() == TicketReturnType.STORE_TO_AGENCY) {
                    grouped.get(returnLine.getBatchLine().getId()).returned += returnLine.getQuantity();
                }
            }
            for (InventoryAdjustment adjustment : adjustmentRepository.findApprovedForBatchLines(
                    batchLineIds, InventoryAdjustmentStatus.APPROVED)) {
                grouped.get(adjustment.getBatchLine().getId()).lost += signedLoss(adjustment);
            }
        }
        List<CashTransaction> storeCash = findCash(storeId, businessDate, null, lockCash);
        long sellerActual = reconciliationRepository
                .findAllByStoreIdAndBusinessDateAndScopeAndStatus(
                        storeId, businessDate, SalesScope.SELLER, ReconciliationStatus.CLOSED)
                .stream()
                .mapToLong(DailyReconciliation::getActualReceivedAmount)
                .reduce(0, Math::addExact);
        return finish(grouped, storeCash, sellerActual);
    }

    private List<CashTransaction> findCash(Long storeId, LocalDate date, Long sellerId, boolean lock) {
        return lock
                ? cashRepository.findUnassignedForUpdate(storeId, date, sellerId, CashTransactionStatus.POSTED)
                : cashRepository.findUnassigned(storeId, date, sellerId, CashTransactionStatus.POSTED);
    }

    private Calculation finish(Map<Long, MutableLine> grouped, List<CashTransaction> cash, long carriedActual) {
        List<Line> lines = grouped.values().stream().map(MutableLine::finish).toList();
        long expected = lines.stream().mapToLong(Line::expectedAmount).reduce(0, Math::addExact);
        long actual = cash.stream().mapToLong(CashTransaction::signedSalesAmount).reduce(carriedActual, Math::addExact);
        return new Calculation(lines, expected, actual, carriedActual, Math.subtractExact(actual, expected), cash);
    }

    private long signedLoss(InventoryAdjustment adjustment) {
        return adjustment.getDirection() == AdjustmentDirection.DECREASE
                ? adjustment.getQuantity()
                : -adjustment.getQuantity();
    }

    public record Line(
            LotteryBatchLine batchLine,
            long baseQuantity,
            long returnedQuantity,
            long lostQuantity,
            long soldQuantity,
            long unitSalePrice,
            long expectedAmount) {
    }

    public record Calculation(
            List<Line> lines,
            long expectedAmount,
            long actualReceivedAmount,
            long sellerReconciliationAmount,
            long differenceAmount,
            List<CashTransaction> attachableCash) {

        public long totalBaseQuantity() {
            return lines.stream().mapToLong(Line::baseQuantity).sum();
        }

        public long totalReturnedQuantity() {
            return lines.stream().mapToLong(Line::returnedQuantity).sum();
        }

        public long totalLostQuantity() {
            return lines.stream().mapToLong(Line::lostQuantity).sum();
        }

        public long totalSoldQuantity() {
            return lines.stream().mapToLong(Line::soldQuantity).sum();
        }
    }

    private static final class MutableLine {
        private final LotteryBatchLine batchLine;
        private long base;
        private long returned;
        private long lost;

        private MutableLine(LotteryBatchLine batchLine) {
            this.batchLine = batchLine;
        }

        private Line finish() {
            long sold = base - returned - lost;
            if (lost < 0 || sold < 0) {
                throw BusinessException.invalid(
                        ErrorCode.INVALID_STATE,
                        "Inventory movements are inconsistent for batch line " + batchLine.getId());
            }
            long expected = Math.multiplyExact(sold, batchLine.getUnitSalePrice());
            return new Line(batchLine, base, returned, lost, sold, batchLine.getUnitSalePrice(), expected);
        }
    }
}
