package com.dworf.slots_demo.security;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("security")
@DisplayName("Admin authentication: password + TOTP, no shared key, no backdoors")
class AdminAuthTest extends IntegrationTestBase {

    private String validTotp() {
        return Totp.code(ADMIN_TOTP_SECRET, Instant.now().getEpochSecond());
    }

    private ApiClient.Response login(String user, String pw, String totp) {
        return anonymous().post("/api/admin/auth/login", map("username", user, "password", pw, "totp", totp));
    }

    @Test
    void correctCredentialsAndCodeGrantAnAdminSession() {
        ApiClient.Response r = login(ADMIN_USER, ADMIN_PASSWORD, validTotp());
        assertEquals(200, r.status(), r.body());
        assertEquals("ADMIN", r.json().get("role"));
    }

    @Test
    void everySingleFactorFailureIsRejectedWithTheSameAnswer() {
        ApiClient.Response badUser = login("nobody", ADMIN_PASSWORD, validTotp());
        ApiClient.Response badPw = login(ADMIN_USER, "wrong-password", validTotp());
        ApiClient.Response badTotp = login(ADMIN_USER, ADMIN_PASSWORD, "000000".equals(validTotp()) ? "111111" : "000000");
        for (ApiClient.Response r : new ApiClient.Response[]{badUser, badPw, badTotp}) {
            assertEquals(401, r.status(), r.body());
        }
        assertEquals(badUser.message(), badPw.message(), "responses must not reveal which factor failed");
        assertEquals(badPw.message(), badTotp.message());
    }

    @Test
    void passwordAloneIsNotEnough() {
        assertEquals(401, anonymous().post("/api/admin/auth/login", map("username", ADMIN_USER, "password", ADMIN_PASSWORD)).status());
        assertEquals(401, login(ADMIN_USER, ADMIN_PASSWORD, "").status());
        assertEquals(401, login(ADMIN_USER, ADMIN_PASSWORD, "abcdef").status());
        assertEquals(401, login(ADMIN_USER, ADMIN_PASSWORD, "12345").status());
    }

    @Test
    void totpAloneIsNotEnough() {
        assertEquals(401, login(ADMIN_USER, "", validTotp()).status());
        assertEquals(401, login("", "", validTotp()).status());
    }

    @Test
    void aTotpCodeFarFromNowIsRejected() {
        String old = Totp.code(ADMIN_TOTP_SECRET, Instant.now().getEpochSecond() - 600);
        assertEquals(401, login(ADMIN_USER, ADMIN_PASSWORD, old).status());
    }

    @Test
    void blankBodyOrNullFieldsDoNotCrash() {
        assertEquals(401, anonymous().post("/api/admin/auth/login", "{}").status());
        assertEquals(400, anonymous().post("/api/admin/auth/login", "not json").status());
    }

    // ---- the old backdoors (C6) -------------------------------------------------------------

    @Test
    void theOldDefaultAdminPasswordGrantsNothing() {
        for (Map<String, String> headers : new Map[]{
                Map.of("X-Admin-Key", "admin1234"),
                Map.of("Authorization", "Bearer admin1234"),
                Map.of("Cookie", "ADMIN_SESSION=admin1234"),
                Map.of("Cookie", "BD_SESSION=admin1234")}) {
            ApiClient.Response r = anonymous().send("GET", "/api/admin/bankroll", null, headers, false);
            assertEquals(401, r.status(), "old-style admin credential accepted: " + headers);
        }
        assertTrue(anonymous().post("/api/admin/login", map("password", "admin1234")).status() >= 400);
        assertTrue(anonymous().post("/api/users/promo", map("promo", "admin1234")).status() >= 400);
    }

    @Test
    void adminSessionCannotActAsAPlayerAndViceVersa() {
        assertEquals(403, admin().get("/api/me/balance").status());
        assertEquals(403, registerPlayer().get("/api/admin/transactions/pending").status());
    }

    @Test
    void adminLogoutRevokesTheSession() {
        ApiClient a = loginAdmin();
        String cookie = a.sessionCookie();
        assertEquals(200, a.post("/api/admin/auth/logout", map()).status());
        ApiClient replay = anonymous();
        replay.setSessionCookie(cookie);
        assertEquals(401, replay.get("/api/admin/bankroll").status());
    }

    @Test
    void failedAndSuccessfulAdminLoginsAreAudited() {
        login(ADMIN_USER, "definitely-wrong", validTotp());
        loginAdmin();
        var audit = admin().get("/api/admin/audit").jsonList();
        assertTrue(audit.stream().anyMatch(a -> "ADMIN_LOGIN_FAILED".equals(a.get("action"))));
        assertTrue(audit.stream().anyMatch(a -> "ADMIN_LOGIN".equals(a.get("action"))));
    }

    @Test
    void adminPageIsServedToAdminsOnly() {
        ApiClient a = admin();
        int status = a.get("/admin").status();
        assertTrue(status == 200 || status == 404, "admin must pass the gate (404 only if templates/admin.html is missing): " + status);
        assertEquals(302, registerPlayer().get("/admin").status());
    }
}
