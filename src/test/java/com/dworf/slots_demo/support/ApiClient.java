package com.dworf.slots_demo.support;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal real-HTTP test client. It handles the BD_SESSION cookie and the CSRF token by hand
 * (instead of a CookieManager) so tests can also send requests with the cookie but WITHOUT the
 * CSRF header, with a forged Origin, etc.
 */
public final class ApiClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record Response(int status, String body, Map<String, List<String>> headers) {
        @SuppressWarnings("unchecked")
        public Map<String, Object> json() {
            try {
                return body == null || body.isBlank() ? Map.of() : MAPPER.readValue(body, Map.class);
            } catch (RuntimeException e) {
                throw new AssertionError("Response is not a JSON object: " + body, e);
            }
        }

        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> jsonList() {
            return MAPPER.readValue(body, List.class);
        }

        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }

        public List<String> headerAll(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).toList();
        }

        public String message() {
            Object m = json().get("message");
            return m == null ? "" : m.toString();
        }
    }

    private final String baseUrl;
    private String sessionCookie;
    private String csrf;

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public String sessionCookie() { return sessionCookie; }
    public String csrf() { return csrf; }
    public void setSessionCookie(String c) { this.sessionCookie = c; }
    public void setCsrf(String c) { this.csrf = c; }

    /** Copy of this client's credentials (cookie + csrf) into a fresh client, e.g. to replay a stolen cookie. */
    public ApiClient copy(int port) {
        ApiClient c = new ApiClient(port);
        c.sessionCookie = sessionCookie;
        c.csrf = csrf;
        return c;
    }

    public Response get(String path) { return send("GET", path, null, Map.of(), true); }

    public Response post(String path, Object body) { return send("POST", path, body, Map.of(), true); }

    public Response post(String path, Object body, Map<String, String> headers) { return send("POST", path, body, headers, true); }

    public Response postWithoutCsrf(String path, Object body) { return send("POST", path, body, Map.of(), false); }

    public Response send(String method, String path, Object body, Map<String, String> extraHeaders, boolean withCsrf) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json");
            String payload = body == null ? null : (body instanceof String s ? s : MAPPER.writeValueAsString(body));
            if (payload != null) b.header("Content-Type", "application/json");
            if (sessionCookie != null) b.header("Cookie", "BD_SESSION=" + sessionCookie);
            if (withCsrf && csrf != null && !"GET".equals(method)) b.header("X-CSRF-Token", csrf);
            extraHeaders.forEach(b::header);
            b.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload));
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            Response res = new Response(r.statusCode(), r.body(), r.headers().map());
            captureCookie(res);
            return res;
        } catch (IOException e) {
            throw new AssertionError("HTTP failure on " + method + " " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private void captureCookie(Response res) {
        for (String sc : res.headerAll("Set-Cookie")) {
            if (sc.startsWith("BD_SESSION=")) {
                String value = sc.substring("BD_SESSION=".length(), sc.indexOf(';') < 0 ? sc.length() : sc.indexOf(';'));
                sessionCookie = value.isEmpty() ? null : value;
            }
        }
    }

    // ---- small JSON helpers used across tests ----

    public static BigDecimal money(Object v) {
        return new BigDecimal(String.valueOf(v));
    }

    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
