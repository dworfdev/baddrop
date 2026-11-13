package com.dworf.slots_demo.security;

import java.text.Normalizer;
import java.util.Locale;

/** Derives a valid in-game username (see {@link UsernamePolicy}: a-z, 0-9, underscore, 3-24 chars) from Telegram data. */
public final class TelegramUsernames {
    private static final int MAX = 24;

    private TelegramUsernames() {}

    /** Prefers the @username, then the first name, then {@code tg<id>}. Never returns something UsernamePolicy rejects. */
    public static String derive(String telegramUsername, String firstName, long telegramId) {
        String name = sanitize(telegramUsername);
        if (name.length() < 3) name = sanitize(firstName);
        if (name.length() < 3) name = "tg" + telegramId;
        if (name.length() > MAX) name = name.substring(0, MAX);
        return UsernamePolicy.validate(name);
    }

    /** Same, with a numeric tail used when the plain name is already taken by another Telegram account. */
    public static String withSuffix(String base, long telegramId, int attempt) {
        String tail = "_" + (attempt == 0 ? Long.toString(Math.abs(telegramId) % 10000) : Long.toString(Math.abs(telegramId * 31 + attempt) % 1000000));
        String head = base.length() + tail.length() > MAX ? base.substring(0, MAX - tail.length()) : base;
        return UsernamePolicy.validate(head + tail);
    }

    private static String sanitize(String raw) {
        if (raw == null) return "";
        String n = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (char c : n.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') sb.append(c);
        }
        return sb.toString();
    }
}
