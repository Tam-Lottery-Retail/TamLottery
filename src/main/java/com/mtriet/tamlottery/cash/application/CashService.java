package com.mtriet.tamlottery.cash.application;

import com.mtriet.tamlottery.audit.application.AuditService;
import com.mtriet.tamlottery.audit.domain.AuditAction;
import com.mtriet.tamlottery.audit.domain.AuditEntityType;
import com.mtriet.tamlottery.cash.api.CashDtos;
import com.mtriet.tamlottery.cash.domain.CashDirection;
import com.mtriet.tamlottery.cash.domain.CashTransaction;
import com.mtriet.tamlottery.cash.domain.CashTransactionSource;
import com.mtriet.tamlottery.cash.domain.CashTransactionStatus;
import com.mtriet.tamlottery.cash.domain.CashTransactionType;
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
import com.mtriet.tamlottery.inventory.application.InventoryAvailabilityService;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationLine;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationStatus;
import com.mtriet.tamlottery.inventory.infrastructure.TicketAllocationLineRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CashService {

    private final CashTransactionRepository cashRepository;
    private final StoreRepository storeRepository;
    private final SellerRepository sellerRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TicketAllocationLineRepository allocationLineRepository;
    private final InventoryAvailabilityService availabilityService;
    private final AuditService auditService;

    public CashService(CashTransactionRepository cashRepository,
                       StoreRepository storeRepository,
                       SellerRepository sellerRepository,
                       CurrentUserProvider currentUserProvider,
                       TicketAllocationLineRepository allocationLineRepository,
                       InventoryAvailabilityService availabilityService,
                       AuditService auditService) {
        this.cashRepository = cashRepository;
        this.storeRepository = storeRepository;
        this.sellerRepository = sellerRepository;
        this.currentUserProvider = currentUserProvider;
        this.allocationLineRepository = allocationLineRepository;
        this.availabilityService = availabilityService;
        this.auditService = auditService;
    }

    @Transactional
    public CashDtos.CashTransactionResponse create(CashDtos.CreateCashTransactionRequest request) {
        CurrentUser current = currentUserProvider.get();
        validateAmount(request.amount());
        validateDirection(request.transactionType(), request.direction());
        Long sellerId = request.sellerId();
        if (sellerOnly(current)) {
            if (current.sellerId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "User has no seller profile");
            }
            sellerId = current.sellerId();
            if (request.transactionType() != CashTransactionType.SALES_COLLECTION || request.direction() != CashDirection.IN) {
                throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Seller can only create incoming sales collections");
            }
        }
        Seller seller = sellerId == null ? null : sellerRepository.findByIdAndStoreId(sellerId, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Seller not found"));
        Store store = storeRepository.getReferenceById(current.storeId());
        CashTransaction transaction = new CashTransaction(
                store,
                seller,
                request.businessDate(),
                request.direction(),
                request.transactionType(),
                request.paymentMethod(),
                request.amount(),
                request.occurredAt(),
                request.note(),
                current.userId());
        attachAndValidateSources(transaction, seller, request);
        cashRepository.save(transaction);
        CashDtos.CashTransactionResponse response = toResponse(transaction);
        auditService.record(current, AuditAction.CASH_TRANSACTION_CREATED, AuditEntityType.CASH_TRANSACTION,
                transaction.getId(), transaction.getBusinessDate(), transaction.getNote(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<CashDtos.CollectionSourceResponse> collectionSources(LocalDate businessDate, Long requestedSellerId) {
        CurrentUser current = currentUserProvider.get();
        Long sellerId = requestedSellerId;
        if (sellerOnly(current)) {
            if (current.sellerId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "User has no seller profile");
            }
            sellerId = current.sellerId();
        }
        if (sellerId == null) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "sellerId is required for collection sources");
        }
        sellerRepository.findByIdAndStoreId(sellerId, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Seller not found"));
        return allocationLineRepository.findSellerLines(
                        current.storeId(), sellerId, businessDate, EnumSet.of(TicketAllocationStatus.ISSUED))
                .stream()
                .sorted(Comparator.comparing(TicketAllocationLine::getId))
                .map(this::toCollectionSource)
                .toList();
    }

    @Transactional
    public CashDtos.CashTransactionResponse post(Long id) {
        CurrentUser current = currentUserProvider.get();
        CashTransaction transaction = cashRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Cash transaction not found"));
        if (transaction.getStatus() != CashTransactionStatus.PENDING) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE, "Only a pending cash transaction can be posted");
        }
        CashDtos.CashTransactionResponse before = toResponse(transaction);
        transaction.post(current.userId(), Instant.now());
        CashDtos.CashTransactionResponse after = toResponse(transaction);
        auditService.record(current, AuditAction.CASH_TRANSACTION_POSTED, AuditEntityType.CASH_TRANSACTION,
                transaction.getId(), transaction.getBusinessDate(), transaction.getNote(), before, after);
        return after;
    }

    @Transactional
    public CashDtos.CashTransactionResponse voidTransaction(
            Long id,
            CashDtos.VoidCashTransactionRequest request) {
        CurrentUser current = currentUserProvider.get();
        String reason = requireReason(request == null ? null : request.reason());
        CashTransaction transaction = cashRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Cash transaction not found"));
        if (transaction.getStatus() == CashTransactionStatus.VOID) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE, "Cash transaction is already void");
        }
        if (transaction.getReconciliation() != null) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE, "Reconciled cash transaction cannot be voided");
        }
        CashDtos.CashTransactionResponse before = toResponse(transaction);
        releaseCollectionCapacity(transaction);
        transaction.voidTransaction(reason, current.userId(), Instant.now());
        CashDtos.CashTransactionResponse after = toResponse(transaction);
        auditService.record(current, AuditAction.CASH_TRANSACTION_VOIDED, AuditEntityType.CASH_TRANSACTION,
                transaction.getId(), transaction.getBusinessDate(), reason, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public Page<CashDtos.CashTransactionResponse> list(Pageable pageable) {
        CurrentUser current = currentUserProvider.get();
        Page<CashTransaction> page = sellerOnly(current)
                ? cashRepository.findAllByStoreIdAndSellerId(current.storeId(), current.sellerId(), pageable)
                : cashRepository.findAllByStoreId(current.storeId(), pageable);
        return page.map(this::toResponse);
    }

    private void validateDirection(CashTransactionType type, CashDirection direction) {
        if (type == CashTransactionType.SALES_COLLECTION && direction != CashDirection.IN) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "SALES_COLLECTION must have IN direction");
        }
        if ((type == CashTransactionType.REFUND || type == CashTransactionType.EXPENSE
                || type == CashTransactionType.AGENCY_PAYMENT) && direction != CashDirection.OUT) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, type + " must have OUT direction");
        }
    }

    private void validateAmount(long amount) {
        if (amount < 10_000) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "Cash transaction amount must be at least 10000");
        }
        if (amount % 10 != 0) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "Cash transaction amount must be divisible by 10");
        }
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "A reason is required to void a cash transaction");
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "Cash transaction void reason must not exceed 500 characters");
        }
        return normalized;
    }

    private boolean sellerOnly(CurrentUser current) {
        return current.hasRole(Role.SELLER) && !current.hasRole(Role.OWNER) && !current.hasRole(Role.MANAGER);
    }

    private void attachAndValidateSources(CashTransaction transaction,
                                          Seller seller,
                                          CashDtos.CreateCashTransactionRequest request) {
        List<CashDtos.CashSourceRequest> requestedSources = request.sources() == null
                ? List.of()
                : request.sources();
        boolean sellerCollection = request.transactionType() == CashTransactionType.SALES_COLLECTION && seller != null;
        if (!sellerCollection) {
            if (!requestedSources.isEmpty()) {
                throw BusinessException.invalid(ErrorCode.INVALID_REQUEST,
                        "Ticket sources are only supported for seller sales collections");
            }
            return;
        }
        if (requestedSources.isEmpty()) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST,
                    "At least one ticket source is required for a seller sales collection");
        }

        Set<Long> uniqueIds = new HashSet<>();
        long sourceTotal = 0;
        for (CashDtos.CashSourceRequest source : requestedSources) {
            if (!uniqueIds.add(source.allocationLineId())) {
                throw BusinessException.invalid(ErrorCode.INVALID_REQUEST,
                        "Each allocation line can appear only once in a cash transaction");
            }
            validateSourceAmount(source.amount());
            sourceTotal = Math.addExact(sourceTotal, source.amount());
        }
        if (sourceTotal != request.amount()) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST,
                    "Cash transaction amount must equal the sum of ticket source amounts");
        }

        List<Long> ids = uniqueIds.stream().sorted().toList();
        List<TicketAllocationLine> lockedLines = allocationLineRepository.findAllForUpdate(
                ids, transaction.getStore().getId());
        if (lockedLines.size() != ids.size()) {
            throw BusinessException.notFound("One or more allocation lines were not found");
        }
        Map<Long, TicketAllocationLine> linesById = new HashMap<>();
        lockedLines.forEach(line -> linesById.put(line.getId(), line));

        List<CashTransactionSource> sources = new ArrayList<>();
        for (CashDtos.CashSourceRequest source : requestedSources) {
            TicketAllocationLine line = linesById.get(source.allocationLineId());
            validateCollectionSource(line, seller, request.businessDate(), source.amount());
            line.collect(source.amount());
            sources.add(new CashTransactionSource(line, source.amount()));
        }
        sources.forEach(transaction::addSource);
    }

    private void validateCollectionSource(TicketAllocationLine line,
                                          Seller seller,
                                          LocalDate businessDate,
                                          long requestedAmount) {
        if (!line.getAllocation().getSeller().getId().equals(seller.getId())) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST,
                    "Ticket source does not belong to the selected seller");
        }
        if (!line.getAllocation().getBusinessDate().equals(businessDate)) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST,
                    "Ticket source and cash transaction must have the same business date");
        }
        if (line.getAllocation().getStatus() != TicketAllocationStatus.ISSUED) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE,
                    "Only issued ticket allocations can receive sales collections");
        }
        CashDtos.CollectionSourceResponse availability = toCollectionSource(line);
        if (requestedAmount > availability.remainingAmount()) {
            throw BusinessException.invalid(ErrorCode.CASH_COLLECTION_EXCEEDS_EXPECTED,
                    "Collection exceeds the remaining expected amount for allocation line " + line.getId());
        }
    }

    private void validateSourceAmount(long amount) {
        if (amount <= 0 || amount % 10 != 0) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST,
                    "Each ticket source amount must be positive and divisible by 10");
        }
    }

    private CashDtos.CollectionSourceResponse toCollectionSource(TicketAllocationLine line) {
        long activeCollectedAmount = line.getActiveCollectedAmount();
        long soldQuantity = Math.max(0, availabilityService.sellerAvailable(line));
        long expectedAmount = Math.multiplyExact(soldQuantity, line.getBatchLine().getUnitSalePrice());
        long remainingAmount = Math.max(0, expectedAmount - activeCollectedAmount);
        return new CashDtos.CollectionSourceResponse(
                line.getId(),
                line.getBatchLine().getId(),
                line.getBatchLine().getBatch().getReceiptCode(),
                line.getBatchLine().getDraw().getProvinceCode(),
                line.getBatchLine().getDraw().getDrawDate(),
                line.getBatchLine().getUnitSalePrice(),
                soldQuantity,
                expectedAmount,
                activeCollectedAmount,
                remainingAmount);
    }

    private void releaseCollectionCapacity(CashTransaction transaction) {
        if (transaction.getSources().isEmpty()) {
            return;
        }
        List<Long> ids = transaction.getSources().stream()
                .map(source -> source.getAllocationLine().getId())
                .distinct()
                .sorted()
                .toList();
        Map<Long, TicketAllocationLine> lockedLines = new HashMap<>();
        allocationLineRepository.findAllForUpdate(ids, transaction.getStore().getId())
                .forEach(line -> lockedLines.put(line.getId(), line));
        if (lockedLines.size() != ids.size()) {
            throw BusinessException.notFound("One or more allocation lines were not found");
        }
        transaction.getSources().forEach(source ->
                lockedLines.get(source.getAllocationLine().getId()).releaseCollection(source.getAmount()));
    }

    private CashDtos.CashTransactionResponse toResponse(CashTransaction transaction) {
        return new CashDtos.CashTransactionResponse(
                transaction.getId(),
                transaction.getSeller() == null ? null : transaction.getSeller().getId(),
                transaction.getReconciliation() == null ? null : transaction.getReconciliation().getId(),
                transaction.getBusinessDate(),
                transaction.getDirection(),
                transaction.getTransactionType(),
                transaction.getPaymentMethod(),
                transaction.getAmount(),
                transaction.getOccurredAt(),
                transaction.getNote(),
                transaction.getStatus(),
                transaction.getPostedAt(),
                transaction.getVoidReason(),
                transaction.getVoidedBy(),
                transaction.getVoidedAt(),
                transaction.getSources().stream().map(source -> {
                    TicketAllocationLine line = source.getAllocationLine();
                    return new CashDtos.CashSourceResponse(
                            source.getId(),
                            line.getId(),
                            line.getBatchLine().getId(),
                            line.getBatchLine().getBatch().getReceiptCode(),
                            line.getBatchLine().getDraw().getProvinceCode(),
                            line.getBatchLine().getDraw().getDrawDate(),
                            source.getAmount());
                }).toList());
    }
}
