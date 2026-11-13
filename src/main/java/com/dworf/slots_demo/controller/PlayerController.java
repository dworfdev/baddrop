package com.dworf.slots_demo.controller;

import com.dworf.slots_demo.dto.BalanceResponse;
import com.dworf.slots_demo.dto.CaseOperationRequest;
import com.dworf.slots_demo.dto.ConfirmDepositRequest;
import com.dworf.slots_demo.dto.DepositSessionResponse;
import com.dworf.slots_demo.dto.DepositStartRequest;
import com.dworf.slots_demo.dto.ItemsResponse;
import com.dworf.slots_demo.dto.OpenCaseResponse;
import com.dworf.slots_demo.dto.UserResponse;
import com.dworf.slots_demo.dto.WithdrawRequest;
import com.dworf.slots_demo.security.AuthInterceptor;
import com.dworf.slots_demo.service.AccountService;
import com.dworf.slots_demo.service.PlayService;
import com.dworf.slots_demo.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated player API. The acting user ALWAYS comes from the server-side session,
 * never from a request parameter, so there is no way to address another account (C1/C4, IDOR).
 */
@RestController
@RequestMapping("/api/me")
public class PlayerController {
    private static final String IDEM = "Idempotency-Key";

    private final AccountService accounts;
    private final WalletService wallet;
    private final PlayService play;

    public PlayerController(AccountService accounts, WalletService wallet, PlayService play) {
        this.accounts = accounts;
        this.wallet = wallet;
        this.play = play;
    }

    private static Long uid(HttpServletRequest req) {
        return AuthInterceptor.session(req).getUserId();
    }

    @GetMapping
    public UserResponse profile(HttpServletRequest req) {
        return accounts.me(uid(req));
    }

    @GetMapping("/balance")
    public BalanceResponse balance(HttpServletRequest req) {
        return play.balance(uid(req));
    }

    @GetMapping("/items")
    public ItemsResponse items(HttpServletRequest req) {
        return play.items(uid(req));
    }

    @PostMapping("/deposit/start")
    public DepositSessionResponse startDeposit(@RequestBody DepositStartRequest body,
                                               @RequestHeader(value = IDEM, required = false) String key,
                                               HttpServletRequest req) {
        return wallet.startDeposit(uid(req), body.amount(), key);
    }

    @GetMapping("/deposit/active")
    public DepositSessionResponse activeDeposit(HttpServletRequest req) {
        return wallet.activeDeposit(uid(req));
    }

    @PostMapping("/deposit/cancel")
    public DepositSessionResponse cancelDeposit(HttpServletRequest req) {
        return wallet.cancelDeposit(uid(req));
    }

    @PostMapping("/deposit/confirm")
    public UserResponse confirmDeposit(@RequestBody ConfirmDepositRequest body, HttpServletRequest req) {
        return wallet.confirmDeposit(uid(req), body.txHash(), body.receipt());
    }

    @PostMapping("/withdraw")
    public UserResponse withdraw(@RequestBody WithdrawRequest body,
                                 @RequestHeader(value = IDEM, required = false) String key,
                                 HttpServletRequest req) {
        return wallet.withdraw(uid(req), body.amount(), body.walletAddress(), key);
    }

    @PostMapping("/cases/buy")
    public UserResponse buy(@RequestBody CaseOperationRequest body,
                            @RequestHeader(value = IDEM, required = false) String key,
                            HttpServletRequest req) {
        return play.buy(uid(req), body.item(), body.resolvedQuantity(), key);
    }

    @PostMapping("/cases/open")
    public OpenCaseResponse open(@RequestBody CaseOperationRequest body, HttpServletRequest req) {
        return play.open(uid(req), body.item(), body.resolvedQuantity());
    }

    @PostMapping("/cases/instant-open")
    public OpenCaseResponse instantOpen(@RequestBody CaseOperationRequest body,
                                        @RequestHeader(value = IDEM, required = false) String key,
                                        HttpServletRequest req) {
        return play.instantOpen(uid(req), body.item(), body.resolvedQuantity(), key);
    }
}
