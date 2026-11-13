package com.dworf.slots_demo.security;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** C3/H21: password strength rules. BCrypt truncates at 72 bytes, so the upper bound is in bytes. */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "1234567890", "qwertyuiop", "admin1234", "letmein123",
            "0123456789", "iloveyou12", "baddrop123", "1q2w3e4r5t");

    private PasswordPolicy() {}

    public static void validate(String password, String username) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Password must be at most " + MAX_BYTES + " bytes");
        }
        String lower = password.toLowerCase();
        if (COMMON.contains(lower)) {
            throw new IllegalArgumentException("Password is too common");
        }
        if (username != null && !username.isBlank() && lower.contains(username.toLowerCase())) {
            throw new IllegalArgumentException("Password must not contain the username");
        }
        boolean allSame = password.chars().distinct().count() == 1;
        if (allSame) {
            throw new IllegalArgumentException("Password is too weak");
        }
    }
}
