package com.dworf.slots_demo.security;

import java.security.SecureRandom;

/** C8: a short unique code the player must put into the transfer comment, so every payment can be matched to a deposit. */
public final class DepositMemo {
    /** No 0/O/1/I: players retype this from a screen. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private DepositMemo() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder("BD");
        for (int i = 0; i < 8; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
