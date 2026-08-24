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

// POST once to grab a message id, then PATCH it every refresh. Failures are
// logged and the same id is retried next cycle - we never auto-repost.
public class DiscordClient {

    private static final String BASE = "https://discord.com/api/webhooks";
    private static final String UA = "UsageMonitor/1.2.3 (Minecraft)";

    private final HttpClient http;
    private final Logger log;

    public DiscordClient(Logger log) {
        this.log = log;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public CompletableFuture<String> postWebhookMessage(String id, String token, String json) {
        if (id == null || token == null) return CompletableFuture.completedFuture(null);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/" + id + "/" + token + "?wait=true"))
                .header("Content-Type", "application/json")
                .header("User-Agent", UA)
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() / 100 == 2) {
                String mid = JsonObjectBuilder.extractStringField(resp.body(), "id");
                log.info("Webhook message posted (id=" + mid + ")");
                return mid;
            }
            log.warning("Webhook POST " + resp.statusCode() + ": " + trim(resp.body()));
            return null;
        }).exceptionally(t -> {
            log.log(Level.WARNING, "Webhook POST error", t);
            return null;
        });
    }

    public CompletableFuture<Boolean> editWebhookMessage(String id, String token, String msgId, String json) {
        if (id == null || token == null || msgId == null || msgId.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/" + id + "/" + token + "/messages/" + msgId))
                .header("Content-Type", "application/json")
                .header("User-Agent", UA)
                .timeout(Duration.ofSeconds(8))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            int s = resp.statusCode();
            if (s / 100 == 2) return true;
            if (s == 404) {
                log.warning("Webhook message " + msgId + " not found. Run `/usagemonitor reset`.");
                return false;
            }
            if (s == 401 || s == 403) {
                log.warning("Webhook rejected (" + s + "). Update discord.webhook-url and reload.");
                return false;
            }
            if (s == 429) {
                log.warning("Rate limited. Bump monitor.refresh-interval-seconds in config.yml.");
                return true;
            }
            log.warning("Webhook edit " + s + ": " + trim(resp.body()));
            return false;
        }).exceptionally(t -> {
            log.warning("Webhook edit error: " + t.getMessage());
            return false;
        });
    }

    private static String trim(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
