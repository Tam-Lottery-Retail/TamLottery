package com.mtriet.tamlottery.inventory.api;

import com.mtriet.tamlottery.inventory.domain.AdjustmentDirection;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustmentStatus;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustmentType;
import com.mtriet.tamlottery.inventory.domain.InventoryHolderType;
import com.mtriet.tamlottery.inventory.domain.LotteryBatchStatus;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationStatus;
import com.mtriet.tamlottery.inventory.domain.TicketReturnStatus;
import com.mtriet.tamlottery.inventory.domain.TicketReturnType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InventoryDtos {
    private InventoryDtos() {
    }

    public record BatchLineRequest(
            @NotNull Long drawId,
            @Positive int quantityReceived,
            @PositiveOrZero long unitCost,
            @Positive long unitSalePrice,
            @Size(max = 40) String serialFrom,
            @Size(max = 40) String serialTo) {
    }

    public record BatchRequest(
            @NotNull Long agencyId,
            @NotBlank @Size(max = 60) String receiptCode,
            @NotNull LocalDate businessDate,
            @NotNull Instant receivedAt,
            @Size(max = 500) String note,
            @NotEmpty List<@Valid BatchLineRequest> lines) {
    }

    public record BatchLineResponse(
            Long id,
            Long drawId,
            String provinceCode,
            LocalDate drawDate,
            int quantityReceived,
            long unitCost,
            long unitSalePrice,
            String serialFrom,
            String serialTo) {
    }

    public record BatchResponse(
            Long id,
            Long agencyId,
            String agencyName,
            String receiptCode,
            LocalDate businessDate,
            Instant receivedAt,
            LotteryBatchStatus status,
            String note,
            Instant confirmedAt,
            List<BatchLineResponse> lines) {
    }

    public record AllocationLineRequest(@NotNull Long batchLineId, @Positive int quantity) {
    }

    public record AllocationRequest(
            @NotNull Long sellerId,
            @NotNull LocalDate businessDate,
            @Size(max = 500) String note,
            @NotEmpty List<@Valid AllocationLineRequest> lines) {
    }

    public record AllocationLineResponse(
            Long id,
            Long batchLineId,
            String provinceCode,
            LocalDate drawDate,
            int quantity) {
    }

    public record AllocationResponse(
            Long id,
            Long sellerId,
            String sellerName,
            LocalDate businessDate,
            Instant issuedAt,
            TicketAllocationStatus status,
            String note,
            List<AllocationLineResponse> lines) {
    }

    public record ReturnLineRequest(
            @NotNull Long batchLineId,
            Long allocationLineId,
            @Positive int quantity) {
    }

    public record ReturnRequest(
            @NotNull TicketReturnType returnType,
            Long sellerId,
            Long agencyId,
            @NotNull LocalDate businessDate,
            @Size(max = 500) String note,
            @NotEmpty List<@Valid ReturnLineRequest> lines) {
    }

    public record ReturnLineResponse(Long id, Long batchLineId, Long allocationLineId, int quantity) {
    }

    public record ReturnResponse(
            Long id,
            TicketReturnType returnType,
            Long sellerId,
            Long agencyId,
            LocalDate businessDate,
            Instant returnedAt,
            TicketReturnStatus status,
            String note,
            List<ReturnLineResponse> lines) {
    }

    public record AdjustmentRequest(
            @NotNull Long batchLineId,
            @NotNull InventoryHolderType holderType,
            Long sellerId,
            Long allocationLineId,
            @NotNull InventoryAdjustmentType adjustmentType,
            @NotNull AdjustmentDirection direction,
            @Positive int quantity,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record AdjustmentResponse(
            Long id,
            Long batchLineId,
            InventoryHolderType holderType,
            Long sellerId,
            Long allocationLineId,
            InventoryAdjustmentType adjustmentType,
            AdjustmentDirection direction,
            int quantity,
            String reason,
            InventoryAdjustmentStatus status) {
    }
}
