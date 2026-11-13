package com.dworf.slots_demo.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** C6: RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30s). */
public final class Totp {
    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private Totp() {}

    public static String generateSecret() {
        byte[] b = new byte[20];
        new SecureRandom().nextBytes(b);
        return base32Encode(b);
    }

    public static String code(String base32Secret, long epochSeconds) {
        return codeForCounter(base32Decode(base32Secret), epochSeconds / 30);
    }

    /** Accepts the current step +/- {@code window} steps; constant-time compare. */
    public static boolean verify(String base32Secret, String submitted, long epochSeconds, int window) {
        if (submitted == null || !submitted.matches("\\d{6}")) return false;
        byte[] key = base32Decode(base32Secret);
        long step = epochSeconds / 30;
        boolean ok = false;
        for (long s = step - window; s <= step + window; s++) {
            String expected = codeForCounter(key, s);
            ok |= MessageDigest.isEqual(expected.getBytes(), submitted.getBytes());
        }
        return ok;
    }

    static String codeForCounter(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int off = h[h.length - 1] & 0x0F;
            int bin = ((h[off] & 0x7F) << 24) | ((h[off + 1] & 0xFF) << 16)
                    | ((h[off + 2] & 0xFF) << 8) | (h[off + 3] & 0xFF);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buf = 0, bits = 0;
        for (byte x : data) {
            buf = (buf << 8) | (x & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32.charAt((buf >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(B32.charAt((buf << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] base32Decode(String s) {
        String in = s.replace("=", "").replace(" ", "").toUpperCase();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buf = 0, bits = 0;
        for (char c : in.toCharArray()) {
            int v = B32.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Invalid base32");
            buf = (buf << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buf >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
