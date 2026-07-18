package com.mtriet.tamlottery.reconciliation.application;

import com.mtriet.tamlottery.cash.domain.CashTransaction;
import com.mtriet.tamlottery.cash.domain.CashTransactionStatus;
import com.mtriet.tamlottery.cash.infrastructure.CashTransactionRepository;
import com.mtriet.tamlottery.common.exception.BusinessException;
import com.mtriet.tamlottery.common.exception.ErrorCode;
import com.mtriet.tamlottery.identity.domain.Role;
import com.mtriet.tamlottery.identity.domain.Seller;
import com.mtriet.tamlottery.identity.domain.Store;
import com.mtriet.tamlottery.identity.infrastructure.SellerRepository;
import com.mtriet.tamlottery.identity.infrastructure.StoreRepository;
import com.mtriet.tamlottery.identity.security.CurrentUser;
import com.mtriet.tamlottery.identity.security.CurrentUserProvider;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustmentStatus;
import com.mtriet.tamlottery.inventory.domain.LotteryBatch;
import com.mtriet.tamlottery.inventory.domain.LotteryBatchStatus;
import com.mtriet.tamlottery.inventory.domain.TicketAllocation;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationLine;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationStatus;
import com.mtriet.tamlottery.inventory.infrastructure.InventoryAdjustmentRepository;
import com.mtriet.tamlottery.inventory.infrastructure.LotteryBatchRepository;
import com.mtriet.tamlottery.inventory.infrastructure.TicketAllocationLineRepository;
import com.mtriet.tamlottery.inventory.infrastructure.TicketAllocationRepository;
import com.mtriet.tamlottery.reconciliation.api.ReconciliationDtos;
import com.mtriet.tamlottery.reconciliation.domain.DailyReconciliation;
import com.mtriet.tamlottery.reconciliation.domain.DailySales;
import com.mtriet.tamlottery.reconciliation.domain.DailySalesLine;
import com.mtriet.tamlottery.reconciliation.domain.ReconciliationStatus;
import com.mtriet.tamlottery.reconciliation.domain.SalesScope;
import com.mtriet.tamlottery.reconciliation.infrastructure.DailyReconciliationRepository;
import com.mtriet.tamlottery.reconciliation.infrastructure.DailySalesRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

@Service
public class ReconciliationService {

    private static final EnumSet<ReconciliationStatus> ACTIVE_RECONCILIATION_STATUSES =
            EnumSet.of(ReconciliationStatus.REVIEW_REQUIRED, ReconciliationStatus.CLOSED);
    private static final EnumSet<TicketAllocationStatus> SALES_ALLOCATION_STATUSES =
            EnumSet.of(TicketAllocationStatus.ISSUED, TicketAllocationStatus.RECONCILED);
    private static final EnumSet<LotteryBatchStatus> SALES_BATCH_STATUSES =
            EnumSet.of(LotteryBatchStatus.CONFIRMED, LotteryBatchStatus.CLOSED);

    private final DailySalesRepository salesRepository;
    private final DailyReconciliationRepository reconciliationRepository;
    private final SalesCalculationService calculationService;
    private final CashTransactionRepository cashRepository;
    private final StoreRepository storeRepository;
    private final SellerRepository sellerRepository;
    private final TicketAllocationRepository allocationRepository;
    private final TicketAllocationLineRepository allocationLineRepository;
    private final LotteryBatchRepository batchRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final CurrentUserProvider currentUserProvider;

    public ReconciliationService(DailySalesRepository salesRepository,
                                 DailyReconciliationRepository reconciliationRepository,
                                 SalesCalculationService calculationService,
                                 CashTransactionRepository cashRepository,
                                 StoreRepository storeRepository,
                                 SellerRepository sellerRepository,
                                 TicketAllocationRepository allocationRepository,
                                 TicketAllocationLineRepository allocationLineRepository,
                                 LotteryBatchRepository batchRepository,
                                 InventoryAdjustmentRepository adjustmentRepository,
                                 CurrentUserProvider currentUserProvider) {
        this.salesRepository = salesRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.calculationService = calculationService;
        this.cashRepository = cashRepository;
        this.storeRepository = storeRepository;
        this.sellerRepository = sellerRepository;
        this.allocationRepository = allocationRepository;
        this.allocationLineRepository = allocationLineRepository;
        this.batchRepository = batchRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public ReconciliationDtos.PreviewResponse preview(LocalDate businessDate, SalesScope scope, Long sellerId) {
        CurrentUser current = currentUserProvider.get();
        validateScope(scope, sellerId);
        enforceSellerAccess(current, scope, sellerId);
        if (scope == SalesScope.SELLER) {
            requireSeller(sellerId, current.storeId());
        }
        SalesCalculationService.Calculation calculation = calculationService.calculate(
                current.storeId(), businessDate, scope, sellerId, false);
        return toPreview(businessDate, scope, sellerId, calculation);
    }

    @Transactional
    public ReconciliationDtos.ReconciliationResponse close(ReconciliationDtos.CloseRequest request) {
        CurrentUser current = currentUserProvider.get();
        validateScope(request.scope(), request.sellerId());
        String scopeKey = scopeKey(request.scope(), request.sellerId());
        if (reconciliationRepository.existsByStoreIdAndBusinessDateAndScopeKeyAndStatusIn(
                current.storeId(), request.businessDate(), scopeKey, ACTIVE_RECONCILIATION_STATUSES)) {
            throw BusinessException.conflict(ErrorCode.RECONCILIATION_ALREADY_EXISTS, "This scope and date are already reconciled");
        }

        Seller seller = request.scope() == SalesScope.SELLER ? requireSeller(request.sellerId(), current.storeId()) : null;
        if (request.scope() == SalesScope.STORE) {
            requireAllSellerReconciliations(current.storeId(), request.businessDate());
        }
        ensureNoPendingRecords(current.storeId(), request.businessDate(), request.scope(), request.sellerId());

        SalesCalculationService.Calculation calculation = calculationService.calculate(
                current.storeId(), request.businessDate(), request.scope(), request.sellerId(), true);
        if (calculation.lines().isEmpty()) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "There are no ticket movements to reconcile");
        }
        if (calculation.differenceAmount() != 0 && (request.note() == null || request.note().isBlank())) {
            throw BusinessException.invalid(ErrorCode.RECONCILIATION_REQUIRES_REVIEW, "A reason is required when reconciliation has a difference");
        }

        int revision = Math.max(
                salesRepository.maxRevision(current.storeId(), request.businessDate(), scopeKey),
                reconciliationRepository.maxRevision(current.storeId(), request.businessDate(), scopeKey)) + 1;
        Store store = storeRepository.getReferenceById(current.storeId());
        DailySales dailySales = new DailySales(
                store, request.businessDate(), request.scope(), scopeKey, seller, revision);
        calculation.lines().forEach(line -> dailySales.addLine(new DailySalesLine(
                line.batchLine(),
                line.baseQuantity(),
                line.returnedQuantity(),
                line.lostQuantity(),
                line.soldQuantity(),
                line.unitSalePrice())));
        salesRepository.save(dailySales);

        Instant now = Instant.now();
        DailyReconciliation reconciliation = reconciliationRepository.save(new DailyReconciliation(
                store,
                request.businessDate(),
                request.scope(),
                scopeKey,
                seller,
                revision,
                dailySales,
                calculation.expectedAmount(),
                calculation.actualReceivedAmount(),
                request.note(),
                current.userId(),
                now));
        calculation.attachableCash().forEach(cash -> cash.assignTo(reconciliation));
        freezeInventory(current.storeId(), request.businessDate(), request.scope(), request.sellerId());
        return toReconciliationResponse(reconciliation);
    }

    @Transactional
    public ReconciliationDtos.ReconciliationResponse approve(Long id) {
        CurrentUser current = currentUserProvider.get();
        DailyReconciliation reconciliation = reconciliationRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Reconciliation not found"));
        if (reconciliation.getStatus() != ReconciliationStatus.REVIEW_REQUIRED) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE, "Only a reconciliation requiring review can be approved");
        }
        reconciliation.approve(current.userId(), Instant.now());
        return toReconciliationResponse(reconciliation);
    }

    @Transactional
    public ReconciliationDtos.ReconciliationResponse reject(Long id) {
        CurrentUser current = currentUserProvider.get();
        DailyReconciliation reconciliation = reconciliationRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Reconciliation not found"));
        if (reconciliation.getStatus() != ReconciliationStatus.REVIEW_REQUIRED) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE, "Only a reconciliation requiring review can be rejected");
        }
        reconciliation.reject(current.userId(), Instant.now());
        reconciliation.getDailySales().voidSnapshot();
        cashRepository.findAllByReconciliationId(reconciliation.getId()).forEach(CashTransaction::unassign);
        reopenInventory(current.storeId(), reconciliation);
        return toReconciliationResponse(reconciliation);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationDtos.ReconciliationResponse> listReconciliations(Pageable pageable) {
        CurrentUser current = currentUserProvider.get();
        boolean sellerOnly = sellerOnly(current);
        return (sellerOnly
                ? reconciliationRepository.findAllByStoreIdAndSellerId(current.storeId(), current.sellerId(), pageable)
                : reconciliationRepository.findAllByStoreId(current.storeId(), pageable))
                .map(this::toReconciliationResponse);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationDtos.DailySalesResponse> listDailySales(Pageable pageable) {
        CurrentUser current = currentUserProvider.get();
        boolean sellerOnly = sellerOnly(current);
        return (sellerOnly
                ? salesRepository.findAllByStoreIdAndSellerId(current.storeId(), current.sellerId(), pageable)
                : salesRepository.findAllByStoreId(current.storeId(), pageable))
                .map(this::toDailySalesResponse);
    }

    @Transactional(readOnly = true)
    public ReconciliationDtos.DailySalesResponse getDailySales(Long id) {
        CurrentUser current = currentUserProvider.get();
        DailySales sales = salesRepository.findByIdAndStoreId(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Daily sales not found"));
        enforceSellerAccess(current, sales.getScope(), sales.getSeller() == null ? null : sales.getSeller().getId());
        return toDailySalesResponse(sales);
    }

    private void requireAllSellerReconciliations(Long storeId, LocalDate businessDate) {
        List<Long> sellerIds = allocationRepository.findAllByStoreIdAndBusinessDateAndStatusIn(
                        storeId, businessDate, SALES_ALLOCATION_STATUSES)
                .stream()
                .map(allocation -> allocation.getSeller().getId())
                .distinct()
                .toList();
        for (Long sellerId : sellerIds) {
            if (!reconciliationRepository.existsByStoreIdAndBusinessDateAndScopeKeyAndStatus(
                    storeId, businessDate, scopeKey(SalesScope.SELLER, sellerId), ReconciliationStatus.CLOSED)) {
                throw BusinessException.invalid(
                        ErrorCode.SELLER_RECONCILIATION_REQUIRED,
                        "Seller %d must be reconciled before closing the store".formatted(sellerId));
            }
        }
    }

    private void ensureNoPendingRecords(Long storeId, LocalDate date, SalesScope scope, Long sellerId) {
        if (scope == SalesScope.STORE) {
            if (cashRepository.countByStoreIdAndBusinessDateAndStatus(storeId, date, CashTransactionStatus.PENDING) > 0) {
                throw BusinessException.invalid(ErrorCode.PENDING_CASH_TRANSACTION, "Pending cash transactions must be posted or voided first");
            }
            List<Long> batchLineIds = batchRepository.findAllByStoreIdAndBusinessDateAndStatusIn(storeId, date, SALES_BATCH_STATUSES)
                    .stream().flatMap(batch -> batch.getLines().stream()).map(line -> line.getId()).toList();
            if (!batchLineIds.isEmpty() && adjustmentRepository.countForBatchLinesAndStatus(
                    batchLineIds, InventoryAdjustmentStatus.PENDING) > 0) {
                throw BusinessException.invalid(ErrorCode.UNAPPROVED_ADJUSTMENT, "Pending inventory adjustments must be reviewed first");
            }
        } else {
            if (cashRepository.countByScopeAndStatus(storeId, date, sellerId, CashTransactionStatus.PENDING) > 0) {
                throw BusinessException.invalid(ErrorCode.PENDING_CASH_TRANSACTION, "Pending seller cash transactions must be posted or voided first");
            }
            List<Long> allocationLineIds = allocationLineRepository.findSellerLines(
                    storeId, sellerId, date, SALES_ALLOCATION_STATUSES).stream().map(line -> line.getId()).toList();
            if (!allocationLineIds.isEmpty() && adjustmentRepository.countForAllocationLinesAndStatus(
                    allocationLineIds, InventoryAdjustmentStatus.PENDING) > 0) {
                throw BusinessException.invalid(ErrorCode.UNAPPROVED_ADJUSTMENT, "Pending seller inventory adjustments must be reviewed first");
            }
        }
    }

    private void freezeInventory(Long storeId, LocalDate date, SalesScope scope, Long sellerId) {
        if (scope == SalesScope.SELLER) {
            allocationRepository.findAllByStoreIdAndSellerIdAndBusinessDateAndStatusIn(
                    storeId, sellerId, date, EnumSet.of(TicketAllocationStatus.ISSUED))
                    .forEach(TicketAllocation::reconcile);
        } else {
            batchRepository.findAllByStoreIdAndBusinessDateAndStatusIn(
                    storeId, date, EnumSet.of(LotteryBatchStatus.CONFIRMED)).forEach(LotteryBatch::close);
        }
    }

    private void reopenInventory(Long storeId, DailyReconciliation reconciliation) {
        if (reconciliation.getScope() == SalesScope.SELLER) {
            allocationRepository.findAllByStoreIdAndSellerIdAndBusinessDateAndStatusIn(
                            storeId,
                            reconciliation.getSeller().getId(),
                            reconciliation.getBusinessDate(),
                            EnumSet.of(TicketAllocationStatus.RECONCILED))
                    .forEach(TicketAllocation::reopenAfterRejectedReconciliation);
        } else {
            batchRepository.findAllByStoreIdAndBusinessDateAndStatusIn(
                            storeId,
                            reconciliation.getBusinessDate(),
                            EnumSet.of(LotteryBatchStatus.CLOSED))
                    .forEach(LotteryBatch::reopenAfterRejectedReconciliation);
        }
    }

    private Seller requireSeller(Long sellerId, Long storeId) {
        return sellerRepository.findByIdAndStoreId(sellerId, storeId)
                .orElseThrow(() -> BusinessException.notFound("Seller not found"));
    }

    private void validateScope(SalesScope scope, Long sellerId) {
        if (scope == SalesScope.SELLER && sellerId == null) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "SELLER scope requires sellerId");
        }
        if (scope == SalesScope.STORE && sellerId != null) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "STORE scope must not contain sellerId");
        }
    }

    private String scopeKey(SalesScope scope, Long sellerId) {
        return scope == SalesScope.STORE ? "STORE" : "SELLER:" + sellerId;
    }

    private void enforceSellerAccess(CurrentUser current, SalesScope scope, Long sellerId) {
        if (sellerOnly(current) && (scope != SalesScope.SELLER || !sellerId.equals(current.sellerId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Seller cannot access this reconciliation scope");
        }
    }

    private boolean sellerOnly(CurrentUser current) {
        return current.hasRole(Role.SELLER)
                && !current.hasRole(Role.OWNER)
                && !current.hasRole(Role.MANAGER);
    }

    private ReconciliationDtos.PreviewResponse toPreview(
            LocalDate date, SalesScope scope, Long sellerId, SalesCalculationService.Calculation calculation) {
        return new ReconciliationDtos.PreviewResponse(
                date,
                scope,
                sellerId,
                calculation.totalBaseQuantity(),
                calculation.totalReturnedQuantity(),
                calculation.totalLostQuantity(),
                calculation.totalSoldQuantity(),
                calculation.expectedAmount(),
                calculation.actualReceivedAmount(),
                calculation.sellerReconciliationAmount(),
                calculation.differenceAmount(),
                calculation.lines().stream().map(this::toLineResponse).toList(),
                calculation.attachableCash().stream().map(cash -> new ReconciliationDtos.ReconciliationCashResponse(
                        cash.getId(),
                        cash.getSeller() == null ? null : cash.getSeller().getId(),
                        cash.getDirection(),
                        cash.getTransactionType(),
                        cash.getPaymentMethod(),
                        cash.getAmount(),
                        cash.getOccurredAt())).toList());
    }

    private ReconciliationDtos.SalesLineResponse toLineResponse(SalesCalculationService.Line line) {
        return new ReconciliationDtos.SalesLineResponse(
                line.batchLine().getId(),
                line.batchLine().getDraw().getProvinceCode(),
                line.batchLine().getDraw().getDrawDate(),
                line.baseQuantity(),
                line.returnedQuantity(),
                line.lostQuantity(),
                line.soldQuantity(),
                line.unitSalePrice(),
                line.expectedAmount());
    }

    private ReconciliationDtos.ReconciliationResponse toReconciliationResponse(DailyReconciliation reconciliation) {
        return new ReconciliationDtos.ReconciliationResponse(
                reconciliation.getId(),
                reconciliation.getDailySales().getId(),
                reconciliation.getBusinessDate(),
                reconciliation.getScope(),
                reconciliation.getSeller() == null ? null : reconciliation.getSeller().getId(),
                reconciliation.getRevision(),
                reconciliation.getExpectedAmount(),
                reconciliation.getActualReceivedAmount(),
                reconciliation.getDifferenceAmount(),
                reconciliation.getStatus(),
                reconciliation.getNote(),
                reconciliation.getClosedAt(),
                cashRepository.findAllByReconciliationId(reconciliation.getId()).stream()
                        .map(CashTransaction::getId)
                        .toList());
    }

    private ReconciliationDtos.DailySalesResponse toDailySalesResponse(DailySales sales) {
        List<ReconciliationDtos.SalesLineResponse> lines = sales.getLines().stream()
                .map(line -> new ReconciliationDtos.SalesLineResponse(
                        line.getBatchLine().getId(),
                        line.getBatchLine().getDraw().getProvinceCode(),
                        line.getBatchLine().getDraw().getDrawDate(),
                        line.getBaseQuantity(),
                        line.getReturnedQuantity(),
                        line.getLostQuantity(),
                        line.getSoldQuantity(),
                        line.getUnitSalePrice(),
                        line.getExpectedAmount()))
                .toList();
        return new ReconciliationDtos.DailySalesResponse(
                sales.getId(),
                sales.getBusinessDate(),
                sales.getScope(),
                sales.getSeller() == null ? null : sales.getSeller().getId(),
                sales.getRevision(),
                sales.getStatus(),
                sales.getTotalBaseQuantity(),
                sales.getTotalReturnedQuantity(),
                sales.getTotalLostQuantity(),
                sales.getTotalSoldQuantity(),
                sales.getExpectedAmount(),
                lines);
    }
}
