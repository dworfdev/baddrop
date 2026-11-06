package com.dworf.slots_demo;

import com.dworf.slots_demo.config.AdminAuthInterceptor;
import com.dworf.slots_demo.controller.AdminController;
import com.dworf.slots_demo.dto.AdminUserOverviewResponse;
import com.dworf.slots_demo.dto.BalanceOperationRequest;
import com.dworf.slots_demo.dto.BalanceResponse;
import com.dworf.slots_demo.dto.ChangePasswordRequest;
import com.dworf.slots_demo.dto.ConfirmDepositRequest;
import com.dworf.slots_demo.dto.DepositSessionResponse;
import com.dworf.slots_demo.dto.ItemOperationRequest;
import com.dworf.slots_demo.dto.ItemsResponse;
import com.dworf.slots_demo.dto.OpenCaseResponse;
import com.dworf.slots_demo.dto.TransactionResponse;
import com.dworf.slots_demo.dto.UserCredentialsRequest;
import com.dworf.slots_demo.dto.UserResponse;
import com.dworf.slots_demo.entity.ItemType;
import com.dworf.slots_demo.entity.TransactionStatus;
import com.dworf.slots_demo.entity.TransactionType;
import com.dworf.slots_demo.service.CaseOpenerEngine;
import com.dworf.slots_demo.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full backend integration test suite.
 * Systematized into 5 functional areas:
 * <ol>
 *     <li>User Account Lifecycle (Registration, Authentication, Password Change, Logout, Account Deletion)</li>
 *     <li>Banking & Transactions (3-Step Deposit with TON Hash, Min/Max Limits, Withdrawal & Refund, Concurrency Guard)</li>
 *     <li>Case Marketplace & Math Engine (PEPE_IRON, SHIBA_INU_GOLD, DOGE_AMETHYST, Purchases, Inventory, Unboxing)</li>
 *     <li>Admin Security & Access Control (AdminController endpoints, Cookie Session with 4h TTL, Interceptor, Promo)</li>
 *     <li>High-Concurrency Payment Race Protection (Simultaneous confirmDeposit requests)</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SlotsDemoApplicationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private AdminController adminController;

    @Autowired
    private AdminAuthInterceptor adminAuthInterceptor;

    private MockMvc mockMvc;

    @Value("${slots.admin.password:admin_new}")
    private String testAdminPassword;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(adminController)
                .addInterceptors(adminAuthInterceptor)
                .build();
    }

    // =========================================================================
    // 1. USER ACCOUNT LIFECYCLE
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1.1 User registration, login verification, and initial zero balance")
    void testUserRegistrationAndLogin() {
        String username = "life_user1";
        String password = "initial_password_123";

        UserResponse created = userService.createUser(new UserCredentialsRequest(username, password));
        assertEquals(username, created.username());
        assertEquals(0, BigDecimal.ZERO.compareTo(created.balance()));
        assertTrue(created.items().isEmpty());

        // Authenticate user via getUser
        UserResponse loggedIn = userService.getUser(username, password);
        assertEquals(username, loggedIn.username());

        // Disallow duplicate user registration
        assertThrows(ResponseStatusException.class, () ->
                userService.createUser(new UserCredentialsRequest(username, "other_pass"))
        );

        // Disallow login with wrong password
        assertThrows(ResponseStatusException.class, () ->
                userService.getUser(username, "wrong_password")
        );

        // Cleanup
        userService.deleteAccount(new UserCredentialsRequest(username, password));
    }

    @Test
    @Order(2)
    @DisplayName("1.2 Password change with old password verification, logout, and account deletion")
    void testPasswordChangeLogoutAndDelete() {
        String username = "life_user2";
        String oldPass = "old_pass_123";
        String newPass = "new_pass_456";

        userService.createUser(new UserCredentialsRequest(username, oldPass));

        // Attempt password change with wrong old password -> must fail
        assertThrows(ResponseStatusException.class, () ->
                userService.changePassword(new ChangePasswordRequest(username, "wrong_old_pass", newPass))
        );

        // Change password successfully
        UserResponse updated = userService.changePassword(new ChangePasswordRequest(username, oldPass, newPass));
        assertNotNull(updated);

        // Old password no longer works
        assertThrows(ResponseStatusException.class, () ->
                userService.getUser(username, oldPass)
        );

        // New password works
        UserResponse authenticated = userService.getUser(username, newPass);
        assertEquals(username, authenticated.username());

        // Logout
        Map<String, String> logoutResult = userService.logoutUser(new UserCredentialsRequest(username, newPass));
        assertEquals("LOGGED_OUT", logoutResult.get("status"));

        // Delete account
        Map<String, String> deleteResult = userService.deleteAccount(new UserCredentialsRequest(username, newPass));
        assertEquals("DELETED", deleteResult.get("status"));

        // User is gone
        assertThrows(ResponseStatusException.class, () ->
                userService.getUser(username, newPass)
        );
    }

    // =========================================================================
    // 2. BANKING & TRANSACTION LIFECYCLE
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("2.1 3-Step Deposit Flow: Session creation, confirmation, and Admin approval")
    void testThreeStepDepositFlowWithAdminApproval() {
        String username = "bank_user1";
        String pass = "bank_pass_123";
        userService.createUser(new UserCredentialsRequest(username, pass));

        // Enforce Minimum Deposit ($5.00 USDT)
        assertThrows(ResponseStatusException.class, () ->
                userService.startDepositSession(new BalanceOperationRequest(username, pass, new BigDecimal("4.99")))
        );

        // Step 1: Start 15-minute deposit session ($50 USDT)
        DepositSessionResponse session = userService.startDepositSession(
                new BalanceOperationRequest(username, pass, new BigDecimal("50.00"))
        );
        assertTrue(session.active());
        assertNotNull(session.txHash());
        assertEquals(64, session.txHash().length(), "Transaction hash must be a 64-character SHA-256 hex string");
        assertEquals("USDT", session.currency());
        assertEquals("Ton", session.chain());
        assertEquals("chromakid", session.adminUsername());
        assertTrue(session.remainingSeconds() > 0 && session.remainingSeconds() <= 900);

        // While in AWAITING_PAYMENT, it is not yet visible in admin's PENDING list
        assertTrue(userService.getAllPendingTransactions().stream()
                .noneMatch(t -> t.txHash().equals(session.txHash())));

        // Active session persists across page reloads
        DepositSessionResponse activeCheck = userService.getActiveDepositSession(username, pass);
        assertTrue(activeCheck.active());
        assertEquals(session.txHash(), activeCheck.txHash());

        // Step 2 -> Step 3: Player clicks "Payment Sent" -> promotes to PENDING
        userService.confirmDepositSent(new ConfirmDepositRequest(username, pass, session.txHash()));

        // Balance remains 0.00 before admin review
        BalanceResponse beforeApprove = userService.getUserBalance(username, pass);
        assertEquals(0, BigDecimal.ZERO.compareTo(beforeApprove.balance()));

        // Now visible to admin as PENDING
        List<TransactionResponse> pendingList = userService.getAllPendingTransactions();
        TransactionResponse depositTx = pendingList.stream()
                .filter(t -> t.txHash().equals(session.txHash()))
                .findFirst()
                .orElseThrow();
        assertEquals(TransactionType.DEPOSIT, depositTx.type());
        assertEquals(TransactionStatus.PENDING, depositTx.status());

        // Step 3: Admin approves deposit -> balance increases to $50.00
        userService.approveTransaction(depositTx.txHash());
        BalanceResponse afterApprove = userService.getUserBalance(username, pass);
        assertEquals(0, new BigDecimal("50.00").compareTo(afterApprove.balance()));

        // Cleanup
        userService.deleteAccount(new UserCredentialsRequest(username, pass));
    }

    @Test
    @Order(4)
    @DisplayName("2.2 Withdrawal Rules: Minimum amount ($10), deposit requirement, TON address, and Admin rejection refund")
    void testWithdrawalFlowAndAdminRejectionRefund() {
        String username = "bank_user2";
        String pass = "bank_pass_123";
        userService.createUser(new UserCredentialsRequest(username, pass));

        // Rule 1: Cannot withdraw if user has never made an approved deposit
        assertThrows(ResponseStatusException.class, () ->
                userService.withdraw(new BalanceOperationRequest(username, pass, new BigDecimal("15.00"), "UQ_ADDR"))
        );

        // Make an approved deposit of $100.00 to qualify for withdrawals
        DepositSessionResponse session = userService.startDepositSession(
                new BalanceOperationRequest(username, pass, new BigDecimal("100.00"))
        );
        userService.confirmDepositSent(new ConfirmDepositRequest(username, pass, session.txHash()));
        userService.approveTransaction(session.txHash());

        // Rule 2: Minimum withdrawal is $10.00 USDT
        assertThrows(ResponseStatusException.class, () ->
                userService.withdraw(new BalanceOperationRequest(username, pass, new BigDecimal("9.99"), "UQ_ADDR"))
        );

        // Rule 3: Valid TON address is required
        assertThrows(ResponseStatusException.class, () ->
                userService.withdraw(new BalanceOperationRequest(username, pass, new BigDecimal("30.00"), ""))
        );

        // Successful withdrawal request ($30.00) -> immediately debited from balance (100 - 30 = 70)
        String tonAddress = "UQCys13azAWTFuERWwjc_5sqKZxQEhJhbtGGaiyp2Sf55Hrg";
        userService.withdraw(new BalanceOperationRequest(username, pass, new BigDecimal("30.00"), tonAddress));

        BalanceResponse afterWithdrawReq = userService.getUserBalance(username, pass);
        assertEquals(0, new BigDecimal("70.00").compareTo(afterWithdrawReq.balance()));

        // Admin overview shows the pending withdrawal
        AdminUserOverviewResponse overview = userService.getAdminUserOverview(username);
        assertEquals(1, overview.pendingTransactions().size());
        TransactionResponse pendingWithdraw = overview.pendingTransactions().get(0);
        assertEquals(TransactionType.WITHDRAW, pendingWithdraw.type());
        assertEquals(tonAddress, pendingWithdraw.walletAddress());

        // Admin rejects withdrawal -> $30.00 is refunded back to user (balance restored to $100.00)
        userService.rejectTransaction(pendingWithdraw.txHash());
        BalanceResponse afterReject = userService.getUserBalance(username, pass);
        assertEquals(0, new BigDecimal("100.00").compareTo(afterReject.balance()));

        // Cleanup
        userService.deleteAccount(new UserCredentialsRequest(username, pass));
    }

    @Test
    @Order(5)
    @DisplayName("2.3 Disallow starting a new transaction while a PENDING one is awaiting admin review")
    void testDisallowOverlappingPendingTransactions() {
        String username = "bank_user3";
        String pass = "bank_pass_123";
        userService.createUser(new UserCredentialsRequest(username, pass));

        // Create an active pending deposit
        DepositSessionResponse session = userService.startDepositSession(
                new BalanceOperationRequest(username, pass, new BigDecimal("20.00"))
        );
        userService.confirmDepositSent(new ConfirmDepositRequest(username, pass, session.txHash()));

        // While PENDING, starting another deposit is rejected
        assertThrows(ResponseStatusException.class, () ->
                userService.startDepositSession(new BalanceOperationRequest(username, pass, new BigDecimal("30.00")))
        );

        // While PENDING, requesting withdrawal is also rejected
        assertThrows(ResponseStatusException.class, () ->
                userService.withdraw(new BalanceOperationRequest(username, pass, new BigDecimal("10.00"), "UQ_ADDR"))
        );

        // Cleanup by rejecting the pending deposit
        userService.rejectTransaction(session.txHash());
        userService.deleteAccount(new UserCredentialsRequest(username, pass));
    }

    // =========================================================================
    // 3. CASE MARKETPLACE & CASE MATH ENGINE
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("3.1 Case Marketplace: ItemType naming (PEPE_IRON, SHIBA_INU_GOLD, DOGE_AMETHYST) and purchases")
    void testCaseMarketplaceAndItemTypeNormalization() {
        // Verify enum constants and string parser
        assertEquals(ItemType.PEPE_IRON, ItemType.fromString("PEPE_IRON"));
        assertEquals(ItemType.PEPE_IRON, ItemType.fromString("pepe-iron"));
        assertEquals(ItemType.PEPE_IRON, ItemType.fromString("pepe_iron"));
        assertEquals(ItemType.SHIBA_INU_GOLD, ItemType.fromString("shiba-inu-gold"));
        assertEquals(ItemType.DOGE_AMETHYST, ItemType.fromString("doge-amethyst"));

        assertEquals(0, new BigDecimal("2.00").compareTo(ItemType.PEPE_IRON.getPrice()));
        assertEquals(0, new BigDecimal("5.00").compareTo(ItemType.SHIBA_INU_GOLD.getPrice()));
        assertEquals(0, new BigDecimal("11.00").compareTo(ItemType.DOGE_AMETHYST.getPrice()));

        // Create player and deposit $50.00
        String username = "case_buyer";
        String pass = "pass_123";
        userService.createUser(new UserCredentialsRequest(username, pass));
        DepositSessionResponse session = userService.startDepositSession(
                new BalanceOperationRequest(username, pass, new BigDecimal("50.00"))
        );
        userService.confirmDepositSent(new ConfirmDepositRequest(username, pass, session.txHash()));
        userService.approveTransaction(session.txHash());

        // Buy one of each case: PEPE_IRON ($2), SHIBA_INU_GOLD ($5), DOGE_AMETHYST ($11)
        userService.buyItem(new ItemOperationRequest(username, pass, ItemType.PEPE_IRON));
        userService.buyItem(new ItemOperationRequest(username, pass, ItemType.SHIBA_INU_GOLD));
        userService.buyItem(new ItemOperationRequest(username, pass, ItemType.DOGE_AMETHYST));

        // Balance check: 50.00 - (2.00 + 5.00 + 11.00) = 32.00
        BalanceResponse balance = userService.getUserBalance(username, pass);
        assertEquals(0, new BigDecimal("32.00").compareTo(balance.balance()));

        // Inventory check
        ItemsResponse items = userService.getUserItems(username, pass);
        assertEquals(List.of(ItemType.PEPE_IRON, ItemType.SHIBA_INU_GOLD, ItemType.DOGE_AMETHYST), items.items());

        // Disallow purchase if insufficient balance
        assertThrows(ResponseStatusException.class, () -> {
            for (int i = 0; i < 10; i++) {
                userService.buyItem(new ItemOperationRequest(username, pass, ItemType.DOGE_AMETHYST));
            }
        });

        // Cleanup
        userService.deleteAccount(new UserCredentialsRequest(username, pass));
    }

    @Test
    @Order(7)
    @DisplayName("3.2 Math Engine Validation and Case Unboxing payout flow")
    void testCaseMathStatsAndUnboxingPayout() {
        // Validate configured paytables for all 3 cases
        List<CaseOpenerEngine.CaseMathStats> mathStats = userService.getAllCasesMathStats();
        assertEquals(3, mathStats.size());

        for (CaseOpenerEngine.CaseMathStats stat : mathStats) {
            assertEquals(1.0, stat.totalProbability(), 0.0001,
                    "Sum of probabilities must equal 1.0 for " + stat.itemType());
            assertTrue(stat.rtpPercent() >= 92.0 && stat.rtpPercent() <= 94.0,
                    "RTP must be configured near 93.0% for " + stat.itemType());
            assertTrue(stat.variance() > 0.0, "Variance must be positive");
        }

        // Setup player with PEPE_IRON in inventory
        String username = "unboxer";
        String pass = "unbox_pass";
        userService.createUser(new UserCredentialsRequest(username, pass));
        DepositSessionResponse session = userService.startDepositSession(
                new BalanceOperationRequest(username, pass, new BigDecimal("10.00"))
        );
        userService.confirmDepositSent(new ConfirmDepositRequest(username, pass, session.txHash()));
        userService.approveTransaction(session.txHash());

        userService.buyItem(new ItemOperationRequest(username, pass, ItemType.PEPE_IRON));
        // Balance before open: 10 - 2 = 8.00

        // Unbox PEPE_IRON
        OpenCaseResponse openResponse = userService.openCase(
                new ItemOperationRequest(username, pass, ItemType.PEPE_IRON)
        );
        assertEquals(ItemType.PEPE_IRON, openResponse.openedCase());
        assertNotNull(openResponse.wonAmount());
        assertTrue(openResponse.wonAmount().compareTo(BigDecimal.ZERO) >= 0);

        // Verification: new balance must equal 8.00 + wonAmount
        assertEquals(0, new BigDecimal("8.00").add(openResponse.wonAmount()).compareTo(openResponse.newBalance()));

        // Case was consumed from inventory
        ItemsResponse afterOpen = userService.getUserItems(username, pass);
        assertTrue(afterOpen.items().isEmpty());

        // Opening again without owning case throws 400
        assertThrows(ResponseStatusException.class, () ->
                userService.openCase(new ItemOperationRequest(username, pass, ItemType.PEPE_IRON))
        );

        // Cleanup
        userService.deleteAccount(new UserCredentialsRequest(username, pass));
    }

    // =========================================================================
    // 4. ADMIN ACCESS CONTROL, COOKIE SESSION & SECURITY INTERCEPTOR
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("4.1 Admin UI View Redirection: Unauthenticated requests to /admin and /admin.html redirect to '/'")
    void testAdminViewRedirectionForUnauthenticatedUsers() throws Exception {
        // Unauthenticated access to /admin -> 302 Redirect to /
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Unauthenticated access to /admin.html -> 302 Redirect to /
        mockMvc.perform(get("/admin.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Unauthenticated access to /api/admin/** -> 401 Unauthorized
        mockMvc.perform(get("/api/admin/transactions/pending")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        // Unauthenticated access to /api/admin/bankroll -> 401 Unauthorized
        mockMvc.perform(get("/api/admin/bankroll")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    @DisplayName("4.2 Admin Login, 4-Hour Cookie Session (ADMIN_SESSION), and Logout Lifecycle")
    void testAdminLoginAndCookieSessionLifecycle() throws Exception {
        // 1. Login with invalid password -> 401 Unauthorized
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"wrong_password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        // 2. Login with valid password -> 200 OK with 4-hour (14400s) HttpOnly SameSite=Strict cookie
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"" + testAdminPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(AdminAuthInterceptor.SESSION_COOKIE_NAME))
                .andExpect(cookie().maxAge(AdminAuthInterceptor.SESSION_COOKIE_NAME, 14400))
                .andExpect(cookie().httpOnly(AdminAuthInterceptor.SESSION_COOKIE_NAME, true));

        // 2b. Login with alternative payload format {"key": "..."} -> 200 OK
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"" + testAdminPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(AdminAuthInterceptor.SESSION_COOKIE_NAME));

        Cookie adminCookie = new Cookie(AdminAuthInterceptor.SESSION_COOKIE_NAME, testAdminPassword);

        // 3. Guarded UI route /admin delivers HTML when authenticated with ADMIN_SESSION cookie
        mockMvc.perform(get("/admin").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("ADMIN PANEL")));

        // 3b. Guarded UI route /admin.html delivers HTML when authenticated with ADMIN_SESSION cookie
        mockMvc.perform(get("/admin.html").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("ADMIN PANEL")));

        // 4. API endpoints allow access with ADMIN_SESSION cookie
        mockMvc.perform(get("/api/admin/transactions/pending").cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/bankroll").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floorBufferRate").value("33%"));

        // 5. Logout clears the ADMIN_SESSION cookie (Max-Age=0)
        mockMvc.perform(post("/api/admin/logout").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(AdminAuthInterceptor.SESSION_COOKIE_NAME, 0));

        // 6. Subsequent visits without cookie redirect back to /
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @Order(10)
    @DisplayName("4.3 AdminAuthInterceptor: Backward-compatible access via X-Admin-Key, Authorization Bearer, & CORS")
    void testAdminAuthInterceptorHeaderAuthentication() throws Exception {
        // Valid X-Admin-Key header -> 200 OK
        mockMvc.perform(get("/api/admin/transactions/pending")
                        .header("X-Admin-Key", testAdminPassword)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Valid Authorization Bearer header -> 200 OK
        mockMvc.perform(get("/api/admin/transactions/pending")
                        .header("Authorization", "Bearer " + testAdminPassword)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Bankroll endpoint with valid key -> 200 OK and fields present
        mockMvc.perform(get("/api/admin/bankroll")
                        .header("X-Admin-Key", testAdminPassword)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profitPool").isNumber())
                .andExpect(jsonPath("$.houseProfit").isNumber())
                .andExpect(jsonPath("$.floorBufferRate").value("33%"));

        // CORS preflight OPTIONS request passes through without credentials
        mockMvc.perform(options("/api/admin/transactions/pending"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(11)
    @DisplayName("4.4 Admin Promo Code verification via POST /api/users/promo")
    void testAdminPromoCodeVerification() {
        Map<String, Object> validPromo = userService.verifyPromoCode(testAdminPassword);
        assertEquals(true, validPromo.get("valid"));

        assertThrows(ResponseStatusException.class, () ->
                userService.verifyPromoCode("invalid_admin_password")
        );
    }

    @Test
    @Order(12)
    @DisplayName("4.5 Bankroll Reset Endpoint: Protected by Interceptor, Strict Phrase Validation, Atomically Zeros Metrics")
    void testAdminBankrollResetEndpoint() throws Exception {
        Cookie adminCookie = new Cookie(AdminAuthInterceptor.SESSION_COOKIE_NAME, testAdminPassword);

        // 1. Unauthenticated request -> 401 Unauthorized
        mockMvc.perform(post("/api/admin/bankroll/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\": \"RESET BANKROLL AS ADMIN\"}"))
                .andExpect(status().isUnauthorized());

        // 2. Authenticated but mismatched confirmation phrase -> 400 Bad Request
        mockMvc.perform(post("/api/admin/bankroll/reset")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\": \"wrong phrase\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid confirmation phrase"));

        // 2b. Missing payload -> 400 Bad Request
        mockMvc.perform(post("/api/admin/bankroll/reset")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid confirmation phrase"));

        // 3. Authenticated with exact phrase -> 200 OK with prizePool and houseProfit equal to 0.00
        mockMvc.perform(post("/api/admin/bankroll/reset")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\": \"RESET BANKROLL AS ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prizePool").value(0.00))
                .andExpect(jsonPath("$.houseProfit").value(0.00));

        // 4. Verify via GET /api/admin/bankroll
        mockMvc.perform(get("/api/admin/bankroll").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profitPool").value(0.00))
                .andExpect(jsonPath("$.houseProfit").value(0.00));
    }

    // =========================================================================
    // 5. CONCURRENCY SAFETY & RACE CONDITION PROTECTION
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("5.1 Concurrent deposit confirmations: Exactly 1 succeeds and 4 fail")
    void testConcurrentDepositConfirmationsRaceCondition() throws Exception {
        String username = "race_tester";
        String pass = "secret_123";
        userService.createUser(new UserCredentialsRequest(username, pass));

        DepositSessionResponse session = userService.startDepositSession(
                new BalanceOperationRequest(username, pass, new BigDecimal("250.00"))
        );
        String txHash = session.txHash();

        int threadCount = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 1; i <= threadCount; i++) {
            final int workerId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                readyLatch.countDown();
                try {
                    startGate.await();
                    userService.confirmDepositSent(
                            new ConfirmDepositRequest(username, pass, txHash)
                    );
                    int s = successCount.incrementAndGet();
                    System.out.printf("[CONCURRENCY-TEST] Worker #%d SUCCEEDED (txHash=%s, totalSuccess=%d)%n",
                            workerId, txHash.substring(0, 12), s);
                } catch (Exception ex) {
                    int f = failureCount.incrementAndGet();
                    System.out.printf("[CONCURRENCY-TEST] Worker #%d FAILED as expected: %s (totalFailed=%d)%n",
                            workerId, ex.getMessage(), f);
                }
            }, pool);
            futures.add(future);
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
        startGate.countDown();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        System.out.printf("[CONCURRENCY-TEST SUMMARY] Successes=%d | Failures=%d%n",
                successCount.get(), failureCount.get());

        assertEquals(1, successCount.get(), "Exactly 1 concurrent confirm request must succeed");
        assertEquals(4, failureCount.get(), "Exactly 4 concurrent confirm requests must fail");

        List<TransactionResponse> userPending = userService.getUserPendingTransactions(username);
        assertEquals(1, userPending.size(), "Only 1 PENDING transaction may exist in DB");
        assertEquals(txHash, userPending.get(0).txHash());
        assertEquals(TransactionStatus.PENDING, userPending.get(0).status());

        // Cleanup
        userService.deleteAccount(new UserCredentialsRequest(username, pass));
    }
}
