/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

// Auth is a single Discord webhook URL — parsed once on load into id + token.
public class PluginConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    private String webhookUrl;
    private String webhookId;
    private String webhookToken;
    private String messageId;

    private int refreshIntervalSeconds;
    private String embedColor;
    private int progressBarLength;
    private boolean showResources;
    private boolean showConfiguration;
    private boolean showLiveStats;
    private boolean showActions;
    private boolean showWorldDetails;

    private final Map<String, String> emojis = new HashMap<>();

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        this.webhookUrl = config.getString("discord.webhook-url", "").trim();
        this.messageId = config.getString("discord.message-id", "").trim();
        parseWebhook();

        this.refreshIntervalSeconds = Math.max(1, config.getInt("monitor.refresh-interval-seconds", 5));
        this.embedColor = config.getString("monitor.embed-color", "#2B2D31").trim();
        this.progressBarLength = Math.max(5, Math.min(20, config.getInt("monitor.progress-bar-length", 12)));

        this.showResources = config.getBoolean("monitor.show-resources", true);
        this.showConfiguration = config.getBoolean("monitor.show-configuration", true);
        this.showLiveStats = config.getBoolean("monitor.show-live-stats", true);
        this.showActions = config.getBoolean("monitor.show-actions", true);
        this.showWorldDetails = config.getBoolean("monitor.show-world-details", true);

        loadEmojis();
    }

    private void loadEmojis() {
        emojis.clear();
        String[] keys = {
            "title", "resources", "configuration", "live-stats", "players", "worlds",
            "status-online", "status-offline", "cpu", "memory", "disk", "ram", "storage",
            "jvm-heap", "software", "bukkit", "tps", "mspt", "players-online", "chunks", "entities"
        };
        for (String k : keys) emojis.put(k, "");
        if (!config.isConfigurationSection("monitor.emojis")) return;
        ConfigurationSection sec = config.getConfigurationSection("monitor.emojis");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            String val = sec.getString(key);
            if (val != null) emojis.put(key, val);
        }
    }

    private void parseWebhook() {
        this.webhookId = null;
        this.webhookToken = null;
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String clean = webhookUrl;
        int q = clean.indexOf('?');
        if (q >= 0) clean = clean.substring(0, q);
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);

        int idx = clean.indexOf("/webhooks/");
        if (idx < 0) {
            plugin.getLogger().warning("[Usage Monitor] Webhook URL is malformed (missing '/webhooks/'): " + webhookUrl);
            return;
        }
        String rest = clean.substring(idx + "/webhooks/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            plugin.getLogger().warning("[Usage Monitor] Webhook URL is malformed (missing token): " + webhookUrl);
            return;
        }
        this.webhookId = rest.substring(0, slash);
        this.webhookToken = rest.substring(slash + 1);
    }

    public boolean isConfigured() {
        return webhookId != null && !webhookId.isEmpty()
                && webhookToken != null && !webhookToken.isEmpty();
    }

    public synchronized void setMessageId(String newMessageId) {
        this.messageId = newMessageId == null ? "" : newMessageId.trim();
        config.set("discord.message-id", this.messageId);
        save();
    }

    private void save() {
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save config.yml", e);
        }
    }

    public String getWebhookUrl() { return webhookUrl; }
    public String getWebhookId() { return webhookId; }
    public String getWebhookToken() { return webhookToken; }
    public String getMessageId() { return messageId; }

    public int getRefreshIntervalSeconds() { return refreshIntervalSeconds; }
    public String getEmbedColor() { return embedColor; }
    public int getProgressBarLengthSafe() { return progressBarLength; }
    public boolean isShowWorldDetailsSafe() { return showWorldDetails; }
    public boolean isShowResources() { return showResources; }
    public boolean isShowConfiguration() { return showConfiguration; }
    public boolean isShowLiveStats() { return showLiveStats; }
    public boolean isShowActions() { return showActions; }

    public String emoji(String key) {
        return emojis.getOrDefault(key, "");
    }
}
