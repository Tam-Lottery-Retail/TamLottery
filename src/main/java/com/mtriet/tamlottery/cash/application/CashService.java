package com.mtriet.tamlottery.cash.application;

import com.mtriet.tamlottery.cash.api.CashDtos;
import com.mtriet.tamlottery.cash.domain.CashDirection;
import com.mtriet.tamlottery.cash.domain.CashTransaction;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CashService {

    private final CashTransactionRepository cashRepository;
    private final StoreRepository storeRepository;
    private final SellerRepository sellerRepository;
    private final CurrentUserProvider currentUserProvider;

    public CashService(CashTransactionRepository cashRepository,
                       StoreRepository storeRepository,
                       SellerRepository sellerRepository,
                       CurrentUserProvider currentUserProvider) {
        this.cashRepository = cashRepository;
        this.storeRepository = storeRepository;
        this.sellerRepository = sellerRepository;
        this.currentUserProvider = currentUserProvider;
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
        return toResponse(cashRepository.save(transaction));
    }

    @Transactional
    public CashDtos.CashTransactionResponse post(Long id) {
        CurrentUser current = currentUserProvider.get();
        CashTransaction transaction = cashRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Cash transaction not found"));
        if (transaction.getStatus() != CashTransactionStatus.PENDING) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE, "Only a pending cash transaction can be posted");
        }
        transaction.post(current.userId(), Instant.now());
        return toResponse(transaction);
    }

    @Transactional
    public CashDtos.CashTransactionResponse voidTransaction(Long id) {
        CurrentUser current = currentUserProvider.get();
        CashTransaction transaction = cashRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Cash transaction not found"));
        if (transaction.getStatus() == CashTransactionStatus.VOID) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE, "Cash transaction is already void");
        }
        if (transaction.getReconciliation() != null) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE, "Reconciled cash transaction cannot be voided");
        }
        transaction.voidTransaction();
        return toResponse(transaction);
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

    private boolean sellerOnly(CurrentUser current) {
        return current.hasRole(Role.SELLER) && !current.hasRole(Role.OWNER) && !current.hasRole(Role.MANAGER);
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
                transaction.getPostedAt());
    }
}
