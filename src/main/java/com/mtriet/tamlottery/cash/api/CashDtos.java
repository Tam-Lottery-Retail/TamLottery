package com.mtriet.tamlottery.cash.api;

import com.mtriet.tamlottery.cash.domain.CashDirection;
import com.mtriet.tamlottery.cash.domain.CashTransactionStatus;
import com.mtriet.tamlottery.cash.domain.CashTransactionType;
import com.mtriet.tamlottery.cash.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CashDtos {
    private CashDtos() {
    }

    public record CreateCashTransactionRequest(
            Long sellerId,
            @NotNull LocalDate businessDate,
            @NotNull CashDirection direction,
            @NotNull CashTransactionType transactionType,
            @NotNull PaymentMethod paymentMethod,
            @Min(10_000) long amount,
            @NotNull Instant occurredAt,
            @Size(max = 500) String note,
            List<@Valid CashSourceRequest> sources) {
    }

    public record CashSourceRequest(
            @NotNull Long allocationLineId,
            @Positive long amount) {
    }

    public record VoidCashTransactionRequest(
            @NotBlank @Size(max = 500) String reason) {
    }

    public record CashSourceResponse(
            Long id,
            Long allocationLineId,
            Long batchLineId,
            String receiptCode,
            String provinceCode,
            LocalDate drawDate,
            long amount) {
    }

    public record CollectionSourceResponse(
            Long allocationLineId,
            Long batchLineId,
            String receiptCode,
            String provinceCode,
            LocalDate drawDate,
            long unitSalePrice,
            long soldQuantity,
            long expectedAmount,
            long activeCollectedAmount,
            long remainingAmount) {
    }

    public record CashTransactionResponse(
            Long id,
            Long sellerId,
            Long reconciliationId,
            LocalDate businessDate,
            CashDirection direction,
            CashTransactionType transactionType,
            PaymentMethod paymentMethod,
            long amount,
            Instant occurredAt,
            String note,
            CashTransactionStatus status,
            Instant postedAt,
            String voidReason,
            Long voidedBy,
            Instant voidedAt,
            List<CashSourceResponse> sources) {
    }
}
