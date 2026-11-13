package com.dworf.slots_demo.security;

import java.util.Base64;

/** C8/H16: validates TON user-friendly addresses (36 bytes, CRC16-XMODEM over the first 34). */
public final class TonAddressValidator {
    private TonAddressValidator() {}

    public static boolean isValid(String address) {
        if (address == null) return false;
        String a = address.trim();
        if (a.length() != 48) return false;
        byte[] raw;
        try {
            raw = decode(a);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (raw.length != 36) return false;
        int tag = raw[0] & 0xFF;
        // 0x11 bounceable, 0x51 non-bounceable; 0x80 bit = testnet
        int base = tag & 0x7F;
        if (base != 0x11 && base != 0x51) return false;
        int wc = raw[1];
        if (wc != 0 && wc != -1) return false;
        int crc = crc16(raw, 34);
        return ((raw[34] & 0xFF) << 8 | (raw[35] & 0xFF)) == crc;
    }

    /** Mainnet only (no testnet flag). */
    public static boolean isMainnet(String address) {
        if (!isValid(address)) return false;
        return (decode(address.trim())[0] & 0x80) == 0;
    }

    private static byte[] decode(String a) {
        String std = a.replace('-', '+').replace('_', '/');
        return Base64.getDecoder().decode(std);
    }

    static int crc16(byte[] data, int len) {
        int crc = 0;
        for (int i = 0; i < len; i++) {
            crc ^= (data[i] & 0xFF) << 8;
            for (int b = 0; b < 8; b++) {
                crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) : (crc << 1);
                crc &= 0xFFFF;
            }
        }
        return crc;
    }
}
