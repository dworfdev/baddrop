package com.dworf.slots_demo.config;

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
 * Rejects any /api/admin/** request that doesn't carry the configured secret key in
 * "X-Admin-Key: <key>" or "Authorization: Bearer <key>".
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

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

        String provided = request.getHeader("X-Admin-Key");
        if (provided == null || provided.isBlank()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                provided = auth.substring(BEARER_PREFIX.length()).trim();
            }
        }

        if (!isValid(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"Unauthorized\"}");
            return false;
        }
        return true;
    }

    private boolean isValid(String provided) {
        // If the key isn't configured, deny everything (fail closed).
        if (adminSecretKey.isBlank() || provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                adminSecretKey.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
