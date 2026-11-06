package com.dworf.slots_demo.controller;

import com.dworf.slots_demo.config.AdminAuthInterceptor;
import com.dworf.slots_demo.dto.AdminUserOverviewResponse;
import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.dto.TransactionResponse;
import com.dworf.slots_demo.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints and guarded view routes.
 * Protected by AdminAuthInterceptor (X-Admin-Key, Authorization: Bearer, or ADMIN_SESSION cookie).
 */
@RestController
public class AdminController {

    private static final int COOKIE_MAX_AGE_SECONDS = 14400; // 4 hours

    private final UserService userService;
    private final AdminAuthInterceptor adminAuthInterceptor;

    public AdminController(UserService userService, AdminAuthInterceptor adminAuthInterceptor) {
        this.userService = userService;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    // 0a) Guarded Admin UI View Route (GET /admin and GET /admin.html)
    @GetMapping(value = {"/admin", "/admin.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> getAdminPage() {
        Resource resource = new ClassPathResource("templates/admin.html");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }

    // 0b) Admin Login: exchanges secret key/password for a 4-hour HttpOnly SameSite=Strict cookie
    @PostMapping("/api/admin/login")
    public ResponseEntity<?> login(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        String provided = null;
        if (body != null) {
            provided = body.get("password");
            if (provided == null || provided.isBlank()) {
                provided = body.get("key");
            }
        }

        if (!adminAuthInterceptor.isValid(provided)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Unauthorized"));
        }

        ResponseCookie cookie = ResponseCookie.from(AdminAuthInterceptor.SESSION_COOKIE_NAME, provided)
                .maxAge(COOKIE_MAX_AGE_SECONDS)
                .path("/")
                .httpOnly(true)
                .sameSite("Strict")
                .secure(request.isSecure())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("status", "OK", "message", "Logged in"));
    }

    // 0c) Admin Logout: clears the ADMIN_SESSION cookie (Max-Age=0)
    @PostMapping("/api/admin/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        ResponseCookie cookie = ResponseCookie.from(AdminAuthInterceptor.SESSION_COOKIE_NAME, "")
                .maxAge(0)
                .path("/")
                .httpOnly(true)
                .sameSite("Strict")
                .secure(request.isSecure())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("status", "OK", "message", "Logged out"));
    }

    // 1) Get all pending transactions across all users
    @GetMapping("/api/admin/transactions/pending")
    public List<TransactionResponse> getAllPendingTransactions() {
        return userService.getAllPendingTransactions();
    }

    // 2) Approve a pending transaction by txHash
    @PostMapping("/api/admin/transactions/{txHash}/approve")
    public TransactionResponse approveTransaction(@PathVariable String txHash) {
        return userService.approveTransaction(txHash);
    }

    // 3) Reject a pending transaction by txHash
    @PostMapping("/api/admin/transactions/{txHash}/reject")
    public TransactionResponse rejectTransaction(@PathVariable String txHash) {
        return userService.rejectTransaction(txHash);
    }

    // 4) Platform bankroll metrics - profit pool, floor buffer, house profit
    @GetMapping("/api/admin/bankroll")
    public BankrollResponse getBankroll() {
        return userService.getBankroll();
    }

    // 4b) Atomically reset platform bankroll metrics (Profit Pool & House Profit) to 0.00
    @PostMapping("/api/admin/bankroll/reset")
    public ResponseEntity<?> resetBankroll(@RequestBody(required = false) Map<String, String> body) {
        String confirmation = body != null ? body.get("confirmation") : null;
        if (!"RESET BANKROLL AS ADMIN".equals(confirmation)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid confirmation phrase"));
        }
        BankrollResponse bankroll = userService.resetBankroll();
        return ResponseEntity.ok(Map.of(
                "prizePool", bankroll.profitPool().setScale(2, RoundingMode.HALF_UP),
                "profitPool", bankroll.profitPool().setScale(2, RoundingMode.HALF_UP),
                "houseProfit", bankroll.houseProfit().setScale(2, RoundingMode.HALF_UP)
        ));
    }

    // 5) Specific user's full overview (pending transactions, history)
    @GetMapping("/api/admin/users/{username}")
    public AdminUserOverviewResponse getAdminUserOverview(@PathVariable String username) {
        return userService.getAdminUserOverview(username);
    }
}
