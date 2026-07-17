package com.mtriet.tamlottery.inventory.api;

import com.mtriet.tamlottery.inventory.application.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Page<InventoryDtos.BatchResponse> listBatches(Pageable pageable) {
        return inventoryService.listBatches(pageable);
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    InventoryDtos.BatchResponse getBatch(@PathVariable Long id) {
        return inventoryService.getBatch(id);
    }

    @PostMapping("/batches")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    InventoryDtos.BatchResponse createBatch(@Valid @RequestBody InventoryDtos.BatchRequest request) {
        return inventoryService.createBatch(request);
    }

    @PutMapping("/batches/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    InventoryDtos.BatchResponse updateBatch(@PathVariable Long id,
                                            @Valid @RequestBody InventoryDtos.BatchRequest request) {
        return inventoryService.updateBatch(id, request);
    }

    @PostMapping("/batches/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    InventoryDtos.BatchResponse confirmBatch(@PathVariable Long id) {
        return inventoryService.confirmBatch(id);
    }

    @PostMapping("/batches/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelBatch(@PathVariable Long id) {
        inventoryService.cancelBatch(id);
    }

    @GetMapping("/allocations")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Page<InventoryDtos.AllocationResponse> listAllocations(Pageable pageable) {
        return inventoryService.listAllocations(pageable);
    }

    @GetMapping("/allocations/{id}")
    InventoryDtos.AllocationResponse getAllocation(@PathVariable Long id) {
        return inventoryService.getAllocation(id);
    }

    @PostMapping("/allocations")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    InventoryDtos.AllocationResponse createAllocation(@Valid @RequestBody InventoryDtos.AllocationRequest request) {
        return inventoryService.createAllocation(request);
    }

    @PostMapping("/allocations/{id}/issue")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    InventoryDtos.AllocationResponse issueAllocation(@PathVariable Long id) {
        return inventoryService.issueAllocation(id);
    }

    @PostMapping("/allocations/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelAllocation(@PathVariable Long id) {
        inventoryService.cancelAllocation(id);
    }

    @GetMapping("/returns")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Page<InventoryDtos.ReturnResponse> listReturns(Pageable pageable) {
        return inventoryService.listReturns(pageable);
    }

    @GetMapping("/returns/{id}")
    InventoryDtos.ReturnResponse getReturn(@PathVariable Long id) {
        return inventoryService.getReturn(id);
    }

    @PostMapping("/returns")
    @ResponseStatus(HttpStatus.CREATED)
    InventoryDtos.ReturnResponse createReturn(@Valid @RequestBody InventoryDtos.ReturnRequest request) {
        return inventoryService.createReturn(request);
    }

    @PostMapping("/returns/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    InventoryDtos.ReturnResponse confirmReturn(@PathVariable Long id) {
        return inventoryService.confirmReturn(id);
    }

    @PostMapping("/returns/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelReturn(@PathVariable Long id) {
        inventoryService.cancelReturn(id);
    }

    @GetMapping("/inventory-adjustments")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Page<InventoryDtos.AdjustmentResponse> listAdjustments(Pageable pageable) {
        return inventoryService.listAdjustments(pageable);
    }

    @PostMapping("/inventory-adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    InventoryDtos.AdjustmentResponse createAdjustment(@Valid @RequestBody InventoryDtos.AdjustmentRequest request) {
        return inventoryService.createAdjustment(request);
    }

    @PostMapping("/inventory-adjustments/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    InventoryDtos.AdjustmentResponse approveAdjustment(@PathVariable Long id) {
        return inventoryService.approveAdjustment(id);
    }

    @PostMapping("/inventory-adjustments/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    InventoryDtos.AdjustmentResponse rejectAdjustment(@PathVariable Long id) {
        return inventoryService.rejectAdjustment(id);
    }
}
