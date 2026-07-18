package com.mtriet.tamlottery.reconciliation.api;

import com.mtriet.tamlottery.cash.domain.CashDirection;
import com.mtriet.tamlottery.cash.domain.CashTransactionType;
import com.mtriet.tamlottery.cash.domain.PaymentMethod;
import com.mtriet.tamlottery.reconciliation.domain.DailySalesStatus;
import com.mtriet.tamlottery.reconciliation.domain.ReconciliationStatus;
import com.mtriet.tamlottery.reconciliation.domain.SalesScope;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReconciliationDtos {
    private ReconciliationDtos() {
    }

    public record SalesLineResponse(
            Long batchLineId,
            String provinceCode,
            LocalDate drawDate,
            long baseQuantity,
            long returnedQuantity,
            long lostQuantity,
            long soldQuantity,
            long unitSalePrice,
            long expectedAmount) {
    }

    public record PreviewResponse(
            LocalDate businessDate,
            SalesScope scope,
            Long sellerId,
            long totalBaseQuantity,
            long totalReturnedQuantity,
            long totalLostQuantity,
            long totalSoldQuantity,
            long expectedAmount,
            long actualReceivedAmount,
            long sellerReconciliationAmount,
            long differenceAmount,
            List<SalesLineResponse> lines,
            List<ReconciliationCashResponse> cashTransactions) {
    }

    public record ReconciliationCashResponse(
            Long id,
            Long sellerId,
            CashDirection direction,
            CashTransactionType transactionType,
            PaymentMethod paymentMethod,
            long amount,
            Instant occurredAt) {
    }

    public record CloseRequest(
            @NotNull LocalDate businessDate,
            @NotNull SalesScope scope,
            Long sellerId,
            @Size(max = 500) String note) {
    }

    public record ReconciliationResponse(
            Long id,
            Long dailySalesId,
            LocalDate businessDate,
            SalesScope scope,
            Long sellerId,
            int revision,
            long expectedAmount,
            long actualReceivedAmount,
            long differenceAmount,
            ReconciliationStatus status,
            String note,
            Instant closedAt,
            List<Long> cashTransactionIds) {
    }

    public record DailySalesResponse(
            Long id,
            LocalDate businessDate,
            SalesScope scope,
            Long sellerId,
            int revision,
            DailySalesStatus status,
            long totalBaseQuantity,
            long totalReturnedQuantity,
            long totalLostQuantity,
            long totalSoldQuantity,
            long expectedAmount,
            List<SalesLineResponse> lines) {
    }
}
