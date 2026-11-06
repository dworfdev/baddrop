package com.dworf.slots_demo.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Rejects any /api/admin/** or /admin / /admin.html request that doesn't carry the configured
 * secret key in "X-Admin-Key", "Authorization: Bearer <key>", or "ADMIN_SESSION" cookie.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_COOKIE_NAME = "ADMIN_SESSION";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String adminSecretKey;

    public AdminAuthInterceptor(@Value("${slots.admin.password:}") String adminSecretKey) {
        this.adminSecretKey = adminSecretKey == null ? "" : adminSecretKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // Let CORS preflight through
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // Login and logout endpoints are public so credentials can be submitted or cleared
        String uri = request.getRequestURI();
        if ("/api/admin/login".equals(uri) || "/api/admin/logout".equals(uri)) {
            return true;
        }

        // 1. Check X-Admin-Key header
        String provided = request.getHeader("X-Admin-Key");

        // 2. Check Authorization: Bearer <key> header
        if (provided == null || provided.isBlank()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                provided = auth.substring(BEARER_PREFIX.length()).trim();
            }
        }

        // 3. Check ADMIN_SESSION cookie
        if (provided == null || provided.isBlank()) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                        provided = cookie.getValue();
                        break;
                    }
                }
            }
        }

        if (!isValid(provided)) {
            if (isAdminViewUri(uri)) {
                response.sendRedirect("/");
                return false;
            }

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"Unauthorized\"}");
            return false;
        }
        return true;
    }

    public boolean isValid(String provided) {
        // If the key isn't configured, deny everything (fail closed).
        if (adminSecretKey.isBlank() || provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                adminSecretKey.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isAdminViewUri(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.equals("/admin") || uri.equals("/admin.html") || uri.startsWith("/admin/") || !uri.startsWith("/api/");
    }
}
