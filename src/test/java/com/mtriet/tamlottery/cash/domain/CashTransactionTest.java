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
