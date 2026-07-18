package com.mtriet.tamlottery.cash.api;

import com.mtriet.tamlottery.cash.domain.CashDirection;
import com.mtriet.tamlottery.cash.domain.CashTransactionStatus;
import com.mtriet.tamlottery.cash.domain.CashTransactionType;
import com.mtriet.tamlottery.cash.domain.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

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
            @Size(max = 500) String note) {
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
            Instant postedAt) {
    }
}
