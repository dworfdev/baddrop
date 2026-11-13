package com.dworf.slots_demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * H20: baseline browser hardening.
 * <ul>
 *   <li>Player pages run inside the Telegram Mini App, so they load Telegram's SDK script and may be framed by
 *       Telegram's web clients (frame-ancestors). Override with {@code slots.csp}.</li>
 *   <li>Admin pages and the admin API are never framable and load nothing from outside
 *       ({@code slots.csp-admin}); only they send X-Frame-Options: DENY.</li>
 * </ul>
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final String playerCsp;
    private final String adminCsp;

    public SecurityHeadersFilter(
            @Value("${slots.csp:default-src 'self'; script-src 'self' https://telegram.org; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; frame-ancestors 'self' https://web.telegram.org https://*.telegram.org; base-uri 'none'; form-action 'self'}") String playerCsp,
            @Value("${slots.csp-admin:default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'}") String adminCsp) {
        this.playerCsp = playerCsp;
        this.adminCsp = adminCsp;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        boolean admin = uri.startsWith("/admin") || uri.startsWith("/api/admin");
        res.setHeader("Content-Security-Policy", admin ? adminCsp : playerCsp);
        if (admin) {
            res.setHeader("X-Frame-Options", "DENY");
        }
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (req.isSecure()) {
            res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        if (uri.startsWith("/api/") || admin) {
            res.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(req, res);
    }
}
