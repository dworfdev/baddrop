package com.dworf.slots_demo.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * H22/C1: verifies Telegram Mini App {@code initData} (the signed query string the Mini App SDK hands to the page).
 * <p>
 * Algorithm (core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app):
 * secret = HMAC_SHA256(key = "WebAppData", message = botToken);
 * hash   = hex(HMAC_SHA256(key = secret, message = data_check_string)), where data_check_string is every
 * received field except {@code hash}, sorted by key, as {@code key=value} lines joined by '\n'.
 * Pure Java, no Spring, so it can be unit-tested without a container.
 */
public final class TelegramInitData {

    /** The identity Telegram vouches for. {@code username} and {@code firstName} may be null. */
    public record Verified(long userId, String username, String firstName, long authDate) {}

    /** Clock skew tolerated for an auth_date that lies in the future. */
    private static final long FUTURE_SKEW_SECONDS = 60;
    private static final int MAX_LENGTH = 4096;

    private TelegramInitData() {}

    /**
     * @throws IllegalArgumentException when the data is missing, malformed, badly signed or too old
     *                                  (the message is for logs only; never echo it to the client)
     * @throws IllegalStateException    when no bot token is configured
     */
    public static Verified verify(String initData, String botToken, long nowEpochSeconds, long maxAgeSeconds) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("Telegram bot token is not configured");
        }
        if (initData == null || initData.isBlank() || initData.length() > MAX_LENGTH) {
            throw bad("missing or oversized initData");
        }

        Map<String, String> fields = new TreeMap<>();
        for (String pair : initData.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) throw bad("malformed pair");
            String key = decode(pair.substring(0, eq));
            String value = decode(pair.substring(eq + 1));
            if (fields.put(key, value) != null) throw bad("duplicate field " + key);
        }

        String hash = fields.remove("hash");
        if (hash == null) throw bad("no hash");
        byte[] given = hex(hash);

        StringBuilder check = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (check.length() > 0) check.append('\n');
            check.append(e.getKey()).append('=').append(e.getValue());
        }
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
        byte[] expected = hmac(secret, check.toString().getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(expected, given)) throw bad("signature mismatch");

        long authDate = parseLong(fields.get("auth_date"), "auth_date");
        long age = nowEpochSeconds - authDate;
        if (age > maxAgeSeconds) throw bad("initData is too old");
        if (age < -FUTURE_SKEW_SECONDS) throw bad("auth_date is in the future");

        String userJson = fields.get("user");
        if (userJson == null) throw bad("no user");
        Map<String, String> user = MiniJson.flatObject(userJson);
        long id = parseLong(user.get("id"), "user.id");
        if (id <= 0) throw bad("invalid user id");
        if ("true".equals(user.get("is_bot"))) throw bad("bots cannot log in");
        return new Verified(id, blankToNull(user.get("username")), blankToNull(user.get("first_name")), authDate);
    }

    /** Builds a correctly signed initData string. Used by tests and dev tooling; never by production code paths. */
    public static String sign(String botToken, long userId, String username, String firstName, long authDate) {
        StringBuilder user = new StringBuilder("{\"id\":").append(userId);
        if (firstName != null) user.append(",\"first_name\":\"").append(MiniJson.escape(firstName)).append('"');
        if (username != null) user.append(",\"username\":\"").append(MiniJson.escape(username)).append('"');
        user.append('}');
        return sign(botToken, user.toString(), authDate);
    }

    /** Same, with the raw {@code user} JSON supplied by the caller. */
    public static String sign(String botToken, String userJson, long authDate) {
        Map<String, String> fields = new TreeMap<>();
        fields.put("auth_date", Long.toString(authDate));
        fields.put("query_id", "AAHtest");
        fields.put("user", userJson);

        StringBuilder check = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (check.length() > 0) check.append('\n');
            check.append(e.getKey()).append('=').append(e.getValue());
        }
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
        String hash = toHex(hmac(secret, check.toString().getBytes(StandardCharsets.UTF_8)));

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            parts.add(encode(e.getKey()) + "=" + encode(e.getValue()));
        }
        parts.add("hash=" + hash);
        return String.join("&", parts);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw bad("bad percent-encoding");
        }
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static byte[] hex(String s) {
        if (s.length() != 64) throw bad("bad hash length");
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) throw bad("bad hash characters");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        return sb.toString();
    }

    private static long parseLong(String s, String what) {
        if (s == null) throw bad("missing " + what);
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw bad("bad " + what);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static IllegalArgumentException bad(String why) {
        return new IllegalArgumentException("Invalid Telegram data: " + why);
    }
}
