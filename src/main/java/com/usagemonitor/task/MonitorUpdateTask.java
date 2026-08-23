/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.task;

import com.usagemonitor.config.PluginConfig;
import com.usagemonitor.discord.DiscordClient;
import com.usagemonitor.discord.DiscordPayloadBuilder;
import com.usagemonitor.metrics.ServerMetricsCollector;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

// Async timer fires; snapshot is collected on the main thread (Bukkit API is
// main-thread only on Paper 1.21+); HTTP I/O then runs back on async.
public class MonitorUpdateTask implements Runnable {

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final DiscordClient discordClient;
    private final ServerMetricsCollector metricsCollector;
    private final DiscordPayloadBuilder payloadBuilder;
    private final Logger logger;

    private BukkitTask scheduledTask;
    private final AtomicBoolean isUpdating = new AtomicBoolean(false);

    public MonitorUpdateTask(JavaPlugin plugin,
                             PluginConfig config,
                             DiscordClient discordClient,
                             ServerMetricsCollector metricsCollector) {
        this.plugin = plugin;
        this.config = config;
        this.discordClient = discordClient;
        this.metricsCollector = metricsCollector;
        this.payloadBuilder = new DiscordPayloadBuilder(config, resolveServerName(plugin));
        this.logger = plugin.getLogger();
    }

    private static String resolveServerName(JavaPlugin plugin) {
        try {
            String ip = plugin.getServer().getIp();
            int port = plugin.getServer().getPort();
            if (ip != null && !ip.isEmpty() && !"0.0.0.0".equals(ip) && !"127.0.0.1".equals(ip)) {
                return ip + ":" + port;
            }
            return "0.0.0.0:" + port;
        } catch (Throwable t) {
            return "Minecraft Server";
        }
    }

    public synchronized void start() {
        stop();
        if (!config.isConfigured()) {
            logger.warning("[Usage Monitor] Webhook not configured. Set discord.webhook-url in plugins/UsageMonitor/config.yml.");
            return;
        }
        long intervalTicks = (long) config.getRefreshIntervalSeconds() * 20L;
        this.scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this, 20L, intervalTicks);
        logger.info("[Usage Monitor] Webhook dashboard active. Refreshing every "
                + config.getRefreshIntervalSeconds() + "s.");
    }

    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    public void forceUpdate() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this);
    }

    @Override
    public void run() {
        if (!config.isConfigured()) return;
        if (!isUpdating.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTask(plugin, this::collectOnMainThread);
    }

    private void collectOnMainThread() {
        ServerMetricsCollector.Snapshot snapshot;
        try {
            snapshot = metricsCollector.collectSnapshot(true);
        } catch (Throwable t) {
            logger.warning("[Usage Monitor] Snapshot collection failed: " + t.getMessage());
            isUpdating.set(false);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> dispatch(snapshot));
    }

    private void dispatch(ServerMetricsCollector.Snapshot snapshot) {
        try {
            String payloadJson = payloadBuilder.buildLiveDashboardPayload(snapshot);
            String messageId = config.getMessageId();

            if (messageId == null || messageId.isEmpty()) {
                logger.info("[Usage Monitor] No dashboard message saved — posting initial webhook message.");
                discordClient.postWebhookMessage(
                        config.getWebhookId(), config.getWebhookToken(), payloadJson
                ).thenAccept(newId -> {
                    if (newId != null && !newId.isEmpty()) {
                        config.setMessageId(newId);
                        logger.info("[Usage Monitor] Dashboard message saved (id=" + newId + ").");
                    } else {
                        logger.warning("[Usage Monitor] Initial webhook post returned no id — next cycle will retry.");
                    }
                    isUpdating.set(false);
                });
            } else {
                discordClient.editWebhookMessage(
                        config.getWebhookId(), config.getWebhookToken(), messageId, payloadJson
                ).thenAccept(success -> {
                    if (!success) {
                        logger.warning("[Usage Monitor] Webhook edit failed for message " + messageId
                                + " — will retry next cycle. If this keeps happening, run `/usagemonitor reset`.");
                    }
                    isUpdating.set(false);
                });
            }
        } catch (Throwable t) {
            logger.warning("[Usage Monitor] Update cycle error: " + t.getMessage());
            isUpdating.set(false);
        }
    }

    public void sendShutdownStatus() {
        if (!config.isConfigured()) return;
        String messageId = config.getMessageId();
        if (messageId == null || messageId.isEmpty()) return;
        try {
            ServerMetricsCollector.Snapshot offlineSnapshot = metricsCollector.collectSnapshot(false);
            String payloadJson = payloadBuilder.buildLiveDashboardPayload(offlineSnapshot);
            discordClient.editWebhookMessage(
                    config.getWebhookId(), config.getWebhookToken(), messageId, payloadJson
            ).join();
            logger.info("[Usage Monitor] Shutdown status pushed to webhook.");
        } catch (Throwable ignored) {
        }
    }
}
