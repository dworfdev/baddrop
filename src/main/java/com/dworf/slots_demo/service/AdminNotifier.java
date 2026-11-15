package com.dworf.slots_demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pushes "new deposit / withdrawal request" messages to the owner through a SEPARATE Telegram bot
 * (not the player-facing one). Fire-and-forget: it runs on its own thread, only after the database transaction
 * has committed, and a failure is logged without ever affecting the player's request. Disabled when the token or
 * the chat id is empty. The bot token is part of the request URL, so nothing from the URL is ever logged.
 */
@Service
public class AdminNotifier {
    private static final Logger log = LoggerFactory.getLogger(AdminNotifier.class);

    private final String token;
    private final String chatId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "admin-notifier");
        t.setDaemon(true);
        return t;
    });

    public AdminNotifier(@Value("${slots.notify.bot-token:}") String token,
                         @Value("${slots.notify.chat-id:}") String chatId) {
        this.token = token == null ? "" : token.trim();
        this.chatId = chatId == null ? "" : chatId.trim();
        if (!enabled()) {
            log.info("Admin notifications are disabled (slots.notify.bot-token / slots.notify.chat-id not set)");
        }
    }

    public boolean enabled() {
        return !token.isEmpty() && !chatId.isEmpty();
    }

    /** Sends once the surrounding transaction commits (immediately when there is none). Never throws. */
    public void notifyAfterCommit(String text) {
        if (!enabled()) return;
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() { submit(text); }
                });
            } else {
                submit(text);
            }
        } catch (RuntimeException e) {
            log.warn("Admin notification was not queued: {}", e.getClass().getSimpleName());
        }
    }

    private void submit(String text) {
        try {
            executor.execute(() -> {
                try {
                    post(token, chatId, text);
                } catch (Exception e) {
                    log.warn("Admin notification failed: {}", e.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException e) {
            log.warn("Admin notification was not queued: {}", e.getClass().getSimpleName());
        }
    }

    /** Overridable in tests so no real HTTP call is made. */
    protected void post(String botToken, String chat, String text) throws Exception {
        String form = "chat_id=" + URLEncoder.encode(chat, StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&disable_web_page_preview=true";
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + botToken + "/sendMessage"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
        if (res.statusCode() != 200) {
            log.warn("Admin notification rejected by Telegram: HTTP {}", res.statusCode());
        }
    }
}
