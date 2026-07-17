package com.mtriet.tamlottery.reconciliation.domain;

import com.mtriet.tamlottery.identity.domain.Store;
import com.mtriet.tamlottery.inventory.domain.LotteryBatchLine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailySalesTest {

    @Test
    void aggregatesSoldQuantityAndExpectedRevenueFromImmutableLines() {
        DailySales sales = new DailySales(
                new Store("TAM-01", "Tam Lottery"),
                LocalDate.of(2026, 7, 17),
                SalesScope.STORE,
                "STORE",
                null,
                1);

        sales.addLine(new DailySalesLine(batchLine(10_000), 100, 10, 2, 88, 10_000));
        sales.addLine(new DailySalesLine(batchLine(20_000), 40, 5, 1, 34, 20_000));

        assertThat(sales.getTotalBaseQuantity()).isEqualTo(140);
        assertThat(sales.getTotalReturnedQuantity()).isEqualTo(15);
        assertThat(sales.getTotalLostQuantity()).isEqualTo(3);
        assertThat(sales.getTotalSoldQuantity()).isEqualTo(122);
        assertThat(sales.getExpectedAmount()).isEqualTo(1_560_000);
        assertThat(sales.getStatus()).isEqualTo(DailySalesStatus.FINALIZED);
        assertThat(sales.getLines()).hasSize(2);
    }

    @Test
    void failsFastWhenExpectedRevenueOverflowsLong() {
        assertThatThrownBy(() -> new DailySalesLine(batchLine(1), 1, 0, 0, 2, Long.MAX_VALUE))
                .isInstanceOf(ArithmeticException.class);
    }

    private static LotteryBatchLine batchLine(long unitSalePrice) {
        return new LotteryBatchLine(null, 100, 8_000, unitSalePrice, null, null);
    }
}
