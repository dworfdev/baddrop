package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.security.DepositMemo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DepositMemoTest {

    @Test
    void hasAFixedReadableShape() {
        for (int i = 0; i < 500; i++) {
            String memo = DepositMemo.generate();
            assertEquals(10, memo.length());
            assertTrue(memo.matches("BD[A-HJ-NP-Z2-9]{8}"), memo);
        }
    }

    @Test
    void doesNotRepeatInPractice() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) seen.add(DepositMemo.generate());
        assertEquals(20_000, seen.size());
    }
}
