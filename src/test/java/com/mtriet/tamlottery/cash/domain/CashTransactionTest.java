package com.mtriet.tamlottery.cash.domain;

import com.mtriet.tamlottery.identity.domain.Store;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CashTransactionTest {

    @Test
    void signsOnlySalesAffectingCashMovements() {
        assertThat(transaction(CashDirection.IN, CashTransactionType.SALES_COLLECTION, 500_000).signedSalesAmount())
                .isEqualTo(500_000);
        assertThat(transaction(CashDirection.OUT, CashTransactionType.REFUND, 30_000).signedSalesAmount())
                .isEqualTo(-30_000);
        assertThat(transaction(CashDirection.OUT, CashTransactionType.EXPENSE, 100_000).signedSalesAmount())
                .isZero();
    }

    @Test
    void recordsReasonActorAndTimeWhenVoided() {
        CashTransaction transaction = transaction(CashDirection.IN, CashTransactionType.SALES_COLLECTION, 500_000);
        Instant voidedAt = Instant.parse("2026-07-17T11:00:00Z");

        transaction.voidTransaction("Nhập sai số tiền", 7L, voidedAt);

        assertThat(transaction.getStatus()).isEqualTo(CashTransactionStatus.VOID);
        assertThat(transaction.getVoidReason()).isEqualTo("Nhập sai số tiền");
        assertThat(transaction.getVoidedBy()).isEqualTo(7L);
        assertThat(transaction.getVoidedAt()).isEqualTo(voidedAt);
    }

    private static CashTransaction transaction(CashDirection direction, CashTransactionType type, long amount) {
        return new CashTransaction(
                new Store("TAM-01", "Tam Lottery"),
                null,
                LocalDate.of(2026, 7, 17),
                direction,
                type,
                PaymentMethod.CASH,
                amount,
                Instant.parse("2026-07-17T10:00:00Z"),
                null,
                1L);
    }
}
