/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.discord;

import com.usagemonitor.config.PluginConfig;
import com.usagemonitor.discord.model.EmbedBuilder;
import com.usagemonitor.metrics.MemoryMetrics;
import com.usagemonitor.metrics.NetworkAndPlayerMetrics;
import com.usagemonitor.metrics.ServerMetricsCollector;
import com.usagemonitor.metrics.WorldMetrics;

public class DiscordPayloadBuilder {

    private final PluginConfig cfg;
    private final String serverName;

    public DiscordPayloadBuilder(PluginConfig cfg, String serverName) {
        this.cfg = cfg;
        this.serverName = (serverName == null || serverName.isEmpty()) ? "Minecraft Server" : serverName;
    }

    // Returns "emoji " or "" depending on config. Inline so the builder stays compact.
    private String e(String key) {
        String em = cfg.emoji(key);
        return em.isEmpty() ? "" : em + " ";
    }

    public String buildLiveDashboardPayload(ServerMetricsCollector.Snapshot snap) {
        int color = parseHex(cfg.getEmbedColor());
        if (!snap.isOnline()) color = 0xE74C3C;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(color);
        embed.setTitle(e("title") + serverName + " - Live Usage");

        StringBuilder desc = new StringBuilder();
        if (snap.isOnline()) {
            desc.append("**Status:** ").append(e("status-online")).append("ONLINE  -  Uptime `")
                    .append(snap.getUptime()).append("`");
        } else {
            desc.append("**Status:** ").append(e("status-offline")).append("OFFLINE  -  Last uptime `")
                    .append(snap.getUptime()).append("`");
        }
        embed.setDescription(desc.toString());

        if (cfg.isShowResources()) {
            StringBuilder v = new StringBuilder();
            v.append("> **").append(e("ram")).append("RAM:** `")
                    .append(MemoryMetrics.formatBytes(snap.getMemory().getSystemTotalBytes())).append("`\n");
            v.append("> **").append(e("cpu")).append("CPU:** `")
                    .append(snap.getCpu().getAvailableProcessors()).append(" Cores`\n");
            v.append("> **").append(e("storage")).append("Storage:** `")
                    .append(MemoryMetrics.formatBytes(snap.getDisk().getTotalSpaceBytes())).append("`\n");
            v.append("> **").append(e("jvm-heap")).append("JVM Heap:** `")
                    .append(MemoryMetrics.formatBytes(snap.getMemory().getHeapMaxBytes())).append("`");
            embed.addField(e("resources") + "Resources", v.toString(), true);
        }

        if (cfg.isShowConfiguration()) {
            double[] t = snap.getTps();
            String tpsIcon = t[0] >= 19.0 ? e("status-online") : (t[0] >= 15.0 ? "" : e("status-offline"));
            StringBuilder v = new StringBuilder();
            v.append("> **").append(e("software")).append("Software:** `").append(snap.getServerVersion()).append("`\n");
            v.append("> **").append(e("bukkit")).append("Bukkit:** `").append(snap.getBukkitVersion()).append("`\n");
            v.append("> **").append(e("tps")).append("TPS (1m):** ").append(tpsIcon)
                    .append("`").append(String.format("%.2f", t[0])).append("`\n");
            v.append("> **").append(e("mspt")).append("MSPT:** `")
                    .append(String.format("%.2f ms", snap.getMspt())).append("`");
            embed.addField(e("configuration") + "Configuration", v.toString(), true);
        }

        if (cfg.isShowLiveStats()) {
            StringBuilder v = new StringBuilder();
            double pcpu = Math.max(0, Math.min(100, snap.getCpu().getProcessCpuLoad()));
            String cpuStr = snap.getCpu().getProcessCpuLoad() >= 0
                    ? String.format("%.1f%%", snap.getCpu().getProcessCpuLoad()) : "N/A";
            v.append("> **").append(e("cpu")).append("CPU:** `").append(cpuStr).append("` ")
                    .append(miniBar(pcpu, 8)).append("\n");
            MemoryMetrics mem = snap.getMemory();
            double memP = mem.getHeapUsagePercentage();
            v.append("> **").append(e("memory")).append("Memory:** `")
                    .append(MemoryMetrics.formatBytes(mem.getHeapUsedBytes())).append("` / `")
                    .append(MemoryMetrics.formatBytes(mem.getHeapMaxBytes())).append("` (")
                    .append(String.format("%.1f%%", memP)).append(") ")
                    .append(miniBar(memP, 8)).append("\n");
            double diskP = snap.getDisk().getUsedPercentage();
            v.append("> **").append(e("disk")).append("Disk:** `")
                    .append(MemoryMetrics.formatBytes(snap.getDisk().getUsedSpaceBytes())).append("` / `")
                    .append(MemoryMetrics.formatBytes(snap.getDisk().getTotalSpaceBytes())).append("` (")
                    .append(String.format("%.1f%%", diskP)).append(") ")
                    .append(miniBar(diskP, 8));
            embed.addField(e("live-stats") + "Live Stats", v.toString(), false);
        }

        if (cfg.isShowActions()) {
            NetworkAndPlayerMetrics net = snap.getNetwork();
            embed.addField(e("players") + "Players",
                    "> **" + e("players-online") + "Players Online:** `"
                            + net.getOnlinePlayerCount() + " / " + net.getMaxPlayers() + "`",
                    false);
        }

        if (cfg.isShowWorldDetailsSafe()) {
            WorldMetrics wm = snap.getWorlds();
            StringBuilder v = new StringBuilder();
            v.append("> **").append(e("chunks")).append("Chunks:** `").append(wm.getTotalLoadedChunks())
                    .append("`  -  **").append(e("entities")).append("Entities:** `")
                    .append(wm.getTotalEntities()).append("`");
            if (wm.getWorlds().size() <= 6) {
                v.append("\n");
                int i = 0;
                for (WorldMetrics.WorldDetail wd : wm.getWorlds()) {
                    if (i++ > 0) v.append("  -  ");
                    v.append("`").append(wd.getName()).append("`: ")
                            .append(wd.getLoadedChunks()).append("c / ")
                            .append(wd.getEntityCount()).append("e");
                }
            }
            embed.addField(e("worlds") + "Worlds", v.toString(), false);
        }

        return new JsonObjectBuilder()
                .add("embeds", java.util.List.of(embed.toJsonObject()))
                .build();
    }

    private static String miniBar(double percent, int width) {
        if (width < 1) width = 1;
        double c = Math.max(0, Math.min(100, percent));
        int filled = (int) Math.round(c / 100.0 * width);
        StringBuilder sb = new StringBuilder(width + 2);
        sb.append('[');
        for (int i = 0; i < width; i++) sb.append(i < filled ? '\u25B0' : '\u25B1');
        sb.append(']');
        return sb.toString();
    }

    private static int parseHex(String hex) {
        try {
            if (hex == null) return 0x2B2D31;
            String c = hex.trim().replace("#", "");
            if (c.isEmpty()) return 0x2B2D31;
            return Integer.parseInt(c, 16);
        } catch (NumberFormatException e) {
            return 0x2B2D31;
        }
    }
}
