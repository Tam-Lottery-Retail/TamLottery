package com.mtriet.tamlottery;

import com.mtriet.tamlottery.cash.api.CashDtos;
import com.mtriet.tamlottery.cash.application.CashService;
import com.mtriet.tamlottery.cash.domain.CashDirection;
import com.mtriet.tamlottery.cash.domain.CashTransactionStatus;
import com.mtriet.tamlottery.cash.domain.CashTransactionType;
import com.mtriet.tamlottery.cash.domain.PaymentMethod;
import com.mtriet.tamlottery.common.exception.BusinessException;
import com.mtriet.tamlottery.common.exception.ErrorCode;
import com.mtriet.tamlottery.identity.api.AuthDtos;
import com.mtriet.tamlottery.identity.api.IdentityDtos;
import com.mtriet.tamlottery.identity.application.AuthService;
import com.mtriet.tamlottery.identity.application.IdentityService;
import com.mtriet.tamlottery.identity.domain.Role;
import com.mtriet.tamlottery.identity.domain.Seller;
import com.mtriet.tamlottery.identity.domain.SellerStatus;
import com.mtriet.tamlottery.identity.domain.Store;
import com.mtriet.tamlottery.identity.domain.UserAccount;
import com.mtriet.tamlottery.identity.domain.UserStatus;
import com.mtriet.tamlottery.identity.infrastructure.SellerRepository;
import com.mtriet.tamlottery.identity.infrastructure.StoreRepository;
import com.mtriet.tamlottery.identity.infrastructure.UserAccountRepository;
import com.mtriet.tamlottery.inventory.api.InventoryDtos;
import com.mtriet.tamlottery.inventory.application.InventoryService;
import com.mtriet.tamlottery.inventory.domain.AdjustmentDirection;
import com.mtriet.tamlottery.inventory.domain.InventoryAdjustmentType;
import com.mtriet.tamlottery.inventory.domain.InventoryHolderType;
import com.mtriet.tamlottery.inventory.domain.LotteryBatchStatus;
import com.mtriet.tamlottery.inventory.domain.TicketAllocationStatus;
import com.mtriet.tamlottery.inventory.domain.TicketReturnType;
import com.mtriet.tamlottery.masterdata.api.MasterDataDtos;
import com.mtriet.tamlottery.masterdata.application.MasterDataService;
import com.mtriet.tamlottery.masterdata.domain.LotteryRegion;
import com.mtriet.tamlottery.reconciliation.api.ReconciliationDtos;
import com.mtriet.tamlottery.reconciliation.application.ReconciliationService;
import com.mtriet.tamlottery.reconciliation.domain.ReconciliationStatus;
import com.mtriet.tamlottery.reconciliation.domain.SalesScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TamLotteryBackendApplicationIT {

    private static final String TEST_PASSWORD = "StrongPass123!";
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 17);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("tam_lottery_test")
            .withUsername("tam_lottery")
            .withPassword("tam_lottery");

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private MasterDataService masterDataService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private CashService cashService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void contextLoadsWithFlywayAndHibernateSchemaValidation() {
        assertThat(storeRepository).isNotNull();
    }

    @Test
    void recordsAndReusesDrawReferenceWhenReceivingBatch() {
        Actor owner = createActor(Role.OWNER);
        authenticate(owner, null);
        int sequence = SEQUENCE.incrementAndGet();
        MasterDataDtos.AgencyResponse agency = masterDataService.createAgency(new MasterDataDtos.CreateAgencyRequest(
                "AG-AUTO-" + sequence, "Đại lý tự động " + sequence, null, null));
        InventoryDtos.BatchLineRequest line = new InventoryDtos.BatchLineRequest(
                "XSKT TP.HCM", "HCM", LotteryRegion.SOUTH, BUSINESS_DATE,
                BUSINESS_DATE.atTime(15, 30).toInstant(ZoneOffset.UTC), 50, 9_000, 10_000, null, null);

        InventoryDtos.BatchResponse batch = inventoryService.createBatch(new InventoryDtos.BatchRequest(
                agency.id(), "AUTO-" + sequence, BUSINESS_DATE, Instant.now(), null, List.of(line, line)));

        assertThat(batch.lines()).hasSize(2);
        assertThat(batch.lines().get(0).drawId()).isEqualTo(batch.lines().get(1).drawId());
        assertThat(masterDataService.listDraws(PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void rotatesRefreshTokensAndRejectsReplay() {
        Actor owner = createActor(Role.OWNER);

        AuthDtos.TokenResponse first = authService.login(owner.user().getUsername(), TEST_PASSWORD);
        AuthDtos.TokenResponse rotated = authService.refresh(first.refreshToken());

        assertThat(first.accessToken()).isNotBlank();
        assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThatThrownBy(() -> authService.refresh(first.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void rejectsDuplicateUsernameIgnoringCaseAndWhitespace() {
        Actor owner = createActor(Role.OWNER);
        authenticate(owner, null);
        int sequence = SEQUENCE.incrementAndGet();
        String username = "cashier-" + sequence;

        identityService.createUser(new IdentityDtos.CreateUserRequest(
                username, TEST_PASSWORD, "Thu ngân", Set.of(Role.SELLER)));

        assertThatThrownBy(() -> identityService.createUser(new IdentityDtos.CreateUserRequest(
                "  " + username.toUpperCase() + "  ", TEST_PASSWORD, "Trùng tên", Set.of(Role.SELLER))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                });
    }

    @Test
    void createsSellerAndLoginAccountAtomically() {
        Actor owner = createActor(Role.OWNER);
        authenticate(owner, null);
        int sequence = SEQUENCE.incrementAndGet();
        String username = "seller-login-" + sequence;
        String sellerCode = "NV-LOGIN-" + sequence;

        IdentityDtos.SellerResponse seller = identityService.createSeller(new IdentityDtos.CreateSellerRequest(
                null,
                sellerCode,
                "Người bán có tài khoản",
                "0909123456",
                new IdentityDtos.CreateSellerLoginRequest(username, TEST_PASSWORD)));

        UserAccount account = userRepository.findByUsernameIgnoreCase(username).orElseThrow();
        assertThat(seller.userId()).isEqualTo(account.getId());
        assertThat(account.getRoles()).containsExactly(Role.SELLER);
        assertThat(sellerRepository.findByUserId(account.getId()).orElseThrow().getId()).isEqualTo(seller.id());
        assertThat(authService.login(username, TEST_PASSWORD).accessToken()).isNotBlank();

        long sellerCount = sellerRepository.count();
        assertThatThrownBy(() -> identityService.createSeller(new IdentityDtos.CreateSellerRequest(
                null,
                sellerCode + "-ROLLBACK",
                "Không được tạo dở dang",
                null,
                new IdentityDtos.CreateSellerLoginRequest("  " + username.toUpperCase() + "  ", TEST_PASSWORD))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
        assertThat(sellerRepository.count()).isEqualTo(sellerCount);
        assertThat(sellerRepository.existsByStoreIdAndCodeIgnoreCase(
                owner.store().getId(), sellerCode + "-ROLLBACK")).isFalse();
    }

    @Test
    void managerCannotCreateSellerLoginAccount() {
        Actor manager = createActor(Role.MANAGER);
        authenticate(manager, null);
        int sequence = SEQUENCE.incrementAndGet();

        assertThatThrownBy(() -> identityService.createSeller(new IdentityDtos.CreateSellerRequest(
                null,
                "NV-MANAGER-" + sequence,
                "Người bán",
                null,
                new IdentityDtos.CreateSellerLoginRequest("manager-created-" + sequence, TEST_PASSWORD))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.getStatus().value()).isEqualTo(403);
                });
    }

    @Test
    void updatesSellerAndRejectsDuplicateCodeWithinStore() {
        Actor owner = createActor(Role.OWNER);
        authenticate(owner, null);
        int sequence = SEQUENCE.incrementAndGet();
        String firstCode = "NV-" + sequence;
        IdentityDtos.SellerResponse first = identityService.createSeller(
                new IdentityDtos.CreateSellerRequest(null, firstCode, "Người bán một", null, null));
        IdentityDtos.SellerResponse second = identityService.createSeller(
                new IdentityDtos.CreateSellerRequest(null, firstCode + "-2", "Người bán hai", null, null));

        IdentityDtos.SellerResponse updated = identityService.updateSeller(first.id(),
                new IdentityDtos.UpdateSellerRequest(null, firstCode, "Người bán đã sửa", "0909000000"));
        assertThat(updated.fullName()).isEqualTo("Người bán đã sửa");
        assertThat(updated.phone()).isEqualTo("0909000000");

        assertThatThrownBy(() -> identityService.updateSeller(second.id(),
                new IdentityDtos.UpdateSellerRequest(null, "  " + firstCode.toLowerCase() + "  ", "Bị trùng", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));

        IdentityDtos.SellerResponse inactive = identityService.changeSellerStatus(first.id(),
                new IdentityDtos.ChangeSellerStatusRequest(SellerStatus.INACTIVE));
        assertThat(inactive.status()).isEqualTo(SellerStatus.INACTIVE);
    }

    @Test
    void disabledAccountCannotLoginAndOwnerCannotDisableSelf() {
        Actor owner = createActor(Role.OWNER);
        authenticate(owner, null);
        int sequence = SEQUENCE.incrementAndGet();
        IdentityDtos.UserResponse sellerUser = identityService.createUser(new IdentityDtos.CreateUserRequest(
                "disabled-" + sequence, TEST_PASSWORD, "Tài khoản tạm ngưng", Set.of(Role.SELLER)));

        identityService.changeUserStatus(sellerUser.id(), new IdentityDtos.ChangeUserStatusRequest(UserStatus.DISABLED));
        assertThatThrownBy(() -> authService.login("disabled-" + sequence, TEST_PASSWORD))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThatThrownBy(() -> identityService.changeUserStatus(
                owner.user().getId(), new IdentityDtos.ChangeUserStatusRequest(UserStatus.DISABLED)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void enforcesAuthenticationRoleAndStoreIsolationOverHttp() throws Exception {
        Actor storeAOwner = createActor(Role.OWNER);
        Actor storeBOwner = createActor(Role.OWNER);
        authenticate(storeBOwner, null);
        InventoryScenario storeBInventory = createInventoryScenario(storeBOwner, BUSINESS_DATE.plusDays(1), 20);
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/stores/current"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/batches")
                        .with(jwtFor(storeAOwner, null, Role.SELLER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/batches/{id}", storeBInventory.batchId())
                        .with(jwtFor(storeAOwner, null, Role.OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void completesReceiveAllocateReturnLossCashAndDailyCloseFlow() {
        Actor owner = createActor(Role.OWNER);
        SellerActor sellerActor = createSellerActor(owner);
        authenticate(owner, null);
        InventoryScenario inventory = createInventoryScenario(owner, BUSINESS_DATE, 100);

        InventoryDtos.AllocationResponse allocation = inventoryService.createAllocation(new InventoryDtos.AllocationRequest(
                sellerActor.seller().getId(),
                BUSINESS_DATE,
                "Giao vé buổi sáng",
                List.of(new InventoryDtos.AllocationLineRequest(inventory.batchLineId(), 60))));
        allocation = inventoryService.issueAllocation(allocation.id());
        assertThat(allocation.status()).isEqualTo(TicketAllocationStatus.ISSUED);
        Long allocationLineId = allocation.lines().getFirst().id();

        authenticate(sellerActor.actor(), sellerActor.seller());
        InventoryDtos.ReturnResponse sellerReturn = inventoryService.createReturn(new InventoryDtos.ReturnRequest(
                TicketReturnType.SELLER_TO_STORE,
                sellerActor.seller().getId(),
                null,
                BUSINESS_DATE,
                "Vé chưa bán hết",
                List.of(new InventoryDtos.ReturnLineRequest(inventory.batchLineId(), allocationLineId, 10))));
        InventoryDtos.AdjustmentResponse loss = inventoryService.createAdjustment(new InventoryDtos.AdjustmentRequest(
                inventory.batchLineId(),
                InventoryHolderType.SELLER,
                sellerActor.seller().getId(),
                allocationLineId,
                InventoryAdjustmentType.LOST,
                AdjustmentDirection.DECREASE,
                2,
                "Thất lạc khi đi giao"));
        CashDtos.CashTransactionResponse cash = cashService.create(new CashDtos.CreateCashTransactionRequest(
                null,
                BUSINESS_DATE,
                CashDirection.IN,
                CashTransactionType.SALES_COLLECTION,
                PaymentMethod.CASH,
                480_000,
                Instant.now(),
                "Seller giao tiền"));
        assertThat(cash.sellerId()).isEqualTo(sellerActor.seller().getId());
        assertThat(cash.status()).isEqualTo(CashTransactionStatus.PENDING);

        authenticate(owner, null);
        inventoryService.confirmReturn(sellerReturn.id());
        inventoryService.approveAdjustment(loss.id());
        InventoryDtos.ReturnResponse agencyReturn = inventoryService.createReturn(new InventoryDtos.ReturnRequest(
                TicketReturnType.STORE_TO_AGENCY,
                null,
                inventory.agencyId(),
                BUSINESS_DATE,
                "Trả toàn bộ tồn cuối ngày",
                List.of(new InventoryDtos.ReturnLineRequest(inventory.batchLineId(), null, 50))));
        inventoryService.confirmReturn(agencyReturn.id());
        cashService.post(cash.id());

        ReconciliationDtos.PreviewResponse sellerPreview = reconciliationService.preview(
                BUSINESS_DATE, SalesScope.SELLER, sellerActor.seller().getId());
        assertThat(sellerPreview.totalBaseQuantity()).isEqualTo(60);
        assertThat(sellerPreview.totalReturnedQuantity()).isEqualTo(10);
        assertThat(sellerPreview.totalLostQuantity()).isEqualTo(2);
        assertThat(sellerPreview.totalSoldQuantity()).isEqualTo(48);
        assertThat(sellerPreview.expectedAmount()).isEqualTo(480_000);
        assertThat(sellerPreview.actualReceivedAmount()).isEqualTo(480_000);
        assertThat(sellerPreview.differenceAmount()).isZero();

        ReconciliationDtos.ReconciliationResponse sellerClose = reconciliationService.close(
                new ReconciliationDtos.CloseRequest(
                        BUSINESS_DATE, SalesScope.SELLER, sellerActor.seller().getId(), null));
        assertThat(sellerClose.status()).isEqualTo(ReconciliationStatus.CLOSED);

        ReconciliationDtos.PreviewResponse storePreview = reconciliationService.preview(
                BUSINESS_DATE, SalesScope.STORE, null);
        assertThat(storePreview.totalBaseQuantity()).isEqualTo(100);
        assertThat(storePreview.totalReturnedQuantity()).isEqualTo(50);
        assertThat(storePreview.totalLostQuantity()).isEqualTo(2);
        assertThat(storePreview.totalSoldQuantity()).isEqualTo(48);
        assertThat(storePreview.expectedAmount()).isEqualTo(480_000);
        assertThat(storePreview.actualReceivedAmount()).isEqualTo(480_000);

        ReconciliationDtos.ReconciliationResponse storeClose = reconciliationService.close(
                new ReconciliationDtos.CloseRequest(BUSINESS_DATE, SalesScope.STORE, null, null));
        assertThat(storeClose.status()).isEqualTo(ReconciliationStatus.CLOSED);
        assertThat(reconciliationService.getDailySales(storeClose.dailySalesId()).totalSoldQuantity()).isEqualTo(48);
        assertThat(inventoryService.getBatch(inventory.batchId()).status()).isEqualTo(LotteryBatchStatus.CLOSED);
    }

    @Test
    void serializesConcurrentAllocationsAndPreventsOverselling() throws Exception {
        Actor owner = createActor(Role.OWNER);
        SellerActor sellerActor = createSellerActor(owner);
        authenticate(owner, null);
        InventoryScenario inventory = createInventoryScenario(owner, BUSINESS_DATE.plusDays(2), 100);

        InventoryDtos.AllocationResponse first = createAllocation(sellerActor.seller(), inventory, 70);
        InventoryDtos.AllocationResponse second = createAllocation(sellerActor.seller(), inventory, 70);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<IssueOutcome> firstResult = executor.submit(
                    () -> issueConcurrently(owner, first.id(), ready, start));
            Future<IssueOutcome> secondResult = executor.submit(
                    () -> issueConcurrently(owner, second.id(), ready, start));
            ready.await();
            start.countDown();

            List<IssueOutcome> outcomes = List.of(firstResult.get(), secondResult.get());
            assertThat(outcomes).filteredOn(IssueOutcome::success).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> outcome.errorCode() == ErrorCode.INVENTORY_NOT_ENOUGH).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sellerInventoryListsContainOnlyOwnRecords() {
        Actor owner = createActor(Role.OWNER);
        SellerActor firstSeller = createSellerActor(owner);
        SellerActor secondSeller = createSellerActor(owner);
        authenticate(owner, null);
        InventoryScenario inventory = createInventoryScenario(owner, BUSINESS_DATE.plusDays(3), 100);
        InventoryDtos.AllocationResponse first = inventoryService.issueAllocation(
                createAllocation(firstSeller.seller(), inventory, 20).id());
        inventoryService.issueAllocation(createAllocation(secondSeller.seller(), inventory, 20).id());
        Long allocationLineId = first.lines().getFirst().id();

        authenticate(firstSeller.actor(), firstSeller.seller());
        inventoryService.createReturn(new InventoryDtos.ReturnRequest(
                TicketReturnType.SELLER_TO_STORE,
                firstSeller.seller().getId(),
                null,
                inventory.businessDate(),
                null,
                List.of(new InventoryDtos.ReturnLineRequest(
                        inventory.batchLineId(), allocationLineId, 2))));
        inventoryService.createAdjustment(new InventoryDtos.AdjustmentRequest(
                inventory.batchLineId(),
                InventoryHolderType.SELLER,
                firstSeller.seller().getId(),
                allocationLineId,
                InventoryAdjustmentType.LOST,
                AdjustmentDirection.DECREASE,
                1,
                "Báo mất vé"));

        assertThat(inventoryService.listAllocations(PageRequest.of(0, 10)).getContent())
                .extracting(InventoryDtos.AllocationResponse::sellerId)
                .containsExactly(firstSeller.seller().getId());
        assertThat(inventoryService.listReturns(PageRequest.of(0, 10)).getContent())
                .extracting(InventoryDtos.ReturnResponse::sellerId)
                .containsExactly(firstSeller.seller().getId());
        assertThat(inventoryService.listAdjustments(PageRequest.of(0, 10)).getContent())
                .extracting(InventoryDtos.AdjustmentResponse::sellerId)
                .containsExactly(firstSeller.seller().getId());
    }

    private InventoryDtos.AllocationResponse createAllocation(
            Seller seller, InventoryScenario inventory, int quantity) {
        return inventoryService.createAllocation(new InventoryDtos.AllocationRequest(
                seller.getId(),
                inventory.businessDate(),
                null,
                List.of(new InventoryDtos.AllocationLineRequest(inventory.batchLineId(), quantity))));
    }

    private IssueOutcome issueConcurrently(
            Actor owner, Long allocationId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        authenticate(owner, null);
        ready.countDown();
        start.await();
        try {
            inventoryService.issueAllocation(allocationId);
            return new IssueOutcome(true, null);
        } catch (BusinessException exception) {
            return new IssueOutcome(false, exception.getErrorCode());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private InventoryScenario createInventoryScenario(Actor owner, LocalDate businessDate, int quantity) {
        int sequence = SEQUENCE.incrementAndGet();
        MasterDataDtos.AgencyResponse agency = masterDataService.createAgency(new MasterDataDtos.CreateAgencyRequest(
                "AG-" + sequence, "Đại lý " + sequence, null, null));
        InventoryDtos.BatchResponse batch = inventoryService.createBatch(new InventoryDtos.BatchRequest(
                agency.id(),
                "RCPT-" + sequence,
                businessDate,
                Instant.now(),
                null,
                List.of(new InventoryDtos.BatchLineRequest(
                        "Công ty xổ số " + sequence,
                        "P" + sequence,
                        LotteryRegion.SOUTH,
                        businessDate,
                        businessDate.atTime(16, 0).toInstant(ZoneOffset.UTC),
                        quantity,
                        9_000,
                        10_000,
                        null,
                        null))));
        batch = inventoryService.confirmBatch(batch.id());
        return new InventoryScenario(
                businessDate, agency.id(), batch.id(), batch.lines().getFirst().id());
    }

    private Actor createActor(Role... roles) {
        int sequence = SEQUENCE.incrementAndGet();
        Store store = storeRepository.save(new Store("STORE-" + sequence, "Cửa hàng " + sequence));
        UserAccount user = userRepository.save(new UserAccount(
                store,
                "user-" + sequence,
                passwordEncoder.encode(TEST_PASSWORD),
                "User " + sequence,
                Set.copyOf(Arrays.asList(roles))));
        return new Actor(store, user);
    }

    private SellerActor createSellerActor(Actor owner) {
        int sequence = SEQUENCE.incrementAndGet();
        UserAccount user = userRepository.save(new UserAccount(
                owner.store(),
                "seller-" + sequence,
                passwordEncoder.encode(TEST_PASSWORD),
                "Seller " + sequence,
                Set.of(Role.SELLER)));
        Seller seller = sellerRepository.save(new Seller(
                owner.store(), user, "SELLER-" + sequence, "Seller " + sequence, null));
        return new SellerActor(new Actor(owner.store(), user), seller);
    }

    private void authenticate(Actor actor, Seller seller) {
        List<String> roles = actor.user().getRoles().stream().map(Role::name).toList();
        Jwt.Builder jwt = Jwt.withTokenValue("test-token-" + actor.user().getId())
                .header("alg", "none")
                .subject(actor.user().getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("storeId", actor.store().getId())
                .claim("roles", roles);
        if (seller != null) {
            jwt.claim("sellerId", seller.getId());
        }
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build(), authorities));
    }

    private RequestPostProcessor jwtFor(Actor actor, Seller seller, Role... roles) {
        List<String> roleNames = Arrays.stream(roles).map(Role::name).toList();
        return jwt()
                .jwt(builder -> {
                    builder.subject(actor.user().getId().toString())
                            .claim("storeId", actor.store().getId())
                            .claim("roles", roleNames);
                    if (seller != null) {
                        builder.claim("sellerId", seller.getId());
                    }
                })
                .authorities(roleNames.stream()
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                        .toList());
    }

    private record Actor(Store store, UserAccount user) {
    }

    private record SellerActor(Actor actor, Seller seller) {
    }

    private record InventoryScenario(LocalDate businessDate, Long agencyId, Long batchId, Long batchLineId) {
    }

    private record IssueOutcome(boolean success, ErrorCode errorCode) {
    }
}
