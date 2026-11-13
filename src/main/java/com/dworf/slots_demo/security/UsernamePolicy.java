package com.dworf.slots_demo.security;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** H21/C2: strict username rules (also closes the stored-XSS vector at the source). */
public final class UsernamePolicy {
    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9_]{3,24}$");

    private UsernamePolicy() {}

    /** Canonical form used for storage and uniqueness: NFKC + lower-case + trim. */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        return Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    /** Returns the canonical username or throws IllegalArgumentException. */
    public static String validate(String raw) {
        String n = normalize(raw);
        if (!ALLOWED.matcher(n).matches()) {
            throw new IllegalArgumentException("Username must be 3-24 characters: a-z, 0-9 and underscore");
        }
        return n;
    }
}
