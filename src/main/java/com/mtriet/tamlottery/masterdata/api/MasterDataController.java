package com.mtriet.tamlottery.masterdata.api;

import com.mtriet.tamlottery.masterdata.application.MasterDataService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @GetMapping("/agencies")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Page<MasterDataDtos.AgencyResponse> listAgencies(Pageable pageable) {
        return masterDataService.listAgencies(pageable);
    }

    @PostMapping("/agencies")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    MasterDataDtos.AgencyResponse createAgency(@Valid @RequestBody MasterDataDtos.CreateAgencyRequest request) {
        return masterDataService.createAgency(request);
    }

    @PatchMapping("/agencies/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    MasterDataDtos.AgencyResponse changeAgencyStatus(@PathVariable Long id,
                                                     @RequestBody MasterDataDtos.ChangeAgencyStatusRequest request) {
        return masterDataService.changeAgencyStatus(id, request);
    }

    @GetMapping("/draws")
    Page<MasterDataDtos.LotteryDrawResponse> listDraws(Pageable pageable) {
        return masterDataService.listDraws(pageable);
    }

    @PostMapping("/draws")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    MasterDataDtos.LotteryDrawResponse createDraw(@Valid @RequestBody MasterDataDtos.CreateLotteryDrawRequest request) {
        return masterDataService.createDraw(request);
    }

    @PatchMapping("/draws/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    MasterDataDtos.LotteryDrawResponse changeDrawStatus(@PathVariable Long id,
                                                        @Valid @RequestBody MasterDataDtos.ChangeLotteryDrawStatusRequest request) {
        return masterDataService.changeDrawStatus(id, request);
    }
}

