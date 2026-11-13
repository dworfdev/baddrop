package com.dworf.slots_demo.security;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * C8: turns whatever a player pastes (a hash in hex or base64, or an explorer link such as
 * https://tonviewer.com/transaction/&lt;hash&gt;) into ONE canonical form: 64 lower-case hex characters.
 * One canonical form matters: the same transaction written two ways must not slip past the uniqueness check.
 */
public final class TonTxHash {
    private static final String[] PATH_MARKERS = {"/transaction/", "/transactions/", "/tx/"};

    private TonTxHash() {}

    /** @throws IllegalArgumentException when the input is not a 32-byte transaction hash in a known encoding */
    public static String normalize(String raw) {
        if (raw == null) throw invalid();
        String s = raw.trim();
        if (s.isEmpty() || s.length() > 300) throw invalid();

        // 1) the whole string first: standard base64 legitimately contains '/', so it must not be mistaken for a link
        String direct = parse(s);
        if (direct != null) return direct;

        // 2) otherwise it must be a link: drop query/fragment, then take what follows the explorer's path marker
        String link = s;
        for (char stop : new char[]{'?', '#'}) {
            int i = link.indexOf(stop);
            if (i >= 0) link = link.substring(0, i);
        }
        while (link.endsWith("/")) link = link.substring(0, link.length() - 1);
        String tail = null;
        for (String marker : PATH_MARKERS) {
            int i = link.lastIndexOf(marker);
            if (i >= 0) {
                tail = link.substring(i + marker.length());
                break;
            }
        }
        if (tail == null) {
            int slash = link.lastIndexOf('/');
            if (slash < 0) throw invalid();
            tail = link.substring(slash + 1);
        }
        try {
            tail = URLDecoder.decode(tail.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        String viaLink = parse(tail);
        if (viaLink == null) throw invalid();
        return viaLink;
    }

    /** Canonical hex for a bare hash (hex or base64/base64url of 32 bytes), or null when it is neither. */
    private static String parse(String s) {
        if (s.length() == 64 && s.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            return s.toLowerCase(Locale.ROOT);
        }
        if ((s.length() == 44 || s.length() == 43) && s.matches("[A-Za-z0-9+/_=-]+")) {
            try {
                byte[] bytes = (s.contains("-") || s.contains("_"))
                        ? Base64.getUrlDecoder().decode(s)
                        : Base64.getDecoder().decode(s);
                if (bytes.length == 32) {
                    StringBuilder sb = new StringBuilder(64);
                    for (byte b : bytes) sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
                    return sb.toString();
                }
            } catch (IllegalArgumentException ignored) {
                // not valid base64
            }
        }
        return null;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Enter the transaction hash (64 hex characters) or the explorer link to your payment");
    }
}
