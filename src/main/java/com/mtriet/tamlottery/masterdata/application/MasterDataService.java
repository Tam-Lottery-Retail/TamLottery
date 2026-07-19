package com.mtriet.tamlottery.masterdata.application;

import com.mtriet.tamlottery.audit.application.AuditService;
import com.mtriet.tamlottery.audit.domain.AuditAction;
import com.mtriet.tamlottery.audit.domain.AuditEntityType;
import com.mtriet.tamlottery.common.exception.BusinessException;
import com.mtriet.tamlottery.common.exception.ErrorCode;
import com.mtriet.tamlottery.identity.domain.Store;
import com.mtriet.tamlottery.identity.infrastructure.StoreRepository;
import com.mtriet.tamlottery.identity.security.CurrentUserProvider;
import com.mtriet.tamlottery.masterdata.api.MasterDataDtos;
import com.mtriet.tamlottery.masterdata.domain.Agency;
import com.mtriet.tamlottery.masterdata.domain.LotteryDraw;
import com.mtriet.tamlottery.masterdata.infrastructure.AgencyRepository;
import com.mtriet.tamlottery.masterdata.infrastructure.LotteryDrawRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterDataService {

    private final AgencyRepository agencyRepository;
    private final LotteryDrawRepository drawRepository;
    private final StoreRepository storeRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    public MasterDataService(AgencyRepository agencyRepository,
                             LotteryDrawRepository drawRepository,
                             StoreRepository storeRepository,
                             CurrentUserProvider currentUserProvider,
                             AuditService auditService) {
        this.agencyRepository = agencyRepository;
        this.drawRepository = drawRepository;
        this.storeRepository = storeRepository;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
    }

    @Transactional
    public MasterDataDtos.AgencyResponse createAgency(MasterDataDtos.CreateAgencyRequest request) {
        var current = currentUserProvider.get();
        Long storeId = current.storeId();
        if (agencyRepository.existsByStoreIdAndCodeIgnoreCase(storeId, request.code())) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Agency code already exists");
        }
        Store store = storeRepository.getReferenceById(storeId);
        Agency agency = agencyRepository.save(new Agency(
                store, request.code(), request.name(), request.contactName(), request.phone()));
        MasterDataDtos.AgencyResponse response = toAgencyResponse(agency);
        auditService.record(current, AuditAction.AGENCY_CREATED, AuditEntityType.AGENCY,
                agency.getId(), null, null, null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<MasterDataDtos.AgencyResponse> listAgencies(Pageable pageable) {
        return agencyRepository.findAllByStoreId(currentUserProvider.get().storeId(), pageable).map(this::toAgencyResponse);
    }

    @Transactional
    public MasterDataDtos.AgencyResponse changeAgencyStatus(Long id, MasterDataDtos.ChangeAgencyStatusRequest request) {
        var current = currentUserProvider.get();
        Agency agency = agencyRepository.findByIdAndStoreId(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Agency not found"));
        MasterDataDtos.AgencyResponse before = toAgencyResponse(agency);
        agency.changeActive(request.active());
        MasterDataDtos.AgencyResponse after = toAgencyResponse(agency);
        auditService.record(current, AuditAction.AGENCY_STATUS_CHANGED, AuditEntityType.AGENCY,
                agency.getId(), null, request.active() ? "ACTIVATED" : "DEACTIVATED", before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public Page<MasterDataDtos.LotteryDrawResponse> listDraws(Pageable pageable) {
        return drawRepository.findAllByStoreId(currentUserProvider.get().storeId(), pageable).map(this::toDrawResponse);
    }

    private MasterDataDtos.AgencyResponse toAgencyResponse(Agency agency) {
        return new MasterDataDtos.AgencyResponse(
                agency.getId(), agency.getCode(), agency.getName(), agency.getContactName(), agency.getPhone(), agency.isActive());
    }

    private MasterDataDtos.LotteryDrawResponse toDrawResponse(LotteryDraw draw) {
        return new MasterDataDtos.LotteryDrawResponse(
                draw.getId(), draw.getIssuerName(), draw.getProvinceCode(), draw.getRegion(), draw.getDrawDate(),
                draw.getReturnCutoffAt(), draw.getStatus());
    }
}
