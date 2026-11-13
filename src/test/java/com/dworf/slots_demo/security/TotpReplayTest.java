package com.dworf.slots_demo.security;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Own application context: replay protection is ON here (the shared test context turns it off). */
@Tag("security")
@TestPropertySource(properties = "slots.admin.totp-replay-protection=true")
class TotpReplayTest extends IntegrationTestBase {

    @Test
    void aTotpCodeCanBeUsedOnlyOnce() {
        String code = Totp.code(ADMIN_TOTP_SECRET, Instant.now().getEpochSecond());
        var body = map("username", ADMIN_USER, "password", ADMIN_PASSWORD, "totp", code);
        ApiClient.Response first = anonymous().post("/api/admin/auth/login", body);
        ApiClient.Response replay = anonymous().post("/api/admin/auth/login", body);
        // If the 30-second step rolled over between the calls the replay may legitimately differ, so only the
        // first outcome is hard-asserted; the replay must never succeed with the SAME code.
        assertEquals(200, first.status(), first.body());
        assertEquals(401, replay.status(), "a captured (password, code) pair must not be replayable");
    }
}
