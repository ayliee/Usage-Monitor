/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor;

import com.usagemonitor.config.PluginConfig;
import com.usagemonitor.discord.DiscordClient;
import com.usagemonitor.metrics.MemoryMetrics;
import com.usagemonitor.metrics.ServerMetricsCollector;
import com.usagemonitor.metrics.TpsTracker;
import com.usagemonitor.task.MonitorUpdateTask;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UsageMonitorPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private PluginConfig pluginConfig;
    private TpsTracker tpsTracker;
    private ServerMetricsCollector metricsCollector;
    private DiscordClient discordClient;
    private MonitorUpdateTask updateTask;

    @Override
    public void onEnable() {
        if (updateTask != null) {
            updateTask.stop();
            updateTask = null;
        }

        this.pluginConfig = new PluginConfig(this);
        this.pluginConfig.load();

        this.tpsTracker = new TpsTracker(this);
        this.tpsTracker.start();
        this.metricsCollector = new ServerMetricsCollector(this, tpsTracker);

        this.discordClient = new DiscordClient(getLogger());
        this.updateTask = new MonitorUpdateTask(this, pluginConfig, discordClient, metricsCollector);
        this.updateTask.start();

        if (getCommand("usagemonitor") != null) {
            getCommand("usagemonitor").setExecutor(this);
            getCommand("usagemonitor").setTabCompleter(this);
        }

        if (pluginConfig.isConfigured()) {
            getLogger().info("[Usage Monitor] Plugin enabled. Webhook configured, refreshing every "
                    + pluginConfig.getRefreshIntervalSeconds() + "s.");
        } else {
            getLogger().warning("[Usage Monitor] Plugin enabled BUT webhook not configured.");
            getLogger().warning("[Usage Monitor] Edit plugins/UsageMonitor/config.yml and set discord.webhook-url.");
        }
    }

    @Override
    public void onDisable() {
        if (updateTask != null) {
            updateTask.sendShutdownStatus();
            updateTask.stop();
            updateTask = null;
        }
        if (tpsTracker != null) {
            tpsTracker.stop();
            tpsTracker = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("usagemonitor.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                pluginConfig.load();
                if (updateTask != null) updateTask.stop();
                updateTask = new MonitorUpdateTask(this, pluginConfig, discordClient, metricsCollector);
                updateTask.start();
                sender.sendMessage(ChatColor.GREEN + "[Usage Monitor] Config reloaded.");
                break;

            case "status":
                sendStatus(sender);
                break;

            case "forceupdate":
                if (!pluginConfig.isConfigured()) {
                    sender.sendMessage(ChatColor.RED + "Webhook not configured. Set discord.webhook-url first.");
                } else {
                    updateTask.forceUpdate();
                    sender.sendMessage(ChatColor.GREEN + "[Usage Monitor] Forced an immediate update.");
                }
                break;

            case "reset":
                pluginConfig.setMessageId("");
                if (updateTask != null) updateTask.forceUpdate();
                sender.sendMessage(ChatColor.GREEN + "[Usage Monitor] Saved message id cleared. "
                        + "Next cycle will post a fresh webhook message in the channel.");
                break;

            default:
                sendHelp(sender, label);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "=== Usage Monitor Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " status" + ChatColor.GRAY + " — show local metrics");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.GRAY + " — reload config.yml");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " forceupdate" + ChatColor.GRAY + " — refresh Discord now");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reset" + ChatColor.GRAY + " — clear saved message id and post a new one");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " help" + ChatColor.GRAY + " — this help");
    }

    private void sendStatus(CommandSender sender) {
        ServerMetricsCollector.Snapshot snap = metricsCollector.collectSnapshot(true);
        double[] tps = snap.getTps();
        MemoryMetrics mem = snap.getMemory();

        sender.sendMessage(ChatColor.GOLD + "=== Server Resource Monitor ===");
        sender.sendMessage(ChatColor.AQUA + "TPS: " + ChatColor.WHITE
                + String.format("%.2f, %.2f, %.2f", tps[0], tps[1], tps[2])
                + ChatColor.DARK_AQUA + " | MSPT: " + ChatColor.WHITE + String.format("%.2f ms", snap.getMspt()));
        sender.sendMessage(ChatColor.AQUA + "JVM Heap: " + ChatColor.WHITE
                + MemoryMetrics.formatBytes(mem.getHeapUsedBytes()) + " / "
                + MemoryMetrics.formatBytes(mem.getHeapMaxBytes())
                + String.format(" (%.1f%%)", mem.getHeapUsagePercentage()));
        sender.sendMessage(ChatColor.AQUA + "Disk: " + ChatColor.WHITE
                + MemoryMetrics.formatBytes(snap.getDisk().getUsedSpaceBytes()) + " / "
                + MemoryMetrics.formatBytes(snap.getDisk().getTotalSpaceBytes())
                + String.format(" (%.1f%%)", snap.getDisk().getUsedPercentage()));
        sender.sendMessage(ChatColor.AQUA + "Uptime: " + ChatColor.WHITE + snap.getUptime());
        sender.sendMessage(ChatColor.AQUA + "Webhook: "
                + (pluginConfig.isConfigured()
                    ? ChatColor.GREEN + "configured (refresh " + pluginConfig.getRefreshIntervalSeconds() + "s)"
                    : ChatColor.RED + "not configured (edit config.yml)"));
        sender.sendMessage(ChatColor.AQUA + "Saved message id: "
                + (pluginConfig.getMessageId() == null || pluginConfig.getMessageId().isEmpty()
                    ? ChatColor.YELLOW + "(none — will post on next cycle)"
                    : ChatColor.GREEN + pluginConfig.getMessageId()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String opt : Arrays.asList("status", "reload", "forceupdate", "reset", "help")) {
                if (opt.startsWith(args[0].toLowerCase())) completions.add(opt);
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
