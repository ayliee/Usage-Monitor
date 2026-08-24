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

// Async timer fires; snapshot is collected on the main thread (Paper 1.21+
// refuses world/player reads off-thread); HTTP I/O then runs back on async.
public class MonitorUpdateTask implements Runnable {

    private final JavaPlugin plugin;
    private final PluginConfig cfg;
    private final DiscordClient discord;
    private final ServerMetricsCollector collector;
    private final DiscordPayloadBuilder builder;
    private final Logger log;

    private BukkitTask scheduled;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public MonitorUpdateTask(JavaPlugin plugin, PluginConfig cfg,
                             DiscordClient discord, ServerMetricsCollector collector) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.discord = discord;
        this.collector = collector;
        this.builder = new DiscordPayloadBuilder(cfg, serverName(plugin));
        this.log = plugin.getLogger();
    }

    private static String serverName(JavaPlugin plugin) {
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
        if (!cfg.isConfigured()) {
            log.warning("Webhook not configured. Set discord.webhook-url in config.yml.");
            return;
        }
        long interval = (long) cfg.getRefreshIntervalSeconds() * 20L;
        scheduled = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this, 20L, interval);
        log.info("Webhook dashboard active. Refresh every " + cfg.getRefreshIntervalSeconds() + "s.");
    }

    public synchronized void stop() {
        if (scheduled != null) { scheduled.cancel(); scheduled = null; }
    }

    public void forceUpdate() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this);
    }

    @Override
    public void run() {
        if (!cfg.isConfigured()) return;
        if (!busy.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTask(plugin, this::collectOnMainThread);
    }

    private void collectOnMainThread() {
        ServerMetricsCollector.Snapshot snap;
        try {
            snap = collector.collectSnapshot(true);
        } catch (Throwable t) {
            log.warning("Snapshot collection failed: " + t.getMessage());
            busy.set(false);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> dispatch(snap));
    }

    private void dispatch(ServerMetricsCollector.Snapshot snap) {
        try {
            String json = builder.buildLiveDashboardPayload(snap);
            String mid = cfg.getMessageId();

            if (mid == null || mid.isEmpty()) {
                log.info("No saved message id - posting initial webhook message.");
                discord.postWebhookMessage(cfg.getWebhookId(), cfg.getWebhookToken(), json)
                        .thenAccept(newId -> {
                            if (newId != null && !newId.isEmpty()) {
                                cfg.setMessageId(newId);
                                log.info("Dashboard message saved (id=" + newId + ").");
                            } else {
                                log.warning("Initial POST returned no id - next cycle will retry.");
                            }
                            busy.set(false);
                        });
            } else {
                discord.editWebhookMessage(cfg.getWebhookId(), cfg.getWebhookToken(), mid, json)
                        .thenAccept(ok -> {
                            if (!ok) {
                                log.warning("Edit failed for " + mid + " - will retry. Run `/usagemonitor reset` if it keeps failing.");
                            }
                            busy.set(false);
                        });
            }
        } catch (Throwable t) {
            log.warning("Update cycle error: " + t.getMessage());
            busy.set(false);
        }
    }

    public void sendShutdownStatus() {
        if (!cfg.isConfigured()) return;
        String mid = cfg.getMessageId();
        if (mid == null || mid.isEmpty()) return;
        try {
            ServerMetricsCollector.Snapshot snap = collector.collectSnapshot(false);
            String json = builder.buildLiveDashboardPayload(snap);
            discord.editWebhookMessage(cfg.getWebhookId(), cfg.getWebhookToken(), mid, json).join();
            log.info("Shutdown status pushed.");
        } catch (Throwable ignored) {
        }
    }
}
