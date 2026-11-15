package com.dworf.slots_demo.unit;

import com.dworf.slots_demo.service.AdminNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
@DisplayName("AdminNotifier")
class AdminNotifierTest {

    private static final class Capturing extends AdminNotifier {
        final CountDownLatch sent = new CountDownLatch(1);
        final AtomicReference<String> chat = new AtomicReference<>();
        final AtomicReference<String> text = new AtomicReference<>();
        boolean fail;

        Capturing(String token, String chatId) { super(token, chatId); }

        @Override
        protected void post(String botToken, String chatId, String message) throws Exception {
            chat.set(chatId);
            text.set(message);
            sent.countDown();
            if (fail) throw new java.io.IOException("boom with secret " + botToken);
        }
    }

    @Test
    void disabledWhenTokenOrChatMissing() {
        assertFalse(new AdminNotifier("", "1").enabled());
        assertFalse(new AdminNotifier("tok", "").enabled());
        assertFalse(new AdminNotifier(null, null).enabled());
        assertTrue(new AdminNotifier("tok", "1").enabled());
    }

    @Test
    void disabledNotifierSendsNothing() throws Exception {
        Capturing n = new Capturing("", "");
        n.notifyAfterCommit("hello");
        assertFalse(n.sent.await(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void enabledNotifierDeliversWithoutATransaction() throws Exception {
        Capturing n = new Capturing("tok", "42");
        n.notifyAfterCommit("New DEPOSIT");
        assertTrue(n.sent.await(2, TimeUnit.SECONDS));
        assertEquals("42", n.chat.get());
        assertEquals("New DEPOSIT", n.text.get());
    }

    @Test
    void aFailingSendNeverPropagates() throws Exception {
        Capturing n = new Capturing("tok", "42");
        n.fail = true;
        n.notifyAfterCommit("x"); // must not throw
        assertTrue(n.sent.await(2, TimeUnit.SECONDS));
    }
}
