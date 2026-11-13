package com.dworf.slots_demo.controller;

import com.dworf.slots_demo.dto.DeleteAccountRequest;
import com.dworf.slots_demo.dto.DevLoginRequest;
import com.dworf.slots_demo.dto.MeResponse;
import com.dworf.slots_demo.dto.TelegramLoginRequest;
import com.dworf.slots_demo.dto.UserResponse;
import com.dworf.slots_demo.entity.AuthSession;
import com.dworf.slots_demo.security.AuthInterceptor;
import com.dworf.slots_demo.security.SessionCookies;
import com.dworf.slots_demo.service.AccountService;
import com.dworf.slots_demo.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final SessionService sessions;
    private final SessionCookies cookies;

    public AuthController(AccountService accounts, SessionService sessions, SessionCookies cookies) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.cookies = cookies;
    }

    /** The only player login: the Mini App posts Telegram.WebApp.initData, the server checks Telegram's signature. */
    @PostMapping("/telegram")
    public ResponseEntity<MeResponse> telegram(@RequestBody TelegramLoginRequest body, HttpServletRequest req) {
        if (body == null || body.initData() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Telegram sign-in failed. Reopen the app from Telegram");
        }
        return respond(HttpStatus.OK, accounts.loginWithTelegram(body.initData(), req.getRemoteAddr()));
    }

    /** 404 unless slots.auth.dev-login-enabled=true (local testing only). */
    @PostMapping("/dev-login")
    public ResponseEntity<MeResponse> devLogin(@RequestBody DevLoginRequest body, HttpServletRequest req) {
        if (!accounts.isDevLoginEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body required");
        return respond(HttpStatus.OK, accounts.devLogin(body.username(), req.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest req) {
        sessions.revoke(SessionCookies.read(req));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.clear()).body(Map.of("status", "LOGGED_OUT"));
    }

    @GetMapping("/me")
    public MeResponse me(HttpServletRequest req) {
        AuthSession s = AuthInterceptor.session(req);
        return new MeResponse(accounts.me(s.getUserId()), s.getCsrfToken());
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, String>> delete(@RequestBody DeleteAccountRequest body, HttpServletRequest req) {
        AuthSession s = AuthInterceptor.session(req);
        accounts.deleteAccount(s.getUserId(), body == null ? null : body.confirm());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.clear()).body(Map.of("status", "DELETED"));
    }

    private ResponseEntity<MeResponse> respond(HttpStatus status, AccountService.Authenticated a) {
        Duration maxAge = Duration.between(Instant.now(), a.session().session().getExpiresAt());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.issue(a.session().rawToken(), maxAge))
                .body(new MeResponse(UserResponse.fromEntity(a.user()), a.session().session().getCsrfToken()));
    }
}
