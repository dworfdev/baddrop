package com.dworf.slots_demo.security;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Just enough JSON to read the flat {@code user} object Telegram puts in initData: top-level scalars are returned
 * as text (strings unescaped, numbers/booleans/null as written); nested objects and arrays are skipped.
 * Kept dependency-free on purpose so the signature check stays unit-testable without Jackson.
 */
final class MiniJson {
    private MiniJson() {}

    static Map<String, String> flatObject(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        int[] p = {0};
        skipWs(json, p);
        expect(json, p, '{');
        skipWs(json, p);
        if (peek(json, p) == '}') {
            return out;
        }
        while (true) {
            skipWs(json, p);
            String key = readString(json, p);
            skipWs(json, p);
            expect(json, p, ':');
            skipWs(json, p);
            char c = peek(json, p);
            if (c == '"') {
                out.put(key, readString(json, p));
            } else if (c == '{' || c == '[') {
                skipNested(json, p);
            } else {
                int start = p[0];
                while (p[0] < json.length() && ",}".indexOf(json.charAt(p[0])) < 0) p[0]++;
                out.put(key, json.substring(start, p[0]).trim());
            }
            skipWs(json, p);
            char d = next(json, p);
            if (d == '}') return out;
            if (d != ',') throw new IllegalArgumentException("Invalid JSON");
        }
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static String readString(String s, int[] p) {
        expect(s, p, '"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next(s, p);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = next(s, p);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (p[0] + 4 > s.length()) throw new IllegalArgumentException("Invalid JSON");
                    try {
                        sb.append((char) Integer.parseInt(s.substring(p[0], p[0] + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Invalid JSON");
                    }
                    p[0] += 4;
                }
                default -> throw new IllegalArgumentException("Invalid JSON");
            }
        }
    }

    private static void skipNested(String s, int[] p) {
        int depth = 0;
        do {
            char c = peek(s, p);
            if (c == '"') {
                readString(s, p);
                continue;
            }
            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;
            p[0]++;
        } while (depth > 0);
    }

    private static void skipWs(String s, int[] p) {
        while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++;
    }

    private static char peek(String s, int[] p) {
        if (p[0] >= s.length()) throw new IllegalArgumentException("Invalid JSON");
        return s.charAt(p[0]);
    }

    private static char next(String s, int[] p) {
        char c = peek(s, p);
        p[0]++;
        return c;
    }

    private static void expect(String s, int[] p, char want) {
        if (next(s, p) != want) throw new IllegalArgumentException("Invalid JSON");
    }
}
