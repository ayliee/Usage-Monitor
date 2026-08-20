/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Zero-dependency Discord webhook client. The plugin posts/updates ONE message
 * in ONE channel using a webhook URL — no bot token, no permissions, no slash
 * commands. Create a webhook in your channel settings and paste the URL into
 * config.yml.
 *
 * Flow:
 *   1. First run: POST the webhook with ?wait=true → Discord returns the
 *      posted message, we extract the id and persist it.
 *   2. Subsequent runs: PATCH /webhooks/{id}/{token}/messages/{messageId}
 *      to edit the same message. The webhook owns its own message and can
 *      edit/delete it without any extra permissions.
 *
 * Rate limits (webhook-specific):
 *   - 30 messages per 60 seconds per webhook (avg 1 per 2s)
 *   - 5 edits per 5 seconds per webhook
 *   At a 5s refresh interval we use 1 edit / 5s = 12/min, well under both.
 */
public class DiscordClient {

    private static final String WEBHOOK_BASE = "https://discord.com/api/webhooks";
    private static final String USER_AGENT = "UsageMonitor-MinecraftPlugin-Webhook (1.2.0)";

    private final HttpClient httpClient;
    private final Logger logger;

    public DiscordClient(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    /**
     * Posts a new message via webhook. Uses ?wait=true so Discord returns the
     * created message (including its id) in the response body.
     *
     * @return the new message id, or null on failure
     */
    public CompletableFuture<String> postWebhookMessage(String webhookId, String webhookToken, String jsonPayload) {
        if (webhookId == null || webhookToken == null) {
            return CompletableFuture.completedFuture(null);
        }
        String url = WEBHOOK_BASE + "/" + webhookId + "/" + webhookToken + "?wait=true";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        String id = JsonObjectBuilder.extractStringField(response.body(), "id");
                        logger.info("[Usage Monitor] Webhook message created (id=" + id + ")");
                        return id;
                    } else {
                        logger.warning("[Usage Monitor] Webhook POST failed: HTTP " + response.statusCode() +
                                " - " + truncate(response.body(), 200));
                        return null;
                    }
                })
                .exceptionally(ex -> {
                    logger.log(Level.WARNING, "[Usage Monitor] Webhook POST error: " + ex.getMessage(), ex);
                    return null;
                });
    }

    /**
     * Edits an existing webhook message. Returns:
     *   true  — edit succeeded (2xx) or rate-limited (429, keep id)
     *   false — message definitively not found (404), invalid webhook (401/403),
     *           or any other non-recoverable error
     *
     * The caller keeps the same message-id and retries on next cycle on false.
     * We never auto-repost because that would create a new message every failure
     * — the exact bug this plugin is designed to avoid.
     */
    public CompletableFuture<Boolean> editWebhookMessage(String webhookId, String webhookToken,
                                                        String messageId, String jsonPayload) {
        if (webhookId == null || webhookToken == null || messageId == null || messageId.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        String url = WEBHOOK_BASE + "/" + webhookId + "/" + webhookToken + "/messages/" + messageId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(8))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return true;
                    } else if (response.statusCode() == 404) {
                        logger.warning("[Usage Monitor] Webhook message " + messageId +
                                " not found (deleted?). Run `/usagemonitor reset` to post a new one.");
                        return false;
                    } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                        logger.warning("[Usage Monitor] Webhook invalid or revoked (HTTP " + response.statusCode() +
                                "). Update discord.webhook-url in config.yml and reload.");
                        return false;
                    } else if (response.statusCode() == 429) {
                        logger.warning("[Usage Monitor] Webhook edit rate-limited (HTTP 429). " +
                                "Increase monitor.refresh-interval-seconds in config.yml.");
                        return true;
                    } else {
                        logger.warning("[Usage Monitor] Webhook edit failed: HTTP " + response.statusCode() +
                                " - " + truncate(response.body(), 200));
                        return false;
                    }
                })
                .exceptionally(ex -> {
                    logger.warning("[Usage Monitor] Webhook edit error: " + ex.getMessage());
                    return false;
                });
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
