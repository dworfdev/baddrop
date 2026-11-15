package com.dworf.slots_demo.integration;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Emergency switch (maintenance mode)")
class MaintenanceTest extends IntegrationTestBase {

    @AfterEach
    void switchOff() {
        admin().post("/api/admin/maintenance", map("enabled", false));
    }

    @Test
    void offByDefaultAndConfigReportsIt() {
        ApiClient.Response r = anonymous().get("/api/config");
        assertEquals(200, r.status());
        assertEquals(Boolean.FALSE, r.json().get("maintenance"));
        assertEquals(200, anonymous().get("/api/cases").status());
    }

    @Test
    void whenOnPlayersAreBlockedButConfigAndAdminStayReachable() {
        ApiClient player = registerPlayer(); // log in first: an existing session must be blocked too

        ApiClient.Response on = admin().post("/api/admin/maintenance", map("enabled", true));
        assertEquals(200, on.status(), on.body());
        assertEquals(Boolean.TRUE, on.json().get("enabled"));

        ApiClient.Response cfg = anonymous().get("/api/config");
        assertEquals(200, cfg.status());
        assertEquals(Boolean.TRUE, cfg.json().get("maintenance"));

        for (ApiClient.Response r : new ApiClient.Response[]{
                player.get("/api/me"), anonymous().get("/api/cases"), anonymous().get("/api/live-feed"),
                anonymous().post("/api/auth/telegram", map("initData", "x"))}) {
            assertEquals(503, r.status(), r.body());
            assertTrue(r.body().contains("\"maintenance\":true"), r.body());
        }
        assertEquals(200, admin().get("/api/admin/bankroll").status(), "admin API must keep working");
        assertEquals(Boolean.TRUE, admin().get("/api/admin/maintenance").json().get("enabled"));

        assertEquals(200, admin().post("/api/admin/maintenance", map("enabled", false)).status());
        assertEquals(200, player.get("/api/me").status(), "after switching off the old session works again");
        assertFalse((Boolean) anonymous().get("/api/config").json().get("maintenance"));
    }

    @Test
    void onlyAdminCanToggle() {
        ApiClient player = registerPlayer();
        assertEquals(403, player.post("/api/admin/maintenance", map("enabled", true)).status());
        assertEquals(401, anonymous().post("/api/admin/maintenance", map("enabled", true)).status());
        assertEquals(Boolean.FALSE, anonymous().get("/api/config").json().get("maintenance"));
    }

    @Test
    void missingFlagIsRejected() {
        assertEquals(400, admin().post("/api/admin/maintenance", map()).status());
    }
}
