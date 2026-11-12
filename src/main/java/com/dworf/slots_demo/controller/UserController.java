package com.dworf.slots_demo.controller;

import com.dworf.slots_demo.dto.BalanceOperationRequest;
import com.dworf.slots_demo.dto.BalanceResponse;
import com.dworf.slots_demo.dto.CaseResponse;
import com.dworf.slots_demo.dto.ChangePasswordRequest;
import com.dworf.slots_demo.dto.ConfirmDepositRequest;
import com.dworf.slots_demo.dto.DepositSessionResponse;
import com.dworf.slots_demo.dto.ItemOperationRequest;
import com.dworf.slots_demo.dto.ItemsResponse;
import com.dworf.slots_demo.dto.LiveFeedEventResponse;
import com.dworf.slots_demo.dto.OpenCaseResponse;
import com.dworf.slots_demo.dto.PromoRequest;
import com.dworf.slots_demo.dto.UserCredentialsRequest;
import com.dworf.slots_demo.dto.UserResponse;
import com.dworf.slots_demo.service.CaseOpenerEngine;
import com.dworf.slots_demo.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Health status endpoint without exposing internal API routes.
     */
    @GetMapping("/api")
    public Map<String, Object> apiOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("application", "BADDROP");
        overview.put("status", "RUNNING");
        overview.put("depositAdminUsername", userService.getDepositAdminUsername());
        return overview;
    }

    /**
     * Public deposit & withdrawal configuration endpoint.
     */
    @GetMapping("/api/config")
    public Map<String, Object> getPublicConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("depositAdminUsername", userService.getDepositAdminUsername());
        config.put("minDepositAmount", UserService.MIN_DEPOSIT_AMOUNT);
        config.put("minWithdrawAmount", UserService.MIN_WITHDRAW_AMOUNT);
        return config;
    }

    // 1a) Create user (POST) - sets balance=0, items=[]
    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@RequestBody UserCredentialsRequest request) {
        return userService.createUser(request);
    }

    // 1b) Login user (POST)
    @PostMapping("/api/users/login")
    public UserResponse loginUser(@RequestBody UserCredentialsRequest request) {
        return userService.loginUser(request);
    }

    // 1c) Change password (POST)
    @PostMapping("/api/users/password")
    public UserResponse changePassword(@RequestBody ChangePasswordRequest request) {
        return userService.changePassword(request);
    }

    // 1d) Logout user (POST)
    @PostMapping("/api/users/logout")
    public Map<String, String> logoutUser(@RequestBody UserCredentialsRequest request) {
        return userService.logoutUser(request);
    }

    // 1e) Delete account (POST) - the frontend (app.js) uses POST /api/users/delete
    @PostMapping("/api/users/delete")
    public Map<String, String> deleteAccount(@RequestBody UserCredentialsRequest request) {
        return userService.deleteAccount(request);
    }

    // 1f) Verify promo code (POST) - grants admin access if matches configured admin password
    @PostMapping("/api/users/promo")
    public Map<String, Object> verifyPromo(@RequestBody PromoRequest request) {
        return userService.verifyPromoCode(request != null ? request.promo() : null);
    }

    // 2a-step1) Start 15-minute deposit session (Step 1 -> Step 2)
    @PostMapping("/api/users/balance/deposit/start")
    public DepositSessionResponse startDepositSession(@RequestBody BalanceOperationRequest request) {
        return userService.startDepositSession(request);
    }

    // 2a-active) Check active 15-minute deposit session (restores Step 2 on page reload / reopen)
    @GetMapping("/api/users/balance/deposit/active")
    public DepositSessionResponse getActiveDepositSession(
            @RequestParam String username,
            @RequestParam(required = false) String password
    ) {
        return userService.getActiveDepositSession(username, password);
    }

    // 2a-step2) Confirm deposit sent ("Sent" button on Step 2 -> promotes to PENDING & moves to Step 3)
    @PostMapping("/api/users/balance/deposit/confirm")
    public UserResponse confirmDepositSent(@RequestBody ConfirmDepositRequest request) {
        return userService.confirmDepositSent(request);
    }

    // 2b) Withdraw request from user's balance (POST) - subtracts immediately, creates PENDING transaction
    @PostMapping("/api/users/balance/withdraw")
    public UserResponse withdraw(@RequestBody BalanceOperationRequest request) {
        return userService.withdraw(request);
    }

    // 3) Player: Buy case from marketplace (POST) - $2 pepe-iron, $5 shiba-inu-gold, $11 doge-amethyst
    @PostMapping("/api/users/items/buy")
    public UserResponse buyItem(@RequestBody ItemOperationRequest request) {
        return userService.buyItem(request);
    }

    // 3b) Player: Open owned case from inventory (POST) - resolves payout via CDF math engine & adds USDT to balance
    @PostMapping("/api/users/items/open")
    public OpenCaseResponse openCase(@RequestBody ItemOperationRequest request) {
        return userService.openCase(request);
    }

    // 3b-instant) Player: Instant open case directly from marketplace (POST) - deducts crate price, spins & credits win
    @PostMapping({"/api/cases/instant-open", "/api/users/cases/instant-open"})
    public OpenCaseResponse instantOpenCase(@RequestBody ItemOperationRequest request) {
        return userService.instantOpenCase(request);
    }

    // 3c-1) Get all active cases ordered by created_at DESC
    @GetMapping("/api/cases")
    public List<CaseResponse> getActiveCases() {
        return userService.getActiveCases();
    }

    // 3c-2) Get mathematical specification (PMF, EV, RTP %, House Edge %, Variance, Paytable) for all cases
    @GetMapping("/api/cases/math")
    public List<CaseOpenerEngine.CaseMathStats> getCasesMathStats() {
        return userService.getAllCasesMathStats();
    }

    // 5a) Get list of all users (GET) - without any args
    @GetMapping("/api/users")
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers();
    }

    // 5b) Get top 3 richest users for LIVE leaderboard (GET)
    @GetMapping("/api/users/top")
    public List<UserResponse> getTopRichUsers() {
        return userService.getTopRichUsers();
    }

    // 5b-feed) Get the 2 most recent real platform activity events for the LIVE bar (GET)
    @GetMapping("/api/live-feed")
    public List<LiveFeedEventResponse> getLiveFeed() {
        return userService.getLiveFeed();
    }

    // 5c) Get 1 specific user (GET) - requests user's username (and optional password)
    @GetMapping("/api/users/user")
    public UserResponse getOneUser(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password,
            @RequestBody(required = false) UserCredentialsRequest body
    ) {
        UserCredentialsRequest creds = resolveCredentials(username, password, body);
        return userService.getUser(creds.username(), creds.password());
    }

    // 6a) Get 1 user's balance (GET)
    @GetMapping("/api/users/balance")
    public BalanceResponse getUserBalance(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password,
            @RequestBody(required = false) UserCredentialsRequest body
    ) {
        UserCredentialsRequest creds = resolveCredentials(username, password, body);
        return userService.getUserBalance(creds.username(), creds.password());
    }

    // 6b) Get 1 user's items (GET)
    @GetMapping("/api/users/items")
    public ItemsResponse getUserItems(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password,
            @RequestBody(required = false) UserCredentialsRequest body
    ) {
        UserCredentialsRequest creds = resolveCredentials(username, password, body);
        return userService.getUserItems(creds.username(), creds.password());
    }

    private UserCredentialsRequest resolveCredentials(
            String username,
            String password,
            UserCredentialsRequest body
    ) {
        String resolvedUsername = (username != null && !username.isBlank())
                ? username
                : (body != null ? body.username() : null);
        String resolvedPassword = (password != null && !password.isBlank())
                ? password
                : (body != null ? body.password() : null);
        return new UserCredentialsRequest(resolvedUsername, resolvedPassword);
    }
}