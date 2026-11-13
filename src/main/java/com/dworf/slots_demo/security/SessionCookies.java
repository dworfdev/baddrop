package com.dworf.slots_demo.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * One cookie for both roles. HttpOnly always; Secure unless explicitly disabled for local HTTP.
 * SameSite=Strict by default. Telegram's mobile and desktop apps open the Mini App as a normal top-level page, where
 * Strict works. If players must also use Telegram Web (an iframe on another site), set slots.cookie.same-site=None:
 * the Origin check and the per-session CSRF token keep state-changing requests safe either way.
 */
@Component
public class SessionCookies {
    public static final String NAME = "BD_SESSION";

    private final boolean secure;
    private final String sameSite;

    public SessionCookies(@Value("${slots.cookie.secure:true}") boolean secure,
                          @Value("${slots.cookie.same-site:Strict}") String sameSite) {
        this.secure = secure;
        String s = sameSite == null ? "Strict" : sameSite.trim();
        // SameSite=None is only honoured by browsers together with Secure
        this.sameSite = ("None".equalsIgnoreCase(s) && secure) ? "None" : ("Lax".equalsIgnoreCase(s) ? "Lax" : "Strict");
    }

    public String issue(String rawToken, Duration maxAge) {
        return base(rawToken).maxAge(maxAge).build().toString();
    }

    public String clear() {
        return base("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite(sameSite).path("/");
    }

    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
