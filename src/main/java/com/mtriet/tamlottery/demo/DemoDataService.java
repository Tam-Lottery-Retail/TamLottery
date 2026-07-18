package com.mtriet.tamlottery.demo;

import com.mtriet.tamlottery.cash.api.CashDtos;
import com.mtriet.tamlottery.cash.application.CashService;
import com.mtriet.tamlottery.cash.domain.CashDirection;
import com.mtriet.tamlottery.cash.domain.CashTransactionType;
import com.mtriet.tamlottery.cash.domain.PaymentMethod;
import com.mtriet.tamlottery.identity.api.IdentityDtos;
import com.mtriet.tamlottery.identity.application.IdentityService;
import com.mtriet.tamlottery.identity.domain.Role;
import com.mtriet.tamlottery.identity.domain.Store;
import com.mtriet.tamlottery.identity.domain.UserAccount;
import com.mtriet.tamlottery.identity.infrastructure.StoreRepository;
import com.mtriet.tamlottery.identity.infrastructure.UserAccountRepository;
import com.mtriet.tamlottery.inventory.api.InventoryDtos;
import com.mtriet.tamlottery.inventory.application.InventoryService;
import com.mtriet.tamlottery.inventory.domain.AdjustmentDirection;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustmentType;
import com.mtriet.tamlottery.inventory.domain.InventoryHolderType;
import com.mtriet.tamlottery.inventory.domain.TicketReturnType;
import com.mtriet.tamlottery.masterdata.api.MasterDataDtos;
import com.mtriet.tamlottery.masterdata.application.MasterDataService;
import com.mtriet.tamlottery.masterdata.domain.LotteryRegion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
public class DemoDataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataService.class);
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DemoDataProperties properties;
    private final StoreRepository storeRepository;
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityService identityService;
    private final MasterDataService masterDataService;
    private final InventoryService inventoryService;
    private final CashService cashService;

    public DemoDataService(DemoDataProperties properties,
                           StoreRepository storeRepository,
                           UserAccountRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           IdentityService identityService,
                           MasterDataService masterDataService,
                           InventoryService inventoryService,
                           CashService cashService) {
        this.properties = properties;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.identityService = identityService;
        this.masterDataService = masterDataService;
        this.inventoryService = inventoryService;
        this.cashService = cashService;
    }

    @Transactional
    public void seed() {
        requireConfiguration();
        if (userRepository.existsByUsernameIgnoreCase(properties.ownerUsername())) {
            LOGGER.info("Demo data already exists for {}", properties.ownerUsername());
            return;
        }

        Store store = storeRepository.findByCodeIgnoreCase(properties.storeCode())
                .orElseGet(() -> storeRepository.save(new Store(properties.storeCode(), properties.storeName())));
        UserAccount owner = userRepository.save(new UserAccount(
                store,
                properties.ownerUsername(),
                passwordEncoder.encode(properties.ownerPassword()),
                "Demo Owner",
                Set.of(Role.OWNER)));

        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            authenticate(owner, store);
            createScenario();
            LOGGER.info("Demo data created for store {} and owner {}", store.getCode(), owner.getUsername());
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private void createScenario() {
        LocalDate businessDate = LocalDate.now(STORE_ZONE);
        Instant now = Instant.now();

        IdentityDtos.SellerResponse seller = identityService.createSeller(new IdentityDtos.CreateSellerRequest(
                null,
                "NV-DEMO",
                "Nguyễn Minh An",
                "0909000001",
                new IdentityDtos.CreateSellerLoginRequest(
                        properties.sellerUsername(), properties.sellerPassword())));
        MasterDataDtos.AgencyResponse agency = masterDataService.createAgency(
                new MasterDataDtos.CreateAgencyRequest(
                        "DL-DEMO", "Đại lý Minh Tâm", "Chị Lan", "0909000002"));

        InventoryDtos.BatchResponse batch = inventoryService.createBatch(new InventoryDtos.BatchRequest(
                agency.id(),
                "PN-DEMO-" + businessDate,
                businessDate,
                now.minusSeconds(7_200),
                "Dữ liệu mẫu cho bản demo v0.1.0",
                List.of(new InventoryDtos.BatchLineRequest(
                        "XSKT TP.HCM",
                        "HCM",
                        LotteryRegion.SOUTH,
                        businessDate,
                        now.plusSeconds(21_600),
                        150,
                        9_000,
                        10_000,
                        "000001",
                        "000150"))));
        batch = inventoryService.confirmBatch(batch.id());
        Long batchLineId = batch.lines().getFirst().id();

        InventoryDtos.AllocationResponse allocation = inventoryService.createAllocation(
                new InventoryDtos.AllocationRequest(
                        seller.id(),
                        businessDate,
                        "Giao vé demo cho seller",
                        List.of(new InventoryDtos.AllocationLineRequest(batchLineId, 80))));
        allocation = inventoryService.issueAllocation(allocation.id());
        Long allocationLineId = allocation.lines().getFirst().id();

        InventoryDtos.ReturnResponse sellerReturn = inventoryService.createReturn(
                new InventoryDtos.ReturnRequest(
                        TicketReturnType.SELLER_TO_STORE,
                        seller.id(),
                        null,
                        businessDate,
                        "Seller trả 10 vé chưa bán",
                        List.of(new InventoryDtos.ReturnLineRequest(batchLineId, allocationLineId, 10))));
        inventoryService.confirmReturn(sellerReturn.id());

        InventoryDtos.AdjustmentResponse loss = inventoryService.createAdjustment(
                new InventoryDtos.AdjustmentRequest(
                        batchLineId,
                        InventoryHolderType.SELLER,
                        seller.id(),
                        allocationLineId,
                        InventoryAdjustmentType.LOST,
                        AdjustmentDirection.DECREASE,
                        2,
                        "Hai vé bị thất lạc khi đi giao"));
        inventoryService.approveAdjustment(loss.id());

        InventoryDtos.ReturnResponse agencyReturn = inventoryService.createReturn(
                new InventoryDtos.ReturnRequest(
                        TicketReturnType.STORE_TO_AGENCY,
                        null,
                        agency.id(),
                        businessDate,
                        "Trả toàn bộ tồn cửa hàng cho đại lý",
                        List.of(new InventoryDtos.ReturnLineRequest(batchLineId, null, 80))));
        inventoryService.confirmReturn(agencyReturn.id());

        CashDtos.CashTransactionResponse cash = cashService.create(
                new CashDtos.CreateCashTransactionRequest(
                        seller.id(),
                        businessDate,
                        CashDirection.IN,
                        CashTransactionType.SALES_COLLECTION,
                        PaymentMethod.CASH,
                        680_000,
                        now,
                        "Seller giao đủ tiền cho 68 vé bán"));
        cashService.post(cash.id());
    }

    private void authenticate(UserAccount owner, Store store) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("demo-seed")
                .header("alg", "none")
                .subject(owner.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("storeId", store.getId())
                .claim("roles", List.of(Role.OWNER.name()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    private void requireConfiguration() {
        require(properties.storeCode(), "DEMO_STORE_CODE");
        require(properties.storeName(), "DEMO_STORE_NAME");
        require(properties.ownerUsername(), "DEMO_OWNER_USERNAME");
        require(properties.ownerPassword(), "DEMO_OWNER_PASSWORD");
        require(properties.sellerUsername(), "DEMO_SELLER_USERNAME");
        require(properties.sellerPassword(), "DEMO_SELLER_PASSWORD");
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when demo data is enabled");
        }
    }
}
