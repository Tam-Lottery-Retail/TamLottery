package com.mtriet.tamlottery.cash.api;

import com.mtriet.tamlottery.cash.application.CashService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cash-transactions")
public class CashController {

    private final CashService cashService;

    public CashController(CashService cashService) {
        this.cashService = cashService;
    }

    @GetMapping
    Page<CashDtos.CashTransactionResponse> list(Pageable pageable) {
        return cashService.list(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CashDtos.CashTransactionResponse create(@Valid @RequestBody CashDtos.CreateCashTransactionRequest request) {
        return cashService.create(request);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    CashDtos.CashTransactionResponse post(@PathVariable Long id) {
        return cashService.post(id);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    CashDtos.CashTransactionResponse voidTransaction(@PathVariable Long id) {
        return cashService.voidTransaction(id);
    }
}
