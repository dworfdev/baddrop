package com.dworf.slots_demo.controller;

import com.dworf.slots_demo.dto.AdminUserOverviewResponse;
import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.dto.TransactionResponse;
import com.dworf.slots_demo.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin endpoints. All /api/admin/** routes are protected by AdminAuthInterceptor
 * (X-Admin-Key or Authorization: Bearer header).
 */
@RestController
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    // Get all pending transactions across all users
    @GetMapping("/api/admin/transactions/pending")
    public List<TransactionResponse> getAllPendingTransactions() {
        return userService.getAllPendingTransactions();
    }

    // Approve a pending transaction by txHash
    @PostMapping("/api/admin/transactions/{txHash}/approve")
    public TransactionResponse approveTransaction(@PathVariable String txHash) {
        return userService.approveTransaction(txHash);
    }

    // Reject a pending transaction by txHash
    @PostMapping("/api/admin/transactions/{txHash}/reject")
    public TransactionResponse rejectTransaction(@PathVariable String txHash) {
        return userService.rejectTransaction(txHash);
    }

    // Platform bankroll metrics - profit pool, floor buffer, house profit
    @GetMapping("/api/admin/bankroll")
    public BankrollResponse getBankroll() {
        return userService.getBankroll();
    }

    // Specific user's full overview (pending transactions, history). Also under /api/admin/**,
    // so it moves here too; the URI is unchanged.
    @GetMapping("/api/admin/users/{username}")
    public AdminUserOverviewResponse getAdminUserOverview(@PathVariable String username) {
        return userService.getAdminUserOverview(username);
    }
}
