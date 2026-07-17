package com.mtriet.tamlottery.reconciliation.domain;

import com.mtriet.tamlottery.identity.domain.Store;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailyReconciliationTest {

    private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");

    @Test
    void closesBalancedReconciliationImmediately() {
        DailyReconciliation reconciliation = reconciliation(1_000_000, 1_000_000, null);

        assertThat(reconciliation.getDifferenceAmount()).isZero();
        assertThat(reconciliation.getStatus()).isEqualTo(ReconciliationStatus.CLOSED);
        assertThat(reconciliation.getClosedAt()).isEqualTo(NOW);
    }

    @Test
    void requiresOwnerReviewWhenMoneyDiffers() {
        DailyReconciliation reconciliation = reconciliation(1_000_000, 980_000, "Thiếu tiền khi giao ca");

        assertThat(reconciliation.getDifferenceAmount()).isEqualTo(-20_000);
        assertThat(reconciliation.getStatus()).isEqualTo(ReconciliationStatus.REVIEW_REQUIRED);
        assertThat(reconciliation.getClosedAt()).isNull();
        assertThat(reconciliation.getNote()).isEqualTo("Thiếu tiền khi giao ca");

        reconciliation.approve(99L, NOW.plusSeconds(60));

        assertThat(reconciliation.getStatus()).isEqualTo(ReconciliationStatus.CLOSED);
    }

    private static DailyReconciliation reconciliation(long expected, long actual, String note) {
        Store store = new Store("TAM-01", "Tam Lottery");
        DailySales sales = new DailySales(
                store, LocalDate.of(2026, 7, 17), SalesScope.STORE, "STORE", null, 1);
        return new DailyReconciliation(
                store,
                LocalDate.of(2026, 7, 17),
                SalesScope.STORE,
                "STORE",
                null,
                1,
                sales,
                expected,
                actual,
                note,
                1L,
                NOW);
    }
}
