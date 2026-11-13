package com.dworf.slots_demo.security;

import com.dworf.slots_demo.entity.AuthSession;
import com.dworf.slots_demo.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;

/**
 * C1/C4/C6/H20: the single gatekeeper for /api/** and the admin pages. DEFAULT DENY:
 * anything not listed in PUBLIC needs a valid session of the right role, and every
 * state-changing request must pass the Origin check and (when authenticated) the CSRF token check.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_SESSION = "baddrop.session";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private static final Set<String> PUBLIC = Set.of(
            "GET /api", "GET /api/config", "GET /api/cases", "GET /api/cases/math",
            "GET /api/live-feed", "GET /api/leaderboard",
            "POST /api/auth/telegram", "POST /api/auth/dev-login", "POST /api/auth/logout",
            "POST /api/admin/auth/login");

    private final SessionService sessions;

    public AuthInterceptor(SessionService sessions) {
        this.sessions = sessions;
    }

    public static AuthSession session(HttpServletRequest request) {
        return (AuthSession) request.getAttribute(ATTR_SESSION);
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String method = req.getMethod();
        String uri = req.getRequestURI();

        // Refuse path tricks outright instead of trying to normalise them (fail closed).
        if (uri.contains(";") || uri.contains("%") || uri.contains("..") || uri.contains("//") || uri.contains("\\")) {
            return deny(req, res, 400, "Bad request");
        }
        if ("OPTIONS".equals(method)) {
            return deny(req, res, 403, "Forbidden"); // same-origin app: no CORS preflight is ever legitimate
        }

        boolean unsafe = !("GET".equals(method) || "HEAD".equals(method));
        if (unsafe && !originAllowed(req)) {
            return deny(req, res, 403, "Cross-origin request rejected");
        }

        Optional<AuthSession> found = sessions.resolve(SessionCookies.read(req));
        found.ifPresent(s -> req.setAttribute(ATTR_SESSION, s));

        boolean adminView = uri.equals("/admin") || uri.equals("/admin.html");
        boolean adminApi = uri.startsWith("/api/admin/");
        boolean isPublic = PUBLIC.contains(method + " " + uri);

        if (isPublic) {
            return true;
        }
        if (adminView) {
            if (found.isPresent() && found.get().getRole() == AuthSession.Role.ADMIN) return true;
            res.sendRedirect("/admin-login.html");
            return false;
        }
        if (found.isEmpty()) {
            return deny(req, res, 401, "Unauthorized");
        }
        AuthSession s = found.get();
        AuthSession.Role needed = adminApi ? AuthSession.Role.ADMIN : AuthSession.Role.PLAYER;
        if (s.getRole() != needed) {
            return deny(req, res, 403, "Forbidden");
        }
        if (unsafe) {
            String sent = req.getHeader(CSRF_HEADER);
            if (sent == null || !MessageDigest.isEqual(
                    sent.getBytes(StandardCharsets.UTF_8), s.getCsrfToken().getBytes(StandardCharsets.UTF_8))) {
                return deny(req, res, 403, "Missing or invalid CSRF token");
            }
        }
        return true;
    }

    /** If the browser sent an Origin it must be exactly this site. A missing Origin (curl, same-origin GET) is allowed. */
    private static boolean originAllowed(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin == null) return true;
        try {
            URI o = URI.create(origin);
            String host = req.getHeader("Host");
            if (host == null || o.getHost() == null) return false;
            String originAuthority = o.getPort() == -1 ? o.getHost() : o.getHost() + ":" + o.getPort();
            return originAuthority.equalsIgnoreCase(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean deny(HttpServletRequest req, HttpServletResponse res, int status, String message) throws Exception {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
        return false;
    }
}
