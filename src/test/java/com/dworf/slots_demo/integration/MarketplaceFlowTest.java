package com.dworf.slots_demo.integration;

import com.dworf.slots_demo.support.ApiClient;
import com.dworf.slots_demo.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.dworf.slots_demo.support.ApiClient.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Marketplace: catalogue, buy, inventory, open, instant open")
class MarketplaceFlowTest extends IntegrationTestBase {

    /** Cheapest active case, discovered from the live catalogue so the test does not hard-code seed data. */
    private Map<String, Object> cheapestCase() {
        List<Map<String, Object>> cases = anonymous().get("/api/cases").jsonList();
        assertFalse(cases.isEmpty(), "the catalogue must be seeded");
        return cases.stream().min((a, b) -> ApiClient.money(a.get("price")).compareTo(ApiClient.money(b.get("price")))).orElseThrow();
    }

    @Test
    void catalogueIsPublicAndNonEmpty() {
        ApiClient.Response r = anonymous().get("/api/cases");
        assertEquals(200, r.status());
        assertFalse(r.jsonList().isEmpty());
        assertEquals(200, anonymous().get("/api/cases/math").status());
    }

    @Test
    void buyDeductsPriceAndAddsToInventory() {
        Map<String, Object> c = cheapestCase();
        String slug = (String) c.get("slug");
        BigDecimal price = ApiClient.money(c.get("price"));
        ApiClient p = registerPlayer();
        fund(p, "50.00");

        ApiClient.Response r = p.post("/api/me/cases/buy", map("item", slug, "quantity", 3), idem());
        assertEquals(200, r.status(), r.body());
        assertEquals(new BigDecimal("50.00").subtract(price.multiply(BigDecimal.valueOf(3))).compareTo(balanceOf(p)), 0);
        List<?> items = (List<?>) p.get("/api/me/items").json().get("items");
        assertEquals(3, items.stream().filter(slug::equals).count());
    }

    @Test
    void buyFailsWithInsufficientFundsAndChangesNothing() {
        Map<String, Object> c = cheapestCase();
        ApiClient p = registerPlayer();
        ApiClient.Response r = p.post("/api/me/cases/buy", map("item", c.get("slug"), "quantity", 1), idem());
        assertEquals(400, r.status(), r.body());
        assertMoney("0.00", balanceOf(p));
        assertTrue(((List<?>) p.get("/api/me/items").json().get("items")).isEmpty());
    }

    @Test
    void quantityIsBoundedAndSlugIsValidated() {
        Map<String, Object> c = cheapestCase();
        ApiClient p = registerPlayer();
        fund(p, "100.00");
        assertEquals(400, p.post("/api/me/cases/buy", map("item", c.get("slug"), "quantity", 0), idem()).status());
        assertEquals(400, p.post("/api/me/cases/buy", map("item", c.get("slug"), "quantity", 6), idem()).status());
        assertEquals(400, p.post("/api/me/cases/buy", map("item", c.get("slug"), "quantity", -1), idem()).status());
        assertTrue(p.post("/api/me/cases/buy", map("item", "no-such-case", "quantity", 1), idem()).status() >= 400);
        assertEquals(400, p.post("/api/me/cases/buy", map("quantity", 1), idem()).status());
    }

    @Test
    void openingConsumesInventoryAndCreditsExactlyTheReportedWin() {
        Map<String, Object> c = cheapestCase();
        String slug = (String) c.get("slug");
        ApiClient p = registerPlayer();
        fund(p, "50.00");
        p.post("/api/me/cases/buy", map("item", slug, "quantity", 2), idem());
        BigDecimal before = balanceOf(p);

        ApiClient.Response r = p.post("/api/me/cases/open", map("item", slug, "quantity", 2));
        assertEquals(200, r.status(), r.body());
        BigDecimal won = ApiClient.money(r.json().get("totalWonAmount"));
        assertEquals(0, before.add(won).compareTo(balanceOf(p)), "balance must grow by exactly the reported win");
        assertTrue(((List<?>) p.get("/api/me/items").json().get("items")).isEmpty());
    }

    @Test
    void cannotOpenACaseYouDoNotOwn() {
        Map<String, Object> c = cheapestCase();
        ApiClient p = registerPlayer();
        fund(p, "50.00");
        assertEquals(400, p.post("/api/me/cases/open", map("item", c.get("slug"), "quantity", 1)).status());
    }

    @Test
    void cannotOpenMoreThanOwned() {
        Map<String, Object> c = cheapestCase();
        ApiClient p = registerPlayer();
        fund(p, "50.00");
        p.post("/api/me/cases/buy", map("item", c.get("slug"), "quantity", 1), idem());
        assertEquals(400, p.post("/api/me/cases/open", map("item", c.get("slug"), "quantity", 2)).status());
        assertEquals(1, ((List<?>) p.get("/api/me/items").json().get("items")).size(), "a failed open must not consume anything");
    }

    @Test
    void instantOpenChargesPriceAndPaysWinInOneStep() {
        Map<String, Object> c = cheapestCase();
        BigDecimal price = ApiClient.money(c.get("price"));
        ApiClient p = registerPlayer();
        fund(p, "50.00");
        ApiClient.Response r = p.post("/api/me/cases/instant-open", map("item", c.get("slug"), "quantity", 1), idem());
        assertEquals(200, r.status(), r.body());
        BigDecimal won = ApiClient.money(r.json().get("totalWonAmount"));
        assertEquals(0, new BigDecimal("50.00").subtract(price).add(won).compareTo(balanceOf(p)));
    }

    @Test
    void instantOpenNeedsFundsAndAnIdempotencyKey() {
        Map<String, Object> c = cheapestCase();
        ApiClient broke = registerPlayer();
        assertEquals(400, broke.post("/api/me/cases/instant-open", map("item", c.get("slug"), "quantity", 1), idem()).status());
        ApiClient rich = registerPlayer();
        fund(rich, "50.00");
        assertEquals(400, rich.post("/api/me/cases/instant-open", map("item", c.get("slug"), "quantity", 1)).status());
    }

    @Test
    void liveFeedShowsRecentOpeningsWithoutAuthentication() {
        Map<String, Object> c = cheapestCase();
        ApiClient p = registerPlayer();
        fund(p, "50.00");
        p.post("/api/me/cases/instant-open", map("item", c.get("slug"), "quantity", 1), idem());
        ApiClient.Response feed = anonymous().get("/api/live-feed");
        assertEquals(200, feed.status());
        assertFalse(feed.jsonList().isEmpty());
        assertTrue(feed.jsonList().size() <= 2);
    }

    @Test
    void leaderboardIsPublicButMasksUsernames() {
        ApiClient p = registerPlayer();
        fund(p, "20.00");
        ApiClient.Response r = anonymous().get("/api/leaderboard");
        assertEquals(200, r.status());
        String body = r.body();
        assertFalse(body.contains(p.get("/api/me").json().get("username").toString()), "the leaderboard must not expose full usernames");
        assertFalse(body.contains("\"id\""), "the leaderboard must not expose internal ids");
    }
}
