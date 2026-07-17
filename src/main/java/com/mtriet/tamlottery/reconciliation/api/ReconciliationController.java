package com.mtriet.tamlottery.reconciliation.api;

import com.mtriet.tamlottery.reconciliation.application.ReconciliationService;
import com.mtriet.tamlottery.reconciliation.domain.SalesScope;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/reconciliations/preview")
    ReconciliationDtos.PreviewResponse preview(@RequestParam LocalDate businessDate,
                                               @RequestParam SalesScope scope,
                                               @RequestParam(required = false) Long sellerId) {
        return reconciliationService.preview(businessDate, scope, sellerId);
    }

    @GetMapping("/reconciliations")
    Page<ReconciliationDtos.ReconciliationResponse> listReconciliations(Pageable pageable) {
        return reconciliationService.listReconciliations(pageable);
    }

    @PostMapping("/reconciliations/close")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    ReconciliationDtos.ReconciliationResponse close(@Valid @RequestBody ReconciliationDtos.CloseRequest request) {
        return reconciliationService.close(request);
    }

    @PostMapping("/reconciliations/{id}/approve")
    @PreAuthorize("hasRole('OWNER')")
    ReconciliationDtos.ReconciliationResponse approve(@PathVariable Long id) {
        return reconciliationService.approve(id);
    }

    @PostMapping("/reconciliations/{id}/reject")
    @PreAuthorize("hasRole('OWNER')")
    ReconciliationDtos.ReconciliationResponse reject(@PathVariable Long id) {
        return reconciliationService.reject(id);
    }

    @GetMapping("/daily-sales")
    Page<ReconciliationDtos.DailySalesResponse> listDailySales(Pageable pageable) {
        return reconciliationService.listDailySales(pageable);
    }

    @GetMapping("/daily-sales/{id}")
    ReconciliationDtos.DailySalesResponse getDailySales(@PathVariable Long id) {
        return reconciliationService.getDailySales(id);
    }
}
