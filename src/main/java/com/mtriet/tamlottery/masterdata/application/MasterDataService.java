package com.mtriet.tamlottery.masterdata.application;

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

    public MasterDataService(AgencyRepository agencyRepository,
                             LotteryDrawRepository drawRepository,
                             StoreRepository storeRepository,
                             CurrentUserProvider currentUserProvider) {
        this.agencyRepository = agencyRepository;
        this.drawRepository = drawRepository;
        this.storeRepository = storeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public MasterDataDtos.AgencyResponse createAgency(MasterDataDtos.CreateAgencyRequest request) {
        Long storeId = currentUserProvider.get().storeId();
        if (agencyRepository.existsByStoreIdAndCodeIgnoreCase(storeId, request.code())) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Agency code already exists");
        }
        Store store = storeRepository.getReferenceById(storeId);
        return toAgencyResponse(agencyRepository.save(new Agency(
                store, request.code(), request.name(), request.contactName(), request.phone())));
    }

    @Transactional(readOnly = true)
    public Page<MasterDataDtos.AgencyResponse> listAgencies(Pageable pageable) {
        return agencyRepository.findAllByStoreId(currentUserProvider.get().storeId(), pageable).map(this::toAgencyResponse);
    }

    @Transactional
    public MasterDataDtos.AgencyResponse changeAgencyStatus(Long id, MasterDataDtos.ChangeAgencyStatusRequest request) {
        Agency agency = agencyRepository.findByIdAndStoreId(id, currentUserProvider.get().storeId())
                .orElseThrow(() -> BusinessException.notFound("Agency not found"));
        agency.changeActive(request.active());
        return toAgencyResponse(agency);
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
