package com.dworf.slots_demo.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** One cookie for both roles. HttpOnly + SameSite=Strict always; Secure unless explicitly disabled for local HTTP. */
@Component
public class SessionCookies {
    public static final String NAME = "BD_SESSION";

    private final boolean secure;

    public SessionCookies(@Value("${slots.cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    public String issue(String rawToken, Duration maxAge) {
        return base(rawToken).maxAge(maxAge).build().toString();
    }

    public String clear() {
        return base("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Strict").path("/");
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
