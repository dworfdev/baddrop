package com.dworf.slots_demo.support;

import com.dworf.slots_demo.security.Totp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base for every test that boots the app. Uses real HTTP on a random port.
 * Only API surface used: @SpringBootTest, @ActiveProfiles, @DynamicPropertySource, @Value("${local.server.port}")
 * (kept deliberately small so it survives Spring Boot 4 test-module moves).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// Static defaults live here so a subclass can override them with its own @TestPropertySource
// (brute-force limits and TOTP replay protection are exercised by dedicated test classes with their own context).
@TestPropertySource(properties = {
        "slots.bcrypt-strength=4",   // tests register hundreds of users; production default is 12
        "slots.admin.totp-replay-protection=false",
        "slots.admin.allow-bankroll-reset=false",
        "slots.limits.login-per-ip=100000",
        "slots.limits.login-per-user-ip=100000",
        "slots.limits.register-per-ip=100000",
        "slots.limits.admin-login-per-ip=100000",
        "slots.limits.password-check-per-user=100000"
})
public abstract class IntegrationTestBase {

    protected static final String ADMIN_USER = "testadmin";
    protected static final String ADMIN_PASSWORD = "Adm1n-Test-Pass!2026";
    protected static final String ADMIN_TOTP_SECRET = Totp.generateSecret();
    protected static final String PLAYER_PASSWORD = "correct-horse-battery";

    /** A valid mainnet TON address (CRC verified in TonAddressValidatorTest). */
    protected static final String TON_WALLET = "UQCys13azAWTFuERWwjc_5sqKZxQEhJhbtGGaiyp2Sf55Hrg";

    private static final AtomicInteger COUNTER = new AtomicInteger();
    /** One admin session per running application context (keyed by port), never shared across contexts. */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, ApiClient> SHARED_ADMIN = new java.util.concurrent.ConcurrentHashMap<>();

    @DynamicPropertySource
    static void baseProperties(DynamicPropertyRegistry r) {
        r.add("slots.admin.username", () -> ADMIN_USER);
        r.add("slots.admin.password-hash", () -> new BCryptPasswordEncoder(4).encode(ADMIN_PASSWORD));
        r.add("slots.admin.totp-secret", () -> ADMIN_TOTP_SECRET);
    }

    @Value("${local.server.port}")
    protected int port;

    protected ApiClient anonymous() {
        return new ApiClient(port);
    }

    protected static String uniqueUsername() {
        return "u" + Long.toString(System.nanoTime(), 36) + COUNTER.incrementAndGet();
    }

    protected static String freshKey() {
        return UUID.randomUUID().toString();
    }

    protected static Map<String, String> idem() {
        return Map.of("Idempotency-Key", freshKey());
    }

    /** Registers a new player and returns a client that carries the session cookie and CSRF token. */
    protected ApiClient registerPlayer(String username) {
        ApiClient c = anonymous();
        ApiClient.Response r = c.post("/api/auth/register", map("username", username, "password", PLAYER_PASSWORD));
        assertEquals(201, r.status(), "register failed: " + r.body());
        c.setCsrf((String) r.json().get("csrfToken"));
        return c;
    }

    protected ApiClient registerPlayer() {
        return registerPlayer(uniqueUsername());
    }

    protected ApiClient loginPlayer(String username, String password) {
        ApiClient c = anonymous();
        ApiClient.Response r = c.post("/api/auth/login", map("username", username, "password", password));
        assertEquals(200, r.status(), "login failed: " + r.body());
        c.setCsrf((String) r.json().get("csrfToken"));
        return c;
    }

    /** Fresh admin login (valid TOTP). */
    protected ApiClient loginAdmin() {
        ApiClient c = anonymous();
        ApiClient.Response r = c.post("/api/admin/auth/login", map(
                "username", ADMIN_USER, "password", ADMIN_PASSWORD,
                "totp", Totp.code(ADMIN_TOTP_SECRET, Instant.now().getEpochSecond())));
        assertEquals(200, r.status(), "admin login failed: " + r.body());
        c.setCsrf((String) r.json().get("csrfToken"));
        return c;
    }

    /** Shared admin session for this application context (a copy, so callers cannot clobber it). */
    protected ApiClient admin() {
        ApiClient a = SHARED_ADMIN.computeIfAbsent(port, p -> loginAdmin());
        ApiClient copy = anonymous();
        copy.setSessionCookie(a.sessionCookie());
        copy.setCsrf(a.csrf());
        return copy;
    }

    /** Full deposit flow: start -> "I sent it" -> admin approves with an on-chain hash. Returns the approval response. */
    protected ApiClient.Response fund(ApiClient player, String amount) {
        ApiClient.Response start = player.post("/api/me/deposit/start", map("amount", new BigDecimal(amount)), idem());
        assertEquals(200, start.status(), "deposit start failed: " + start.body());
        String txHash = (String) start.json().get("txHash");
        ApiClient.Response confirm = player.post("/api/me/deposit/confirm", map("txHash", txHash));
        assertEquals(200, confirm.status(), "deposit confirm failed: " + confirm.body());
        return admin().post("/api/admin/transactions/" + txHash + "/approve",
                map("externalTxHash", "ext" + UUID.randomUUID().toString().replace("-", "")));
    }

    protected BigDecimal balanceOf(ApiClient player) {
        ApiClient.Response r = player.get("/api/me/balance");
        assertEquals(200, r.status(), r.body());
        return ApiClient.money(r.json().get("balance"));
    }

    protected static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }
}
