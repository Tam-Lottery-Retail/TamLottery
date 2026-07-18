package com.mtriet.tamlottery.inventory.application;

import com.mtriet.tamlottery.common.exception.BusinessException;
import com.mtriet.tamlottery.common.exception.ErrorCode;
import com.mtriet.tamlottery.identity.domain.Role;
import com.mtriet.tamlottery.identity.domain.Seller;
import com.mtriet.tamlottery.identity.domain.SellerStatus;
import com.mtriet.tamlottery.identity.domain.Store;
import com.mtriet.tamlottery.identity.infrastructure.SellerRepository;
import com.mtriet.tamlottery.identity.infrastructure.StoreRepository;
import com.mtriet.tamlottery.identity.security.CurrentUser;
import com.mtriet.tamlottery.identity.security.CurrentUserProvider;
import com.mtriet.tamlottery.inventory.api.InventoryDtos;
import com.mtriet.tamlottery.inventory.domain.AdjustmentDirection;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustment;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustmentStatus;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustmentType;
import com.mtriet.tamlottery.inventory.domain.InventoryHolderType;
import com.mtriet.tamlottery.inventory.domain.LotteryBatch;
import com.mtriet.tamlottery.inventory.domain.LotteryBatchLine;
import com.mtriet.tamlottery.inventory.domain.LotteryBatchStatus;
import com.mtriet.tamlottery.inventory.domain.TicketAllocation;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationLine;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationStatus;
import com.mtriet.tamlottery.inventory.domain.TicketReturn;
import com.mtriet.tamlottery.inventory.domain.TicketReturnLine;
import com.mtriet.tamlottery.inventory.domain.TicketReturnStatus;
import com.mtriet.tamlottery.inventory.domain.TicketReturnType;
import com.mtriet.tamlottery.inventory.infrastructure.InventoryAdjustmentRepository;
import com.mtriet.tamlottery.inventory.infrastructure.LotteryBatchLineRepository;
import com.mtriet.tamlottery.inventory.infrastructure.LotteryBatchRepository;
import com.mtriet.tamlottery.inventory.infrastructure.TicketAllocationLineRepository;
import com.mtriet.tamlottery.inventory.infrastructure.TicketAllocationRepository;
import com.mtriet.tamlottery.inventory.infrastructure.TicketReturnRepository;
import com.mtriet.tamlottery.masterdata.domain.Agency;
import com.mtriet.tamlottery.masterdata.domain.LotteryDraw;
import com.mtriet.tamlottery.masterdata.domain.LotteryDrawStatus;
import com.mtriet.tamlottery.masterdata.infrastructure.AgencyRepository;
import com.mtriet.tamlottery.masterdata.infrastructure.LotteryDrawRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class InventoryService {

    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final StoreRepository storeRepository;
    private final SellerRepository sellerRepository;
    private final AgencyRepository agencyRepository;
    private final LotteryDrawRepository drawRepository;
    private final LotteryBatchRepository batchRepository;
    private final LotteryBatchLineRepository batchLineRepository;
    private final TicketAllocationRepository allocationRepository;
    private final TicketAllocationLineRepository allocationLineRepository;
    private final TicketReturnRepository returnRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryAvailabilityService availabilityService;
    private final CurrentUserProvider currentUserProvider;

    public InventoryService(StoreRepository storeRepository,
                            SellerRepository sellerRepository,
                            AgencyRepository agencyRepository,
                            LotteryDrawRepository drawRepository,
                            LotteryBatchRepository batchRepository,
                            LotteryBatchLineRepository batchLineRepository,
                            TicketAllocationRepository allocationRepository,
                            TicketAllocationLineRepository allocationLineRepository,
                            TicketReturnRepository returnRepository,
                            InventoryAdjustmentRepository adjustmentRepository,
                            InventoryAvailabilityService availabilityService,
                            CurrentUserProvider currentUserProvider) {
        this.storeRepository = storeRepository;
        this.sellerRepository = sellerRepository;
        this.agencyRepository = agencyRepository;
        this.drawRepository = drawRepository;
        this.batchRepository = batchRepository;
        this.batchLineRepository = batchLineRepository;
        this.allocationRepository = allocationRepository;
        this.allocationLineRepository = allocationLineRepository;
        this.returnRepository = returnRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.availabilityService = availabilityService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public InventoryDtos.BatchResponse createBatch(InventoryDtos.BatchRequest request) {
        Long storeId = currentUserProvider.get().storeId();
        if (batchRepository.existsByStoreIdAndReceiptCodeIgnoreCase(storeId, request.receiptCode())) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Receipt code already exists");
        }
        Store store = storeRepository.getReferenceById(storeId);
        Agency agency = requireAgency(request.agencyId(), storeId);
        LotteryBatch batch = new LotteryBatch(
                store, agency, request.receiptCode(), request.businessDate(), request.receivedAt(), request.note());
        buildBatchLines(request.lines(), store, request.receivedAt()).forEach(batch::addLine);
        return toBatchResponse(batchRepository.save(batch));
    }

    @Transactional
    public InventoryDtos.BatchResponse updateBatch(Long id, InventoryDtos.BatchRequest request) {
        Long storeId = currentUserProvider.get().storeId();
        LotteryBatch batch = batchRepository.findForUpdate(id, storeId)
                .orElseThrow(() -> BusinessException.notFound("Lottery batch not found"));
        requireState(batch.getStatus() == LotteryBatchStatus.DRAFT, "Only a draft batch can be updated");
        if (batchRepository.existsByStoreIdAndReceiptCodeIgnoreCaseAndIdNot(storeId, request.receiptCode(), id)) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Receipt code already exists");
        }
        batch.replaceDetails(
                requireAgency(request.agencyId(), storeId),
                request.receiptCode(),
                request.businessDate(),
                request.receivedAt(),
                request.note(),
                buildBatchLines(request.lines(), batch.getStore(), request.receivedAt()));
        return toBatchResponse(batch);
    }

    @Transactional
    public InventoryDtos.BatchResponse confirmBatch(Long id) {
        CurrentUser current = currentUserProvider.get();
        LotteryBatch batch = batchRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Lottery batch not found"));
        requireState(batch.getStatus() == LotteryBatchStatus.DRAFT, "Only a draft batch can be confirmed");
        if (batch.getLines().isEmpty()) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "Batch must contain at least one line");
        }
        batch.confirm(current.userId(), Instant.now());
        return toBatchResponse(batch);
    }

    @Transactional
    public void cancelBatch(Long id) {
        LotteryBatch batch = batchRepository.findForUpdate(id, currentUserProvider.get().storeId())
                .orElseThrow(() -> BusinessException.notFound("Lottery batch not found"));
        requireState(batch.getStatus() == LotteryBatchStatus.DRAFT, "Only a draft batch can be cancelled");
        batch.cancel();
    }

    @Transactional(readOnly = true)
    public Page<InventoryDtos.BatchResponse> listBatches(Pageable pageable) {
        return batchRepository.findAllByStoreId(currentUserProvider.get().storeId(), pageable).map(this::toBatchResponse);
    }

    @Transactional(readOnly = true)
    public InventoryDtos.BatchResponse getBatch(Long id) {
        LotteryBatch batch = batchRepository.findByIdAndStoreId(id, currentUserProvider.get().storeId())
                .orElseThrow(() -> BusinessException.notFound("Lottery batch not found"));
        return toBatchResponse(batch);
    }

    @Transactional
    public InventoryDtos.AllocationResponse createAllocation(InventoryDtos.AllocationRequest request) {
        Long storeId = currentUserProvider.get().storeId();
        Seller seller = requireSeller(request.sellerId(), storeId);
        Store store = storeRepository.getReferenceById(storeId);
        TicketAllocation allocation = new TicketAllocation(store, seller, request.businessDate(), request.note());
        ensureUnique(request.lines().stream().map(InventoryDtos.AllocationLineRequest::batchLineId).toList(), "batchLineId");
        for (InventoryDtos.AllocationLineRequest lineRequest : request.lines()) {
            LotteryBatchLine line = requireConfirmedBatchLine(lineRequest.batchLineId(), storeId);
            long available = availabilityService.storeAvailable(line);
            if (lineRequest.quantity() > available) {
                throw BusinessException.invalid(
                        ErrorCode.INVENTORY_NOT_ENOUGH,
                        "Batch line %d has only %d tickets available".formatted(line.getId(), available));
            }
            allocation.addLine(new TicketAllocationLine(line, lineRequest.quantity()));
        }
        return toAllocationResponse(allocationRepository.save(allocation));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InventoryDtos.AllocationResponse issueAllocation(Long id) {
        CurrentUser current = currentUserProvider.get();
        TicketAllocation allocation = allocationRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Ticket allocation not found"));
        requireState(allocation.getStatus() == TicketAllocationStatus.DRAFT, "Only a draft allocation can be issued");
        if (allocation.getLines().isEmpty()) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "Allocation must contain at least one line");
        }
        List<Long> ids = allocation.getLines().stream().map(line -> line.getBatchLine().getId()).sorted().toList();
        List<LotteryBatchLine> locked = batchLineRepository.findAllForUpdate(ids, current.storeId());
        if (locked.size() != ids.size()) {
            throw BusinessException.notFound("One or more batch lines were not found");
        }
        for (TicketAllocationLine line : allocation.getLines()) {
            if (line.getBatchLine().getBatch().getStatus() != LotteryBatchStatus.CONFIRMED) {
                throw BusinessException.conflict(ErrorCode.INVALID_STATE, "Batch was closed before allocation was issued");
            }
            long available = availabilityService.storeAvailable(line.getBatchLine());
            if (line.getQuantityAllocated() > available) {
                throw BusinessException.invalid(
                        ErrorCode.INVENTORY_NOT_ENOUGH,
                        "Batch line %d has only %d tickets available".formatted(line.getBatchLine().getId(), available));
            }
        }
        allocation.issue(current.userId(), Instant.now());
        return toAllocationResponse(allocation);
    }

    @Transactional
    public void cancelAllocation(Long id) {
        TicketAllocation allocation = allocationRepository.findForUpdate(id, currentUserProvider.get().storeId())
                .orElseThrow(() -> BusinessException.notFound("Ticket allocation not found"));
        requireState(allocation.getStatus() == TicketAllocationStatus.DRAFT, "Only a draft allocation can be cancelled");
        allocation.cancel();
    }

    @Transactional(readOnly = true)
    public Page<InventoryDtos.AllocationResponse> listAllocations(Pageable pageable) {
        CurrentUser current = currentUserProvider.get();
        requireLinkedSeller(current);
        return (sellerOnly(current)
                ? allocationRepository.findAllByStoreIdAndSellerId(current.storeId(), current.sellerId(), pageable)
                : allocationRepository.findAllByStoreId(current.storeId(), pageable))
                .map(this::toAllocationResponse);
    }

    @Transactional(readOnly = true)
    public InventoryDtos.AllocationResponse getAllocation(Long id) {
        CurrentUser current = currentUserProvider.get();
        TicketAllocation allocation = allocationRepository.findByIdAndStoreId(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Ticket allocation not found"));
        if (sellerOnly(current) && !allocation.getSeller().getId().equals(current.sellerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, org.springframework.http.HttpStatus.FORBIDDEN, "Seller cannot view another seller's allocation");
        }
        return toAllocationResponse(allocation);
    }

    @Transactional
    public InventoryDtos.ReturnResponse createReturn(InventoryDtos.ReturnRequest request) {
        CurrentUser current = currentUserProvider.get();
        validateReturnParties(request, current);
        Store store = storeRepository.getReferenceById(current.storeId());
        Seller seller = request.sellerId() == null ? null : requireSeller(request.sellerId(), current.storeId());
        Agency agency = request.agencyId() == null ? null : requireAgency(request.agencyId(), current.storeId());
        TicketReturn ticketReturn = new TicketReturn(
                store, request.returnType(), seller, agency, request.businessDate(), request.note());
        Instant now = Instant.now();
        ensureUnique(request.lines().stream().map(line -> line.batchLineId() + ":" + line.allocationLineId()).toList(), "return line");

        for (InventoryDtos.ReturnLineRequest lineRequest : request.lines()) {
            LotteryBatchLine batchLine = requireConfirmedBatchLine(lineRequest.batchLineId(), current.storeId());
            TicketAllocationLine allocationLine = null;
            if (request.returnType() == TicketReturnType.SELLER_TO_STORE) {
                allocationLine = allocationLineRepository.findByIdAndAllocationStoreId(lineRequest.allocationLineId(), current.storeId())
                        .orElseThrow(() -> BusinessException.notFound("Allocation line not found"));
                validateSellerReturnLine(seller, batchLine, allocationLine);
            } else {
                if (!batchLine.getBatch().getAgency().getId().equals(agency.getId())) {
                    throw BusinessException.invalid(ErrorCode.INVALID_RETURN_PARTIES, "Agency does not own the selected batch line");
                }
                requireAgencyReturnOpen(batchLine, now);
            }
            ticketReturn.addLine(new TicketReturnLine(batchLine, allocationLine, lineRequest.quantity()));
        }
        return toReturnResponse(returnRepository.save(ticketReturn));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InventoryDtos.ReturnResponse confirmReturn(Long id) {
        CurrentUser current = currentUserProvider.get();
        TicketReturn ticketReturn = returnRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Ticket return not found"));
        requireState(ticketReturn.getStatus() == TicketReturnStatus.DRAFT, "Only a draft return can be confirmed");
        Instant confirmedAt = Instant.now();

        List<Long> batchLineIds = ticketReturn.getLines().stream().map(line -> line.getBatchLine().getId()).distinct().sorted().toList();
        batchLineRepository.findAllForUpdate(batchLineIds, current.storeId());

        if (ticketReturn.getReturnType() == TicketReturnType.SELLER_TO_STORE) {
            List<Long> allocationLineIds = ticketReturn.getLines().stream()
                    .map(line -> line.getAllocationLine().getId()).distinct().sorted().toList();
            allocationLineRepository.findAllForUpdate(allocationLineIds, current.storeId());
            for (TicketReturnLine line : ticketReturn.getLines()) {
                if (line.getAllocationLine().getAllocation().getStatus() != TicketAllocationStatus.ISSUED) {
                    throw BusinessException.conflict(ErrorCode.INVALID_STATE, "Allocation is no longer open for returns");
                }
                long available = availabilityService.sellerAvailable(line.getAllocationLine());
                if (line.getQuantity() > available) {
                    throw BusinessException.invalid(
                            ErrorCode.RETURN_EXCEEDS_ALLOCATION,
                            "Allocation line %d has only %d unresolved tickets".formatted(line.getAllocationLine().getId(), available));
                }
            }
        } else {
            for (TicketReturnLine line : ticketReturn.getLines()) {
                if (line.getBatchLine().getBatch().getStatus() != LotteryBatchStatus.CONFIRMED) {
                    throw BusinessException.conflict(ErrorCode.INVALID_STATE, "Batch is no longer open for agency returns");
                }
                requireAgencyReturnOpen(line.getBatchLine(), confirmedAt);
                long available = availabilityService.storeAvailable(line.getBatchLine());
                if (line.getQuantity() > available) {
                    throw BusinessException.invalid(
                            ErrorCode.INVENTORY_NOT_ENOUGH,
                            "Batch line %d has only %d tickets available for agency return".formatted(line.getBatchLine().getId(), available));
                }
            }
        }
        ticketReturn.confirm(current.userId(), confirmedAt);
        return toReturnResponse(ticketReturn);
    }

    @Transactional
    public void cancelReturn(Long id) {
        CurrentUser current = currentUserProvider.get();
        TicketReturn ticketReturn = returnRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Ticket return not found"));
        if (sellerOnly(current) && (ticketReturn.getSeller() == null || !ticketReturn.getSeller().getId().equals(current.sellerId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, org.springframework.http.HttpStatus.FORBIDDEN, "Seller cannot cancel this return");
        }
        requireState(ticketReturn.getStatus() == TicketReturnStatus.DRAFT, "Only a draft return can be cancelled");
        ticketReturn.cancel();
    }

    @Transactional(readOnly = true)
    public Page<InventoryDtos.ReturnResponse> listReturns(Pageable pageable) {
        CurrentUser current = currentUserProvider.get();
        requireLinkedSeller(current);
        return (sellerOnly(current)
                ? returnRepository.findAllByStoreIdAndSellerId(current.storeId(), current.sellerId(), pageable)
                : returnRepository.findAllByStoreId(current.storeId(), pageable))
                .map(this::toReturnResponse);
    }

    @Transactional(readOnly = true)
    public InventoryDtos.ReturnResponse getReturn(Long id) {
        CurrentUser current = currentUserProvider.get();
        TicketReturn ticketReturn = returnRepository.findByIdAndStoreId(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Ticket return not found"));
        if (sellerOnly(current) && (ticketReturn.getSeller() == null || !ticketReturn.getSeller().getId().equals(current.sellerId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, org.springframework.http.HttpStatus.FORBIDDEN, "Seller cannot view this return");
        }
        return toReturnResponse(ticketReturn);
    }

    @Transactional
    public InventoryDtos.AdjustmentResponse createAdjustment(InventoryDtos.AdjustmentRequest request) {
        CurrentUser current = currentUserProvider.get();
        validateAdjustmentType(request);
        Store store = storeRepository.getReferenceById(current.storeId());
        LotteryBatchLine batchLine = requireConfirmedBatchLine(request.batchLineId(), current.storeId());
        Seller seller = null;
        TicketAllocationLine allocationLine = null;

        if (request.holderType() == InventoryHolderType.SELLER) {
            if (request.sellerId() == null || request.allocationLineId() == null) {
                throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "Seller holder requires sellerId and allocationLineId");
            }
            seller = requireSeller(request.sellerId(), current.storeId());
            allocationLine = allocationLineRepository.findByIdAndAllocationStoreId(request.allocationLineId(), current.storeId())
                    .orElseThrow(() -> BusinessException.notFound("Allocation line not found"));
            validateSellerReturnLine(seller, batchLine, allocationLine);
            if (sellerOnly(current) && !seller.getId().equals(current.sellerId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, org.springframework.http.HttpStatus.FORBIDDEN, "Seller cannot adjust another seller's inventory");
            }
        } else if (request.sellerId() != null || request.allocationLineId() != null) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "Store holder must not contain sellerId or allocationLineId");
        } else if (sellerOnly(current)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, org.springframework.http.HttpStatus.FORBIDDEN, "Seller cannot adjust store inventory");
        }

        InventoryAdjustment adjustment = new InventoryAdjustment(
                store,
                batchLine,
                request.holderType(),
                seller,
                allocationLine,
                request.adjustmentType(),
                request.direction(),
                request.quantity(),
                request.reason(),
                current.userId());
        return toAdjustmentResponse(adjustmentRepository.save(adjustment));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InventoryDtos.AdjustmentResponse approveAdjustment(Long id) {
        CurrentUser current = currentUserProvider.get();
        InventoryAdjustment adjustment = adjustmentRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Inventory adjustment not found"));
        requireState(adjustment.getStatus() == InventoryAdjustmentStatus.PENDING, "Only a pending adjustment can be approved");

        batchLineRepository.findAllForUpdate(List.of(adjustment.getBatchLine().getId()), current.storeId());
        long available;
        long netLoss;
        if (adjustment.getHolderType() == InventoryHolderType.STORE) {
            available = availabilityService.storeAvailable(adjustment.getBatchLine());
            netLoss = availabilityService.netAdjustment(adjustment.getBatchLine().getId(), InventoryHolderType.STORE);
        } else {
            allocationLineRepository.findAllForUpdate(List.of(adjustment.getAllocationLine().getId()), current.storeId());
            available = availabilityService.sellerAvailable(adjustment.getAllocationLine());
            netLoss = availabilityService.netAdjustment(adjustment.getAllocationLine().getId());
        }
        if (adjustment.getDirection() == AdjustmentDirection.DECREASE && adjustment.getQuantity() > available) {
            throw BusinessException.invalid(ErrorCode.ADJUSTMENT_EXCEEDS_INVENTORY, "Adjustment exceeds available inventory");
        }
        if (adjustment.getAdjustmentType() == InventoryAdjustmentType.FOUND && adjustment.getQuantity() > netLoss) {
            throw BusinessException.invalid(ErrorCode.ADJUSTMENT_EXCEEDS_INVENTORY, "Found quantity exceeds previously lost quantity");
        }
        adjustment.approve(current.userId(), Instant.now());
        return toAdjustmentResponse(adjustment);
    }

    @Transactional
    public InventoryDtos.AdjustmentResponse rejectAdjustment(Long id) {
        CurrentUser current = currentUserProvider.get();
        InventoryAdjustment adjustment = adjustmentRepository.findForUpdate(id, current.storeId())
                .orElseThrow(() -> BusinessException.notFound("Inventory adjustment not found"));
        requireState(adjustment.getStatus() == InventoryAdjustmentStatus.PENDING, "Only a pending adjustment can be rejected");
        adjustment.reject(current.userId(), Instant.now());
        return toAdjustmentResponse(adjustment);
    }

    @Transactional(readOnly = true)
    public Page<InventoryDtos.AdjustmentResponse> listAdjustments(Pageable pageable) {
        CurrentUser current = currentUserProvider.get();
        requireLinkedSeller(current);
        return (sellerOnly(current)
                ? adjustmentRepository.findAllByStoreIdAndSellerId(current.storeId(), current.sellerId(), pageable)
                : adjustmentRepository.findAllByStoreId(current.storeId(), pageable))
                .map(this::toAdjustmentResponse);
    }

    private List<LotteryBatchLine> buildBatchLines(List<InventoryDtos.BatchLineRequest> requests,
                                                    Store store,
                                                    Instant receivedAt) {
        List<LotteryBatchLine> lines = new ArrayList<>();
        for (InventoryDtos.BatchLineRequest request : requests) {
            validateReturnCutoff(receivedAt, request);
            LotteryDraw draw = drawRepository.findByStoreIdAndProvinceCodeIgnoreCaseAndDrawDate(
                            store.getId(), request.provinceCode(), request.drawDate())
                    .orElseGet(() -> drawRepository.save(new LotteryDraw(
                            store,
                            request.issuerName(),
                            request.provinceCode(),
                            request.region(),
                            request.drawDate(),
                            request.returnCutoffAt())));
            if (draw.getStatus() != LotteryDrawStatus.OPEN) {
                throw BusinessException.invalid(ErrorCode.INVALID_STATE, "Lottery draw is not open");
            }
            lines.add(new LotteryBatchLine(
                    draw,
                    request.quantityReceived(),
                    request.unitCost(),
                    request.unitSalePrice(),
                    request.serialFrom(),
                    request.serialTo()));
        }
        return lines;
    }

    private void validateReturnCutoff(Instant receivedAt, InventoryDtos.BatchLineRequest request) {
        if (!request.returnCutoffAt().isAfter(receivedAt)) {
            throw BusinessException.invalid(
                    ErrorCode.INVALID_REQUEST,
                    "Agency return deadline must be after the batch receipt time");
        }
        if (request.returnCutoffAt().atZone(STORE_ZONE).toLocalDate().isAfter(request.drawDate())) {
            throw BusinessException.invalid(
                    ErrorCode.INVALID_REQUEST,
                    "Agency return deadline cannot be after the draw date");
        }
    }

    private void requireAgencyReturnOpen(LotteryBatchLine batchLine, Instant instant) {
        if (!batchLine.getDraw().acceptsAgencyReturnsAt(instant)) {
            throw BusinessException.conflict(
                    ErrorCode.RETURN_CUTOFF_EXPIRED,
                    "Agency return deadline has passed for %s on %s".formatted(
                            batchLine.getDraw().getProvinceCode(),
                            batchLine.getDraw().getDrawDate()));
        }
    }

    private Agency requireAgency(Long id, Long storeId) {
        Agency agency = agencyRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> BusinessException.notFound("Agency not found"));
        if (!agency.isActive()) {
            throw BusinessException.invalid(ErrorCode.INVALID_STATE, "Agency is inactive");
        }
        return agency;
    }

    private Seller requireSeller(Long id, Long storeId) {
        Seller seller = sellerRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> BusinessException.notFound("Seller not found"));
        if (seller.getStatus() != SellerStatus.ACTIVE) {
            throw BusinessException.invalid(ErrorCode.INVALID_STATE, "Seller is inactive");
        }
        return seller;
    }

    private LotteryBatchLine requireConfirmedBatchLine(Long id, Long storeId) {
        LotteryBatchLine line = batchLineRepository.findByIdAndBatchStoreId(id, storeId)
                .orElseThrow(() -> BusinessException.notFound("Lottery batch line not found"));
        if (line.getBatch().getStatus() != LotteryBatchStatus.CONFIRMED) {
            throw BusinessException.invalid(ErrorCode.INVALID_STATE, "Batch line is not available");
        }
        return line;
    }

    private void validateReturnParties(InventoryDtos.ReturnRequest request, CurrentUser current) {
        if (request.returnType() == TicketReturnType.SELLER_TO_STORE) {
            if (request.sellerId() == null || request.agencyId() != null
                    || request.lines().stream().anyMatch(line -> line.allocationLineId() == null)) {
                throw BusinessException.invalid(
                        ErrorCode.INVALID_RETURN_PARTIES,
                        "SELLER_TO_STORE requires sellerId and allocationLineId, and must not contain agencyId");
            }
            if (sellerOnly(current) && !request.sellerId().equals(current.sellerId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, org.springframework.http.HttpStatus.FORBIDDEN, "Seller cannot create another seller's return");
            }
        } else if (request.agencyId() == null || request.sellerId() != null
                || request.lines().stream().anyMatch(line -> line.allocationLineId() != null)) {
            throw BusinessException.invalid(
                    ErrorCode.INVALID_RETURN_PARTIES,
                    "STORE_TO_AGENCY requires agencyId, and must not contain sellerId or allocationLineId");
        } else if (sellerOnly(current)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, org.springframework.http.HttpStatus.FORBIDDEN, "Seller cannot return store inventory to an agency");
        }
    }

    private void validateSellerReturnLine(Seller seller, LotteryBatchLine batchLine, TicketAllocationLine allocationLine) {
        if (!allocationLine.getAllocation().getSeller().getId().equals(seller.getId())
                || !allocationLine.getBatchLine().getId().equals(batchLine.getId())
                || allocationLine.getAllocation().getStatus() != TicketAllocationStatus.ISSUED) {
            throw BusinessException.invalid(ErrorCode.INVALID_RETURN_PARTIES, "Allocation line does not belong to the seller and batch line");
        }
    }

    private void validateAdjustmentType(InventoryDtos.AdjustmentRequest request) {
        if ((request.adjustmentType() == InventoryAdjustmentType.LOST
                || request.adjustmentType() == InventoryAdjustmentType.DAMAGED)
                && request.direction() != AdjustmentDirection.DECREASE) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "LOST and DAMAGED adjustments must decrease inventory");
        }
        if (request.adjustmentType() == InventoryAdjustmentType.FOUND
                && request.direction() != AdjustmentDirection.INCREASE) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "FOUND adjustment must increase inventory");
        }
    }

    private boolean sellerOnly(CurrentUser current) {
        return current.hasRole(Role.SELLER) && !current.hasRole(Role.OWNER) && !current.hasRole(Role.MANAGER);
    }

    private void requireLinkedSeller(CurrentUser current) {
        if (sellerOnly(current) && current.sellerId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, org.springframework.http.HttpStatus.FORBIDDEN,
                    "User has no seller profile");
        }
    }

    private void requireState(boolean valid, String message) {
        if (!valid) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE, message);
        }
    }

    private <T> void ensureUnique(List<T> values, String field) {
        Set<T> unique = new HashSet<>(values);
        if (unique.size() != values.size()) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "Duplicate " + field + " in request");
        }
    }

    private InventoryDtos.BatchResponse toBatchResponse(LotteryBatch batch) {
        List<InventoryDtos.BatchLineResponse> lines = batch.getLines().stream()
                .map(line -> {
                    long storeAvailable = batch.getStatus() == LotteryBatchStatus.CONFIRMED
                            ? availabilityService.storeAvailable(line)
                            : 0;
                    return new InventoryDtos.BatchLineResponse(
                            line.getId(),
                            line.getDraw().getId(),
                            line.getDraw().getIssuerName(),
                            line.getDraw().getProvinceCode(),
                            line.getDraw().getRegion(),
                            line.getDraw().getDrawDate(),
                            line.getDraw().getReturnCutoffAt(),
                            line.getQuantityReceived(),
                            storeAvailable,
                            line.getUnitCost(),
                            line.getUnitSalePrice(),
                            line.getSerialFrom(),
                            line.getSerialTo());
                })
                .toList();
        return new InventoryDtos.BatchResponse(
                batch.getId(),
                batch.getAgency().getId(),
                batch.getAgency().getName(),
                batch.getReceiptCode(),
                batch.getBusinessDate(),
                batch.getReceivedAt(),
                batch.getStatus(),
                batch.getNote(),
                batch.getConfirmedAt(),
                lines);
    }

    private InventoryDtos.AllocationResponse toAllocationResponse(TicketAllocation allocation) {
        List<InventoryDtos.AllocationLineResponse> lines = allocation.getLines().stream()
                .map(line -> new InventoryDtos.AllocationLineResponse(
                        line.getId(),
                        line.getBatchLine().getId(),
                        line.getBatchLine().getDraw().getProvinceCode(),
                        line.getBatchLine().getDraw().getDrawDate(),
                        line.getQuantityAllocated()))
                .toList();
        return new InventoryDtos.AllocationResponse(
                allocation.getId(),
                allocation.getSeller().getId(),
                allocation.getSeller().getFullName(),
                allocation.getBusinessDate(),
                allocation.getIssuedAt(),
                allocation.getStatus(),
                allocation.getNote(),
                lines);
    }

    private InventoryDtos.ReturnResponse toReturnResponse(TicketReturn ticketReturn) {
        List<InventoryDtos.ReturnLineResponse> lines = ticketReturn.getLines().stream()
                .map(line -> new InventoryDtos.ReturnLineResponse(
                        line.getId(),
                        line.getBatchLine().getId(),
                        line.getAllocationLine() == null ? null : line.getAllocationLine().getId(),
                        line.getQuantity()))
                .toList();
        return new InventoryDtos.ReturnResponse(
                ticketReturn.getId(),
                ticketReturn.getReturnType(),
                ticketReturn.getSeller() == null ? null : ticketReturn.getSeller().getId(),
                ticketReturn.getAgency() == null ? null : ticketReturn.getAgency().getId(),
                ticketReturn.getBusinessDate(),
                ticketReturn.getReturnedAt(),
                ticketReturn.getStatus(),
                ticketReturn.getNote(),
                lines);
    }

    private InventoryDtos.AdjustmentResponse toAdjustmentResponse(InventoryAdjustment adjustment) {
        return new InventoryDtos.AdjustmentResponse(
                adjustment.getId(),
                adjustment.getBatchLine().getId(),
                adjustment.getHolderType(),
                adjustment.getSeller() == null ? null : adjustment.getSeller().getId(),
                adjustment.getAllocationLine() == null ? null : adjustment.getAllocationLine().getId(),
                adjustment.getAdjustmentType(),
                adjustment.getDirection(),
                adjustment.getQuantity(),
                adjustment.getReason(),
                adjustment.getStatus());
    }
}
