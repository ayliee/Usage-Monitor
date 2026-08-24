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

    private PluginConfig cfg;
    private TpsTracker tps;
    private ServerMetricsCollector collector;
    private DiscordClient discord;
    private MonitorUpdateTask task;

    @Override
    public void onEnable() {
        if (task != null) {
            task.stop();
            task = null;
        }
        cfg = new PluginConfig(this);
        cfg.load();

        tps = new TpsTracker(this);
        tps.start();
        collector = new ServerMetricsCollector(this, tps);

        discord = new DiscordClient(getLogger());
        task = new MonitorUpdateTask(this, cfg, discord, collector);
        task.start();

        if (getCommand("usagemonitor") != null) {
            getCommand("usagemonitor").setExecutor(this);
            getCommand("usagemonitor").setTabCompleter(this);
        }

        if (cfg.isConfigured()) {
            getLogger().info("Usage Monitor enabled. Webhook live, refresh every " + cfg.getRefreshIntervalSeconds() + "s.");
        } else {
            getLogger().warning("Usage Monitor loaded but webhook is not configured. Set discord.webhook-url in config.yml.");
        }
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.sendShutdownStatus();
            task.stop();
            task = null;
        }
        if (tps != null) { tps.stop(); tps = null; }
    }

    // TODO: split status output into a separate helper class if it grows further
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("usagemonitor.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                cfg.load();
                if (task != null) task.stop();
                task = new MonitorUpdateTask(this, cfg, discord, collector);
                task.start();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded.");
                break;
            case "status": sendStatus(sender); break;
            case "forceupdate":
                if (!cfg.isConfigured()) {
                    sender.sendMessage(ChatColor.RED + "Webhook not configured.");
                } else {
                    task.forceUpdate();
                    sender.sendMessage(ChatColor.GREEN + "Forced a refresh.");
                }
                break;
            case "reset":
                cfg.setMessageId("");
                if (task != null) task.forceUpdate();
                sender.sendMessage(ChatColor.GREEN + "Saved message id cleared - next cycle will post a fresh message.");
                break;
            default: sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender s, String label) {
        s.sendMessage(ChatColor.GOLD + "Usage Monitor");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " status " + ChatColor.GRAY + "- show local metrics");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " reload " + ChatColor.GRAY + "- reload config");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " forceupdate " + ChatColor.GRAY + "- push to Discord now");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " reset " + ChatColor.GRAY + "- clear message id and repost");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " help");
    }

    private void sendStatus(CommandSender s) {
        ServerMetricsCollector.Snapshot snap = collector.collectSnapshot(true);
        double[] tpsArr = snap.getTps();
        MemoryMetrics mem = snap.getMemory();

        s.sendMessage(ChatColor.GOLD + "Server Monitor");
        s.sendMessage(ChatColor.AQUA + "TPS: " + ChatColor.WHITE
                + String.format("%.2f, %.2f, %.2f", tpsArr[0], tpsArr[1], tpsArr[2])
                + ChatColor.DARK_AQUA + "  MSPT: " + ChatColor.WHITE
                + String.format("%.2f ms", snap.getMspt()));
        s.sendMessage(ChatColor.AQUA + "Heap: " + ChatColor.WHITE
                + MemoryMetrics.formatBytes(mem.getHeapUsedBytes()) + " / "
                + MemoryMetrics.formatBytes(mem.getHeapMaxBytes())
                + String.format(" (%.1f%%)", mem.getHeapUsagePercentage()));
        s.sendMessage(ChatColor.AQUA + "Disk: " + ChatColor.WHITE
                + MemoryMetrics.formatBytes(snap.getDisk().getUsedSpaceBytes()) + " / "
                + MemoryMetrics.formatBytes(snap.getDisk().getTotalSpaceBytes())
                + String.format(" (%.1f%%)", snap.getDisk().getUsedPercentage()));
        s.sendMessage(ChatColor.AQUA + "Uptime: " + ChatColor.WHITE + snap.getUptime());
        s.sendMessage(ChatColor.AQUA + "Webhook: "
                + (cfg.isConfigured()
                    ? ChatColor.GREEN + "ok (" + cfg.getRefreshIntervalSeconds() + "s refresh)"
                    : ChatColor.RED + "not configured"));
        s.sendMessage(ChatColor.AQUA + "Message id: "
                + (cfg.getMessageId() == null || cfg.getMessageId().isEmpty()
                    ? ChatColor.YELLOW + "(none, will post on next cycle)"
                    : ChatColor.GREEN + cfg.getMessageId()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String opt : Arrays.asList("status", "reload", "forceupdate", "reset", "help")) {
            if (opt.startsWith(args[0].toLowerCase())) out.add(opt);
        }
        return out;
    }
}
