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
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        "slots.cookie.secure=true",   // the dev profile turns it off for plain http; SessionCookieTest asserts the production default
        "slots.bcrypt-strength=4",   // admin password hashing only
        "slots.auth.dev-login-enabled=false",
        "slots.admin.totp-replay-protection=false",
        "slots.admin.allow-bankroll-reset=false",
        "slots.limits.login-per-ip=100000",
        "slots.limits.admin-login-per-ip=100000"
})
public abstract class IntegrationTestBase {

    protected static final String ADMIN_USER = "testadmin";
    protected static final String ADMIN_PASSWORD = "Adm1n-Test-Pass!2026";
    protected static final String ADMIN_TOTP_SECRET = Totp.generateSecret();
    protected static final String BOT_TOKEN = "123456:TEST-BOT-TOKEN-for-junit";
    private static final java.util.concurrent.atomic.AtomicLong TG_IDS = new java.util.concurrent.atomic.AtomicLong(7_000_000_000L);
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> TG_ID_BY_USERNAME = new java.util.concurrent.ConcurrentHashMap<>();

    /** A valid mainnet TON address (CRC verified in TonAddressValidatorTest). */
    protected static final String TON_WALLET = "UQCys13azAWTFuERWwjc_5sqKZxQEhJhbtGGaiyp2Sf55Hrg";

    private static final AtomicInteger COUNTER = new AtomicInteger();
    /** One admin session per running application context (keyed by port), never shared across contexts. */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, ApiClient> SHARED_ADMIN = new java.util.concurrent.ConcurrentHashMap<>();

    @DynamicPropertySource
    static void baseProperties(DynamicPropertyRegistry r) {
        r.add("slots.telegram.bot-token", () -> BOT_TOKEN);
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

    /** Signed Mini App initData for the given Telegram user (fresh auth_date). */
    protected static String initData(long telegramId, String username) {
        return com.dworf.slots_demo.security.TelegramInitData.sign(
                BOT_TOKEN, telegramId, username, "Test", Instant.now().getEpochSecond());
    }

    /** Logs a player in through the real Telegram endpoint. The first call for a username allocates a Telegram id. */
    protected ApiClient registerPlayer(String username) {
        long tgId = TG_ID_BY_USERNAME.computeIfAbsent(username, u -> TG_IDS.incrementAndGet());
        return loginTelegram(tgId, username);
    }

    protected ApiClient registerPlayer() {
        return registerPlayer(uniqueUsername());
    }

    protected ApiClient loginTelegram(long telegramId, String username) {
        ApiClient c = anonymous();
        ApiClient.Response r = c.post("/api/auth/telegram", map("initData", initData(telegramId, username)));
        assertEquals(200, r.status(), "telegram login failed: " + r.body());
        c.setCsrf((String) r.json().get("csrfToken"));
        return c;
    }

    /** Same Telegram user logging in again (new session). */
    protected ApiClient loginPlayer(String username) {
        Long id = TG_ID_BY_USERNAME.get(username);
        if (id == null) throw new IllegalArgumentException("unknown test player " + username);
        return loginTelegram(id, username);
    }

    protected static long telegramIdOf(String username) {
        Long id = TG_ID_BY_USERNAME.get(username);
        if (id == null) throw new IllegalArgumentException("unknown test player " + username);
        return id;
    }

    protected static String freshReceipt() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
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
        assertNotNull(start.json().get("memo"), "deposit must carry a memo");
        ApiClient.Response confirm = player.post("/api/me/deposit/confirm", map("txHash", txHash, "receipt", freshReceipt()));
        assertEquals(200, confirm.status(), "deposit confirm failed: " + confirm.body());
        return admin().post("/api/admin/transactions/" + txHash + "/approve", map());
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
