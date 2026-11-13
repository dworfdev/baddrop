package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.security.TonTxHash;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class TonTxHashTest {

    private static final String HEX_SEQ = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static final String B64_SEQ = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String HEX_FB = "fb".repeat(32);

    @Test
    void acceptsHexAndLowerCasesIt() {
        assertEquals(HEX_SEQ, TonTxHash.normalize(HEX_SEQ));
        assertEquals(HEX_SEQ, TonTxHash.normalize("  " + HEX_SEQ.toUpperCase() + " "));
    }

    @Test
    void base64AndHexOfTheSameTransactionCollapseToOneForm() {
        assertEquals(HEX_SEQ, TonTxHash.normalize(B64_SEQ));
        assertEquals(HEX_SEQ, TonTxHash.normalize(B64_SEQ.substring(0, 43)));      // padding stripped
        assertEquals(HEX_FB, TonTxHash.normalize("+/v7+/v7+/v7+/v7+/v7+/v7+/v7+/v7+/v7+/v7+/s="));   // standard alphabet
        assertEquals(HEX_FB, TonTxHash.normalize("-_v7-_v7-_v7-_v7-_v7-_v7-_v7-_v7-_v7-_v7-_s="));   // url-safe alphabet
    }

    @Test
    void extractsTheHashFromExplorerLinks() {
        assertEquals(HEX_SEQ, TonTxHash.normalize("https://tonviewer.com/transaction/" + HEX_SEQ));
        assertEquals(HEX_SEQ, TonTxHash.normalize("https://tonviewer.com/transaction/" + HEX_SEQ + "/"));
        assertEquals(HEX_SEQ, TonTxHash.normalize("https://tonscan.org/tx/" + HEX_SEQ + "?utm=x#top"));
        assertEquals(HEX_SEQ, TonTxHash.normalize("tonviewer.com/transaction/" + HEX_SEQ));                       // no scheme
        assertEquals(HEX_SEQ, TonTxHash.normalize("https://tonscan.org/tx/" + B64_SEQ));
        assertEquals(HEX_FB, TonTxHash.normalize("https://tonscan.org/tx/+/v7+/v7+/v7+/v7+/v7+/v7+/v7+/v7+/v7+/v7+/s="));
        assertEquals(HEX_FB, TonTxHash.normalize("https://tonscan.org/tx/%2B%2Fv7%2B%2Fv7%2B%2Fv7%2B%2Fv7%2B%2Fv7%2B%2Fv7%2B%2Fv7%2B%2Fv7%2B%2Fv7%2B%2Fv7%2B%2Fs%3D"));
    }

    @Test
    void rejectsEverythingElse() {
        for (String bad : new String[]{"", "   ", "abc", "0".repeat(63), "0".repeat(65), "g".repeat(64),
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHg==", // 31 bytes
                "https://tonviewer.com/transaction/", "https://example.com/", "BD7K2M9QXA", "x".repeat(400)}) {
            assertThrows(IllegalArgumentException.class, () -> TonTxHash.normalize(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> TonTxHash.normalize(null));
    }
}
