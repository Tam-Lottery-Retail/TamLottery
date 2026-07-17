package com.mtriet.tamlottery.masterdata.api;

import com.mtriet.tamlottery.masterdata.domain.LotteryDrawStatus;
import com.mtriet.tamlottery.masterdata.domain.LotteryRegion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class MasterDataDtos {
    private MasterDataDtos() {
    }

    public record CreateAgencyRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String contactName,
            @Size(max = 30) String phone) {
    }

    public record AgencyResponse(Long id, String code, String name, String contactName, String phone, boolean active) {
    }

    public record ChangeAgencyStatusRequest(boolean active) {
    }

    public record CreateLotteryDrawRequest(
            @NotBlank @Size(max = 160) String issuerName,
            @NotBlank @Size(max = 30) String provinceCode,
            @NotNull LotteryRegion region,
            @NotNull LocalDate drawDate,
            @NotNull Instant returnCutoffAt) {
    }

    public record LotteryDrawResponse(
            Long id,
            String issuerName,
            String provinceCode,
            LotteryRegion region,
            LocalDate drawDate,
            Instant returnCutoffAt,
            LotteryDrawStatus status) {
    }

    public record ChangeLotteryDrawStatusRequest(@NotNull LotteryDrawStatus status) {
    }
}

