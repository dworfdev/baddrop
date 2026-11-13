package com.dworf.slots_demo.controller;

import com.dworf.slots_demo.dto.AdminLoginRequest;
import com.dworf.slots_demo.dto.AdminUserOverviewResponse;
import com.dworf.slots_demo.dto.ApproveTransactionRequest;
import com.dworf.slots_demo.dto.BankrollResponse;
import com.dworf.slots_demo.dto.SessionResponse;
import com.dworf.slots_demo.dto.TransactionResponse;
import com.dworf.slots_demo.entity.AuditLog;
import com.dworf.slots_demo.repository.AuditLogRepository;
import com.dworf.slots_demo.security.AuthInterceptor;
import com.dworf.slots_demo.security.SessionCookies;
import com.dworf.slots_demo.service.AdminAuthService;
import com.dworf.slots_demo.service.AuditService;
import com.dworf.slots_demo.service.CaseOpeningService;
import com.dworf.slots_demo.service.ReconciliationService;
import com.dworf.slots_demo.service.SessionService;
import com.dworf.slots_demo.service.TransactionReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Everything here (except login) is gated by AuthInterceptor: ADMIN session + CSRF token. */
@RestController
public class AdminController {
    private final AdminAuthService adminAuth;
    private final SessionService sessions;
    private final SessionCookies cookies;
    private final TransactionReviewService review;
    private final CaseOpeningService caseOpeningService;
    private final ReconciliationService reconciliation;
    private final AuditService audit;
    private final AuditLogRepository auditLog;
    private final boolean allowBankrollReset;

    public AdminController(AdminAuthService adminAuth, SessionService sessions, SessionCookies cookies,
                           TransactionReviewService review, CaseOpeningService caseOpeningService,
                           ReconciliationService reconciliation, AuditService audit, AuditLogRepository auditLog,
                           @Value("${slots.admin.allow-bankroll-reset:false}") boolean allowBankrollReset) {
        this.adminAuth = adminAuth;
        this.sessions = sessions;
        this.cookies = cookies;
        this.review = review;
        this.caseOpeningService = caseOpeningService;
        this.reconciliation = reconciliation;
        this.audit = audit;
        this.auditLog = auditLog;
        this.allowBankrollReset = allowBankrollReset;
    }

    private static String actor(HttpServletRequest req) {
        return AuthInterceptor.session(req).getAdminUsername();
    }

    @GetMapping(value = {"/admin", "/admin.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> page() {
        Resource r = new ClassPathResource("templates/admin.html");
        return r.exists() ? ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(r) : ResponseEntity.notFound().build();
    }

    @PostMapping("/api/admin/auth/login")
    public ResponseEntity<SessionResponse> login(@RequestBody AdminLoginRequest body, HttpServletRequest req) {
        if (body == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        SessionService.Issued issued = adminAuth.login(body.username(), body.password(), body.totp(), req.getRemoteAddr());
        Duration maxAge = Duration.between(Instant.now(), issued.session().getExpiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(issued.rawToken(), maxAge))
                .body(new SessionResponse(issued.session().getAdminUsername(), "ADMIN", issued.session().getCsrfToken()));
    }

    @PostMapping("/api/admin/auth/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest req) {
        sessions.revoke(SessionCookies.read(req));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.clear()).body(Map.of("status", "LOGGED_OUT"));
    }

    @GetMapping("/api/admin/auth/me")
    public SessionResponse me(HttpServletRequest req) {
        var s = AuthInterceptor.session(req);
        return new SessionResponse(s.getAdminUsername(), "ADMIN", s.getCsrfToken());
    }

    @GetMapping("/api/admin/transactions/pending")
    public List<TransactionResponse> pending() {
        return review.pending();
    }

    @PostMapping("/api/admin/transactions/{txHash}/approve")
    public TransactionResponse approve(@PathVariable String txHash,
                                       @RequestBody(required = false) ApproveTransactionRequest body,
                                       HttpServletRequest req) {
        return review.approve(txHash, body == null ? null : body.externalTxHash(), actor(req), req.getRemoteAddr());
    }

    @PostMapping("/api/admin/transactions/{txHash}/reject")
    public TransactionResponse reject(@PathVariable String txHash, HttpServletRequest req) {
        return review.reject(txHash, actor(req), req.getRemoteAddr());
    }

    @GetMapping("/api/admin/bankroll")
    public BankrollResponse bankroll() {
        return caseOpeningService.getBankroll();
    }

    @GetMapping("/api/admin/reconciliation")
    public ReconciliationService.Report reconcile() {
        return reconciliation.report();
    }

    @GetMapping("/api/admin/users/{username}")
    public AdminUserOverviewResponse userOverview(@PathVariable String username) {
        return review.overview(username);
    }

    @GetMapping("/api/admin/audit")
    public List<AuditLog> auditTrail() {
        return auditLog.findTop100ByOrderByIdDesc();
    }

    /**
     * Zeroing the bankroll breaks the reconciliation invariant, so it is OFF by default
     * (slots.admin.allow-bankroll-reset=false) and, when enabled, audited.
     */
    @PostMapping("/api/admin/bankroll/reset")
    public ResponseEntity<?> resetBankroll(@RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        if (!allowBankrollReset) {
            audit.recordIndependent(actor(req), "BANKROLL_RESET_DENIED", null, null, req.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Bankroll reset is disabled"));
        }
        if (body == null || !"RESET BANKROLL AS ADMIN".equals(body.get("confirmation"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid confirmation phrase"));
        }
        BankrollResponse before = caseOpeningService.getBankroll();
        BankrollResponse after = caseOpeningService.resetBankroll();
        audit.recordIndependent(actor(req), "BANKROLL_RESET", null,
                "before pool=" + before.profitPool() + " house=" + before.houseProfit(), req.getRemoteAddr());
        return ResponseEntity.ok(after);
    }
}
