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

public class PluginConfig {

    private final JavaPlugin plugin;
    private FileConfiguration raw;

    private String webhookUrl;
    private String webhookId;
    private String webhookToken;
    private String messageId;

    private int refreshIntervalSeconds = 5;
    private String embedColor = "#2B2D31";
    private int progressBarLength = 12;
    private boolean showResources = true;
    private boolean showConfiguration = true;
    private boolean showLiveStats = true;
    private boolean showActions = true;
    private boolean showWorldDetails = true;

    private final Map<String, String> emojiMap = new HashMap<>();

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.raw = plugin.getConfig();

        webhookUrl = raw.getString("discord.webhook-url", "").trim();
        messageId = raw.getString("discord.message-id", "").trim();
        parseWebhook();

        refreshIntervalSeconds = Math.max(1, raw.getInt("monitor.refresh-interval-seconds", 5));
        embedColor = raw.getString("monitor.embed-color", "#2B2D31").trim();
        progressBarLength = Math.max(5, Math.min(20, raw.getInt("monitor.progress-bar-length", 12)));

        showResources     = raw.getBoolean("monitor.show-resources", true);
        showConfiguration = raw.getBoolean("monitor.show-configuration", true);
        showLiveStats     = raw.getBoolean("monitor.show-live-stats", true);
        showActions       = raw.getBoolean("monitor.show-actions", true);
        showWorldDetails  = raw.getBoolean("monitor.show-world-details", true);

        loadEmojis();
    }

    private void loadEmojis() {
        emojiMap.clear();
        for (String k : new String[]{
                "title", "resources", "configuration", "live-stats", "players", "worlds",
                "status-online", "status-offline", "cpu", "memory", "disk", "ram", "storage",
                "jvm-heap", "software", "bukkit", "tps", "mspt",
                "players-online", "chunks", "entities"}) {
            emojiMap.put(k, "");
        }
        if (!raw.isConfigurationSection("monitor.emojis")) return;
        ConfigurationSection sec = raw.getConfigurationSection("monitor.emojis");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            String v = sec.getString(key);
            if (v != null) emojiMap.put(key, v);
        }
    }

    private void parseWebhook() {
        webhookId = null;
        webhookToken = null;
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String clean = webhookUrl;
        int q = clean.indexOf('?');
        if (q >= 0) clean = clean.substring(0, q);
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);

        int idx = clean.indexOf("/webhooks/");
        if (idx < 0) {
            plugin.getLogger().warning("Malformed webhook URL (missing /webhooks/): " + webhookUrl);
            return;
        }
        String rest = clean.substring(idx + "/webhooks/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            plugin.getLogger().warning("Malformed webhook URL (missing token): " + webhookUrl);
            return;
        }
        webhookId = rest.substring(0, slash);
        webhookToken = rest.substring(slash + 1);
    }

    public boolean isConfigured() {
        return webhookId != null && !webhookId.isEmpty()
                && webhookToken != null && !webhookToken.isEmpty();
    }

    public synchronized void setMessageId(String id) {
        this.messageId = id == null ? "" : id.trim();
        raw.set("discord.message-id", this.messageId);
        save();
    }

    private void save() {
        try {
            raw.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "config save failed", e);
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
        return emojiMap.getOrDefault(key, "");
    }
}
