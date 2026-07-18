package com.mtriet.tamlottery.masterdata.domain;

import com.mtriet.tamlottery.identity.domain.Store;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LotteryDrawTest {

    @Test
    void acceptsAgencyReturnsOnlyBeforeTheCutoff() {
        Instant cutoff = Instant.parse("2026-07-19T08:30:00Z");
        LotteryDraw draw = new LotteryDraw(
                new Store("TEST", "Test Store"),
                "XSKT Vĩnh Long",
                "VL",
                LotteryRegion.SOUTH,
                LocalDate.of(2026, 7, 19),
                cutoff);

        assertThat(draw.acceptsAgencyReturnsAt(cutoff.minusNanos(1))).isTrue();
        assertThat(draw.acceptsAgencyReturnsAt(cutoff)).isFalse();
        assertThat(draw.acceptsAgencyReturnsAt(cutoff.plusSeconds(1))).isFalse();
    }
}
